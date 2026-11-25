package taxi.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import taxi.db.DBConnection;
import taxi.model.Report;

/** report 테이블(신고)을 다루는 DAO. */
public class ReportDAO {

    /**
     * 신고 접수. 새로 만들어진 report_id 를 돌려준다.
     *
     * 평점과 마찬가지로, 완료된 매칭에 함께 참여한 사람끼리만 신고할 수 있다.
     * (아무나 아무나를 신고하는 악용을 막기 위해서다.)
     *
     * @throws IllegalStateException 완료된 매칭의 참여자끼리가 아닐 때
     * @throws java.sql.SQLIntegrityConstraintViolationException
     *         같은 매칭에서 같은 사람을 이미 신고했을 때 (UNIQUE 제약 위반)
     */
    public long insert(Report r) throws SQLException {
        if (r.getReporterId() == r.getReportedId()) {
            throw new IllegalArgumentException("자기 자신은 신고할 수 없습니다.");
        }

        String sql = "INSERT INTO report (match_id, reporter_id, reported_id, reason, detail) "
                   + "VALUES (?, ?, ?, ?, ?)";
        try (Connection con = DBConnection.getConnection()) {
            if (!RideMatchDAO.isCompletedWithBothMembers(con, r.getMatchId(), r.getReporterId(), r.getReportedId())) {
                throw new IllegalStateException("완료된 매칭에 함께 참여한 사람만 신고할 수 있습니다.");
            }
            try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, r.getMatchId());
                ps.setLong(2, r.getReporterId());
                ps.setLong(3, r.getReportedId());
                ps.setString(4, r.getReason().name());
                ps.setString(5, r.getDetail());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getLong(1);
                    }
                }
                throw new SQLException("신고 번호를 받아오지 못했습니다.");
            }
        }
    }

    /**
     * 번호로 신고 한 건 찾기. 없으면 null.
     * (관리자가 신고를 반려/처리할 때, 신고당한 사람이 누구인지 알아내는 데 쓴다)
     */
    public Report findById(long reportId) throws SQLException {
        List<Report> list = findList("SELECT * FROM report WHERE report_id = ?", reportId);
        return list.isEmpty() ? null : list.get(0);
    }

    /** 어떤 회원이 신고당한 내역 (최신순). */
    public List<Report> findByReportedId(long reportedId) throws SQLException {
        return findList("SELECT * FROM report WHERE reported_id = ? ORDER BY created_at DESC", reportedId);
    }

    /** 어떤 회원이 접수한 신고 내역 (최신순). "내 신고 내역" 화면용. */
    public List<Report> findByReporterId(long reporterId) throws SQLException {
        return findList("SELECT * FROM report WHERE reporter_id = ? ORDER BY created_at DESC", reporterId);
    }

    /**
     * 어떤 회원이 신고당한 횟수.
     * "처리 결과가 반려(REJECTED)된 신고"는 제외하고 센다. 억울한 신고로 이용정지되는 것을 막기 위해서다.
     * 참고: 이용정지 판단은 이 메서드가 아니라 ReportPenaltyService 가 한다.
     *       (그쪽은 같은 사람의 신고를 1명으로 세고, 정지와 요청 취소를 한 트랜잭션으로 처리한다)
     *       이 메서드는 "신고 접수 건수" 자체를 화면에 보여 줄 때 쓴다.
     */
    public int countAgainst(long reportedId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM report WHERE reported_id = ? AND status <> 'REJECTED'";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, reportedId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /**
     * 신고 처리 상태 변경 (관리자 기능).
     * 처리완료(RESOLVED)나 반려(REJECTED)로 바꿀 때는 처리 시각도 함께 기록한다.
     */
    public void updateStatus(long reportId, Report.Status status) throws SQLException {
        boolean finished = (status == Report.Status.RESOLVED || status == Report.Status.REJECTED);
        String sql = finished
                ? "UPDATE report SET status = ?, resolved_at = NOW() WHERE report_id = ?"
                : "UPDATE report SET status = ?, resolved_at = NULL WHERE report_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setLong(2, reportId);
            ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------------
    // 내부 도우미 메서드
    // ------------------------------------------------------------------

    /** "? 가 숫자 하나" 인 SELECT 를 실행해서 Report 목록으로 돌려주는 도우미. */
    private List<Report> findList(String sql, long param) throws SQLException {
        List<Report> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    private Report mapRow(ResultSet rs) throws SQLException {
        Report r = new Report();
        r.setReportId(rs.getLong("report_id"));
        r.setMatchId(rs.getLong("match_id"));
        r.setReporterId(rs.getLong("reporter_id"));
        r.setReportedId(rs.getLong("reported_id"));
        r.setReason(Report.Reason.valueOf(rs.getString("reason")));
        r.setDetail(rs.getString("detail"));
        r.setStatus(Report.Status.valueOf(rs.getString("status")));
        r.setCreatedAt(DaoUtil.getDateTime(rs, "created_at"));
        r.setResolvedAt(DaoUtil.getDateTime(rs, "resolved_at"));
        return r;
    }
}
