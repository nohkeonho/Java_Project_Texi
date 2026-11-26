package taxi;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.GeneralPath;

public class MatchingResultPanel extends JPanel {
    private JLabel statusTitleLabel;
    private JLabel statusDescLabel;
    private JLabel tripSummaryLabel;
    private JLabel partnerNameLabel;
    private JLabel partnerStyleLabel;
    private JPanel partnerInfoCard;
    private UIComponents.RoundedButton actionBtn;
    private TaxiSharePrototype mainFrameRef;

    private boolean isMatched = false;
    private Timer matchTimer;

    public MatchingResultPanel(TaxiSharePrototype mainFrame) {
        this.mainFrameRef = mainFrame;
        setLayout(new BorderLayout());
        setBackground(TaxiSharePrototype.BG_WHITE);

        MatchingMapCanvas mapCanvas = new MatchingMapCanvas();
        mapCanvas.setLayout(null);
        add(mapCanvas, BorderLayout.CENTER);

        JPanel bottomSheet = new JPanel();
        bottomSheet.setLayout(new BoxLayout(bottomSheet, BoxLayout.Y_AXIS));
        bottomSheet.setBackground(TaxiSharePrototype.BG_WHITE);
        bottomSheet.setBorder(new EmptyBorder(12, 24, 24, 24));

        JPanel handleBar = new JPanel();
        handleBar.setBackground(new Color(215, 215, 215));
        handleBar.setMaximumSize(new Dimension(40, 4));
        handleBar.setPreferredSize(new Dimension(40, 4));
        handleBar.setAlignmentX(Component.CENTER_ALIGNMENT);
        bottomSheet.add(handleBar);
        bottomSheet.add(Box.createVerticalStrut(14));

        statusTitleLabel = UIComponents.createLabel("주변 이웃을 찾는 중입니다...", 20, Font.BOLD, TaxiSharePrototype.TEXT_DARK);
        statusTitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        bottomSheet.add(statusTitleLabel);
        bottomSheet.add(Box.createVerticalStrut(4));

        statusDescLabel = UIComponents.createLabel("같은 방향으로 가는 동승자를 매칭하고 있어요", 13, Font.PLAIN, TaxiSharePrototype.TEXT_GRAY);
        statusDescLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        bottomSheet.add(statusDescLabel);
        bottomSheet.add(Box.createVerticalStrut(6));

        tripSummaryLabel = UIComponents.createLabel("", 12, Font.BOLD, TaxiSharePrototype.PRIMARY_BLUE);
        tripSummaryLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        bottomSheet.add(tripSummaryLabel);
        bottomSheet.add(Box.createVerticalStrut(16));

        partnerInfoCard = new JPanel();
        partnerInfoCard.setLayout(new BoxLayout(partnerInfoCard, BoxLayout.Y_AXIS));
        partnerInfoCard.setBackground(new Color(248, 249, 252));
        partnerInfoCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(TaxiSharePrototype.BORDER_GRAY, 1, true),
                new EmptyBorder(14, 16, 14, 16)
        ));
        partnerInfoCard.setMaximumSize(new Dimension(350, 92));
        partnerInfoCard.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel partnerHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        partnerHeader.setOpaque(false);
        partnerHeader.setMaximumSize(new Dimension(320, 24));

        JLabel iconLbl = UIComponents.createLabel("👤", 14, Font.PLAIN, TaxiSharePrototype.TEXT_DARK);
        partnerNameLabel = UIComponents.createLabel("매칭 대기 중...", 14, Font.BOLD, TaxiSharePrototype.TEXT_DARK);
        partnerHeader.add(iconLbl);
        partnerHeader.add(partnerNameLabel);
        partnerInfoCard.add(partnerHeader);
        partnerInfoCard.add(Box.createVerticalStrut(6));

        partnerStyleLabel = UIComponents.createLabel("", 12, Font.PLAIN, TaxiSharePrototype.TEXT_GRAY);
        partnerStyleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        partnerInfoCard.add(partnerStyleLabel);

        partnerInfoCard.setVisible(false);
        bottomSheet.add(partnerInfoCard);
        bottomSheet.add(Box.createVerticalStrut(18));

        actionBtn = new UIComponents.RoundedButton("매칭 취소하기", new Color(240, 240, 240), TaxiSharePrototype.TEXT_DARK);
        actionBtn.addActionListener(e -> {
            stopMatchTimer();
            if (!isMatched) {
                resetMatchingState();
                mainFrameRef.showScreen("CALL");
            } else {
                resetMatchingState();
                mainFrameRef.showScreen("PAYMENT");
            }
        });
        bottomSheet.add(actionBtn);

        add(bottomSheet, BorderLayout.SOUTH);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        resetMatchingState();
        if (mainFrameRef != null) {
            tripSummaryLabel.setText(String.format("%s → %s · 1인 예상 %,d원",
                    mainFrameRef.getCurrentOrigin(),
                    mainFrameRef.getCurrentDestination(),
                    mainFrameRef.getCurrentPaidFare()));
        }
        // 프로토타입 확인용 2.5초 후 가상 매칭 완료 (실제 소켓 수신 시 updateMatchCompleteState 직접 호출로 대체)
        startDemoMatchSimulation();
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        stopMatchTimer();
    }

    private void startDemoMatchSimulation() {
        stopMatchTimer();
        matchTimer = new Timer(2500, e ->
                updateMatchCompleteState("eunwoo_99", "스쳐 지나가는 바람처럼 고요하게, 가벼운 인사 정도만 나누어요")
        );
        matchTimer.setRepeats(false);
        matchTimer.start();
    }

    private void stopMatchTimer() {
        if (matchTimer != null && matchTimer.isRunning()) {
            matchTimer.stop();
        }
    }

    // 소켓/서버 응답 수신 시 외부에서도 바로 호출 가능
    public void updateMatchCompleteState(String partnerId, String partnerStyle) {
        stopMatchTimer();
        isMatched = true;

        statusTitleLabel.setText("동승 매칭이 완료되었습니다!");
        statusTitleLabel.setHorizontalAlignment(SwingConstants.LEFT);
        statusTitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        statusDescLabel.setText("같은 경로 이웃과 연결되었어요. 탑승 후 결제를 진행하세요.");
        statusDescLabel.setHorizontalAlignment(SwingConstants.LEFT);
        statusDescLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        partnerNameLabel.setText("동승 이웃: " + partnerId + " 님");
        partnerNameLabel.setHorizontalAlignment(SwingConstants.LEFT);

        // 긴 문장이 들어가도 오른쪽으로 쏠리지 않도록 폭 제한 및 좌측 정렬
        partnerStyleLabel.setText("<html><body style='width:280px;'>\"" + partnerStyle + "\"</body></html>");
        partnerStyleLabel.setHorizontalAlignment(SwingConstants.LEFT);
        partnerStyleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        partnerInfoCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        partnerInfoCard.setVisible(true);

        actionBtn.setText("결제 화면으로 이동");
        actionBtn.setBackground(TaxiSharePrototype.PRIMARY_BLUE);
        actionBtn.setForeground(Color.WHITE);
        actionBtn.setAlignmentX(Component.LEFT_ALIGNMENT);

        bottomSheetRevalidate();
    }

    public void resetMatchingState() {
        stopMatchTimer();
        isMatched = false;
        statusTitleLabel.setText("주변 이웃을 찾는 중입니다...");
        statusDescLabel.setText("같은 방향으로 가는 동승자를 매칭하고 있어요");
        partnerInfoCard.setVisible(false);
        partnerNameLabel.setText("매칭 대기 중...");
        partnerStyleLabel.setText("");

        actionBtn.setText("매칭 취소하기");
        actionBtn.setBackground(new Color(240, 240, 240));
        actionBtn.setForeground(TaxiSharePrototype.TEXT_DARK);
        bottomSheetRevalidate();
    }
    
    private JLabel createLeftLabel(String text, int size, int style, Color color) {
        JLabel lbl = UIComponents.createLabel(text, size, style, color);
        lbl.setHorizontalAlignment(SwingConstants.LEFT);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        return lbl;
    }

    private void bottomSheetRevalidate() {
        revalidate();
        repaint();
    }

    static class MatchingMapCanvas extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(new Color(242, 240, 232));
            g2.fillRect(0, 0, getWidth(), getHeight());

            g2.setColor(new Color(216, 232, 205));
            g2.fillRoundRect(30, 80, 80, 100, 16, 16);
            g2.fillRoundRect(220, 150, 120, 90, 18, 18);

            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(0, 120, 400, 150);
            g2.drawLine(150, 0, 180, 400);

            g2.setColor(new Color(37, 83, 244, 45));
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(120, 140, 160, 160);
            g2.drawOval(80, 100, 240, 240);

            g2.setColor(TaxiSharePrototype.PRIMARY_BLUE);
            g2.fillOval(192, 212, 16, 16);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(3f));
            g2.drawOval(192, 212, 16, 16);

            g2.dispose();
        }
    }
}