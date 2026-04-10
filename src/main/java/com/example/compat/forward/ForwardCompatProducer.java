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

/**
 * Forward Compatibility 테스트 - Producer
 *
 * 신 스키마(v2, timestamp 제거)로 메시지를 전송한다.
 * ForwardCompatConsumer는 v1으로 수신 → timestamp는 기본값 0
 *
 * 실행: mvn compile exec:java -Dexec.mainClass="com.example.compat.forward.ForwardCompatProducer"
 */
public class ForwardCompatProducer {

    private static final Logger log = LoggerFactory.getLogger(ForwardCompatProducer.class);

    private static final String TOPIC            = AppConfig.get("kafka.topic");
    private static final String BOOTSTRAP_SERVER = AppConfig.get("kafka.bootstrap.servers");
    private static final String SCHEMA_REGISTRY  = AppConfig.get("schema.registry.url");

    public static void main(String[] args) throws InterruptedException {
        log.info("=== Forward Compat Producer 시작 (v2 스키마, timestamp 없음) ===");
        log.info("topic: {}", TOPIC);
        try (KafkaProducer<String, UserEventV2ForwardProto.UserEvent> producer = new KafkaProducer<>(buildProps())) {
            for (int i = 1; i <= 5; i++) {
                // v2 스키마: timestamp 필드 없음
                UserEventV2ForwardProto.UserEvent event = UserEventV2ForwardProto.UserEvent.newBuilder()
                        .setUserId("user-" + i)
                        .setAction("PURCHASE")
                        .setPayload("item-" + i)
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

        log.info("전송 완료. ForwardCompatConsumer로 수신하면 timestamp=0 확인 가능");
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
