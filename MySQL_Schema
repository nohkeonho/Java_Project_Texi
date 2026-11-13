-- =====================================================================
-- 택시 합승객 매칭 어플리케이션 - MySQL 스키마 (MySQL 8.0.16 이상)
-- 계획서 기준: 회원 / 합승 요청 / 매칭 / 평점·신고
-- =====================================================================
-- [기본 규칙]
--  * 엔진은 InnoDB (외래키, 트랜잭션 지원), 문자셋은 utf8mb4 (한글, 이모지 OK)
--  * 금액은 원 단위 INT, 거리는 미터(m) 단위 INT 로 저장 (소수점 오차 방지)
--  * 위도/경도는 DECIMAL(9,6) (소수점 6자리 = 약 10cm 정밀도)
--  * 계획서의 개인정보 대책: 정확한 주소는 저장하지 않고 "00편의점 앞" 같은
--    장소 이름(구역)과 좌표만 저장한다.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS taxi_share
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE taxi_share;

-- 다시 실행해도 되도록, 자식 테이블부터 순서대로 삭제한다. (외래키 때문에 순서 중요!)
DROP TABLE IF EXISTS report;
DROP TABLE IF EXISTS rating;
DROP TABLE IF EXISTS match_member;
DROP TABLE IF EXISTS ride_match;
DROP TABLE IF EXISTS ride_request;
DROP TABLE IF EXISTS member;


-- ---------------------------------------------------------------------
-- 1. 회원 (member)
-- ---------------------------------------------------------------------
CREATE TABLE member (
    member_id           BIGINT          NOT NULL AUTO_INCREMENT,
    email               VARCHAR(100)    NOT NULL,                -- 로그인 ID로 사용
    password_hash       VARCHAR(255)    NOT NULL,                -- 비밀번호 원문 저장 금지! 해시값만 저장
    nickname            VARCHAR(30)     NOT NULL,                -- 매칭 상대에게 보이는 이름 (실명 노출 방지)
    gender              ENUM('M','F')   NOT NULL,                -- 동성 합승 조건용 (법령 확인 후 필요 없으면 삭제)
    location_agreed_at  DATETIME        NULL,                    -- 위치정보 수집·이용 동의 시각 (NULL이면 미동의)
    is_virtual          TINYINT(1)      NOT NULL DEFAULT 0,      -- 1이면 시뮬레이터용 가상 사용자
    avg_rating          DECIMAL(3,2)    NOT NULL DEFAULT 0.00,   -- 평균 평점 (매칭 점수에 사용, 조회 속도용 저장값)
    rating_count        INT             NOT NULL DEFAULT 0,      -- 받은 평점 개수
    status              ENUM('ACTIVE','SUSPENDED','WITHDRAWN')
                                        NOT NULL DEFAULT 'ACTIVE', -- 정상 / 이용정지(신고 누적) / 탈퇴
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (member_id),
    UNIQUE KEY uk_member_email (email),
    UNIQUE KEY uk_member_nickname (nickname)
) ENGINE=InnoDB;


-- ---------------------------------------------------------------------
-- 2. 합승 요청 (ride_request)  ※ 계획서: 합승 요청(호출) 페이지, 모의 호출
--    "이 사람이 이 시간에 여기서 저기까지 같이 타고 싶다" 한 건이 한 행이다.
-- ---------------------------------------------------------------------
CREATE TABLE ride_request (
    request_id      BIGINT          NOT NULL AUTO_INCREMENT,
    member_id       BIGINT          NOT NULL,                    -- 요청한 회원

    -- 출발지 / 목적지: 정확한 주소 대신 구역 이름 + 좌표
    origin_name     VARCHAR(100)    NOT NULL,                    -- 예: '선문대 정문 앞'
    origin_lat      DECIMAL(9,6)    NOT NULL,
    origin_lng      DECIMAL(9,6)    NOT NULL,
    dest_name       VARCHAR(100)    NOT NULL,                    -- 예: '천안역 동부광장 앞'
    dest_lat        DECIMAL(9,6)    NOT NULL,
    dest_lng        DECIMAL(9,6)    NOT NULL,

    -- 출발 희망 시간대: 이 범위가 겹쳐야 매칭 후보가 된다 (계획서: 시간대)
    depart_from     DATETIME        NOT NULL,                    -- 가장 이른 출발 시각
    depart_until    DATETIME        NOT NULL,                    -- 가장 늦은 출발 시각

    -- 매칭 조건
    max_detour_m    INT             NOT NULL DEFAULT 2000,       -- 우회 거리 허용치 (m) (계획서: 우회 거리 허용치)
    same_gender_only TINYINT(1)     NOT NULL DEFAULT 1,          -- 1이면 동성끼리만 매칭

    -- 지도 API로 계산한 값 (혼자 탔을 때 기준)
    est_distance_m  INT             NULL,                        -- 예상 이동 거리 (m)
    est_fare        INT             NULL,                        -- 예상 택시 요금 (원)

    status          ENUM('WAITING','MATCHED','CANCELLED','EXPIRED')
                                    NOT NULL DEFAULT 'WAITING',  -- 대기중 / 매칭됨 / 취소 / 시간 만료
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (request_id),
    -- 매칭 후보 검색("대기중이면서 시간이 겹치는 요청")을 빠르게 하려는 인덱스
    KEY idx_req_status_time (status, depart_from, depart_until),
    KEY idx_req_member (member_id),
    CONSTRAINT fk_req_member FOREIGN KEY (member_id) REFERENCES member (member_id),
    CONSTRAINT chk_req_time CHECK (depart_until >= depart_from),
    CONSTRAINT chk_req_detour CHECK (max_detour_m >= 0)
) ENGINE=InnoDB;


