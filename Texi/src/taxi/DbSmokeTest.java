package taxi;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import taxi.db.DBConnection;

/**
 * DB 연결이 잘 되는지 확인하는 테스트용 main.
 * 실행해서 "연결 성공" 이 나오면 db.properties 설정과 JDBC 드라이버(jar)가 제대로 된 것이다.
 */
public class DbSmokeTest {

    public static void main(String[] args) {
        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM member")) {

            System.out.println("연결 성공! DB: " + con.getCatalog());
            rs.next();
            System.out.println("현재 회원 수: " + rs.getInt(1));

        } catch (SQLException e) {
            System.out.println("연결 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
