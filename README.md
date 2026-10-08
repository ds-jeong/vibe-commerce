# E-Commerce Service

<aside>
💡AI를 활용한 바이브코딩 방식으로 이커머스 서비스를 설계하고 구현한 전체 개발 과정 기록입니다. 각 주제는 화살표를 클릭해 펼쳐볼 수 있습니다.
접속 url(render 프리티어 정책으로 1~2정도 로딩) : https://vibe-commerce-frontend-ed8h.onrender.com/
</aside>

## 프로젝트 개요

### 1.1 프로젝트 목적

VibeCommerce는 실제 운영되는 이커머스 서비스의 주요 비즈니스 흐름을 하나의 서비스 안에서 연결하는 것을 목표로 개발한 프로젝트다.

```
상품 → 회원 / 비회원 → 주문 → 결제 → 배송 → 구매 이력
→ 취소 / 환불 → 정산 → 관리자 통계 → Excel 다운로드
```

단순한 상품 조회와 장바구니 구현을 넘어, 주문·결제 원천 데이터를 수수료·할인·환불 데이터로 가공하고 정산서와 관리자 대시보드까지 연결하는 것을 목표로 했다.

### 1.2 기술 스택

**Backend**

- Java 17
- Spring Boot 3.x
- Spring Data JPA
- Querydsl
- Spring Security
- Gradle
- PostgreSQL
- Apache POI

**Frontend**

- React
- Vite
- Node.js
- Axios
- React Router
- Tailwind CSS
- Recharts

**개발 환경**

로컬에서는 Docker로 PostgreSQL 16을 실행하고, 배포 환경에서는 Cloud PostgreSQL 사용을 고려했다.

```
Docker
└── PostgreSQL 16
```

### 1.3 프로젝트 아키텍처

Backend와 Frontend를 하나의 Repository 안에서 분리하는 Monorepo 형태로 구성했다.

```
ecommerce-platform/
├── backend/
│   ├── src/main/java/com/example/ecommerce/
│   │   ├── global/
│   │   ├── domain/
│   │   ├── repository/
│   │   ├── service/
│   │   └── controller/
│   └── build.gradle
└── frontend/
    ├── src/
    │   ├── components/
    │   ├── pages/
    │   ├── api/
    │   ├── hooks/
    │   └── App.jsx
    └── package.json
```

```
Backend:  Controller → Service → Repository → Entity
Frontend: Page → Component → API
```

### 비즈니스 로직 설계

1. 이커머스 비즈니스 로직 설계

### 2.1 회원 / 비회원

회원은 로그인 후 자신의 장바구니, 주문, 구매 이력에 접근한다.

```
USER
├── 상품 조회
├── 장바구니
├── 주문
└── 구매 이력
```

비회원도 상품 조회와 주문이 가능하도록 설계한다. 주문 조회를 위해 UUID 성격의 식별값, 주문자 이름, 연락처, 조회용 인증값 등을 고려한다.

```
GUEST
└── 상품 조회 및 비회원 주문
```

### 2.2 주문

주문 하나에 여러 상품이 포함될 수 있으므로 Order와 OrderItem은 1:N 관계로 구성한다.

```
Order
├── OrderItem
├── OrderItem
└── OrderItem
```

주요 Order 데이터:

```
id
user
orderMerchantUid
status
totalAmount
discountAmount
orderDate
```

OrderItem에는 주문 당시의 가격과 수량을 별도로 저장한다.

```
product
orderPrice
count
```

상품 가격이 이후 변경되더라도 과거 주문 금액을 보존할 수 있다.

### 2.3 결제

Payment에는 PG 거래와 결제 결과를 추적할 수 있는 값을 저장한다.

```
id
order
pgImpUid
pgProvider
payMethod
amount
paidAt
```

프론트엔드의 결제 금액을 그대로 신뢰하지 않고 Backend에서 최종 결제 금액을 다시 계산한다.

