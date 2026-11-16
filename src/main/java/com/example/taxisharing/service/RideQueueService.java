package com.example.taxisharing.service;

import java.util.LinkedList;
import java.util.Queue;

import org.springframework.stereotype.Service;

import com.example.taxisharing.dto.RideRequestDto;

@Service
public class RideQueueService {
    private final Queue<RideRequestDto> rideQueue = new LinkedList<>();

    public void addRequest(RideRequestDto request) {
        rideQueue.offer(request);
    }

    public Queue<RideRequestDto> getQueue() {
        return rideQueue;
    }
}