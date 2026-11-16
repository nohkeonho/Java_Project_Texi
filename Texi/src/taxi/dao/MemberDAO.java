package taxi.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import taxi.db.DBConnection;
import taxi.model.Member;

/**
 * member 테이블(회원)을 다루는 DAO (Data Access Object).
 *
 * DAO 란? "SQL 을 실행하는 일"만 맡는 클래스다.
 * 화면(Swing)은 SQL 을 몰라도 되고, memberDAO.findByEmail(...) 처럼 메서드만 부르면 된다.
 *
 * 모든 SQL 은 PreparedStatement 와 ? 자리표시자를 사용한다.
 * (문자열을 이어 붙여 SQL 을 만들면 SQL 인젝션 공격에 뚫린다!)
 */
public class MemberDAO {

    // 조회할 때 자주 쓰는 컬럼 목록 (비밀번호 해시 포함)
    private static final String COLUMNS =
            "member_id, email, password_hash, nickname, gender, "
          + "location_agreed_at, is_virtual, avg_rating, rating_count, status, created_at";

    /** 회원 가입. 새로 만들어진 member_id 를 돌려준다. */
    public long insert(Member m) throws SQLException {
        String sql = "INSERT INTO member (email, password_hash, nickname, gender, is_virtual) "
                   + "VALUES (?, ?, ?, ?, ?)";
        // RETURN_GENERATED_KEYS : AUTO_INCREMENT 로 생성된 번호를 돌려받겠다는 옵션
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, m.getEmail());
            ps.setString(2, m.getPasswordHash());
            ps.setString(3, m.getNickname());
            ps.setString(4, m.getGender());
            ps.setBoolean(5, m.isVirtual());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            throw new SQLException("회원 번호를 받아오지 못했습니다.");
        }
    }

    /** 번호로 회원 한 명 찾기. 없으면 null. */
    public Member findById(long memberId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM member WHERE member_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    /** 이메일로 회원 한 명 찾기 (로그인할 때 사용). 없으면 null. */
    public Member findByEmail(String email) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM member WHERE email = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    /** 이메일 중복 확인. 이미 있으면 true. */
    public boolean existsByEmail(String email) throws SQLException {
        return exists("SELECT 1 FROM member WHERE email = ?", email);
    }

    /** 닉네임 중복 확인. 이미 있으면 true. */
    public boolean existsByNickname(String nickname) throws SQLException {
        return exists("SELECT 1 FROM member WHERE nickname = ?", nickname);
    }

    /** 위치정보 수집·이용에 동의했을 때 동의 시각을 기록한다. */
    public void agreeLocation(long memberId) throws SQLException {
        String sql = "UPDATE member SET location_agreed_at = NOW() WHERE member_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, memberId);
            ps.executeUpdate();
        }
    }

    /** 회원 상태 변경 (신고 누적 시 SUSPENDED, 탈퇴 시 WITHDRAWN 등). */
    public void updateStatus(long memberId, Member.Status status) throws SQLException {
        String sql = "UPDATE member SET status = ? WHERE member_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, status.name());   // enum -> 문자열 ("ACTIVE" 등)
            ps.setLong(2, memberId);
            ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------------
    // 내부 도우미 메서드
    // ------------------------------------------------------------------

    private boolean exists(String sql, String value) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** ResultSet 의 현재 행 1줄을 Member 객체로 바꾼다. */
    private Member mapRow(ResultSet rs) throws SQLException {
        Member m = new Member();
        m.setMemberId(rs.getLong("member_id"));
        m.setEmail(rs.getString("email"));
        m.setPasswordHash(rs.getString("password_hash"));
        m.setNickname(rs.getString("nickname"));
        m.setGender(rs.getString("gender"));
        m.setLocationAgreedAt(DaoUtil.getDateTime(rs, "location_agreed_at"));
        m.setVirtual(rs.getBoolean("is_virtual"));
        m.setAvgRating(rs.getDouble("avg_rating"));
        m.setRatingCount(rs.getInt("rating_count"));
        m.setStatus(Member.Status.valueOf(rs.getString("status")));   // 문자열 -> enum
        m.setCreatedAt(DaoUtil.getDateTime(rs, "created_at"));
        return m;
    }
}
