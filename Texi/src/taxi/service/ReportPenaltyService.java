package taxi.service;

import java.sql.SQLException;

import taxi.dao.ReportPenaltyDAO;
import taxi.model.PenaltyResult;

/**
 * 신고 누적 처리 정책을 담당하는 클래스.
 *
 * [정책]  (계획서: "신고, 평점 시스템 운용으로 안전성 확보")
 *   - 서로 다른 동승자 3명 이상에게 신고받으면 이용정지(SUSPENDED)
 *   - 2명에게 신고받은 상태면 "경고" (1명만 더 신고하면 정지)
 *   - 반려(REJECTED)된 신고는 세지 않고, 같은 사람의 신고는 여러 번이어도 1명으로 센다.
 *   - 관리자가 신고를 반려해서 기준 아래로 내려가면 정지가 자동으로 풀린다.
 *
 * 정지되면
 *   - 로그인 불가 (AuthService.login 이 이미 SUSPENDED 를 막고 있음)
 *   - 새 합승 요청 불가 (RideRequestDAO.insert 가 ACTIVE 만 허용)
 *   - 다른 사람의 매칭 후보에서 빠짐 (RideRequestDAO.findCandidates 가 ACTIVE 만 검색)
 *   - 아직 출발 전인 대기 요청·제안됨 매칭은 자동 취소 (ReportPenaltyDAO)
 *
 * 기준을 바꾸고 싶으면 SUSPEND_THRESHOLD 숫자만 바꾸면 된다.
 */
public class ReportPenaltyService {

    /** 이용정지 기준: 서로 다른 신고자 수 */
    public static final int SUSPEND_THRESHOLD = 3;

    private final ReportPenaltyDAO penaltyDAO = new ReportPenaltyDAO();

    /**
     * 신고가 새로 들어왔거나 처리 상태가 바뀐 뒤에 호출한다.
     * 신고 수를 다시 세서 정지 / 정지 해제를 DB 에 반영한다.
     * 여러 번 불러도 결과가 같다. (이미 정지된 사람을 또 정지하지 않음)
     *
     * @throws IllegalArgumentException 존재하지 않는 회원일 때
     */
    public PenaltyResult evaluate(long memberId) throws SQLException {
        return penaltyDAO.apply(memberId, SUSPEND_THRESHOLD);
    }

    /**
     * 상태를 바꾸지 않고 현재 신고 누적 상황만 조회한다.
     * 예) 로그인 직후:
     *     PenaltyResult p = penaltyService.preview(me.getMemberId());
     *     if (p != null && p.getMessage() != null) JOptionPane.showMessageDialog(this, p.getMessage());
     *
     * @return 존재하지 않는 회원이면 null
     */
    public PenaltyResult preview(long memberId) throws SQLException {
        return penaltyDAO.preview(memberId, SUSPEND_THRESHOLD);
    }
}