```
주문 상품 금액
→ 할인 금액 계산
→ Backend 최종 결제 금액 계산
→ PG 실제 결제 금액 조회
→ 금액 일치 여부 검증
→ 결제 완료 처리
```

### 2.4 주문 상태와 취소 / 환불

기본 주문 및 배송 흐름:

```
ORDERED → PAID → DELIVERING → DELIVERED
```

배송 전 취소:

```
PAID
 ↓
사용자 취소
 ↓
PG 결제 취소
 ↓
CANCELLED
```

배송 이후 환불:

```
DELIVERING / DELIVERED
 ↓
환불 신청
 ↓
REFUND_REQUESTED
 ↓
관리자 승인
 ↓
PG 환불
 ↓
REFUNDED
```

환불은 Order 상태만 변경하지 않고 별도 Refund 데이터로 관리한다.

```
order
pgCancelUid
refundAmount
refundPgFee
refundPlatformFee
netRefundAmount
refundReason
refundedAt
```

### 2.5 정산

정산은 주문·결제·환불 데이터를 가공해 최종 지급 금액을 계산하는 업무 흐름이다.

```
총 매출
- PG 수수료
- 플랫폼 수수료
- 할인 금액
- 환불 금액
= 최종 정산 금액
```

원래 거래를 삭제하지 않고 환불 거래를 별도로 남겨 거래 흐름을 추적한다.

```
매출 +100,000
환불 -30,000
----------------
실제 정산 기준 70,000
```

### Backend 기술 설계

1. Backend 기술 설계

### 3.1 Entity

**User**

```
id, userKey, password, name, phoneNumber, role, createdAt
```

Role은 `USER`, `GUEST`, `ADMIN`으로 구분한다.

**Product**

```
id, name, price, stockQuantity, imageUrl, description, createdAt
```

상품 이미지와 대량 조회를 고려해 `imageUrl`, 조회 기준 컬럼, 인덱스 적용 가능성을 검토했다.

**Payment / Refund / Settlement**

PG 거래 정보, 환불 내역, 정산 결과를 각각 별도 데이터로 관리해 거래 흐름을 추적한다.

### 3.2 Pageable 기반 상품 조회

상품 전체 조회는 데이터가 증가할수록 메모리, DB 조회량, 네트워크, Frontend 렌더링 부담을 키울 수 있다. 따라서 Spring Data JPA `Pageable` 기반 페이징을 적용했다.

```
GET /api/products?page=0&size=8
```

상품이 20개이고 페이지 크기가 8이면 다음과 같이 조회된다.

```
Page 0 → 1~8
Page 1 → 9~16
Page 2 → 17~20
```

Backend는 `Page<Product>` 형태로 반환하고 Frontend는 `content`, `totalPages`를 사용한다.

향후 데이터 규모가 커지면 Cursor 기반 조회로 확장한다.

```sql
WHERE id < :lastId
ORDER BY id DESC
LIMIT 8
```

### 3.3 Spring Security 권한 분리

```
Guest
└── 상품 조회

USER
├── 상품 조회
├── 장바구니
└── 주문 / 구매 이력

ADMIN
└── 관리자 기능
```

- 공개 API: `GET /api/products`
- 회원 전용 API: `/api/cart/**`, `/api/orders/**`
- 관리자 API: `/api/admin/**`

관리자 인증은 일반 사용자 인증과 분리하고, PasswordEncoder와 `ADMIN` Role 기반 구조로 고도화한다.

### 3.4 DailySettlementSummary

대시보드 조회 때마다 Orders, OrderItems, Payments, Refunds, Settlement를 다시 집계하면 데이터가 많아질수록 비용이 커질 수 있다.

```
summaryDate
dailyTotalSales
dailyTotalPgFee
dailyTotalPlatformFee
dailyNetSettlement
totalOrderCount
```

```
원천 주문 / 결제 / 환불 데이터
            ↓
      일별 집계 작업
            ↓
 DailySettlementSummary
            ↓
     관리자 Dashboard
```

