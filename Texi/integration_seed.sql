-- =====================================================================
-- 택시 합승 앱 - 통합 테스트·시연용 DB 초기화 파일 (이 파일 하나만 실행하면 됨)
-- =====================================================================
-- ⚠ 실행하면 taxi_share DB 를 통째로 지우고 다시 만든다. (기존 데이터는 모두 사라짐)
--    누가 언제 실행해도 "같은 상태"로 돌아가게 하려고 일부러 이렇게 만들었다.
--    테스트 중에 데이터가 바뀌었으면(신고 → 정지 등) 이 파일을 다시 실행하면 처음 상태로 돌아간다.
--
-- 포함 내용 : schema.sql + consent_migration.sql + ride_migration.sql + gender_migration.sql 의 테이블 구조
--            + 동승 스타일(ride_style) 컬럼 + 시연용 고정 데이터
--            (schema.sql 을 바꾸면 이 파일의 CREATE TABLE 부분도 같이 바꿔 줄 것)
--
-- 동성 합승만 허용 : 모든 매칭은 같은 성별끼리만 들어 있다. (요청에 동성/혼성 선택 칸 없음)
--
-- 시간 값은 모두 "실행한 시각(NOW()) 기준"이라, 테스트·시연 직전에 실행하는 것을 권장한다.
-- (대기 중 요청은 실행 후 약 6시간 동안 유효)
--
-- ---------------------------------------------------------------------
-- [테스트 회원]  비밀번호는 모두 test1234
-- ---------------------------------------------------------------------
--  번호  이름        아이디(이메일)          성별  역할
--   1   노건호      geonho@sunmoon.test     M    일반 회원. 지금 "탑승 중"인 합승이 있음 (완료 → 결제 → 평점 테스트)
--   2   정원이      wonyi@sunmoon.test      M    신고 3명 누적 → 이용정지 (로그인 차단 확인)
--   3   이토 미나미  minami@sunmoon.test     F    평점 좋은 일반 회원. 여성 쪽 매칭 점수 비교 테스트의 "호출하는 사람"
--   4   진경은      kyungeun@sunmoon.test   F    신고 2명 누적(경고). 1명만 더 신고하면 정지 → 신고 누적 테스트 대상
--   5   김가영      gayoung@sunmoon.test    F    평점 낮음 (평균 1.6, 5개) → 여성 후보 중 점수가 낮아야 함
--   6   이예빈      yebin@sunmoon.test      F    평점 높음 (평균 4.8, 5개) → 여성 후보 중 점수가 높아야 함
--   7   최민준      minjun@sunmoon.test     M    평점 높음 (평균 4.75, 4개) → 남성 후보 중 점수가 높아야 함
--   8   한지훈      jihun@sunmoon.test      M    평점 낮음 (평균 1.5, 4개) → 남성 후보 중 점수가 낮아야 함
--   9   윤서연      seoyeon@sunmoon.test    F    신규 회원 (합승 기록·평점 없음) → 평점 점수가 중간(62.5점)이어야 함
--  10   강도현      dohyun@sunmoon.test     M    탈퇴 회원 → 로그인 차단 ("탈퇴한 계정입니다.")
--   (닉네임은 회원가입 규칙(공백 불가)에 맞춰 '이토미나미' 로 저장)
--
-- ---------------------------------------------------------------------
-- [합승 기록]  요청 상태: WAITING=대기중, MATCHED=매칭됨 / 매칭 상태: CONFIRMED=탑승중, COMPLETED=완료
-- ---------------------------------------------------------------------
--  매칭 1  완료    7일 전   여성 4인: 이예빈·김가영·이토 미나미·진경은   선문대 → 천안터미널
--  매칭 2  완료    5일 전   남성 4인: 노건호·정원이·최민준·한지훈       선문대 → 천안터미널
--  매칭 3  완료    2일 전   여성 2인: 이토 미나미·진경은                선문대 → 천안역
--  매칭 4  탑승중  30분 전 출발  남성 2인: 노건호·최민준               선문대 → 천안터미널
--  매칭 5  완료    3일 전   남성 4인: 노건호·최민준·한지훈·강도현       선문대 → 천안터미널
--  매칭 6  완료    4일 전   여성 3인: 이예빈·김가영·이토 미나미         선문대 → 천안터미널
--  대기 요청 6건 : 선문대 → 천안터미널, 10분 뒤~6시간 뒤, 출발지·목적지·시간이 모두 같고 평점만 다름
--                 여성 이예빈·윤서연·김가영 / 남성 최민준·한지훈
--                 + 진경은 (선문대 → 천안아산역, 4~6시간 뒤)
--
-- ---------------------------------------------------------------------
-- [이 데이터로 확인할 것]
-- ---------------------------------------------------------------------
--  A. 평점이 매칭 점수에 반영되는지 + 동성끼리만 후보가 되는지
--     선문대학교 → 천안터미널 호출 (출발 시간: 지금 ~ 30분 뒤)
--     이토 미나미로 호출 → 후보: 이예빈 78.8 > 윤서연 62.5(신규) > 김가영 38.8   (남성 후보는 안 나와야 정상)
--     노건호로 호출       → 후보: 최민준 76.4 > 한지훈 40.3                    (여성 후보는 안 나와야 정상)
--     출발지·목적지·시간이 똑같으므로 점수 차이는 평점(RatingSummary)에서만 나온다.
--  B. 신고 누적 처리
--     진경은은 현재 서로 다른 2명(이토 미나미, 김가영)에게 신고받은 상태(경고).
--     이예빈으로 로그인 → 매칭 1(완료)에서 진경은 신고
--     → 진경은 이용정지, 대기 요청 자동 취소, 진경은 로그인 차단 확인
--     (이토 미나미가 매칭 3에서 진경은을 또 신고하면? 같은 사람이므로 여전히 2명 → 정지 안 됨)
--  C. 이미 정지된 회원 : 정원이로 로그인 → "신고 누적으로 이용이 정지된 계정입니다."
--     탈퇴 회원 : 강도현으로 로그인 → "탈퇴한 계정입니다."
--  D. 전체 흐름 : 노건호로 로그인 → 매칭 4 완료 처리 → 분담 금액 5,500원 결제 완료 화면 → 최민준에게 평점·신고
-- =====================================================================

