package com.example.taxisharing.dto;

/** 그룹 상태 변경 요청 (예: MATCHED -> IN_RIDE) */
public record StatusUpdateDto(long groupId, MatchStatus status) {
}
