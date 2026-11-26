package taxi;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class ProfilePanel extends JPanel {
    private final TaxiSharePrototype mainFrameRef;

    private UIComponents.RoundedTextField idField;
    private UIComponents.RoundedTextField pwField;
    private JToggleButton maleBtn;
    private JToggleButton femaleBtn;
    private ButtonGroup genderGroup;
    private JTextArea styleArea;

    public ProfilePanel(TaxiSharePrototype mainFrame) {
        this.mainFrameRef = mainFrame;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(TaxiSharePrototype.BG_WHITE);
        setBorder(new EmptyBorder(25, 25, 25, 25));

        // 상단 뒤로가기 (홈으로)
        JLabel backBtn = UIComponents.createLabel("←  홈으로", 13, Font.PLAIN, TaxiSharePrototype.TEXT_GRAY);
        backBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        backBtn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                loadProfileData();
                mainFrameRef.showScreen("HOME");
            }
        });

        JPanel backWrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        backWrapper.setOpaque(false);
        backWrapper.setMaximumSize(new Dimension(350, 25));
        backWrapper.setAlignmentX(Component.CENTER_ALIGNMENT);
        backWrapper.add(backBtn);
        add(backWrapper);
        add(Box.createVerticalStrut(12));

        // 타이틀
        JPanel titlePanel = new JPanel();
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        titlePanel.setOpaque(false);
        titlePanel.setMaximumSize(new Dimension(350, 56));
        titlePanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel title = UIComponents.createLabel("내 프로필", 26, Font.BOLD, TaxiSharePrototype.TEXT_DARK);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        titlePanel.add(title);
        titlePanel.add(Box.createVerticalStrut(4));

        JLabel subtitle = UIComponents.createLabel("계정 정보와 동승 프로필을 확인하고 수정하세요", 13, Font.PLAIN, TaxiSharePrototype.TEXT_GRAY);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        titlePanel.add(subtitle);

        add(titlePanel);
        add(Box.createVerticalStrut(16));

        // 아이디 & 비밀번호
        idField = new UIComponents.RoundedTextField("아이디를 입력하세요");
        addFormField("아이디", idField);
        add(Box.createVerticalStrut(10));

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
        maleBtn = createGenderToggle("남성");
        femaleBtn = createGenderToggle("여성");
        genderGroup.add(maleBtn);
        genderGroup.add(femaleBtn);

        genderGrid.add(maleBtn);
        genderGrid.add(femaleBtn);
        genderWrapper.add(genderGrid);

        add(genderWrapper);
        add(Box.createVerticalStrut(10));

        // 동승 스타일
        JPanel styleWrapper = new JPanel();
        styleWrapper.setLayout(new BoxLayout(styleWrapper, BoxLayout.Y_AXIS));
        styleWrapper.setOpaque(false);
        styleWrapper.setMaximumSize(new Dimension(350, 105));
        styleWrapper.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel styleLbl = UIComponents.createLabel("나의 동승 스타일", 13, Font.BOLD, TaxiSharePrototype.TEXT_GRAY);
        styleLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        styleWrapper.add(styleLbl);
        styleWrapper.add(Box.createVerticalStrut(6));

        styleArea = new JTextArea(3, 20);
        styleArea.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        styleArea.setForeground(TaxiSharePrototype.TEXT_DARK);
        styleArea.setLineWrap(true);
        styleArea.setWrapStyleWord(true);
        styleArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(TaxiSharePrototype.BORDER_GRAY, 1, true),
                new EmptyBorder(8, 12, 8, 12)
        ));
        styleArea.setMaximumSize(new Dimension(350, 70));
        styleArea.setPreferredSize(new Dimension(350, 70));
        styleArea.setAlignmentX(Component.LEFT_ALIGNMENT);
        styleWrapper.add(styleArea);

        add(styleWrapper);
        add(Box.createVerticalGlue());

        // 저장 버튼
        UIComponents.RoundedButton saveBtn = new UIComponents.RoundedButton("변경사항 저장", TaxiSharePrototype.PRIMARY_BLUE, Color.WHITE);
        saveBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        saveBtn.addActionListener(e -> {
            String newId = idField.getText().trim();
            String newPw = pwField.getText().trim();
            String newStyle = styleArea.getText().trim();

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
            if (!maleBtn.isSelected() && !femaleBtn.isSelected()) {
                JOptionPane.showMessageDialog(this, "성별을 선택해주세요.", "필수 선택 안내", JOptionPane.WARNING_MESSAGE);
                return;
            }

            String selectedGender = maleBtn.isSelected() ? "남성" : "여성";
            boolean keepConsent = mainFrameRef.isLocationConsent();
            mainFrameRef.registerAccount(newId, newPw, selectedGender, newStyle, keepConsent);

            JOptionPane.showMessageDialog(
                    this,
                    "프로필 정보가 저장되었습니다.",
                    "저장 완료",
                    JOptionPane.INFORMATION_MESSAGE
            );
            mainFrameRef.showScreen("HOME");
        });
        add(saveBtn);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        loadProfileData();
    }

    public void loadProfileData() {
        if (mainFrameRef == null) return;
        idField.setText(mainFrameRef.getRegisteredUserId());
        pwField.setText(mainFrameRef.getRegisteredUserPw());

        boolean isFemale = "여성".equals(mainFrameRef.getRegisteredGender());
        femaleBtn.setSelected(isFemale);
        maleBtn.setSelected(!isFemale);
        refreshGenderStyles();

        styleArea.setText(mainFrameRef.getRegisteredStyle());
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

    private JToggleButton createGenderToggle(String text) {
        JToggleButton btn = new JToggleButton(text);
        btn.setFont(new Font("맑은 고딕", Font.BOLD, 13));
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createLineBorder(TaxiSharePrototype.BORDER_GRAY, 1, true));
        btn.addActionListener(e -> refreshGenderStyles());
        return btn;
    }

    private void refreshGenderStyles() {
        maleBtn.setBackground(maleBtn.isSelected() ? TaxiSharePrototype.HERO_CIRCLE_BG : TaxiSharePrototype.BG_WHITE);
        maleBtn.setForeground(maleBtn.isSelected() ? TaxiSharePrototype.PRIMARY_BLUE : TaxiSharePrototype.TEXT_DARK);
        maleBtn.setBorder(BorderFactory.createLineBorder(
                maleBtn.isSelected() ? TaxiSharePrototype.PRIMARY_BLUE : TaxiSharePrototype.BORDER_GRAY, 1, true));

        femaleBtn.setBackground(femaleBtn.isSelected() ? TaxiSharePrototype.HERO_CIRCLE_BG : TaxiSharePrototype.BG_WHITE);
        femaleBtn.setForeground(femaleBtn.isSelected() ? TaxiSharePrototype.PRIMARY_BLUE : TaxiSharePrototype.TEXT_DARK);
        femaleBtn.setBorder(BorderFactory.createLineBorder(
                femaleBtn.isSelected() ? TaxiSharePrototype.PRIMARY_BLUE : TaxiSharePrototype.BORDER_GRAY, 1, true));
    }
}