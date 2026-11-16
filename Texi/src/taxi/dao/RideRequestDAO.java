package taxi.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import taxi.db.DBConnection;
import taxi.model.RideRequest;

/** ride_request 테이블(합승 요청)을 다루는 DAO. */
public class RideRequestDAO {

    /** 합승 요청 등록. 새로 만들어진 request_id 를 돌려준다. (상태는 DB 기본값 WAITING) */
    public long insert(RideRequest r) throws SQLException {
        String sql = "INSERT INTO ride_request "
                   + "(member_id, origin_name, origin_lat, origin_lng, dest_name, dest_lat, dest_lng, "
                   + " depart_from, depart_until, max_detour_m, same_gender_only, est_distance_m, est_fare) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, r.getMemberId());
            ps.setString(2, r.getOriginName());
            ps.setDouble(3, r.getOriginLat());
            ps.setDouble(4, r.getOriginLng());
            ps.setString(5, r.getDestName());
            ps.setDouble(6, r.getDestLat());
            ps.setDouble(7, r.getDestLng());
            ps.setTimestamp(8, DaoUtil.toTimestamp(r.getDepartFrom()));
            ps.setTimestamp(9, DaoUtil.toTimestamp(r.getDepartUntil()));
            ps.setInt(10, r.getMaxDetourM());
            ps.setBoolean(11, r.isSameGenderOnly());
            DaoUtil.setNullableInt(ps, 12, r.getEstDistanceM());
            DaoUtil.setNullableInt(ps, 13, r.getEstFare());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            throw new SQLException("요청 번호를 받아오지 못했습니다.");
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
        String sql = "SELECT * FROM ride_request WHERE member_id = ? ORDER BY created_at DESC";
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
     * 주기적으로(예: 1분마다 Timer 로) 호출하면 된다.
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
     *  1) 상대 요청이 WAITING 상태
     *  2) 내 요청이 아니고, 같은 사람의 요청도 아님
     *  3) 출발 가능 시간대가 서로 겹침
     *  4) 상대 회원이 정상(ACTIVE) 상태
     *  5) 한쪽이라도 "동성만"을 원하면 두 사람의 성별이 같아야 함
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
              + "JOIN member om ON om.member_id = o.member_id AND om.status = 'ACTIVE' "
              + "WHERE me.request_id = ? "
              + "  AND ((me.same_gender_only = 0 AND o.same_gender_only = 0) OR mm.gender = om.gender) "
              + "ORDER BY o.depart_from";
        List<RideRequest> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
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
        r.setDepartFrom(DaoUtil.getDateTime(rs, "depart_from"));
        r.setDepartUntil(DaoUtil.getDateTime(rs, "depart_until"));
        r.setMaxDetourM(rs.getInt("max_detour_m"));
        r.setSameGenderOnly(rs.getBoolean("same_gender_only"));
        r.setEstDistanceM(DaoUtil.getNullableInt(rs, "est_distance_m"));
        r.setEstFare(DaoUtil.getNullableInt(rs, "est_fare"));
        r.setStatus(RideRequest.Status.valueOf(rs.getString("status")));
        r.setCreatedAt(DaoUtil.getDateTime(rs, "created_at"));
        return r;
    }
}