향후 Spring Batch로 집계 작업을 자동화한다.

### 3.5 Excel 다운로드

Apache POI를 이용해 관리자 정산 데이터를 Excel 파일로 생성한다.

```
정산 데이터 조회
→ Workbook 생성
→ 헤더 / 금액 / 날짜 구성
→ 파일 다운로드
```

### 3.6 Backend 설계 원칙

- Controller는 요청과 응답 처리에 집중
- Service는 주문·결제·환불·정산 규칙을 담당
- Repository는 데이터 조회와 저장을 담당
- Entity는 도메인 데이터를 표현
- 대량 조회는 Pagination과 집계 쿼리로 제어
- 원천 거래와 집계 결과를 분리해 추적성과 성능을 확보

### AI / Vibe Coding 개발 일지

1. AI / Vibe Coding 개발 일지

### 4.1 AI에게 요구사항 전달

AI에게 단순히 “쇼핑몰을 만들어줘”라고 요청하지 않고 다음 내용을 단계적으로 전달했다.

- 현재 구현하려는 기능
- 사용자와 관리자별 사용 범위
- 비즈니스 규칙
- 필요한 Entity와 관계
- API 요청과 응답 형태
- 예상되는 성공 결과
- 현재 프로젝트 구조

### 4.2 Cursor 구현

```
요구사항 정의
→ Entity / Repository 초안
→ Service 구현
→ Controller 연결
→ Frontend API 연동
→ 화면 구현
```

AI는 초기 프로젝트 구조, Entity 초안, API 코드, React 컴포넌트, 리팩토링 제안을 빠르게 생성하는 데 활용했다.

### 4.3 오류 발생과 AI 분석

코드를 생성한 뒤 로컬에서 실제 실행하고 컴파일 또는 런타임 오류를 확인했다.

```
코드 생성
→ 로컬 실행
→ 컴파일 / 런타임 오류 발생
→ 로그 확인
→ 관련 파일과 의존 관계 분석
```

오류를 전달할 때는 다음 정보를 함께 제공했다.

```
1. 현재 구현 목적
2. 변경한 파일
3. 실행한 명령어
4. 실제 에러 로그
5. 기대 결과
6. 현재 결과
```

### 4.4 수정 / 검증

```
수정 코드 반영
→ Gradle / Frontend 재실행
→ API 응답 확인
→ 화면 동작 확인
→ 기대 결과와 비교
→ 다음 기능 개발
```

상품 API 검증 예시:

```
.\\gradlew.bat bootRun -x test
http://localhost:8080/api/products?page=0&size=8
```

### 4.5 AI와 개발자의 역할

**AI가 담당한 영역**

- 초기 프로젝트 구조 제안
- Entity와 API 코드 초안
- React 컴포넌트 생성
- 오류 로그 분석 보조
- 리팩토링 제안
- 기술 선택지 비교

**개발자가 담당한 영역**

- 프로젝트 목표와 성공 기준 설정
- 이커머스 비즈니스 요구사항 정의
- 회원·비회원 정책 결정
- 주문·결제·환불·정산 규칙 결정
- 보안 문제 판단
- 생성 코드 검토
- 실행 결과 검증
- 다음 개선 방향 결정

### 4.6 개발 일지 템플릿

```
[Feature]
기능 이름

1. 문제 / 요구사항
2. 기존 상태
3. 설계
4. AI에게 전달한 요구사항
5. 구현 파일과 API
6. 문제 발생
7. 원인 분석
8. 해결
9. 검증
10. 개선점
```

### Troubleshooting

1. Troubleshooting

### 5.1 ProductService 컴파일 오류

```
error: cannot find symbol
class ProductService
location: class ProductController
```

Service 계층을 연결하는 과정에서 `ProductService.java`가 없거나 패키지·클래스·생성자 주입 구조가 정상적으로 연결되지 않은 상태였다. Lombok을 사용하는 경우 Annotation Processor 설정도 확인해야 한다.

