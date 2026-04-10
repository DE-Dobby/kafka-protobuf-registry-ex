package com.example.compat.forward;

import com.example.config.AppConfig;
import com.example.proto.UserEventV2ForwardProto;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema;
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchemaProvider;
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
import java.util.Map;
import java.util.Properties;

/**
 * Forward Compatibility 테스트 - Consumer V2 (ProducerV1보다 먼저 배포)
 *
 * 시작 시 v2 스키마(timestamp 제거)를 Schema Registry에 등록한다.
 * ProducerV1이 여전히 v1 메시지(timestamp 있음)를 보내도 timestamp 필드를 무시하고 정상 수신한다.
 * 이후 ProducerV2가 배포되면 timestamp 없는 메시지도 그대로 수신한다.
 *
 * 시나리오:
 *   1. ForwardCompatProducerV1 + ForwardCompatConsumerV1 실행 (초기 운영)
 *   2. 이 ConsumerV2 배포 → v2 스키마 Schema Registry 등록
 *   3. ProducerV1 계속 동작 → ConsumerV1/V2 모두 정상 수신
 *   4. ForwardCompatProducerV2 배포 → ConsumerV1: timestamp=0, ConsumerV2: 정상
 *
 * 실행: mvn compile exec:java -Dexec.mainClass="com.example.compat.forward.ForwardCompatConsumerV2"
 */
public class ForwardCompatConsumerV2 {

    private static final Logger log = LoggerFactory.getLogger(ForwardCompatConsumerV2.class);

    private static final String TOPIC            = AppConfig.get("kafka.topic");
    private static final String BOOTSTRAP_SERVER = AppConfig.get("kafka.bootstrap.servers");
    private static final String SCHEMA_REGISTRY  = AppConfig.get("schema.registry.url");
    private static final String SUBJECT          = AppConfig.get("schema.subject");
    private static final String GROUP_ID         = AppConfig.get("kafka.consumer.group-id.forward.v2");

    public static void main(String[] args) throws Exception {
        log.info("=== Forward Consumer V2 시작 (신 스키마, timestamp 없음) ===");

        registerV2Schema();

        try (KafkaConsumer<String, UserEventV2ForwardProto.UserEvent> consumer = new KafkaConsumer<>(buildProps())) {
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
                ConsumerRecords<String, UserEventV2ForwardProto.UserEvent> records =
                        consumer.poll(Duration.ofMillis(1000));
                records.forEach(record -> {
                    UserEventV2ForwardProto.UserEvent event = record.value();
                    log.info("[v2 consumer] 수신 → userId={} action={} payload='{}'",
                            event.getUserId(), event.getAction(), event.getPayload());
                });
            }
        }
    }

    private static void registerV2Schema() throws Exception {
        SchemaRegistryClient registry = new CachedSchemaRegistryClient(
                SCHEMA_REGISTRY, 100, List.of(new ProtobufSchemaProvider()), Map.of());
        ProtobufSchema v2Schema = new ProtobufSchema(UserEventV2ForwardProto.UserEvent.getDescriptor());
        if (registry.testCompatibility(SUBJECT, v2Schema)) {
            int id = registry.register(SUBJECT, v2Schema);
            log.info("[Schema Registry] v2 스키마 등록 완료 → schema_id={}", id);
        } else {
            log.info("[Schema Registry] v2 스키마 이미 등록됨");
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
        props.put(KafkaProtobufDeserializerConfig.SPECIFIC_PROTOBUF_VALUE_TYPE,
                UserEventV2ForwardProto.UserEvent.class.getName());
        return props;
    }
}
