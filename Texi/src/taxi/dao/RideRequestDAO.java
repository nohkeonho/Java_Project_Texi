package taxi.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import taxi.db.DBConnection;
import taxi.model.RideRequest;

/**
 * ride_request 테이블(합승 요청)을 다루는 DAO.
 *
 * [예외 사용 규칙] 화면(Swing)에서 catch 해서 메시지를 띄우기 쉽도록 종류를 나눴다.
 *  - IllegalArgumentException : 사용자가 입력한 값이 잘못됨   (예: 인원 5명, 도착 시간이 출발보다 빠름)
 *  - IllegalStateException    : 입력은 맞지만 지금 상태에서 불가 (예: 이미 대기 중인 요청이 있음)
 *  - SQLException             : DB 연결·쿼리 자체의 오류
 *
 * [동성 합승만 허용]
 *   요청에는 성별 옵션이 없고, 후보 검색(findCandidates)에서 항상 "회원 성별이 같은 요청"만 고른다.
 */
public class RideRequestDAO {

    /** 장소 이름 최대 길이 (schema.sql 의 VARCHAR(100) 과 맞춤) */
    private static final int MAX_NAME_LENGTH = 100;

    /**
     * 합승 요청 등록. 새로 만들어진 request_id 를 돌려준다. (상태는 DB 기본값 WAITING)
     *
     * 저장 전에 하는 일
     *  1) 입력값 검사 (좌표 범위, 인원, 시간 순서 등)
     *  2) 회원이 정상(ACTIVE) 상태이고 위치정보에 동의했는지 확인
     *  3) 같은 시간대에 이미 진행 중(WAITING/MATCHED)인 요청이 있으면 거부
     *     -> "등록" 버튼을 두 번 눌러 요청이 2개 생기는 문제를 막는다.
     *
     * 2), 3) 과 INSERT 는 하나의 트랜잭션으로 묶고, 회원 행을 FOR UPDATE 로 잠근다.
     * 잠그지 않으면 거의 동시에 들어온 두 요청이 둘 다 "진행 중인 요청 없음" 으로 판단해 둘 다 저장될 수 있다.
     */
    public long insert(RideRequest r) throws SQLException {
        validate(r);

        String memberSql = "SELECT status, location_agreed_at FROM member WHERE member_id = ? FOR UPDATE";
        String dupSql = "SELECT COUNT(*) FROM ride_request "
                      + "WHERE member_id = ? AND status IN ('WAITING', 'MATCHED') "
                      + "  AND depart_from <= ? AND depart_until >= ?";   // 시간대가 겹치는 진행 중 요청
        String insertSql = "INSERT INTO ride_request "
                   + "(member_id, origin_name, origin_lat, origin_lng, dest_name, dest_lat, dest_lng, passenger_count, "
                   + " depart_from, depart_until, max_detour_m, est_distance_m, est_fare) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);   // 트랜잭션 시작
            try {
                // 1) 회원 상태 확인 + 잠금
                try (PreparedStatement ps = con.prepareStatement(memberSql)) {
                    ps.setLong(1, r.getMemberId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalStateException("존재하지 않는 회원입니다.");
                        }
                        if (!"ACTIVE".equals(rs.getString("status"))) {
                            throw new IllegalStateException("이용이 정지되었거나 탈퇴한 회원은 합승을 요청할 수 없습니다.");
                        }
                        if (rs.getTimestamp("location_agreed_at") == null) {
                            throw new IllegalStateException("위치정보 수집·이용에 동의해야 합승을 요청할 수 있습니다.");
                        }
                    }
                }

                // 2) 같은 시간대의 진행 중 요청이 있는지 확인
                try (PreparedStatement ps = con.prepareStatement(dupSql)) {
                    ps.setLong(1, r.getMemberId());
                    ps.setTimestamp(2, DaoUtil.toTimestamp(r.getDepartUntil()));
                    ps.setTimestamp(3, DaoUtil.toTimestamp(r.getDepartFrom()));
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        if (rs.getInt(1) > 0) {
                            throw new IllegalStateException("같은 시간대에 이미 대기 중이거나 매칭된 요청이 있습니다.");
                        }
                    }
                }

