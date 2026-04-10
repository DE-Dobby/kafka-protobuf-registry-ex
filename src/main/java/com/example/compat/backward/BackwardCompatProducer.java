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

/**
 * Backward Compatibility 테스트 - Producer
 *
 * 구 스키마(v1, UserEvent)로 메시지를 전송한다.
 * BackwardCompatConsumer는 v2(email 추가)로 수신 → email은 기본값 ""
 *
 * 실행: mvn compile exec:java -Dexec.mainClass="com.example.compat.backward.BackwardCompatProducer"
 */
public class BackwardCompatProducer {

    private static final Logger log = LoggerFactory.getLogger(BackwardCompatProducer.class);

    private static final String TOPIC            = AppConfig.get("kafka.topic");
    private static final String BOOTSTRAP_SERVER = AppConfig.get("kafka.bootstrap.servers");
    private static final String SCHEMA_REGISTRY  = AppConfig.get("schema.registry.url");

    public static void main(String[] args) throws InterruptedException {
        log.info("=== Backward Compat Producer 시작 (v1 스키마) ===");
        log.info("topic: {}", TOPIC);
        try (KafkaProducer<String, UserEvent> producer = new KafkaProducer<>(buildProps())) {
            for (int i = 1; i <= 5; i++) {
                // v1 스키마: email 필드 없음
                UserEvent event = UserEvent.newBuilder()
                        .setUserId("user-" + i)
                        .setAction("LOGIN")
                        .setPayload("payload-" + i)
                        .setTimestamp(System.currentTimeMillis())
                        .build();

                producer.send(new ProducerRecord<>(TOPIC, event.getUserId(), event), (meta, ex) -> {
                    if (ex != null) {
                        log.error("전송 실패", ex);
                    } else {
                        log.info("전송 완료 → partition={} offset={} userId={}",
                                meta.partition(), meta.offset(), event.getUserId());
                    }
                });

                Thread.sleep(500);
            }
            producer.flush();
        }

        log.info("전송 완료. BackwardCompatConsumer로 수신하면 email='' 확인 가능");
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
