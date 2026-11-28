import java.util.*;
import java.util.stream.Collectors;

public class ScoreBasedMatcher {

    // --- 1. 데이터 모델 정의 ---
    public static class LatLng {
        public final double lat;
        public final double lng;

        public LatLng(double lat, double lng) {
            this.lat = lat;
            this.lng = lng;
        }

        @Override
        public String toString() {
            return String.format("(%.4f, %.4f)", lat, lng);
        }
    }

    public enum StopType {
        PICKUP, DROPOFF
    }

    public static class Waypoint {
        public final String userId;
        public final StopType type;
        public final String placeName;
        public final LatLng location;

        public Waypoint(String userId, StopType type, String placeName, LatLng location) {
            this.userId = userId;
            this.type = type;
            this.placeName = placeName;
            this.location = location;
        }

        @Override
        public String toString() {
            return "[" + type + "] " + userId + " (" + placeName + ")";
        }
    }

    public static class UserRequest {
        public final String userId;
        public final String originName;
        public final LatLng origin;
        public final String destinationName;
        public final LatLng destination;
        public final double maxDetourRatio;
        public final int passengerCount;
        public final long requestTimeMillis;
        public final double rating;        // 사용자 평점 (신규 유저 기본값 3.5)
        public final boolean isRestricted; // 신고 누적 이용 제한 여부

        public UserRequest(
                String userId,
                String originName,
                LatLng origin,
                String destinationName,
                LatLng destination,
                double maxDetourRatio,
                int passengerCount,
                long requestTimeMillis,
                double rating,
                boolean isRestricted
        ) {
            this.userId = userId;
            this.originName = originName;
            this.origin = origin;
            this.destinationName = destinationName;
            this.destination = destination;
            this.maxDetourRatio = maxDetourRatio;
            this.passengerCount = passengerCount;
            this.requestTimeMillis = requestTimeMillis;
            this.rating = (rating <= 0.0) ? DEFAULT_NEW_USER_RATING : rating;
            this.isRestricted = isRestricted;
        }

        // 간편 생성자 (기본 우회율 1.3, 신규 평점 3.5, 제한 없음)
        public UserRequest(
                String userId,
                String originName,
                LatLng origin,
                String destinationName,
                LatLng destination,
                int passengerCount
        ) {
            this(userId, originName, origin, destinationName, destination, 1.3, passengerCount, System.currentTimeMillis(), DEFAULT_NEW_USER_RATING, false);
        }
    }

    public static class MatchResult {
        public final String groupId;
        public final String sharedDestinationName;
        public final List<UserRequest> users;
        public final List<Waypoint> route;
        public final double totalDistanceKm;
        public final int totalFare;
        public final Map<String, Integer> fareSplit;
        public final Map<String, Double> personalDetourRatios;
        public final double totalScore;

        public MatchResult(
                String groupId,
                String sharedDestinationName,
                List<UserRequest> users,
                List<Waypoint> route,
                double totalDistanceKm,
                int totalFare,
                Map<String, Integer> fareSplit,
                Map<String, Double> personalDetourRatios,
                double totalScore
        ) {
            this.groupId = groupId;
            this.sharedDestinationName = sharedDestinationName;
            this.users = users;
            this.route = route;
            this.totalDistanceKm = totalDistanceKm;
            this.totalFare = totalFare;
            this.fareSplit = fareSplit;
            this.personalDetourRatios = personalDetourRatios;
            this.totalScore = totalScore;
        }
    }

    // --- 2. 상수 및 가중치 설정 ---
    private static final int MAX_TAXI_CAPACITY = 4;
    private static final double MAX_PICKUP_RADIUS_KM = 1.5;
    private static final double SAME_DESTINATION_TOLERANCE_KM = 0.05; // 동일 목적지 좌표 허용 오차 (50m 이내)
    private static final double ROAD_DETOUR_FACTOR = 1.25;