-- ---------------------------------------------------------------------
-- 3. 매칭 (ride_match)  : 합승 한 팀(택시 1대)
-- ---------------------------------------------------------------------
CREATE TABLE ride_match (
    match_id          BIGINT    NOT NULL AUTO_INCREMENT,
    status            ENUM('PROPOSED','CONFIRMED','COMPLETED','CANCELLED')
                                NOT NULL DEFAULT 'PROPOSED',     -- 제안됨 / 모두 수락(확정) / 완료 / 취소
    total_distance_m  INT       NULL,                            -- 경유 순서대로 갔을 때 택시 총 이동 거리 (m)
    total_fare        INT       NULL,                            -- 택시 총 예상 요금 (원)
    created_at        DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at      DATETIME  NULL,                            -- 완료 시각 (평점·신고는 완료 후 가능하게 하기)

    PRIMARY KEY (match_id)
) ENGINE=InnoDB;


-- ---------------------------------------------------------------------
-- 3-1. 매칭 참여자 (match_member)
--    회원 ↔ 매칭은 다대다(N:M) 관계라서 중간 테이블이 필요하다.
--    한 매칭에 2명 이상이 참여하고, 사람마다 점수·요금·내리는 순서가 다르다.
-- ---------------------------------------------------------------------
CREATE TABLE match_member (
    match_member_id BIGINT          NOT NULL AUTO_INCREMENT,
    match_id        BIGINT          NOT NULL,
    request_id      BIGINT          NOT NULL,                    -- 어떤 요청으로 이 매칭에 들어왔는지
    member_id       BIGINT          NOT NULL,                    -- 조회 편의를 위해 저장 (request에서도 알 수 있음)

    pickup_order    TINYINT         NOT NULL,                    -- 탑승 순서 (1, 2, ...)
    dropoff_order   TINYINT         NOT NULL,                    -- 하차 순서 (1, 2, ...)
    own_distance_m  INT             NOT NULL,                    -- 이 사람이 실제로 탄 거리 (m) → 거리 비례 요금 분담의 기준
    detour_m        INT             NOT NULL DEFAULT 0,          -- 합승 때문에 늘어난 우회 거리 (m)
    match_score     DECIMAL(5,2)    NULL,                        -- 매칭 점수 (경로 겹침, 우회 거리, 시간대 등 가중치 합)
    fare_share      INT             NOT NULL,                    -- 이 사람이 내야 할 분담 요금 (원)
    response        ENUM('PENDING','ACCEPTED','REJECTED')
                                    NOT NULL DEFAULT 'PENDING',  -- 매칭 제안에 대한 수락 여부

    PRIMARY KEY (match_member_id),
    UNIQUE KEY uk_mm_match_request (match_id, request_id),       -- 같은 요청이 한 매칭에 두 번 들어가는 것 방지
    UNIQUE KEY uk_mm_match_member (match_id, member_id),         -- 같은 사람이 한 매칭에 두 번 들어가는 것 방지
    KEY idx_mm_member (member_id),
    KEY idx_mm_request (request_id),
    CONSTRAINT fk_mm_match   FOREIGN KEY (match_id)   REFERENCES ride_match (match_id),
    CONSTRAINT fk_mm_request FOREIGN KEY (request_id) REFERENCES ride_request (request_id),
    CONSTRAINT fk_mm_member  FOREIGN KEY (member_id)  REFERENCES member (member_id),
    CONSTRAINT chk_mm_fare   CHECK (fare_share >= 0)
) ENGINE=InnoDB;


