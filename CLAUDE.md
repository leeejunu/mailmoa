1# CLAUDE.md

## 패키지 구조

기능(feature) 단위로 폴더를 나누고, 각 기능 안에 레이어별 폴더를 둔다.

```
{feature}/
├── application/
│   ├── dto/               # Command (입력), Result (출력)
│   ├── exception/         # 커스텀 예외
│   ├── port/              # 외부 연동 인터페이스 (infrastructure가 구현)
│   ├── service/           # 비즈니스 로직 구현체 ({Feature}Service.java)
│   └── usecase/           # 유스케이스 인터페이스 ({Feature}UseCase.java)
├── domain/
│   ├── model/             # 엔티티 ({Feature}.java)
│   └── repository/        # 레포지토리 인터페이스
├── infrastructure/
│   ├── persistence/       # JPA 구현체
│   │   ├── {Feature}JpaRepository.java
│   │   └── {Feature}RepositoryAdapter.java
│   └── redis/             # Redis 구현체 ({Feature}RedisAdapter.java)
└── presentation/
    ├── dto/
    │   ├── req/           # HTTP 요청 DTO
    │   └── res/           # HTTP 응답 DTO
    └── {Feature}Controller.java
```

실제 예시 (user):
```
user/
├── application/
│   ├── dto/
│   │   ├── LoginCommand.java
│   │   ├── LoginResult.java
│   │   ├── SignUpCommand.java
│   │   └── SignUpResult.java
│   ├── exception/
│   │   ├── DuplicateEmailException.java
│   │   └── InvalidCredentialsException.java
│   ├── port/
│   │   └── RefreshTokenPort.java
│   ├── service/UserService.java
│   └── usecase/UserUseCase.java
├── domain/
│   ├── model/User.java
│   └── repository/UserRepository.java
├── infrastructure/
│   ├── persistence/
│   │   ├── UserJpaRepository.java
│   │   └── UserRepositoryAdapter.java
│   └── redis/
│       └── RedisAdapter.java
└── presentation/
    ├── dto/
    │   ├── req/LoginRequest.java
    │   ├── req/SignUpRequest.java
    │   ├── res/LoginResponse.java
    │   └── res/SignUpResponse.java
    └── AuthController.java
```

## 레이어 정의

| 레이어 | 설명 |
|--------|------|
| domain/model | JPA 엔티티 |
| domain/repository | 레포지토리 인터페이스 |
| application/usecase | 유스케이스 인터페이스 |
| application/service | 비즈니스 로직 구현체 |
| application/dto | Command (입력), Result (출력) — presentation 몰라도 됨 |
| application/port | infrastructure 연동 인터페이스 (Redis 등) |
| application/exception | 커스텀 예외 |
| infrastructure/persistence | JpaRepository 및 Repository 구현체 |
| infrastructure/redis | Redis 구현체 (port 인터페이스 구현) |
| presentation/dto | HTTP 요청/응답 전용 DTO |
| presentation | Controller — Request→Command, Result→Response 변환 |

## 레이어 설명

### presentation
클라이언트(브라우저, 앱)의 HTTP 요청을 받아 응답을 돌려주는 레이어.
Request를 Command로 변환해 application을 호출하고, Result를 Response로 변환해 반환한다.

### application
우리 서버 안에서 처리하는 비즈니스 로직 레이어.
DB, Redis 같은 외부 시스템을 직접 알지 않고, port 인터페이스를 통해 간접적으로 사용한다.

### infrastructure
**외부 시스템과 직접 연동하는 레이어.** DB(JPA), Redis, 외부 메일 API 등 서버 바깥과 통신하는 코드는 모두 여기에 작성한다.
application의 port 인터페이스를 구현하는 구현체가 위치한다.

### domain
비즈니스 핵심 모델(엔티티)과 레포지토리 인터페이스. 어떤 레이어에도 의존하지 않는다.

> **엔티티는 반드시 `domain/model/` 안에 작성한다.**

## 의존 방향

```
presentation → application → domain
infrastructure → domain (port 인터페이스 구현)
```

- application은 presentation, infrastructure를 몰라야 한다
- infrastructure는 application의 port 인터페이스를 구현한다
- presentation은 application의 Command/Result를 사용한다

## global 패키지

기능에 속하지 않는 공통 코드는 `global/` 아래에 둔다.

```
global/
├── config/            # Spring 설정 (Security, Redis 등)
├── exception/         # GlobalExceptionHandler, ExceptionResponse
├── filter/            # JwtFilter 등 공통 필터
└── util/              # JwtProvider 등 공통 유틸
```

## 프로젝트 경로

- 백엔드: `/Users/junwoo/Desktop/dev/mailmoa`
- 프론트엔드: `/Users/junwoo/Desktop/dev/mailmoa_fe`

## 문서

- 문서 파일은 `docs/` 폴더에 작성
