# Schema 호환성 확인

Kafka + Schema Registry 환경에서 Protobuf 스키마가 변경될 때
실제 데이터가 어떻게 처리되는지, Schema Registry가 어떤 변경을 허용/거부하는지 확인한다.

---

## 핵심 개념

| 호환성 | 의미 | 안전한 배포 순서 |
|---|---|---|
| **Backward** | 신규 스키마(v2)로 구 데이터(v1)를 읽을 수 있음 | Consumer 먼저 배포 |
| **Forward** | 구 스키마(v1)로 신규 데이터(v2)를 읽을 수 있음 | Producer 먼저 배포 |
| **Break** | 둘 다 안 됨 | 배포 불가 (롤링 불가) |

---

## 사전 조건

```bash
# Confluent Platform 서비스 확인
confluent local services status
# Schema Registry is [UP] ← 확인

# 프로젝트 루트에서 실행
cd /Users/kds/workspace/kafka-protobuf-registry-ex
```

---

## Step 1. v1 기준 스키마 등록

subject를 초기화하고 v1 기준 스키마만 등록한다.
v2 스키마는 각 테스트(Consumer/Producer)가 실행 시점에 직접 등록한다.

```bash
mvn compile exec:java -Dexec.mainClass="com.example.compat.SchemaEvolutionRunner"
```

예상 출력:
```
subject 초기화 완료: dobby-events-value
[v1 기준 스키마] 등록 완료 → schema_id=1
등록된 버전 수: 1
다음 단계: BackwardCompatConsumer 또는 ForwardCompatProducer 실행
```

---

## Step 2. 실제 Kafka 메시지로 호환성 확인

### Backward 테스트

**시나리오:** 새 Consumer(v2 스키마 인식)를 먼저 배포 → 기존 Producer(v1)가 계속 전송
- Consumer 시작 시 v2 스키마(email 추가)를 Schema Registry에 등록
- Producer는 v1으로 전송 → v1 데이터에 없는 `email` 필드는 기본값 `""` 으로 채워진다

```bash
# 터미널 1 - Consumer 먼저 기동 (v2 스키마 등록 후 대기)
mvn compile exec:java -Dexec.mainClass="com.example.compat.backward.BackwardCompatConsumer"

# 터미널 2 - Producer 실행
mvn compile exec:java -Dexec.mainClass="com.example.compat.backward.BackwardCompatProducer"
```

Consumer 출력 예시:
```
[Schema Registry] v2 스키마 등록 완료 → schema_id=2
수신 → userId=user-1 action=LOGIN payload=payload-1 timestamp=1234567890 email=''
```

---

### Forward 테스트

**시나리오:** 새 Producer(v2 스키마)를 먼저 배포 → 기존 Consumer(v1)가 계속 수신
- Producer 전송 시 v2 스키마(timestamp 제거)를 Schema Registry에 자동 등록
- Consumer는 v1으로 수신 → v2 데이터에 없는 `timestamp` 필드는 기본값 `0` 으로 채워진다

```bash
# 터미널 1 - Consumer 먼저 기동 (v1 스키마로 대기)
mvn compile exec:java -Dexec.mainClass="com.example.compat.forward.ForwardCompatConsumer"

# 터미널 2 - Producer 실행 (v2 스키마 자동 등록 후 전송)
mvn compile exec:java -Dexec.mainClass="com.example.compat.forward.ForwardCompatProducer"
```

Consumer 출력 예시:
```
수신 → userId=user-1 action=PURCHASE payload=item-1 timestamp=0
```

---

## 구현 설계

### proto 파일 구조

Schema Registry는 **메시지 이름**을 기준으로 호환성을 비교한다.
v2 파일들도 메시지 이름을 `UserEvent`로 통일해야 v1과 비교가 가능하다.

같은 proto package 안에 동일한 메시지 이름은 중복될 수 없으므로
v2 파일들은 별도 proto package를 사용하고, Java 클래스 충돌은 `java_outer_classname`으로 해결한다.

| 파일 | proto package | Java 클래스 |
|---|---|---|
| `user_event.proto` | `com.example` | `UserEvent` |
| `user_event_v2_backward.proto` | `com.example.v2backward` | `UserEventV2BackwardProto.UserEvent` |
| `user_event_v2_forward.proto` | `com.example.v2forward` | `UserEventV2ForwardProto.UserEvent` |
| `user_event_v2_break.proto` | `com.example.v2break` | `UserEventV2BreakProto.UserEvent` |

### 스키마 초기화

`SchemaEvolutionRunner`만 실행 시 subject를 초기화한다.
Backward/Forward Producer는 이미 등록된 스키마를 그대로 사용한다.

```
SchemaEvolutionRunner   → subject 초기화 + 스키마 등록
BackwardCompatProducer  → 등록된 v1 스키마로 전송
ForwardCompatProducer   → 등록된 v2 스키마로 전송
```

---

## Schema Registry 확인

```bash
# 등록된 버전 목록
curl http://localhost:8081/subjects/dobby-events-value/versions

# 특정 버전 스키마 조회
curl http://localhost:8081/subjects/dobby-events-value/versions/1
```
