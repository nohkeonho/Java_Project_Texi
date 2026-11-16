package taxi.model;

import java.time.LocalDateTime;

/** rating 테이블의 한 행(평점 1건)을 담는 클래스. */
public class Rating {

    private long ratingId;
    private long matchId;
    private long raterId;    // 평점을 준 사람
    private long rateeId;    // 평점을 받은 사람
    private int score;       // 1 ~ 5
    private String comment;  // 한줄평 (null 가능)
    private LocalDateTime createdAt;

    public long getRatingId() { return ratingId; }
    public void setRatingId(long ratingId) { this.ratingId = ratingId; }
    public long getMatchId() { return matchId; }
    public void setMatchId(long matchId) { this.matchId = matchId; }
    public long getRaterId() { return raterId; }
    public void setRaterId(long raterId) { this.raterId = raterId; }
    public long getRateeId() { return rateeId; }
    public void setRateeId(long rateeId) { this.rateeId = rateeId; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "Rating{id=" + ratingId + ", match=" + matchId + ", " + raterId + " -> " + rateeId
                + ", score=" + score + "}";
    }
}
