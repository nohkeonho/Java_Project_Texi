package taxi.auth;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * 비밀번호를 해시로 바꾸고, 로그인 때 비교해 주는 도구 클래스.
 *
 * schema.sql 의 member 테이블에는 솔트 전용 컬럼이 없고 password_hash VARCHAR(255) 하나만 있다.
 * 그래서 솔트와 해시를 한 문자열로 합쳐서 password_hash 에 저장한다.
 *
 *   저장 형식:  pbkdf2$반복횟수$솔트(Base64)$해시(Base64)
 *   예시:      pbkdf2$65536$q3k...Zw==$Xv9...a8=   (약 97자 → 255자 안에 충분히 들어감)
 *
 * 반복횟수까지 같이 저장해 두면, 나중에 반복횟수를 올려도 예전 회원 비밀번호를 그대로 검증할 수 있다.
 */
public final class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String PREFIX = "pbkdf2";
    private static final int ITERATIONS = 65_536; // 반복 횟수 (클수록 안전하지만 느려짐)
    private static final int KEY_LENGTH = 256;    // 결과 해시 길이(bit)
    private static final int SALT_BYTES = 16;     // 솔트 길이(byte)

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    /** 회원가입 때 사용: 비밀번호 → DB에 저장할 문자열 */
    public static String encode(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(password, salt, ITERATIONS);
        Base64.Encoder b64 = Base64.getEncoder();
        return PREFIX + "$" + ITERATIONS + "$" + b64.encodeToString(salt) + "$" + b64.encodeToString(hash);
    }

    /** 로그인 때 사용: 입력한 비밀번호가 DB에 저장된 문자열과 맞는지 확인 */
    public static boolean matches(String password, String stored) {
        if (stored == null) {
            return false;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            return false; // 형식이 다른 값(예: 직접 넣은 테스트 데이터)은 로그인 실패로 처리
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = pbkdf2(password, salt, iterations);
            // 비교 시간이 항상 같아서 "시간 차이로 추측하는 공격"을 막아준다.
            return MessageDigest.isEqual(actual, expected);
        } catch (IllegalArgumentException e) { // 숫자/Base64 형식이 깨진 경우
            return false;
        }
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH);
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("비밀번호 해시 처리 중 오류가 발생했습니다.", e);
        }
    }
}
