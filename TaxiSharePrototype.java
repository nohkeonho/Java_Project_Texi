package taxi;

import javax.swing.*;
import java.awt.*;

public class TaxiSharePrototype extends JFrame {

    public static final Color PRIMARY_BLUE = new Color(37, 83, 244);     // #2553F4
    public static final Color BG_WHITE = Color.WHITE;
    public static final Color BORDER_GRAY = new Color(224, 224, 224);
    public static final Color TEXT_DARK = new Color(30, 30, 30);
    public static final Color TEXT_GRAY = new Color(120, 120, 120);
    public static final Color DISCOUNT_RED = new Color(225, 65, 45);
    public static final Color DISCOUNT_BG = new Color(253, 237, 235);
    public static final Color HERO_CIRCLE_BG = new Color(238, 242, 255);

    private CardLayout cardLayout;
    private JPanel mainContainer;
    private HomePanel homePanel;

    // 경로 및 요금 상태값
    private String currentOrigin = "선문대학교";
    private String currentDestination = "천안터미널";
    private int currentPaidFare = 6000;

    // 계정 및 프로필 상태값
    private String registeredUserId = "admin";
    private String registeredUserPw = "1234";
    private String registeredGender = "남성";
    private String registeredStyle = "스쳐 지나가는 바람처럼 고요하게, 가벼운 인사 정도만 나누어요";
    private boolean locationConsent = true;
    private boolean isLoggedIn = false;

    public TaxiSharePrototype() {
        setTitle("택시 합승 어플리케이션");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(400, 820);
        setResizable(false);
        setLocationRelativeTo(null);

        cardLayout = new CardLayout();
        mainContainer = new JPanel(cardLayout);
        homePanel = new HomePanel(this);

        mainContainer.add(new LoginPanel(this), "LOGIN");
        mainContainer.add(new SignupPanel(this), "SIGNUP");
        mainContainer.add(homePanel, "HOME");
        mainContainer.add(new CallTaxiPanel(this), "CALL");
        mainContainer.add(new MatchingResultPanel(this), "MATCHING");
        mainContainer.add(new PaymentCompletePanel(this), "PAYMENT");
        mainContainer.add(new ProfilePanel(this), "PROFILE");

        add(mainContainer);
        cardLayout.show(mainContainer, "LOGIN");

        try {
            ImageIcon icon = new ImageIcon("C:/Temp2/taxi_icon.png");
            setIconImage(icon.getImage());
        } catch (Exception e) {
            System.out.println("아이콘을 불러오지 못했습니다: " + e.getMessage());
        }
    }

    public void registerAccount(String userId, String password) {
        registerAccount(userId, password, this.registeredGender, this.registeredStyle, this.locationConsent);
    }

    public void registerAccount(String userId, String password, String gender, String style, boolean locationConsent) {
        this.registeredUserId = userId;
        this.registeredUserPw = password;
        if (gender != null && !gender.isEmpty()) {
            this.registeredGender = gender;
        }
        this.registeredStyle = (style != null) ? style : "";
        this.locationConsent = locationConsent;
    }

    public boolean verifyCredentials(String userId, String password) {
        if (registeredUserId == null || registeredUserPw == null) {
            return false;
        }
        return registeredUserId.equals(userId) && registeredUserPw.equals(password);
    }

    public String getRegisteredUserId() {
        return registeredUserId;
    }

    public String getRegisteredUserPw() {
        return registeredUserPw;
    }

    public String getRegisteredGender() {
        return registeredGender;
    }

    public String getRegisteredStyle() {
        return registeredStyle;
    }

    public boolean isLocationConsent() {
        return locationConsent;
    }

    public boolean isLoggedIn() {
        return isLoggedIn;
    }

    public void setLoggedIn(boolean loggedIn) {
        this.isLoggedIn = loggedIn;
    }

    public void showHomeOrLogin() {
        if (isLoggedIn) {
            showScreen("CALL");
        } else {
            showScreen("LOGIN");
        }
    }

    public void showScreen(String screenName) {
        cardLayout.show(mainContainer, screenName);
    }

    public void setTripInfo(String origin, String dest, int fare) {
        this.currentOrigin = origin;
        this.currentDestination = dest;
        this.currentPaidFare = fare;
    }

    public String getCurrentOrigin() {
        return currentOrigin;
    }

    public String getCurrentDestination() {
        return currentDestination;
    }

    public int getCurrentPaidFare() {
        return currentPaidFare;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new TaxiSharePrototype().setVisible(true);
        });
    }
}