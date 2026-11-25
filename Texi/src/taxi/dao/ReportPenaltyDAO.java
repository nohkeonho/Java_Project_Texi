package taxi.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import taxi.db.DBConnection;
import taxi.model.Member;
import taxi.model.PenaltyResult;

/**
 * 신고 누적에 따른 이용정지/정지 해제를 DB 에 반영하는 DAO.
 *
 * "몇 명에게 신고당하면 정지할지" 같은 정책(기준값)은 ReportPenaltyService 가 정하고,
 * 이 클래스는 넘겨받은 기준값으로 SQL 만 실행한다. (MemberDAO, ReportDAO 와 같은 역할 나누기)
 *
 * [신고 수를 세는 규칙]
 *   - 반려(REJECTED)된 신고는 세지 않는다. (억울한 신고로 정지되는 것을 막기 위해)
 *   - 같은 사람이 여러 번 신고해도 1명으로 센다. (COUNT(DISTINCT reporter_id))
 *     -> 한 사람이 같은 상대와 여러 번 합승하면서 매번 신고하면, 그 한 사람만으로 정지시킬 수 있기 때문이다.
 */
public class ReportPenaltyDAO {

    /** 반려되지 않은 신고를 한 "서로 다른 신고자" 수를 세는 SQL */
    private static final String COUNT_SQL =
            "SELECT COUNT(DISTINCT reporter_id) FROM report WHERE reported_id = ? AND status <> 'REJECTED'";

    /**
     * 신고자 수를 세서 회원 상태를 맞춘다. 전체가 하나의 트랜잭션이다.
     *
     *  1) 회원 행을 FOR UPDATE 로 잠근다.
     *     -> 신고 두 건이 거의 동시에 들어와도, 두 번째 검사는 첫 번째가 끝날 때까지 기다렸다가
     *        최신 신고 수로 다시 판단한다. (둘 다 "아직 2명"이라고 판단해서 정지를 놓치는 일이 없다)
     *  2) 신고자 수를 센다.
     *  3-a) ACTIVE 인데 기준 이상    -> SUSPENDED 로 바꾸고,
     *       아직 출발 전인 합승(대기 중 요청, 제안됨 매칭)을 정리한다.
     *  3-b) SUSPENDED 인데 기준 미만 -> ACTIVE 로 되돌린다. (관리자가 신고를 반려한 경우)
     *  3-c) 그 밖의 경우(탈퇴 회원 등)는 바꾸지 않는다.
     *
     * @param threshold 이용정지 기준 신고자 수
     * @throws IllegalArgumentException 존재하지 않는 회원일 때
     */
    public PenaltyResult apply(long memberId, int threshold) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                Member.Status before = lockMemberStatus(con, memberId);
                int count = countValidReporters(con, memberId);

                Member.Status after = before;
                PenaltyResult.Action action = PenaltyResult.Action.NONE;
                int cancelledMatches = 0;
                int cancelledRequests = 0;

                if (before == Member.Status.ACTIVE && count >= threshold) {
                    updateMemberStatus(con, memberId, Member.Status.SUSPENDED);
                    // 순서 중요: 매칭을 먼저 정리해야 그 매칭에 묶여 있던 본인 요청까지 함께 취소된다.
                    cancelledMatches = cancelProposedMatches(con, memberId);
                    cancelledRequests = cancelWaitingRequests(con, memberId);
                    after = Member.Status.SUSPENDED;
                    action = PenaltyResult.Action.SUSPENDED;
                } else if (before == Member.Status.SUSPENDED && count < threshold) {
                    updateMemberStatus(con, memberId, Member.Status.ACTIVE);
                    after = Member.Status.ACTIVE;
                    action = PenaltyResult.Action.RESTORED;
                }

