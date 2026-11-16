package taxi.auth;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.regex.Pattern;

import taxi.dao.MemberDAO;
import taxi.model.Member;

/**
 * 회원가입 / 로그인 / 중복검사를 담당하는 "인증 API" 클래스.
 *
 * [계층 구조]  Swing 화면  →  AuthService(규칙 검사)  →  MemberDAO(SQL 실행)  →  MySQL member 테이블
 *   - 화면은 AuthService 만 부르고, SQL 은 전혀 모른다.
 *   - AuthService 는 "입력이 올바른지, 중복인지, 비밀번호가 맞는지" 같은 규칙만 판단한다.
 *   - 실제 DB 작업은 기존 MemberDAO 가 그대로 담당한다. (MemberDAO 는 수정하지 않음)
 *
 * [schema.sql 에 맞춘 부분]
 *   - 로그인 ID는 member.email 컬럼 (UNIQUE) → 아이디 중복검사 = 이메일 중복검사
 *   - nickname(UNIQUE, NOT NULL), gender('M'/'F', NOT NULL) 도 가입 때 받아야 한다.
 *   - 솔트 컬럼이 없으므로 PasswordHasher 가 솔트+해시를 password_hash 한 칸에 합쳐 저장한다.
 *   - status 가 SUSPENDED(이용정지), WITHDRAWN(탈퇴)인 회원은 로그인을 막는다.
 */
public class AuthService {

    // ===== 입력 규칙 (정규식) =====
    // 이메일: 아이디@도메인.최상위도메인 형태 (간단한 형식 검사)
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final int EMAIL_MAX = 100;   // schema: email VARCHAR(100)

