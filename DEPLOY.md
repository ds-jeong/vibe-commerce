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

## 6. 참고

- Free 플랜은 유휴 시 꺼집니다. 콜드 스타트가 있습니다.
- 시크릿은 Render Environment 에만 넣고 코드에 쓰지 않습니다.
- 로컬은 기존처럼 `application.yml` + Vite 프록시(`localhost:8080`)입니다.
