package Texi_signup;

/**
 * DB 없이 동의 검사 로직만 확인하는 테스트 (main 으로 실행).
 */
public class ConsentTest {

    private static int pass = 0, fail = 0;

    public static void main(String[] args) {
        // 1) 동의 검사 결과
        ConsentCheckResult none = ConsentCheckResult.check(false, false);
        check("둘 다 미동의 → 두 항목 모두 표시 대상", none.isPrivacyMissing() && none.isLocationMissing());

        ConsentCheckResult noPrivacy = ConsentCheckResult.check(false, true);
        check("개인정보만 빠짐 → 개인정보만 표시 대상",
                noPrivacy.isPrivacyMissing() && !noPrivacy.isLocationMissing());
        check("문구: " + noPrivacy.getMessage(),
                "개인정보 수집·이용에 동의해야 가입할 수 있습니다.".equals(noPrivacy.getMessage()));

        ConsentCheckResult noLocation = ConsentCheckResult.check(true, false);
        check("위치정보만 빠짐 → 위치정보만 표시 대상",
                !noLocation.isPrivacyMissing() && noLocation.isLocationMissing());

        ConsentCheckResult all = ConsentCheckResult.check(true, true);
        check("모두 동의 → 통과, 문구 없음", all.isOk() && all.getMessage() == null);

        // 2) beforeSignup: 미동의면 false + 화면 표시 코드가 호출되는지
        final ConsentCheckResult[] shown = { null };
        final boolean[] cleared = { false };
        ConsentErrorHandler fakeGui = new ConsentErrorHandler() {
            @Override public void showConsentError(ConsentCheckResult r) { shown[0] = r; }
            @Override public void clearConsentError() { cleared[0] = true; }
        };

        boolean allowed = ConsentService.beforeSignup(false, true, fakeGui);
        check("미동의 → 가입 진행 불가(false)", !allowed);
        check("미동의 → GUI 경고 표시 호출됨", shown[0] != null && shown[0].isPrivacyMissing());

        allowed = ConsentService.beforeSignup(true, true, fakeGui);
        check("모두 동의 → 가입 진행 가능(true)", allowed);
        check("모두 동의 → GUI 빨간 표시 지우기 호출됨", cleared[0]);

        // 3) afterSignup 을 미동의 상태로 부르면 (beforeSignup 빼먹은 실수) 바로 알려 주는지
        try {
            ConsentService.afterSignup(1, false, true);
            check("beforeSignup 누락 감지", false);
        } catch (IllegalStateException e) {
            check("beforeSignup 누락 감지 (DB 에 저장 안 함)", true);
        } catch (Exception e) {
            check("beforeSignup 누락 감지 (다른 예외: " + e + ")", false);
        }

        System.out.println("\n결과: 통과 " + pass + " / 실패 " + fail);
    }

    private static void check(String name, boolean ok) {
        System.out.println((ok ? "[통과] " : "[실패] ") + name);
        if (ok) pass++; else fail++;
    }
}