    // 4대 매칭 가중치 (합계 100.0)
    private static final double WEIGHT_OVERLAP = 40.0;
    private static final double WEIGHT_DETOUR = 30.0;
    private static final double WEIGHT_WAIT_TIME = 20.0;
    private static final double WEIGHT_RATING = 10.0;

    private static final double MAX_WAIT_SECONDS = 180.0;
    public static final double DEFAULT_NEW_USER_RATING = 3.5;

    private static class EvaluatedRoute {
        final List<Waypoint> waypoints;
        final double totalDistanceKm;
        final Map<String, Double> personalRideDistances;

        EvaluatedRoute(
                List<Waypoint> waypoints,
                double totalDistanceKm,
                Map<String, Double> personalRideDistances
        ) {
            this.waypoints = waypoints;
            this.totalDistanceKm = totalDistanceKm;
            this.personalRideDistances = personalRideDistances;
        }
    }

    // --- 3. 점수 기반 동일 목적지 매칭 메인 로직 ---
    public List<MatchResult> match(List<UserRequest> queue, long currentTimeMillis) {
        List<MatchResult> results = new ArrayList<>();

        // 신고 누적 제한자 제외 후 대기 시간이 긴 순서대로 정렬
        List<UserRequest> unmatched = queue.stream()
                .filter(u -> !u.isRestricted)
                .sorted(Comparator.comparingLong(u -> u.requestTimeMillis))
                .collect(Collectors.toList());

        while (unmatched.size() >= 2) {
            UserRequest anchor = unmatched.remove(0);
            List<UserRequest> group = new ArrayList<>();
            group.add(anchor);
            int currentPassengers = anchor.passengerCount;

            while (currentPassengers < MAX_TAXI_CAPACITY) {
                UserRequest bestCandidate = null;
                double bestGroupScore = -1.0;

                for (UserRequest candidate : unmatched) {
                    // 1) 정원 초과 검사
                    if (currentPassengers + candidate.passengerCount > MAX_TAXI_CAPACITY) {
                        continue;
                    }
                    // 2) 동일 목적지 필수 검사 (목적지가 같은 사람만 탑승)
                    if (!isSameDestination(anchor, candidate)) {
                        continue;
                    }
                    // 3) 출발지 픽업 반경 검사
                    if (calculateDistanceKm(anchor.origin, candidate.origin) > MAX_PICKUP_RADIUS_KM) {
                        continue;
                    }

                    List<UserRequest> tempGroup = new ArrayList<>(group);
                    tempGroup.add(candidate);
                    EvaluatedRoute evaluated = findBestPickupRouteToSameDest(tempGroup);

                    // 4) 개인별 허용 우회율 검사
                    if (!isDetourAcceptableForAll(tempGroup, evaluated)) {
                        continue;
                    }

                    // 5) 4대 가중치 매칭 점수 계산
                    double score = calculateGroupScore(tempGroup, evaluated, currentTimeMillis);
                    if (score > bestGroupScore) {
                        bestGroupScore = score;
                        bestCandidate = candidate;
                    }
                }

                if (bestCandidate != null) {
                    group.add(bestCandidate);
                    currentPassengers += bestCandidate.passengerCount;
                    unmatched.remove(bestCandidate);
                } else {
                    break;
                }
            }

            if (group.size() >= 2) {
                EvaluatedRoute finalRoute = findBestPickupRouteToSameDest(group);
                double roundedTotalDist = Math.round(finalRoute.totalDistanceKm * 100.0) / 100.0;
                int totalFare = estimateTaxiFare(finalRoute.totalDistanceKm);
                Map<String, Integer> fareSplit = calculateProportionalFare(group, totalFare);
                double finalScore = Math.round(calculateGroupScore(group, finalRoute, currentTimeMillis) * 10.0) / 10.0;

                Map<String, Double> detourRatios = new LinkedHashMap<>();
                for (UserRequest u : group) {
                    double soloDist = calculateDistanceKm(u.origin, u.destination);
                    double rideDist = finalRoute.personalRideDistances.get(u.userId);
                    double ratio = (soloDist > 0) ? Math.round((rideDist / soloDist) * 100.0) / 100.0 : 1.0;
                    detourRatios.put(u.userId, ratio);
                }

                results.add(new MatchResult(
                        UUID.randomUUID().toString().substring(0, 8),
                        anchor.destinationName,
                        group,
                        finalRoute.waypoints,
                        roundedTotalDist,
                        totalFare,
                        fareSplit,
                        detourRatios,
                        finalScore
                ));
            }
        }
        return results;
    }

