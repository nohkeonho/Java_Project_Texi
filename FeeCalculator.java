package taxi;

public class FeeCalculator {
    // 기준이 되는 총 택시비 (추후 외부 거리별 API/데이터가 연동되면 이 변수나 메서드에 대입됩니다)
    private static final int STANDARD_TOTAL_FARE = 12000;

    /**
     * [현재 프로토타입용] 
     * 기획 의도 반영: 총 택시비를 인원수로 공평하게 나누어(N분의 1) 1인당 부담 금액 산출
     */
    public static int calculateStaticFare(int pax) {
        if (pax <= 0) return STANDARD_TOTAL_FARE;
        
        // 인원수가 늘어날수록 총액을 인원수로 나누어 1인당 금액이 줄어듦 (2인이면 절반, 3인이면 1/3...)
        return STANDARD_TOTAL_FARE / pax;
    }

    /**
     * [추후 외부 데이터/거리 연동 확장용]
     * 외부에서 계산된 총 거리별 요금과 탑승 인원을 받아 N분의 1로 나누는 메서드
     */
    public static int calculateFareByDistance(double distanceKm, int pax) {
        // 예시: 거리에 따른 총 택시비 산정 (추후 외부 API 연동 지점)
        int totalFare = (int) (distanceKm * 2500); 
        if (totalFare < 12000) {
            totalFare = 12000; // 최소 기본 총액 보장
        }

        if (pax <= 0) return totalFare;
        
        // 총 요금을 인원수로 나누어 반환
        return totalFare / pax;
    }
}