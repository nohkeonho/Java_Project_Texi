package com.example.taxisharing.dto;

public class RideRequestDto {
    private String userId;
    private String departure;
    private String destination;

    // Getter
    public String getUserId() { return userId; }
    public String getDeparture() { return departure; }
    public String getDestination() { return destination; }

    // Setter
    public void setUserId(String userId) { this.userId = userId; }
    public void setDeparture(String departure) { this.departure = departure; }
    public void setDestination(String destination) { this.destination = destination; }
}