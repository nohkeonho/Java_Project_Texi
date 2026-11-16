package taxi.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import taxi.db.DBConnection;
import taxi.model.MatchMember;
import taxi.model.RideMatch;

/**
 * ride_match(매칭), match_member(매칭 참여자) 테이블을 다루는 DAO.
 *
 * 매칭을 만들 때는 여러 테이블을 동시에 바꿔야 해서 "트랜잭션"이 꼭 필요하다.
 *   예) 매칭 INSERT 는 성공했는데 참여자 INSERT 가 실패하면?  -> 전부 취소(rollback)해야 데이터가 꼬이지 않는다.
 */
public class RideMatchDAO {

    /**
     * 매칭 한 팀을 만든다.
     *  1) ride_match 에 매칭 1건 INSERT
     *  2) match_member 에 참여자들 INSERT
     *  3) 참여자들의 ride_request 상태를 WAITING -> MATCHED 로 변경
     * 위 3단계는 하나의 트랜잭션으로 묶여서, 하나라도 실패하면 전부 취소된다.
     *
     * 3단계에서 "이미 다른 매칭에 배정된 요청"이면(= WAITING 이 아니면) 예외를 던져 전체를 취소한다.
     * 두 매칭 알고리즘이 동시에 같은 요청을 잡는 상황을 막아 준다.
     *
     * @param match   총 거리·총 요금이 채워진 매칭 정보
     * @param members 참여자 목록 (matchId 는 이 메서드가 채워 넣으므로 비워 둬도 됨)
     * @return 새로 만들어진 match_id
     */
    public long createMatch(RideMatch match, List<MatchMember> members) throws SQLException {
        String matchSql = "INSERT INTO ride_match (total_distance_m, total_fare) VALUES (?, ?)";
        String memberSql = "INSERT INTO match_member "
                         + "(match_id, request_id, member_id, pickup_order, dropoff_order, "
                         + " own_distance_m, detour_m, match_score, fare_share) "
                         + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String requestSql = "UPDATE ride_request SET status = 'MATCHED' "
                          + "WHERE request_id = ? AND status = 'WAITING'";

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);   // 트랜잭션 시작
            try {
                // 1) 매칭 INSERT
                long matchId;
                try (PreparedStatement ps = con.prepareStatement(matchSql, Statement.RETURN_GENERATED_KEYS)) {
                    DaoUtil.setNullableInt(ps, 1, match.getTotalDistanceM());
                    DaoUtil.setNullableInt(ps, 2, match.getTotalFare());
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("매칭 번호를 받아오지 못했습니다.");
                        }
                        matchId = keys.getLong(1);
                    }
                }

                // 2), 3) 참여자 INSERT + 요청 상태 변경
                for (MatchMember mm : members) {
                    try (PreparedStatement ps = con.prepareStatement(memberSql)) {
                        ps.setLong(1, matchId);
                        ps.setLong(2, mm.getRequestId());
                        ps.setLong(3, mm.getMemberId());
                        ps.setInt(4, mm.getPickupOrder());
                        ps.setInt(5, mm.getDropoffOrder());
                        ps.setInt(6, mm.getOwnDistanceM());
                        ps.setInt(7, mm.getDetourM());
                        DaoUtil.setNullableDouble(ps, 8, mm.getMatchScore());
                        ps.setInt(9, mm.getFareShare());
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = con.prepareStatement(requestSql)) {
                        ps.setLong(1, mm.getRequestId());
                        if (ps.executeUpdate() != 1) {
                            throw new SQLException("이미 다른 매칭에 배정되었거나 취소된 요청입니다. (request_id="
                                    + mm.getRequestId() + ")");
                        }
                    }
                }

                con.commit();       // 모두 성공했을 때만 확정
                return matchId;
            } catch (SQLException e) {
                con.rollback();     // 하나라도 실패하면 전부 취소
                throw e;
            }
        }
    }

    /** 번호로 매칭 한 건 찾기. 없으면 null. */
    public RideMatch findById(long matchId) throws SQLException {
        String sql = "SELECT * FROM ride_match WHERE match_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, matchId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapMatch(rs) : null;
            }
        }
    }

    /** 한 매칭에 속한 참여자 목록 (탑승 순서대로). 경로·요금 분담 화면에서 쓴다. */
    public List<MatchMember> findMembersByMatchId(long matchId) throws SQLException {
        String sql = "SELECT * FROM match_member WHERE match_id = ? ORDER BY pickup_order";
        List<MatchMember> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, matchId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapMember(rs));
                }
            }
        }
        return list;
    }

    /** 한 회원이 참여한 매칭 목록 (최신순). "내 합승 내역" 화면용. */
    public List<RideMatch> findByMemberId(long memberId) throws SQLException {
        String sql = "SELECT rm.* FROM ride_match rm "
                   + "JOIN match_member mm ON mm.match_id = rm.match_id "
                   + "WHERE mm.member_id = ? ORDER BY rm.created_at DESC";
        List<RideMatch> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapMatch(rs));
                }
            }
        }
        return list;
    }

    /**
     * 매칭 제안에 대한 참여자의 수락/거절을 기록한다.
     *
     *  - ACCEPTED : 참여자 전원이 수락하면 매칭 상태가 CONFIRMED(확정)로 바뀐다.
     *  - REJECTED : 매칭이 CANCELLED 로 바뀌고,
     *               거절한 사람의 요청은 CANCELLED, 나머지 사람의 요청은 다시 WAITING 으로 돌아가
     *               새 매칭을 기다릴 수 있다.
     *
     * @throws IllegalStateException 이미 응답했거나, 이미 끝난(제안 상태가 아닌) 매칭일 때
     */
    public void respond(long matchMemberId, MatchMember.Response response) throws SQLException {
        if (response == MatchMember.Response.PENDING) {
            throw new IllegalArgumentException("응답은 ACCEPTED 또는 REJECTED 여야 합니다.");
        }

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                // 이 참여자의 match_id, request_id 를 알아낸다. (FOR UPDATE : 읽는 동안 다른 곳에서 못 바꾸게 잠금)
                long matchId;
                long requestId;
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT match_id, request_id FROM match_member WHERE match_member_id = ? FOR UPDATE")) {
                    ps.setLong(1, matchMemberId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalStateException("존재하지 않는 매칭 참여자입니다.");
                        }
                        matchId = rs.getLong("match_id");
                        requestId = rs.getLong("request_id");
                    }
                }

                // 매칭이 아직 "제안됨" 상태인지 확인
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT status FROM ride_match WHERE match_id = ? FOR UPDATE")) {
                    ps.setLong(1, matchId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next() || !"PROPOSED".equals(rs.getString(1))) {
                            throw new IllegalStateException("이미 확정되었거나 취소된 매칭입니다.");
                        }
                    }
                }

                // 내 응답 기록 (아직 PENDING 일 때만 바뀜 -> 중복 응답 방지)
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE match_member SET response = ? WHERE match_member_id = ? AND response = 'PENDING'")) {
                    ps.setString(1, response.name());
                    ps.setLong(2, matchMemberId);
                    if (ps.executeUpdate() != 1) {
                        throw new IllegalStateException("이미 응답한 매칭입니다.");
                    }
                }

                if (response == MatchMember.Response.REJECTED) {
                    // 매칭 취소
                    execute(con, "UPDATE ride_match SET status = 'CANCELLED' WHERE match_id = ?", matchId);
                    // 거절한 사람의 요청은 취소
                    execute(con, "UPDATE ride_request SET status = 'CANCELLED' WHERE request_id = ?", requestId);
                    // 나머지 사람들의 요청은 다시 대기 상태로
                    try (PreparedStatement ps = con.prepareStatement(
                            "UPDATE ride_request SET status = 'WAITING' "
                          + "WHERE status = 'MATCHED' AND request_id IN "
                          + "(SELECT request_id FROM match_member WHERE match_id = ? AND match_member_id <> ?)")) {
                        ps.setLong(1, matchId);
                        ps.setLong(2, matchMemberId);
                        ps.executeUpdate();
                    }
                } else {
                    // 아직 수락하지 않은 사람이 한 명도 없으면 확정
                    int notAccepted;
                    try (PreparedStatement ps = con.prepareStatement(
                            "SELECT COUNT(*) FROM match_member WHERE match_id = ? AND response <> 'ACCEPTED'")) {
                        ps.setLong(1, matchId);
                        try (ResultSet rs = ps.executeQuery()) {
                            rs.next();
                            notAccepted = rs.getInt(1);
                        }
                    }
                    if (notAccepted == 0) {
                        execute(con, "UPDATE ride_match SET status = 'CONFIRMED' WHERE match_id = ?", matchId);
                    }
                }

                con.commit();
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    /**
     * 합승이 끝났을 때 매칭을 COMPLETED(완료)로 바꾼다.
     * 확정(CONFIRMED)된 매칭만 완료할 수 있다. 완료되어야 평점·신고를 남길 수 있다.
     *
     * @return 완료 처리에 성공하면 true
     */
    public boolean complete(long matchId) throws SQLException {
        String sql = "UPDATE ride_match SET status = 'COMPLETED', completed_at = NOW() "
                   + "WHERE match_id = ? AND status = 'CONFIRMED'";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, matchId);
            return ps.executeUpdate() == 1;
        }
    }

    /**
     * 평점·신고를 남길 자격이 있는지 확인한다. (RatingDAO, ReportDAO 가 함께 사용)
     *   조건: 매칭이 COMPLETED 이고, 두 회원이 모두 그 매칭의 참여자일 것
     *
     * 이미 열려 있는 Connection 을 받는 이유: 평점 INSERT 와 같은 트랜잭션 안에서 확인하기 위해서다.
     * (static 메서드라서 DAO 객체 없이 RideMatchDAO.isCompletedWithBothMembers(...) 로 부른다.)
     */
    static boolean isCompletedWithBothMembers(Connection con, long matchId, long memberA, long memberB)
            throws SQLException {
        String sql = "SELECT COUNT(*) FROM ride_match rm "
                   + "JOIN match_member mm ON mm.match_id = rm.match_id "
                   + "WHERE rm.match_id = ? AND rm.status = 'COMPLETED' AND mm.member_id IN (?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, matchId);
            ps.setLong(2, memberA);
            ps.setLong(3, memberB);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) == 2;   // 서로 다른 두 사람이 모두 참여자면 2
            }
        }
    }

    // ------------------------------------------------------------------
    // 내부 도우미 메서드
    // ------------------------------------------------------------------

    /** "? 가 숫자 하나" 인 간단한 UPDATE 를 실행하는 도우미. */
    private void execute(Connection con, String sql, long param) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, param);
            ps.executeUpdate();
        }
    }

    private RideMatch mapMatch(ResultSet rs) throws SQLException {
        RideMatch m = new RideMatch();
        m.setMatchId(rs.getLong("match_id"));
        m.setStatus(RideMatch.Status.valueOf(rs.getString("status")));
        m.setTotalDistanceM(DaoUtil.getNullableInt(rs, "total_distance_m"));
        m.setTotalFare(DaoUtil.getNullableInt(rs, "total_fare"));
        m.setCreatedAt(DaoUtil.getDateTime(rs, "created_at"));
        m.setCompletedAt(DaoUtil.getDateTime(rs, "completed_at"));
        return m;
    }

    private MatchMember mapMember(ResultSet rs) throws SQLException {
        MatchMember mm = new MatchMember();
        mm.setMatchMemberId(rs.getLong("match_member_id"));
        mm.setMatchId(rs.getLong("match_id"));
        mm.setRequestId(rs.getLong("request_id"));
        mm.setMemberId(rs.getLong("member_id"));
        mm.setPickupOrder(rs.getInt("pickup_order"));
        mm.setDropoffOrder(rs.getInt("dropoff_order"));
        mm.setOwnDistanceM(rs.getInt("own_distance_m"));
        mm.setDetourM(rs.getInt("detour_m"));
        mm.setMatchScore(DaoUtil.getNullableDouble(rs, "match_score"));
        mm.setFareShare(rs.getInt("fare_share"));
        mm.setResponse(MatchMember.Response.valueOf(rs.getString("response")));
        return mm;
    }
}
