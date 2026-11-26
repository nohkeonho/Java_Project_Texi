package taxi;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class SignupPanel extends JPanel {
    private UIComponents.RoundedTextField idField;
    private UIComponents.RoundedTextField pwField;
    private JToggleButton maleCheck;
    private JToggleButton femaleCheck;
    private ButtonGroup genderGroup;
    private JTextArea styleArea;
    private JCheckBox locationCheckBox;

    private final String STYLE_PLACEHOLDER = "예) 스쳐 지나가는 바람처럼 고요하게, 때로는 작은 별빛 같은 따뜻한 인사를 나누며";

    public SignupPanel(TaxiSharePrototype mainFrame) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(TaxiSharePrototype.BG_WHITE);
        setBorder(new EmptyBorder(25, 25, 25, 25));

        // 뒤로가기 헤더
        JLabel backBtn = UIComponents.createLabel("←  로그인으로", 13, Font.PLAIN, TaxiSharePrototype.TEXT_GRAY);
        backBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        backBtn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                clearForm();
                mainFrame.showScreen("LOGIN");
            }
        });

        JPanel backWrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        backWrapper.setOpaque(false);
        backWrapper.setMaximumSize(new Dimension(350, 25));
        backWrapper.setAlignmentX(Component.CENTER_ALIGNMENT);
        backWrapper.add(backBtn);
        add(backWrapper);
        add(Box.createVerticalStrut(12));

        // 타이틀 및 서브타이틀
        JPanel titlePanel = new JPanel();
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        titlePanel.setOpaque(false);
        titlePanel.setMaximumSize(new Dimension(350, 60));
        titlePanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel title = UIComponents.createLabel("회원가입", 26, Font.BOLD, TaxiSharePrototype.TEXT_DARK);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        titlePanel.add(title);
        titlePanel.add(Box.createVerticalStrut(4));

        JLabel subtitle = UIComponents.createLabel("함께 탈 이웃에게 보여줄 정보를 알려주세요", 13, Font.PLAIN, TaxiSharePrototype.TEXT_GRAY);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        titlePanel.add(subtitle);

        add(titlePanel);
        add(Box.createVerticalStrut(18));

        // 아이디
        idField = new UIComponents.RoundedTextField("아이디를 입력하세요");
        addFormField("아이디", idField);
        add(Box.createVerticalStrut(10));

        // 비밀번호
        pwField = new UIComponents.RoundedTextField("비밀번호를 입력하세요");
        addFormField("비밀번호", pwField);
        add(Box.createVerticalStrut(10));

        // 성별 선택
        JPanel genderWrapper = new JPanel();
        genderWrapper.setLayout(new BoxLayout(genderWrapper, BoxLayout.Y_AXIS));
        genderWrapper.setOpaque(false);
        genderWrapper.setMaximumSize(new Dimension(350, 70));
        genderWrapper.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel genderLbl = UIComponents.createLabel("성별", 13, Font.BOLD, TaxiSharePrototype.TEXT_GRAY);
        genderLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        genderWrapper.add(genderLbl);
        genderWrapper.add(Box.createVerticalStrut(6));

        JPanel genderGrid = new JPanel(new GridLayout(1, 2, 10, 0));
        genderGrid.setOpaque(false);
        genderGrid.setMaximumSize(new Dimension(350, 44));
        genderGrid.setAlignmentX(Component.LEFT_ALIGNMENT);

        genderGroup = new ButtonGroup();
        maleCheck = createGenderToggleButton("남성", true);
        femaleCheck = createGenderToggleButton("여성", false);

        genderGroup.add(maleCheck);
        genderGroup.add(femaleCheck);

        genderGrid.add(maleCheck);
        genderGrid.add(femaleCheck);
        genderWrapper.add(genderGrid);

        add(genderWrapper);
        add(Box.createVerticalStrut(10));

        // 나의 동승 스타일
        JPanel styleWrapper = new JPanel();
        styleWrapper.setLayout(new BoxLayout(styleWrapper, BoxLayout.Y_AXIS));
        styleWrapper.setOpaque(false);
        styleWrapper.setMaximumSize(new Dimension(350, 115));
        styleWrapper.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel styleLbl = UIComponents.createLabel("나의 동승 스타일", 13, Font.BOLD, TaxiSharePrototype.TEXT_GRAY);
        styleLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        styleWrapper.add(styleLbl);
        styleWrapper.add(Box.createVerticalStrut(6));

        styleArea = new JTextArea(STYLE_PLACEHOLDER);
        styleArea.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        styleArea.setForeground(TaxiSharePrototype.TEXT_GRAY);
        styleArea.setLineWrap(true);
        styleArea.setWrapStyleWord(true);
        styleArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(TaxiSharePrototype.BORDER_GRAY, 1, true),
                new EmptyBorder(8, 12, 8, 12)
        ));
        styleArea.setMaximumSize(new Dimension(350, 70));
        styleArea.setPreferredSize(new Dimension(350, 70));
        styleArea.setAlignmentX(Component.LEFT_ALIGNMENT);
        styleArea.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (styleArea.getText().equals(STYLE_PLACEHOLDER)) {
                    styleArea.setText("");
                    styleArea.setForeground(TaxiSharePrototype.TEXT_DARK);
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (styleArea.getText().trim().isEmpty()) {
                    styleArea.setText(STYLE_PLACEHOLDER);
                    styleArea.setForeground(TaxiSharePrototype.TEXT_GRAY);
                }
            }
        });
        styleWrapper.add(styleArea);
        add(styleWrapper);
        add(Box.createVerticalStrut(4));

        JPanel hintWrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        hintWrapper.setOpaque(false);
        hintWrapper.setMaximumSize(new Dimension(350, 20));
        hintWrapper.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel hintLabel = UIComponents.createLabel("매칭된 동승자에게 보여요 · 최대 60자", 11, Font.PLAIN, TaxiSharePrototype.TEXT_GRAY);
        hintWrapper.add(hintLabel);
        add(hintWrapper);
        add(Box.createVerticalStrut(12));

        // 위치정보 수집 동의 체크박스
        locationCheckBox = new JCheckBox(" 위치정보 수집 및 활용에 동의하시겠습니까?");
        locationCheckBox.setFont(new Font("맑은 고딕", Font.BOLD, 12));
        locationCheckBox.setForeground(TaxiSharePrototype.TEXT_DARK);
        locationCheckBox.setOpaque(true);
        locationCheckBox.setBackground(new Color(248, 249, 252));
        locationCheckBox.setFocusPainted(false);
        locationCheckBox.setBorder(BorderFactory.createLineBorder(TaxiSharePrototype.BORDER_GRAY, 1, true));
        locationCheckBox.setMaximumSize(new Dimension(350, 42));
        locationCheckBox.setPreferredSize(new Dimension(350, 42));
        locationCheckBox.setAlignmentX(Component.CENTER_ALIGNMENT);
        add(locationCheckBox);

        add(Box.createVerticalGlue());

        // 가입하기 버튼
        UIComponents.RoundedButton submitBtn = new UIComponents.RoundedButton("가입하기", TaxiSharePrototype.PRIMARY_BLUE, Color.WHITE);
        submitBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        submitBtn.addActionListener(e -> {
            String newId = idField.getText().trim();
            String newPw = pwField.getText().trim();

            if (newId.isEmpty()) {
                JOptionPane.showMessageDialog(this, "아이디를 입력해주세요.", "필수 입력 안내", JOptionPane.WARNING_MESSAGE);
                idField.requestFocus();
                return;
            }
            if (newPw.isEmpty()) {
                JOptionPane.showMessageDialog(this, "비밀번호를 입력해주세요.", "필수 입력 안내", JOptionPane.WARNING_MESSAGE);
                pwField.requestFocus();
                return;
            }
            if (!maleCheck.isSelected() && !femaleCheck.isSelected()) {
                JOptionPane.showMessageDialog(this, "성별을 선택해주세요.", "필수 선택 안내", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (!locationCheckBox.isSelected()) {
                JOptionPane.showMessageDialog(this, "위치정보 수집 및 활용에 동의해야 가입이 가능합니다.", "필수 동의 안내", JOptionPane.WARNING_MESSAGE);
                return;
            }

            String selectedGender = maleCheck.isSelected() ? "남성" : "여성";
            String rawStyle = styleArea.getText().trim();
            String finalStyle = rawStyle.equals(STYLE_PLACEHOLDER) ? "" : rawStyle;
            boolean agreedLocation = locationCheckBox.isSelected();

            mainFrame.registerAccount(newId, newPw, selectedGender, finalStyle, agreedLocation);

            JOptionPane.showMessageDialog(
                    this,
                    "회원가입이 완료되었습니다.\n가입하신 아이디와 비밀번호로 로그인해주세요.",
                    "가입 완료",
                    JOptionPane.INFORMATION_MESSAGE
            );
            clearForm();
            mainFrame.showScreen("LOGIN");
        });
        add(submitBtn);
    }

    private void addFormField(String labelText, JComponent field) {
        JPanel wrapper = new JPanel();
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setOpaque(false);
        wrapper.setMaximumSize(new Dimension(350, 70));
        wrapper.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lbl = UIComponents.createLabel(labelText, 13, Font.BOLD, TaxiSharePrototype.TEXT_GRAY);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.add(lbl);
        wrapper.add(Box.createVerticalStrut(6));

        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.add(field);

        add(wrapper);
    }

    private void clearForm() {
        if (idField != null) idField.setText("");
        if (pwField != null) pwField.setText("");
        if (genderGroup != null) genderGroup.clearSelection();
        if (maleCheck != null) {
            maleCheck.setBackground(TaxiSharePrototype.BG_WHITE);
            maleCheck.setForeground(TaxiSharePrototype.TEXT_DARK);
            maleCheck.setBorder(BorderFactory.createLineBorder(TaxiSharePrototype.BORDER_GRAY, 1, true));
        }
        if (femaleCheck != null) {
            femaleCheck.setBackground(TaxiSharePrototype.BG_WHITE);
            femaleCheck.setForeground(TaxiSharePrototype.TEXT_DARK);
            femaleCheck.setBorder(BorderFactory.createLineBorder(TaxiSharePrototype.BORDER_GRAY, 1, true));
        }
        if (styleArea != null) {
            styleArea.setText(STYLE_PLACEHOLDER);
            styleArea.setForeground(TaxiSharePrototype.TEXT_GRAY);
        }
        if (locationCheckBox != null) locationCheckBox.setSelected(false);
    }

    private JToggleButton createGenderToggleButton(String text, boolean isMale) {
        Icon genderIcon = new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                g2.setColor(c instanceof JToggleButton && ((JToggleButton) c).isSelected()
                        ? TaxiSharePrototype.PRIMARY_BLUE : TaxiSharePrototype.TEXT_DARK);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                if (isMale) {
                    g2.drawOval(x, y + 4, 10, 10);
                    g2.drawLine(x + 8, y + 6, x + 15, y + 1);
                    g2.drawLine(x + 11, y + 1, x + 15, y + 1);
                    g2.drawLine(x + 15, y + 1, x + 15, y + 5);
                } else {
                    g2.drawOval(x + 3, y, 10, 10);
                    g2.drawLine(x + 8, y + 10, x + 8, y + 16);
                    g2.drawLine(x + 5, y + 13, x + 11, y + 13);
                }
                g2.dispose();
            }

            @Override
            public int getIconWidth() { return 18; }

            @Override
            public int getIconHeight() { return 18; }
        };

        JToggleButton btn = new JToggleButton(text, genderIcon);
        btn.setHorizontalTextPosition(SwingConstants.RIGHT);
        btn.setIconTextGap(8);
        btn.setFont(new Font("맑은 고딕", Font.PLAIN, 14));
        btn.setForeground(TaxiSharePrototype.TEXT_DARK);
        btn.setBackground(TaxiSharePrototype.BG_WHITE);
        btn.setHorizontalAlignment(SwingConstants.CENTER);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createLineBorder(TaxiSharePrototype.BORDER_GRAY, 1, true));
        btn.setBorderPainted(true);

        btn.addActionListener(e -> {
            maleCheck.setBackground(maleCheck.isSelected() ? TaxiSharePrototype.HERO_CIRCLE_BG : TaxiSharePrototype.BG_WHITE);
            maleCheck.setForeground(maleCheck.isSelected() ? TaxiSharePrototype.PRIMARY_BLUE : TaxiSharePrototype.TEXT_DARK);
            maleCheck.setBorder(BorderFactory.createLineBorder(maleCheck.isSelected() ? TaxiSharePrototype.PRIMARY_BLUE : TaxiSharePrototype.BORDER_GRAY, 1, true));

            femaleCheck.setBackground(femaleCheck.isSelected() ? TaxiSharePrototype.HERO_CIRCLE_BG : TaxiSharePrototype.BG_WHITE);
            femaleCheck.setForeground(femaleCheck.isSelected() ? TaxiSharePrototype.PRIMARY_BLUE : TaxiSharePrototype.TEXT_DARK);
            femaleCheck.setBorder(BorderFactory.createLineBorder(femaleCheck.isSelected() ? TaxiSharePrototype.PRIMARY_BLUE : TaxiSharePrototype.BORDER_GRAY, 1, true));
        });

        return btn;
    }
}