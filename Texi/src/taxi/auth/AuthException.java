package taxi.auth;

/**
 * 회원가입/로그인 중 "사용자에게 보여줄 오류 메시지"를 담아 던지는 예외.
 *
 * 화면(Swing)에서는 catch 해서 e.getMessage()를 JOptionPane 으로 띄우면 된다.
 */
public class AuthException extends Exception {

    private static final long serialVersionUID = 1L; // 직렬화 버전 번호 (컴파일러 경고 방지용)

    public AuthException(String message) {
        super(message);
    }

    /** DB 오류처럼 원인 예외(cause)가 있을 때 사용. 원인은 e.getCause()로 꺼내 디버깅에 쓴다. */
    public AuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
