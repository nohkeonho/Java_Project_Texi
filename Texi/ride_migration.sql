-- =====================================================================
-- 합승 요청·매칭 저장 기능용 DB 변경 (schema.sql 실행 후, 이 파일을 한 번만 실행)
-- =====================================================================
-- 바뀌는 점
--  1) ride_request 에 "인원(passenger_count)" 추가
--     - 한 요청 = 한 사람 + 동행 인원. 예) 친구랑 둘이 타면 2
--     - 택시 승객석은 4석이고, 합승 상대가 최소 1명은 타야 하므로 한 요청은 1~3명
--  2) ride_match 에 "매칭 전체 점수(match_score)" 추가
--     - match_member.match_score 는 "각 참여자 입장의 점수",
--       ride_match.match_score 는 "이 팀 조합 자체의 점수" (알고리즘 비교·시뮬레이터 통계용)
--  3) 점수는 0~100 점, 분담 금액은 0원 이상이 되도록 CHECK 제약 추가
--
--  * DEFAULT 값을 주고 추가하므로 기존 데이터·기존 INSERT SQL 은 그대로 동작한다.
-- =====================================================================

USE taxi_share;

-- 1) 합승 요청: 인원
ALTER TABLE ride_request
    ADD COLUMN passenger_count TINYINT NOT NULL DEFAULT 1
        COMMENT '이 요청으로 탑승하는 인원 (본인 포함, 1~3명)'
        AFTER dest_lng,
    ADD CONSTRAINT chk_req_passenger CHECK (passenger_count BETWEEN 1 AND 3);

-- 2) 매칭: 팀 전체 점수
ALTER TABLE ride_match
    ADD COLUMN match_score DECIMAL(5,2) NULL
        COMMENT '매칭 조합 전체 점수 (0~100)'
        AFTER status,
    ADD CONSTRAINT chk_match_score CHECK (match_score IS NULL OR match_score BETWEEN 0 AND 100);

-- 3) 매칭 참여자: 개인 점수 범위
ALTER TABLE match_member
    ADD CONSTRAINT chk_mm_score CHECK (match_score IS NULL OR match_score BETWEEN 0 AND 100);

-- 4) "이 회원의 진행 중 요청" 을 빨리 찾기 위한 인덱스 (중복 요청 검사에 사용)
ALTER TABLE ride_request
    ADD KEY idx_req_member_status (member_id, status);

-- 확인용
-- SELECT request_id, member_id, origin_name, dest_name, passenger_count, status FROM ride_request;
-- SELECT match_id, status, match_score, total_fare FROM ride_match;
