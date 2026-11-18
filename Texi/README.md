# Texi - 택시 합승 앱 DB / 로그인·회원가입 / 평점·신고 API

Java Swing 화면에서 사용하는 **MySQL DB 계층(DAO)**, **로그인·회원가입 API**, **평점·신고 API** 입니다.
저장소 루트의 Spring Boot 프로젝트(`src/main/java`)와는 별개의 이클립스 Java 프로젝트입니다.

## 폴더 구성

```
Texi/
├─ schema.sql                 MySQL 테이블 생성 스크립트 (taxi_share DB)
├─ db.properties.example      DB 접속 설정 예시 (복사해서 db.properties 로 사용)
├─ lib/
│   └─ mysql-connector-j-26.10.0.jar   MySQL JDBC 드라이버
└─ src/taxi/
    ├─ auth/                  ★ 로그인·회원가입 API
    │   ├─ AuthService.java      회원가입 / 로그인 / 이메일·닉네임 중복검사
    │   ├─ PasswordHasher.java   비밀번호 해시 (PBKDF2, 솔트 포함)
    │   └─ AuthException.java    화면에 보여줄 오류 메시지용 예외
    ├─ service/               ★ 평점·신고 API
    │   ├─ RatingService.java    평점 제출(1~5점, 미입력 시 건너뜀) / 평균 평점·개수 조회
    │   └─ ReportService.java    신고 제출(내용 없으면 건너뜀)
    ├─ dao/                   SQL 실행 클래스 (MemberDAO, RideRequestDAO, RideMatchDAO, RatingDAO, ReportDAO)
    ├─ db/DBConnection.java   db.properties 를 읽어 MySQL 연결 생성
    ├─ model/                 테이블 한 행을 담는 클래스 (Member, RideRequest ...)
    ├─ DbSmokeTest.java       DB 연결 확인용 main
    ├─ AuthSmokeTest.java     회원가입·로그인 저장 확인용 main
    └─ ReviewSmokeTest.java   평점·신고 저장 확인용 main
```

구조:
- `Swing 화면 → AuthService(입력 규칙 검사) → MemberDAO(SQL) → MySQL member 테이블`
- `Swing 화면 → RatingService / ReportService(입력 규칙 검사) → RatingDAO / ReportDAO(SQL) → MySQL rating / report 테이블`

---

## 1. 처음 설정하기 (팀원 각자 1번)

### 1-1. DB 만들기
MySQL Workbench 에서 `schema.sql` 을 열고 전체 실행(⚡)합니다.

> ⚠ `schema.sql` 은 실행할 때마다 테이블을 **삭제 후 다시 만듭니다.** 이미 데이터가 있으면 모두 지워집니다.

### 1-2. 이클립스 프로젝트로 가져오기
1. `File → New → Java Project`
2. 프로젝트 이름 `Texi`, **Use default location 체크 해제** → Location 에 이 저장소의 `Texi` 폴더 선택 → Finish
3. `lib/mysql-connector-j-26.10.0.jar` 우클릭 → `Build Path → Add to Build Path`
   (Referenced Libraries 에 jar 가 보이면 성공)

### 1-3. DB 접속 설정 파일 만들기
1. `db.properties.example` 을 복사해서 같은 위치(`Texi` 폴더)에 `db.properties` 로 저장
2. `db.password=` 뒤에 **본인 MySQL 비밀번호** 입력

> ⚠ `db.properties` 는 비밀번호가 들어 있어서 `.gitignore` 로 제외되어 있습니다. **절대 커밋하지 마세요.**
> 프로그램은 실행 폴더에서 `db.properties` 를 찾으므로, 이클립스 실행 시 작업 폴더가 `Texi` 여야 합니다(기본값).

### 1-4. 동작 확인
1. `taxi/DbSmokeTest.java` 실행 → `연결 성공! DB: taxi_share` 출력 확인
2. `taxi/AuthSmokeTest.java` 실행 → 아래처럼 나오면 회원가입·로그인 데이터가 DB에 저장되는 것

