package Texi_signup;

import java.sql.SQLException;

/**
 * 기존 회원가입 API 앞뒤에 끼워 넣는 동의 처리 도우미.
 *
 * 기존 회원가입·로그인 코드는 한 줄도 고치지 않는다. 가입 버튼 코드에서 이렇게 감싸기만 하면 된다.
 *
 *   boolean p = privacyCheckBox.isSelected();
 *   boolean l = locationCheckBox.isSelected();
 *
 *   // 1) 가입 전: 동의 검사. 미동의면 경고를 띄우고 false → 여기서 return 해서 가입을 막는다
 *   if (!ConsentService.beforeSignup(p, l, consentHandler)) {
 *       return;
 *   }
 *
 *   // 2) 기존 회원가입 API 호출 (원래 코드 그대로)
 *   long memberId = 기존회원가입API(...);
 *
 *   // 3) 가입 후: 동의 시각을 DB 에 저장
 *   ConsentService.afterSignup(memberId, p, l);
 */
public final class ConsentService {

    private static final ConsentDAO DAO = new ConsentDAO();

    private ConsentService() {
    }

    /**
     * [가입 전] 동의 검사.
     *
     * @param handler 미동의일 때 화면 표시를 맡을 GUI 코드 (빨간 글자 + 경고 문구 등)
     * @return true  = 모두 동의함 → 기존 회원가입 API 를 호출해도 됨
     *         false = 동의 빠짐 → handler 로 경고를 보여 줬으니, 호출한 쪽은 가입을 멈춰야 함
     */
    public static boolean beforeSignup(boolean privacyAgreed, boolean locationAgreed,
                                       ConsentErrorHandler handler) {
        ConsentCheckResult result = ConsentCheckResult.check(privacyAgreed, locationAgreed);
        if (!result.isOk()) {
            if (handler != null) {
                handler.showConsentError(result);
            }
            return false;
        }
        if (handler != null) {
            handler.clearConsentError();   // 전에 띄운 빨간 표시가 있으면 지운다
        }
        return true;
    }

    /**
     * [가입 후] 기존 API 가 만든 회원 번호로 동의 시각을 저장한다.
     *
     * 동의를 안 했으면 beforeSignup 에서 막혀야 정상이므로,
     * 여기까지 왔는데 동의가 빠져 있으면 "가입 버튼 코드에서 beforeSignup 을 빼먹은 것"이다.
     * 그런 실수를 빨리 알아차리도록 예외를 던진다.
     *
     * @throws SQLException 저장 실패 (DB 오류 또는 해당 회원 없음)
     */
    public static void afterSignup(long memberId, boolean privacyAgreed, boolean locationAgreed)
            throws SQLException {
        requireConsented(privacyAgreed, locationAgreed);
        if (!DAO.saveConsents(memberId, privacyAgreed, locationAgreed)) {
            throw new SQLException("동의 정보를 저장할 회원을 찾지 못했습니다. (member_id=" + memberId + ")");
        }
    }

    /** [가입 후] 기존 API 가 회원 번호를 돌려주지 않는 경우, 이메일로 저장한다. */
    public static void afterSignupByEmail(String email, boolean privacyAgreed, boolean locationAgreed)
            throws SQLException {
        requireConsented(privacyAgreed, locationAgreed);
        if (!DAO.saveConsentsByEmail(email, privacyAgreed, locationAgreed)) {
            throw new SQLException("동의 정보를 저장할 회원을 찾지 못했습니다. (email=" + email + ")");
        }
    }

    private static void requireConsented(boolean privacyAgreed, boolean locationAgreed) {
        ConsentCheckResult result = ConsentCheckResult.check(privacyAgreed, locationAgreed);
        if (!result.isOk()) {
            throw new IllegalStateException("beforeSignup 검사를 먼저 해야 합니다: " + result.getMessage());
        }
    }
}
