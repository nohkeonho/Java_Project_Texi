package taxi;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import taxi.dao.MemberDAO;
import taxi.dao.RideMatchDAO;
import taxi.dao.RideRequestDAO;
import taxi.model.MatchMember;
import taxi.model.Member;
import taxi.model.RideMatch;
import taxi.model.RideRequest;
import taxi.service.RatingService;
import taxi.service.ReportService;

/**
 * 평점·신고 기능(RatingService, ReportService)이 잘 동작하는지 확인하는 테스트용 main.
 *
 * 하는 일
 *   1) 테스트용 가상 회원 2명(A, B)을 만들고, 두 사람을 한 매칭으로 묶는다.
 *   2) 여러 상황(평점 미입력, 정상 입력, 중복 입력 등)을 차례로 실행해 본다.
 *   3) 상황마다 기대한 결과가 나오면 [통과], 아니면 [실패]를 출력한다.
 *
 * 실행 전 준비: DbSmokeTest 를 먼저 실행해서 "연결 성공"이 나오는지 확인할 것.
 * 참고: 실행할 때마다 가상 회원(is_virtual = 1) 2명과 매칭 1건이 DB 에 새로 쌓인다.
 */
public class ReviewSmokeTest {

    private static int passCount = 0;
    private static int failCount = 0;

    public static void main(String[] args) {
        try {
            run();
        } catch (SQLException e) {
            System.out.println("DB 오류로 테스트를 끝까지 진행하지 못했습니다: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println();
        System.out.println("결과: 통과 " + passCount + "개 / 실패 " + failCount + "개");
    }

    private static void run() throws SQLException {
        MemberDAO memberDAO = new MemberDAO();
        RideRequestDAO requestDAO = new RideRequestDAO();
        RideMatchDAO matchDAO = new RideMatchDAO();
        RatingService ratingService = new RatingService();
        ReportService reportService = new ReportService();

        // ---------- 1. 테스트 데이터 만들기 ----------
        // 이메일·닉네임은 중복이 안 되므로, 현재 시각(밀리초)을 붙여서 실행할 때마다 다른 값이 되게 한다.
        long stamp = System.currentTimeMillis();
        long memberA = memberDAO.insert(newMember("a" + stamp));
        long memberB = memberDAO.insert(newMember("b" + stamp));
        // 합승 요청은 위치정보에 동의한 회원만 할 수 있으므로(RideRequestDAO), 두 사람 모두 동의 처리
        memberDAO.agreeLocation(memberA);
        memberDAO.agreeLocation(memberB);
        long requestA = requestDAO.insert(newRequest(memberA));
        long requestB = requestDAO.insert(newRequest(memberB));

        RideMatch match = new RideMatch();
        match.setTotalDistanceM(5000);
        match.setTotalFare(8000);
        List<MatchMember> members = new ArrayList<>();
        members.add(newMatchMember(requestA, memberA, 1));
        members.add(newMatchMember(requestB, memberB, 2));
        long matchId = matchDAO.createMatch(match, members);
        System.out.println("테스트 데이터 준비 완료 (회원 " + memberA + ", " + memberB + " / 매칭 " + matchId + ")");
        System.out.println();

        // ---------- 2. 합승이 끝나기 전에는 평점을 줄 수 없어야 한다 ----------
        try {
            ratingService.submit(matchId, memberA, memberB, 5);
            check("완료 전 평점은 거절", false);
        } catch (IllegalStateException e) {
            check("완료 전 평점은 거절", true);
        }

        // 두 사람 모두 수락 -> 매칭 확정 -> 합승 완료 처리
        for (MatchMember mm : matchDAO.findMembersByMatchId(matchId)) {
            matchDAO.respond(mm.getMatchMemberId(), MatchMember.Response.ACCEPTED);
        }
        check("매칭 완료 처리", matchDAO.complete(matchId));

        // ---------- 3. 평점 ----------
        check("평점 미입력(null)이면 저장 안 하고 false", !ratingService.submit(matchId, memberA, memberB, null));

        try {
            ratingService.submit(matchId, memberA, memberB, 6);
            check("6점은 거절", false);
        } catch (IllegalArgumentException e) {
            check("6점은 거절", true);
        }

        check("5점 입력하면 저장하고 true", ratingService.submit(matchId, memberA, memberB, 5));
        check("B의 평균 평점이 5.0", ratingService.getAverageRating(memberB) == 5.0);
        check("B의 평점 개수가 1", ratingService.getRatingCount(memberB) == 1);

        try {
            ratingService.submit(matchId, memberA, memberB, 3);
            check("같은 사람에게 두 번 평점은 거절", false);
        } catch (IllegalStateException e) {
            check("같은 사람에게 두 번 평점은 거절", true);
        }

        // ---------- 4. 신고 ----------
        check("신고 내용 없음(null)이면 저장 안 하고 false", !reportService.submit(matchId, memberA, memberB, null));
        check("신고 내용이 공백뿐이면 저장 안 하고 false", !reportService.submit(matchId, memberA, memberB, "   "));
        check("신고 내용이 있으면 저장하고 true", reportService.submit(matchId, memberA, memberB, "테스트 신고입니다."));

        try {
            reportService.submit(matchId, memberA, memberB, "두 번째 신고");
            check("같은 사람을 두 번 신고는 거절", false);
        } catch (IllegalStateException e) {
            check("같은 사람을 두 번 신고는 거절", true);
        }
    }

    // 기대한 결과(ok)가 맞으면 [통과], 아니면 [실패]를 출력하고 개수를 센다.
    private static void check(String name, boolean ok) {
        System.out.println((ok ? "[통과] " : "[실패] ") + name);
        if (ok) {
            passCount++;
        } else {
            failCount++;
        }
    }

    // ---------- 테스트 데이터를 만드는 도우미 메서드 ----------

    private static Member newMember(String key) {
        Member m = new Member();
        m.setEmail("test_" + key + "@example.com");
        m.setPasswordHash("test");      // 테스트용이라 아무 값이나 넣는다
        m.setNickname("t" + key);
        m.setGender("M");
        m.setVirtual(true);             // 가상 사용자 표시 (나중에 구분하기 쉽게)
        return m;
    }

    private static RideRequest newRequest(long memberId) {
        RideRequest r = new RideRequest();
        r.setMemberId(memberId);
        r.setOriginName("선문대 정문 앞");
        r.setOriginLat(36.800);
        r.setOriginLng(127.075);
        r.setDestName("천안역 동부광장 앞");
        r.setDestLat(36.810);
        r.setDestLng(127.146);
        r.setDepartFrom(LocalDateTime.now());
        r.setDepartUntil(LocalDateTime.now().plusMinutes(30));
        return r;
    }

    private static MatchMember newMatchMember(long requestId, long memberId, int order) {
        MatchMember mm = new MatchMember();
        mm.setRequestId(requestId);
        mm.setMemberId(memberId);
        mm.setPickupOrder(order);
        mm.setDropoffOrder(order);
        mm.setOwnDistanceM(5000);
        mm.setDetourM(0);
        mm.setFareShare(4000);
        return mm;
    }
}