-- 한글이 깨지지 않도록 접속 문자셋을 utf8mb4 로 맞춘다. (명령줄 mysql 로 실행할 때도 안전하게)
SET NAMES utf8mb4;

DROP DATABASE IF EXISTS taxi_share;
CREATE DATABASE taxi_share DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE taxi_share;


-- =====================================================================
-- 1. 테이블 (schema.sql + 마이그레이션들을 합친 최종 모양)
-- =====================================================================

CREATE TABLE member (
    member_id           BIGINT          NOT NULL AUTO_INCREMENT,
    email               VARCHAR(100)    NOT NULL,                -- 로그인 ID
    password_hash       VARCHAR(255)    NOT NULL,                -- PasswordHasher 형식 해시
    nickname            VARCHAR(30)     NOT NULL,
    gender              ENUM('M','F')   NOT NULL,                -- 동성 합승 매칭에 사용
    privacy_agreed_at   DATETIME        NULL,                    -- 개인정보 동의 시각 (consent_migration)
    location_agreed_at  DATETIME        NULL,                    -- 위치정보 동의 시각
    ride_style          VARCHAR(100)    NULL,                    -- 나의 동승 스타일 (회원가입 화면의 입력칸)
    is_virtual          TINYINT(1)      NOT NULL DEFAULT 0,
    avg_rating          DECIMAL(3,2)    NOT NULL DEFAULT 0.00,
    rating_count        INT             NOT NULL DEFAULT 0,
    status              ENUM('ACTIVE','SUSPENDED','WITHDRAWN') NOT NULL DEFAULT 'ACTIVE',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (member_id),
    UNIQUE KEY uk_member_email (email),
    UNIQUE KEY uk_member_nickname (nickname)
) ENGINE=InnoDB;

CREATE TABLE ride_request (
    request_id       BIGINT        NOT NULL AUTO_INCREMENT,
    member_id        BIGINT        NOT NULL,
    origin_name      VARCHAR(100)  NOT NULL,
    origin_lat       DECIMAL(9,6)  NOT NULL,
    origin_lng       DECIMAL(9,6)  NOT NULL,
    dest_name        VARCHAR(100)  NOT NULL,
    dest_lat         DECIMAL(9,6)  NOT NULL,
    dest_lng         DECIMAL(9,6)  NOT NULL,
    passenger_count  TINYINT       NOT NULL DEFAULT 1,           -- ride_migration
    depart_from      DATETIME      NOT NULL,
    depart_until     DATETIME      NOT NULL,
    max_detour_m     INT           NOT NULL DEFAULT 2000,
    est_distance_m   INT           NULL,
    est_fare         INT           NULL,
    status           ENUM('WAITING','MATCHED','CANCELLED','EXPIRED') NOT NULL DEFAULT 'WAITING',
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (request_id),
    KEY idx_req_status_time (status, depart_from, depart_until),
    KEY idx_req_member (member_id),
    KEY idx_req_member_status (member_id, status),
    CONSTRAINT fk_req_member FOREIGN KEY (member_id) REFERENCES member (member_id),
    CONSTRAINT chk_req_time CHECK (depart_until >= depart_from),
    CONSTRAINT chk_req_detour CHECK (max_detour_m >= 0),
    CONSTRAINT chk_req_passenger CHECK (passenger_count BETWEEN 1 AND 3)
) ENGINE=InnoDB;

CREATE TABLE ride_match (
    match_id          BIGINT        NOT NULL AUTO_INCREMENT,
    status            ENUM('PROPOSED','CONFIRMED','COMPLETED','CANCELLED') NOT NULL DEFAULT 'PROPOSED',
    match_score       DECIMAL(5,2)  NULL,                        -- ride_migration
    total_distance_m  INT           NULL,
    total_fare        INT           NULL,
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at      DATETIME      NULL,
    PRIMARY KEY (match_id),
    CONSTRAINT chk_match_score CHECK (match_score IS NULL OR match_score BETWEEN 0 AND 100)
) ENGINE=InnoDB;

