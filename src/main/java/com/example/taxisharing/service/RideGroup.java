package com.example.taxisharing.service;

import com.example.taxisharing.dto.MatchStatus;
import com.example.taxisharing.dto.RideRequestDto;

import java.util.List;

/** 매칭이 확정된 그룹 */
public class RideGroup {
    private final long groupId;
    private final List<RideRequestDto> members;
    private final int totalFare;
    private final int splitFare;
    private final int distanceMeters;
    private final int durationSeconds;
    private MatchStatus status = MatchStatus.MATCHED;

    public RideGroup(long groupId, List<RideRequestDto> members, int totalFare, int splitFare,
                     int distanceMeters, int durationSeconds) {
        this.groupId = groupId;
        this.members = members;
        this.totalFare = totalFare;
        this.splitFare = splitFare;
        this.distanceMeters = distanceMeters;
        this.durationSeconds = durationSeconds;
    }

    public long getGroupId() { return groupId; }
    public List<RideRequestDto> getMembers() { return members; }
    public int getTotalFare() { return totalFare; }
    public int getSplitFare() { return splitFare; }
    public int getDistanceMeters() { return distanceMeters; }
    public int getDurationSeconds() { return durationSeconds; }
    public MatchStatus getStatus() { return status; }
    public void setStatus(MatchStatus status) { this.status = status; }
}