    public List<MatchResult> match(List<UserRequest> queue) {
        return match(queue, System.currentTimeMillis());
    }

    // --- 4. 동일 목적지 판별 로직 ---
    private boolean isSameDestination(UserRequest u1, UserRequest u2) {
        if (u1.destinationName != null && u2.destinationName != null) {
            String d1 = u1.destinationName.trim().replaceAll("\\s+", "");
            String d2 = u2.destinationName.trim().replaceAll("\\s+", "");
            if (d1.equalsIgnoreCase(d2)) {
                return true;
            }
        }
        // 이름이 미세하게 다르더라도 좌표가 50m 이내 동일 지점이면 같은 목적지로 인정
        return calculateRawDistanceKm(u1.destination, u2.destination) <= SAME_DESTINATION_TOLERANCE_KM;
    }

    // --- 5. 4대 가중치 점수 산출 로직 ---
    public double calculateGroupScore(List<UserRequest> group, EvaluatedRoute route, long currentTimeMillis) {
        // 1) 경로 겹침 점수 (40점)
        double sumSoloDist = 0.0;
        for (UserRequest u : group) {
            sumSoloDist += calculateDistanceKm(u.origin, u.destination);
        }
        double overlapRatio = (sumSoloDist > 0)
                ? Math.max(0.0, Math.min(1.0, (sumSoloDist - route.totalDistanceKm) / sumSoloDist))
                : 0.0;
        double overlapScore = overlapRatio * WEIGHT_OVERLAP;

        // 2) 우회 효율 점수 (30점)
        double detourEfficiencySum = 0.0;
        for (UserRequest u : group) {
            double soloDist = calculateDistanceKm(u.origin, u.destination);
            double rideDist = route.personalRideDistances.get(u.userId);
            if (soloDist <= 0 || u.maxDetourRatio <= 1.0) {
                detourEfficiencySum += 1.0;
            } else {
                double actualRatio = rideDist / soloDist;
                double efficiency = (u.maxDetourRatio - actualRatio) / (u.maxDetourRatio - 1.0);
                detourEfficiencySum += Math.max(0.0, Math.min(1.0, efficiency));
            }
        }
        double detourScore = (detourEfficiencySum / group.size()) * WEIGHT_DETOUR;

        // 3) 대기 시간 점수 (20점)
        double waitNormSum = 0.0;
        for (UserRequest u : group) {
            double waitedSec = Math.max(0.0, (currentTimeMillis - u.requestTimeMillis) / 1000.0);
            double norm = Math.min(1.2, waitedSec / MAX_WAIT_SECONDS);
            waitNormSum += norm;
        }
        double waitScore = Math.min(WEIGHT_WAIT_TIME, (waitNormSum / group.size()) * WEIGHT_WAIT_TIME);

        // 4) 사용자 평점 점수 (10점, 신규 유저 기본값 3.5점)
        double ratingNormSum = 0.0;
        for (UserRequest u : group) {
            ratingNormSum += Math.max(0.0, Math.min(5.0, u.rating)) / 5.0;
        }
        double ratingScore = (ratingNormSum / group.size()) * WEIGHT_RATING;

        return overlapScore + detourScore + waitScore + ratingScore;
    }