CREATE TABLE match_member (
    match_member_id BIGINT        NOT NULL AUTO_INCREMENT,
    match_id        BIGINT        NOT NULL,
    request_id      BIGINT        NOT NULL,
    member_id       BIGINT        NOT NULL,
    pickup_order    TINYINT       NOT NULL,
    dropoff_order   TINYINT       NOT NULL,
    own_distance_m  INT           NOT NULL,
    detour_m        INT           NOT NULL DEFAULT 0,
    match_score     DECIMAL(5,2)  NULL,
    fare_share      INT           NOT NULL,
    response        ENUM('PENDING','ACCEPTED','REJECTED') NOT NULL DEFAULT 'PENDING',
    PRIMARY KEY (match_member_id),
    UNIQUE KEY uk_mm_match_request (match_id, request_id),
    UNIQUE KEY uk_mm_match_member (match_id, member_id),
    KEY idx_mm_member (member_id),
    KEY idx_mm_request (request_id),
    CONSTRAINT fk_mm_match   FOREIGN KEY (match_id)   REFERENCES ride_match (match_id),
    CONSTRAINT fk_mm_request FOREIGN KEY (request_id) REFERENCES ride_request (request_id),
    CONSTRAINT fk_mm_member  FOREIGN KEY (member_id)  REFERENCES member (member_id),
    CONSTRAINT chk_mm_fare   CHECK (fare_share >= 0),
    CONSTRAINT chk_mm_score  CHECK (match_score IS NULL OR match_score BETWEEN 0 AND 100)
) ENGINE=InnoDB;

CREATE TABLE rating (
    rating_id   BIGINT       NOT NULL AUTO_INCREMENT,
    match_id    BIGINT       NOT NULL,
    rater_id    BIGINT       NOT NULL,
    ratee_id    BIGINT       NOT NULL,
    score       TINYINT      NOT NULL,
    comment     VARCHAR(200) NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (rating_id),
    UNIQUE KEY uk_rating_once (match_id, rater_id, ratee_id),
    KEY idx_rating_ratee (ratee_id),
    CONSTRAINT fk_rating_match FOREIGN KEY (match_id) REFERENCES ride_match (match_id),
    CONSTRAINT fk_rating_rater FOREIGN KEY (rater_id) REFERENCES member (member_id),
    CONSTRAINT fk_rating_ratee FOREIGN KEY (ratee_id) REFERENCES member (member_id),
    CONSTRAINT chk_rating_score CHECK (score BETWEEN 1 AND 5),
    CONSTRAINT chk_rating_self  CHECK (rater_id <> ratee_id)
) ENGINE=InnoDB;

CREATE TABLE report (
    report_id    BIGINT    NOT NULL AUTO_INCREMENT,
    match_id     BIGINT    NOT NULL,
    reporter_id  BIGINT    NOT NULL,
    reported_id  BIGINT    NOT NULL,
    reason       ENUM('NO_SHOW','ABUSE','FARE_DISPUTE','ROUTE_DEVIATION','OTHER') NOT NULL,
    detail       TEXT      NULL,
    status       ENUM('RECEIVED','REVIEWING','RESOLVED','REJECTED') NOT NULL DEFAULT 'RECEIVED',
    created_at   DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at  DATETIME  NULL,
    PRIMARY KEY (report_id),
    UNIQUE KEY uk_report_once (match_id, reporter_id, reported_id),
    KEY idx_report_reported (reported_id),
    CONSTRAINT fk_report_match    FOREIGN KEY (match_id)    REFERENCES ride_match (match_id),
    CONSTRAINT fk_report_reporter FOREIGN KEY (reporter_id) REFERENCES member (member_id),
    CONSTRAINT fk_report_reported FOREIGN KEY (reported_id) REFERENCES member (member_id),
    CONSTRAINT chk_report_self    CHECK (reporter_id <> reported_id)
) ENGINE=InnoDB;


-- =====================================================================
-- 2. 테스트 회원 10명 (남성 5명, 여성 5명)
--    번호를 직접 지정해서, 실행할 때마다 노건호=1번, 정원이=2번 ... 으로 항상 같다.
--    비밀번호 test1234 의 해시 (10명이 같은 값을 쓴다. 테스트 전용이라 괜찮지만 실제 회원에게는 절대 이렇게 하면 안 된다)
-- =====================================================================
SET @pw = 'pbkdf2$65536$U0VFRFRFU1RTRUVEVEVTXw==$4cdwNoLoLeTKy0wOYmAzEjr2T0cQaTqPYpMGnmxGDKY=';
SET @joined = NOW() - INTERVAL 14 DAY;   -- 가입·동의 시각 (2주 전)

