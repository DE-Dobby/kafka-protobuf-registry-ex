package com.example.compat;

import com.example.config.AppConfig;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchemaProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Schema Registry에 등록된 subject의 모든 버전을 삭제한다.
 *
 * Backward/Forward 테스트를 반복하다 보면 버전이 계속 쌓이는데,
 * 이 클래스를 실행하면 subject 자체를 삭제해 버전을 초기화할 수 있다.
 * 다음 Producer 실행 시 v1부터 다시 등록된다.
 *
 * 실행: mvn compile exec:java -Dexec.mainClass="com.example.compat.SchemaResetRunner"
 */
public class SchemaResetRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaResetRunner.class);

    private static final String SCHEMA_REGISTRY_URL = AppConfig.get("schema.registry.url");
    private static final String SUBJECT             = AppConfig.get("schema.subject");

    public static void main(String[] args) throws Exception {
        SchemaRegistryClient registry = new CachedSchemaRegistryClient(
                SCHEMA_REGISTRY_URL, 100,
                List.of(new ProtobufSchemaProvider()), Map.of());

        log.info("subject        : {}", SUBJECT);
        log.info("schema registry: {}", SCHEMA_REGISTRY_URL);
        log.info("=".repeat(60));

        try {
            Collection<Integer> versions = registry.getAllVersions(SUBJECT);
            log.info("현재 등록된 버전 수: {}", versions.size());

            // soft delete → permanent delete 순서로 호출해야 실제로 삭제된다
            registry.deleteSubject(SUBJECT);
            registry.deleteSubject(SUBJECT, true);
            log.info("subject 영구 삭제 완료: {}", SUBJECT);
        } catch (Exception e) {
            log.info("subject가 존재하지 않음, 스킵: {}", SUBJECT);
        }
    }
}
