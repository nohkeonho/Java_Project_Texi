# Texi_signup - 개인정보·위치정보 동의 처리

회원가입 화면의 **개인정보 수집·이용 동의 / 위치정보 수집·이용 동의 체크박스**를 처리하는 패키지입니다.

- 동의하지 않으면 **가입을 막고**, 빠진 항목과 경고 문구를 **GUI 로 넘겨줍니다.** (빨간 글자 표시 등은 GUI 에서 처리)
- 동의하고 가입하면 **동의 시각을 DB(member 테이블)에 저장**합니다.
- 기존 회원가입·로그인 API(`taxi.auth.AuthService`)와 DAO 는 **수정하지 않았습니다.** 가입 버튼 코드에서 앞뒤로 감싸서 사용합니다.

```
가입하기 클릭
  → ConsentService.beforeSignup()   동의 검사
       ├─ 미동의 → GUI 에 경고 표시 → 가입 중단 (AuthService 호출 안 함)
       └─ 동의   → AuthService.signUp() (기존 API 그대로) → member_id
                 → ConsentService.afterSignup(member_id)  동의 시각 DB 저장
```

---

## 1. 파일 구성

```
Texi/
├─ consent_migration.sql          member 테이블에 privacy_agreed_at 컬럼 추가 (1번만 실행)
└─ src/Texi_signup/
    ├─ ConsentCheckResult.java    체크박스 값 검사 → 빠진 항목 + 경고 문구
    ├─ ConsentErrorHandler.java   경고를 화면에 어떻게 보여 줄지 GUI 가 구현하는 인터페이스
    ├─ ConsentService.java        가입 전 검사 beforeSignup() / 가입 후 저장 afterSignup()
    ├─ ConsentDAO.java            동의 시각 저장·조회 SQL (UPDATE / SELECT)
    ├─ ConsentTest.java           자동 테스트 (DB 필요 없음)
    └─ ConsentDemoFrame.java      임시 테스트 화면 (GUI 완성 후 삭제해도 됨)
```

DB 연결은 기존 `taxi.db.DBConnection` 을 그대로 사용합니다. (`db.properties` 설정 공유)

| DB 컬럼 (member 테이블) | 의미 |
|---|---|
| `privacy_agreed_at` | 개인정보 수집·이용 동의 시각 (`consent_migration.sql` 로 추가, NULL 이면 미동의) |
| `location_agreed_at` | 위치정보 수집·이용 동의 시각 (원래 `schema.sql` 에 있음, NULL 이면 미동의) |

---

## 2. 처음 설정 (팀원 각자 1번)

1. 기존 README 대로 `schema.sql` 실행, `db.properties` 작성까지 끝낸 상태에서
2. MySQL Workbench 에서 **`Texi/consent_migration.sql` 실행** (⚡)
3. 확인:
   ```sql
   SHOW COLUMNS FROM taxi_share.member LIKE '%agreed%';
   ```
   `privacy_agreed_at`, `location_agreed_at` 두 줄이 나오면 성공

> - **한 번만 실행하세요.** 두 번 실행하면 `Duplicate column name` 오류가 납니다. (DB 가 망가지지는 않음)
> - `schema.sql` 을 다시 실행하면 member 테이블이 새로 만들어지면서 컬럼이 사라지므로, **그 뒤에 `consent_migration.sql` 도 다시 실행**하세요.
> - 컬럼이 NULL 허용이라 기존 회원가입·로그인 SQL 은 수정 없이 그대로 동작합니다.

---

## 3. GUI 에 연동하는 방법

회원가입 화면(`SignupPanel`)에 아래 3가지만 넣으면 됩니다.

### 3-1. 경고 문구를 띄울 라벨 준비

체크박스는 GUI 에서 만든 것을 그대로 사용합니다. 경고 문구용 라벨만 하나 추가합니다.