```
ProductService 존재 여부 확인
→ Service 클래스 생성
→ Controller 의존성 연결
→ Repository 연결
→ Gradle 재빌드
→ Spring Boot 재실행
```

### 5.2 DB 연결 오류

다음 항목을 순서대로 확인한다.

- PostgreSQL 또는 Docker 컨테이너 실행 여부
- DB URL, 포트, Database 이름
- DB Username과 Password
- `application.yml`의 환경별 설정
- 로컬 DB와 배포 DB 설정 혼용 여부
- Cloud PostgreSQL 접근 허용 설정

### 5.3 Render 배포 오류

```
React Frontend (Vercel)
          │ HTTPS
          ↓
Spring Boot API (Render)
          │
          ↓
PostgreSQL (Supabase / Neon)
```

- Build Command와 Start Command가 프로젝트 구조에 맞는가
- Java 버전이 프로젝트 설정과 맞는가
- Render 환경 변수가 등록되었는가
- 서버가 지정된 포트에서 실행되는가
- 배포 로그에 컴파일·Migration 오류가 없는가
- 배포 API URL과 Frontend 요청 URL이 일치하는가

### 5.4 CORS 오류

- Backend에서 Frontend 도메인을 허용했는가
- HTTP와 HTTPS를 혼용하지 않았는가
- 요청 Method와 Header가 허용되어 있는가
- Preflight `OPTIONS` 요청이 차단되지 않는가
- 로컬 URL과 배포 URL을 구분했는가
- 인증 쿠키 사용 시 credentials 설정이 일치하는가

### 5.5 환경변수와 관리자 보안

DB 접속정보와 관리자 인증정보는 소스코드에 직접 작성하지 않는다.

```
DB_URL
DB_USERNAME
DB_PASSWORD
ADMIN_USERNAME
ADMIN_PASSWORD
```

초기 구현의 관리자 credential 하드코딩은 개선 대상이다.

```java
if ("admin".equals(username)
        && "admin1234".equals(password)) {
    // 인증 처리
}
```

개선 방향:

```
Environment Variable
        ↓
application.yml
        ↓
Configuration
        ↓
Spring Security
        ↓
Password Encoder
        ↓
Role = ADMIN
        ↓
/api/admin/**
```

### 5.6 기타 오류 대응 순서

```
현상 재현
→ 실제 로그 확보
→ 최근 변경 파일 확인
→ Controller / Service / Repository 연결 확인
→ 설정과 환경변수 확인
→ 최소 범위 수정
→ 재실행
→ API / UI 검증
```

**오류 기록 템플릿**

```
[Error]
오류 이름

1. 증상
2. 재현 방법
3. 실제 로그
4. 원인 후보
5. 최종 원인
6. 수정 파일
7. 해결 내용
8. 재검증 결과
9. 재발 방지 방법
```

### 5.7 최종 회고

이번 프로젝트의 핵심은 AI로 코드를 생성하는 데 있지 않다. 개발자가 요구사항과 비즈니스 규칙을 정의하고, AI로 구현 속도를 높인 뒤, 실제 실행 결과와 로그를 바탕으로 수정·검증하는 반복 개발 과정에 있다.

```
요구사항 정의
→ 비즈니스 규칙 설계
→ DB 모델링
→ API 설계
→ AI 기반 구현
→ 실행 오류 분석
→ 수정 및 검증
→ 성능 / 보안 / 확장성 검토
```

# Render + Supabase 배포

## 1. 배포 목적

Vibe Commerce는 로컬 환경에서만 실행되는 프로젝트가 아니라 실제 사용자가 접근할 수 있는 서비스 형태로 구현하기 위해 **Frontend / Backend / Database를 각각 배포**하였다.

### 배포 구성

```
사용자
  │
  ▼
React + Vite
Render
  │
  │ REST API
  ▼
Spring Boot
Render
  │
  │ JDBC
  ▼
Supabase PostgreSQL
```