    // --- 6. 동일 목적지 기준 최적 픽업 순서(Waypoint) 계산 ---
    private EvaluatedRoute findBestPickupRouteToSameDest(List<UserRequest> users) {
        List<Waypoint> pickups = users.stream()
                .map(u -> new Waypoint(u.userId, StopType.PICKUP, u.originName, u.origin))
                .collect(Collectors.toList());

        UserRequest firstUser = users.get(0);
        Waypoint sharedDropoff = new Waypoint(
                "ALL",
                StopType.DROPOFF,
                firstUser.destinationName,
                firstUser.destination
        );

        List<List<Waypoint>> pickupPerms = generatePermutations(pickups);

        List<Waypoint> bestWaypoints = Collections.emptyList();
        double minObjectiveCost = Double.MAX_VALUE;
        double bestTotalDist = 0.0;
        Map<String, Double> bestPersonalDists = Collections.emptyMap();

        for (List<Waypoint> pOrder : pickupPerms) {
            List<Waypoint> fullPath = new ArrayList<>(pOrder);
            fullPath.add(sharedDropoff);

            double dist = 0.0;
            for (int i = 0; i < fullPath.size() - 1; i++) {
                dist += calculateDistanceKm(fullPath.get(i).location, fullPath.get(i + 1).location);
            }

            Map<String, Double> personalDists = calculatePersonalRideDistancesSameDest(users, fullPath);
            double variancePenalty = calculateDetourVariancePenalty(users, personalDists);

            double objectiveCost = dist + (variancePenalty * 0.5);
            if (objectiveCost < minObjectiveCost) {
                minObjectiveCost = objectiveCost;
                bestTotalDist = dist;
                bestWaypoints = fullPath;
                bestPersonalDists = personalDists;
            }
        }

        return new EvaluatedRoute(bestWaypoints, bestTotalDist, bestPersonalDists);
    }

    private Map<String, Double> calculatePersonalRideDistancesSameDest(List<UserRequest> users, List<Waypoint> path) {
        Map<String, Double> rideDistances = new HashMap<>();

        for (UserRequest user : users) {
            String uid = user.userId;
            boolean onBoard = false;
            double dist = 0.0;

            for (int i = 0; i < path.size() - 1; i++) {
                Waypoint curr = path.get(i);
                Waypoint next = path.get(i + 1);

                if (curr.type == StopType.PICKUP && curr.userId.equals(uid)) {
                    onBoard = true;
                }
                if (onBoard) {
                    dist += calculateDistanceKm(curr.location, next.location);
                }
            }
            rideDistances.put(uid, dist);
        }
        return rideDistances;
    }

    private double calculateDetourVariancePenalty(List<UserRequest> users, Map<String, Double> personalDists) {
        List<Double> ratios = new ArrayList<>();
        double sum = 0.0;
        for (UserRequest u : users) {
            double solo = calculateDistanceKm(u.origin, u.destination);
            double ratio = (solo > 0) ? (personalDists.get(u.userId) / solo) : 1.0;
            ratios.add(ratio);
            sum += ratio;
        }
        double mean = sum / ratios.size();
        double variance = 0.0;
        for (double r : ratios) {
            variance += Math.pow(r - mean, 2);
        }
        return variance / ratios.size();
    }

    private boolean isDetourAcceptableForAll(List<UserRequest> users, EvaluatedRoute route) {
        for (UserRequest user : users) {
            double soloDist = calculateDistanceKm(user.origin, user.destination);
            double actualRideDist = route.personalRideDistances.get(user.userId);
            if (actualRideDist > soloDist * user.maxDetourRatio) {
                return false;
            }
        }
        return true;
    }

    public Map<String, Integer> calculateProportionalFare(List<UserRequest> users, int totalFare) {
        Map<String, Double> soloDistances = new HashMap<>();
        double totalSoloDist = 0.0;

        for (UserRequest u : users) {
            double d = calculateDistanceKm(u.origin, u.destination);
            soloDistances.put(u.userId, d);
            totalSoloDist += d;
        }

        Map<String, Integer> fareSplit = new LinkedHashMap<>();
        int allocated = 0;

        for (int i = 0; i < users.size(); i++) {
            UserRequest u = users.get(i);
            if (i == users.size() - 1) {
                fareSplit.put(u.userId, totalFare - allocated);
            } else {
                double ratio = (totalSoloDist > 0) ? (soloDistances.get(u.userId) / totalSoloDist) : (1.0 / users.size());
                int share = (int) (Math.round((totalFare * ratio) / 10.0) * 10);
                fareSplit.put(u.userId, share);
                allocated += share;
            }
        }
        return fareSplit;
    }