INSERT INTO member (member_id, email, password_hash, nickname, gender,
                    privacy_agreed_at, location_agreed_at, ride_style, is_virtual, status, created_at) VALUES
    (1,  'geonho@sunmoon.test',   @pw, '노건호',     'M', @joined, @joined, '스몰톡 해도 괜찮아요',        1, 'ACTIVE',    @joined),
    (2,  'wonyi@sunmoon.test',    @pw, '정원이',     'M', @joined, @joined, '음악 들으면서 갈게요',        1, 'SUSPENDED', @joined),
    (3,  'minami@sunmoon.test',   @pw, '이토미나미', 'F', @joined, @joined, '조용히 갈래요',               1, 'ACTIVE',    @joined),
    (4,  'kyungeun@sunmoon.test', @pw, '진경은',     'F', @joined, @joined, '통화는 짧게 할게요',          1, 'ACTIVE',    @joined),
    (5,  'gayoung@sunmoon.test',  @pw, '김가영',     'F', @joined, @joined, '창문 열고 가고 싶어요',       1, 'ACTIVE',    @joined),
    (6,  'yebin@sunmoon.test',    @pw, '이예빈',     'F', @joined, @joined, '조용히 갈래요, 시간 약속 잘 지켜요', 1, 'ACTIVE', @joined),
    (7,  'minjun@sunmoon.test',   @pw, '최민준',     'M', @joined, @joined, '길 안내 도와드릴게요',        1, 'ACTIVE',    @joined),
    (8,  'jihun@sunmoon.test',    @pw, '한지훈',     'M', @joined, @joined, '통화를 자주 해요',            1, 'ACTIVE',    @joined),
    -- 윤서연 : 어제 가입한 신규 회원 (합승 기록 없음)
    (9,  'seoyeon@sunmoon.test',  @pw, '윤서연',     'F', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY, '처음이라 잘 부탁드려요', 1, 'ACTIVE', NOW() - INTERVAL 1 DAY),
    (10, 'dohyun@sunmoon.test',   @pw, '강도현',     'M', @joined, @joined, '조용히 갈래요',               1, 'WITHDRAWN', @joined);


-- =====================================================================
-- 3. 합승 요청 25건 (매칭됨 19건, 대기중 6건)
--    장소는 정확한 주소 대신 구역 이름 + 좌표 (계획서의 개인정보 대책)
-- =====================================================================
SET @t1 = DATE_FORMAT(NOW() - INTERVAL 7 DAY, '%Y-%m-%d 23:10:00');   -- 매칭 1 출발 (심야)
SET @t2 = DATE_FORMAT(NOW() - INTERVAL 5 DAY, '%Y-%m-%d 22:40:00');   -- 매칭 2 출발
SET @t3 = DATE_FORMAT(NOW() - INTERVAL 2 DAY, '%Y-%m-%d 18:30:00');   -- 매칭 3 출발
SET @t4 = NOW() - INTERVAL 30 MINUTE;                                 -- 매칭 4 출발 (지금 타고 가는 중)
SET @t5 = DATE_FORMAT(NOW() - INTERVAL 3 DAY, '%Y-%m-%d 23:40:00');   -- 매칭 5 출발 (심야)
SET @t6 = DATE_FORMAT(NOW() - INTERVAL 4 DAY, '%Y-%m-%d 21:50:00');   -- 매칭 6 출발
SET @tw = NOW() + INTERVAL 10 MINUTE;                                 -- 대기 요청 시작 시각

