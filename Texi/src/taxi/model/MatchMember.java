package taxi.model;

/** match_member 테이블의 한 행(한 매칭 안의 참여자 1명)을 담는 클래스. */
public class MatchMember {

    /** 매칭 제안에 대한 응답: 대기 / 수락 / 거절 */
    public enum Response { PENDING, ACCEPTED, REJECTED }

    private long matchMemberId;
    private long matchId;
    private long requestId;
    private long memberId;

    private int pickupOrder;     // 탑승 순서 (1, 2, ...)
    private int dropoffOrder;    // 하차 순서 (1, 2, ...)
    private int ownDistanceM;    // 이 사람이 실제로 탄 거리(m) -> 거리 비례 요금 분담의 기준
    private int detourM;         // 합승 때문에 늘어난 우회 거리(m)
    private Double matchScore;   // 매칭 점수 (계산 전이면 null)
    private int fareShare;       // 이 사람이 내야 할 분담 요금(원)
    private Response response = Response.PENDING;

    public long getMatchMemberId() { return matchMemberId; }
    public void setMatchMemberId(long matchMemberId) { this.matchMemberId = matchMemberId; }
    public long getMatchId() { return matchId; }
    public void setMatchId(long matchId) { this.matchId = matchId; }
    public long getRequestId() { return requestId; }
    public void setRequestId(long requestId) { this.requestId = requestId; }
    public long getMemberId() { return memberId; }
    public void setMemberId(long memberId) { this.memberId = memberId; }
    public int getPickupOrder() { return pickupOrder; }
    public void setPickupOrder(int pickupOrder) { this.pickupOrder = pickupOrder; }
    public int getDropoffOrder() { return dropoffOrder; }
    public void setDropoffOrder(int dropoffOrder) { this.dropoffOrder = dropoffOrder; }
    public int getOwnDistanceM() { return ownDistanceM; }
    public void setOwnDistanceM(int ownDistanceM) { this.ownDistanceM = ownDistanceM; }
    public int getDetourM() { return detourM; }
    public void setDetourM(int detourM) { this.detourM = detourM; }
    public Double getMatchScore() { return matchScore; }
    public void setMatchScore(Double matchScore) { this.matchScore = matchScore; }
    public int getFareShare() { return fareShare; }
    public void setFareShare(int fareShare) { this.fareShare = fareShare; }
    public Response getResponse() { return response; }
    public void setResponse(Response response) { this.response = response; }

    @Override
    public String toString() {
        return "MatchMember{id=" + matchMemberId + ", match=" + matchId + ", member=" + memberId
                + ", fareShare=" + fareShare + ", response=" + response + "}";
    }
}