### 사용 기술

| 구분 | 기술 | 역할 |
| --- | --- | --- |
| Frontend | React + Vite | 사용자 인터페이스 |
| Backend | Spring Boot | REST API 및 비즈니스 로직 |
| Database | Supabase PostgreSQL | 상품, 회원, 주문 등 데이터 저장 |
| Frontend Hosting | Render | 프론트엔드 배포 |
| Backend Hosting | Render | Spring Boot 서버 배포 |
| Database Hosting | Supabase | PostgreSQL 데이터베이스 |
| Repository | GitHub | 소스 코드 관리 |

---

# 2. Supabase 데이터베이스 구축

기존 로컬 개발환경에서 사용하던 데이터베이스를 실제 배포 환경에서도 사용할 수 있도록 Supabase PostgreSQL을 구성하였다.

### 구성 과정

1. Supabase 프로젝트 생성
2. PostgreSQL 데이터베이스 확인
3. 데이터베이스 접속 정보 확인
4. Spring Boot에서 사용할 DB 연결 정보 구성
5. Render Backend에 환경변수 등록

Spring Boot에서는 데이터베이스 접속 정보를 코드에 직접 작성하지 않고 환경변수를 통해 주입하도록 구성하였다.

```
spring:
  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}
```

이를 통해 GitHub에 데이터베이스 인증정보가 노출되는 것을 방지하고, 로컬/배포 환경에 따라 다른 DB를 사용할 수 있도록 구성하였다.

---

# 3. Backend 배포

Spring Boot Backend는 Render Web Service를 이용하여 배포하였다.

### 배포 과정

```
GitHub Repository
       ↓
Render Web Service 연결
       ↓
Gradle Build
       ↓
Spring Boot 실행
       ↓
API Server 배포
```

Render에서 GitHub Repository를 연결한 뒤 Build 및 Start 명령을 설정하였다.

배포가 완료되면 Render에서 제공하는 URL을 통해 Backend API에 접근할 수 있도록 구성하였다.

예시:

```
https://<backend-service>.onrender.com
```

---

# 4. Backend ↔ Supabase 연결

배포 환경에서는 로컬 개발환경과 달리 서버가 실행되는 환경이 변경되기 때문에 데이터베이스 연결 정보를 Render에 별도로 등록해야 했다.

Render의 Environment 설정에서 다음과 같은 환경변수를 등록하였다.

```
DATABASE_URL
DATABASE_USERNAME
DATABASE_PASSWORD
```

이를 통해 다음과 같은 구조로 연결하였다.

```
Spring Boot
    │
    │ Environment Variables
    ▼
Render
    │
    │ JDBC
    ▼
Supabase PostgreSQL
```

### 환경변수를 사용한 이유

- DB 접속정보의 소스코드 노출 방지
- GitHub Repository에 비밀번호 저장 방지
- 로컬/배포 환경의 DB 설정 분리
- 배포 환경 변경 시 코드 수정 없이 설정 변경 가능

---

# 5. Frontend 배포

React + Vite 기반 Frontend는 Render를 통해 배포하였다.

Frontend에서는 Backend API를 호출해야 하기 때문에 로컬 개발환경에서 사용하던 API 주소를 배포된 Backend 주소로 변경하였다.

### 개발 환경

```
http://localhost:8080
```

### 배포 환경

```
https://<backend-service>.onrender.com
```

따라서 실제 서비스에서는 다음과 같은 구조로 API가 호출된다.

```
Browser
   │
   ▼
React Frontend
   │
   │ HTTP Request
   ▼
Render Backend
   │
   │ JDBC
   ▼
Supabase
```

---

# 6. 배포 후 발생한 문제

배포가 완료된 후 서비스 URL에 접속했을 때 Frontend 화면 자체는 정상적으로 표시되었지만, **상품 등의 데이터가 정상적으로 호출되지 않는 현상**이 발생하였다.

