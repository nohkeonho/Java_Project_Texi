package taxi.model;

import java.time.LocalDateTime;

/** member 테이블의 한 행(회원 1명)을 담는 클래스. */
public class Member {

    /** 회원 상태: 정상 / 이용정지 / 탈퇴 */
    public enum Status { ACTIVE, SUSPENDED, WITHDRAWN }

    private long memberId;
    private String email;
    private String passwordHash;      // 비밀번호 "해시값" (원문 아님!)
    private String nickname;
    private String gender;            // "M" 또는 "F"
    private LocalDateTime locationAgreedAt;   // 위치정보 동의 시각 (null이면 미동의)
    private boolean virtual;          // 시뮬레이터용 가상 사용자 여부
    private double avgRating;
    private int ratingCount;
    private Status status = Status.ACTIVE;
    private LocalDateTime createdAt;

    public long getMemberId() { return memberId; }
    public void setMemberId(long memberId) { this.memberId = memberId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public LocalDateTime getLocationAgreedAt() { return locationAgreedAt; }
    public void setLocationAgreedAt(LocalDateTime locationAgreedAt) { this.locationAgreedAt = locationAgreedAt; }
    public boolean isVirtual() { return virtual; }
    public void setVirtual(boolean virtual) { this.virtual = virtual; }
    public double getAvgRating() { return avgRating; }
    public void setAvgRating(double avgRating) { this.avgRating = avgRating; }
    public int getRatingCount() { return ratingCount; }
    public void setRatingCount(int ratingCount) { this.ratingCount = ratingCount; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "Member{id=" + memberId + ", email=" + email + ", nickname=" + nickname
                + ", avgRating=" + avgRating + ", status=" + status + "}";
    }
}
