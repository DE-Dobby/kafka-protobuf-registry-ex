package com.example.compat.forward;

import com.example.config.AppConfig;
import com.example.proto.UserEvent;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializerConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

/**
 * Forward Compatibility 테스트 - Consumer
 *
 * 구 스키마(v1, UserEvent)로 수신한다.
 * ForwardCompatProducer가 보낸 v2 데이터를 읽으면 timestamp는 기본값 0으로 채워진다.
 *
 * 실행: mvn compile exec:java -Dexec.mainClass="com.example.compat.forward.ForwardCompatConsumer"
 */
public class ForwardCompatConsumer {

    private static final Logger log = LoggerFactory.getLogger(ForwardCompatConsumer.class);

    private static final String TOPIC            = AppConfig.get("kafka.topic");
    private static final String BOOTSTRAP_SERVER = AppConfig.get("kafka.bootstrap.servers");
    private static final String SCHEMA_REGISTRY  = AppConfig.get("schema.registry.url");
    private static final String GROUP_ID         = AppConfig.get("kafka.consumer.group-id.forward");

    public static void main(String[] args) {
        log.info("=== Forward Compat Consumer 시작 (v1 스키마) ===");
        log.info("topic: {}, group: {}", TOPIC, GROUP_ID);
        log.info("v2 데이터 수신 시 timestamp 필드는 기본값 0 으로 채워짐");

        try (KafkaConsumer<String, UserEvent> consumer = new KafkaConsumer<>(buildProps())) {
            // 파티션이 할당되는 시점에 바로 끝으로 이동 — poll() 이전에 seek가 적용됨
            consumer.subscribe(List.of(TOPIC), new ConsumerRebalanceListener() {
                @Override
                public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                    consumer.seekToEnd(partitions);
                    log.info("파티션 끝으로 이동 완료. 새 메시지 대기 중...");
                }
                @Override
                public void onPartitionsRevoked(Collection<TopicPartition> partitions) {}
            });

            while (true) {
                ConsumerRecords<String, UserEvent> records = consumer.poll(Duration.ofMillis(1000));

                records.forEach(record -> {
                    UserEvent event = record.value();
                    log.info("수신 → userId={} action={} payload={} timestamp={}",
                            event.getUserId(), event.getAction(), event.getPayload(),
                            event.getTimestamp());
                });
            }
        }
    }

    private static Properties buildProps() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVER);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaProtobufDeserializer.class.getName());
        props.put("schema.registry.url", SCHEMA_REGISTRY);
        // v1 Java 클래스로 역직렬화
        props.put(KafkaProtobufDeserializerConfig.SPECIFIC_PROTOBUF_VALUE_TYPE,
                UserEvent.class.getName());
        return props;
    }
}
