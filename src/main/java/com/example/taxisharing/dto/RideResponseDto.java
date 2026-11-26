package com.example.taxisharing.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RideResponseDto {
    private MatchStatus status;
    private String statusLabel;
    private String message;
    private Long requestId;
    private String userId;
    private Long groupId;
    private List<String> memberUserIds;
    private Integer totalFare;
    private Integer splitFare;
    private Integer distanceMeters;
    private Integer durationSeconds;

    public RideResponseDto() {
    }

    public RideResponseDto(MatchStatus status, String message) {
        setStatus(status);
        this.message = message;
    }

    public static RideResponseDto of(MatchStatus status, String message, String userId, Long requestId) {
        RideResponseDto r = new RideResponseDto(status, message);
        r.userId = userId;
        r.requestId = requestId;
        return r;
    }

    public MatchStatus getStatus() { return status; }
    public void setStatus(MatchStatus status) {
        this.status = status;
        this.statusLabel = status != null ? status.getLabel() : null;
    }
    public String getStatusLabel() { return statusLabel; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
    public List<String> getMemberUserIds() { return memberUserIds; }
    public void setMemberUserIds(List<String> memberUserIds) { this.memberUserIds = memberUserIds; }
    public Integer getTotalFare() { return totalFare; }
    public void setTotalFare(Integer totalFare) { this.totalFare = totalFare; }
    public Integer getSplitFare() { return splitFare; }
    public void setSplitFare(Integer splitFare) { this.splitFare = splitFare; }
    public Integer getDistanceMeters() { return distanceMeters; }
    public void setDistanceMeters(Integer distanceMeters) { this.distanceMeters = distanceMeters; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }
}
