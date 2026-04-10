# Kafka + Protobuf + Schema Registry 예제

Confluent Platform 로컬 환경에서 Protobuf 스키마를 Schema Registry에 등록하고
Kafka Producer/Consumer로 메시지를 주고받는 Maven 샘플 프로젝트입니다.


TODO : 추후 좀 더 분리해서 Backward/Forward 토픽별로 나눠서 스키마 변경하는거에 대해 다시 정리 예정...
---

## 프로젝트 구조

```
kafka-protobuf-registry-ex/
├── pom.xml
└── src/main/
    ├── proto/
    │   ├── user_event.proto              # v1 기준 스키마
    │   ├── user_event_v2_backward.proto  # email 필드 추가 (Backward 테스트용)
    │   └── user_event_v2_forward.proto   # timestamp 필드 제거 (Forward 테스트용)
    ├── java/com/example/
    │   ├── config/
    │   │   └── AppConfig.java            # application.properties 로드
    │   ├── producer/
    │   │   └── UserEventProducer.java    # 기본 Producer
    │   ├── consumer/
    │   │   └── UserEventConsumer.java    # 기본 Consumer
    │   └── compat/
    │       ├── SchemaEvolutionRunner.java # subject 초기화 + v1 등록
    │       ├── SchemaResetRunner.java     # subject 버전 삭제
    │       ├── backward/
    │       │   ├── BackwardCompatConsumer.java
    │       │   └── BackwardCompatProducer.java
    │       └── forward/
    │           ├── ForwardCompatConsumer.java
    │           └── ForwardCompatProducer.java
    └── resources/
        ├── application.properties
        └── logback.xml
```

### 주요 의존성

| 라이브러리 | 버전 |
|---|---|
| kafka-clients | 3.7.0 |
| kafka-protobuf-serializer (Confluent) | 7.6.0 |
| protobuf-java | 3.25.3 |

---

## 1. 환경 설정

### Confluent Platform 설치

```bash
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

## 2. 서비스 시작 및 토픽 생성

```bash
# ZooKeeper → Kafka → Schema Registry 순으로 자동 기동
confluent local services start

# 토픽 생성
kafka-topics --create \
  --bootstrap-server localhost:9092 \
  --topic dobby-events \
  --partitions 3 \
  --replication-factor 1
```

---

## 3. 기본 Producer / Consumer 실행

Protobuf 직렬화와 Schema Registry 자동 등록 동작을 확인한다.

```bash
# 터미널 1 - Producer
mvn compile exec:java -Dexec.mainClass="com.example.producer.UserEventProducer"

# 터미널 2 - Consumer
mvn compile exec:java -Dexec.mainClass="com.example.consumer.UserEventConsumer"
```

---

## 4. Schema 호환성 테스트

### Backward vs Forward

| | Backward | Forward |
|---|---|---|
| **의미** | 새 스키마(v2)로 구 데이터(v1)를 읽을 수 있다 | 구 스키마(v1)로 새 데이터(v2)를 읽을 수 있다 |
| **배포 순서** | Consumer 먼저 → Producer 나중 | Producer 먼저 → Consumer 나중 |
| **스키마 변경** | 필드 추가 | 필드 제거 |
| **쓰는 상황** | 새 필드를 추가했는데 구 Producer가 아직 살아있을 때 | 필드를 제거했는데 구 Consumer가 아직 살아있을 때 |

자세한 내용은 [schema-compatibility.md](./schema-compatibility.md)를 참고하세요.

---

### 실행 순서

**Step 1 — v1 기준 스키마 등록** (테스트 시작 전 한 번 실행)

```bash
mvn compile exec:java -Dexec.mainClass="com.example.compat.SchemaEvolutionRunner"
```

**Step 2a — Backward 테스트** (Consumer가 v2 등록 → Producer는 v1 전송)

```bash
# 터미널 1: Consumer 먼저 시작 (v2 스키마 등록 후 대기)
mvn compile exec:java -Dexec.mainClass="com.example.compat.backward.BackwardCompatConsumer"

# 터미널 2: Producer 실행
mvn compile exec:java -Dexec.mainClass="com.example.compat.backward.BackwardCompatProducer"
```

**Step 2b — Forward 테스트** (Producer가 v2 등록+전송 → Consumer는 v1 수신)

```bash
# 터미널 1: Consumer 먼저 시작
mvn compile exec:java -Dexec.mainClass="com.example.compat.forward.ForwardCompatConsumer"

# 터미널 2: Producer 실행 (v2 스키마 자동 등록 후 전송)
mvn compile exec:java -Dexec.mainClass="com.example.compat.forward.ForwardCompatProducer"
```

**스키마 초기화** (반복 테스트 시 Step 1 전에 실행)

```bash
mvn compile exec:java -Dexec.mainClass="com.example.compat.SchemaResetRunner"
```

---

## 5. Schema Registry 확인

```bash
# 등록된 subject 목록
curl http://localhost:8081/subjects

# 버전 목록
curl http://localhost:8081/subjects/dobby-events-value/versions

# 최신 버전 스키마 조회
curl http://localhost:8081/subjects/dobby-events-value/versions/latest
```

---

## 관리 명령어

```bash
# 서비스 상태 확인
confluent local services status

# 전체 중지
confluent local services stop

# 로그 확인
confluent local services kafka log
confluent local services schema-registry log

# 데이터 포함 완전 초기화
confluent local destroy
```
