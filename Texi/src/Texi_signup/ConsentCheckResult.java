package Texi_signup;

/**
 * 동의 검사 결과를 GUI 쪽에 넘겨주는 상자.
 *
 * GUI 에서 할 일 예시:
 *   if (result.isPrivacyMissing())  privacyCheckBox.setForeground(Color.RED);
 *   if (result.isLocationMissing()) locationCheckBox.setForeground(Color.RED);
 *   errorLabel.setText(result.getMessage());
 */
public final class ConsentCheckResult {

    private final boolean privacyMissing;    // 개인정보 동의가 빠졌는가
    private final boolean locationMissing;   // 위치정보 동의가 빠졌는가
    private final String message;            // 화면에 띄울 경고 문구 (모두 동의했으면 null)

    private ConsentCheckResult(boolean privacyMissing, boolean locationMissing, String message) {
        this.privacyMissing = privacyMissing;
        this.locationMissing = locationMissing;
        this.message = message;
    }

    /**
     * 체크박스 두 개의 값으로 동의 상태를 검사한다. (DB 연결 필요 없음)
     *
     * @param privacyAgreed  개인정보 체크박스.isSelected()
     * @param locationAgreed 위치정보 체크박스.isSelected()
     */
    public static ConsentCheckResult check(boolean privacyAgreed, boolean locationAgreed) {
        boolean privacyMissing = !privacyAgreed;
        boolean locationMissing = !locationAgreed;

        // 경고 문구는 이 한 곳에서만 만든다 → 문구를 바꿀 때 여기만 고치면 된다
        String message = null;
        if (privacyMissing && locationMissing) {
            message = "개인정보 수집·이용 및 위치정보 수집·이용에 동의해야 가입할 수 있습니다.";
        } else if (privacyMissing) {
            message = "개인정보 수집·이용에 동의해야 가입할 수 있습니다.";
        } else if (locationMissing) {
            message = "위치정보 수집·이용에 동의해야 가입할 수 있습니다.";
        }
        return new ConsentCheckResult(privacyMissing, locationMissing, message);
    }

    /** 필수 동의를 모두 했으면 true */
    public boolean isOk() {
        return !privacyMissing && !locationMissing;
    }

    public boolean isPrivacyMissing() { return privacyMissing; }
    public boolean isLocationMissing() { return locationMissing; }

    /** 예: "개인정보 수집·이용에 동의해야 가입할 수 있습니다."  (isOk() 이면 null) */
    public String getMessage() { return message; }
}
