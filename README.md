# Kafka + Protobuf + Schema Registry 예제

Confluent Platform 로컬 환경에서 Protobuf 스키마를 Schema Registry에 등록하고
Kafka Producer/Consumer로 메시지를 주고받는 Maven 샘플 프로젝트입니다.
추후 플링크 프로젝트를 만들어서 CDC 연계할 예정입니다.

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
    │       ├── SchemaEvolutionRunner.java  # subject 초기화 + v1 등록
    │       ├── SchemaResetRunner.java      # subject 버전 삭제
    │       ├── backward/
    │       │   ├── BackwardCompatProducerV1.java
    │       │   ├── BackwardCompatProducerV2.java
    │       │   ├── BackwardCompatConsumerV1.java
    │       │   └── BackwardCompatConsumerV2.java
    │       └── forward/
    │           ├── ForwardCompatProducerV1.java
    │           ├── ForwardCompatProducerV2.java
    │           ├── ForwardCompatConsumerV1.java
    │           └── ForwardCompatConsumerV2.java
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

## 4. Schema 호환성

### Backward Compatibility — 필드를 추가할 때

새 필드(`email`)를 추가하는 상황.

```
1. 초기 운영 상태
   Producer V1 ──→ [kafka] ──→ Consumer V1

2. Producer V2 재배포 (email 추가, Schema Registry에 v2 자동 등록)
   Producer V2 ──→ [kafka] ──→ Consumer V1  (email 필드 모름 → Protobuf가 무시, 정상 동작)

3. Consumer V2 배포 (선택적, email이 필요한 서비스만)
   Producer V2 ──→ [kafka] ──→ Consumer V1  (email 무시, 계속 운영)
                            ──→ Consumer V2  (email 정상 수신)
```

Consumer V1은 v2 메시지를 받아도 모르는 필드를 무시하므로 정상 동작한다.
email이 필요 없는 서비스는 Consumer를 업그레이드하지 않아도 된다.

### Forward Compatibility — 필드를 제거할 때

기존 필드(`timestamp`)를 제거하는 상황. **Consumer를 먼저 v2로 올리고 스키마를 등록한다.**

```
1. 초기 운영 상태
   Producer V1 ──→ [kafka] ──→ Consumer V1

2. Consumer V2 먼저 배포 (Schema Registry에 v2 스키마 등록)
   Producer V1 ──→ [kafka] ──→ Consumer V1  (timestamp 정상 수신)
                            ──→ Consumer V2  (timestamp 무시, 정상 동작)

3. Producer V2 재배포 (timestamp 제거 메시지 전송)
   Producer V2 ──→ [kafka] ──→ Consumer V1  (timestamp = 0, 기본값)
                            ──→ Consumer V2  (timestamp 없음, 정상)
```

Consumer V2가 먼저 배포되어 스키마 호환성을 확보한 뒤 Producer를 교체하므로
Consumer를 한 대씩 순차 재배포하는 동안 서비스 중단이 없다.

---

### 실행

**사전 준비** (테스트 시작 전 한 번 실행 / 반복 시 SchemaResetRunner 먼저)

```bash
mvn compile exec:java -Dexec.mainClass="com.example.compat.SchemaResetRunner"
mvn compile exec:java -Dexec.mainClass="com.example.compat.SchemaEvolutionRunner"
```

**Backward 테스트**

```bash
# 터미널 1: Consumer V1 실행 (초기 운영 상태)
mvn compile exec:java -Dexec.mainClass="com.example.compat.backward.BackwardCompatConsumerV1"

# 터미널 2: Producer V1 실행 (초기 운영 상태)
mvn compile exec:java -Dexec.mainClass="com.example.compat.backward.BackwardCompatProducerV1"

# --- 스키마 변경: Producer V2 재배포 ---
# 터미널 2: Producer V1 중단 → Producer V2 실행 (v2 스키마 자동 등록)
mvn compile exec:java -Dexec.mainClass="com.example.compat.backward.BackwardCompatProducerV2"
# Consumer V1은 중단 없이 계속 수신 (email 무시)

# 터미널 3: Consumer V2 배포 (선택적, email이 필요한 경우)
mvn compile exec:java -Dexec.mainClass="com.example.compat.backward.BackwardCompatConsumerV2"
```

**Forward 테스트**

```bash
# 터미널 1: Consumer V1 실행 (초기 운영 상태)
mvn compile exec:java -Dexec.mainClass="com.example.compat.forward.ForwardCompatConsumerV1"

# 터미널 2: Producer V1 실행 (초기 운영 상태)
mvn compile exec:java -Dexec.mainClass="com.example.compat.forward.ForwardCompatProducerV1"

# --- 스키마 변경: Consumer V2 먼저 배포 (v2 스키마 Schema Registry 등록) ---
# 터미널 3: Consumer V2 배포 → v2 스키마 자동 등록, ConsumerV1/V2 동시 운영 가능
mvn compile exec:java -Dexec.mainClass="com.example.compat.forward.ForwardCompatConsumerV2"
# Producer V1은 중단 없이 계속 전송 (Consumer V1: timestamp 정상, Consumer V2: timestamp 무시)

# --- Producer V2 재배포 (timestamp 제거 메시지 전송) ---
# 터미널 2: Producer V1 중단 → Producer V2 실행
mvn compile exec:java -Dexec.mainClass="com.example.compat.forward.ForwardCompatProducerV2"
# Consumer V1: timestamp=0 (기본값), Consumer V2: 정상 수신
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
