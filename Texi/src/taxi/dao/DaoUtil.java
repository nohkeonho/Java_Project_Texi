package taxi.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;

/**
 * DAO 들이 같이 쓰는 작은 도구 모음.
 *
 * DB 의 NULL 값을 다룰 때 자바의 int 는 null 을 담을 수 없어서 불편하다.
 * 그래서 NULL 일 수 있는 컬럼은 Integer / Double 로 받고, 변환은 여기서 한꺼번에 처리한다.
 */
final class DaoUtil {

    private DaoUtil() {
    }

    /** Integer 값을 넣되, null 이면 DB 에도 NULL 로 넣는다. */
    static void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    /** Double 값을 넣되, null 이면 DB 에도 NULL 로 넣는다. */
    static void setNullableDouble(PreparedStatement ps, int index, Double value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.DECIMAL);
        } else {
            ps.setDouble(index, value);
        }
    }

    /** NULL 컬럼을 읽을 때 쓴다. (getInt 는 NULL 이면 0 을 돌려줘서 구분이 안 된다. wasNull 로 확인!) */
    static Integer getNullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    static Double getNullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    /** DB 의 DATETIME 을 자바의 LocalDateTime 으로 바꾼다. NULL 이면 null. */
    static LocalDateTime getDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return (ts == null) ? null : ts.toLocalDateTime();
    }

    /** LocalDateTime 을 DB 에 넣을 수 있는 Timestamp 로 바꾼다. */
    static Timestamp toTimestamp(LocalDateTime time) {
        return (time == null) ? null : Timestamp.valueOf(time);
    }

    /**
     * IN ( ?, ?, ? ) 처럼 개수가 정해지지 않은 자리표시자를 만든다.
     * 예) placeholders(3) -> "?, ?, ?"
     * 값을 문자열로 직접 이어 붙이지 않고 ? 를 쓰기 때문에 SQL 인젝션에 안전하다.
     */
    static String placeholders(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append('?');
        }
        return sb.toString();
    }
}
