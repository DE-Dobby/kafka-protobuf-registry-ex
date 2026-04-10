package com.example.compat;

import com.example.config.AppConfig;
import com.example.proto.UserEvent;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema;
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchemaProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * subject를 초기화하고 v1 기준 스키마만 등록한다.
 *
 * Backward/Forward 테스트 전에 한 번 실행해 깨끗한 시작점을 만든다.
 * - v2 스키마 등록은 각 테스트(Consumer/Producer)가 직접 담당한다.
 * - 버전만 초기화할 때는 SchemaResetRunner를 사용한다.
 *
 * 실행: mvn compile exec:java -Dexec.mainClass="com.example.compat.SchemaEvolutionRunner"
 */
public class SchemaEvolutionRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaEvolutionRunner.class);

    private static final String SCHEMA_REGISTRY_URL = AppConfig.get("schema.registry.url");
    private static final String SUBJECT             = AppConfig.get("schema.subject");

    public static void main(String[] args) throws Exception {
        SchemaRegistryClient registry = new CachedSchemaRegistryClient(
                SCHEMA_REGISTRY_URL, 100,
                List.of(new ProtobufSchemaProvider()), Map.of());

        try {
            registry.deleteSubject(SUBJECT);
            registry.deleteSubject(SUBJECT, true);
            log.info("subject 초기화 완료: {}", SUBJECT);
        } catch (Exception e) {
            log.info("subject 없음, 초기화 스킵: {}", SUBJECT);
        }

        log.info("subject        : {}", SUBJECT);
        log.info("schema registry: {}", SCHEMA_REGISTRY_URL);
        log.info("=".repeat(60));

        int id = registry.register(SUBJECT, new ProtobufSchema(UserEvent.getDescriptor()));
        log.info("[v1 기준 스키마] 등록 완료 → schema_id={}", id);
        log.info("등록된 버전 수: {}", registry.getAllVersions(SUBJECT).size());
        log.info("=".repeat(60));
        log.info("다음 단계: BackwardCompatConsumer 또는 ForwardCompatProducer 실행");
    }
}
