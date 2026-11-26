package com.example.taxisharing.dto;

import java.time.LocalDateTime;

public class RideRequestDto {
    private long requestId;
    private String userId;
    private String departure;
    private String destination;
    private double startLat;
    private double startLng;
    private double endLat;
    private double endLng;
    private LocalDateTime departureTime;
    private int passengerCount;
    private LocalDateTime createdAt; // 서버가 설정

    /** 입력 검증. 문제가 없으면 null, 있으면 오류 메시지 반환 */
    public String validationError() {
        if (userId == null || userId.isBlank()) return "userId가 필요합니다.";
        if (!validCoord(startLat, startLng)) return "출발 좌표가 올바르지 않습니다.";
        if (!validCoord(endLat, endLng)) return "도착 좌표가 올바르지 않습니다.";
        if (passengerCount < 1 || passengerCount > 4) return "탑승 인원은 1~4명이어야 합니다.";
        return null;
    }

    private static boolean validCoord(double lat, double lng) {
        if (lat == 0.0 && lng == 0.0) return false; // 누락된 값
        return lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180;
    }

    /** 알고리즘 모듈 입력용 배열: [id, 출발위도, 출발경도, 도착위도, 도착경도, 인원] */
    public double[] toAlgorithmArray(Long id) {
        return new double[]{
                id != null ? id.doubleValue() : (double) requestId,
                startLat,
                startLng,
                endLat,
                endLng,
                (double) Math.max(1, passengerCount)
        };
    }

    public long getRequestId() { return requestId; }
    public void setRequestId(long requestId) { this.requestId = requestId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getDeparture() { return departure; }
    public void setDeparture(String departure) { this.departure = departure; }
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
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
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
