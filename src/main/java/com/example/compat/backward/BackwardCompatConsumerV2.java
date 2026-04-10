package com.example.compat.backward;

import com.example.config.AppConfig;
import com.example.proto.UserEventV2BackwardProto;
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
 * Backward Compatibility 테스트 - Consumer V2 (신 스키마, 재배포 후)
 *
 * v2 스키마(email 추가)로 구독한다.
 * email이 필요한 서비스가 선택적으로 v2로 업그레이드한 상황을 시뮬레이션한다.
 *
 * BackwardCompatConsumerV1과 동시에 실행하면 롤링 재배포를 확인할 수 있다.
 *
 * 실행: mvn compile exec:java -Dexec.mainClass="com.example.compat.backward.BackwardCompatConsumerV2"
 */
public class BackwardCompatConsumerV2 {

    private static final Logger log = LoggerFactory.getLogger(BackwardCompatConsumerV2.class);

    private static final String TOPIC            = AppConfig.get("kafka.topic");
    private static final String BOOTSTRAP_SERVER = AppConfig.get("kafka.bootstrap.servers");
    private static final String SCHEMA_REGISTRY  = AppConfig.get("schema.registry.url");
    private static final String SUBJECT          = AppConfig.get("schema.subject");
    private static final String GROUP_ID         = AppConfig.get("kafka.consumer.group-id.backward.v2");

    public static void main(String[] args) throws Exception {
        log.info("=== Backward Consumer V2 시작 (신 스키마, email 있음) ===");

        registerV2Schema();

        try (KafkaConsumer<String, UserEventV2BackwardProto.UserEvent> consumer = new KafkaConsumer<>(buildProps())) {
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
                ConsumerRecords<String, UserEventV2BackwardProto.UserEvent> records =
                        consumer.poll(Duration.ofMillis(1000));
                records.forEach(record -> {
                    UserEventV2BackwardProto.UserEvent event = record.value();
                    log.info("[v2 consumer] 수신 → userId={} action={} timestamp={} email='{}'",
                            event.getUserId(), event.getAction(), event.getTimestamp(), event.getEmail());
                });
            }
        }
    }

    private static void registerV2Schema() throws Exception {
        SchemaRegistryClient registry = new CachedSchemaRegistryClient(
                SCHEMA_REGISTRY, 100, List.of(new ProtobufSchemaProvider()), Map.of());
        ProtobufSchema v2Schema = new ProtobufSchema(UserEventV2BackwardProto.UserEvent.getDescriptor());
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
                UserEventV2BackwardProto.UserEvent.class.getName());
        return props;
    }
}