INSERT INTO ride_request (request_id, member_id, origin_name, origin_lat, origin_lng, dest_name, dest_lat, dest_lng,
                          passenger_count, depart_from, depart_until, max_detour_m,
                          est_distance_m, est_fare, status, created_at) VALUES
    -- 매칭 1 (완료) : 여성 4인
    ( 1, 6,  '선문대학교 정문 앞', 36.799000, 127.075000, '천안종합버스터미널 앞', 36.819700, 127.156300, 1, @t1, @t1 + INTERVAL 20 MINUTE, 2000, 9400, 13300, 'MATCHED', @t1 - INTERVAL 30 MINUTE),
    ( 2, 5,  '선문대학교 정문 앞', 36.799000, 127.075000, '천안종합버스터미널 앞', 36.819700, 127.156300, 1, @t1, @t1 + INTERVAL 30 MINUTE, 2000, 9400, 13300, 'MATCHED', @t1 - INTERVAL 28 MINUTE),
    ( 3, 3,  '선문대 기숙사 앞',   36.800500, 127.077000, '천안종합버스터미널 앞', 36.819700, 127.156300, 1, @t1, @t1 + INTERVAL 20 MINUTE, 2000, 9200, 13100, 'MATCHED', @t1 - INTERVAL 25 MINUTE),
    ( 4, 4,  '선문대 기숙사 앞',   36.800500, 127.077000, '천안역 동부광장 앞',   36.809500, 127.146200, 1, @t1, @t1 + INTERVAL 25 MINUTE, 2500, 7500, 10700, 'MATCHED', @t1 - INTERVAL 20 MINUTE),
    -- 매칭 2 (완료) : 남성 4인
    ( 5, 1,  '선문대학교 정문 앞', 36.799000, 127.075000, '천안종합버스터미널 앞', 36.819700, 127.156300, 1, @t2, @t2 + INTERVAL 20 MINUTE, 2000, 9400, 13300, 'MATCHED', @t2 - INTERVAL 30 MINUTE),
    ( 6, 2,  '선문대 기숙사 앞',   36.800500, 127.077000, '천안종합버스터미널 앞', 36.819700, 127.156300, 1, @t2, @t2 + INTERVAL 20 MINUTE, 2000, 9200, 13100, 'MATCHED', @t2 - INTERVAL 27 MINUTE),
    ( 7, 7,  '선문대학교 정문 앞', 36.799000, 127.075000, '천안종합버스터미널 앞', 36.819700, 127.156300, 1, @t2, @t2 + INTERVAL 30 MINUTE, 2000, 9400, 13300, 'MATCHED', @t2 - INTERVAL 24 MINUTE),
    ( 8, 8,  '선문대 기숙사 앞',   36.800500, 127.077000, '천안종합버스터미널 앞', 36.819700, 127.156300, 1, @t2, @t2 + INTERVAL 20 MINUTE, 2000, 9200, 13100, 'MATCHED', @t2 - INTERVAL 20 MINUTE),
    -- 매칭 3 (완료) : 여성 2인, 천안역
    ( 9, 3,  '선문대학교 정문 앞', 36.799000, 127.075000, '천안역 동부광장 앞',   36.809500, 127.146200, 1, @t3, @t3 + INTERVAL 20 MINUTE, 2000, 7600, 10800, 'MATCHED', @t3 - INTERVAL 30 MINUTE),
    (10, 4,  '선문대 기숙사 앞',   36.800500, 127.077000, '천안역 동부광장 앞',   36.809500, 127.146200, 1, @t3, @t3 + INTERVAL 20 MINUTE, 2000, 7500, 10700, 'MATCHED', @t3 - INTERVAL 25 MINUTE),
    -- 매칭 4 (탑승중) : 남성 2인
    (11, 1,  '선문대학교 정문 앞', 36.799000, 127.075000, '천안종합버스터미널 앞', 36.819700, 127.156300, 1, @t4, @t4 + INTERVAL 10 MINUTE, 2000, 9400, 13300, 'MATCHED', @t4 - INTERVAL 15 MINUTE),
    (12, 7,  '선문대 기숙사 앞',   36.800500, 127.077000, '천안종합버스터미널 앞', 36.819700, 127.156300, 1, @t4, @t4 + INTERVAL 10 MINUTE, 2000, 9200, 13100, 'MATCHED', @t4 - INTERVAL 12 MINUTE),
    -- 매칭 5 (완료) : 남성 4인
    (13, 1,  '선문대학교 정문 앞', 36.799000, 127.075000, '천안종합버스터미널 앞', 36.819700, 127.156300, 1, @t5, @t5 + INTERVAL 20 MINUTE, 2000, 9400, 13300, 'MATCHED', @t5 - INTERVAL 30 MINUTE),
    (14, 7,  '선문대학교 정문 앞', 36.799000, 127.075000, '천안종합버스터미널 앞', 36.819700, 127.156300, 1, @t5, @t5 + INTERVAL 20 MINUTE, 2000, 9400, 13300, 'MATCHED', @t5 - INTERVAL 27 MINUTE),
    (15, 8,  '선문대 기숙사 앞',   36.800500, 127.077000, '천안종합버스터미널 앞', 36.819700, 127.156300, 1, @t5, @t5 + INTERVAL 30 MINUTE, 2000, 9200, 13100, 'MATCHED', @t5 - INTERVAL 24 MINUTE),
    (16, 10, '선문대 기숙사 앞',   36.800500, 127.077000, '천안종합버스터미널 앞', 36.819700, 127.156300, 1, @t5, @t5 + INTERVAL 20 MINUTE, 2000, 9200, 13100, 'MATCHED', @t5 - INTERVAL 20 MINUTE),
    -- 매칭 6 (완료) : 여성 3인
    (17, 6,  '선문대학교 정문 앞', 36.799000, 127.075000, '천안종합버스터미널 앞', 36.819700, 127.156300, 1, @t6, @t6 + INTERVAL 20 MINUTE, 2000, 9400, 13300, 'MATCHED', @t6 - INTERVAL 30 MINUTE),
    (18, 5,  '선문대 기숙사 앞',   36.800500, 127.077000, '천안종합버스터미널 앞', 36.819700, 127.156300, 1, @t6, @t6 + INTERVAL 20 MINUTE, 2000, 9200, 13100, 'MATCHED', @t6 - INTERVAL 26 MINUTE),
    (19, 3,  '선문대학교 정문 앞', 36.799000, 127.075000, '천안종합버스터미널 앞', 36.819700, 127.156300, 1, @t6, @t6 + INTERVAL 25 MINUTE, 2000, 9400, 13300, 'MATCHED', @t6 - INTERVAL 22 MINUTE),
    -- 대기중 : 출발지·목적지·시간·조건이 완전히 같고 평점만 다르다 (매칭 점수 비교용)
    (20, 6,  '선문대학교 정문 앞', 36.799000, 127.075000, '천안종합버스터미널 앞', 36.819700, 127.156300, 1, @tw, @tw + INTERVAL 6 HOUR, 2000, 9400, 13300, 'WAITING', NOW()),   -- 이예빈 (F)
    (21, 5,  '선문대학교 정문 앞', 36.799000, 127.075000, '천안종합버스터미널 앞', 36.819700, 127.156300, 1, @tw, @tw + INTERVAL 6 HOUR, 2000, 9400, 13300, 'WAITING', NOW()),   -- 김가영 (F)
    (22, 9,  '선문대학교 정문 앞', 36.799000, 127.075000, '천안종합버스터미널 앞', 36.819700, 127.156300, 1, @tw, @tw + INTERVAL 6 HOUR, 2000, 9400, 13300, 'WAITING', NOW()),   -- 윤서연 (F)
    (23, 7,  '선문대학교 정문 앞', 36.799000, 127.075000, '천안종합버스터미널 앞', 36.819700, 127.156300, 1, @tw, @tw + INTERVAL 6 HOUR, 2000, 9400, 13300, 'WAITING', NOW()),   -- 최민준 (M)
    (24, 8,  '선문대학교 정문 앞', 36.799000, 127.075000, '천안종합버스터미널 앞', 36.819700, 127.156300, 1, @tw, @tw + INTERVAL 6 HOUR, 2000, 9400, 13300, 'WAITING', NOW()),   -- 한지훈 (M)
    -- 대기중 : 진경은 (신고로 정지되면 자동 취소되는지 확인용. 4시간 뒤 출발이라 A 테스트 후보에는 안 나온다)
    (25, 4,  '선문대 기숙사 앞',   36.800500, 127.077000, '천안아산역 앞',        36.794600, 127.104600, 1, NOW() + INTERVAL 4 HOUR, NOW() + INTERVAL 6 HOUR, 2000, 3400, 5800, 'WAITING', NOW());


