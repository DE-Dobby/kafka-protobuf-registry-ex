package com.example.compat.backward;

import com.example.config.AppConfig;
import com.example.proto.UserEvent;
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
 * Backward Compatibility 테스트 - Producer V1 (초기 운영 상태)
 *
 * email 필드가 없는 v1 스키마로 메시지를 계속 전송한다.
 * 스키마 변경 전 초기 운영 상태를 시뮬레이션한다.
 *
 * 실행: mvn compile exec:java -Dexec.mainClass="com.example.compat.backward.BackwardCompatProducerV1"
 */
public class BackwardCompatProducerV1 {

    private static final Logger log = LoggerFactory.getLogger(BackwardCompatProducerV1.class);

    private static final String TOPIC            = AppConfig.get("kafka.topic");
    private static final String BOOTSTRAP_SERVER = AppConfig.get("kafka.bootstrap.servers");
    private static final String SCHEMA_REGISTRY  = AppConfig.get("schema.registry.url");

    public static void main(String[] args) throws InterruptedException {
        log.info("=== Backward Producer V1 시작 (초기 운영, email 없음) ===");

        AtomicInteger counter = new AtomicInteger(1);

        try (KafkaProducer<String, UserEvent> producer = new KafkaProducer<>(buildProps())) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("종료 중... flush 완료 후 종료");
                producer.flush();
            }));

            while (!Thread.currentThread().isInterrupted()) {
                int i = counter.getAndIncrement();
                UserEvent event = UserEvent.newBuilder()
                        .setUserId("user-" + i)
                        .setAction("LOGIN")
                        .setPayload("payload-" + i)
                        .setTimestamp(System.currentTimeMillis())
                        .build();

                producer.send(new ProducerRecord<>(TOPIC, event.getUserId(), event), (meta, ex) -> {
                    if (ex != null) log.error("전송 실패", ex);
                    else log.info("[v1] 전송 → userId={}", event.getUserId());
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
