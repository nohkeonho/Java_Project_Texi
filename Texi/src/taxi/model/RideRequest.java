package taxi.model;

import java.time.LocalDateTime;

/**
 * ride_request 테이블의 한 행(합승 요청 1건)을 담는 클래스.
 *
 * [동성 합승만 허용]
 *   이 앱은 같은 성별끼리만 합승할 수 있다. (계획서: 여객자동차 운수사업법령상 동성 합승 요건)
 *   그래서 요청마다 "동성만 / 혼성 허용" 을 고르는 칸이 없다.
 *   성별은 요청이 아니라 회원(member.gender)에 저장되어 있고, 매칭할 때 DAO 가 회원 성별을 비교한다.
 */
public class RideRequest {

    /** 요청 상태: 대기중 / 매칭됨 / 취소 / 시간 만료 */
    public enum Status { WAITING, MATCHED, CANCELLED, EXPIRED }

    /** 택시 한 대의 승객 좌석 수 (일반 중형 택시 기준) */
    public static final int TAXI_SEATS = 4;

    /** 요청 1건의 최대 인원. 합승 상대가 최소 1명은 타야 하므로 좌석 수 - 1 */
    public static final int MAX_PASSENGERS = TAXI_SEATS - 1;

    private long requestId;
    private long memberId;

    private String originName;        // 예: "선문대 정문 앞" (정확한 주소가 아니라 구역 이름)
    private double originLat;
    private double originLng;
    private String destName;
    private double destLat;
    private double destLng;

    private int passengerCount = 1;   // 본인 포함 탑승 인원 (1 ~ MAX_PASSENGERS)

    private LocalDateTime departFrom;   // 가장 이른 출발 시각
    private LocalDateTime departUntil;  // 가장 늦은 출발 시각

    private int maxDetourM = 2000;      // 우회 허용 거리(m)

    // 지도 API 계산 결과. 아직 계산 전이면 null 이므로 int 가 아니라 Integer 를 쓴다.
    private Integer estDistanceM;
    private Integer estFare;

    private Status status = Status.WAITING;
    private LocalDateTime createdAt;

    public long getRequestId() { return requestId; }
    public void setRequestId(long requestId) { this.requestId = requestId; }
    public long getMemberId() { return memberId; }
    public void setMemberId(long memberId) { this.memberId = memberId; }
    public String getOriginName() { return originName; }
    public void setOriginName(String originName) { this.originName = originName; }
    public double getOriginLat() { return originLat; }
    public void setOriginLat(double originLat) { this.originLat = originLat; }
    public double getOriginLng() { return originLng; }
    public void setOriginLng(double originLng) { this.originLng = originLng; }
    public String getDestName() { return destName; }
    public void setDestName(String destName) { this.destName = destName; }
    public double getDestLat() { return destLat; }
    public void setDestLat(double destLat) { this.destLat = destLat; }
    public double getDestLng() { return destLng; }
    public void setDestLng(double destLng) { this.destLng = destLng; }
    public int getPassengerCount() { return passengerCount; }
    public void setPassengerCount(int passengerCount) { this.passengerCount = passengerCount; }
    public LocalDateTime getDepartFrom() { return departFrom; }
    public void setDepartFrom(LocalDateTime departFrom) { this.departFrom = departFrom; }
    public LocalDateTime getDepartUntil() { return departUntil; }
    public void setDepartUntil(LocalDateTime departUntil) { this.departUntil = departUntil; }
    public int getMaxDetourM() { return maxDetourM; }
    public void setMaxDetourM(int maxDetourM) { this.maxDetourM = maxDetourM; }
    public Integer getEstDistanceM() { return estDistanceM; }
    public void setEstDistanceM(Integer estDistanceM) { this.estDistanceM = estDistanceM; }
    public Integer getEstFare() { return estFare; }
    public void setEstFare(Integer estFare) { this.estFare = estFare; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "RideRequest{id=" + requestId + ", member=" + memberId + ", " + originName + " -> " + destName
                + ", " + passengerCount + "명, " + departFrom + "~" + departUntil + ", status=" + status + "}";
    }
}
