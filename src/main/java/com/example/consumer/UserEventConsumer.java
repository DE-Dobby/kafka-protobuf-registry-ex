package com.example.consumer;

import com.example.proto.UserEvent;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializerConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Properties;

public class UserEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(UserEventConsumer.class);

    private static final String TOPIC            = "dobby-events";
    private static final String BOOTSTRAP_SERVER = "localhost:9092";
    private static final String SCHEMA_REGISTRY  = "http://localhost:8081";
    // Consumer Group: 같은 그룹 내 인스턴스들이 파티션을 나눠서 처리 (수평 확장)
    private static final String GROUP_ID         = "dobby-consumer-group";

    public static void main(String[] args) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVER);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID);
        // earliest: 컨슈머 그룹이 처음 구독할 때 토픽의 맨 처음 오프셋부터 읽음
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaProtobufDeserializer.class.getName());
        // KafkaProtobufDeserializer가 Schema Registry에서 스키마를 조회할 때 사용하는 주소
        props.put("schema.registry.url", SCHEMA_REGISTRY);
        // 역직렬화 결과를 GenericMessage가 아닌 지정한 Protobuf 클래스 타입으로 반환
        props.put(KafkaProtobufDeserializerConfig.SPECIFIC_PROTOBUF_VALUE_TYPE, UserEvent.class.getName());

        // try-with-resources: 종료 시 consumer.close() 자동 호출 (오프셋 커밋 + 그룹 리밸런싱 트리거)
        try (KafkaConsumer<String, UserEvent> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(TOPIC));
            log.info("컨슈머 시작 - topic={}, group={}", TOPIC, GROUP_ID);

            while (true) {
                // poll(): 최대 1초 대기 후 브로커에서 배치로 레코드를 가져옴
                ConsumerRecords<String, UserEvent> records = consumer.poll(Duration.ofMillis(1000));
                records.forEach(record -> {
                    UserEvent event = record.value();
                    log.info("수신 → key={} userId={} action={} payload={} ts={}",
                            record.key(),
                            event.getUserId(),
                            event.getAction(),
                            event.getPayload(),
                            event.getTimestamp());
                });
            }
        }
    }
}
