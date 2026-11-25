package taxi.model;

/**
 * 신고 누적 검사 결과 1건을 담는 클래스.
 *
 * ReportPenaltyService.evaluate() / preview() 가 돌려준다.
 * 화면에서는 이 객체 하나로 "몇 번 신고됐는지, 경고 대상인지, 방금 정지됐는지"를 모두 알 수 있다.
 */
public class PenaltyResult {

    /** 이번 검사로 회원 상태에 일어난 변화 */
    public enum Action {
        NONE,       // 변화 없음
        SUSPENDED,  // 이번에 기준을 넘어서 이용정지됨 (ACTIVE -> SUSPENDED)
        RESTORED    // 신고가 반려되어 기준 아래로 내려가서 정지가 풀림 (SUSPENDED -> ACTIVE)
    }

    private final long memberId;
    private final int validReporterCount;   // 반려되지 않은 신고를 한 "서로 다른 사람" 수
    private final int threshold;            // 이용정지 기준 (예: 3명)
    private final Member.Status status;     // 검사가 끝난 뒤의 회원 상태
    private final Action action;
    private final int cancelledRequests;    // 정지되면서 취소된 대기 중 요청 수
    private final int cancelledMatches;     // 정지되면서 취소된 "제안됨" 매칭 수

    public PenaltyResult(long memberId, int validReporterCount, int threshold, Member.Status status,
                         Action action, int cancelledRequests, int cancelledMatches) {
        this.memberId = memberId;
        this.validReporterCount = validReporterCount;
        this.threshold = threshold;
        this.status = status;
        this.action = action;
        this.cancelledRequests = cancelledRequests;
        this.cancelledMatches = cancelledMatches;
    }

    public long getMemberId() { return memberId; }
    public int getValidReporterCount() { return validReporterCount; }
    public int getThreshold() { return threshold; }
    public Member.Status getStatus() { return status; }
    public Action getAction() { return action; }
    public int getCancelledRequests() { return cancelledRequests; }
    public int getCancelledMatches() { return cancelledMatches; }

    /** 이용정지까지 남은 신고 수 (이미 기준 이상이면 0) */
    public int getRemaining() {
        return Math.max(0, threshold - validReporterCount);
    }

    /**
     * 경고 대상인지. (정상 회원인데 1명만 더 신고하면 정지되는 상태)
     * 로그인 직후나 마이페이지에서 경고 문구를 띄울지 정할 때 쓴다.
     */
    public boolean isWarning() {
        return status == Member.Status.ACTIVE && getRemaining() == 1;
    }

    /** 화면에 그대로 보여 줄 수 있는 안내 문구. 보여 줄 내용이 없으면 null. */
    public String getMessage() {
        // 같은 사람의 신고는 1명으로 세므로 "건"이 아니라 "명"으로 안내한다.
        if (status == Member.Status.SUSPENDED) {
            return "동승자 " + validReporterCount + "명에게 신고를 받아 이용이 정지되었습니다.";
        }
        if (isWarning()) {
            return "동승자 " + validReporterCount + "명에게 신고를 받았습니다. "
                 + getRemaining() + "명에게 더 신고를 받으면 이용이 정지됩니다.";
        }
        return null;
    }

    @Override
    public String toString() {
        return "PenaltyResult{member=" + memberId + ", reporters=" + validReporterCount + "/" + threshold
                + ", status=" + status + ", action=" + action
                + ", cancelledRequests=" + cancelledRequests + ", cancelledMatches=" + cancelledMatches + "}";
    }
}