처음에는 Frontend 문제인지 Backend 문제인지 명확하지 않았기 때문에 다음과 같이 문제를 분리하여 확인하였다.

```
Frontend
   ↓
API 요청
   ↓
Backend
   ↓
Database
```

각 구간의 정상 동작 여부를 확인하여 문제 발생 지점을 좁히는 방식으로 접근하였다.

---

# 7. Render Cold Start / Sleep 문제 확인

추가로 배포된 서비스를 처음 접속할 때 **5~6분 정도의 긴 로딩 시간**이 발생하는 현상을 확인하였다.

Render에서 일정 시간 동안 요청이 없는 서비스가 Sleep 상태로 전환될 수 있으며, 이후 새로운 요청이 들어오면 서버가 다시 기동되는 과정이 발생할 수 있다.

### 동작 과정

```
사용자 요청 없음
       ↓
Render Server Sleep
       ↓
방문자 A 접속
       ↓
Render Server Wake Up
       ↓
Spring Boot 실행
       ↓
Supabase 연결
       ↓
API 응답
```

이 경우 서버가 Sleep 상태에서 깨어나는 과정에서 **첫 번째 방문자의 응답 시간이 길어질 수 있다.**

반면 서버가 이미 실행 중이라면 이후 방문자는 일반적으로 이러한 초기 기동 시간을 기다리지 않는다.

### 확인한 내용

```
첫 방문자
→ 서버가 Sleep 상태라면 초기 응답이 느릴 수 있음

이후 방문자
→ 서버가 실행 중이라면 정상적으로 빠르게 응답

장시간 요청이 없는 경우
→ 다시 Sleep 상태가 될 수 있음
```

따라서 별도의 로컬 서버를 직접 실행하거나 서버를 수동으로 깨울 필요는 없으며, Render가 요청에 따라 서버를 자동으로 기동한다.

---

# 8. 배포 과정에서 얻은 인사이트

이번 배포를 통해 단순히 애플리케이션을 개발하는 것뿐만 아니라 **실제 서비스 환경에서 발생하는 문제를 직접 경험하고 해결하는 과정**을 경험하였다.

특히 로컬 환경에서는 정상적으로 동작하던 서비스도 실제 배포 환경에서는 다음과 같은 차이가 발생할 수 있다는 점을 확인하였다.

### Local vs Production

| 구분 | Local | Production |
| --- | --- | --- |
| Frontend | localhost | Render |
| Backend | localhost | Render |
| Database | Local DB | Supabase |
| 환경변수 | 로컬 설정 | Render Environment |
| 서버 상태 | 항상 실행 | Sleep/Wake 가능 |
| API 주소 | localhost | 배포 Backend URL |

이를 통해 **환경변수 관리, 서버 배포, DB 외부 연결, API Endpoint 관리, Cold Start 등 실제 운영환경에서 고려해야 할 요소**를 이해할 수 있었다.

---

# 9. 최종 배포 구조

현재 서비스의 전체적인 배포 구조는 다음과 같다.

```
                         ┌─────────────────┐
                         │      User       │
                         └────────┬────────┘
                                  │
                                  ▼
                         ┌─────────────────┐
                         │ React + Vite    │
                         │    Render       │
                         └────────┬────────┘
                                  │
                              REST API
                                  │
                                  ▼
                         ┌─────────────────┐
                         │   Spring Boot   │
                         │     Render      │
                         └────────┬────────┘
                                  │
                                JDBC
                                  │
                                  ▼
                         ┌─────────────────┐
                         │    Supabase     │
                         │   PostgreSQL    │
                         └─────────────────┘
```

### 배포 결과

- Frontend 실제 서비스 URL 배포
- Spring Boot Backend 배포
- Supabase PostgreSQL 연동
- GitHub → Render 배포 환경 구성
- 환경변수를 통한 DB 인증정보 관리
- 배포 후 API/DB 연결 문제 및 Cold Start 현상 확인
- 로컬 환경과 Production 환경의 차이 경험
