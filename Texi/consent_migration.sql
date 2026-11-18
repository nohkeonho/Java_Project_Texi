-- =====================================================================
-- 동의 기능용 DB 변경 (기존 schema.sql 은 그대로 두고, 이 파일만 한 번 실행)
-- =====================================================================
-- member 테이블에 "개인정보 수집·이용 동의 시각" 컬럼을 추가한다.
--  * NULL 허용으로 추가하므로, 기존 회원가입·로그인 SQL 은 수정 없이 그대로 동작한다.
--    (INSERT 할 때 이 컬럼을 안 써도 자동으로 NULL 이 들어간다)
--  * 위치정보 동의 시각(location_agreed_at)은 원래 스키마에 이미 있다.
--  * 기존 데이터는 지워지지 않는다.
-- =====================================================================

USE taxi_share;

ALTER TABLE member
    ADD COLUMN privacy_agreed_at DATETIME NULL COMMENT '개인정보 수집·이용 동의 시각 (NULL이면 미동의)'
    AFTER gender;

-- 확인용: 가입 테스트 후 동의 시각이 들어갔는지 보기
-- SELECT member_id, email, privacy_agreed_at, location_agreed_at FROM member;
