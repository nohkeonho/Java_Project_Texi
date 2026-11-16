package taxi.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import taxi.db.DBConnection;
import taxi.model.Rating;

/** rating 테이블(평점)을 다루는 DAO. */
public class RatingDAO {

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
}
