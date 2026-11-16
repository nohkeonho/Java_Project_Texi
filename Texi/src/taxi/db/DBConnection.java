package taxi.db;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * MySQL 연결을 만들어 주는 클래스.
 *
 * - DB 주소, 아이디, 비밀번호는 코드에 직접 쓰지 않고 db.properties 파일에서 읽는다.
 *   (비밀번호가 GitHub에 올라가는 사고를 막기 위해서다. db.properties 는 .gitignore 에 추가할 것!)
 * - 사용법:  try (Connection con = DBConnection.getConnection()) { ... }
 *   try-with-resources 를 쓰면 블록이 끝날 때 연결이 자동으로 닫힌다.
 */
public class DBConnection {

    // 프로그램을 실행하는 폴더(작업 폴더)에 있는 설정 파일 이름
    private static final String CONFIG_FILE = "db.properties";

    private static final String URL;
    private static final String USER;
    private static final String PASSWORD;

    // static 블록: 클래스가 처음 사용될 때 딱 한 번 실행된다. 여기서 설정 파일을 읽는다.
    static {
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(CONFIG_FILE)) {
            props.load(in);
        } catch (IOException e) {
            // 설정 파일이 없으면 프로그램을 계속할 수 없으므로 바로 에러로 알려 준다.
            throw new ExceptionInInitializerError(
                    CONFIG_FILE + " 파일을 읽을 수 없습니다. db.properties.example 을 복사해서 만들어 주세요. (" + e.getMessage() + ")");
        }
        URL = props.getProperty("db.url");
        USER = props.getProperty("db.user");
        PASSWORD = props.getProperty("db.password");
    }

    // 객체를 만들 필요가 없는 클래스이므로 생성자를 막아 둔다.
    private DBConnection() {
    }

    /**
     * 새 DB 연결을 하나 만들어서 돌려준다.
     * 호출한 쪽에서 반드시 close() 해야 한다. (try-with-resources 권장)
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
