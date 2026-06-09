# 프로젝트 개요
**[Trillion](https://github.com/nhnacademy-be12-trillion)** 온라인 서점 서비스의 **주문(Order)**, **결제(Payment)**, **장바구니(Cart)** 도메인을 담당하는 마이크로서비스.

### **📱 주문**
- 담당자 : 강병호
    - Orchestration Saga 패턴 기반의 주문 생성 및 취소, 환불 분산 트랜잭션 구현
    - 스케줄러를 이용한 중단된 트랜잭션 복구 시스템 구축
    - 멱등성 키 설계를 통한 중복 요청 방어 및 데이터 정합성 확보
    - 배송 정책 및 포장지 관리 등 주문 관련 부가 도메인 기능 구현

### **🛒 장바구니**
- 담당자 : 이승현
    - 회원/비회원 장바구니 CRUD(상품 추가, 수량 변경, 삭제) 기능 구현
    - 성능을 위해 Redis를 활용하여 장바구니 기능을 구축하고, 회원 데이터는 DB에 영구 저장하여 안정성 확보
    - 모든 장바구니 연산을 Redis(In-Memory)에서 처리하여 응답 지연(Latency) 최소화
    - Write-Back(지연 쓰기) 패턴을 적용, 스케줄러를 통해 Redis 데이터를 DB에 비동기 동기화하여 영속성 보장
    - 데이터 생명주기 관리 및 리소스 최적화를 위해 담은 지 90일 경과 상품 자동 삭제 스케줄러 구현

### **💳 결제**
- 담당자 : 최지훈
    - TossPayments API와 연동.
    - 결제 승인/실패 프로세스
  
---

## 목차
1. [주문 도메인](#주문-도메인)
    *   [도메인 구조](#도메인-구조)
    *   [기술적 도전 과제 및 해결 방안](#기술적-도전-과제-및-해결-방안)
        *   [1. Saga Pattern을 이용한 분산 트랜잭션](#1-saga-pattern을-이용한-분산-트랜잭션-orchestration)
        *   [2. 데이터 정합성 보장 (Scheduling)](#2-데이터-정합성-보장---자동-복구-scheduling)
        *   [3. 장애 격리 (Circuit Breaker)](#3-장애-격리-fault-tolerance)
2. [사용 기술](#사용-기술)
3. [문서 (Wiki)](#문서)

---

## 주문 도메인

### 도메인 구조

```text
com.nhnacademy.order
├── order       # 주문 생성, 조회, 상태 관리 핵심 로직
├── orderitem   # 주문 상세 상품 정보 및 개별 상태 관리
├── ordercoupon # 주문 시 사용된 쿠폰 매핑 및 이력 관리
├── ordersaga   # 사가 패턴 기반 분산 트랜잭션 오케스트레이터 (Creation, Cancellation, Refund)
├── packaging   # 포장지 종류 및 가격 정책 관리
├── delivery    # 배송 정책(배송비 산정) 관리
├── point       # 주문 프로세스 내 포인트 적립 로직
├── scheduler   # 중단된 Saga 트랜잭션 복구(보상/재시도)를 통한 데이터 정합성 관리
├── client      # 타 마이크로서비스(Book, Member, Coupon)와의 통신 모듈
└── common      # 공통 예외 처리, AOP(권한 체크), Argument Resolver 등 공통 모듈
```

---

### 기술적 도전 과제 및 해결 방안

#### 1. Saga Pattern을 이용한 분산 트랜잭션 (Orchestration)

*   **문제점:** 각 마이크로서비스가 독립된 데이터베이스를 소유(Database per Service)하므로, 단일 트랜잭션(`@Transactional`)만으로는 여러 서비스에 걸친 데이터 정합성 보장 불가
*   **해결책:** `OrderCreateOrchestrator`가 중앙에서 `재고 차감 → 쿠폰 적용 → 포인트 사용` 흐름을 제어하고, 실패 시 역순으로 보상 트랜잭션을 수행
*   **추가 고려사항:**
    *   **5xx 불확실성**: 외부 서비스가 요청을 처리한 후 응답 직전에 네트워크가 끊기면 성공 여부를 알 수 없음. 이 경우 무조건 보상 트랜잭션을 전송하고, 수신 서비스가 **멱등성(Idempotency)**으로 중복 처리를 방어하도록 설계
    *   **선기록 전략**: 외부 API 호출 직전에 `STOCK_DECREASING` 같은 진행 중 상태를 먼저 저장해, 서버 장애 시 스케줄러가 요청 송신 여부를 판단하고 안전하게 보상 트랜잭션을 실행할 수 있도록 설계
    *   **복구 전략 이원화**: 주문 생성은 하나라도 실패 시 전체 롤백(All-or-Nothing), 주문 취소는 사용자 의사가 확정된 상태이므로 성공할 때까지 재시도로 구분

#### 2. 데이터 정합성 보장 - 자동 복구 (Scheduling)

*   **문제점:** Saga 실행 중 서버 장애 발생 시 트랜잭션이 중단된 채로 남거나, 모든 단계가 성공했음에도 주문 도메인 상태 반영(`bridged`) 직전에 장애가 나면 사가와 도메인 간 불일치 발생
*   **해결책:** **Reconciliation Scheduler**를 5분 주기로 운영하여 미완료 사가를 감지하고 복구 정책(롤백/재시도/Bridge)을 적용
    *   생성 사가 중단 → 롤백, 취소·환불 사가 중단 → 재시도, 도메인 미반영 → 재동기화
    *   결제 없이 1시간 이상 대기 중인 `PENDING` 주문은 선점된 재고·쿠폰·포인트를 강제 회수
*   **분산 락:** 이중화 환경에서 중복 실행을 막기 위해 Redis 없이 RDB 레코드를 활용하는 **ShedLock**을 적용

#### 3. 장애 격리 (Fault Tolerance)

*   **문제점:** OpenFeign 기반 동기 통신은 외부 서비스 지연 시 스레드가 블로킹되고, 주문 서비스 전체의 장애로 전파될 위험이 존재
*   **해결책:** **Resilience4j Circuit Breaker**를 도입하여 실패율이 임계치를 초과하면 즉시 요청을 차단(Open)하고 빠른 실패(Fail-Fast)를 수행

---

### 사용 기술

| Category | Technology | Version | Description |
| --- | --- | --- |---|
| **Language** | Java | 21 | LTS 버전 활용 |
| **Framework** | Spring Boot | 3.5.7 | |
| **Database** | MySQL | 8.0+ | 메인 DB |
| **ORM** | Spring Data JPA | | |
| **Communication** | Spring Cloud OpenFeign | | 선언적 HTTP 클라이언트 |
| **Resilience** | Resilience4j | | 재시도, 서킷 브레이커 |
| **Scheduling** | Spring Scheduler | | 주기적 데이터 정합성 검사 |
| **Locking** | ShedLock | 7.2.1 | 스케줄러 분산 락 |
| **Build** | Maven | | |

---

### 문서
더 자세한 기술적 의사결정 과정과 구현 상세는 **docs/wiki** 디렉토리에서 확인 가능함.

*   **[Saga Pattern] [분산 트랜잭션 구현과 5xx 에러 처리 전략](./docs/wiki/01-saga-pattern.md)**
    *   오케스트레이션 Saga를 통한 서비스 간 정합성 보장 및 불확실한 상태에서의 멱등성 복구 로직
*   **[Scheduling] [데이터 정합성 복구를 위한 스케줄링 전략](./docs/wiki/02-scheduling.md)**
    *   Spring Scheduler를 이용한 미완료 트랜잭션 탐지 및 ShedLock을 이용한 분산 락 적용

---
