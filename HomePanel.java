package taxi;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;

public class HomePanel extends JPanel {
    private JLabel welcomeLabel;
    private TaxiSharePrototype mainFrameRef;

    public HomePanel(TaxiSharePrototype mainFrame) {
        this.mainFrameRef = mainFrame;
        setLayout(new BorderLayout());
        setBackground(TaxiSharePrototype.BG_WHITE);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(TaxiSharePrototype.BG_WHITE);
        content.setBorder(new EmptyBorder(28, 24, 24, 24));

        // 상단 헤더 (앱 타이틀 + 원형 프로필 아이콘 버튼)
        JPanel topHeaderRow = new JPanel(new BorderLayout());
        topHeaderRow.setOpaque(false);
        topHeaderRow.setMaximumSize(new Dimension(350, 38));
        topHeaderRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel brandBadge = UIComponents.createLabel("TAXI SHARE", 12, Font.BOLD, TaxiSharePrototype.PRIMARY_BLUE);
        topHeaderRow.add(brandBadge, BorderLayout.WEST);

        JPanel profileIconBtn = createProfileIconButton();
        topHeaderRow.add(profileIconBtn, BorderLayout.EAST);

        content.add(topHeaderRow);
        content.add(Box.createVerticalStrut(6));

        welcomeLabel = UIComponents.createLabel("오늘도 같이 타고 가벼운 이동", 22, Font.BOLD, TaxiSharePrototype.TEXT_DARK);
        welcomeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(welcomeLabel);
        content.add(Box.createVerticalStrut(6));

        JLabel subCopy = UIComponents.createLabel("같은 방향으로 가는 이웃과 택시비를 나누어 보세요.", 13, Font.PLAIN, TaxiSharePrototype.TEXT_GRAY);
        subCopy.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(subCopy);
        content.add(Box.createVerticalStrut(22));

        // 핵심 액션 카드: 택시 호출 바로가기
        JPanel heroCard = new JPanel();
        heroCard.setLayout(new BoxLayout(heroCard, BoxLayout.Y_AXIS));
        heroCard.setBackground(new Color(244, 247, 255));
        heroCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(210, 222, 255), 1, true),
                new EmptyBorder(18, 18, 18, 18)
        ));
        heroCard.setMaximumSize(new Dimension(350, 145));
        heroCard.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel cardTag = UIComponents.createLabel("N분의 1 합승 호출", 12, Font.BOLD, TaxiSharePrototype.PRIMARY_BLUE);
        cardTag.setAlignmentX(Component.LEFT_ALIGNMENT);
        heroCard.add(cardTag);
        heroCard.add(Box.createVerticalStrut(6));

        JLabel cardTitle = UIComponents.createLabel("어디로 가시나요?", 17, Font.BOLD, TaxiSharePrototype.TEXT_DARK);
        cardTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        heroCard.add(cardTitle);
        heroCard.add(Box.createVerticalStrut(4));

        JLabel cardDesc = UIComponents.createLabel("2인 탑승 시 50% 절감 · 인원별 요금 자동 계산", 12, Font.PLAIN, TaxiSharePrototype.TEXT_GRAY);
        cardDesc.setAlignmentX(Component.LEFT_ALIGNMENT);
        heroCard.add(cardDesc);
        heroCard.add(Box.createVerticalStrut(14));

        UIComponents.RoundedButton goCallBtn = new UIComponents.RoundedButton("택시 호출하러 가기", TaxiSharePrototype.PRIMARY_BLUE, Color.WHITE);
        goCallBtn.setMaximumSize(new Dimension(314, 42));
        goCallBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        goCallBtn.addActionListener(e -> mainFrameRef.showScreen("CALL"));
        heroCard.add(goCallBtn);

        content.add(heroCard);
        content.add(Box.createVerticalStrut(22));

        // 자주 찾는 경로 섹션
        JLabel quickTitle = UIComponents.createLabel("자주 찾는 경로", 14, Font.BOLD, TaxiSharePrototype.TEXT_DARK);
        quickTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(quickTitle);
        content.add(Box.createVerticalStrut(10));

        content.add(createQuickRouteItem("선문대학교 → 천안터미널", "학교 · 터미널 이동 인기 노선", "선문대학교", "천안터미널"));
        content.add(Box.createVerticalStrut(10));
        content.add(createQuickRouteItem("천안터미널 → 선문대학교", "복귀 경로 바로 선택", "천안터미널", "선문대학교"));

        content.add(Box.createVerticalGlue());

        // 하단 안내 문구
        JLabel footerTip = UIComponents.createLabel("앱을 종료한 뒤 다시 실행하면 로그인부터 시작합니다.", 11, Font.PLAIN, TaxiSharePrototype.TEXT_GRAY);
        footerTip.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(footerTip);

        add(content, BorderLayout.CENTER);
    }

    private JPanel createProfileIconButton() {
        final boolean[] hovered = {false};
        Color baseBg = new Color(248, 249, 252);

        JPanel btn = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int size = Math.min(getWidth(), getHeight()) - 2;
                int x = (getWidth() - size) / 2;
                int y = (getHeight() - size) / 2;

                Color circleBg = hovered[0] ? TaxiSharePrototype.HERO_CIRCLE_BG : baseBg;
                Color silhouetteColor = hovered[0] ? TaxiSharePrototype.PRIMARY_BLUE : new Color(55, 55, 55);

                // 원형 배경
                g2.setColor(circleBg);
                g2.fillOval(x, y, size, size);

                // 원형 내부에 사람 실루엣 클리핑
                Area clipArea = new Area(new Ellipse2D.Double(x, y, size, size));
                g2.setClip(clipArea);

                double headR = size * 0.21;
                double headCx = x + size / 2.0;
                double headCy = y + size * 0.37;

                // 어깨 타원
                double shoulderW = size * 0.68;
                double shoulderH = size * 0.44;
                double shoulderX = x + (size - shoulderW) / 2.0;
                double shoulderY = y + size * 0.63;

                // 머리 주변 컷아웃 테두리 (첨부 이미지의 머리-어깨 분리 느낌)
                g2.setColor(circleBg);
                g2.setStroke(new BasicStroke((float) (size * 0.065), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(new Ellipse2D.Double(headCx - headR, headCy - headR, headR * 2, headR * 2));

                // 어깨 채우기
                g2.setColor(silhouetteColor);
                g2.fill(new Ellipse2D.Double(shoulderX, shoulderY, shoulderW, shoulderH));

                // 머리 주변 얇은 여백 다시 한 번 강조
                g2.setColor(circleBg);
                g2.setStroke(new BasicStroke((float) (size * 0.05), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(new Ellipse2D.Double(headCx - headR, headCy - headR, headR * 2, headR * 2));

                // 머리 원형 채우기
                g2.setColor(silhouetteColor);
                g2.fill(new Ellipse2D.Double(headCx - headR, headCy - headR, headR * 2, headR * 2));

                g2.setClip(null);
                g2.setColor(hovered[0] ? TaxiSharePrototype.PRIMARY_BLUE : TaxiSharePrototype.BORDER_GRAY);
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawOval(x, y, size, size);

                g2.dispose();
            }
        };

        btn.setOpaque(false);
        btn.setPreferredSize(new Dimension(36, 36));
        btn.setMaximumSize(new Dimension(36, 36));
        btn.setMinimumSize(new Dimension(36, 36));
        btn.setToolTipText("내 프로필");
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hovered[0] = true;
                btn.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hovered[0] = false;
                btn.repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (mainFrameRef != null) {
                    if (mainFrameRef.isLoggedIn()) {
                        mainFrameRef.showScreen("PROFILE");
                    } else {
                        mainFrameRef.showScreen("LOGIN");
                    }
                }
            }
        });

        return btn;
    }

    private JPanel createQuickRouteItem(String routeTitle, String desc, String origin, String dest) {
        JPanel item = new JPanel(new BorderLayout());
        item.setBackground(Color.WHITE);
        item.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(TaxiSharePrototype.BORDER_GRAY, 1, true),
                new EmptyBorder(12, 14, 12, 14)
        ));
        item.setMaximumSize(new Dimension(350, 62));
        item.setAlignmentX(Component.LEFT_ALIGNMENT);
        item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel textCol = new JPanel();
        textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));
        textCol.setOpaque(false);

        JLabel titleLbl = UIComponents.createLabel(routeTitle, 13, Font.BOLD, TaxiSharePrototype.TEXT_DARK);
        JLabel descLbl = UIComponents.createLabel(desc, 11, Font.PLAIN, TaxiSharePrototype.TEXT_GRAY);
        textCol.add(titleLbl);
        textCol.add(Box.createVerticalStrut(2));
        textCol.add(descLbl);

        JLabel arrowLbl = UIComponents.createLabel("선택 →", 12, Font.BOLD, TaxiSharePrototype.PRIMARY_BLUE);

        item.add(textCol, BorderLayout.CENTER);
        item.add(arrowLbl, BorderLayout.EAST);

        item.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (mainFrameRef != null) {
                    mainFrameRef.setTripInfo(origin, dest, FeeCalculator.calculateStaticFare(2));
                    mainFrameRef.showScreen("CALL");
                }
            }
        });

        return item;
    }

    public void setUserGreeting(String userName) {
        if (userName != null && !userName.trim().isEmpty()) {
            welcomeLabel.setText(userName.trim() + "님, 오늘도 같이 이동할까요?");
        }
    }
}