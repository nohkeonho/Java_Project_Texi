package taxi;

import java.util.Arrays;
import java.util.Map;

import taxi.dao.RatingDAO;
import taxi.model.RatingSummary;

/**
 * 매칭 점수용 평점 조회 API 테스트.
 *
 *  1부: 점수 계산만 확인 (DB 필요 없음) -> 바로 실행 가능
 *  2부: 실제 DB 에서 조회 (DbSmokeTest 가 성공하는 환경이어야 함)
 */
public class RatingScoreTest {

    public static void main(String[] args) {
        System.out.println("===== 1부: 점수 계산 (DB 없이) =====");
        print("신규 회원 (평점 0개)", new RatingSummary(1, 0.00, 0));
        print("평점 1개, 5점",        new RatingSummary(2, 5.00, 1));
        print("평점 50개, 평균 4.80", new RatingSummary(3, 4.80, 50));
        print("평점 3개, 평균 1.00",  new RatingSummary(4, 1.00, 3));
        print("평점 100개, 평균 1.20", new RatingSummary(5, 1.20, 100));
        System.out.println("-> 평점 1개짜리 5점보다 50개짜리 4.8점이 더 높게 나오면 정상\n");

        System.out.println("===== 2부: DB 조회 =====");
        RatingDAO dao = new RatingDAO();
        try {
            System.out.println("한 명 조회 (1번): " + dao.findSummary(1));
            System.out.println("없는 회원 (999999번): " + dao.findSummary(999999));

            // 중복(1)과 없는 번호(999999)를 섞어서 넣어 본다
            Map<Long, RatingSummary> map = dao.findSummaries(Arrays.asList(1L, 2L, 3L, 1L, 999999L));
            System.out.println("여러 명 조회 결과 " + map.size() + "명:");
            for (RatingSummary s : map.values()) {
                System.out.println("  " + s);
            }
        } catch (Exception e) {
            System.out.println("DB 조회 실패 (DB 연결 설정을 확인하세요): " + e.getMessage());
        }
    }

    private static void print(String label, RatingSummary s) {
        System.out.printf("%-20s 보정평균 %.2f  ->  평점 점수 %6.2f%n",
                label, s.getAdjustedRating(), s.getRatingScore());
    }
}
