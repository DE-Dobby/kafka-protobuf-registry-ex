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
 * Forward Compatibility 테스트 - Consumer V1 (구 스키마, 재배포 전)
 *
 * v1 스키마(timestamp 있음)로 구독한다.
 * Producer가 이미 v2(timestamp 제거)로 배포된 상태이므로,
 * v2 메시지를 수신하면 timestamp는 기본값 0으로 채워진다.
 *
 * 시나리오: ForwardCompatConsumerV2와 동시에 실행해 롤링 재배포를 시뮬레이션한다.
 *
 * 실행: mvn compile exec:java -Dexec.mainClass="com.example.compat.forward.ForwardCompatConsumerV1"
 */
public class ForwardCompatConsumerV1 {

    private static final Logger log = LoggerFactory.getLogger(ForwardCompatConsumerV1.class);

    private static final String TOPIC            = AppConfig.get("kafka.topic");
    private static final String BOOTSTRAP_SERVER = AppConfig.get("kafka.bootstrap.servers");
    private static final String SCHEMA_REGISTRY  = AppConfig.get("schema.registry.url");
    private static final String GROUP_ID         = AppConfig.get("kafka.consumer.group-id.forward.v1");

    public static void main(String[] args) {
        log.info("=== Forward Consumer V1 시작 (구 스키마, timestamp 있음) ===");
        log.info("v2 메시지 수신 시 timestamp는 기본값 0으로 채워진다.");

        try (KafkaConsumer<String, UserEvent> consumer = new KafkaConsumer<>(buildProps())) {
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
                    log.info("[v1 consumer] 수신 → userId={} action={} payload='{}' timestamp={}",
                            event.getUserId(), event.getAction(), event.getPayload(), event.getTimestamp());
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
        props.put(KafkaProtobufDeserializerConfig.SPECIFIC_PROTOBUF_VALUE_TYPE, UserEvent.class.getName());
        return props;
    }
}
