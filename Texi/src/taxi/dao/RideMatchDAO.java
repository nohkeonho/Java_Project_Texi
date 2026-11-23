package taxi.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import taxi.db.DBConnection;
import taxi.model.MatchMember;
import taxi.model.RideMatch;
import taxi.model.RideRequest;

/**
 * ride_match(매칭), match_member(매칭 참여자) 테이블을 다루는 DAO.
 *
 * 매칭을 만들 때는 여러 테이블을 동시에 바꿔야 해서 "트랜잭션"이 꼭 필요하다.
 *   예) 매칭 INSERT 는 성공했는데 참여자 INSERT 가 실패하면?  -> 전부 취소(rollback)해야 데이터가 꼬이지 않는다.
 *
 * 예외 규칙은 RideRequestDAO 와 같다. (IllegalArgument = 입력 오류, IllegalState = 상태 오류, SQL = DB 오류)
 */
public class RideMatchDAO {

    /** 점수 범위 (schema 의 DECIMAL(5,2) + CHECK 0~100 과 맞춤) */
    private static final double MIN_SCORE = 0.0;
    private static final double MAX_SCORE = 100.0;

    /**
     * 매칭 한 팀을 저장한다.
     *
     *  0) [자바] 입력값 검사: 인원 수, 탑승·하차 순서, 점수 범위, 분담금 합계 = 총 요금
     *  1) [DB]  묶을 요청들을 FOR UPDATE 로 잠그고 다시 검사
     *           - 모두 WAITING 이고 시간이 지나지 않았는지
     *           - 서로 다른 회원인지, 인원 합이 4석 이하인지, 출발 시간대가 모두 겹치는지
     *           - 참여자 모두 성별이 같은지 (동성 합승만 허용)
     *  2) ride_match 에 매칭 1건 INSERT
     *  3) match_member 에 참여자들을 한 번에(batch) INSERT
     *  4) 요청들의 상태를 WAITING -> MATCHED 로 변경
     * 1~4 는 하나의 트랜잭션이라, 하나라도 실패하면 전부 취소된다.
     *
     * member_id 는 호출한 쪽이 넣은 값을 믿지 않고 DB 의 ride_request 에서 읽은 값으로 채운다.
     * (요청 번호와 회원 번호가 어긋나게 저장되는 실수를 원천 차단)
     *
     * @param match   점수·총 거리·총 요금이 채워진 매칭 정보 (저장 후 matchId 가 채워진다)
     * @param members 참여자 목록 (저장 후 matchId, memberId 가 채워진다)
     * @return 새로 만들어진 match_id
     */
    public long createMatch(RideMatch match, List<MatchMember> members) throws SQLException {
        validateMatch(match, members);

        int n = members.size();
        String in = DaoUtil.placeholders(n);

        // ORDER BY request_id : 여러 행을 잠글 때 항상 같은 순서로 잠가야
        // 두 매칭 작업이 서로의 잠금을 기다리는 "교착 상태(deadlock)"가 덜 생긴다.
        // gender : 요청한 회원의 성별을 함께 읽는다. (괄호 안 SELECT 는 읽기만 하므로 member 행은 잠그지 않는다)
        String lockSql = "SELECT request_id, member_id, status, passenger_count, depart_from, depart_until, "
                       + "       (depart_until >= NOW()) AS not_expired, "
                       + "       (SELECT m.gender FROM member m WHERE m.member_id = ride_request.member_id) AS gender "
                       + "FROM ride_request WHERE request_id IN (" + in + ") "
                       + "ORDER BY request_id FOR UPDATE";
        String matchSql = "INSERT INTO ride_match (match_score, total_distance_m, total_fare) VALUES (?, ?, ?)";
        String memberSql = "INSERT INTO match_member "
                         + "(match_id, request_id, member_id, pickup_order, dropoff_order, "
                         + " own_distance_m, detour_m, match_score, fare_share) "
                         + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String requestSql = "UPDATE ride_request SET status = 'MATCHED' "
                          + "WHERE request_id IN (" + in + ") AND status = 'WAITING'";

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);   // 트랜잭션 시작
            try {
                // 1) 요청 잠금 + 검사
                Map<Long, Long> memberOfRequest = lockAndCheckRequests(con, lockSql, members);
                for (MatchMember mm : members) {
                    mm.setMemberId(memberOfRequest.get(mm.getRequestId()));
                }

                // 2) 매칭 INSERT
                long matchId;
                try (PreparedStatement ps = con.prepareStatement(matchSql, Statement.RETURN_GENERATED_KEYS)) {
                    DaoUtil.setNullableDouble(ps, 1, match.getMatchScore());
                    DaoUtil.setNullableInt(ps, 2, match.getTotalDistanceM());
                    DaoUtil.setNullableInt(ps, 3, match.getTotalFare());
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("매칭 번호를 받아오지 못했습니다.");
                        }
                        matchId = keys.getLong(1);
                    }
                }

                // 3) 참여자 INSERT : PreparedStatement 를 한 번만 만들고 addBatch 로 모아서 한 번에 실행
                try (PreparedStatement ps = con.prepareStatement(memberSql)) {
                    for (MatchMember mm : members) {
                        ps.setLong(1, matchId);
                        ps.setLong(2, mm.getRequestId());
                        ps.setLong(3, mm.getMemberId());
                        ps.setInt(4, mm.getPickupOrder());
                        ps.setInt(5, mm.getDropoffOrder());
                        ps.setInt(6, mm.getOwnDistanceM());
                        ps.setInt(7, mm.getDetourM());
                        DaoUtil.setNullableDouble(ps, 8, mm.getMatchScore());
                        ps.setInt(9, mm.getFareShare());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                // 4) 요청 상태 변경 (UPDATE 한 번으로 n 개를 같이 바꾼다)
                try (PreparedStatement ps = con.prepareStatement(requestSql)) {
                    for (int i = 0; i < n; i++) {
                        ps.setLong(i + 1, members.get(i).getRequestId());
                    }
                    if (ps.executeUpdate() != n) {
                        // 1) 에서 잠갔으므로 정상이라면 일어나지 않지만, 혹시 모를 상황 대비
                        throw new IllegalStateException("요청 상태를 바꾸는 중 문제가 생겼습니다. 다시 시도해 주세요.");
                    }
                }

                con.commit();       // 모두 성공했을 때만 확정

                // 저장된 번호를 객체에도 반영 (화면에서 바로 쓰기 위해)
                match.setMatchId(matchId);
                match.setStatus(RideMatch.Status.PROPOSED);
                for (MatchMember mm : members) {
                    mm.setMatchId(matchId);
                    mm.setResponse(MatchMember.Response.PENDING);
                }
                return matchId;
            } catch (SQLException | RuntimeException e) {
                con.rollback();     // 하나라도 실패하면 전부 취소 (검사 실패로 던진 예외 포함)
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

    /**
     * 내 요청이 들어간 매칭 중 가장 최근 것. 없으면 null.
     * "합승 요청 → 매칭 결과 확인" 화면에서 요청 번호만 알고 있을 때 쓴다.
     * (거절로 취소된 뒤 다시 매칭될 수 있어서 한 요청이 여러 매칭에 들어갈 수 있으므로 최신 1건)
     */
    public RideMatch findLatestByRequestId(long requestId) throws SQLException {
        String sql = "SELECT rm.* FROM ride_match rm "
                   + "JOIN match_member mm ON mm.match_id = rm.match_id "
                   + "WHERE mm.request_id = ? ORDER BY rm.match_id DESC LIMIT 1";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, requestId);
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
                   + "WHERE mm.member_id = ? ORDER BY rm.created_at DESC, rm.match_id DESC";
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
        if (response == null || response == MatchMember.Response.PENDING) {
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

    /**
     * [자바 단계 검사] DB 에 가기 전에 알 수 있는 오류를 먼저 막는다.
     * 매칭 알고리즘에 버그가 있어도 잘못된 데이터가 DB 에 들어가지 않게 하는 "안전망" 역할.
     */
    private void validateMatch(RideMatch match, List<MatchMember> members) {
        if (match == null || members == null) {
            throw new IllegalArgumentException("매칭 정보가 없습니다.");
        }
        int n = members.size();
        if (n < 2 || n > RideRequest.TAXI_SEATS) {
            throw new IllegalArgumentException("한 매칭에는 2~" + RideRequest.TAXI_SEATS + "개의 요청만 묶을 수 있습니다.");
        }
        checkScore(match.getMatchScore(), "매칭 전체 점수");
        if (match.getTotalDistanceM() != null && match.getTotalDistanceM() < 0) {
            throw new IllegalArgumentException("총 이동 거리가 음수입니다.");
        }
        if (match.getTotalFare() != null && match.getTotalFare() < 0) {
            throw new IllegalArgumentException("총 요금이 음수입니다.");
        }

        Set<Long> requestIds = new HashSet<>();
        Set<Integer> pickups = new HashSet<>();
        Set<Integer> dropoffs = new HashSet<>();
        long fareSum = 0;   // int 로 더하면 아주 큰 값에서 넘칠 수 있어 long 사용

        for (MatchMember mm : members) {
            if (mm == null) {
                throw new IllegalArgumentException("참여자 정보에 빈 값이 있습니다.");
            }
            // Set.add 는 이미 있는 값이면 false 를 돌려준다 -> 중복 검사에 사용
            if (!requestIds.add(mm.getRequestId())) {
                throw new IllegalArgumentException("같은 요청이 두 번 들어 있습니다. (request_id=" + mm.getRequestId() + ")");
            }
            if (mm.getPickupOrder() < 1 || mm.getPickupOrder() > n || !pickups.add(mm.getPickupOrder())) {
                throw new IllegalArgumentException("탑승 순서는 1~" + n + " 을 한 번씩만 써야 합니다.");
            }
            if (mm.getDropoffOrder() < 1 || mm.getDropoffOrder() > n || !dropoffs.add(mm.getDropoffOrder())) {
                throw new IllegalArgumentException("하차 순서는 1~" + n + " 을 한 번씩만 써야 합니다.");
            }
            if (mm.getOwnDistanceM() < 0 || mm.getDetourM() < 0) {
                throw new IllegalArgumentException("이동 거리·우회 거리는 음수일 수 없습니다.");
            }
            if (mm.getFareShare() < 0) {
                throw new IllegalArgumentException("분담 금액은 음수일 수 없습니다.");
            }
            checkScore(mm.getMatchScore(), "참여자 매칭 점수");
            fareSum += mm.getFareShare();
        }

        // 거리 비례로 나누면 소수점 때문에 1~2원이 남거나 모자랄 수 있다.
        // 그 차이는 알고리즘 쪽에서 한 사람에게 몰아 주는 식으로 맞춰서, 합계가 정확히 총 요금이 되게 해야 한다.
        if (match.getTotalFare() != null && fareSum != match.getTotalFare()) {
            throw new IllegalArgumentException("분담 금액의 합(" + fareSum + "원)이 총 요금("
                    + match.getTotalFare() + "원)과 다릅니다.");
        }
    }

    private void checkScore(Double score, String label) {
        if (score != null && (score.isNaN() || score < MIN_SCORE || score > MAX_SCORE)) {
            throw new IllegalArgumentException(label + "는 " + (int) MIN_SCORE + "~" + (int) MAX_SCORE + " 사이여야 합니다.");
        }
    }

    /**
     * [DB 단계 검사] 묶을 요청들을 잠근 뒤, 지금 이 순간에도 매칭 가능한지 확인한다.
     * 알고리즘이 후보를 고른 시점과 저장하는 시점 사이에 상대가 취소했을 수 있기 때문이다.
     *
     * 동성 합승 검사도 여기서 한다.
     * findCandidates 가 이미 같은 성별만 골라 주지만, 매칭 알고리즘(서버 A·알고리즘 담당)이
     * 다른 경로로 요청을 묶어서 넘겨도 혼성 매칭이 DB 에 저장되지 않게 막는 "마지막 안전장치"다.
     *
     * @return 요청 번호 -> 그 요청을 만든 회원 번호
     */
    private Map<Long, Long> lockAndCheckRequests(Connection con, String lockSql, List<MatchMember> members)
            throws SQLException {
        Map<Long, Long> memberOfRequest = new TreeMap<>();
        Set<Long> memberIds = new HashSet<>();
        Set<String> genders = new HashSet<>();   // 참여자들의 성별 모음. 크기가 1 이어야 동성 매칭
        int totalPassengers = 0;
        Timestamp latestFrom = null;    // 모든 요청의 "가장 이른 출발" 중 제일 늦은 것
        Timestamp earliestUntil = null; // 모든 요청의 "가장 늦은 출발" 중 제일 이른 것

        try (PreparedStatement ps = con.prepareStatement(lockSql)) {
            for (int i = 0; i < members.size(); i++) {
                ps.setLong(i + 1, members.get(i).getRequestId());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long requestId = rs.getLong("request_id");
                    long memberId = rs.getLong("member_id");

                    if (!"WAITING".equals(rs.getString("status"))) {
                        throw new IllegalStateException("이미 매칭되었거나 취소된 요청이 있습니다. (request_id=" + requestId + ")");
                    }
                    if (!rs.getBoolean("not_expired")) {
                        throw new IllegalStateException("출발 가능 시간이 지난 요청이 있습니다. (request_id=" + requestId + ")");
                    }
                    if (!memberIds.add(memberId)) {
                        throw new IllegalStateException("한 사람의 요청 두 개를 같은 매칭에 묶을 수 없습니다.");
                    }
                    genders.add(rs.getString("gender"));
                    totalPassengers += rs.getInt("passenger_count");

                    Timestamp from = rs.getTimestamp("depart_from");
                    Timestamp until = rs.getTimestamp("depart_until");
                    if (latestFrom == null || from.after(latestFrom)) {
                        latestFrom = from;
                    }
                    if (earliestUntil == null || until.before(earliestUntil)) {
                        earliestUntil = until;
                    }
                    memberOfRequest.put(requestId, memberId);
                }
            }
        }

        if (memberOfRequest.size() != members.size()) {
            throw new IllegalStateException("존재하지 않는 요청 번호가 포함되어 있습니다.");
        }
        // 성별이 2가지 이상 섞여 있으면(남+여) 저장하지 않는다.
        if (genders.size() != 1) {
            throw new IllegalStateException("동성끼리만 합승할 수 있습니다. (성별이 다른 요청이 섞여 있습니다)");
        }
        if (totalPassengers > RideRequest.TAXI_SEATS) {
            throw new IllegalStateException("인원 합계(" + totalPassengers + "명)가 택시 좌석 수("
                    + RideRequest.TAXI_SEATS + "석)를 넘습니다.");
        }
        // 모든 사람이 같이 출발할 수 있는 시각이 하나라도 있으려면: 가장 늦은 시작 <= 가장 이른 끝
        if (latestFrom.after(earliestUntil)) {
            throw new IllegalStateException("모든 참여자가 함께 출발할 수 있는 시간대가 없습니다.");
        }
        return memberOfRequest;
    }

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
        m.setMatchScore(DaoUtil.getNullableDouble(rs, "match_score"));
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
