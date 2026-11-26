package taxi;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class PaymentCompletePanel extends JPanel {
    private int currentRating = 0; // 기본 별점 0점 고정
    private JLabel[] stars = new JLabel[5];
    private JLabel summaryLabel;
    private TaxiSharePrototype mainFrameRef;

    public PaymentCompletePanel(TaxiSharePrototype mainFrame) {
        this.mainFrameRef = mainFrame;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(TaxiSharePrototype.BG_WHITE);
        setBorder(new EmptyBorder(65, 28, 30, 28));

        JPanel checkIconPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int size = 68;
                int x = (getWidth() - size) / 2;
                g2.setColor(TaxiSharePrototype.PRIMARY_BLUE);
                g2.fillOval(x, 5, size, size);

                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(x + 20, 40, x + 31, 51);
                g2.drawLine(x + 31, 51, x + 49, 29);
                g2.dispose();
            }
        };
        checkIconPanel.setOpaque(false);
        checkIconPanel.setMaximumSize(new Dimension(350, 80));
        checkIconPanel.setPreferredSize(new Dimension(350, 80));
        add(checkIconPanel);
        add(Box.createVerticalStrut(18));

        JLabel title = UIComponents.createLabel("결제되었습니다", 24, Font.BOLD, TaxiSharePrototype.TEXT_DARK);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        add(title);
        add(Box.createVerticalStrut(8));

        summaryLabel = UIComponents.createLabel("", 13, Font.PLAIN, TaxiSharePrototype.TEXT_GRAY);
        summaryLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        add(summaryLabel);
        add(Box.createVerticalStrut(42));

        JLabel question = UIComponents.createLabel("이번 동승은 어떠셨나요?", 15, Font.BOLD, TaxiSharePrototype.TEXT_DARK);
        question.setAlignmentX(Component.CENTER_ALIGNMENT);
        add(question);
        add(Box.createVerticalStrut(14));

        JPanel starPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        starPanel.setOpaque(false);
        starPanel.setMaximumSize(new Dimension(350, 45));
        starPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        for (int i = 0; i < 5; i++) {
            JLabel star = new JLabel("☆");
            star.setFont(new Font("SansSerif", Font.PLAIN, 34));
            star.setForeground(TaxiSharePrototype.TEXT_GRAY);
            stars[i] = star;
            starPanel.add(star);
        }

        // =========================================================================
        // 클릭 및 드래그(Drag & Slide)로 별점을 자유롭게 선택할 수 있는 마우스 리스너
        // =========================================================================
        MouseAdapter starDragAdapter = new MouseAdapter() {
            private void updateRating(MouseEvent e) {
                Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), starPanel);
                int calculatedRating = 0;
                
                for (int i = 0; i < 5; i++) {
                    Rectangle bounds = stars[i].getBounds();
                    // 마우스 X 좌표가 해당 별의 위치에 걸치거나 우측에 있으면 해당 별점 이상으로 산정
                    if (p.x >= bounds.x - 5) {
                        calculatedRating = i + 1;
                    }
                }
                // 맨 왼쪽 밖으로 나가면 0점 처리
                if (p.x < stars[0].getBounds().x) {
                    calculatedRating = 0;
                }
                setRating(calculatedRating);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                updateRating(e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                updateRating(e);
            }
        };

        starPanel.addMouseListener(starDragAdapter);
        starPanel.addMouseMotionListener(starDragAdapter);

        add(starPanel);
        add(Box.createVerticalStrut(8));

        JLabel starHint = UIComponents.createLabel("별을 누르거나 드래그하여 평가해 주세요", 12, Font.PLAIN, TaxiSharePrototype.TEXT_GRAY);
        starHint.setAlignmentX(Component.CENTER_ALIGNMENT);
        add(starHint);
        add(Box.createVerticalStrut(26));

        Icon sirenIcon = new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(TaxiSharePrototype.DISCOUNT_RED);
                g2.fillArc(x + 2, y + 4, 16, 16, 0, 180);
                g2.setColor(new Color(160, 160, 160));
                g2.fillRoundRect(x, y + 12, 20, 5, 3, 3);
                g2.dispose();
            }
            @Override
            public int getIconWidth() { return 20; }
            @Override
            public int getIconHeight() { return 18; }
        };

        JButton reportBtn = new JButton("신고하기", sirenIcon) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(TaxiSharePrototype.BG_WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 40, 40);
                g2.setColor(TaxiSharePrototype.BORDER_GRAY);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 40, 40);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        reportBtn.setFont(new Font("맑은 고딕", Font.BOLD, 13));
        reportBtn.setForeground(TaxiSharePrototype.DISCOUNT_RED);
        reportBtn.setIconTextGap(8);
        reportBtn.setHorizontalTextPosition(SwingConstants.RIGHT);
        reportBtn.setContentAreaFilled(false);
        reportBtn.setBorderPainted(false);
        reportBtn.setFocusPainted(false);
        reportBtn.setMaximumSize(new Dimension(155, 42));
        reportBtn.setPreferredSize(new Dimension(155, 42));
        reportBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        reportBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        reportBtn.addActionListener(e -> openReportDialog());
        add(reportBtn);

        add(Box.createVerticalGlue());

        UIComponents.RoundedButton doneBtn = new UIComponents.RoundedButton("완료", TaxiSharePrototype.PRIMARY_BLUE, Color.WHITE);
        doneBtn.addActionListener(e -> {
            JOptionPane.showMessageDialog(this, "이용해 주셔서 감사합니다! (" + currentRating + "점 평가 완료)");
            setRating(0);
            mainFrame.showScreen("HOME");
        });
        add(doneBtn);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (mainFrameRef != null) {
            String origin = mainFrameRef.getCurrentOrigin();
            String dest = mainFrameRef.getCurrentDestination();
            int fare = mainFrameRef.getCurrentPaidFare();
            summaryLabel.setText(String.format("%,d원 · %s → %s", fare, origin, dest));
        }
    }
    
    private void openReportDialog() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner, "동승자 신고", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setSize(340, 360);
        dialog.setLocationRelativeTo(this);
        dialog.setResizable(false);

        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(TaxiSharePrototype.BG_WHITE);
        root.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel titleLbl = UIComponents.createLabel("신고 사유 입력", 18, Font.BOLD, TaxiSharePrototype.TEXT_DARK);
        titleLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(titleLbl);
        root.add(Box.createVerticalStrut(6));

        String tripInfo = (mainFrameRef != null)
                ? mainFrameRef.getCurrentOrigin() + " → " + mainFrameRef.getCurrentDestination()
                : "동승 내역";
        JLabel subLbl = UIComponents.createLabel("접수된 내용은 운영팀에 전달됩니다.", 11, Font.PLAIN, TaxiSharePrototype.TEXT_GRAY);
        subLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(subLbl);
        root.add(Box.createVerticalStrut(14));

        // 빠른 선택용 사유 콤보박스
        String[] presets = {
                "직접 입력",
                "노쇼 / 약속 시간 미준수",
                "경로 무단 변경 또는 하차 요구",
                "불쾌한 언행 및 매너 부족",
                "요금 정산 분쟁"
        };
        JComboBox<String> reasonCombo = new JComboBox<>(presets);
        reasonCombo.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        reasonCombo.setBackground(Color.WHITE);
        reasonCombo.setMaximumSize(new Dimension(300, 34));
        reasonCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(reasonCombo);
        root.add(Box.createVerticalStrut(10));

        // 상세 신고 사유 입력란
        JTextArea detailArea = new JTextArea(4, 20);
        detailArea.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        detailArea.setLineWrap(true);
        detailArea.setWrapStyleWord(true);
        detailArea.setBorder(new EmptyBorder(8, 10, 8, 10));

        JScrollPane scrollPane = new JScrollPane(detailArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(TaxiSharePrototype.BORDER_GRAY, 1, true));
        scrollPane.setMaximumSize(new Dimension(300, 105));
        scrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(scrollPane);
        root.add(Box.createVerticalStrut(16));

        // 콤보박스 선택 시 기본 문구 채우기
        reasonCombo.addActionListener(e -> {
            String selected = (String) reasonCombo.getSelectedItem();
            if (selected != null && !"직접 입력".equals(selected) && detailArea.getText().trim().isEmpty()) {
                detailArea.setText(selected);
            }
        });

        // 하단 취소 / 제출 버튼
        JPanel btnRow = new JPanel(new GridLayout(1, 2, 10, 0));
        btnRow.setOpaque(false);
        btnRow.setMaximumSize(new Dimension(300, 42));
        btnRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton cancelBtn = new JButton("취소");
        cancelBtn.setFont(new Font("맑은 고딕", Font.BOLD, 13));
        cancelBtn.setBackground(new Color(240, 240, 240));
        cancelBtn.setForeground(TaxiSharePrototype.TEXT_DARK);
        cancelBtn.setFocusPainted(false);
        cancelBtn.setBorderPainted(false);
        cancelBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cancelBtn.addActionListener(e -> dialog.dispose());

        JButton submitBtn = new JButton("신고 제출");
        submitBtn.setFont(new Font("맑은 고딕", Font.BOLD, 13));
        submitBtn.setBackground(TaxiSharePrototype.PRIMARY_BLUE);
        submitBtn.setForeground(Color.WHITE);
        submitBtn.setFocusPainted(false);
        submitBtn.setBorderPainted(false);
        submitBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        submitBtn.addActionListener(e -> {
            String reasonText = detailArea.getText().trim();
            if (reasonText.isEmpty()) {
                JOptionPane.showMessageDialog(dialog,
                        "신고 사유를 입력해주세요.",
                        "입력 안내",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            JOptionPane.showMessageDialog(dialog,
                    "신고가 정상적으로 접수되었습니다.",
                    "접수 완료",
                    JOptionPane.INFORMATION_MESSAGE);
            dialog.dispose();
            
            setRating(0);
            if (mainFrameRef != null) {
                mainFrameRef.showScreen("HOME");
            }
        });

        btnRow.add(cancelBtn);
        btnRow.add(submitBtn);
        root.add(btnRow);

        dialog.setContentPane(root);
        dialog.setVisible(true);
    }

    private void setRating(int rating) {
        this.currentRating = rating;
        for (int i = 0; i < 5; i++) {
            if (i < rating) {
                stars[i].setText("★");
                stars[i].setForeground(new Color(255, 193, 7));
            } else {
                stars[i].setText("☆");
                stars[i].setForeground(TaxiSharePrototype.TEXT_GRAY);
            }
        }
    }
}