    public int estimateTaxiFare(double distanceKm) {
        final int BASE_FARE = 4800;
        final double BASE_DIST_KM = 1.6;
        if (distanceKm <= BASE_DIST_KM) {
            return BASE_FARE;
        }
        double extraMeters = (distanceKm - BASE_DIST_KM) * 1000.0;
        int extraFare = (int) Math.ceil(extraMeters / 131.0) * 100;
        return BASE_FARE + extraFare;
    }

    private double calculateRawDistanceKm(LatLng c1, LatLng c2) {
        final double EARTH_RADIUS_KM = 6371.0;
        double dLat = Math.toRadians(c2.lat - c1.lat);
        double dLng = Math.toRadians(c2.lng - c1.lng);

        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(c1.lat)) * Math.cos(Math.toRadians(c2.lat))
                * Math.pow(Math.sin(dLng / 2), 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c;
    }

    public double calculateDistanceKm(LatLng c1, LatLng c2) {
        return calculateRawDistanceKm(c1, c2) * ROAD_DETOUR_FACTOR;
    }

    private <T> List<List<T>> generatePermutations(List<T> items) {
        List<List<T>> result = new ArrayList<>();
        permute(new ArrayList<>(items), 0, result);
        return result;
    }

    private <T> void permute(List<T> arr, int k, List<List<T>> result) {
        if (k == arr.size()) {
            result.add(new ArrayList<>(arr));
            return;
        }
        for (int i = k; i < arr.size(); i++) {
            Collections.swap(arr, i, k);
            permute(arr, k + 1, result);
            Collections.swap(arr, k, i);
        }
    }

    // --- 7. 실행 테스트용 main 메서드 ---
    public static void main(String[] args) {
        ScoreBasedMatcher matcher = new ScoreBasedMatcher();
        long now = System.currentTimeMillis();

        LatLng destStation = new LatLng(36.7945, 127.1045); // 천안아산역
        LatLng destTerminal = new LatLng(36.8195, 127.1560); // 천안종합터미널

        List<UserRequest> queue = Arrays.asList(
                new UserRequest("userA", "순천향대 후문", new LatLng(36.7715, 127.0950), "천안아산역", destStation, 1.3, 1, now - 150_000L, 4.8, false),
                new UserRequest("userB", "신창역 앞", new LatLng(36.7690, 127.0980), "천안아산역", destStation, 1.3, 1, now - 60_000L, 3.5, false),
                new UserRequest("userC", "온양온천역", new LatLng(36.7805, 127.0030), "천안종합터미널", destTerminal, 1.3, 1, now - 120_000L, 4.5, false),
                new UserRequest("userD", "아산배방역", new LatLng(36.7775, 127.0530), "천안아산역", destStation, 1.3, 1, now - 180_000L, 4.9, false)
        );

        List<MatchResult> results = matcher.match(queue, now);

        System.out.println("=== 동일 목적지 점수 기반 매칭 결과 ===");
        for (MatchResult res : results) {
            System.out.println("그룹 ID: " + res.groupId + " | 목적지: " + res.sharedDestinationName);
            System.out.println("매칭 점수: " + res.totalScore + "점 / 100점");
            System.out.println("경유 순서: " + res.route);
            System.out.println("총 거리: " + res.totalDistanceKm + "km | 총 요금: " + res.totalFare + "원");
            System.out.println("개인별 요금 분담: " + res.fareSplit);
            System.out.println("개인별 우회율: " + res.personalDetourRatios);
            System.out.println("---------------------------------------");
        }
    }
}
