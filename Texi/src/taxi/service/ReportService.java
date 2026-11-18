package taxi.service;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

import taxi.dao.ReportDAO;
import taxi.model.Report;

/**
 * 신고 기능의 "창구" 역할을 하는 클래스.
 * 화면(Swing)은 이 클래스의 submit 만 부르면 되고, 실제 SQL 은 기존 ReportDAO 가 실행한다.
 */
public class ReportService {

    // 너무 긴 글이 들어오는 것을 막기 위한 글자 수 제한 (필요하면 숫자만 바꾸면 된다)
    private static final int MAX_DETAIL_LENGTH = 1000;

    private final ReportDAO reportDAO = new ReportDAO();

    /**
     * 신고 제출 (사유를 따로 고르지 않는 화면용).
     * report 테이블의 reason 컬럼은 비워 둘 수 없어서(NOT NULL) "기타(OTHER)"로 저장한다.
     *
     * @param detail 신고 내용. 아무것도 쓰지 않았으면 null 이나 빈 문자열을 그대로 넘기면 된다.
     * @return DB 에 저장했으면 true, 내용이 없어서 건너뛰었으면 false
     */
    public boolean submit(long matchId, long reporterId, long reportedId, String detail) throws SQLException {
        return submit(matchId, reporterId, reportedId, Report.Reason.OTHER, detail);
    }

    /**
     * 신고 제출 (나중에 화면에 사유 선택 칸을 추가했을 때 쓰는 버전).
     *
     * @throws IllegalArgumentException 내용이 너무 길거나 자기 자신을 신고하려 할 때
     * @throws IllegalStateException    이미 신고했거나, 완료된 합승의 동승자가 아닐 때
     * @throws SQLException             DB 연결 실패 등 DB 쪽 문제가 생겼을 때
     */
    public boolean submit(long matchId, long reporterId, long reportedId, Report.Reason reason, String detail)
            throws SQLException {
        // 1) 신고 내용이 없으면 아무것도 하지 않고 넘어간다.
        //    isBlank() : 비어 있거나 공백(스페이스, 엔터)만 있으면 true
        if (detail == null || detail.isBlank()) {
            return false;
        }
        // 2) 앞뒤 공백을 지우고 길이를 검사한다.
        String trimmed = detail.trim();
        if (trimmed.length() > MAX_DETAIL_LENGTH) {
            throw new IllegalArgumentException("신고 내용은 " + MAX_DETAIL_LENGTH + "자 이하여야 합니다.");
        }

        // 3) 기존 모델 클래스(Report)에 값을 담아 DAO 에 넘긴다.
        Report report = new Report();
        report.setMatchId(matchId);
        report.setReporterId(reporterId);
        report.setReportedId(reportedId);
        report.setReason(reason == null ? Report.Reason.OTHER : reason);
        report.setDetail(trimmed);

        try {
            reportDAO.insert(report);
        } catch (SQLIntegrityConstraintViolationException e) {
            // 같은 합승에서 같은 사람을 두 번 신고하면 DB 의 UNIQUE 제약에 걸린다.
            // SQL 오류 메시지 대신 알아보기 쉬운 메시지로 바꿔서 던진다.
            throw new IllegalStateException("이미 이 동승자를 신고했습니다.", e);
        }
        return true;
    }
}
