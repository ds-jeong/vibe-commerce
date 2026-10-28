# Render + Supabase 배포 가이드

로컬 `application.yml`은 그대로 두고, 클라우드에서는 `SPRING_PROFILES_ACTIVE=prod` 와 환경변수만 사용합니다.

## 1. Supabase PostgreSQL

1. [Supabase](https://supabase.com) 프로젝트 생성
2. **Project Settings → Database** 에서 Connection string (URI) 확인
3. **Session pooler / Direct** 의 호스트를 씁니다. Transaction pooler(6543) 는 JPA와 맞지 않습니다.
4. JDBC 형태로 변환합니다.

```
jdbc:postgresql://<HOST>:5432/postgres?sslmode=require
```

- `DB_USERNAME`: 보통 `postgres` (또는 대시보드의 user)
- `DB_PASSWORD`: 프로젝트 DB 비밀번호
- `DB_URL`: 위 JDBC URL 전체 (비밀번호를 URL에 넣지 말고 `DB_PASSWORD`로 분리)

## 2. GitHub

이미 `.gradle/`, `build/`, `node_modules/` 는 제외된 상태면 그대로 푸시합니다.  
추가된 파일: `backend/Dockerfile`, `frontend/Dockerfile`, `application-prod.properties`

## 3. Render — 백엔드 (Docker)

1. [Render](https://render.com) → **New → Web Service** → GitHub 저장소 연결
2. 설정
   - **Root Directory:** `backend`
   - **Runtime:** Docker
   - **Dockerfile Path:** `Dockerfile`
   - **Instance:** Free
3. Environment

| Key | 값 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_URL` | `jdbc:postgresql://...sslmode=require` |
| `DB_USERNAME` | Supabase user |
| `DB_PASSWORD` | Supabase password |
| `PORTONE_API_SECRET` | PortOne 시크릿 (있으면) |
| `FRONTEND_ORIGIN` | 프론트 Render URL. 예: `https://vibe-web.onrender.com` |
| `FILE_UPLOAD_DIR` | `/app/uploads` |

`PORT` 는 Render가 넣습니다. `application-prod.properties`가 `${PORT:8080}` 으로 수신합니다.

배포 후 URL 예: `https://vibe-commerce-api.onrender.com`

## 4. Render — 프론트엔드 (Docker)

1. **New → Web Service** → 같은 GitHub 저장소
2. 설정
   - **Root Directory:** `frontend`
   - **Runtime:** Docker
   - **Dockerfile Path:** `Dockerfile`
3. Environment

| Key | 값 |
| --- | --- |
| `BACKEND_URL` | 백엔드 공개 URL. 슬래시 없이. 예: `https://vibe-commerce-api.onrender.com` |
| `VITE_PORTONE_STORE_ID` | PortOne 스토어 ID (빌드 인자로도 전달 가능) |
| `VITE_PORTONE_CHANNEL_KEY` | PortOne 채널 키 |

`VITE_API_BASE_URL` 은 비워 둡니다. nginx가 `/api`, `/uploads` 를 `BACKEND_URL` 로 프록시합니다.

프론트 URL을 백엔드 `FRONTEND_ORIGIN` 에 다시 넣고 **백엔드 Redeploy**.

## 5. 확인 순서

1. 백엔드 로그에 PostgreSQL 연결 / 테이블 `ddl-auto=update` 성공
2. `https://<백엔드>/api/products` 응답
3. 프론트 쇼핑몰 목록, 로그인, 결제 검증

## 6. 챗봇 배포

챗봇은 백엔드에서만 Gemini Developer API를 호출합니다. 프론트 환경변수나 빌드 인자로 API 키를 넣지 않습니다. 한도를 넘어도 유료 모델로 자동 전환하지 않습니다.

| Key | 위치 | 값 |
| --- | --- | --- |
| `GEMINI_API_KEY` | 백엔드 Render Environment | Google AI Studio에서 발급한 키. 저장소에 넣지 않음 |
| `GEMINI_MODEL` | 같은 위치. 생략 가능 | 기본 `gemini-3.8-flash`. 가격 문서의 무료 Standard 티어 |
| `LLM_TIMEOUT_SECONDS` | 같은 위치. 생략 가능 | 1~60초. 기본 15 |
| `LLM_MAX_REQUESTS_PER_MINUTE` | 같은 위치. 생략 가능 | 사용자당 1~30회. 기본 10 |

`application-prod.properties`가 이 값을 읽습니다. 주소는 `https://generativelanguage.googleapis.com/v1beta`로 고정합니다. 키가 비어 있으면 외부 호출 없이 설정 확인 메시지를 반환합니다. 무료 티어는 요청 내용이 Google 제품 개선에 사용될 수 있으므로, 주문 답변에 필요한 최소 정보만 보냅니다. 호출은 `store=false`입니다. 기존 `LLM_API_KEY`, `LLM_BASE_URL`, `LLM_MODEL`은 더 이상 읽지 않습니다.

이번 챗봇 변경은 새 테이블이나 컬럼을 추가하지 않습니다. 반품 사유는 기존 `return_requests.reason`을 사용합니다. 운영 DB에 수동 DDL을 실행하지 않습니다. `ddl-auto=update`는 기존 설정 그대로라 배포 시 Hibernate가 스키마를 맞출 수 있으므로, 배포 전에 Supabase 백업을 둡니다. 롤백은 이전 백엔드 리비전을 다시 배포하는 것입니다. `update`는 컬럼을 지우지 않으므로, 이번 배포만으로 되돌릴 스키마 변경은 없습니다.

Free 인스턴스는 유휴 시 잠듭니다. 깨어나는 동안 챗봇의 15초 제한에 걸리면 503이 나고, 사용자는 다시 시도할 수 있습니다. 호출 횟수 제한은 인스턴스 메모리에만 있으므로 인스턴스가 여러 개면 인스턴스마다 따로 셉니다.

프론트 nginx가 `/api`를 백엔드로 넘기면 브라우저는 같은 출처로 호출합니다. 프론트를 다른 출처에서 직접 백엔드에 연결할 때만 `FRONTEND_ORIGIN`을 그 프론트 주소로 지정합니다.

## 7. 참고

- Free 플랜은 유휴 시 꺼집니다. 콜드 스타트가 있습니다.
- 시크릿은 Render Environment 에만 넣고 코드에 쓰지 않습니다.
- 로컬은 기존처럼 `application.yml` + Vite 프록시(`localhost:8080`)입니다.