                // 3) 저장
                long requestId;
                try (PreparedStatement ps = con.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setLong(1, r.getMemberId());
                    ps.setString(2, r.getOriginName().trim());
                    ps.setDouble(3, r.getOriginLat());
                    ps.setDouble(4, r.getOriginLng());
                    ps.setString(5, r.getDestName().trim());
                    ps.setDouble(6, r.getDestLat());
                    ps.setDouble(7, r.getDestLng());
                    ps.setInt(8, r.getPassengerCount());
                    ps.setTimestamp(9, DaoUtil.toTimestamp(r.getDepartFrom()));
                    ps.setTimestamp(10, DaoUtil.toTimestamp(r.getDepartUntil()));
                    ps.setInt(11, r.getMaxDetourM());
                    DaoUtil.setNullableInt(ps, 12, r.getEstDistanceM());
                    DaoUtil.setNullableInt(ps, 13, r.getEstFare());
                    ps.executeUpdate();

                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("요청 번호를 받아오지 못했습니다.");
                        }
                        requestId = keys.getLong(1);
                    }
                }

                con.commit();
                // 저장된 값을 객체에도 반영해 두면, 화면에서 바로 r.getRequestId() 를 쓸 수 있다.
                r.setRequestId(requestId);
                r.setStatus(RideRequest.Status.WAITING);
                return requestId;
            } catch (SQLException | RuntimeException e) {
                // RuntimeException(IllegalStateException 등)도 롤백해야 잠금이 바로 풀린다.
                con.rollback();
                throw e;
            }
        }
    }

    /** 번호로 요청 한 건 찾기. 없으면 null. */
    public RideRequest findById(long requestId) throws SQLException {
        String sql = "SELECT * FROM ride_request WHERE request_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    /** 한 회원의 요청 목록 (최신순). "내 요청 내역" 화면용. */
    public List<RideRequest> findByMemberId(long memberId) throws SQLException {
        String sql = "SELECT * FROM ride_request WHERE member_id = ? ORDER BY created_at DESC, request_id DESC";
        List<RideRequest> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    /**
     * 요청 취소. 아직 WAITING 상태일 때만 취소할 수 있다.
     * (이미 매칭된 요청을 마음대로 취소하면 같이 타는 사람이 곤란해지므로)
     *
     * @return 취소에 성공하면 true
     */
    public boolean cancel(long requestId) throws SQLException {
        String sql = "UPDATE ride_request SET status = 'CANCELLED' "
                   + "WHERE request_id = ? AND status = 'WAITING'";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, requestId);
            return ps.executeUpdate() == 1;   // 바뀐 행이 1개면 성공
        }
    }

    /**
     * 출발 가능 시간이 지났는데 아직 WAITING 인 요청을 EXPIRED 로 바꾼다.
     * 주기적으로(예: 1분마다 javax.swing.Timer 로) 호출하면 된다.
     *
     * @return 만료 처리된 요청 개수
     */
    public int expireOldRequests() throws SQLException {
        String sql = "UPDATE ride_request SET status = 'EXPIRED' "
                   + "WHERE status = 'WAITING' AND depart_until < NOW()";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            return ps.executeUpdate();
        }
    }

    /**
     * 내 요청과 매칭할 수 있는 "후보 요청"을 찾는다. (점수 계산 전 1차 거르기 단계)
     *
     * 걸러내는 조건:
     *  1) 내 요청과 상대 요청 모두 WAITING 상태
     *  2) 내 요청이 아니고, 같은 사람의 요청도 아님
     *  3) 출발 가능 시간대가 서로 겹치고, 상대 요청이 아직 시간 만료 전
     *  4) 두 요청 인원을 합쳐도 택시 좌석 수(4석)를 넘지 않음
     *  5) 상대 회원이 정상(ACTIVE) 상태
     *  6) 두 회원의 성별이 같음 (동성 합승만 허용)
     *
     * 경로 겹침 비율, 우회 거리 같은 계산은 DB 가 아니라 자바(매칭 알고리즘)에서 하고,
     * 이 메서드는 후보를 넉넉히 추려 주는 역할만 한다.
     */
    public List<RideRequest> findCandidates(long requestId) throws SQLException {
        String sql =
                "SELECT o.* "
              + "FROM ride_request me "
              + "JOIN member mm ON mm.member_id = me.member_id "
              + "JOIN ride_request o "
              + "  ON o.status = 'WAITING' "
              + " AND o.request_id <> me.request_id "
              + " AND o.member_id  <> me.member_id "
              + " AND o.depart_from  <= me.depart_until "
              + " AND o.depart_until >= me.depart_from "
              + " AND o.depart_until >= NOW() "
              + " AND me.passenger_count + o.passenger_count <= ? "
              // 상대 회원: 정상 상태 + 나와 같은 성별 (JOIN 조건에 넣어서 다른 성별은 아예 후보에서 빠진다)
              + "JOIN member om ON om.member_id = o.member_id AND om.status = 'ACTIVE' AND om.gender = mm.gender "
              + "WHERE me.request_id = ? AND me.status = 'WAITING' "
              + "ORDER BY o.depart_from";
        List<RideRequest> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, RideRequest.TAXI_SEATS);
            ps.setLong(2, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    // ------------------------------------------------------------------
    // 내부 도우미 메서드
    // ------------------------------------------------------------------

    /**
     * 입력값 검사. DB 의 CHECK 제약에 걸리기 전에 자바에서 먼저 막으면
     * "Check constraint 'chk_req_time' is violated" 같은 어려운 메시지 대신
     * 사용자가 이해할 수 있는 한국어 메시지를 보여 줄 수 있다.
     */
    private void validate(RideRequest r) {
        if (r == null) {
            throw new IllegalArgumentException("요청 정보가 없습니다.");
        }
        if (r.getMemberId() <= 0) {
            throw new IllegalArgumentException("로그인한 회원 정보가 없습니다.");
        }
        checkName(r.getOriginName(), "출발 구역");
        checkName(r.getDestName(), "도착 구역");
        checkCoordinate(r.getOriginLat(), r.getOriginLng(), "출발지");
        checkCoordinate(r.getDestLat(), r.getDestLng(), "도착지");
        if (r.getOriginLat() == r.getDestLat() && r.getOriginLng() == r.getDestLng()) {
            throw new IllegalArgumentException("출발지와 도착지가 같습니다.");
        }
        if (r.getPassengerCount() < 1 || r.getPassengerCount() > RideRequest.MAX_PASSENGERS) {
            throw new IllegalArgumentException("인원은 1~" + RideRequest.MAX_PASSENGERS + "명만 가능합니다.");
        }

        LocalDateTime from = r.getDepartFrom();
        LocalDateTime until = r.getDepartUntil();
        if (from == null || until == null) {
            throw new IllegalArgumentException("희망 출발 시간을 입력해 주세요.");
        }
        if (until.isBefore(from)) {
            throw new IllegalArgumentException("출발 가능 시간의 끝이 시작보다 빠릅니다.");
        }
        if (until.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("이미 지난 시간으로는 요청할 수 없습니다.");
        }

        if (r.getMaxDetourM() < 0) {
            throw new IllegalArgumentException("우회 허용 거리는 0 이상이어야 합니다.");
        }
        if (r.getEstDistanceM() != null && r.getEstDistanceM() < 0) {
            throw new IllegalArgumentException("예상 거리가 음수입니다.");
        }
        if (r.getEstFare() != null && r.getEstFare() < 0) {
            throw new IllegalArgumentException("예상 요금이 음수입니다.");
        }
    }

    private void checkName(String name, String label) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException(label + "을(를) 입력해 주세요.");
        }
        if (name.trim().length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(label + " 이름은 " + MAX_NAME_LENGTH + "자 이하로 입력해 주세요.");
        }
    }

    private void checkCoordinate(double lat, double lng, String label) {
        // Double.isNaN : 지도 API 응답이 이상해서 숫자가 아닌 값(NaN)이 들어온 경우도 막는다.
        if (Double.isNaN(lat) || Double.isNaN(lng) || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new IllegalArgumentException(label + " 좌표가 올바르지 않습니다.");
        }
    }

    /** ResultSet 의 현재 행 1줄을 RideRequest 객체로 바꾼다. */
    private RideRequest mapRow(ResultSet rs) throws SQLException {
        RideRequest r = new RideRequest();
        r.setRequestId(rs.getLong("request_id"));
        r.setMemberId(rs.getLong("member_id"));
        r.setOriginName(rs.getString("origin_name"));
        r.setOriginLat(rs.getDouble("origin_lat"));
        r.setOriginLng(rs.getDouble("origin_lng"));
        r.setDestName(rs.getString("dest_name"));
        r.setDestLat(rs.getDouble("dest_lat"));
        r.setDestLng(rs.getDouble("dest_lng"));
        r.setPassengerCount(rs.getInt("passenger_count"));
        r.setDepartFrom(DaoUtil.getDateTime(rs, "depart_from"));
        r.setDepartUntil(DaoUtil.getDateTime(rs, "depart_until"));
        r.setMaxDetourM(rs.getInt("max_detour_m"));
        r.setEstDistanceM(DaoUtil.getNullableInt(rs, "est_distance_m"));
        r.setEstFare(DaoUtil.getNullableInt(rs, "est_fare"));
        r.setStatus(RideRequest.Status.valueOf(rs.getString("status")));
        r.setCreatedAt(DaoUtil.getDateTime(rs, "created_at"));
        return r;
    }
}
