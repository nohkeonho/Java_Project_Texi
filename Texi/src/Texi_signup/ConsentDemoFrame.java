package Texi_signup;

import java.awt.BorderLayout;
import java.awt.Color;
import java.sql.SQLException;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * [테스트용 임시 화면] 동의 체크 → 경고 표시 → (가입 후) DB 저장 흐름 확인용.
 *
 * ★1 ~ ★3 이 진짜 회원가입 화면에 옮겨 넣을 코드다.
 * 여기서는 기존 회원가입 API 대신 fakeExistingSignup() 을 부른다.
 * 진짜 화면이 완성되면 이 파일은 지워도 된다.
 */
public class ConsentDemoFrame extends JFrame {

    public ConsentDemoFrame() {
        super("동의 처리 테스트 (임시 화면)");

        // ★1) 동의 체크박스 + 경고 문구 라벨 (GUI 팀이 만든 것으로 바꾸면 됨)
        JCheckBox privacyCheckBox = new JCheckBox("개인정보 수집·이용에 동의합니다. (필수)");
        JCheckBox locationCheckBox = new JCheckBox("위치정보 수집·이용에 동의합니다. (필수)");
        JLabel consentErrorLabel = new JLabel(" ");
        consentErrorLabel.setForeground(Color.RED);
        Color normalColor = privacyCheckBox.getForeground();

        // ★2) 동의가 빠졌을 때 화면 표시 방법
        ConsentErrorHandler consentHandler = new ConsentErrorHandler() {
            @Override
            public void showConsentError(ConsentCheckResult r) {
                privacyCheckBox.setForeground(r.isPrivacyMissing() ? Color.RED : normalColor);
                locationCheckBox.setForeground(r.isLocationMissing() ? Color.RED : normalColor);
                consentErrorLabel.setText(r.getMessage());
            }

            @Override
            public void clearConsentError() {
                privacyCheckBox.setForeground(normalColor);
                locationCheckBox.setForeground(normalColor);
                consentErrorLabel.setText(" ");
            }
        };

        JButton submitBtn = new JButton("가입하기");

        // ★3) 가입 버튼: 기존 회원가입 API 호출 앞뒤로 두 줄만 추가
        submitBtn.addActionListener(e -> {
            boolean p = privacyCheckBox.isSelected();
            boolean l = locationCheckBox.isSelected();

            if (!ConsentService.beforeSignup(p, l, consentHandler)) {
                return;                                   // 미동의 → 가입 차단 (기존 API 호출 안 함)
            }

            long memberId = fakeExistingSignup();         // ← 여기에 기존 회원가입 API 호출

            try {
                ConsentService.afterSignup(memberId, p, l);   // 동의 시각 DB 저장
                JOptionPane.showMessageDialog(this, "가입 및 동의 저장 완료 (회원 번호 " + memberId + ")");
            } catch (SQLException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "동의 정보 저장에 실패했습니다.\n" + ex.getMessage(),
                        "오류", JOptionPane.ERROR_MESSAGE);
            }
        });

        JPanel consentArea = new JPanel();
        consentArea.setLayout(new BoxLayout(consentArea, BoxLayout.Y_AXIS));
        consentArea.add(privacyCheckBox);
        consentArea.add(locationCheckBox);
        consentArea.add(consentErrorLabel);

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(consentArea, BorderLayout.CENTER);
        content.add(submitBtn, BorderLayout.SOUTH);

        setContentPane(content);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        pack();
        setLocationRelativeTo(null);
    }

    /**
     * 기존 회원가입 API 를 흉내 내는 가짜 메서드.
     * DB 에 실제로 있는 회원 번호를 돌려줘야 afterSignup 저장이 성공한다. (테스트할 때 숫자를 바꿔 볼 것)
     */
    private long fakeExistingSignup() {
        return 1;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ConsentDemoFrame().setVisible(true));
    }
}
