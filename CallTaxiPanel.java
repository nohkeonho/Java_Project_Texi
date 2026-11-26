package taxi;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.GeneralPath;
import java.util.function.Consumer;

public class CallTaxiPanel extends JPanel {
    private static final String DEFAULT_ORIGIN = "선문대학교";
    private static final String DEFAULT_DEST = "천안터미널";
    private static final int BASE_FARE = 12000;

    private int selectedPax = 0;
    private JLabel priceLabel;
    private JLabel saveInfoLabel;
    private JToggleButton[] paxButtons = new JToggleButton[4];
    private JTextField originField;
    private JTextField destField;
    private ButtonGroup paxGroup;
    private boolean isLoading = false;
    private Consumer<String[]> onRouteChangedCallback;

    public CallTaxiPanel(TaxiSharePrototype mainFrame) {
        setLayout(new BorderLayout());
        setBackground(TaxiSharePrototype.BG_WHITE);

        SampleMapCanvas mapCanvas = new SampleMapCanvas();
        mapCanvas.setLayout(null);

        // 뒤로 가기 아이콘 버튼
        JPanel backButtonPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(245, 246, 248));
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.setColor(TaxiSharePrototype.TEXT_DARK);
                g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(14, getHeight() / 2, 26, getHeight() / 2);
                g2.drawLine(14, getHeight() / 2, 19, getHeight() / 2 - 5);
                g2.drawLine(14, getHeight() / 2, 19, getHeight() / 2 + 5);
                g2.dispose();
            }
        };
        backButtonPanel.setOpaque(false);
        backButtonPanel.setBounds(15, 12, 40, 40);
        backButtonPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        backButtonPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                mainFrame.showScreen("HOME");
            }
        });
        mapCanvas.add(backButtonPanel);

        // 출발지 / 도착지 검색 바
        JPanel routeBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 8)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 40, 40);
                g2.setColor(new Color(230, 230, 230));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 40, 40);
                g2.dispose();
            }
        };
        routeBar.setOpaque(false);
        routeBar.setBounds(62, 12, 318, 40);

        JLabel originDot = UIComponents.createLabel("◉", 12, Font.BOLD, TaxiSharePrototype.PRIMARY_BLUE);
        originField = new JTextField(DEFAULT_ORIGIN, 8);
        originField.setFont(new Font("맑은 고딕", Font.BOLD, 13));
        originField.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

        JLabel arrowText = UIComponents.createLabel("→", 14, Font.PLAIN, TaxiSharePrototype.TEXT_GRAY);

        JLabel destDot = UIComponents.createLabel("◉", 12, Font.BOLD, TaxiSharePrototype.TEXT_DARK);
        destField = new JTextField(DEFAULT_DEST, 8);
        destField.setFont(new Font("맑은 고딕", Font.BOLD, 13));
        destField.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

        routeBar.add(originDot);
        routeBar.add(originField);
        routeBar.add(arrowText);
        routeBar.add(destDot);
        routeBar.add(destField);
        mapCanvas.add(routeBar);

        JLabel apiTag = new JLabel("지도 API 검색 영역", SwingConstants.CENTER);
        apiTag.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
        apiTag.setForeground(TaxiSharePrototype.TEXT_GRAY);
        apiTag.setOpaque(true);
        apiTag.setBackground(new Color(255, 255, 255, 220));
        apiTag.setBounds(18, 360, 135, 24);
        mapCanvas.add(apiTag);

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

        JLabel paxTitle = UIComponents.createLabel("탑승 인원", 13, Font.BOLD, TaxiSharePrototype.TEXT_GRAY);
        paxTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        bottomSheet.add(paxTitle);
        bottomSheet.add(Box.createVerticalStrut(8));

        JPanel paxGrid = new JPanel(new GridLayout(1, 4, 8, 0));
        paxGrid.setOpaque(false);
        paxGrid.setMaximumSize(new Dimension(350, 44));
        paxGrid.setAlignmentX(Component.LEFT_ALIGNMENT);

        paxGroup = new ButtonGroup();
        for (int i = 0; i < 4; i++) {
            int count = i + 1;

            Icon personIcon = new Icon() {
                @Override
                public void paintIcon(Component c, Graphics g, int x, int y) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(c instanceof JToggleButton && ((JToggleButton) c).isSelected()
                            ? TaxiSharePrototype.PRIMARY_BLUE : TaxiSharePrototype.TEXT_DARK);
                    g2.fillOval(x + 4, y + 1, 8, 8);
                    g2.fillArc(x, y + 10, 16, 12, 0, 180);
                    g2.dispose();
                }

                @Override
                public int getIconWidth() { return 16; }

                @Override
                public int getIconHeight() { return 16; }
            };

            JToggleButton btn = new JToggleButton(count + "인", personIcon);
            btn.setHorizontalTextPosition(SwingConstants.RIGHT);
            btn.setIconTextGap(6);
            btn.setFont(new Font("맑은 고딕", Font.BOLD, 13));
            btn.setFocusPainted(false);
            btn.setBackground(TaxiSharePrototype.BG_WHITE);
            btn.setForeground(TaxiSharePrototype.TEXT_DARK);
            btn.setBorder(BorderFactory.createLineBorder(TaxiSharePrototype.BORDER_GRAY, 1, true));

            btn.addActionListener(e -> updatePaxSelectionWithLoading(count));
            paxButtons[i] = btn;
            paxGroup.add(btn);
            paxGrid.add(btn);
        }
        paxGroup.clearSelection();

        bottomSheet.add(paxGrid);
        bottomSheet.add(Box.createVerticalStrut(18));

        JPanel fareHeader = new JPanel(new BorderLayout());
        fareHeader.setOpaque(false);
        fareHeader.setMaximumSize(new Dimension(350, 26));
        fareHeader.setAlignmentX(Component.LEFT_ALIGNMENT);

        fareHeader.add(UIComponents.createLabel("예상 요금", 13, Font.BOLD, TaxiSharePrototype.TEXT_GRAY), BorderLayout.WEST);

        JLabel badge = new JLabel(" 합승 할인 적용 ");
        badge.setFont(new Font("맑은 고딕", Font.BOLD, 11));
        badge.setForeground(TaxiSharePrototype.DISCOUNT_RED);
        badge.setOpaque(true);
        badge.setBackground(TaxiSharePrototype.DISCOUNT_BG);
        fareHeader.add(badge, BorderLayout.EAST);
        bottomSheet.add(fareHeader);
        bottomSheet.add(Box.createVerticalStrut(4));

        JLabel originalPrice = new JLabel("<html><strike>12,000원</strike></html>");
        originalPrice.setFont(new Font("맑은 고딕", Font.PLAIN, 14));
        originalPrice.setForeground(TaxiSharePrototype.TEXT_GRAY);
        originalPrice.setAlignmentX(Component.LEFT_ALIGNMENT);
        bottomSheet.add(originalPrice);

        JPanel finalPriceRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        finalPriceRow.setOpaque(false);
        finalPriceRow.setMaximumSize(new Dimension(350, 42));
        finalPriceRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        priceLabel = UIComponents.createLabel("-", 30, Font.BOLD, TaxiSharePrototype.DISCOUNT_RED);
        JLabel wonLabel = UIComponents.createLabel("원", 16, Font.BOLD, TaxiSharePrototype.TEXT_DARK);
        finalPriceRow.add(priceLabel);
        finalPriceRow.add(wonLabel);
        bottomSheet.add(finalPriceRow);

        saveInfoLabel = UIComponents.createLabel("탑승 인원을 선택해주세요", 12, Font.PLAIN, TaxiSharePrototype.TEXT_GRAY);
        saveInfoLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        bottomSheet.add(saveInfoLabel);
        bottomSheet.add(Box.createVerticalStrut(18));

        UIComponents.RoundedButton callBtn = new UIComponents.RoundedButton("택시 호출", TaxiSharePrototype.PRIMARY_BLUE, Color.WHITE);
        callBtn.addActionListener(e -> {
            String origin = getOriginText();
            String dest = getDestinationText();

            if (origin.isEmpty() || dest.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "출발지와 도착지를 모두 입력해주세요.",
                        "경로 입력 안내",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (selectedPax == 0) {
                JOptionPane.showMessageDialog(this,
                        "탑승 인원을 선택해주세요.",
                        "필수 선택 안내",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (origin.equals(dest)) {
                JOptionPane.showMessageDialog(this,
                        "출발지와 도착지가 동일합니다. 올바른 경로를 입력해주세요.",
                        "경로 오류",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (isLoading) {
                JOptionPane.showMessageDialog(this,
                        "요금을 계산 중입니다. 잠시만 기다려주세요.",
                        "안내",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            int currentFare = FeeCalculator.calculateStaticFare(selectedPax);
            mainFrame.setTripInfo(origin, dest, currentFare);

            mainFrame.showScreen("MATCHING");
            resetCallForm();
        });
        bottomSheet.add(callBtn);

        add(bottomSheet, BorderLayout.SOUTH);
    }

    // 외부 입력(지도 핀 선택, 자동완성, API/소켓 데이터 등)으로 출발지/도착지 주입
    public void setRoute(String origin, String dest) {
        if (origin != null && originField != null) {
            originField.setText(origin.trim());
        }
        if (dest != null && destField != null) {
            destField.setText(dest.trim());
        }
        recalculateFareIfSelected();
        notifyRouteChanged();
    }

    public void setOriginText(String origin) {
        if (origin != null && originField != null) {
            originField.setText(origin.trim());
            recalculateFareIfSelected();
            notifyRouteChanged();
        }
    }

    public void setDestinationText(String dest) {
        if (dest != null && destField != null) {
            destField.setText(dest.trim());
            recalculateFareIfSelected();
            notifyRouteChanged();
        }
    }

    public String getOriginText() {
        return originField != null ? originField.getText().trim() : "";
    }

    public String getDestinationText() {
        return destField != null ? destField.getText().trim() : "";
    }

    public void setOnRouteChangedCallback(Consumer<String[]> callback) {
        this.onRouteChangedCallback = callback;
    }

    private void notifyRouteChanged() {
        if (onRouteChangedCallback != null) {
            onRouteChangedCallback.accept(new String[]{getOriginText(), getDestinationText()});
        }
    }

    private void recalculateFareIfSelected() {
        if (!isLoading && selectedPax > 0 && priceLabel != null && saveInfoLabel != null) {
            int perPersonFare = FeeCalculator.calculateStaticFare(selectedPax);
            int savedAmount = BASE_FARE - perPersonFare;
            priceLabel.setText(String.format("%,d", perPersonFare));
            saveInfoLabel.setText(selectedPax + "인 매칭 기준 · " + String.format("%,d", savedAmount) + "원 절약");
        }
    }

    private void updatePaxSelectionWithLoading(int count) {
        if (isLoading) return;
        isLoading = true;
        selectedPax = count;

        priceLabel.setText("...");
        saveInfoLabel.setText("요금 산정 및 거리 계산 중...");

        Timer loadingTimer = new Timer(300, evt -> {
            for (int i = 0; i < 4; i++) {
                boolean isSel = (i + 1 == count);
                paxButtons[i].setBackground(isSel ? TaxiSharePrototype.HERO_CIRCLE_BG : TaxiSharePrototype.BG_WHITE);
                paxButtons[i].setForeground(isSel ? TaxiSharePrototype.PRIMARY_BLUE : TaxiSharePrototype.TEXT_DARK);
                paxButtons[i].setBorder(BorderFactory.createLineBorder(isSel ? TaxiSharePrototype.PRIMARY_BLUE : TaxiSharePrototype.BORDER_GRAY, 1, true));
                paxButtons[i].repaint();
            }

            int perPersonFare = FeeCalculator.calculateStaticFare(count);
            int savedAmount = BASE_FARE - perPersonFare;

            priceLabel.setText(String.format("%,d", perPersonFare));
            saveInfoLabel.setText(count + "인 매칭 기준 · " + String.format("%,d", savedAmount) + "원 절약");
            isLoading = false;
        });
        loadingTimer.setRepeats(false);
        loadingTimer.start();
    }

    public void resetCallForm() {
        selectedPax = 0;
        if (paxGroup != null) {
            paxGroup.clearSelection();
        }
        for (int i = 0; i < 4; i++) {
            paxButtons[i].setBackground(TaxiSharePrototype.BG_WHITE);
            paxButtons[i].setForeground(TaxiSharePrototype.TEXT_DARK);
            paxButtons[i].setBorder(BorderFactory.createLineBorder(TaxiSharePrototype.BORDER_GRAY, 1, true));
            paxButtons[i].repaint();
        }
        if (originField != null) originField.setText(DEFAULT_ORIGIN);
        if (destField != null) destField.setText(DEFAULT_DEST);
        if (priceLabel != null) priceLabel.setText("-");
        if (saveInfoLabel != null) saveInfoLabel.setText("탑승 인원을 선택해주세요");
    }

    static class SampleMapCanvas extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(new Color(242, 240, 232));
            g2.fillRect(0, 0, getWidth(), getHeight());

            g2.setColor(new Color(216, 232, 205));
            g2.fillRoundRect(-10, 100, 75, 95, 16, 16);
            g2.fillRoundRect(120, 195, 110, 80, 18, 18);
            g2.fillRoundRect(325, 235, 80, 110, 18, 18);

            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(75, 0, 110, 420);
            g2.drawLine(250, 0, 260, 420);
            g2.drawLine(310, 0, 300, 420);
            g2.drawLine(0, 95, 400, 75);
            g2.drawLine(0, 325, 130, 315);
            g2.drawLine(130, 315, 400, 175);

            GeneralPath routePath = new GeneralPath();
            routePath.moveTo(85, 318);
            routePath.quadTo(185, 255, 295, 188);
            routePath.lineTo(308, 135);

            g2.setColor(TaxiSharePrototype.PRIMARY_BLUE);
            g2.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(routePath);

            g2.setColor(Color.WHITE);
            g2.fillOval(63, 312, 24, 24);
            g2.setColor(TaxiSharePrototype.PRIMARY_BLUE);
            g2.setStroke(new BasicStroke(4f));
            g2.drawOval(67, 316, 16, 16);

            g2.setColor(Color.WHITE);
            g2.fillOval(298, 112, 24, 24);
            g2.setColor(TaxiSharePrototype.TEXT_DARK);
            g2.setStroke(new BasicStroke(4f));
            g2.drawOval(302, 116, 16, 16);

            g2.dispose();
        }
    }
}