                con.commit();
                return new PenaltyResult(memberId, count, threshold, after, action,
                        cancelledRequests, cancelledMatches);
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    /**
     * 상태는 바꾸지 않고 "지금 신고자 수와 현재 상태"만 조회한다.
     * 로그인 직후 경고 문구를 보여 주거나, 테스트에서 결과를 확인할 때 쓴다.
     *
     * @return 존재하지 않는 회원이면 null
     */
    public PenaltyResult preview(long memberId, int threshold) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            Member.Status status;
            try (PreparedStatement ps = con.prepareStatement("SELECT status FROM member WHERE member_id = ?")) {
                ps.setLong(1, memberId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    status = Member.Status.valueOf(rs.getString(1));
                }
            }
            int count = countValidReporters(con, memberId);
            return new PenaltyResult(memberId, count, threshold, status, PenaltyResult.Action.NONE, 0, 0);
        }
    }

    // ------------------------------------------------------------------
    // 내부 도우미 메서드 (모두 같은 Connection = 같은 트랜잭션 안에서 실행)
    // ------------------------------------------------------------------

    private Member.Status lockMemberStatus(Connection con, long memberId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT status FROM member WHERE member_id = ? FOR UPDATE")) {
            ps.setLong(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("존재하지 않는 회원입니다. (member_id=" + memberId + ")");
                }
                return Member.Status.valueOf(rs.getString(1));
            }
        }
    }

    private int countValidReporters(Connection con, long memberId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(COUNT_SQL)) {
            ps.setLong(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private void updateMemberStatus(Connection con, long memberId, Member.Status status) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("UPDATE member SET status = ? WHERE member_id = ?")) {
            ps.setString(1, status.name());
            ps.setLong(2, memberId);
            ps.executeUpdate();
        }
    }

    /**
     * 정지된 회원이 들어 있는 "제안됨(PROPOSED)" 매칭을 모두 취소한다.
     * 아직 수락 전이므로 상대방이 정지된 사람과 합승하지 않도록 막는 것이다.
     * 처리 방식은 RideMatchDAO.respond() 의 "거절" 과 같다.
     *   - 매칭              -> CANCELLED
     *   - 정지된 회원의 요청 -> CANCELLED
     *   - 나머지 사람의 요청 -> 다시 WAITING (새 상대를 기다릴 수 있게)
     *
     * 확정(CONFIRMED)된 매칭은 이미 택시를 타러 가는 중일 수 있어서 건드리지 않는다.
     *
     * @return 취소한 매칭 수
     */
    private int cancelProposedMatches(Connection con, long memberId) throws SQLException {
        // 1) 대상 매칭 번호를 먼저 모은다. (FOR UPDATE: 그 사이에 누가 수락해서 확정되는 것을 막음)
        List<Long> matchIds = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT rm.match_id FROM ride_match rm "
              + "JOIN match_member mm ON mm.match_id = rm.match_id "
              + "WHERE mm.member_id = ? AND rm.status = 'PROPOSED' "
              + "ORDER BY rm.match_id FOR UPDATE")) {
            ps.setLong(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    matchIds.add(rs.getLong(1));
                }
            }
        }

        // 2) 매칭마다 취소 처리 (보통 0~1건이라 반복문으로 충분하다)
        String cancelMatch = "UPDATE ride_match SET status = 'CANCELLED' WHERE match_id = ? AND status = 'PROPOSED'";
        String cancelOwn = "UPDATE ride_request SET status = 'CANCELLED' WHERE status = 'MATCHED' AND request_id IN "
                         + "(SELECT request_id FROM match_member WHERE match_id = ? AND member_id = ?)";
        String requeueOthers = "UPDATE ride_request SET status = 'WAITING' WHERE status = 'MATCHED' AND request_id IN "
                             + "(SELECT request_id FROM match_member WHERE match_id = ? AND member_id <> ?)";
        for (long matchId : matchIds) {
            try (PreparedStatement ps = con.prepareStatement(cancelMatch)) {
                ps.setLong(1, matchId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement(cancelOwn)) {
                ps.setLong(1, matchId);
                ps.setLong(2, memberId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement(requeueOthers)) {
                ps.setLong(1, matchId);
                ps.setLong(2, memberId);
                ps.executeUpdate();
            }
        }
        return matchIds.size();
    }

    /** 정지된 회원의 대기 중(WAITING) 요청을 취소한다. 다른 사람의 매칭 후보로 뜨지 않게 하기 위해서다. */
    private int cancelWaitingRequests(Connection con, long memberId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE ride_request SET status = 'CANCELLED' WHERE member_id = ? AND status = 'WAITING'")) {
            ps.setLong(1, memberId);
            return ps.executeUpdate();
        }
    }
}