```
1) 이메일 사용 가능? true
2) 회원가입 성공! member_id = 1
3) 가입 후 같은 이메일 사용 가능? false (false 가 정상)
4) 로그인 성공! Member{id=1, email=smoke12345@test.com, ...}
5) 틀린 비밀번호 차단: 아이디 또는 비밀번호가 올바르지 않습니다.
```

MySQL 에서 직접 확인:
```sql
USE taxi_share;
SELECT member_id, email, nickname, status, created_at, LEFT(password_hash, 30) AS hash_preview
  FROM member ORDER BY member_id DESC;
```
`password_hash` 가 `pbkdf2$65536$...` 로 시작하고 비밀번호 원문이 보이지 않으면 정상입니다.

테스트 데이터 정리: `DELETE FROM member WHERE email LIKE 'smoke%@test.com';`

---

## 2. Swing 화면에 연결하기

화면 클래스에서 `AuthService` 하나만 사용하면 됩니다. SQL 은 몰라도 됩니다.

| 화면 동작 | 호출할 메서드 | 반환값 |
|---|---|---|
| 아이디(이메일) 중복확인 버튼 | `isEmailAvailable(email)` | 사용 가능하면 `true` |
| 닉네임 중복확인 버튼 | `isNicknameAvailable(nickname)` | 사용 가능하면 `true` |
| 회원가입 버튼 | `signUp(email, pw, pwConfirm, nickname, gender)` | 새 `member_id` |
| 로그인 버튼 | `login(email, pw)` | 로그인한 `Member` |

모든 메서드는 실패하면 `AuthException` 을 던지고, `getMessage()` 에 화면에 그대로 보여줄 문구가 들어 있습니다.

```java
import taxi.auth.AuthException;
import taxi.auth.AuthService;
import taxi.model.Member;

AuthService auth = new AuthService();

// 회원가입 버튼
try {
    auth.signUp(emailField.getText(),
                new String(pwField.getPassword()),
                new String(pwConfirmField.getPassword()),
                nicknameField.getText(),
                maleRadio.isSelected() ? "M" : "F");
    JOptionPane.showMessageDialog(this, "회원가입 완료!");
} catch (AuthException ex) {
    JOptionPane.showMessageDialog(this, ex.getMessage());
}

// 로그인 버튼
try {
    Member me = auth.login(emailField.getText(), new String(pwField.getPassword()));
    // me.getMemberId() 를 합승 요청(RideRequestDAO) 등 다른 화면에 넘겨서 사용
} catch (AuthException ex) {
    JOptionPane.showMessageDialog(this, ex.getMessage());
}
```

---

## 3. 입력 규칙

| 항목 | 규칙 | 바꾸려면 (`AuthService.java`) |
|---|---|---|
| 아이디 | 이메일 형식, 100자 이하, 대소문자 구분 없이 중복 불가(소문자로 저장) | `EMAIL_PATTERN` |
| 비밀번호 | **영문과 숫자를 모두 포함한 8자 이상**, 특수문자·공백 불가 | `PASSWORD_PATTERN` (정확히 8자만 허용하려면 `{8,}` → `{8}`) |
| 닉네임 | 한글·영문·숫자 2~10자, 중복 불가 | `NICKNAME_PATTERN` |
| 성별 | `"M"` 또는 `"F"` | — |

그 외 동작:
- 비밀번호는 원문 대신 `pbkdf2$반복횟수$솔트$해시` 형식으로 `password_hash` 컬럼에 저장합니다.
- 로그인 실패 시 "아이디 없음"과 "비밀번호 틀림"을 같은 메시지로 보여줍니다. (가입된 이메일 노출 방지)
- `status` 가 `SUSPENDED`(이용정지), `WITHDRAWN`(탈퇴)인 회원은 로그인할 수 없습니다.
- 중복확인 직후 다른 사람이 같은 값으로 먼저 가입해도, DB 의 UNIQUE 제약이 막고 "이미 사용 중" 메시지를 보여줍니다.

---

## 4. 평점·신고 API