```java
JCheckBox privacyCheckBox  = ...;   // GUI 에서 만든 개인정보 동의 체크박스
JCheckBox locationCheckBox = ...;   // GUI 에서 만든 위치정보 동의 체크박스

JLabel consentErrorLabel = new JLabel(" ");   // " " 로 두면 빈 상태에서도 자리 높이가 유지됨
consentErrorLabel.setForeground(Color.RED);
add(consentErrorLabel);                      // 체크박스 아래 등 원하는 위치에

Color normalColor = privacyCheckBox.getForeground();   // 원래 글자색 기억
```

### 3-2. 경고 표시 방법 정하기 (`ConsentErrorHandler` 구현)

```java
ConsentErrorHandler consentHandler = new ConsentErrorHandler() {
    @Override
    public void showConsentError(ConsentCheckResult r) {
        // 빠진 항목만 빨간색으로
        privacyCheckBox.setForeground(r.isPrivacyMissing() ? Color.RED : normalColor);
        locationCheckBox.setForeground(r.isLocationMissing() ? Color.RED : normalColor);
        consentErrorLabel.setText(r.getMessage());   // 경고 문구 표시
    }

    @Override
    public void clearConsentError() {                // 동의 후 다시 누르면 원래대로
        privacyCheckBox.setForeground(normalColor);
        locationCheckBox.setForeground(normalColor);
        consentErrorLabel.setText(" ");
    }
};
```

`ConsentCheckResult` 에서 쓸 수 있는 값:

| 메서드 | 의미 |
|---|---|
| `isPrivacyMissing()` | 개인정보 동의가 빠졌으면 true |
| `isLocationMissing()` | 위치정보 동의가 빠졌으면 true |
| `getMessage()` | 화면에 띄울 경고 문구 |
| `isOk()` | 모두 동의했으면 true |

경고 문구는 아래 3가지 중 하나입니다. (문구를 바꾸려면 `ConsentCheckResult.check()` 만 고치면 됩니다)

- 개인정보 수집·이용 및 위치정보 수집·이용에 동의해야 가입할 수 있습니다.
- 개인정보 수집·이용에 동의해야 가입할 수 있습니다.
- 위치정보 수집·이용에 동의해야 가입할 수 있습니다.

### 3-3. 가입 버튼에서 기존 API 앞뒤로 감싸기

```java
submitBtn.addActionListener(e -> {
    boolean privacy  = privacyCheckBox.isSelected();
    boolean location = locationCheckBox.isSelected();

    // ① 가입 전: 동의 검사. 미동의면 경고 표시 후 false → 여기서 멈춘다 (가입 차단)
    if (!ConsentService.beforeSignup(privacy, location, consentHandler)) {
        return;
    }

    try {
        // ② 기존 회원가입 API (수정 없이 그대로 호출)
        long memberId = authService.signUp(email, password, passwordConfirm, nickname, gender);

        // ③ 가입 후: 동의 시각 DB 저장
        ConsentService.afterSignup(memberId, privacy, location);

        JOptionPane.showMessageDialog(this, "회원가입이 완료되었습니다!");
    } catch (AuthException ex) {                 // 기존 API 오류 (중복 이메일 등)
        JOptionPane.showMessageDialog(this, ex.getMessage(), "회원가입 실패", JOptionPane.WARNING_MESSAGE);
    } catch (SQLException ex) {                  // 동의 저장 실패
        ex.printStackTrace();
        JOptionPane.showMessageDialog(this, "동의 정보 저장 중 오류가 발생했습니다.", "오류", JOptionPane.ERROR_MESSAGE);
    }
});
```

필요한 import:

```java
import java.sql.SQLException;
import Texi_signup.ConsentCheckResult;
import Texi_signup.ConsentErrorHandler;
import Texi_signup.ConsentService;
import taxi.auth.AuthException;
import taxi.auth.AuthService;
```

> - 회원 번호 대신 이메일로 저장하려면 `ConsentService.afterSignupByEmail(email, privacy, location)` 을 사용합니다.
> - ①을 빼먹고 ③만 호출하면 `IllegalStateException` 이 발생해서 실수를 바로 알 수 있습니다.

### 3-4. (선택) 로그인 후 동의 여부 확인

