package taxi.model;

/**
 * 한 회원의 "평점 요약" (평균 평점 + 받은 평점 개수) 을 담고,
 * 그것을 매칭 점수에 넣을 수 있는 0~100 점짜리 "평점 점수"로 바꿔 주는 클래스.
 *
 * [왜 평균 평점을 그대로 쓰지 않나?]
 *   평점 1개(5점)만 받은 신규 회원 A  -> 평균 5.00
 *   평점 50개를 받은 우수 회원 B      -> 평균 4.80
 *   평균만 보면 A 가 더 높지만, A 는 "운 좋게 한 번" 받은 점수일 수 있어서 믿기 어렵다.
 *
 * [해결: 베이지안 평균 (Bayesian average)]
 *   모든 회원이 처음부터 "가상의 평점 PRIOR_COUNT 개 (각 PRIOR_MEAN 점)" 를 받았다고 치고 평균을 낸다.
 *
 *              PRIOR_MEAN x PRIOR_COUNT  +  실제평균 x 실제개수
 *   보정평균 = ------------------------------------------------
 *                       PRIOR_COUNT  +  실제개수
 *
 *   - 평점이 적으면 보정평균은 PRIOR_MEAN(3.5) 근처에 머문다.  (A: 약 3.75)
 *   - 평점이 많아질수록 실제 평균에 가까워진다.               (B: 약 4.68)
 *   -> 평점이 쌓인 사람일수록 그 평점을 더 믿는 효과.
 *
 * 이 클래스는 DB 를 전혀 모른다. 값만 받아서 계산하므로 DB 없이도 테스트할 수 있다.
 */
public class RatingSummary {

    /** 평점이 하나도 없는 사람에게 가정하는 기본 평점 (1~5 의 중간보다 살짝 높게: 신규 회원을 너무 불리하게 하지 않으려고) */
    public static final double PRIOR_MEAN = 3.5;

    /** 가상의 평점 개수. 클수록 "평점이 많이 쌓여야 실제 평균을 믿는다" 는 뜻 */
    public static final int PRIOR_COUNT = 5;

    private static final double MIN_RATING = 1.0;
    private static final double MAX_RATING = 5.0;

    private final long memberId;
    private final double avgRating;   // member.avg_rating (평점이 없으면 0.00)
    private final int ratingCount;    // member.rating_count

    public RatingSummary(long memberId, double avgRating, int ratingCount) {
        // DB 값이 이상하게 들어와도 계산이 망가지지 않도록 생성할 때 한 번 막아 둔다.
        if (ratingCount < 0) {
            throw new IllegalArgumentException("평점 개수는 음수일 수 없습니다.");
        }
        if (ratingCount > 0 && (avgRating < MIN_RATING || avgRating > MAX_RATING)) {
            throw new IllegalArgumentException("평균 평점은 1~5 사이여야 합니다. (현재 " + avgRating + ")");
        }
        this.memberId = memberId;
        this.avgRating = avgRating;
        this.ratingCount = ratingCount;
    }

    public long getMemberId() { return memberId; }
    public double getAvgRating() { return avgRating; }
    public int getRatingCount() { return ratingCount; }

    /** 평점을 한 번이라도 받았는지 (화면에 "신규 회원" 표시할 때 사용) */
    public boolean hasRatings() {
        return ratingCount > 0;
    }

    /** 베이지안 평균으로 보정한 평점 (1.0 ~ 5.0). 위 클래스 설명의 공식 그대로. */
    public double getAdjustedRating() {
        // 평점이 0개면 avgRating 은 0.00 이지만, ratingCount 가 0 이라 곱해서 사라지므로 결과는 PRIOR_MEAN 이 된다.
        return (PRIOR_MEAN * PRIOR_COUNT + avgRating * ratingCount) / (PRIOR_COUNT + ratingCount);
    }

    /**
     * 매칭 점수에 넣을 평점 점수 (0 ~ 100).
     * 보정평균 1점 -> 0점, 5점 -> 100점 으로 직선 변환한다.
     * (매칭 점수 match_score 가 0~100 범위라서 다른 항목(경로 겹침, 우회 거리 등)과 단위를 맞춘 것)
     *
     * 사용 예) 총점 = 0.4 * 경로겹침점수 + 0.3 * 우회점수 + 0.2 * 시간점수 + 0.1 * 상대.getRatingScore()
     */
    public double getRatingScore() {
        double score = (getAdjustedRating() - MIN_RATING) / (MAX_RATING - MIN_RATING) * 100.0;
        // DB 컬럼이 DECIMAL(5,2) 이므로 소수점 둘째 자리까지 반올림
        return Math.round(score * 100.0) / 100.0;
    }

    @Override
    public String toString() {
        return "RatingSummary{member=" + memberId + ", avg=" + avgRating + ", count=" + ratingCount
                + ", adjusted=" + String.format("%.2f", getAdjustedRating())
                + ", score=" + getRatingScore() + "}";
    }
}
