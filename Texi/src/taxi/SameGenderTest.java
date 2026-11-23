package taxi;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import taxi.dao.RideMatchDAO;
import taxi.dao.RideRequestDAO;
import taxi.db.DBConnection;
import taxi.model.MatchMember;
import taxi.model.RideMatch;
import taxi.model.RideRequest;

/**
 * "동성 합승만 허용" 규칙이 서버 B 에서 지켜지는지 확인하는 테스트용 main.
 *
 *  1) 후보 검색(findCandidates) : 다른 성별의 요청은 후보에 나오지 않아야 한다.
 *  2) 매칭 저장(createMatch)    : 남녀를 묶어서 저장하려고 하면 막히고, 아무것도 저장되지 않아야 한다(롤백).
 *  3) 같은 성별끼리는 정상 저장되어야 한다.
 *
 * 실행할 때마다 가상 회원(is_virtual = 1) 3명(남 2, 여 1)을 새로 만든다.
 * 마지막 줄이 "결과: 통과 N개 / 실패 0개" 면 정상이다.
 */
public class SameGenderTest {

    private static final RideRequestDAO requestDAO = new RideRequestDAO();
    private static final RideMatchDAO matchDAO = new RideMatchDAO();

    private static int passCount = 0;
    private static int failCount = 0;

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
        long stamp = System.currentTimeMillis() % 1_000_000_000L;   // 이메일·닉네임 중복 방지
        long male1 = createMember("sg" + stamp + "m1", "M");
        long male2 = createMember("sg" + stamp + "m2", "M");
        long female = createMember("sg" + stamp + "f", "F");

        // 세 사람 모두 같은 시간, 같은 경로로 요청 -> 성별만 다르다
        LocalDateTime from = LocalDateTime.now().plusMinutes(20).withSecond(0).withNano(0);
        long reqM1 = requestDAO.insert(newRequest(male1, from));
        long reqM2 = requestDAO.insert(newRequest(male2, from));
        long reqF = requestDAO.insert(newRequest(female, from));

        // ---------- 1. 후보 검색 ----------
        List<Long> candidatesOfM1 = requestIds(requestDAO.findCandidates(reqM1));
        check("남성 요청의 후보에 같은 남성 요청이 있음", candidatesOfM1.contains(reqM2));
        check("남성 요청의 후보에 여성 요청은 없음", !candidatesOfM1.contains(reqF));
        List<Long> candidatesOfF = requestIds(requestDAO.findCandidates(reqF));
        check("여성 요청의 후보에 남성 요청은 없음",
                !candidatesOfF.contains(reqM1) && !candidatesOfF.contains(reqM2));

        // ---------- 2. 남녀를 묶은 매칭 저장은 막혀야 함 ----------
        int before = countMatches();
        try {
            matchDAO.createMatch(newMatch(), members(reqM1, reqF));
            check("남녀 매칭 저장 차단", false);
        } catch (IllegalStateException e) {
            check("남녀 매칭 저장 차단 (" + e.getMessage() + ")", e.getMessage().contains("동성"));
        }
        check("차단된 뒤 매칭이 하나도 저장되지 않음 (롤백)", before == countMatches());
        check("차단된 뒤 요청은 그대로 대기 상태",
                requestDAO.findById(reqM1).getStatus() == RideRequest.Status.WAITING
             && requestDAO.findById(reqF).getStatus() == RideRequest.Status.WAITING);

        // ---------- 3. 같은 성별끼리는 정상 저장 ----------
        long matchId = matchDAO.createMatch(newMatch(), members(reqM1, reqM2));
        check("남성끼리 매칭 저장 성공 (match_id=" + matchId + ")", matchDAO.findById(matchId) != null);
    }

    // =================================================================
    // 테스트 데이터를 만드는 도우미 메서드
    // =================================================================

    private static RideRequest newRequest(long memberId, LocalDateTime from) {
        RideRequest r = new RideRequest();
        r.setMemberId(memberId);
        r.setOriginName("선문대학교 정문 앞");
        r.setOriginLat(36.7990);
        r.setOriginLng(127.0750);
        r.setDestName("천안종합버스터미널 앞");
        r.setDestLat(36.8197);
        r.setDestLng(127.1563);
        r.setDepartFrom(from);
        r.setDepartUntil(from.plusMinutes(30));
        return r;
    }

    /** 2인 매칭 (총 9000원 = 4500 + 4500) */
    private static RideMatch newMatch() {
        RideMatch m = new RideMatch();
        m.setTotalDistanceM(9400);
        m.setTotalFare(9000);
        return m;
    }

    private static List<MatchMember> members(long reqA, long reqB) {
        List<MatchMember> list = new ArrayList<>();
        list.add(newMatchMember(reqA, 1));
        list.add(newMatchMember(reqB, 2));
        return list;
    }

    private static MatchMember newMatchMember(long requestId, int order) {
        MatchMember mm = new MatchMember();
        mm.setRequestId(requestId);
        mm.setPickupOrder(order);
        mm.setDropoffOrder(order);
        mm.setOwnDistanceM(9000);
        mm.setDetourM(0);
        mm.setFareShare(4500);
        return mm;
    }

    private static List<Long> requestIds(List<RideRequest> requests) {
        List<Long> ids = new ArrayList<>();
        for (RideRequest r : requests) {
            ids.add(r.getRequestId());
        }
        return ids;
    }

    /** 위치정보 동의까지 마친 가상 회원을 만든다. */
    private static long createMember(String key, String gender) throws SQLException {
        String sql = "INSERT INTO member (email, password_hash, nickname, gender, location_agreed_at, is_virtual) "
                   + "VALUES (?, 'test-hash', ?, ?, NOW(), 1)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, key + "@test.com");
            ps.setString(2, key);
            ps.setString(3, gender);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private static int countMatches() throws SQLException {
        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM ride_match")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static void check(String name, boolean ok) {
        System.out.println((ok ? "[통과] " : "[실패] ") + name);
        if (ok) {
            passCount++;
        } else {
            failCount++;
        }
    }
}
