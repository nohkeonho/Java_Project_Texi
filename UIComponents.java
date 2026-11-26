package taxi;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

public class UIComponents {

    public static JLabel createLabel(String text, int size, int style, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("맑은 고딕", style, size));
        label.setForeground(color);
        return label;
    }

    public static class RoundedButton extends JButton {
        private Color bgColor;

        public RoundedButton(String text, Color bgColor, Color fgColor) {
            super(text);
            this.bgColor = bgColor;
            setFont(new Font("맑은 고딕", Font.BOLD, 15));
            setForeground(fgColor);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setMaximumSize(new Dimension(340, 48));
            setPreferredSize(new Dimension(340, 48));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getModel().isPressed() ? bgColor.darker() : bgColor);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 16, 16));
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // 글자 잘림 현상을 막기 위해 좌우 마진을 18px로 넉넉하게 준 텍스트필드
    public static class RoundedTextField extends JTextField {
        private String placeholder;

        public RoundedTextField(String placeholder) {
            this.placeholder = placeholder;
            setFont(new Font("맑은 고딕", Font.PLAIN, 13));
            setForeground(TaxiSharePrototype.TEXT_DARK);
            setOpaque(false);
            setBorder(new EmptyBorder(4, 18, 4, 18));
            
            // 가로 폭과 세로 높이를 정확하게 고정합니다.
            Dimension fixedSize = new Dimension(340, 46);
            setPreferredSize(fixedSize);
            setMaximumSize(fixedSize);
            setMinimumSize(fixedSize);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(TaxiSharePrototype.BG_WHITE);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
            g2.setColor(TaxiSharePrototype.BORDER_GRAY);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
            g2.dispose();

            super.paintComponent(g);

            if (getText().isEmpty() && !isFocusOwner()) {
                Graphics2D g2p = (Graphics2D) g.create();
                g2p.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2p.setColor(TaxiSharePrototype.TEXT_GRAY);
                g2p.setFont(getFont());
                FontMetrics fm = g2p.getFontMetrics();
                int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                g2p.drawString(placeholder, 18, y);
                g2p.dispose();
            }
        }
    }
}