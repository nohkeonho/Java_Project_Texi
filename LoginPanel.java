package taxi;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class LoginPanel extends JPanel {
    private UIComponents.RoundedTextField idField;
    private UIComponents.RoundedTextField pwField;
    
 // 현재 저장된 테스트 계정 정보 (추후 SignupPanel / 파일 / DB / AuthManager 등으로 교체)
    private static final String SAVED_USER_ID = "admin";
    private static final String SAVED_USER_PW = "1234";

    public LoginPanel(TaxiSharePrototype mainFrame) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(TaxiSharePrototype.BG_WHITE);
        setBorder(new EmptyBorder(40, 28, 30, 28));

        // 상단 원형 타이틀 배너
        JPanel circleBanner = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(TaxiSharePrototype.HERO_CIRCLE_BG);
                int size = 250;
                g2.fillOval((getWidth() - size) / 2, 10, size, size);
                g2.dispose();
            }
        };
        circleBanner.setLayout(new BoxLayout(circleBanner, BoxLayout.Y_AXIS));
        circleBanner.setOpaque(false);
        circleBanner.setPreferredSize(new Dimension(340, 260));
        circleBanner.setMaximumSize(new Dimension(340, 260));
        circleBanner.setAlignmentX(Component.CENTER_ALIGNMENT);

        circleBanner.add(Box.createVerticalStrut(65));
        JLabel title1 = UIComponents.createLabel("오늘 밤,", 24, Font.BOLD, TaxiSharePrototype.TEXT_DARK);
        JLabel title2 = UIComponents.createLabel("같이 타면 반값", 24, Font.BOLD, TaxiSharePrototype.TEXT_DARK);
        JLabel sub1 = UIComponents.createLabel("같은 방향 이웃과", 13, Font.PLAIN, TaxiSharePrototype.TEXT_GRAY);
        JLabel sub2 = UIComponents.createLabel("택시비를 나눠요", 13, Font.PLAIN, TaxiSharePrototype.TEXT_GRAY);

        title1.setAlignmentX(Component.CENTER_ALIGNMENT);
        title2.setAlignmentX(Component.CENTER_ALIGNMENT);
        sub1.setAlignmentX(Component.CENTER_ALIGNMENT);
        sub2.setAlignmentX(Component.CENTER_ALIGNMENT);

        circleBanner.add(title1);
        circleBanner.add(Box.createVerticalStrut(4));
        circleBanner.add(title2);
        circleBanner.add(Box.createVerticalStrut(12));
        circleBanner.add(sub1);
        circleBanner.add(Box.createVerticalStrut(2));
        circleBanner.add(sub2);

        add(circleBanner);
        add(Box.createVerticalStrut(25));

        // 아이디 입력 영역
        JLabel idLabel = UIComponents.createLabel("아이디", 13, Font.BOLD, TaxiSharePrototype.TEXT_GRAY);
        idLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        add(idLabel);
        add(Box.createVerticalStrut(6));

        idField = new UIComponents.RoundedTextField("아이디를 입력하세요");
        idField.setAlignmentX(Component.CENTER_ALIGNMENT);
        add(idField);
        add(Box.createVerticalStrut(16));

        // 비밀번호 입력 영역
        JLabel pwLabel = UIComponents.createLabel("비밀번호", 13, Font.BOLD, TaxiSharePrototype.TEXT_GRAY);
        pwLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        add(pwLabel);
        add(Box.createVerticalStrut(6));

        pwField = new UIComponents.RoundedTextField("비밀번호를 입력하세요");
        pwField.setAlignmentX(Component.CENTER_ALIGNMENT);
        add(pwField);
        add(Box.createVerticalStrut(24));

        // 로그인 버튼 (아이디/비밀번호 공백 체크 유효성 검사)
        UIComponents.RoundedButton loginBtn = new UIComponents.RoundedButton("로그인", TaxiSharePrototype.PRIMARY_BLUE, Color.WHITE);
        loginBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        loginBtn.addActionListener(e -> {
        	// LoginPanel.java 로그인 버튼 리스너 내부
        	String userId = idField.getText().trim();
        	String password = pwField.getText().trim();

        	if (userId.isEmpty()) {
        	    JOptionPane.showMessageDialog(this,
        	            "아이디를 입력해주세요.",
        	            "필수 입력 안내",
        	            JOptionPane.WARNING_MESSAGE);
        	    idField.requestFocus();
        	    return;
        	}

        	if (password.isEmpty()) {
        	    JOptionPane.showMessageDialog(this,
        	            "비밀번호를 입력해주세요.",
        	            "필수 입력 안내",
        	            JOptionPane.WARNING_MESSAGE);
        	    pwField.requestFocus();
        	    return;
        	}

        	if (!mainFrame.verifyCredentials(userId, password)) {
        	    JOptionPane.showMessageDialog(
        	            this,
        	            "아이디 또는 비밀번호가 일치하지 않습니다.\n회원가입한 정보를 다시 확인해주세요.",
        	            "로그인 실패",
        	            JOptionPane.ERROR_MESSAGE
        	    );
        	    pwField.setText("");
        	    pwField.requestFocus();
        	    return;
        	}

        	// 로그인 성공 -> 메인 허브(HOME)로 이동
        	mainFrame.setLoggedIn(true);
        	mainFrame.showScreen("HOME");
        });
        add(loginBtn);
        add(Box.createVerticalStrut(16));

        // 하단 회원가입 링크
        JPanel bottomLinkPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        bottomLinkPanel.setOpaque(false);
        bottomLinkPanel.setMaximumSize(new Dimension(340, 30));
        bottomLinkPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        bottomLinkPanel.add(UIComponents.createLabel("아직 계정이 없나요?", 12, Font.PLAIN, TaxiSharePrototype.TEXT_GRAY));

        JLabel signupLink = UIComponents.createLabel("회원가입", 12, Font.BOLD, TaxiSharePrototype.PRIMARY_BLUE);
        signupLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        signupLink.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                mainFrame.showScreen("SIGNUP");
            }
        });
        bottomLinkPanel.add(signupLink);
        add(bottomLinkPanel);
    }
}