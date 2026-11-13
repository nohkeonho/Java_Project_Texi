package com.example.taxisharing.dto;

public class RideResponseDto {
    private String status;
    private String message;

    // 생성자
    public RideResponseDto(String status, String message) {
        this.status = status;
        this.message = message;
    }

    // Getter
    public String getStatus() { return status; }
    public String getMessage() { return message; }
}