package Texi_signup;

/**
 * "동의 안 하고 가입하려 할 때 화면에 어떻게 보여 줄지"를 GUI 쪽에서 채워 넣는 자리(인터페이스).
 *
 * 인터페이스를 쓰는 이유:
 *  동의 처리 코드(ConsentSignupFlow)는 "경고를 보여 줘야 한다"는 것만 알고,
 *  "글자를 빨갛게 바꾼다" 같은 구체적인 방법은 GUI 개발자가 정한다.
 *  → GUI 디자인이 바뀌어도 동의 처리 코드는 고칠 필요가 없다.
 *
 * GUI 쪽 구현 예시:
 *   ConsentErrorHandler handler = new ConsentErrorHandler() {
 *       public void showConsentError(ConsentCheckResult r) {
 *           privacyCheckBox.setForeground(r.isPrivacyMissing() ? Color.RED : Color.BLACK);
 *           locationCheckBox.setForeground(r.isLocationMissing() ? Color.RED : Color.BLACK);
 *           consentErrorLabel.setText(r.getMessage());
 *       }
 *       public void clearConsentError() {
 *           privacyCheckBox.setForeground(Color.BLACK);
 *           locationCheckBox.setForeground(Color.BLACK);
 *           consentErrorLabel.setText(" ");
 *       }
 *   };
 */
public interface ConsentErrorHandler {

    /**
     * 필수 동의가 빠졌을 때 호출된다.
     * @param result 빠진 항목(isPrivacyMissing / isLocationMissing)과 경고 문구(getMessage)
     */
    void showConsentError(ConsentCheckResult result);

    /** 동의 문제가 해결됐을 때 호출된다. (빨간 표시를 원래대로) 필요 없으면 구현 안 해도 됨 */
    default void clearConsentError() {
    }
}
