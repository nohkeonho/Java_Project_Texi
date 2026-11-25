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
 * 합승 요청·매칭 저장이 잘 되는지 확인하는 테스트용 main.
 * (ride_migration.sql 을 먼저 실행해 두어야 한다)
 *
 * 가상 회원 2명을 만들고 -> 각자 요청 등록 -> 후보 검색 -> 매칭 저장 -> 잘못된 입력이 막히는지 확인한다.
 * 실행할 때마다 새 가상 회원(is_virtual = 1)을 만들므로 여러 번 돌려도 된다.
 */
public class RideSaveTest {

    private static final RideRequestDAO requestDAO = new RideRequestDAO();
    private static final RideMatchDAO matchDAO = new RideMatchDAO();

    public static void main(String[] args) throws SQLException {
        long stamp = System.currentTimeMillis() % 1_000_000_000L;   // 이메일·닉네임이 겹치지 않게
        long memberA = createVirtualMember("a" + stamp, "M");
        long memberB = createVirtualMember("b" + stamp, "M");

        LocalDateTime base = LocalDateTime.now().plusMinutes(30).withSecond(0).withNano(0);

        // 1) 요청 등록
        RideRequest reqA = newRequest(memberA, "선문대 정문 앞", 36.7990, 127.0750, "천안역 동부광장 앞", 36.8095, 127.1462,
                1, base, base.plusMinutes(20), 7600);
        RideRequest reqB = newRequest(memberB, "선문대 기숙사 앞", 36.8005, 127.0770, "천안역 서부광장 앞", 36.8085, 127.1440,
                2, base.plusMinutes(10), base.plusMinutes(40), 7300);
        requestDAO.insert(reqA);
        requestDAO.insert(reqB);
        System.out.println("[요청 저장] " + reqA);
        System.out.println("[요청 저장] " + reqB);

        // 2) 같은 시간대 중복 요청은 막혀야 함
        expectFail("중복 요청", () -> requestDAO.insert(newRequest(memberA, "선문대 정문 앞", 36.7990, 127.0750,
                "천안역", 36.8095, 127.1462, 1, base, base.plusMinutes(10), null)));
        // 3) 인원 4명은 막혀야 함
        expectFail("인원 초과 요청", () -> requestDAO.insert(newRequest(memberA, "A", 36.79, 127.07,
                "B", 36.80, 127.14, 4, base.plusHours(3), base.plusHours(4), null)));

        // 4) 후보 검색
        List<RideRequest> candidates = requestDAO.findCandidates(reqA.getRequestId());
        System.out.println("[후보] A 의 후보 수 = " + candidates.size() + " -> " + candidates);

        // 5) 분담금 합이 총 요금과 다르면 막혀야 함
        RideMatch wrong = newMatch(85.5, 8200, 9000);
        expectFail("분담금 합계 불일치", () -> matchDAO.createMatch(wrong, members(reqA, reqB, 3000, 5000)));

        // 6) 정상 매칭 저장 (3200 + 5800 = 9000)
        RideMatch match = newMatch(85.5, 8200, 9000);
        List<MatchMember> mms = members(reqA, reqB, 3200, 5800);
        long matchId = matchDAO.createMatch(match, mms);
        System.out.println("[매칭 저장] " + matchDAO.findById(matchId));
        for (MatchMember mm : matchDAO.findMembersByMatchId(matchId)) {
            System.out.println("    " + mm + ", 점수=" + mm.getMatchScore());
        }
        System.out.println("[요청 상태] A=" + requestDAO.findById(reqA.getRequestId()).getStatus()
                + ", B=" + requestDAO.findById(reqB.getRequestId()).getStatus());
        System.out.println("[요청으로 매칭 찾기] " + matchDAO.findLatestByRequestId(reqB.getRequestId()));

        // 7) 이미 매칭된 요청으로 또 매칭하면 막혀야 함 (그리고 롤백되어 ride_match 가 늘지 않아야 함)
        int before = countMatches();
        expectFail("이미 매칭된 요청 재매칭", () -> matchDAO.createMatch(newMatch(80.0, 8200, 9000),
                members(reqA, reqB, 3200, 5800)));
        System.out.println("[롤백 확인] 매칭 수 변화 없음 = " + (before == countMatches()));
    }

    // ------------------------------------------------------------------
    // 테스트용 도우미
    // ------------------------------------------------------------------

    /** 실패해야 하는 동작을 실행해서, 정말 예외가 나는지 확인한다. */
    private static void expectFail(String label, ThrowingRunnable action) {
        try {
            action.run();
            System.out.println("[실패!] " + label + " 이(가) 막히지 않았습니다.");
        } catch (Exception e) {
            System.out.println("[정상 차단] " + label + " -> " + e.getMessage());
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static RideRequest newRequest(long memberId, String origin, double oLat, double oLng,
                                          String dest, double dLat, double dLng, int people,
                                          LocalDateTime from, LocalDateTime until, Integer estFare) {
        RideRequest r = new RideRequest();
        r.setMemberId(memberId);
        r.setOriginName(origin);
        r.setOriginLat(oLat);
        r.setOriginLng(oLng);
        r.setDestName(dest);
        r.setDestLat(dLat);
        r.setDestLng(dLng);
        r.setPassengerCount(people);
        r.setDepartFrom(from);
        r.setDepartUntil(until);
        r.setEstFare(estFare);
        return r;
    }

    private static RideMatch newMatch(double score, int distance, int fare) {
        RideMatch m = new RideMatch();
        m.setMatchScore(score);
        m.setTotalDistanceM(distance);
        m.setTotalFare(fare);
        return m;
    }

    /** A 가 먼저 타고(1) 먼저 내리는(1) 2인 매칭 참여자 목록 */
    private static List<MatchMember> members(RideRequest a, RideRequest b, int fareA, int fareB) {
        List<MatchMember> list = new ArrayList<>();
        list.add(member(a.getRequestId(), 1, 1, 7000, 0, 88.0, fareA));
        list.add(member(b.getRequestId(), 2, 2, 7900, 600, 83.0, fareB));
        return list;
    }

    private static MatchMember member(long requestId, int pickup, int dropoff, int dist, int detour,
                                      double score, int fare) {
        MatchMember mm = new MatchMember();
        mm.setRequestId(requestId);
        mm.setPickupOrder(pickup);
        mm.setDropoffOrder(dropoff);
        mm.setOwnDistanceM(dist);
        mm.setDetourM(detour);
        mm.setMatchScore(score);
        mm.setFareShare(fare);
        return mm;
    }

    /** 위치정보 동의까지 마친 가상 회원을 만든다. */
    private static long createVirtualMember(String name, String gender) throws SQLException {
        String sql = "INSERT INTO member (email, password_hash, nickname, gender, location_agreed_at, is_virtual) "
                   + "VALUES (?, 'test-hash', ?, ?, NOW(), 1)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name + "@test.com");
            ps.setString(2, name);
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
}
