package Texi_signup;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import taxi.db.DBConnection;   // 기존 DB 연결 클래스를 "사용"만 한다 (수정하지 않음)

/**
 * 동의 정보만 DB 에 저장·조회하는 DAO.
 *
 * 기존 MemberDAO(회원가입·로그인)는 건드리지 않기 위해 따로 만들었다.
 * member 테이블의 동의 시각 컬럼 2개만 다룬다.
 *   - privacy_agreed_at  : 개인정보 수집·이용 동의 시각 (consent_migration.sql 로 추가)
 *   - location_agreed_at : 위치정보 수집·이용 동의 시각 (원래 스키마에 있음)
 *   → 값이 NULL 이면 "동의 안 함", 시각이 있으면 "그 시각에 동의함"
 */
public class ConsentDAO {

    /**
     * 회원 번호로 동의 시각을 저장한다. (기존 회원가입 API 가 member_id 를 돌려줄 때 사용)
     *
     * @return 저장에 성공하면 true, 해당 회원이 없으면 false
     */
    public boolean saveConsents(long memberId, boolean privacyAgreed, boolean locationAgreed)
            throws SQLException {
        String sql = "UPDATE member SET privacy_agreed_at = ?, location_agreed_at = ? WHERE member_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            setConsentParams(ps, privacyAgreed, locationAgreed);
            ps.setLong(3, memberId);
            return ps.executeUpdate() == 1;   // 바뀐 행이 1개면 성공
        }
    }

    /**
     * 이메일로 동의 시각을 저장한다. (기존 회원가입 API 가 member_id 를 돌려주지 않을 때 사용)
     * email 은 UNIQUE 라서 한 명만 찾아진다.
     */
    public boolean saveConsentsByEmail(String email, boolean privacyAgreed, boolean locationAgreed)
            throws SQLException {
        String sql = "UPDATE member SET privacy_agreed_at = ?, location_agreed_at = ? WHERE email = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            setConsentParams(ps, privacyAgreed, locationAgreed);
            ps.setString(3, email);
            return ps.executeUpdate() == 1;
        }
    }

    /**
     * 이 회원이 필수 동의를 모두 했는지 DB 에서 확인한다.
     * (예: 로그인 후, 동의 안 한 옛날 회원이면 동의 화면을 다시 보여 줄 때 사용)
     */
    public boolean hasAllConsents(long memberId) throws SQLException {
        String sql = "SELECT 1 FROM member WHERE member_id = ? "
                   + "AND privacy_agreed_at IS NOT NULL AND location_agreed_at IS NOT NULL";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    // 동의했으면 "지금 시각", 안 했으면 NULL 을 넣는다.
    // 두 동의를 같은 시각으로 맞추려고 now 를 한 번만 구한다.
    private void setConsentParams(PreparedStatement ps, boolean privacyAgreed, boolean locationAgreed)
            throws SQLException {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now().withNano(0));   // DATETIME 은 초 단위
        ps.setTimestamp(1, privacyAgreed ? now : null);
        ps.setTimestamp(2, locationAgreed ? now : null);
    }
}
