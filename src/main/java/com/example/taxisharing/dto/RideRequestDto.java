package com.example.taxisharing.dto;

import java.time.LocalDateTime;

public class RideRequestDto {
    private String userId;
    private String departure;
    private String destination;

    private long requestId;
    private double startLat;
    private double startLng;
    private double endLat;
    private double endLng;
    private LocalDateTime departureTime;
    private int passengerCount = 1;

    public RideRequestDto() {
    }

    public String getUserId() { return userId; }
    public String getDeparture() { return departure; }
    public String getDestination() { return destination; }

    public void setUserId(String userId) { this.userId = userId; }
    public void setDeparture(String departure) { this.departure = departure; }
    public void setDestination(String destination) { this.destination = destination; }

    public long getRequestId() { return requestId; }
    public void setRequestId(long requestId) { this.requestId = requestId; }

    public double getStartLat() { return startLat; }
    public void setStartLat(double startLat) { this.startLat = startLat; }

    public double getStartLng() { return startLng; }
    public void setStartLng(double startLng) { this.startLng = startLng; }

    public double getEndLat() { return endLat; }
    public void setEndLat(double endLat) { this.endLat = endLat; }

    public double getEndLng() { return endLng; }
    public void setEndLng(double endLng) { this.endLng = endLng; }

    public LocalDateTime getDepartureTime() { return departureTime; }
    public void setDepartureTime(LocalDateTime departureTime) { this.departureTime = departureTime; }

    public int getPassengerCount() { return passengerCount; }
    public void setPassengerCount(int passengerCount) { this.passengerCount = passengerCount; }

    public double[] toAlgorithmArray(long resolvedId) {
        return new double[]{
                (double) resolvedId,
                startLat,
                startLng,
                endLat,
                endLng,
                (double) Math.max(1, passengerCount)
        };
    }
}