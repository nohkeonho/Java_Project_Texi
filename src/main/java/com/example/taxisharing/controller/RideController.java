package com.example.taxisharing.controller;

import com.example.taxisharing.dto.RideRequestDto;
import com.example.taxisharing.dto.RideResponseDto;
import com.example.taxisharing.service.RideQueueService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class RideController {

    private final RideQueueService rideQueueService;

    public RideController(RideQueueService rideQueueService) {
        this.rideQueueService = rideQueueService;
    }

    @MessageMapping("/match/request")
    @SendTo("/sub/queue")
    public RideResponseDto handleRideRequest(RideRequestDto request) {
        System.out.println("합승 신청 수신 - userId: " + request.getUserId()
                + ", 출발: " + request.getDeparture()
                + ", 도착: " + request.getDestination());
        return rideQueueService.processRideRequest(request);
    }
}