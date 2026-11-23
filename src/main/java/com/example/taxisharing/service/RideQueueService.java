package com.example.taxisharing.service;

import com.example.taxisharing.algorithm.CarpoolAlgorithm;
import com.example.taxisharing.dto.RideRequestDto;
import com.example.taxisharing.dto.RideResponseDto;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class RideQueueService {

    private final AtomicLong idGenerator = new AtomicLong(1L);
    private final Map<Long, RideRequestDto> waitingRequests = new LinkedHashMap<>();

    public synchronized RideResponseDto processRideRequest(RideRequestDto request) {
        if (request == null) {
            return new RideResponseDto("ERROR", "요청 데이터가 비어 있습니다.");
        }

        long reqId = request.getRequestId() > 0 ? request.getRequestId() : idGenerator.getAndIncrement();
        request.setRequestId(reqId);

        if (request.getDepartureTime() == null) {
            request.setDepartureTime(LocalDateTime.now());
        }

        int passengerCount = Math.max(1, request.getPassengerCount());
        request.setPassengerCount(passengerCount);

        waitingRequests.put(reqId, request);

        List<double[]> waitingList = new ArrayList<>();
        List<LocalDateTime> waitingTimes = new ArrayList<>();

        for (Map.Entry<Long, RideRequestDto> entry : waitingRequests.entrySet()) {
            waitingList.add(entry.getValue().toAlgorithmArray(entry.getKey()));
            waitingTimes.add(entry.getValue().getDepartureTime());
        }

        List<Long> matchedIds = CarpoolAlgorithm.findMatchGroup(
                reqId,
                request.getStartLat(),
                request.getStartLng(),
                request.getEndLat(),
                request.getEndLng(),
                request.getDepartureTime(),
                passengerCount,
                waitingList,
                waitingTimes
        );

        if (matchedIds != null && matchedIds.size() >= 2) {
            int totalPassengers = 0;
            for (Long id : matchedIds) {
                RideRequestDto removed = waitingRequests.remove(id);
                if (removed != null) {
                    totalPassengers += Math.max(1, removed.getPassengerCount());
                }
            }

            int[] routeInfo = CarpoolAlgorithm.estimateRouteAndFare(
                    request.getStartLat(),
                    request.getStartLng(),
                    request.getEndLat(),
                    request.getEndLng()
            );
            int totalFare = routeInfo[2];
            int splitFare = CarpoolAlgorithm.calculateSplitFare(totalFare, Math.max(1, totalPassengers));

            String msg = String.format(
                    "매칭 완료 (요청ID: %s, 총요금: %d원, 1인당 예상요금: %d원, 남은 대기: %d건)",
                    matchedIds, totalFare, splitFare, waitingRequests.size()
            );
            return new RideResponseDto("MATCHED", msg);
        }

        String msg = String.format(
                "대기 등록 완료 (요청ID: %d, 사용자: %s, 출발: %s, 도착: %s, 현재 대기: %d건)",
                reqId, request.getUserId(), request.getDeparture(), request.getDestination(), waitingRequests.size()
        );
        return new RideResponseDto("WAITING", msg);
    }
}