package com.example.producer;

import com.example.proto.UserEvent;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class UserEventProducer {

    private static final Logger log = LoggerFactory.getLogger(UserEventProducer.class);

    private static final String TOPIC            = "dobby-events";
    private static final String BOOTSTRAP_SERVER = "localhost:9092";
    private static final String SCHEMA_REGISTRY  = "http://localhost:8081";

    public static void main(String[] args) throws InterruptedException {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVER);
        // Key는 일반 String, Value는 Protobuf 메시지를 직렬화
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaProtobufSerializer.class.getName());
        // KafkaProtobufSerializer가 Schema Registry에 스키마를 자동 등록/조회할 때 사용하는 주소
        props.put("schema.registry.url", SCHEMA_REGISTRY);

        // try-with-resources: 종료 시 producer.close() 자동 호출
        try (KafkaProducer<String, UserEvent> producer = new KafkaProducer<>(props)) {
            for (int i = 1; i <= 5; i++) {
                // Protobuf 메시지는 빌더 패턴으로 생성 (proto 컴파일 시 자동 생성된 코드)
                UserEvent event = UserEvent.newBuilder()
                        .setUserId("user-" + i)
                        .setAction("LOGIN")
                        .setPayload("payload-" + i)
                        .setTimestamp(System.currentTimeMillis())
                        .build();

                // Key를 userId로 설정 → 같은 userId는 항상 같은 파티션으로 라우팅됨
                ProducerRecord<String, UserEvent> record = new ProducerRecord<>(TOPIC, event.getUserId(), event);

                // send()는 비동기 → 콜백으로 성공/실패 처리
                producer.send(record, (metadata, ex) -> {
                    if (ex != null) {
                        log.error("전송 실패", ex);
                    } else {
                        log.info("전송 완료 → topic={} partition={} offset={}",
                                metadata.topic(), metadata.partition(), metadata.offset());
                    }
                });

                Thread.sleep(500);
            }

            // flush(): 내부 버퍼에 남아 있는 메시지를 모두 전송하고 반환
            producer.flush();
        }
    }
}
