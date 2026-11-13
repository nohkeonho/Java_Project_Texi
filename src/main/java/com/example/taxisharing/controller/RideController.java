package com.example.taxisharing.controller;

import java.util.Queue;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.taxisharing.dto.RideRequestDto;
import com.example.taxisharing.dto.RideResponseDto;
import com.example.taxisharing.service.RideQueueService;

@RestController
@RequestMapping("/api/ride")
public class RideController {

    private final RideQueueService rideQueueService;

    // 일반 생성자 주입 (롬복 불필요)
    public RideController(RideQueueService rideQueueService) {
        this.rideQueueService = rideQueueService;
    }

    @PostMapping("/request")
    public RideResponseDto requestRide(@RequestBody RideRequestDto requestDto) {
        rideQueueService.addRequest(requestDto);
        return new RideResponseDto("SUCCESS", "매칭 대기열에 등록되었습니다.");
    }

    @GetMapping("/queue")
    public Queue<RideRequestDto> getQueue() {
        return rideQueueService.getQueue();
    }
}