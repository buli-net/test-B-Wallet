package wallet.ui.indicator;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class IndicatorRepository {

    public static class MaLine {
        public boolean enabled;
        public int period;
        public int color;
        public float width; // 1f, 2f, 3f
        public MaLine(boolean e, int p, int c, float w) {
            enabled = e; period = p; color = c; width = w;
        }
    }

    public static class MainIndicators {
        public List<MaLine> maLines = new ArrayList<>();
        public List<MaLine> emaLines = new ArrayList<>();
        // sau này thêm BOLL, SAR...
    }

    public static class SubIndicators {
        public boolean showVOL = true;
        public boolean showMACD = false;
        public boolean showRSI = false;
        public int volMa1 = 5;
        public int volMa2 = 10;
    }

    public static class Config {
        public MainIndicators main = new MainIndicators();
        public SubIndicators sub = new SubIndicators();
    }

    private static final String PREF_NAME = "chart_indicator_config";
    private static final String KEY_JSON = "config_json";

    // Màu mặc định y hệt chart mày đang dùng
    private static final int[] DEFAULT_MA_COLORS = {
            0xFFF0B90B, 0xFF9B59B6, 0xFF5DADE2, 0xFF2ECC71, 0xFFE74C3C,
            0xFF1ABC9C, 0xFF34495E, 0xFFF39C12, 0xFFD35400, 0xFF2980B9
    };
    private static final int[] DEFAULT_MA_PERIODS = {7, 25, 99, 0, 0, 0, 0, 0, 0, 0};

    public static Config load(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = sp.getString(KEY_JSON, "");
        Config cfg = new Config();
        if (json.isEmpty()) {
            // Tạo mặc định lần đầu
            for (int i = 0; i < 10; i++) {
                boolean en = i < 3; // chỉ bật 3 MA đầu như hiện tại
                cfg.main.maLines.add(new MaLine(en, DEFAULT_MA_PERIODS[i], DEFAULT_MA_COLORS[i], 1f));
                cfg.main.emaLines.add(new MaLine(false, DEFAULT_MA_PERIODS[i], DEFAULT_MA_COLORS[i], 1f));
            }
            return cfg;
        }
        try {
            JSONObject root = new JSONObject(json);
            JSONArray maArr = root.getJSONArray("ma");
            for (int i = 0; i < maArr.length(); i++) {
                JSONObject o = maArr.getJSONObject(i);
                cfg.main.maLines.add(new MaLine(o.getBoolean("e"), o.getInt("p"), o.getInt("c"), (float)o.getDouble("w")));
            }
            // đọc thêm ema, sub...
            JSONObject sub = root.getJSONObject("sub");
            cfg.sub.showVOL = sub.getBoolean("vol");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return cfg;
    }

    public static void save(Context ctx, Config cfg) {
        try {
            JSONObject root = new JSONObject();
            JSONArray maArr = new JSONArray();
            for (MaLine m : cfg.main.maLines) {
                JSONObject o = new JSONObject();
                o.put("e", m.enabled); o.put("p", m.period); o.put("c", m.color); o.put("w", m.width);
                maArr.put(o);
            }
            root.put("ma", maArr);
            JSONObject sub = new JSONObject();
            sub.put("vol", cfg.sub.showVOL);
            root.put("sub", sub);
            ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putString(KEY_JSON, root.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
