package com.example.taxisharing.controller;

import com.example.taxisharing.dto.RideRequestDto;
import com.example.taxisharing.dto.StatusUpdateDto;
import com.example.taxisharing.service.RideQueueService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

/**
 * 클라이언트 -> 서버: /pub/match/request, /pub/match/cancel, /pub/match/status
 * 서버 -> 클라이언트: /sub/match/{userId} (본인에게만 전달)
 */
@Controller
public class RideController {

    private final RideQueueService rideQueueService;

    public RideController(RideQueueService rideQueueService) {
        this.rideQueueService = rideQueueService;
    }

    @MessageMapping("/match/request")
    public void handleRideRequest(RideRequestDto request) {
        rideQueueService.processRideRequest(request);
    }

    @MessageMapping("/match/cancel")
    public void handleCancel(RideRequestDto request) {
        if (request != null) {
            rideQueueService.cancelRequest(request.getUserId());
        }
    }

    @MessageMapping("/match/status")
    public void handleStatusUpdate(StatusUpdateDto update) {
        if (update != null) {
            rideQueueService.updateGroupStatus(update.groupId(), update.status());
        }
    }
}