-- =====================================================================
-- 4. 매칭 6건 + 참여자 (분담 금액의 합 = 총 요금)
--    분담 금액은 거리 비례: 각자 탄 거리(own_distance_m) 비율로 총 요금을 나눔
-- =====================================================================
INSERT INTO ride_match (match_id, status, match_score, total_distance_m, total_fare, created_at, completed_at) VALUES
    (1, 'COMPLETED', 81.20, 10300, 13800, @t1 - INTERVAL 10 MINUTE, @t1 + INTERVAL 28 MINUTE),
    (2, 'COMPLETED', 79.60, 10600, 14200, @t2 - INTERVAL 10 MINUTE, @t2 + INTERVAL 30 MINUTE),
    (3, 'COMPLETED', 88.40,  8100, 11600, @t3 - INTERVAL 10 MINUTE, @t3 + INTERVAL 22 MINUTE),
    (4, 'CONFIRMED', 90.10,  9700, 11400, @t4 - INTERVAL 10 MINUTE, NULL),
    (5, 'COMPLETED', 83.70, 10400, 14000, @t5 - INTERVAL 10 MINUTE, @t5 + INTERVAL 30 MINUTE),
    (6, 'COMPLETED', 85.30, 10000, 12900, @t6 - INTERVAL 10 MINUTE, @t6 + INTERVAL 27 MINUTE);

INSERT INTO match_member (match_id, request_id, member_id, pickup_order, dropoff_order,
                          own_distance_m, detour_m, match_score, fare_share, response) VALUES
    -- 매칭 1 (여성) : 13,800원 = 3,200 + 3,400 + 3,500 + 3,700
    (1,  4, 4,  2, 1, 7600, 100, 76.00, 3200, 'ACCEPTED'),   -- 진경은 (천안역에서 먼저 내림)
    (1,  3, 3,  1, 2, 8000, 0,   84.50, 3400, 'ACCEPTED'),   -- 이토 미나미
    (1,  2, 5,  3, 3, 8300, 300, 80.20, 3500, 'ACCEPTED'),   -- 김가영
    (1,  1, 6,  4, 4, 8800, 500, 82.00, 3700, 'ACCEPTED'),   -- 이예빈
    -- 매칭 2 (남성) : 14,200원 = 3,300 + 3,500 + 3,600 + 3,800
    (2,  6, 2,  1, 1, 8200, 0,   78.00, 3300, 'ACCEPTED'),   -- 정원이
    (2,  8, 8,  2, 2, 8700, 200, 79.10, 3500, 'ACCEPTED'),   -- 한지훈
    (2,  5, 1,  3, 3, 9000, 300, 80.30, 3600, 'ACCEPTED'),   -- 노건호
    (2,  7, 7,  4, 4, 9500, 600, 81.00, 3800, 'ACCEPTED'),   -- 최민준
    -- 매칭 3 (여성) : 11,600원 = 5,600 + 6,000
    (3, 10, 4,  1, 1, 7500, 0,   89.00, 5600, 'ACCEPTED'),   -- 진경은
    (3,  9, 3,  2, 2, 8000, 500, 87.80, 6000, 'ACCEPTED'),   -- 이토 미나미
    -- 매칭 4 (남성, 탑승중) : 11,400원 = 5,500 + 5,900  ← 결제 완료 화면에서 노건호는 5,500원
    (4, 11, 1,  1, 1, 9100, 0,   91.00, 5500, 'ACCEPTED'),   -- 노건호
    (4, 12, 7,  2, 2, 9700, 600, 89.20, 5900, 'ACCEPTED'),   -- 최민준
    -- 매칭 5 (남성) : 14,000원 = 3,300 + 3,400 + 3,600 + 3,700
    (5, 16, 10, 1, 1, 8300, 0,   82.00, 3300, 'ACCEPTED'),   -- 강도현 (이후 탈퇴)
    (5, 15, 8,  2, 2, 8600, 100, 83.10, 3400, 'ACCEPTED'),   -- 한지훈
    (5, 13, 1,  3, 3, 9000, 300, 84.20, 3600, 'ACCEPTED'),   -- 노건호
    (5, 14, 7,  4, 4, 9300, 500, 85.50, 3700, 'ACCEPTED'),   -- 최민준
    -- 매칭 6 (여성) : 12,900원 = 4,100 + 4,300 + 4,500
    (6, 18, 5,  1, 1, 9000, 0,   84.00, 4100, 'ACCEPTED'),   -- 김가영
    (6, 19, 3,  2, 2, 9400, 200, 85.60, 4300, 'ACCEPTED'),   -- 이토 미나미
    (6, 17, 6,  3, 3, 9900, 500, 86.30, 4500, 'ACCEPTED');   -- 이예빈


