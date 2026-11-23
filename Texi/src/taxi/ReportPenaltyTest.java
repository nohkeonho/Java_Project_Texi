package taxi;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import Login_API.auth.AuthException;
import Login_API.auth.AuthService;
import Login_API.auth.PasswordHasher;
import taxi.dao.MemberDAO;
import taxi.dao.ReportDAO;
import taxi.dao.RideMatchDAO;
import taxi.dao.RideRequestDAO;
import taxi.db.DBConnection;
import taxi.model.MatchMember;
import taxi.model.Member;
import taxi.model.PenaltyResult;
import taxi.model.Report;
import taxi.model.RideMatch;
import taxi.model.RideRequest;
import taxi.service.ReportPenaltyService;
import taxi.service.ReportService;

/**
 * 신고 누적 처리 통합 테스트용 main.
 *
 * 회원가입(가상 회원) → 합승 요청 → 매칭 → 수락 → 완료 → 신고 → 누적 정지 → 관리자 반려 → 정지 해제
 * 까지 실제 DAO·Service 를 그대로 사용해서 처음부터 끝까지 한 번에 확인한다.
 *
 * 등장인물 (모두 is_virtual = 1 인 가상 회원, 비밀번호 test1234)
 *   T  : 신고당하는 사람
 *   R1 : T 와 두 번 합승하고 두 번 다 신고함 (그래도 1명으로 세져야 함)
 *   R2 : T 를 신고 -> 2명째, 경고
 *   R3 : T 를 신고 -> 3명째, 이용정지
 *   P  : T 와 매칭 "제안" 상태였던 사람 (T 가 정지되면 P 의 요청은 다시 대기 상태로 돌아가야 함)
 *
 * 실행 전 준비: schema.sql → consent_migration.sql → ride_migration.sql 실행, DbSmokeTest 성공 확인.
 * 마지막 줄이 "결과: 통과 N개 / 실패 0개" 면 정상이다.
 */
public class ReportPenaltyTest {

    private static final String PASSWORD = "test1234";

    private static final MemberDAO memberDAO = new MemberDAO();
    private static final RideRequestDAO requestDAO = new RideRequestDAO();
    private static final RideMatchDAO matchDAO = new RideMatchDAO();
    private static final ReportDAO reportDAO = new ReportDAO();
    private static final ReportService reportService = new ReportService();
    private static final ReportPenaltyService penaltyService = new ReportPenaltyService();
    private static final AuthService authService = new AuthService();

    private static int passCount = 0;
    private static int failCount = 0;

    /** 테스트 요청의 기준 출발 시각 (지금부터 1시간 뒤) */
    private static final LocalDateTime BASE = LocalDateTime.now().plusHours(1).withSecond(0).withNano(0);

    public static void main(String[] args) {
        try {
            run();
        } catch (Exception e) {
            System.out.println("예상하지 못한 오류로 테스트를 끝까지 진행하지 못했습니다: " + e);
            e.printStackTrace();
            failCount++;
        }
        System.out.println();
        System.out.println("결과: 통과 " + passCount + "개 / 실패 " + failCount + "개");
    }

