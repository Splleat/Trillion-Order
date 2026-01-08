# 프로젝트 개요
이 저장소는 서점 서비스의 핵심인 **주문(Order)**, **결제(Payment)**, **장바구니(Cart)** 도메인을 담당하는 마이크로서비스임.

---

## 목차
1. [주문 도메인](#주문-도메인)
    *   [도메인 구조](#도메인-구조)
    *   [기술적 도전 과제 및 해결 방안](#기술적-도전-과제-및-해결-방안)
        *   [1. Saga Pattern을 이용한 분산 트랜잭션](#1-saga-pattern을-이용한-분산-트랜잭션-orchestration)
        *   [2. 데이터 정합성 보장 (Scheduling)](#2-데이터-정합성-보장---동시성-제어-및-자동-복구concurrency--scheduling)
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

#### 주요 도전 과제
```
1. 분산 트랜잭션 처리: Saga Pattern을 이용해 '재고 차감 → 쿠폰 적용 → 포인트 사용'을 분산 처리.
2. 데이터 정합성 보장: 서버 장애 등으로 중단된 트랜잭션을 감지하고 자동 복구하는 스케줄러 도입.
3. 장애 격리: Resilience4j Circuit Breaker로 타 서비스 장애에 의한 장애 전파를 방지.
```

#### 해결 방안

#### 1. Saga Pattern을 이용한 분산 트랜잭션 (Orchestration)
주문 생성 시 `재고 감소(도서 서비스) -> 쿠폰 사용(쿠폰 서비스) -> 포인트 사용(회원 서비스)`으로 이어지는 분산 환경에서의 트랜잭션을 보장하기 위해 Saga Pattern (Orchestration 방식)을 도입함.

*   **문제점:** 각 마이크로서비스가 독립된 데이터베이스를 소유(Database per Service)하므로, 단일 트랜잭션(ACID)만으로는 여러 서비스에 걸친 데이터 정합성을 보장할 수 없음.
*   **해결책:** `OrderCreateOrchestrator`가 중앙에서 트랜잭션 흐름을 제어하고, 실패 시 보상 트랜잭션(Compensating Transaction)을 통해 데이터를 원복함.
*   **흐름:**
    1.  **재고 차감** (Book Service)
    2.  **쿠폰 적용** (Coupon Service)
    3.  **포인트 사용** (Member Service)
    4.  *(실패 시 역순으로 Rollback 수행)*

    ```mermaid
    sequenceDiagram
    autonumber
    participant Order as 주문 서비스
    box 외부 서비스
        participant Book as 도서 서비스
        participant Coupon as 쿠폰 서비스
        participant Member as 회원 서비스
    end

    Note over Order, Member: [Step 1] 재고 처리
    rect rgba(0, 255, 0, 0.1)
        Order->>Book: 재고 차감 요청
    end

    alt 재고 차감 실패
        rect rgba(255, 0, 0, 0.1)
            Book-->>Order: 에러 응답 (4xx/5xx)
            Order->>Book: 재고 복구
            Note over Order: 주문 실패
        end
    else
        Note over Order, Member: [Step 2] 쿠폰 처리
        rect rgba(0, 255, 0, 0.1)
            Order->>Coupon: 쿠폰 적용 요청
        end

        alt 쿠폰 적용 실패
            rect rgba(255, 0, 0, 0.1)
                Coupon-->>Order: 에러 응답 (4xx/5xx)
                Order->>Coupon: 쿠폰 취소
                Order->>Book: 재고 복구
                Note over Order: 주문 실패
            end
        else
            Note over Order, Member: [Step 3] 포인트 처리
            rect rgba(0, 255, 0, 0.1)
                Order->>Member: 포인트 사용 요청
            end

            alt 포인트 사용 실패
                rect rgba(255, 0, 0, 0.1)
                    Member-->>Order: 에러 응답 (4xx/5xx)
                    Order->>Member: 포인트 환불
                    Order->>Coupon: 쿠폰 취소
                    Order->>Book: 재고 복구
                    Note over Order: 주문 실패
                end
            else
                Note over Order: 주문 생성 완료
            end
        end
    end
    ```

#### 2. 데이터 정합성 보장 - 동시성 제어 및 자동 복구(Concurrency & Scheduling)
*   **문제점:** Saga 트랜잭션 진행 중 서버 장애 발생 시, '재고는 차감되었으나 주문은 완료되지 않은 상태'로 남게 되어 데이터 불일치가 발생함. 또한, 다중 인스턴스 환경에서 스케줄러가 중복 실행될 경우 동일 주문에 대해 중복 보상 처리가 수행될 위험이 있음.
*   **해결책:** **Reconciliation Scheduler**를 운영하여 중단된 트랜잭션을 주기적으로 감지 및 자동 복구(보상/재시도)함. 동시에 **ShedLock** 기반의 분산 락을 적용하여 스케줄러의 유일한 실행을 보장함으로써 데이터 정합성을 유지함.

#### 3. 장애 격리 (Fault Tolerance)
*   **문제점:** HTTP 기반의 동기 통신(Feign Client)은 타 서비스의 장애가 발생했을 때 응답 지연(Blocking)이 발생하고, 이것이 주문 서비스 전체의 장애로 전파(Cascading Failure)될 위험이 있음.
*   **해결책:** **Resilience4j Circuit Breaker**를 도입하여, 일정 비율 이상 실패가 감지되면 즉시 요청을 차단(Open)하고 빠른 실패(Fail-Fast)를 수행하여 시스템 전체의 안정성을 보장함.

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

*   **[Saga Pattern] [분산 트랜잭션 구현과 5xx 에러 처리 전략](./docs/wiki/Saga-Pattern.md)**
    *   오케스트레이션을 통한 서비스 간 정합성 보장 및 불확실한 상태에서의 멱등성 복구 로직 상세 설명.
*   **[Scheduling] [데이터 정합성 복구를 위한 스케줄링 전략](./docs/wiki/Scheduling.md)**
    *   Reconciliation Scheduler를 이용한 미완료 트랜잭션 탐지 및 ShedLock을 이용한 분산 락 적용 사례.
*   **[Resilience4j] [장애 전파 차단과 회복력 확보 전략](./docs/wiki/Resilience4j.md)**
    *   Resilience4j Circuit Breaker 설정 기준 및 동기 통신 환경에서의 장애 전파 방지 전략.

---
