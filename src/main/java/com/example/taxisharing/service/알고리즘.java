import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class CarpoolAlgorithm {

    // 1주차 합의 기준 상수
    private static final int MAX_PASSENGERS = 4;               // 최대 탑승 인원
    private static final long TIME_WINDOW_MINUTES = 15;        // 출발 시간 허용 오차 (±15분)
    private static final double ORIGIN_RADIUS_METERS = 500.0;  // 출발지 허용 반경 (500m)
    private static final double DEST_RADIUS_METERS = 1000.0;   // 도착지 허용 반경 (1km)

    /**
     * [1주차 기능 1] 두 좌표 간 거리 계산 (단위: 미터)
     */
    public static double getDistanceMeters(double lat1, double lng1, double lat2, double lng2) {
        final int EARTH_RADIUS = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS * c;
    }

    /**
     * [1주차 기능 2] 경로 · 거리 · 예상 시간 · 예상 요금 조회 (API 연동 전 초안)
     * 반환값 배열: [0] 예상거리(m), [1] 예상소요시간(초), [2] 예상택시요금(원)
     */
    public static int[] estimateRouteAndFare(double startLat, double startLng, double endLat, double endLng) {
        // 1. 도로 예상 거리 (직선거리 * 굴곡도 1.3)
        int distanceMeters = (int) (getDistanceMeters(startLat, startLng, endLat, endLng) * 1.3);

        // 2. 예상 소요 시간 (도심 평균 시속 30km/h = 약 8.33m/s 기준)
        int durationSeconds = (int) (distanceMeters / 8.33);

        // 3. 예상 택시 요금 (기본요금 4,800원 + 1.6km 초과 시 131m당 100원)
        int baseFare = 4800;
        int extraDistance = Math.max(0, distanceMeters - 1600);
        int totalFare = baseFare + ((extraDistance / 131) * 100);

        return new int[]{distanceMeters, durationSeconds, totalFare};
    }

    /**
     * [1주차 기능 3] 요금 분담 계산 초안 (N분의 1 균등 분할, 100원 단위 올림)
     */
    public static int calculateSplitFare(int totalFare, int totalPassengers) {
        double perPerson = (double) totalFare / totalPassengers;
        return (int) (Math.ceil(perPerson / 100.0) * 100);
    }

    /**
     * [1주차 기능 4] 규칙 기반 매칭 초안 (반경 + 시간창 + 인원 제한)
     * 대기열(waitingList)에서 내 요청(myRequest)과 같이 탈 수 있는 요청 ID 목록을 뽑아 반환
     * 
     * 각 요청 배열 구조: [0]요청ID, [1]출발위도, [2]출발경도, [3]도착위도, [4]도착경도, [5]인원수
     * 시간 리스트(waitingTimes)는 waitingList와 같은 순서로 출발시간 전달
     */
    public static List<Long> findMatchGroup(
            long myId, double myStartLat, double myStartLng, double myEndLat, double myEndLng,
            LocalDateTime myTime, int myCount,
            List<double[]> waitingList, List<LocalDateTime> waitingTimes
    ) {
        List<Long> matchedIds = new ArrayList<>();
        matchedIds.add(myId); // 나 자신 포함
        int currentPassengers = myCount;

        for (int i = 0; i < waitingList.size(); i++) {
            double[] candidate = waitingList.get(i);
            long candidateId = (long) candidate[0];
            LocalDateTime candidateTime = waitingTimes.get(i);
            int candidateCount = (int) candidate[5];

            // 자기 자신은 건너뜀
            if (candidateId == myId) continue;

            // 조건 1: 최대 인원(4명) 초과 여부
            if (currentPassengers + candidateCount > MAX_PASSENGERS) continue;

            // 조건 2: 시간창(Time Window) 체크 (±15분 이내)
            long timeDiff = Math.abs(Duration.between(myTime, candidateTime).toMinutes());
            if (timeDiff > TIME_WINDOW_MINUTES) continue;

            // 조건 3: 출발지 반경(500m) & 도착지 반경(1km) 체크
            double originDist = getDistanceMeters(myStartLat, myStartLng, candidate[1], candidate[2]);
            double destDist = getDistanceMeters(myEndLat, myEndLng, candidate[3], candidate[4]);

            if (originDist <= ORIGIN_RADIUS_METERS && destDist <= DEST_RADIUS_METERS) {
                matchedIds.add(candidateId);
                currentPassengers += candidateCount;
            }

            // 4명 꽉 차면 매칭 종료
            if (currentPassengers == MAX_PASSENGERS) break;
        }

        return matchedIds;
    }
}
