-- =====================================================================
-- 동성 합승만 허용하도록 바꾸는 DB 변경 (이미 DB 를 만들어 둔 사람만 한 번 실행)
-- =====================================================================
-- 실행 순서: schema.sql → consent_migration.sql → ride_migration.sql → 이 파일
-- (integration_seed.sql 은 DB 를 새로 만들면서 이 변경까지 이미 반영되어 있으므로 이 파일이 필요 없다)
--
-- 바뀌는 점
--  * ride_request.same_gender_only 컬럼 삭제
--    - 예전에는 요청마다 "동성만(1) / 혼성 허용(0)" 을 고를 수 있었다.
--    - 이제는 항상 동성끼리만 매칭하므로 고를 필요가 없어졌다.
--    - 성별은 member.gender 로 비교한다. (RideRequestDAO.findCandidates, RideMatchDAO.createMatch)
--  * 기존 데이터 중 남녀가 섞인 매칭이 있는지 확인하는 쿼리 제공 (자동으로 지우지는 않는다)
-- =====================================================================

USE taxi_share;

ALTER TABLE ride_request DROP COLUMN same_gender_only;

-- 확인용 1: 컬럼이 사라졌는지 (same_gender_only 가 목록에 없으면 정상)
-- SHOW COLUMNS FROM ride_request;

-- 확인용 2: 예전 데이터 중 남녀가 섞인 매칭 → 0행이면 정상.
--           결과가 나오면 테스트 데이터이므로 integration_seed.sql 로 DB 를 다시 만드는 것을 권장
SELECT mm.match_id, COUNT(DISTINCT m.gender) AS 성별종류수
  FROM match_member mm
  JOIN member m ON m.member_id = mm.member_id
 GROUP BY mm.match_id
HAVING COUNT(DISTINCT m.gender) > 1;
