package com.example.compat.forward;

import com.example.config.AppConfig;
import com.example.proto.UserEventV2ForwardProto;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Forward Compatibility 테스트 - Producer V2 (나중에 배포)
 *
 * timestamp 필드가 제거된 v2 스키마로 메시지를 계속 전송한다.
 * ConsumerV2가 먼저 배포되어 v2 스키마를 Schema Registry에 등록한 이후,
 * 이 ProducerV2를 배포하면 v1 Consumer(timestamp=0), v2 Consumer(정상) 동시 운영 가능하다.
 *
 * 시나리오:
 *   1. ForwardCompatProducerV1 + ForwardCompatConsumerV1 실행 (초기 운영)
 *   2. ForwardCompatConsumerV2 배포 (v2 스키마 등록)
 *   3. ProducerV1 계속 동작 중 (v1 Consumer 정상, v2 Consumer도 정상)
 *   4. 이 ProducerV2 배포 → v1 Consumer: timestamp=0, v2 Consumer: 정상
 *
 * 실행: mvn compile exec:java -Dexec.mainClass="com.example.compat.forward.ForwardCompatProducerV2"
 */
public class ForwardCompatProducerV2 {

    private static final Logger log = LoggerFactory.getLogger(ForwardCompatProducerV2.class);

    private static final String TOPIC            = AppConfig.get("kafka.topic");
    private static final String BOOTSTRAP_SERVER = AppConfig.get("kafka.bootstrap.servers");
    private static final String SCHEMA_REGISTRY  = AppConfig.get("schema.registry.url");

    public static void main(String[] args) throws InterruptedException {
        log.info("=== Forward Producer V2 시작 (스키마 변경, timestamp 제거) ===");

        AtomicInteger counter = new AtomicInteger(1);

        try (KafkaProducer<String, UserEventV2ForwardProto.UserEvent> producer = new KafkaProducer<>(buildProps())) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("종료 중... flush 완료 후 종료");
                producer.flush();
            }));

            while (!Thread.currentThread().isInterrupted()) {
                int i = counter.getAndIncrement();
                UserEventV2ForwardProto.UserEvent event = UserEventV2ForwardProto.UserEvent.newBuilder()
                        .setUserId("user-" + i)
                        .setAction("PURCHASE")
                        .setPayload("item-" + i)
                        .build();

                producer.send(new ProducerRecord<>(TOPIC, event.getUserId(), event), (meta, ex) -> {
                    if (ex != null) log.error("전송 실패", ex);
                    else log.info("[v2] 전송 → userId={} (timestamp 없음)", event.getUserId());
                });

                Thread.sleep(2000);
            }
        }
    }

    private static Properties buildProps() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVER);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaProtobufSerializer.class.getName());
        props.put("schema.registry.url", SCHEMA_REGISTRY);
        return props;
    }
}
