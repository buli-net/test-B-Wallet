public class IndicatorModels {
    public static class MaLine { boolean enabled; int period; int color; float width; }
    public static class EmaLine { boolean enabled; int period; int color; float width; }
    public static class BollConfig { boolean enabled; int period; float stdDev; int colorUp, colorMid, colorLow; }
    // VOL, MACD, RSI...
    public static class MainConfig {
        List<MaLine> ma = new ArrayList<>(); // 10 đường
        List<EmaLine> ema = new ArrayList<>();
        BollConfig boll;
        boolean showSAR;
    }
    public static class SubConfig {
        boolean showVOL, showMACD, showRSI, showKDJ, showOBV;
        int volMa1=5, volMa2=10;
    }
}