    private static void run() throws SQLException {
        // ---------- 0. 등장인물 만들기 ----------
        long stamp = System.currentTimeMillis() % 1_000_000_000L;   // 이메일·닉네임 중복 방지
        long t  = createMember("pt" + stamp + "t");
        long r1 = createMember("pt" + stamp + "r1");
        long r2 = createMember("pt" + stamp + "r2");
        long r3 = createMember("pt" + stamp + "r3");
        long p  = createMember("pt" + stamp + "p");
        String tEmail = "pt" + stamp + "t@test.com";
        System.out.println("등장인물: T=" + t + ", R1=" + r1 + ", R2=" + r2 + ", R3=" + r3 + ", P=" + p);
        System.out.println("기준: 서로 다른 신고자 " + ReportPenaltyService.SUSPEND_THRESHOLD + "명이면 이용정지\n");

        // T 는 여러 번 합승하므로, 시간이 겹치지 않게 "몇 번째 시간칸(slot)" 인지로 구분한다.
        int slot = 0;

        // ---------- 1. 첫 신고 : 1명 ----------
        long m1 = completedRide(r1, t, slot++);
        check("R1 첫 신고 저장", reportService.submit(m1, r1, t, Report.Reason.ABUSE, "욕설을 했습니다."));
        PenaltyResult after1 = penaltyService.preview(t);
        check("신고자 1명, 정상 상태", after1.getValidReporterCount() == 1 && after1.getStatus() == Member.Status.ACTIVE);
        check("1명일 때는 경고 아님", !after1.isWarning() && after1.getMessage() == null);

        // ---------- 2. 같은 사람이 다른 합승에서 또 신고 : 여전히 1명 ----------
        long m2 = completedRide(r1, t, slot++);
        check("R1 두 번째 합승에서 다시 신고 저장", reportService.submit(m2, r1, t, "또 욕설을 했습니다."));
        check("같은 사람의 신고는 1명으로 셈", penaltyService.preview(t).getValidReporterCount() == 1);
        check("신고 접수 건수(countAgainst)는 2건", reportDAO.countAgainst(t) == 2);

        // ---------- 3. 두 번째 사람 : 경고 ----------
        long m3 = completedRide(r2, t, slot++);
        check("R2 신고 저장", reportService.submit(m3, r2, t, Report.Reason.NO_SHOW, "약속 장소에 나오지 않았습니다."));
        PenaltyResult warn = penaltyService.preview(t);
        check("신고자 2명이면 경고 상태", warn.isWarning() && warn.getRemaining() == 1);
        System.out.println("    경고 문구: " + warn.getMessage());

        // ---------- 4. 정지 직전 상황 만들기 : T 의 대기 요청 + T 와 P 의 제안된 매칭 ----------
        // (R3 의 합승을 먼저 끝내 두어야 한다. 정지된 뒤에는 매칭 완료 처리를 할 수 없으므로)
        long m4 = completedRide(r3, t, slot++);

        long tProposedReq = requestDAO.insert(newRequest(t, slot));
        long pProposedReq = requestDAO.insert(newRequest(p, slot));
        long proposedMatch = createMatch(tProposedReq, t, pProposedReq, p);
        slot++;
        long tWaitingReq = requestDAO.insert(newRequest(t, slot++));
        check("정지 전: 제안된 매칭 상태 PROPOSED", matchDAO.findById(proposedMatch).getStatus() == RideMatch.Status.PROPOSED);

        // ---------- 5. 세 번째 사람 : 이용정지 ----------
        check("R3 신고 저장 (신고자에게는 정상 저장으로 보임)",
                reportService.submit(m4, r3, t, Report.Reason.FARE_DISPUTE, "요금을 내지 않았습니다."));
        check("신고자 3명 -> T 이용정지", memberDAO.findById(t).getStatus() == Member.Status.SUSPENDED);
        check("T 의 대기 요청 자동 취소", requestStatus(tWaitingReq) == RideRequest.Status.CANCELLED);
        check("T 가 들어 있던 제안 매칭 자동 취소", matchDAO.findById(proposedMatch).getStatus() == RideMatch.Status.CANCELLED);
        check("그 매칭의 T 요청은 취소", requestStatus(tProposedReq) == RideRequest.Status.CANCELLED);
        check("그 매칭의 P 요청은 다시 대기(WAITING)", requestStatus(pProposedReq) == RideRequest.Status.WAITING);
        check("완료된 합승 기록은 그대로 유지", matchDAO.findById(m1).getStatus() == RideMatch.Status.COMPLETED);

        // ---------- 6. 정지된 회원이 할 수 없는 일 ----------
        try {
            authService.login(tEmail, PASSWORD);
            check("정지 회원 로그인 차단", false);
        } catch (AuthException e) {
            check("정지 회원 로그인 차단 (" + e.getMessage() + ")", e.getMessage().contains("정지"));
        }
        try {
            requestDAO.insert(newRequest(t, slot++));
            check("정지 회원 합승 요청 차단", false);
        } catch (IllegalStateException e) {
            check("정지 회원 합승 요청 차단", true);
        }
        check("다시 검사해도 결과 같음 (중복 처리 없음)", penaltyService.evaluate(t).getAction() == PenaltyResult.Action.NONE);

        // ---------- 7. 관리자 : R3 의 신고를 반려 -> 정지 해제 ----------
        long r3ReportId = findReportId(t, r3);
        PenaltyResult restored = reportService.reject(r3ReportId);
        check("R3 신고 반려 -> 정지 해제(RESTORED)", restored.getAction() == PenaltyResult.Action.RESTORED);
        check("해제 후 상태 ACTIVE, 신고자 2명", restored.getStatus() == Member.Status.ACTIVE
                && restored.getValidReporterCount() == 2);
        try {
            authService.login(tEmail, PASSWORD);
            check("해제 후 로그인 가능", true);
        } catch (AuthException e) {
            check("해제 후 로그인 가능 (" + e.getMessage() + ")", false);
        }

        // ---------- 8. 관리자 : R2 의 신고를 처리완료 -> 누적은 그대로 ----------
        PenaltyResult resolved = reportService.resolve(findReportId(t, r2));
        check("처리완료(RESOLVED)된 신고는 계속 누적에 포함", resolved.getValidReporterCount() == 2
                && resolved.getAction() == PenaltyResult.Action.NONE);

        // ---------- 9. 잘못된 입력 ----------
        try {
            reportService.reject(999_999_999L);
            check("없는 신고 번호 반려는 거절", false);
        } catch (IllegalArgumentException e) {
            check("없는 신고 번호 반려는 거절", true);
        }
        check("없는 회원 미리보기는 null", penaltyService.preview(999_999_999L) == null);
    }