    // 비밀번호: 영문+숫자만, 두 종류 모두 포함, 8자 이상
    //   (?=.*[A-Za-z]) 영문 최소 1개 / (?=.*\\d) 숫자 최소 1개 / [A-Za-z\\d]{8,} 영문·숫자로만 8자 이상
    //   "정확히 8자리"만 허용하려면 {8,} 를 {8} 로 바꾸면 된다.
    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,}$");

    // 닉네임: 한글·영문·숫자 2~10자 (schema 는 30자까지지만 화면에 보이기 좋게 10자로 제한)
    private static final Pattern NICKNAME_PATTERN = Pattern.compile("^[가-힣A-Za-z0-9]{2,10}$");

    private final MemberDAO memberDAO;

    /** 실제 프로그램에서 쓰는 생성자 */
    public AuthService() {
        this(new MemberDAO());
    }

    /** 테스트 때 가짜 DAO 를 넣을 수 있도록 열어 둔 생성자 */
    public AuthService(MemberDAO memberDAO) {
        this.memberDAO = memberDAO;
    }

    // =================================================================
    // 1. 중복검사 (회원가입 화면의 "중복확인" 버튼)
    // =================================================================

    /** 아이디(이메일) 사용 가능 여부. 사용 가능하면 true. */
    public boolean isEmailAvailable(String email) throws AuthException {
        String normalized = normalizeEmail(email);
        validateEmail(normalized);
        try {
            return !memberDAO.existsByEmail(normalized);
        } catch (SQLException e) {
            throw dbError(e);
        }
    }

    /** 닉네임 사용 가능 여부. schema 에서 nickname 도 UNIQUE 라서 같이 검사한다. */
    public boolean isNicknameAvailable(String nickname) throws AuthException {
        String trimmed = trim(nickname);
        validateNickname(trimmed);
        try {
            return !memberDAO.existsByNickname(trimmed);
        } catch (SQLException e) {
            throw dbError(e);
        }
    }

    // =================================================================
    // 2. 회원가입
    // =================================================================

    /**
     * 회원가입. 성공하면 새로 만들어진 member_id 를 돌려준다.
     *
     * @param gender "M" 또는 "F"
     */
    public long signUp(String email, String password, String passwordConfirm,
                       String nickname, String gender) throws AuthException {
        // (1) 입력값 정리 + 형식 검사 : DB 에 가기 전에 걸러서 불필요한 쿼리를 줄인다.
        String normalizedEmail = normalizeEmail(email);
        String trimmedNickname = trim(nickname);

        validateEmail(normalizedEmail);
        validatePassword(password);
        if (!password.equals(passwordConfirm)) {
            throw new AuthException("비밀번호 확인이 일치하지 않습니다.");
        }
        validateNickname(trimmedNickname);
        if (!"M".equals(gender) && !"F".equals(gender)) {
            throw new AuthException("성별을 선택해주세요.");
        }

        try {
            // (2) 중복검사 : "중복확인" 버튼을 눌렀더라도 가입 직전에 한 번 더 확인한다.
            if (memberDAO.existsByEmail(normalizedEmail)) {
                throw new AuthException("이미 사용 중인 아이디(이메일)입니다.");
            }
            if (memberDAO.existsByNickname(trimmedNickname)) {
                throw new AuthException("이미 사용 중인 닉네임입니다.");
            }

            // (3) Member 객체를 만들어 기존 MemberDAO.insert() 로 저장
            Member m = new Member();
            m.setEmail(normalizedEmail);
            m.setPasswordHash(PasswordHasher.encode(password)); // 원문 대신 해시 저장
            m.setNickname(trimmedNickname);
            m.setGender(gender);
            m.setVirtual(false);                                // 실제 사용자
            return memberDAO.insert(m);

        } catch (SQLIntegrityConstraintViolationException e) {
            // (4) 마지막 안전장치: (2)와 (3) 사이에 누가 같은 값으로 먼저 가입하면
            //     DB 의 UNIQUE 제약조건이 INSERT 를 막고 이 예외가 발생한다.
            //     MySQL 메시지 예: "Duplicate entry 'a@b.com' for key 'member.uk_member_email'"
            String msg = String.valueOf(e.getMessage());
            if (msg.contains("uk_member_nickname")) {
                throw new AuthException("이미 사용 중인 닉네임입니다.", e);
            }
            throw new AuthException("이미 사용 중인 아이디(이메일)입니다.", e);
        } catch (SQLException e) {
            throw dbError(e);
        }
    }

    // =================================================================
    // 3. 로그인
    // =================================================================

    /**
     * 로그인. 성공하면 회원 정보(Member)를 돌려준다.
     * 돌려준 Member 의 memberId 를 합승 요청(RideRequestDAO) 등 다른 DAO 에 넘기면 된다.
     */
    public Member login(String email, String password) throws AuthException {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail.isEmpty() || password == null || password.isEmpty()) {
            throw new AuthException("아이디(이메일)와 비밀번호를 입력해주세요.");
        }

        Member member;
        try {
            member = memberDAO.findByEmail(normalizedEmail);
        } catch (SQLException e) {
            throw dbError(e);
        }

        // 보안: "아이디 없음"과 "비밀번호 틀림"을 같은 메시지로 보여준다.
        // (다르게 알려주면 어떤 이메일이 가입돼 있는지 알아낼 수 있다.)
        if (member == null || !PasswordHasher.matches(password, member.getPasswordHash())) {
            throw new AuthException("아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        // 비밀번호가 맞는 사람에게만 계정 상태를 알려준다.
        if (member.getStatus() == Member.Status.SUSPENDED) {
            throw new AuthException("신고 누적으로 이용이 정지된 계정입니다.");
        }
        if (member.getStatus() == Member.Status.WITHDRAWN) {
            throw new AuthException("탈퇴한 계정입니다.");
        }

        // 로그인 이후에는 해시값이 필요 없으므로 지워서 화면 쪽에 넘기지 않는다.
        member.setPasswordHash(null);
        return member;
    }

    // =================================================================
    // 내부 도우미 메서드
    // =================================================================

    /** 앞뒤 공백 제거 + 소문자 변환 → "A@b.com" 과 "a@b.com" 이 따로 가입되는 것을 막는다. */
    private String normalizeEmail(String email) {
        return trim(email).toLowerCase();
    }

    private String trim(String s) {
        return (s == null) ? "" : s.trim();
    }

    private void validateEmail(String email) throws AuthException {
        if (email.isEmpty()) {
            throw new AuthException("아이디(이메일)를 입력해주세요.");
        }
        if (email.length() > EMAIL_MAX || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new AuthException("이메일 형식으로 입력해주세요. (예: taxi@sunmoon.ac.kr)");
        }
    }

    private void validatePassword(String password) throws AuthException {
        if (password == null || password.isEmpty()) {
            throw new AuthException("비밀번호를 입력해주세요.");
        }
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            throw new AuthException("비밀번호는 영문과 숫자를 모두 포함한 8자 이상이어야 합니다. (특수문자·공백 불가)");
        }
    }

    private void validateNickname(String nickname) throws AuthException {
        if (nickname.isEmpty()) {
            throw new AuthException("닉네임을 입력해주세요.");
        }
        if (!NICKNAME_PATTERN.matcher(nickname).matches()) {
            throw new AuthException("닉네임은 한글·영문·숫자 2~10자로 입력해주세요.");
        }
    }

    /** DB 오류는 사용자에게 자세한 SQL 내용을 보여주지 않고, 원인은 cause 에 담아 둔다. */
    private AuthException dbError(SQLException e) {
        return new AuthException("서버(DB) 연결에 문제가 발생했습니다. 잠시 후 다시 시도해주세요.", e);
    }
}
