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
 * Backward Compatibility 테스트 - Consumer
 *
 * 시작 시 v2 스키마(email 추가)를 Schema Registry에 등록한 뒤 구독을 시작한다.
 * 실제 배포 시나리오에서 새 Consumer가 먼저 배포되어 v2 스키마를 등록하는 흐름과 동일하다.
 *
 * BackwardCompatProducer가 v1으로 보낸 메시지를 v2로 수신하면 email은 기본값 "" 로 채워진다.
 *
 * 실행 순서:
 *   터미널 1: mvn compile exec:java -Dexec.mainClass="com.example.compat.backward.BackwardCompatConsumer"
 *   터미널 2: mvn compile exec:java -Dexec.mainClass="com.example.compat.backward.BackwardCompatProducer"
 */
public class BackwardCompatConsumer {

    private static final Logger log = LoggerFactory.getLogger(BackwardCompatConsumer.class);

    private static final String TOPIC            = AppConfig.get("kafka.topic");
    private static final String BOOTSTRAP_SERVER = AppConfig.get("kafka.bootstrap.servers");
    private static final String SCHEMA_REGISTRY  = AppConfig.get("schema.registry.url");
    private static final String SUBJECT          = AppConfig.get("schema.subject");
    private static final String GROUP_ID         = AppConfig.get("kafka.consumer.group-id.backward");

    public static void main(String[] args) throws Exception {
        log.info("=== Backward Compat Consumer 시작 ===");
        log.info("topic: {}, group: {}", TOPIC, GROUP_ID);

        // 새 Consumer가 먼저 배포되면서 v2 스키마를 Schema Registry에 등록한다
        registerV2Schema();

        log.info("v1 데이터 수신 시 email 필드는 기본값 '' 으로 채워짐");
        log.info("BackwardCompatProducer를 실행하세요...");

        try (KafkaConsumer<String, UserEventV2BackwardProto.UserEvent> consumer = new KafkaConsumer<>(buildProps())) {
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
                ConsumerRecords<String, UserEventV2BackwardProto.UserEvent> records =
                        consumer.poll(Duration.ofMillis(1000));

                records.forEach(record -> {
                    UserEventV2BackwardProto.UserEvent event = record.value();
                    log.info("수신 → userId={} action={} payload={} timestamp={} email='{}'",
                            event.getUserId(), event.getAction(), event.getPayload(),
                            event.getTimestamp(), event.getEmail());
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
            log.warn("[Schema Registry] v2 스키마가 호환되지 않음 → 등록 스킵");
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
        // v2 Java 클래스로 역직렬화
        props.put(KafkaProtobufDeserializerConfig.SPECIFIC_PROTOBUF_VALUE_TYPE,
                UserEventV2BackwardProto.UserEvent.class.getName());
        return props;
    }
}