    // =================================================================
    // 테스트 데이터를 만드는 도우미 메서드
    // =================================================================

    /** a 와 b 가 함께 탄 합승을 만들고 "완료" 까지 처리한다. 완료된 매칭 번호를 돌려준다. */
    private static long completedRide(long a, long b, int slot) throws SQLException {
        long reqA = requestDAO.insert(newRequest(a, slot));
        long reqB = requestDAO.insert(newRequest(b, slot));
        long matchId = createMatch(reqA, a, reqB, b);
        for (MatchMember mm : matchDAO.findMembersByMatchId(matchId)) {
            matchDAO.respond(mm.getMatchMemberId(), MatchMember.Response.ACCEPTED);
        }
        if (!matchDAO.complete(matchId)) {
            throw new IllegalStateException("테스트 준비 실패: 매칭 " + matchId + " 을(를) 완료 처리하지 못했습니다.");
        }
        return matchId;
    }

    /** 2인 매칭 (총 8000원 = 4000 + 4000) */
    private static long createMatch(long reqA, long memberA, long reqB, long memberB) throws SQLException {
        RideMatch match = new RideMatch();
        match.setTotalDistanceM(8200);
        match.setTotalFare(8000);
        List<MatchMember> members = new ArrayList<>();
        members.add(newMatchMember(reqA, memberA, 1));
        members.add(newMatchMember(reqB, memberB, 2));
        return matchDAO.createMatch(match, members);
    }

    private static MatchMember newMatchMember(long requestId, long memberId, int order) {
        MatchMember mm = new MatchMember();
        mm.setRequestId(requestId);
        mm.setMemberId(memberId);   // (RideMatchDAO 가 DB 값으로 다시 채우지만, 명확하게 넣어 둔다)
        mm.setPickupOrder(order);
        mm.setDropoffOrder(order);
        mm.setOwnDistanceM(7000);
        mm.setDetourM(0);
        mm.setFareShare(4000);
        return mm;
    }

    /** slot 번째 시간칸(2시간 간격)에 출발하는 요청. 같은 slot 끼리는 시간이 겹쳐서 매칭할 수 있다. */
    private static RideRequest newRequest(long memberId, int slot) {
        LocalDateTime from = BASE.plusHours(2L * slot);
        RideRequest r = new RideRequest();
        r.setMemberId(memberId);
        r.setOriginName("선문대 정문 앞");
        r.setOriginLat(36.7990);
        r.setOriginLng(127.0750);
        r.setDestName("천안역 동부광장 앞");
        r.setDestLat(36.8095);
        r.setDestLng(127.1462);
        r.setDepartFrom(from);
        r.setDepartUntil(from.plusMinutes(20));
        return r;
    }

    /**
     * 위치정보 동의까지 마친 가상 회원을 만든다.
     * 로그인 테스트를 하려고 비밀번호는 진짜 해시(PasswordHasher)로 저장한다.
     */
    private static long createMember(String key) throws SQLException {
        String sql = "INSERT INTO member (email, password_hash, nickname, gender, location_agreed_at, is_virtual) "
                   + "VALUES (?, ?, ?, 'M', NOW(), 1)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, key + "@test.com");
            ps.setString(2, PasswordHasher.encode(PASSWORD));
            ps.setString(3, key);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private static RideRequest.Status requestStatus(long requestId) throws SQLException {
        return requestDAO.findById(requestId).getStatus();
    }

    /** reporter 가 reported 를 신고한 건의 번호 (가장 최근 것) */
    private static long findReportId(long reported, long reporter) throws SQLException {
        for (Report r : reportDAO.findByReportedId(reported)) {
            if (r.getReporterId() == reporter) {
                return r.getReportId();
            }
        }
        throw new IllegalStateException("테스트 준비 실패: 신고를 찾지 못했습니다.");
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
}
