package Login_API;

import Login_API.auth.AuthException;
import Login_API.auth.AuthService;
import taxi.model.Member;

/**
 * 실제 MySQL(taxi_share)에 연결해서 회원가입 → 중복검사 → 로그인을 확인하는 테스트용 main.
 * DbSmokeTest 가 성공한 뒤에 실행할 것.
 *
 * 실행할 때마다 다른 이메일을 쓰도록 현재 시각을 붙인다. (UNIQUE 때문에 같은 이메일은 두 번 못 넣음)
 * 테스트로 생긴 회원은 MySQL 에서 직접 지우면 된다:
 *   DELETE FROM member WHERE email LIKE 'smoke%@test.com';
 */
public class AuthSmokeTest {

    public static void main(String[] args) {
        AuthService auth = new AuthService();
        long stamp = System.currentTimeMillis() % 100000;   // 5자리 숫자
        String email = "smoke" + stamp + "@test.com";
        String nickname = "테스트" + stamp;

        try {
            System.out.println("1) 이메일 사용 가능? " + auth.isEmailAvailable(email));

            long id = auth.signUp(email, "abc12345", "abc12345", nickname, "M");
            System.out.println("2) 회원가입 성공! member_id = " + id);

            System.out.println("3) 가입 후 같은 이메일 사용 가능? " + auth.isEmailAvailable(email) + " (false 가 정상)");

            Member me = auth.login(email, "abc12345");
            System.out.println("4) 로그인 성공! " + me);

        } catch (AuthException e) {
            System.out.println("실패: " + e.getMessage());
            if (e.getCause() != null) {
                e.getCause().printStackTrace(); // DB 오류라면 원인 출력
            }
            return;
        }

        try {
            auth.login(email, "wrong1234");
            System.out.println("5) [이상] 틀린 비밀번호로 로그인됨");
        } catch (AuthException e) {
            System.out.println("5) 틀린 비밀번호 차단: " + e.getMessage());
        }
    }
}
