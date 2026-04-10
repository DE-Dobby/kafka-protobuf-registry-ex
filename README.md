# Kafka + Protobuf + Schema Registry 예제

Confluent Platform 로컬 환경에서 Protobuf 스키마를 Schema Registry에 등록하고,
Kafka Producer/Consumer로 메시지를 주고받는 Maven 샘플 프로젝트입니다.

---

## 프로젝트 구조

```
kafka-protobuf-registry-ex/
├── pom.xml
└── src/main/
    ├── proto/
    │   └── user_event.proto          # UserEvent 메시지 정의
    ├── java/com/example/
    │   ├── producer/
    │   │   └── UserEventProducer.java
    │   └── consumer/
    │       └── UserEventConsumer.java
    └── resources/
        └── logback.xml
```

### 주요 의존성

| 라이브러리 | 버전 |
|---|---|
| kafka-clients | 3.7.0 |
| kafka-protobuf-serializer (Confluent) | 7.6.0 |
| protobuf-java | 3.25.3 |

---

## 1. 환경 설정 (Confluent Platform 로컬)

### Confluent CLI 설치

```bash
brew install confluentinc/tap/cli
confluent version
```

### Confluent Platform 설치

```bash
# 다운로드 (~1GB)
curl -O https://packages.confluent.io/archive/7.6/confluent-7.6.0.tar.gz
tar -xzf confluent-7.6.0.tar.gz
sudo mv confluent-7.6.0 /opt/confluent
rm confluent-7.6.0.tar.gz
```

### 환경변수 설정

```bash
echo 'export CONFLUENT_HOME="/opt/confluent"' >> ~/.zshrc
echo 'export PATH="$CONFLUENT_HOME/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

---

## 2. 서비스 시작

```bash
# ZooKeeper → Kafka → Schema Registry 순으로 자동 기동
confluent local services start
```

정상 출력:

```
ZooKeeper is [UP]
Kafka is [UP]
Schema Registry is [UP]
```

### 기본 포트

| 서비스 | 포트 |
|---|---|
| ZooKeeper | `2181` |
| Kafka Broker | `9092` |
| Schema Registry | `8081` |

---

## 3. 토픽 생성

```bash
kafka-topics --create \
  --bootstrap-server localhost:9092 \
  --topic dobby-events \
  --partitions 3 \
  --replication-factor 1

# 확인
kafka-topics --list --bootstrap-server localhost:9092
```

---

## 4. 빌드 및 실행

```bash
# Producer 실행 (터미널 1)
mvn compile exec:java -Dexec.mainClass="com.example.producer.UserEventProducer"

# Consumer 실행 (터미널 2)
mvn compile exec:java -Dexec.mainClass="com.example.consumer.UserEventConsumer"
```

### 동작 흐름

```
Producer
  → UserEvent 직렬화
  → Schema Registry에 스키마 자동 등록
  → Kafka 토픽(dobby-events)으로 전송

Consumer
  → Kafka 토픽 구독
  → Schema Registry에서 스키마 조회
  → UserEvent 역직렬화 후 출력
```

---

## 5. Schema Registry 확인

```bash
# 등록된 스키마 목록
curl http://localhost:8081/subjects

# 특정 스키마 상세 조회
curl http://localhost:8081/subjects/dobby-events-value/versions/latest
```

---

## 관리 명령어

```bash
# 서비스 상태 확인
confluent local services status

# 전체 중지
confluent local services stop

# 특정 서비스만 제어
confluent local services kafka start
confluent local services schema-registry stop

# 로그 확인
confluent local services kafka log
confluent local services schema-registry log

# 데이터 포함 완전 초기화
confluent local destroy
```
