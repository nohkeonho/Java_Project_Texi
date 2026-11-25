package taxi.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import taxi.db.DBConnection;
import taxi.model.Rating;
import taxi.model.RatingSummary;

/** rating 테이블(평점)을 다루는 DAO. */
public class RatingDAO {

    /** 평점 요약 조회에 쓰는 컬럼 (member 테이블에 미리 계산해 둔 값) */
    private static final String SUMMARY_COLUMNS = "member_id, avg_rating, rating_count";

    /**
     * 평점 등록. 새로 만들어진 rating_id 를 돌려준다.
     *
     * 한 트랜잭션 안에서 아래 순서로 처리한다.
     *   1) 평점을 줄 자격 확인 (완료된 매칭의 참여자끼리인지)
     *   2) rating 테이블에 INSERT
     *   3) 받은 사람(member)의 평균 평점·평점 개수를 다시 계산해서 UPDATE
     * 2)만 성공하고 3)이 실패하면 평균이 틀어지기 때문에 반드시 한 묶음으로 처리해야 한다.
     *
     * @throws IllegalArgumentException 점수가 1~5 범위를 벗어났을 때
     * @throws IllegalStateException    완료된 매칭의 참여자끼리가 아닐 때
     * @throws java.sql.SQLIntegrityConstraintViolationException
     *         같은 매칭에서 같은 사람에게 이미 평점을 줬을 때 (UNIQUE 제약 위반)
     */
    public long insert(Rating r) throws SQLException {
        // DB 의 CHECK 제약이 있지만, MySQL 8.0.16 미만에서는 무시되므로 자바에서도 한 번 더 검사한다.
        if (r.getScore() < 1 || r.getScore() > 5) {
            throw new IllegalArgumentException("평점은 1~5 사이여야 합니다.");
        }
        if (r.getRaterId() == r.getRateeId()) {
            throw new IllegalArgumentException("자기 자신에게는 평점을 줄 수 없습니다.");
        }

        String insertSql = "INSERT INTO rating (match_id, rater_id, ratee_id, score, comment) "
                         + "VALUES (?, ?, ?, ?, ?)";
        // 받은 사람의 평균과 개수를 rating 테이블에서 다시 계산해서 member 에 저장
        String updateSql = "UPDATE member m SET "
                         + " m.avg_rating   = (SELECT ROUND(AVG(score), 2) FROM rating WHERE ratee_id = m.member_id), "
                         + " m.rating_count = (SELECT COUNT(*) FROM rating WHERE ratee_id = m.member_id) "
                         + "WHERE m.member_id = ?";

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                if (!RideMatchDAO.isCompletedWithBothMembers(con, r.getMatchId(), r.getRaterId(), r.getRateeId())) {
                    throw new IllegalStateException("완료된 매칭에 함께 참여한 사람에게만 평점을 줄 수 있습니다.");
                }

                long ratingId;
                try (PreparedStatement ps = con.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setLong(1, r.getMatchId());
                    ps.setLong(2, r.getRaterId());
                    ps.setLong(3, r.getRateeId());
                    ps.setInt(4, r.getScore());
                    ps.setString(5, r.getComment());   // null 이면 DB 에 NULL 로 들어감
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("평점 번호를 받아오지 못했습니다.");
                        }
                        ratingId = keys.getLong(1);
                    }
                }

                try (PreparedStatement ps = con.prepareStatement(updateSql)) {
                    ps.setLong(1, r.getRateeId());
                    ps.executeUpdate();
                }

                con.commit();
                return ratingId;
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    /** 어떤 회원이 받은 평점 목록 (최신순). */
    public List<Rating> findByRateeId(long rateeId) throws SQLException {
        String sql = "SELECT * FROM rating WHERE ratee_id = ? ORDER BY created_at DESC";
        List<Rating> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, rateeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Rating r = new Rating();
                    r.setRatingId(rs.getLong("rating_id"));
                    r.setMatchId(rs.getLong("match_id"));
                    r.setRaterId(rs.getLong("rater_id"));
                    r.setRateeId(rs.getLong("ratee_id"));
                    r.setScore(rs.getInt("score"));
                    r.setComment(rs.getString("comment"));
                    r.setCreatedAt(DaoUtil.getDateTime(rs, "created_at"));
                    list.add(r);
                }
            }
        }
        return list;
    }

    /** 이 매칭에서 내가 이미 이 사람에게 평점을 줬는지 확인 (화면에서 버튼을 막을 때 사용). */
    public boolean exists(long matchId, long raterId, long rateeId) throws SQLException {
        String sql = "SELECT 1 FROM rating WHERE match_id = ? AND rater_id = ? AND ratee_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, matchId);
            ps.setLong(2, raterId);
            ps.setLong(3, rateeId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    // ==================================================================
    // 매칭 점수용 평점 조회 API
    // ==================================================================
    //
    // rating 테이블을 매번 AVG() 로 집계하지 않고, insert() 때 member 테이블에 저장해 둔
    // avg_rating / rating_count 를 읽는다. (매칭할 때마다 후보 여러 명을 조회하므로 빨라야 한다)
    //
    // 매칭 알고리즘에서는 보통 아래처럼 쓴다.
    //   Map<Long, RatingSummary> ratings = ratingDAO.findSummaries(후보회원번호목록);
    //   double r = ratings.get(후보회원번호).getRatingScore();   // 0~100
    // ==================================================================

    /**
     * 회원 한 명의 평점 요약을 조회한다.
     *
     * @return 평점 요약. 그런 회원이 없으면 null
     *         (평점을 한 번도 안 받은 회원은 null 이 아니라 개수 0 인 요약이 돌아온다)
     */
    public RatingSummary findSummary(long memberId) throws SQLException {
        String sql = "SELECT " + SUMMARY_COLUMNS + " FROM member WHERE member_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapSummary(rs) : null;
            }
        }
    }

    /**
     * 여러 회원의 평점 요약을 SQL 한 번으로 조회한다. (매칭 후보 목록용)
     *
     * 후보가 10명일 때 findSummary() 를 10번 부르면 DB 왕복도 10번이다. (이른바 "N+1 문제")
     * IN (?, ?, ...) 으로 묶으면 한 번에 끝난다.
     *
     * @param memberIds 조회할 회원 번호들 (중복이 있어도 괜찮다)
     * @return 회원 번호 -> 평점 요약. DB 에 없는 회원 번호는 Map 에 들어가지 않는다.
     *         빈 목록을 넘기면 빈 Map 을 돌려준다. (null 을 돌려주지 않으므로 호출한 쪽에서 null 검사 불필요)
     * @throws IllegalArgumentException 목록 안에 null 이 들어 있을 때
     */
    public Map<Long, RatingSummary> findSummaries(Collection<Long> memberIds) throws SQLException {
        Map<Long, RatingSummary> result = new HashMap<>();
        if (memberIds == null || memberIds.isEmpty()) {
            return result;   // IN () 처럼 빈 괄호는 SQL 문법 오류라서 DB 에 가기 전에 끝낸다
        }

        // 중복 제거 (LinkedHashSet: 중복은 없애고 넣은 순서는 유지)
        Set<Long> ids = new LinkedHashSet<>();
        for (Long id : memberIds) {
            if (id == null) {
                // null 을 setLong 에 넣으면 NullPointerException 이 나므로, 원인을 알기 쉬운 메시지로 먼저 막는다
                throw new IllegalArgumentException("회원 번호 목록에 null 이 들어 있습니다.");
            }
            ids.add(id);
        }

        String sql = "SELECT " + SUMMARY_COLUMNS + " FROM member "
                   + "WHERE member_id IN (" + DaoUtil.placeholders(ids.size()) + ")";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            int index = 1;
            for (long id : ids) {
                ps.setLong(index++, id);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    RatingSummary s = mapSummary(rs);
                    result.put(s.getMemberId(), s);
                }
            }
        }
        return result;
    }

    /** ResultSet 의 현재 행을 RatingSummary 로 바꾼다. */
    private RatingSummary mapSummary(ResultSet rs) throws SQLException {
        return new RatingSummary(
                rs.getLong("member_id"),
                rs.getDouble("avg_rating"),   // DECIMAL(3,2) -> double
                rs.getInt("rating_count"));
    }
}
