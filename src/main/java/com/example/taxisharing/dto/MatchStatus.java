package com.example.taxisharing.dto;

public enum MatchStatus {
    WAITING("대기중"),
    MATCHED("매칭됨"),
    IN_RIDE("탑승중"),
    COMPLETED("완료"),
    EXPIRED("만료"),
    CANCELED("취소"),
    ERROR("오류");

    private final String label;

    MatchStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean canTransitionTo(MatchStatus next) {
        return switch (this) {
            case MATCHED -> next == IN_RIDE || next == CANCELED;
            case IN_RIDE -> next == COMPLETED;
            default -> false;
        };
    }
}