가입과 동의 저장은 따로 실행되므로, 드물게 가입은 됐지만 동의 저장만 실패할 수 있습니다.
로그인 후 아래처럼 확인해서 `false` 면 동의 화면을 다시 보여 주면 됩니다.

```java
boolean ok = new ConsentDAO().hasAllConsents(member.getMemberId());
```

---

## 4. 테스트 방법

### 4-1. 자동 테스트 (DB 필요 없음)

`src/Texi_signup/ConsentTest.java` 우클릭 → **Run As → Java Application**

```
[통과] 둘 다 미동의 → 두 항목 모두 표시 대상
[통과] 개인정보만 빠짐 → 개인정보만 표시 대상
...
결과: 통과 10 / 실패 0
```

> 한글이 `???` 로 깨지면: Run → Run Configurations → 해당 클래스 → **Common** 탭 → Encoding 을 **UTF-8** 로 변경

### 4-2. 화면에서 가입 차단 확인 (DB 필요 없음)

`src/Texi_signup/ConsentDemoFrame.java` → **Run As → Java Application**

| # | 해 볼 것 | 정상 결과 |
|---|---|---|
| 1 | 아무것도 체크 안 하고 가입하기 | 두 체크박스 모두 빨간색 + "개인정보 수집·이용 및 위치정보 수집·이용에 동의해야…" |
| 2 | 위치정보만 체크하고 가입하기 | **개인정보 글자만** 빨간색 + "개인정보 수집·이용에 동의해야…" |
| 3 | 개인정보만 체크하고 가입하기 | 위치정보 글자만 빨간색 + "위치정보 수집·이용에 동의해야…" |

### 4-3. 동의 저장 확인 (MySQL 필요)

1. `consent_migration.sql` 을 실행했는지 확인
2. 테스트용 회원 준비 (없으면 추가):
   ```sql
   USE taxi_share;
   INSERT INTO member (email, password_hash, nickname, gender)
   VALUES ('test@test.com', 'test', '테스트', 'M');
   SELECT member_id, email, privacy_agreed_at, location_agreed_at FROM member;
   ```
3. `ConsentDemoFrame.java` 맨 아래 `fakeExistingSignup()` 의 `return 1;` 을 위에서 확인한 `member_id` 로 변경
4. `ConsentDemoFrame` 실행 → 두 동의 모두 체크 → 가입하기 → "가입 및 동의 저장 완료" 창 확인
5. Workbench 에서 확인: 두 동의 시각에 방금 누른 시각이 같은 값으로 들어 있으면 성공
   ```sql
   SELECT member_id, email, privacy_agreed_at, location_agreed_at FROM member;
   ```
6. 다시 테스트하려면 동의 시각 비우기:
   ```sql
   UPDATE member SET privacy_agreed_at = NULL, location_agreed_at = NULL WHERE member_id = 1;
   ```

### 4-4. 실제 GUI 연동 후

3장처럼 붙인 뒤 4-2 표의 1~3번과 "모두 동의 후 가입"을 다시 해 봅니다.

- 미동의일 때 member 테이블에 **새 행이 생기지 않아야** 합니다.
- 동의 후 가입하면 **새 회원 행에 동의 시각 2개가 저장**되어야 합니다.

---

## 5. 오류가 날 때

| 오류 메시지 | 원인 / 해결 |
|---|---|
| `db.properties 파일을 읽을 수 없습니다` | `db.properties` 가 `Texi/` 바로 아래에 없음 |
| `Communications link failure` | MySQL 서버가 꺼져 있음 |
| `Unknown column 'privacy_agreed_at'` | `consent_migration.sql` 미실행 |
| `동의 정보를 저장할 회원을 찾지 못했습니다` | 넘긴 member_id(또는 이메일)가 DB 에 없음 |
| `No suitable driver` | `lib/mysql-connector-j-26.10.0.jar` 가 Build Path 에 없음 |
| `beforeSignup 검사를 먼저 해야 합니다` | 가입 버튼 코드에서 ① `beforeSignup()` 을 빼먹음 |
