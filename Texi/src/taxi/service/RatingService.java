package taxi.service;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

import taxi.dao.MemberDAO;
import taxi.dao.RatingDAO;
import taxi.model.Member;
import taxi.model.Rating;

/**
 * 평점 기능의 "창구" 역할을 하는 클래스.
 *
 * 역할 나누기
 *   - 화면(Swing)  : 버튼을 누르면 이 클래스의 메서드를 부른다.
 *   - RatingService : 입력값을 검사하고, 보낼지 말지 결정한다.   <- 이 클래스
 *   - RatingDAO     : 실제로 SQL 을 실행한다. (기존 파일, 수정하지 않음)
 *
 * 이렇게 나누면 화면 코드에는 "검사 규칙"이나 SQL 이 섞이지 않아서 읽기 쉬워진다.
 */
public class RatingService {

    private static final int MIN_SCORE = 1;
    private static final int MAX_SCORE = 5;

    private final RatingDAO ratingDAO = new RatingDAO();
    private final MemberDAO memberDAO = new MemberDAO();

    /**
     * 평점 제출.
     *
     * @param score 선택한 평점(1~5). 평점을 선택하지 않았으면 null 을 넘긴다.
     *              (int 는 null 을 담을 수 없어서 Integer 를 쓴다)
     * @return DB 에 저장했으면 true, 평점 입력이 없어서 건너뛰었으면 false
     *
     * @throws IllegalArgumentException 점수가 1~5 가 아니거나 자기 자신을 평가하려 할 때
     * @throws IllegalStateException    이미 평점을 줬거나, 완료된 합승의 동승자가 아닐 때
     * @throws SQLException             DB 연결 실패 등 DB 쪽 문제가 생겼을 때
     */
    public boolean submit(long matchId, long raterId, long rateeId, Integer score) throws SQLException {
        // 1) 평점 입력이 없으면 아무것도 하지 않고 넘어간다.
        if (score == null) {
            return false;
        }
        // 2) 1~5점 정수만 허용 (0.5점 단위는 Integer 타입이라 애초에 들어올 수 없다)
        if (score < MIN_SCORE || score > MAX_SCORE) {
            throw new IllegalArgumentException("평점은 1점부터 5점 사이여야 합니다.");
        }
        // 3) 이미 평점을 줬는지 미리 확인해서, 알아보기 쉬운 메시지로 알려 준다.
        if (ratingDAO.exists(matchId, raterId, rateeId)) {
            throw new IllegalStateException("이미 이 동승자에게 평점을 등록했습니다.");
        }

        // 4) 기존 모델 클래스(Rating)에 값을 담아 DAO 에 넘긴다.
        Rating rating = new Rating();
        rating.setMatchId(matchId);
        rating.setRaterId(raterId);
        rating.setRateeId(rateeId);
        rating.setScore(score);
        // 한줄평(comment)은 받지 않으므로 넣지 않는다. (null 로 저장됨)

        try {
            // RatingDAO.insert 가 "저장 + 받은 사람의 평균 평점 갱신"을 한 트랜잭션으로 처리한다.
            ratingDAO.insert(rating);
        } catch (SQLIntegrityConstraintViolationException e) {
            // 버튼을 빠르게 두 번 눌러 3)의 검사를 둘 다 통과한 경우, DB 의 UNIQUE 제약이 막아 준다.
            throw new IllegalStateException("이미 이 동승자에게 평점을 등록했습니다.", e);
        }
        return true;
    }

    /**
     * 회원의 평균 평점 조회. (member 테이블에 저장된 값이라 계산 없이 바로 읽는다)
     * 받은 평점이 없으면 0.0 이 나오므로, 화면에서는 getRatingCount 와 함께 써서
     * "평점 없음"으로 표시할지 정하면 된다.
     *
     * @throws IllegalArgumentException 존재하지 않는 회원일 때
     */
    public double getAverageRating(long memberId) throws SQLException {
        return findMember(memberId).getAvgRating();
    }

    /** 회원이 받은 평점 개수 조회. */
    public int getRatingCount(long memberId) throws SQLException {
        return findMember(memberId).getRatingCount();
    }

    // 회원을 찾고, 없으면 예외를 던지는 공통 메서드 (위 두 메서드의 중복 코드를 줄이기 위해 분리)
    private Member findMember(long memberId) throws SQLException {
        Member member = memberDAO.findById(memberId);
        if (member == null) {
            throw new IllegalArgumentException("존재하지 않는 회원입니다. (member_id=" + memberId + ")");
        }
        return member;
    }
}