-- =====================================================================
-- 5. 평점 29건 (완료된 매칭 1, 2, 3, 5, 6 에서만)
--    결과: 여성 - 이예빈 4.80(5개, 높음) / 김가영 1.60(5개, 낮음) / 이토 미나미 4.50(4개) / 진경은 2.50(2개)
--          남성 - 최민준 4.75(4개, 높음) / 한지훈 1.50(4개, 낮음) / 노건호 4.50(2개) / 정원이 1.33(3개)
--          윤서연·강도현은 평점 없음
-- =====================================================================
INSERT INTO rating (match_id, rater_id, ratee_id, score, created_at) VALUES
    -- 매칭 1 (여성)
    (1, 5, 6, 5, @t1 + INTERVAL 1 HOUR),    -- 김가영 → 이예빈
    (1, 3, 6, 5, @t1 + INTERVAL 1 HOUR),    -- 이토 미나미 → 이예빈
    (1, 4, 6, 5, @t1 + INTERVAL 1 HOUR),    -- 진경은 → 이예빈
    (1, 6, 5, 2, @t1 + INTERVAL 1 HOUR),    -- 이예빈 → 김가영
    (1, 3, 5, 1, @t1 + INTERVAL 1 HOUR),    -- 이토 미나미 → 김가영
    (1, 4, 5, 2, @t1 + INTERVAL 1 HOUR),    -- 진경은 → 김가영
    (1, 6, 3, 5, @t1 + INTERVAL 1 HOUR),    -- 이예빈 → 이토 미나미
    (1, 5, 3, 4, @t1 + INTERVAL 1 HOUR),    -- 김가영 → 이토 미나미
    (1, 3, 4, 2, @t1 + INTERVAL 1 HOUR),    -- 이토 미나미 → 진경은
    -- 매칭 6 (여성)
    (6, 5, 6, 4, @t6 + INTERVAL 1 HOUR),    -- 김가영 → 이예빈
    (6, 3, 6, 5, @t6 + INTERVAL 1 HOUR),    -- 이토 미나미 → 이예빈
    (6, 6, 5, 1, @t6 + INTERVAL 1 HOUR),    -- 이예빈 → 김가영
    (6, 3, 5, 2, @t6 + INTERVAL 1 HOUR),    -- 이토 미나미 → 김가영
    (6, 6, 3, 4, @t6 + INTERVAL 1 HOUR),    -- 이예빈 → 이토 미나미
    -- 매칭 3 (여성)
    (3, 3, 4, 3, @t3 + INTERVAL 1 HOUR),    -- 이토 미나미 → 진경은
    (3, 4, 3, 5, @t3 + INTERVAL 1 HOUR),    -- 진경은 → 이토 미나미
    -- 매칭 2 (남성)
    (2, 1, 2, 1, @t2 + INTERVAL 1 HOUR),    -- 노건호 → 정원이
    (2, 7, 2, 1, @t2 + INTERVAL 1 HOUR),    -- 최민준 → 정원이
    (2, 8, 2, 2, @t2 + INTERVAL 1 HOUR),    -- 한지훈 → 정원이
    (2, 2, 1, 4, @t2 + INTERVAL 1 HOUR),    -- 정원이 → 노건호
    (2, 1, 8, 2, @t2 + INTERVAL 1 HOUR),    -- 노건호 → 한지훈
    (2, 7, 8, 1, @t2 + INTERVAL 1 HOUR),    -- 최민준 → 한지훈
    (2, 1, 7, 5, @t2 + INTERVAL 1 HOUR),    -- 노건호 → 최민준
    -- 매칭 5 (남성)
    (5, 8, 7, 5, @t5 + INTERVAL 1 HOUR),    -- 한지훈 → 최민준
    (5, 10, 7, 4, @t5 + INTERVAL 1 HOUR),   -- 강도현 → 최민준
    (5, 1, 7, 5, @t5 + INTERVAL 1 HOUR),    -- 노건호 → 최민준
    (5, 10, 8, 2, @t5 + INTERVAL 1 HOUR),   -- 강도현 → 한지훈
    (5, 7, 8, 1, @t5 + INTERVAL 1 HOUR),    -- 최민준 → 한지훈
    (5, 7, 1, 5, @t5 + INTERVAL 1 HOUR);    -- 최민준 → 노건호

