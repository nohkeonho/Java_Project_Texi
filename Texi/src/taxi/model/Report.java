package taxi.model;

import java.time.LocalDateTime;

/** report 테이블의 한 행(신고 1건)을 담는 클래스. */
public class Report {

    /** 신고 사유: 노쇼 / 욕설·불쾌행위 / 요금 분쟁 / 경로 이탈 / 기타 */
    public enum Reason { NO_SHOW, ABUSE, FARE_DISPUTE, ROUTE_DEVIATION, OTHER }

    /** 처리 상태: 접수 / 검토중 / 처리완료 / 반려 */
    public enum Status { RECEIVED, REVIEWING, RESOLVED, REJECTED }

    private long reportId;
    private long matchId;
    private long reporterId;   // 신고한 사람
    private long reportedId;   // 신고당한 사람
    private Reason reason;
    private String detail;     // 상세 내용 (null 가능)
    private Status status = Status.RECEIVED;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;

    public long getReportId() { return reportId; }
    public void setReportId(long reportId) { this.reportId = reportId; }
    public long getMatchId() { return matchId; }
    public void setMatchId(long matchId) { this.matchId = matchId; }
    public long getReporterId() { return reporterId; }
    public void setReporterId(long reporterId) { this.reporterId = reporterId; }
    public long getReportedId() { return reportedId; }
    public void setReportedId(long reportedId) { this.reportedId = reportedId; }
    public Reason getReason() { return reason; }
    public void setReason(Reason reason) { this.reason = reason; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }

    @Override
    public String toString() {
        return "Report{id=" + reportId + ", match=" + matchId + ", " + reporterId + " -> " + reportedId
                + ", reason=" + reason + ", status=" + status + "}";
    }
}
