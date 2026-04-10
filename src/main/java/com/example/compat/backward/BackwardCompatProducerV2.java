package com.example.compat.backward;

import com.example.config.AppConfig;
import com.example.proto.UserEventV2BackwardProto;
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
 * Backward Compatibility 테스트 - Producer V2 (스키마 변경 후 재배포)
 *
 * email 필드가 추가된 v2 스키마로 메시지를 계속 전송한다.
 * KafkaProtobufSerializer가 v2 스키마를 Schema Registry에 자동 등록한다.
 * 기존 ConsumerV1은 email 필드를 모르지만 무시하고 정상 동작한다.
 *
 * 실행: mvn compile exec:java -Dexec.mainClass="com.example.compat.backward.BackwardCompatProducerV2"
 */
public class BackwardCompatProducerV2 {

    private static final Logger log = LoggerFactory.getLogger(BackwardCompatProducerV2.class);

    private static final String TOPIC            = AppConfig.get("kafka.topic");
    private static final String BOOTSTRAP_SERVER = AppConfig.get("kafka.bootstrap.servers");
    private static final String SCHEMA_REGISTRY  = AppConfig.get("schema.registry.url");

    public static void main(String[] args) throws InterruptedException {
        log.info("=== Backward Producer V2 시작 (스키마 변경, email 추가) ===");

        AtomicInteger counter = new AtomicInteger(1);

        try (KafkaProducer<String, UserEventV2BackwardProto.UserEvent> producer = new KafkaProducer<>(buildProps())) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("종료 중... flush 완료 후 종료");
                producer.flush();
            }));

            while (!Thread.currentThread().isInterrupted()) {
                int i = counter.getAndIncrement();
                // payload는 deprecated → 설정하지 않음, email로 대체
                UserEventV2BackwardProto.UserEvent event = UserEventV2BackwardProto.UserEvent.newBuilder()
                        .setUserId("user-" + i)
                        .setAction("LOGIN")
                        .setTimestamp(System.currentTimeMillis())
                        .setEmail("user-" + i + "@example.com")
                        .build();

                producer.send(new ProducerRecord<>(TOPIC, event.getUserId(), event), (meta, ex) -> {
                    if (ex != null) log.error("전송 실패", ex);
                    else log.info("[v2] 전송 → userId={} email={}", event.getUserId(), event.getEmail());
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