-- ---------------------------------------------------------------------
-- 4. 평점 (rating)  : 합승 끝난 뒤 같이 탄 사람에게 매기는 점수
--    한 매칭 안에서 "누가 누구에게" 한 번만 줄 수 있다.
-- ---------------------------------------------------------------------
CREATE TABLE rating (
    rating_id   BIGINT       NOT NULL AUTO_INCREMENT,
    match_id    BIGINT       NOT NULL,
    rater_id    BIGINT       NOT NULL,                           -- 평점을 준 사람
    ratee_id    BIGINT       NOT NULL,                           -- 평점을 받은 사람
    score       TINYINT      NOT NULL,                           -- 1 ~ 5점
    comment     VARCHAR(200) NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (rating_id),
    UNIQUE KEY uk_rating_once (match_id, rater_id, ratee_id),    -- 중복 평가 방지
    KEY idx_rating_ratee (ratee_id),
    CONSTRAINT fk_rating_match FOREIGN KEY (match_id) REFERENCES ride_match (match_id),
    CONSTRAINT fk_rating_rater FOREIGN KEY (rater_id) REFERENCES member (member_id),
    CONSTRAINT fk_rating_ratee FOREIGN KEY (ratee_id) REFERENCES member (member_id),
    CONSTRAINT chk_rating_score CHECK (score BETWEEN 1 AND 5),
    CONSTRAINT chk_rating_self  CHECK (rater_id <> ratee_id)     -- 자기 자신 평가 금지
) ENGINE=InnoDB;


-- ---------------------------------------------------------------------
-- 5. 신고 (report)
-- ---------------------------------------------------------------------
CREATE TABLE report (
    report_id    BIGINT       NOT NULL AUTO_INCREMENT,
    match_id     BIGINT       NOT NULL,
    reporter_id  BIGINT       NOT NULL,                          -- 신고한 사람
    reported_id  BIGINT       NOT NULL,                          -- 신고당한 사람
    reason       ENUM('NO_SHOW','ABUSE','FARE_DISPUTE','ROUTE_DEVIATION','OTHER')
                              NOT NULL,                          -- 노쇼 / 욕설·불쾌행위 / 요금 분쟁 / 경로 이탈 / 기타
    detail       TEXT         NULL,                              -- 상세 내용
    status       ENUM('RECEIVED','REVIEWING','RESOLVED','REJECTED')
                              NOT NULL DEFAULT 'RECEIVED',       -- 접수 / 검토중 / 처리완료 / 반려
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at  DATETIME     NULL,

    PRIMARY KEY (report_id),
    UNIQUE KEY uk_report_once (match_id, reporter_id, reported_id),
    KEY idx_report_reported (reported_id),                       -- "이 사람이 몇 번 신고당했나" 집계용
    CONSTRAINT fk_report_match    FOREIGN KEY (match_id)    REFERENCES ride_match (match_id),
    CONSTRAINT fk_report_reporter FOREIGN KEY (reporter_id) REFERENCES member (member_id),
    CONSTRAINT fk_report_reported FOREIGN KEY (reported_id) REFERENCES member (member_id),
    CONSTRAINT chk_report_self    CHECK (reporter_id <> reported_id)
) ENGINE=InnoDB;


-- =====================================================================
-- [자주 쓰게 될 쿼리 예시]  (실행하려면 주석 기호 -- 를 지우세요)
-- =====================================================================

-- (1) 매칭 후보 찾기: 내 요청과 출발 시간대가 겹치는 "대기중" 요청 (내 요청 id = 1 이라고 가정)
-- SELECT o.*
--   FROM ride_request me
--   JOIN ride_request o
--     ON o.status = 'WAITING'
--    AND o.request_id <> me.request_id
--    AND o.depart_from  <= me.depart_until   -- 시간 범위가 겹치는 조건
--    AND o.depart_until >= me.depart_from
--  WHERE me.request_id = 1;

-- (2) 평점을 받은 뒤 회원의 평균 평점 갱신 (평점 INSERT와 같은 트랜잭션으로 실행)
-- UPDATE member m
--    SET m.avg_rating   = (SELECT ROUND(AVG(score), 2) FROM rating WHERE ratee_id = m.member_id),
--        m.rating_count = (SELECT COUNT(*)             FROM rating WHERE ratee_id = m.member_id)
--  WHERE m.member_id = 2;

-- (3) 신고 3회 이상 받은 회원 조회 (이용정지 검토 대상)
-- SELECT reported_id, COUNT(*) AS report_cnt
--   FROM report
--  GROUP BY reported_id
-- HAVING COUNT(*) >= 3;
