package com.example.taxisharing.service;

import com.example.taxisharing.algorithm.CarpoolAlgorithm;
import com.example.taxisharing.dto.MatchStatus;
import com.example.taxisharing.dto.RideRequestDto;
import com.example.taxisharing.dto.RideResponseDto;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 메모리 대기열 + 매칭 확정 + 상태 관리.
 * 모든 상태 변경 메서드는 synchronized (단일 인스턴스 기준 동시성 보호).
 * 결과는 사용자별 토픽 /sub/match/{userId} 로 푸시합니다.
 */
@Service
public class RideQueueService {

    /** 대기 최대 시간 */
    private static final long MAX_WAIT_MINUTES = 10;
    /** 출발 시간 허용 오차(분). 알고리즘 담당의 TIME_WINDOW_MINUTES와 값을 맞출 것 */
    private static final long TIME_WINDOW_MINUTES = 15;

    private final AtomicLong requestIdGen = new AtomicLong(1L);
    private final AtomicLong groupIdGen = new AtomicLong(1L);

    private final Map<Long, RideRequestDto> waiting = new LinkedHashMap<>();
    private final Map<String, Long> waitingByUser = new HashMap<>();   // userId -> requestId
    private final Map<Long, RideGroup> groups = new HashMap<>();
    private final Map<String, Long> groupByUser = new HashMap<>();     // userId -> groupId

    private final SimpMessagingTemplate messaging;

