import java.util.*;
import java.util.stream.Collectors;

public class SameDestinationMatcher {

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
        public final LatLng location;

        public Waypoint(String userId, StopType type, LatLng location) {
            this.userId = userId;
            this.type = type;
            this.location = location;
        }
    }

    public static class UserRequest {
        public final String userId;
        public final LatLng origin;
        public final LatLng destination;
        public final double maxDetourRatio;
        public final int passengerCount;

        public UserRequest(String userId, LatLng origin, LatLng destination, double maxDetourRatio, int passengerCount) {
            this.userId = userId;
            this.origin = origin;
            this.destination = destination;
            this.maxDetourRatio = maxDetourRatio;
            this.passengerCount = passengerCount;
        }

        public UserRequest(String userId, LatLng origin, LatLng destination, int passengerCount) {
            this(userId, origin, destination, 1.3, passengerCount);
        }
    }

    public static class MatchResult {
        public final String groupId;
        public final List<UserRequest> users;
        public final List<Waypoint> route;
        public final double totalDistanceKm;
        public final int totalFare;
        public final Map<String, Integer> fareSplit;
        public final Map<String, Double> personalDetourRatios;

        public MatchResult(
                String groupId,
                List<UserRequest> users,
                List<Waypoint> route,
                double totalDistanceKm,
                int totalFare,
                Map<String, Integer> fareSplit,
                Map<String, Double> personalDetourRatios
        ) {
            this.groupId = groupId;
            this.users = users;
            this.route = route;
            this.totalDistanceKm = totalDistanceKm;
            this.totalFare = totalFare;
            this.fareSplit = fareSplit;
            this.personalDetourRatios = personalDetourRatios;
        }
    }

    // --- 2. 매칭 알고리즘 로직 ---
    private static final int MAX_TAXI_CAPACITY = 4;
    private static final double MAX_PICKUP_RADIUS_KM = 1.5;
    private static final double MAX_DESTINATION_RADIUS_KM = 0.8;
    private static final double ROAD_DETOUR_FACTOR = 1.25;

    private static class EvaluatedRoute {
        final List<Waypoint> waypoints;
        final double totalDistanceKm;
        final Map<String, Double> personalRideDistances;

        EvaluatedRoute(List<Waypoint> waypoints, double totalDistanceKm, Map<String, Double> personalRideDistances) {
            this.waypoints = waypoints;
            this.totalDistanceKm = totalDistanceKm;
            this.personalRideDistances = personalRideDistances;
        }
    }

    public List<MatchResult> match(List<UserRequest> queue) {
        List<MatchResult> results = new ArrayList<>();
        List<UserRequest> unmatched = new ArrayList<>(queue);

        while (unmatched.size() >= 2) {
            UserRequest anchor = unmatched.remove(0);
            List<UserRequest> group = new ArrayList<>();
            group.add(anchor);
            int currentPassengers = anchor.passengerCount;

            List<UserRequest> matchedCandidates = new ArrayList<>();

            for (UserRequest candidate : unmatched) {
                if (currentPassengers + candidate.passengerCount > MAX_TAXI_CAPACITY) {
                    continue;
                }
                if (calculateDistanceKm(anchor.origin, candidate.origin) > MAX_PICKUP_RADIUS_KM) {
                    continue;
                }
                if (!isDestinationCloseToGroup(group, candidate)) {
                    continue;
                }

                List<UserRequest> tempGroup = new ArrayList<>(group);
                tempGroup.add(candidate);
                EvaluatedRoute evaluated = findBestPickupDropoffRoute(tempGroup);

                if (isDetourAcceptableForAll(tempGroup, evaluated)) {
                    group.add(candidate);
                    currentPassengers += candidate.passengerCount;
                    matchedCandidates.add(candidate);
                }
            }

            if (group.size() >= 2) {
                unmatched.removeAll(matchedCandidates);

                EvaluatedRoute finalRoute = findBestPickupDropoffRoute(group);
                double roundedTotalDist = Math.round(finalRoute.totalDistanceKm * 100.0) / 100.0;
                int totalFare = estimateTaxiFare(finalRoute.totalDistanceKm);
                Map<String, Integer> fareSplit = calculateProportionalFare(group, totalFare);

                Map<String, Double> detourRatios = new LinkedHashMap<>();
                for (UserRequest u : group) {
                    double soloDist = calculateDistanceKm(u.origin, u.destination);
                    double rideDist = finalRoute.personalRideDistances.get(u.userId);
                    double ratio = Math.round((rideDist / soloDist) * 100.0) / 100.0;
                    detourRatios.put(u.userId, ratio);
                }

                results.add(new MatchResult(
                        UUID.randomUUID().toString().substring(0, 8),
                        group,
                        finalRoute.waypoints,
                        roundedTotalDist,
                        totalFare,
                        fareSplit,
                        detourRatios
                ));
            }
        }
        return results;
    }

    private boolean isDestinationCloseToGroup(List<UserRequest> group, UserRequest candidate) {
        for (UserRequest member : group) {
            double destDist = calculateDistanceKm(member.destination, candidate.destination);
            if (destDist > MAX_DESTINATION_RADIUS_KM) {
                return false;
            }
        }
        return true;
    }

    private EvaluatedRoute findBestPickupDropoffRoute(List<UserRequest> users) {
        List<Waypoint> pickups = users.stream()
                .map(u -> new Waypoint(u.userId, StopType.PICKUP, u.origin))
                .collect(Collectors.toList());
        List<Waypoint> dropoffs = users.stream()
                .map(u -> new Waypoint(u.userId, StopType.DROPOFF, u.destination))
                .collect(Collectors.toList());

        List<List<Waypoint>> pickupPerms = generatePermutations(pickups);
        List<List<Waypoint>> dropoffPerms = generatePermutations(dropoffs);

        List<Waypoint> bestWaypoints = Collections.emptyList();
        double minTotalDist = Double.MAX_VALUE;

        for (List<Waypoint> pOrder : pickupPerms) {
            for (List<Waypoint> dOrder : dropoffPerms) {
                List<Waypoint> fullPath = new ArrayList<>(pOrder);
                fullPath.addAll(dOrder);

                double dist = 0.0;
                for (int i = 0; i < fullPath.size() - 1; i++) {
                    dist += calculateDistanceKm(fullPath.get(i).location, fullPath.get(i + 1).location);
                }

                if (dist < minTotalDist) {
                    minTotalDist = dist;
                    bestWaypoints = fullPath;
                }
            }
        }

        Map<String, Double> personalRideDistances = calculatePersonalRideDistances(users, bestWaypoints);
        return new EvaluatedRoute(bestWaypoints, minTotalDist, personalRideDistances);
    }

    private Map<String, Double> calculatePersonalRideDistances(List<UserRequest> users, List<Waypoint> path) {
        Map<String, Double> rideDistances = new HashMap<>();

        for (UserRequest user : users) {
            String uid = user.userId;
            boolean onBoard = false;
            double dist = 0.0;

            for (int i = 0; i < path.size() - 1; i++) {
                Waypoint curr = path.get(i);
                Waypoint next = path.get(i + 1);

                if (curr.userId.equals(uid) && curr.type == StopType.PICKUP) {
                    onBoard = true;
                }
                if (onBoard) {
                    dist += calculateDistanceKm(curr.location, next.location);
                }
                if (next.userId.equals(uid) && next.type == StopType.DROPOFF) {
                    break;
                }
            }
            rideDistances.put(uid, dist);
        }
        return rideDistances;
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
                double ratio = soloDistances.get(u.userId) / totalSoloDist;
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

    public double calculateDistanceKm(LatLng c1, LatLng c2) {
        final double EARTH_RADIUS_KM = 6371.0;
        double dLat = Math.toRadians(c2.lat - c1.lat);
        double dLng = Math.toRadians(c2.lng - c1.lng);

        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(c1.lat)) * Math.cos(Math.toRadians(c2.lat))
                * Math.pow(Math.sin(dLng / 2), 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c * ROAD_DETOUR_FACTOR;
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
}
