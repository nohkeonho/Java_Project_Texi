package taxi.model;

import java.time.LocalDateTime;

/** ride_match 테이블의 한 행(합승 한 팀 = 택시 1대)을 담는 클래스. */
public class RideMatch {

    /** 매칭 상태: 제안됨 / 모두 수락(확정) / 완료 / 취소 */
    public enum Status { PROPOSED, CONFIRMED, COMPLETED, CANCELLED }

    private long matchId;
    private Status status = Status.PROPOSED;
    private Integer totalDistanceM;   // 택시 총 이동 거리(m), 아직 계산 전이면 null
    private Integer totalFare;        // 택시 총 예상 요금(원)
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    public long getMatchId() { return matchId; }
    public void setMatchId(long matchId) { this.matchId = matchId; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Integer getTotalDistanceM() { return totalDistanceM; }
    public void setTotalDistanceM(Integer totalDistanceM) { this.totalDistanceM = totalDistanceM; }
    public Integer getTotalFare() { return totalFare; }
    public void setTotalFare(Integer totalFare) { this.totalFare = totalFare; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    @Override
    public String toString() {
        return "RideMatch{id=" + matchId + ", status=" + status + ", totalFare=" + totalFare + "}";
    }
}