    public RideQueueService(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    // ---------- 요청 등록 / 매칭 ----------

    public synchronized void processRideRequest(RideRequestDto request) {
        if (request == null) return;

        String error = request.validationError();
        if (error != null) {
            push(request.getUserId(), RideResponseDto.of(MatchStatus.ERROR, error, request.getUserId(), null));
            return;
        }

        String userId = request.getUserId();
        if (waitingByUser.containsKey(userId)) {
            push(userId, RideResponseDto.of(MatchStatus.ERROR, "이미 대기 중인 요청이 있습니다.", userId,
                    waitingByUser.get(userId)));
            return;
        }
        if (groupByUser.containsKey(userId)) {
            push(userId, RideResponseDto.of(MatchStatus.ERROR, "이미 매칭되어 진행 중인 합승이 있습니다.", userId, null));
            return;
        }

        // ID는 서버에서만 발급 (클라이언트 값 무시)
        long reqId = requestIdGen.getAndIncrement();
        request.setRequestId(reqId);
        request.setCreatedAt(LocalDateTime.now());
        if (request.getDepartureTime() == null) {
            request.setDepartureTime(LocalDateTime.now());
        }

        waiting.put(reqId, request);
        waitingByUser.put(userId, reqId);

        // TODO(3주차): [필요 모듈] 점수 기반 매칭 모듈 (알고리즘 담당)
        //  - 역할: 새 요청과 대기열을 받아 함께 탈 그룹을 고름
        //          (경로 겹침, 출발 시간, 우회 거리 가중치로 점수 계산)
        //  - 입력: 새 요청 + 현재 대기열 / 출력: 매칭된 요청 ID 목록(본인 포함)
        //  - 지금은 규칙 기반(CarpoolAlgorithm.findMatchGroup)을 임시로 호출함.
        //    점수 기반 모듈이 나오면 아래 findMatchedIds() 안의 호출만 교체하면 됨.
        List<Long> matchedIds = findMatchedIds(request);

        // 확정 직전 재검증: 아직 대기열에 있는 요청만 남김
        List<RideRequestDto> confirmed = new ArrayList<>();
        for (Long id : matchedIds) {
            RideRequestDto r = waiting.get(id);
            if (r != null) {
                confirmed.add(r);
            }
        }

        if (confirmed.size() >= 2) {
            confirmGroup(confirmed);
        } else {
            RideResponseDto res = RideResponseDto.of(MatchStatus.WAITING,
                    String.format("대기 등록 완료 (현재 대기 %d건)", waiting.size()), userId, reqId);
            push(userId, res);
        }
    }

    /** 매칭 모듈 호출 지점 (현재: 규칙 기반 임시 호출) */
    private List<Long> findMatchedIds(RideRequestDto request) {
        List<double[]> waitingList = new ArrayList<>();
        List<LocalDateTime> waitingTimes = new ArrayList<>();
        for (Map.Entry<Long, RideRequestDto> e : waiting.entrySet()) {
            waitingList.add(e.getValue().toAlgorithmArray(e.getKey()));
            waitingTimes.add(e.getValue().getDepartureTime());
        }
        return CarpoolAlgorithm.findMatchGroup(
                request.getRequestId(),
                request.getStartLat(), request.getStartLng(),
                request.getEndLat(), request.getEndLng(),
                request.getDepartureTime(), request.getPassengerCount(),
                waitingList, waitingTimes);
    }

    private void confirmGroup(List<RideRequestDto> members) {
        int totalPassengers = 0;
        int bestDistance = 0, bestDuration = 0, bestFare = 0;

        for (RideRequestDto m : members) {
            waiting.remove(m.getRequestId());
            waitingByUser.remove(m.getUserId());
            totalPassengers += m.getPassengerCount();

            // TODO(3주차): [필요 모듈] 경유 순서·경로·요금 계산 모듈 (알고리즘 담당)
            //  - 역할: 그룹원의 승·하차 경유 순서를 정하고 그룹 전체 경로를 계산
            //  - 입력: 확정된 그룹원 목록 / 출력: 경유 순서, 총 거리, 예상 시간, 총 요금
            //  - 지금은 그룹원 중 가장 긴 경로 하나로 임시 계산함. 모듈이 나오면
            //    이 반복문과 아래 요금 계산을 해당 모듈 결과로 교체.
            int[] route = CarpoolAlgorithm.estimateRouteAndFare(
                    m.getStartLat(), m.getStartLng(), m.getEndLat(), m.getEndLng());
            if (route[2] >= bestFare) {
                bestDistance = route[0];
                bestDuration = route[1];
                bestFare = route[2];
            }
        }

        int split = CarpoolAlgorithm.calculateSplitFare(bestFare, Math.max(1, totalPassengers));
        RideGroup group = new RideGroup(groupIdGen.getAndIncrement(), members, bestFare, split,
                bestDistance, bestDuration);
        groups.put(group.getGroupId(), group);
        for (RideRequestDto m : members) {
            groupByUser.put(m.getUserId(), group.getGroupId());
        }

        pushGroup(group, "매칭 완료");
    }

    // ---------- 취소 / 만료 ----------

    public synchronized void cancelRequest(String userId) {
        Long reqId = waitingByUser.remove(userId);
        if (reqId == null) {
            push(userId, RideResponseDto.of(MatchStatus.ERROR, "취소할 대기 요청이 없습니다.", userId, null));
            return;
        }
        waiting.remove(reqId);
        push(userId, RideResponseDto.of(MatchStatus.CANCELED, "요청이 취소되었습니다.", userId, reqId));
    }

    /** 30초마다 만료된 대기 요청 정리 (TaxisharingApplication에 @EnableScheduling 필요) */
    @Scheduled(fixedDelay = 30_000)
    public synchronized void expireOldRequests() {
        LocalDateTime now = LocalDateTime.now();
        Iterator<RideRequestDto> it = waiting.values().iterator();
        while (it.hasNext()) {
            RideRequestDto r = it.next();
            boolean waitedTooLong = Duration.between(r.getCreatedAt(), now).toMinutes() >= MAX_WAIT_MINUTES;
            boolean timeWindowPassed = now.isAfter(r.getDepartureTime().plusMinutes(TIME_WINDOW_MINUTES));
            if (waitedTooLong || timeWindowPassed) {
                it.remove();
                waitingByUser.remove(r.getUserId());
                push(r.getUserId(), RideResponseDto.of(MatchStatus.EXPIRED,
                        "매칭 상대를 찾지 못해 요청이 만료되었습니다.", r.getUserId(), r.getRequestId()));
            }
        }
    }

    // ---------- 상태 관리 ----------

    public synchronized void updateGroupStatus(long groupId, MatchStatus next) {
        RideGroup group = groups.get(groupId);
        if (group == null || next == null) return;

        if (!group.getStatus().canTransitionTo(next)) {
            String msg = String.format("상태를 %s에서 %s(으)로 변경할 수 없습니다.",
                    group.getStatus().getLabel(), next.getLabel());
            for (RideRequestDto m : group.getMembers()) {
                push(m.getUserId(), RideResponseDto.of(MatchStatus.ERROR, msg, m.getUserId(), m.getRequestId()));
            }
            return;
        }

        group.setStatus(next);
        pushGroup(group, "상태 변경: " + next.getLabel());

        if (next == MatchStatus.COMPLETED || next == MatchStatus.CANCELED) {
            for (RideRequestDto m : group.getMembers()) {
                groupByUser.remove(m.getUserId());
            }
            groups.remove(groupId);
        }
    }

    // ---------- 푸시 ----------

    private void pushGroup(RideGroup group, String message) {
        List<String> memberIds = new ArrayList<>();
        for (RideRequestDto m : group.getMembers()) memberIds.add(m.getUserId());

        for (RideRequestDto m : group.getMembers()) {
            RideResponseDto res = RideResponseDto.of(group.getStatus(), message, m.getUserId(), m.getRequestId());
            res.setGroupId(group.getGroupId());
            res.setMemberUserIds(memberIds);
            res.setTotalFare(group.getTotalFare());
            res.setSplitFare(group.getSplitFare());
            res.setDistanceMeters(group.getDistanceMeters());
            res.setDurationSeconds(group.getDurationSeconds());
            push(m.getUserId(), res);
        }
    }

    private void push(String userId, RideResponseDto response) {
        if (userId == null || userId.isBlank()) return;
        messaging.convertAndSend("/sub/match/" + userId, response);
    }
}