### 4-1. 동작 확인
`taxi/ReviewSmokeTest.java` 실행 → 마지막 줄이 `결과: 통과 12개 / 실패 0개` 이면 정상입니다.
(테스트용 가상 회원 2명과 완료된 매칭 1건을 만든 뒤 평점·신고 규칙을 차례로 확인합니다.)

```sql
USE taxi_share;
SELECT * FROM rating ORDER BY rating_id DESC LIMIT 5;
SELECT * FROM report ORDER BY report_id DESC LIMIT 5;
```

> 실행할 때마다 가상 회원(`is_virtual = 1`) 2명과 매칭 1건이 쌓입니다. 실제 회원과는 `is_virtual` 로 구분됩니다.

### 4-2. Swing 화면에 연결하기

| 화면 동작 | 호출할 메서드 | 반환값 |
|---|---|---|
| 평점 제출 버튼 | `ratingService.submit(matchId, raterId, rateeId, score)` | 저장하면 `true`, `score` 가 `null` 이면 건너뛰고 `false` |
| 신고 제출 버튼 | `reportService.submit(matchId, reporterId, reportedId, detail)` | 저장하면 `true`, 내용이 비었으면 건너뛰고 `false` |
| 평균 평점 표시 | `ratingService.getAverageRating(memberId)` / `getRatingCount(memberId)` | 평균(0.0~5.0) / 받은 개수 |

```java
import java.sql.SQLException;
import taxi.service.RatingService;
import taxi.service.ReportService;

RatingService ratingService = new RatingService();
ReportService reportService = new ReportService();

try {
    // selectedScore: 선택 안 했으면 null (Integer 타입)
    ratingService.submit(matchId, myId, partnerId, selectedScore);
    // 빈칸이면 알아서 건너뜀
    reportService.submit(matchId, myId, partnerId, reportArea.getText());
} catch (IllegalArgumentException | IllegalStateException e) {
    JOptionPane.showMessageDialog(this, e.getMessage());   // 사용자에게 보여줄 안내
} catch (SQLException e) {
    JOptionPane.showMessageDialog(this, "서버(DB) 연결에 문제가 생겼습니다.");
}
```

### 4-3. 규칙

| 항목 | 규칙 |
|---|---|
| 평점 | 1~5점 정수 (0.5점 없음). 미입력(`null`)이면 저장하지 않고 넘어감. 저장 시 받은 사람의 평균 평점·개수 자동 갱신 |
| 신고 | 내용이 비었거나 공백뿐이면 저장하지 않고 넘어감. 1000자 이하. 사유는 `OTHER`(기타)로 저장 (사유를 받는 `submit(..., Report.Reason, detail)` 도 있음) |
| 공통 | **완료(`COMPLETED`)된 매칭**에 함께 탄 사람끼리만 가능. 자기 자신 불가. 같은 매칭에서 같은 사람에게 중복 불가 |

오류 종류: 잘못된 값 → `IllegalArgumentException`, 중복·완료 전 매칭 → `IllegalStateException`, DB 문제 → `SQLException`

---

## 5. 자주 나는 오류

| 증상 | 원인 / 해결 |
|---|---|
| `db.properties 파일을 읽을 수 없습니다` | `Texi` 폴더에 `db.properties` 가 없거나 실행 작업 폴더가 다름 (1-3 참고) |
| `서버(DB) 연결에 문제가 발생했습니다` | `AuthSmokeTest` 콘솔에 원인 출력. `Access denied` → 계정/비밀번호, `Unknown database` → `schema.sql` 미실행 |
| `No suitable driver found` | MySQL 드라이버 jar 가 Build Path 에 없음 (1-2 의 3번) |
| `The declared package "taxi.auth" does not match ...` | 파일 위치가 `src/taxi/auth/` 가 아님. 패키지 이름과 폴더 경로가 같아야 함 |
| `MemberDAO cannot be resolved` | `auth` 폴더를 별도 프로젝트에 넣음. 같은 `Texi` 프로젝트의 `src/taxi/` 안에 있어야 함 |