-- 평균 평점·개수를 member 에 저장 (RatingDAO.insert() 가 하는 계산과 같음)
UPDATE member m
   SET m.avg_rating   = COALESCE((SELECT ROUND(AVG(score), 2) FROM rating r WHERE r.ratee_id = m.member_id), 0.00),
       m.rating_count = (SELECT COUNT(*) FROM rating r WHERE r.ratee_id = m.member_id)
 WHERE m.member_id BETWEEN 1 AND 10;


-- =====================================================================
-- 6. 신고 8건 (같은 매칭 = 같은 성별끼리만 신고 가능)
--    누적 규칙(ReportPenaltyService): 반려 제외, 같은 신고자는 1명, 서로 다른 3명이면 정지
--      정원이 : 노건호·최민준·한지훈 3명 → SUSPENDED
--      진경은 : 이토 미나미·김가영 2명 → 경고 (1명 더 신고하면 정지)
--      김가영 : 이토 미나미 1명 / 한지훈 : 최민준 1명
--      노건호 : 정원이의 신고 1건이 반려(REJECTED) → 0명
-- =====================================================================
INSERT INTO report (match_id, reporter_id, reported_id, reason, detail, status, created_at, resolved_at) VALUES
    (2, 1, 2, 'ABUSE',           '탑승 내내 큰 소리로 욕설을 했습니다.',            'RESOLVED',  @t2 + INTERVAL 1 HOUR, @t2 + INTERVAL 1 DAY),
    (2, 7, 2, 'NO_SHOW',         '약속 장소에 15분 늦게 나타났습니다.',             'RECEIVED',  @t2 + INTERVAL 1 HOUR, NULL),
    (2, 8, 2, 'FARE_DISPUTE',    '분담 금액을 보내지 않았습니다.',                  'REVIEWING', @t2 + INTERVAL 2 HOUR, NULL),
    (2, 2, 1, 'OTHER',           '신고당한 것에 대한 보복성 신고 (관리자 반려)',    'REJECTED',  @t2 + INTERVAL 3 HOUR, @t2 + INTERVAL 1 DAY),
    (1, 3, 4, 'NO_SHOW',         '출발 시간에 10분 늦었습니다.',                    'RECEIVED',  @t1 + INTERVAL 1 HOUR, NULL),
    (1, 5, 4, 'ABUSE',           '통화를 오래 크게 했습니다.',                      'RECEIVED',  @t1 + INTERVAL 2 HOUR, NULL),
    (1, 3, 5, 'OTHER',           '창문을 계속 열어 두어서 추웠습니다.',             'RECEIVED',  @t1 + INTERVAL 1 HOUR, NULL),
    (5, 7, 8, 'ROUTE_DEVIATION', '자기 목적지로 먼저 가 달라고 계속 요구했습니다.', 'RECEIVED',  @t5 + INTERVAL 1 HOUR, NULL);


-- =====================================================================
-- 7. 확인용 쿼리 (실행 결과 창에서 확인)
-- =====================================================================

-- (1) 회원별 현황. 기대값: 정원이 SUSPENDED·신고자 3, 진경은 2, 김가영·한지훈 1, 강도현 WITHDRAWN, 나머지 0
SELECT m.member_id AS 번호, m.nickname AS 이름, m.email AS 아이디, m.gender AS 성별, m.ride_style AS 동승스타일,
       m.status AS 상태, m.avg_rating AS 평균평점, m.rating_count AS 평점수,
       (SELECT COUNT(DISTINCT r.reporter_id) FROM report r
         WHERE r.reported_id = m.member_id AND r.status <> 'REJECTED') AS 유효신고자수
  FROM member m ORDER BY m.member_id;

-- (2) 요청 상태별 건수. 기대값: MATCHED 19, WAITING 6
SELECT status AS 요청상태, COUNT(*) AS 건수 FROM ride_request GROUP BY status;

-- (3) 분담 금액 합계가 총 요금과 다른 매칭 → 0행이어야 정상
SELECT rm.match_id, rm.total_fare, SUM(mm.fare_share) AS 분담합계
  FROM ride_match rm JOIN match_member mm ON mm.match_id = rm.match_id
 GROUP BY rm.match_id, rm.total_fare
HAVING SUM(mm.fare_share) <> rm.total_fare;

-- (4) 남녀가 섞인 매칭 → 0행이어야 정상 (동성 합승만 허용)
SELECT mm.match_id, COUNT(DISTINCT m.gender) AS 성별종류수
  FROM match_member mm JOIN member m ON m.member_id = mm.member_id
 GROUP BY mm.match_id
HAVING COUNT(DISTINCT m.gender) > 1;
