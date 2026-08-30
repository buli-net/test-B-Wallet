/*
 * Copyright (c) 2024
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package wallet.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import wallet.R;

/**
 * Custom View that renders a financial market chart with candlesticks,
 * moving averages, volume bars, and interactive gestures (zoom/pan).
 * Data is fetched from Binance API.
 * Defaults are now provided from layout via setDefaultsFromLayout(), not from XML resources.
 */
public class MarketChartView extends View {

    // --------------------------------------------------------------------
    // Nested classes
    // --------------------------------------------------------------------

    /** Represents a single candlestick data point. */
    public static class Candle {
        public final float open;
        public final float high;
        public final float low;
        public final float close;
        public final float volume;
        public final long openTime;
        public final long closeTime;

        public Candle(float open, float high, float low, float close, float volume,
                      long openTime, long closeTime) {
            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
            this.volume = volume;
            this.openTime = openTime;
            this.closeTime = closeTime;
        }
    }

    /** Configuration for a Moving Average line. */
    public static class MaLine {
        public int period;
        public int color;

        public MaLine(int period, int color) {
            this.period = period;
            this.color = color;
        }
    }

    /** Listener for chart updates. */
    public interface OnChartUpdateListener {
        void onPriceUpdate(float price, float high24h, float low24h);
        void onTickerUpdate(float high24h, float low24h, float volBtc, float volUsdt, float changePercent);
        void onMaUpdate(List<Float> maValues);
        void onCountdownUpdate(String countdown);
        void onCandleSelected(Candle candle);
        void onNothingSelected();
    }

    /** Listener for volume bar clicks. */
    public interface OnVolumeClickListener {
        void onVolumeClick(Candle candle);
    }

    // --------------------------------------------------------------------
    // Listener fields
    // --------------------------------------------------------------------
    private OnChartUpdateListener updateListener;
    private OnVolumeClickListener volumeClickListener;

    public void setOnChartUpdateListener(OnChartUpdateListener listener) {
        this.updateListener = listener;
    }

    public void setOnVolumeClickListener(OnVolumeClickListener listener) {
        this.volumeClickListener = listener;
    }

    // --------------------------------------------------------------------
    // Data and paint objects
    // --------------------------------------------------------------------
    private List<Candle> data = new ArrayList<>();
    private List<MaLine> maLines = new ArrayList<>();

    private Paint bullishPaint;
    private Paint bearishPaint;
    private Paint wickBullishPaint;
    private Paint wickBearishPaint;
    private Paint gridPaint;
    private Paint textPaint;
    private Paint lastPriceLinePaint;
    private Paint lastPriceBgPaint;
    private Paint lastPriceTextPaint;
    private Paint movingAverage5Paint;
    private Paint movingAverage10Paint;
    private Paint movingAverage20Paint;
    private Paint volumeBullishPaint;
    private Paint volumeBearishPaint;
    private Paint selectedLinePaint;
    private List<Paint> maExtraPaints = new ArrayList<>();

    // --------------------------------------------------------------------
    // Hard constants (not configurable via layout) - view dimensions
    // --------------------------------------------------------------------
    private int DEFAULT_VISIBLE_CANDLE_COUNT = 80;
    private int MIN_VISIBLE_CANDLE_COUNT = 20;
    private int MAX_VISIBLE_CANDLE_COUNT = 150;
    private int TOP_PADDING_PX = 20;
    private int BOTTOM_PADDING_PX = 20;
    private int VOLUME_CHART_HEIGHT_DP = 80;
    private int VOLUME_TOP_MARGIN_PX = 10;
    private int PRICE_AXIS_WIDTH_DP = 60;
    private int FETCH_LIMIT = 300;
    private long LIVE_REFRESH_INTERVAL_MS = 5000;
    private long COUNTDOWN_INTERVAL_MS = 1000;

    // --------------------------------------------------------------------
    // Defaults from layout - set via setDefaultsFromLayout()
    // --------------------------------------------------------------------
    private boolean defaultsLoadedFromLayout = false;
    private float defBodyFraction;
    private float defWickWidthPx;
    private float defMaWidthPx;
    private int defVisibleCount;
    private boolean defShowGrid;
    private boolean defShowVolume;
    private boolean defShowLastPrice;
    private boolean defLastDashed;
    private float defPriceTextSizePx;
    private float defLastLineWidthPx;
    private float defLabelTextSizePx;
    private int defBullColor;
    private int defBearColor;
    private List<MaLine> defMaLines = new ArrayList<>();
    // Default colors for appearance that are not in the 5 layouts (grid, text, etc.) - use theme or fallback to layout's last price colors
    private int defLastPriceLineColor;
    private int defLastPriceBgColor;
    private int defPriceTextColor;
    private int defGridColor;
    private int defLabelTextColor;

    // --------------------------------------------------------------------
    // Preferences keys
    // --------------------------------------------------------------------
    private static final String PREFS_MA = "ma_prefs";
    private static final String KEY_MA = "ma_lines";
    private static final String PREFS_CANDLE = "candle_prefs";
    private static final String KEY_BULL = "bull_color";
    private static final String KEY_BEAR = "bear_color";

    private static final String PREFS_CHART = "chart_options_prefs";
    private static final String KEY_BODY_FRACTION = "body_fraction";
    private static final String KEY_WICK_WIDTH = "wick_width";
    private static final String KEY_MA_WIDTH = "ma_width";
    private static final String KEY_SHOW_GRID = "show_grid";
    private static final String KEY_SHOW_VOLUME = "show_volume";
    private static final String KEY_VISIBLE_COUNT = "visible_count";
    private static final String KEY_SHOW_LAST_PRICE = "show_last_price";
    private static final String KEY_LAST_PRICE_LINE_COLOR = "last_price_line_color";
    private static final String KEY_LAST_PRICE_BG_COLOR = "last_price_bg_color";
    private static final String KEY_PRICE_TEXT_SIZE = "price_text_size";
    private static final String KEY_PRICE_TEXT_COLOR = "price_text_color";
    private static final String KEY_GRID_COLOR = "grid_color";
    private static final String KEY_BG_COLOR = "bg_color";
    private static final String KEY_LAST_LINE_WIDTH = "last_line_width";
    private static final String KEY_LAST_LINE_DASH = "last_line_dash";
    private static final String KEY_LAST_LABEL_TEXT_SIZE = "last_label_text_size";
    private static final String KEY_LAST_LABEL_TEXT_COLOR = "last_label_text_color";

    // --------------------------------------------------------------------
    // Runtime state
    // --------------------------------------------------------------------
    private int visibleCandleCount;
    private float translationX = 0f;
    private float minPrice = 0f;
    private float maxPrice = 0f;
    private float lastPrice = 0f;
    private float maxVolume = 0f;
    private int selectedIndex = -1;
    private int startIndexCache = 0;
    private float extraOffsetX = 0f;

    // --------------------------------------------------------------------
    // Gesture detectors
    // --------------------------------------------------------------------
    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetector gestureDetector;

    // --------------------------------------------------------------------
    // Handlers and runnables for live updates
    // --------------------------------------------------------------------
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Handler liveHandler = new Handler(Looper.getMainLooper());
    private Handler countdownHandler = new Handler(Looper.getMainLooper());
    private Runnable liveRunnable;
    private Runnable countdownRunnable;

    // --------------------------------------------------------------------
    // Chart parameters
    // --------------------------------------------------------------------
    private String currentSymbol;
    private String currentInterval;
    private SimpleDateFormat timeFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.US);
    private long currentCandleCloseTime = 0L;
    private String fiatCode = "USD";
    private float fiatMultiplier = 1f;

    // --------------------------------------------------------------------
    // Configurable chart style settings
    // --------------------------------------------------------------------
    private int bullishColor;
    private int bearishColor;
    private float bodyWidthFraction;
    private float wickWidthPx;
    private float maLineWidthPx;
    private boolean showGrid;
    private boolean showVolume;
    private boolean showLastPriceLine;
    private int lastPriceLineColor;
    private int lastPriceBgColor;
    private float priceTextSizePx;
    private int priceTextColor;
    private int gridColor;
    private int bgColor;
    private float lastLineWidthPx;
    private boolean lastLineDashed;
    private float lastPriceLabelTextSizePx;
    private int lastPriceLabelTextColor;

    // --------------------------------------------------------------------
    // Constructors
    // --------------------------------------------------------------------
    public MarketChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        // Load view dimension constants from dimen that are NOT part of chart_defaults
        loadViewDimensions(context);
        visibleCandleCount = DEFAULT_VISIBLE_CANDLE_COUNT;
        // Defaults will be injected later from Activity via setDefaultsFromLayout()
        // Do not init colors/MA here with fallback XML, wait for layout defaults
        initGestures(context);
        // Paints will be initialized after defaults are set, or with placeholder
        initPaints(context);
    }

    // --------------------------------------------------------------------
    // Initialization helpers
    // --------------------------------------------------------------------
    private void loadViewDimensions(Context context) {
        // These are structural dimensions, not user-configurable chart options
        Resources res = context.getResources();
        try { TOP_PADDING_PX = res.getDimensionPixelSize(R.dimen.default_top_padding); } catch(Exception ignored){}
        try { BOTTOM_PADDING_PX = res.getDimensionPixelSize(R.dimen.default_bottom_padding); } catch(Exception ignored){}
        try { VOLUME_CHART_HEIGHT_DP = res.getDimensionPixelSize(R.dimen.default_volume_height); } catch(Exception ignored){}
        try { VOLUME_TOP_MARGIN_PX = res.getDimensionPixelSize(R.dimen.default_volume_top_margin); } catch(Exception ignored){}
        try { PRICE_AXIS_WIDTH_DP = res.getDimensionPixelSize(R.dimen.default_price_axis_width); } catch(Exception ignored){}
        try { FETCH_LIMIT = res.getInteger(R.integer.chart_fetch_limit); } catch(Exception ignored){}
        try { LIVE_REFRESH_INTERVAL_MS = res.getInteger(R.integer.chart_live_interval_ms); } catch(Exception ignored){}
        try { COUNTDOWN_INTERVAL_MS = res.getInteger(R.integer.chart_countdown_interval_ms); } catch(Exception ignored){}
    }

    /**
     * Called from MarketChartActivity after inflating chart_settings_popup.xml.
     * All defaults must come from layout, no fallback to R.array / R.integer.
     */
    public void setDefaultsFromLayout(float bodyFrac, float wickW, float maW, int visCount,
                                      boolean showG, boolean showV, boolean showLast, boolean dashed,
                                      float txtSize, float lastW, float labelSize,
                                      int bullColor, int bearColor, List<MaLine> maDefaults) {
        if (maDefaults == null || maDefaults.isEmpty()) {
            throw new IllegalStateException("defMaLines empty - check tvDefMaPeriods in layout");
        }
        this.defBodyFraction = bodyFrac;
        this.defWickWidthPx = wickW;
        this.defMaWidthPx = maW;
        this.defVisibleCount = visCount;
        this.defShowGrid = showG;
        this.defShowVolume = showV;
        this.defShowLastPrice = showLast;
        this.defLastDashed = dashed;
        this.defPriceTextSizePx = txtSize;
        this.defLastLineWidthPx = lastW;
        this.defLabelTextSizePx = labelSize;
        this.defBullColor = bullColor;
        this.defBearColor = bearColor;
        this.defMaLines = new ArrayList<>();
        for (MaLine m : maDefaults) this.defMaLines.add(new MaLine(m.period, m.color));

        // Use bull/bear colors also as fallback for line colors if not yet persisted
        this.defLastPriceLineColor = bullColor;
        this.defLastPriceBgColor = bullColor;
        this.defGridColor = 0xFF424242;
        this.defPriceTextColor = 0xFF9E9E9E;
        this.defLabelTextColor = 0xFFFFFFFF;

        DEFAULT_VISIBLE_CANDLE_COUNT = visCount;
        visibleCandleCount = defVisibleCount;

        defaultsLoadedFromLayout = true;

        // Now load persisted prefs or apply defaults
        initCandleColors(getContext());
        initMaLines(getContext());
        loadChartOptions(getContext());
        initPaints(getContext());
        invalidate();
    }

    private void ensureDefaultsLoaded() {
        if (!defaultsLoadedFromLayout) {
            throw new IllegalStateException("setDefaultsFromLayout() must be called before using MarketChartView - defaults come from layout");
        }
    }

    private void initCandleColors(Context context) {
        ensureDefaultsLoaded();
        try {
            SharedPreferences sp = context.getSharedPreferences(PREFS_CANDLE, Context.MODE_PRIVATE);
            bullishColor = sp.contains(KEY_BULL)? sp.getInt(KEY_BULL, defBullColor) : defBullColor;
            bearishColor = sp.contains(KEY_BEAR)? sp.getInt(KEY_BEAR, defBearColor) : defBearColor;
        } catch (Exception e) {
            bullishColor = defBullColor;
            bearishColor = defBearColor;
        }
    }

    private void loadChartOptions(Context context) {
        ensureDefaultsLoaded();
        try {
            SharedPreferences sp = context.getSharedPreferences(PREFS_CHART, Context.MODE_PRIVATE);
            bodyWidthFraction = sp.getFloat(KEY_BODY_FRACTION, defBodyFraction);
            wickWidthPx = sp.getFloat(KEY_WICK_WIDTH, defWickWidthPx);
            maLineWidthPx = sp.getFloat(KEY_MA_WIDTH, defMaWidthPx);
            showGrid = sp.getBoolean(KEY_SHOW_GRID, defShowGrid);
            showVolume = sp.getBoolean(KEY_SHOW_VOLUME, defShowVolume);
            visibleCandleCount = sp.getInt(KEY_VISIBLE_COUNT, defVisibleCount);
            showLastPriceLine = sp.getBoolean(KEY_SHOW_LAST_PRICE, defShowLastPrice);
            lastPriceLineColor = sp.contains(KEY_LAST_PRICE_LINE_COLOR)?
                    sp.getInt(KEY_LAST_PRICE_LINE_COLOR, defLastPriceLineColor) : defLastPriceLineColor;
            lastPriceBgColor = sp.contains(KEY_LAST_PRICE_BG_COLOR)?
                    sp.getInt(KEY_LAST_PRICE_BG_COLOR, defLastPriceBgColor) : defLastPriceBgColor;
            priceTextSizePx = sp.getFloat(KEY_PRICE_TEXT_SIZE, defPriceTextSizePx);
            priceTextColor = sp.contains(KEY_PRICE_TEXT_COLOR)?
                    sp.getInt(KEY_PRICE_TEXT_COLOR, defPriceTextColor) : defPriceTextColor;
            gridColor = sp.contains(KEY_GRID_COLOR)?
                    sp.getInt(KEY_GRID_COLOR, defGridColor) : defGridColor;
            bgColor = sp.contains(KEY_BG_COLOR)?
                    sp.getInt(KEY_BG_COLOR, 0) : 0;
            lastLineWidthPx = sp.getFloat(KEY_LAST_LINE_WIDTH, defLastLineWidthPx);
            lastLineDashed = sp.getBoolean(KEY_LAST_LINE_DASH, defLastDashed);
            lastPriceLabelTextSizePx = sp.getFloat(KEY_LAST_LABEL_TEXT_SIZE, defLabelTextSizePx);
            lastPriceLabelTextColor = sp.contains(KEY_LAST_LABEL_TEXT_COLOR)?
                    sp.getInt(KEY_LAST_LABEL_TEXT_COLOR, defLabelTextColor) : defLabelTextColor;
        } catch (Exception e) {
            bodyWidthFraction = defBodyFraction;
            wickWidthPx = defWickWidthPx;
            maLineWidthPx = defMaWidthPx;
            showGrid = defShowGrid;
            showVolume = defShowVolume;
            visibleCandleCount = defVisibleCount;
            showLastPriceLine = defShowLastPrice;
            lastPriceLineColor = defLastPriceLineColor;
            lastPriceBgColor = defLastPriceBgColor;
            priceTextSizePx = defPriceTextSizePx;
            priceTextColor = defPriceTextColor;
            gridColor = defGridColor;
            bgColor = 0;
            lastLineWidthPx = defLastLineWidthPx;
            lastLineDashed = defLastDashed;
            lastPriceLabelTextSizePx = defLabelTextSizePx;
            lastPriceLabelTextColor = defLabelTextColor;
        }
    }

    // --------------------------------------------------------------------
    // Public getters/setters (used by MarketChartActivity)
    // --------------------------------------------------------------------
    public int getBullishColor() { return bullishColor; }
    public int getBearishColor() { return bearishColor; }
    public float getBodyWidthFraction() { return bodyWidthFraction; }
    public float getWickWidthPx() { return wickWidthPx; }
    public float getMaLineWidthPx() { return maLineWidthPx; }
    public boolean isShowGrid() { return showGrid; }
    public boolean isShowVolume() { return showVolume; }
    public int getVisibleCandleCountValue() { return visibleCandleCount; }
    public boolean isShowLastPriceLine() { return showLastPriceLine; }
    public int getLastPriceLineColor() { return lastPriceLineColor; }
    public int getLastPriceBgColor() { return lastPriceBgColor; }
    public float getPriceTextSizePx() { return priceTextSizePx; }
    public int getPriceTextColor() { return priceTextColor; }
    public int getGridColor() { return gridColor; }
    public int getBgColor() { return bgColor; }
    public float getLastLineWidthPx() { return lastLineWidthPx; }
    public boolean isLastLineDashed() { return lastLineDashed; }
    public float getLastPriceLabelTextSizePx() { return lastPriceLabelTextSizePx; }
    public int getLastPriceLabelTextColor() { return lastPriceLabelTextColor; }

    public void setChartAppearance(boolean sLastPrice, int lastLineColor, int lastBgColor,
                                   float txtSize, int txtColor, int gColor, int bColor,
                                   float lastW, boolean lastDash) {
        this.showLastPriceLine = sLastPrice;
        this.lastPriceLineColor = lastLineColor;
        this.lastPriceBgColor = lastBgColor;
        this.priceTextSizePx = txtSize;
        this.priceTextColor = txtColor;
        this.gridColor = gColor;
        this.bgColor = bColor;
        this.lastLineWidthPx = lastW;
        this.lastLineDashed = lastDash;
        try {
            SharedPreferences sp = getContext().getSharedPreferences(PREFS_CHART, Context.MODE_PRIVATE);
            SharedPreferences.Editor ed = sp.edit();
            ed.putBoolean(KEY_SHOW_LAST_PRICE, sLastPrice);
            ed.putInt(KEY_LAST_PRICE_LINE_COLOR, lastLineColor);
            ed.putInt(KEY_LAST_PRICE_BG_COLOR, lastBgColor);
            ed.putFloat(KEY_PRICE_TEXT_SIZE, txtSize);
            ed.putInt(KEY_PRICE_TEXT_COLOR, txtColor);
            ed.putInt(KEY_GRID_COLOR, gColor);
            ed.remove(KEY_BG_COLOR);
            ed.putFloat(KEY_LAST_LINE_WIDTH, lastW);
            ed.putBoolean(KEY_LAST_LINE_DASH, lastDash);
            ed.apply();
        } catch (Exception e) { /* ignore */ }
        initPaints(getContext());
        invalidate();
    }

    public void setChartAppearance(boolean sLastPrice, int lastLineColor, int lastBgColor,
                                   float txtSize, int txtColor, int gColor) {
        setChartAppearance(sLastPrice, lastLineColor, lastBgColor, txtSize, txtColor, gColor,
                bgColor, lastLineWidthPx, lastLineDashed);
    }

    public void setLastPriceLabelAppearance(int bgColor, int textColor, float textSizePx) {
        this.lastPriceBgColor = bgColor;
        this.lastPriceLabelTextColor = textColor;
        this.lastPriceLabelTextSizePx = textSizePx;
        try {
            SharedPreferences sp = getContext().getSharedPreferences(PREFS_CHART, Context.MODE_PRIVATE);
            sp.edit()
                   .putInt(KEY_LAST_PRICE_BG_COLOR, bgColor)
                   .putInt(KEY_LAST_LABEL_TEXT_COLOR, textColor)
                   .putFloat(KEY_LAST_LABEL_TEXT_SIZE, textSizePx)
                   .apply();
        } catch (Exception e) { /* ignore */ }
        initPaints(getContext());
        invalidate();
    }

    public void setCandleColors(int bull, int bear) {
        this.bullishColor = bull;
        this.bearishColor = bear;
        try {
            SharedPreferences sp = getContext().getSharedPreferences(PREFS_CANDLE, Context.MODE_PRIVATE);
            sp.edit().putInt(KEY_BULL, bull).putInt(KEY_BEAR, bear).apply();
        } catch (Exception e) { /* ignore */ }
        initPaints(getContext());
        invalidate();
    }

    public void setChartOptions(float bodyFraction, float wickWidth, float maWidth,
                                boolean sGrid, boolean sVolume, int visCount) {
        this.bodyWidthFraction = bodyFraction;
        this.wickWidthPx = wickWidth;
        this.maLineWidthPx = maWidth;
        this.showGrid = sGrid;
        this.showVolume = sVolume;
        this.visibleCandleCount = visCount;
        clampVisibleCount();
        try {
            SharedPreferences sp = getContext().getSharedPreferences(PREFS_CHART, Context.MODE_PRIVATE);
            sp.edit()
                   .putFloat(KEY_BODY_FRACTION, bodyFraction)
                   .putFloat(KEY_WICK_WIDTH, wickWidth)
                   .putFloat(KEY_MA_WIDTH, maWidth)
                   .putBoolean(KEY_SHOW_GRID, sGrid)
                   .putBoolean(KEY_SHOW_VOLUME, sVolume)
                   .putInt(KEY_VISIBLE_COUNT, this.visibleCandleCount)
                   .apply();
        } catch (Exception e) { /* ignore */ }
        initPaints(getContext());
        clampTranslationX();
        invalidate();
    }

    private void clampVisibleCount() {
        if (visibleCandleCount < MIN_VISIBLE_CANDLE_COUNT) {
            visibleCandleCount = MIN_VISIBLE_CANDLE_COUNT;
        }
        if (visibleCandleCount > MAX_VISIBLE_CANDLE_COUNT) {
            visibleCandleCount = MAX_VISIBLE_CANDLE_COUNT;
        }
    }

    /**
     * Reset from layout-stored defaults, not from XML resources.
     */
    public void resetToDefaultsFromLayout() {
        ensureDefaultsLoaded();
        try {
            getContext().getSharedPreferences(PREFS_CHART, Context.MODE_PRIVATE).edit().clear().apply();
            getContext().getSharedPreferences(PREFS_CANDLE, Context.MODE_PRIVATE).edit().clear().apply();
            getContext().getSharedPreferences(PREFS_MA, Context.MODE_PRIVATE).edit().clear().apply();
        } catch (Exception e) { /* ignore */ }

        bullishColor = defBullColor;
        bearishColor = defBearColor;
        bodyWidthFraction = defBodyFraction;
        wickWidthPx = defWickWidthPx;
        maLineWidthPx = defMaWidthPx;
        showGrid = defShowGrid;
        showVolume = defShowVolume;
        visibleCandleCount = defVisibleCount;
        showLastPriceLine = defShowLastPrice;
        lastPriceLineColor = defLastPriceLineColor;
        lastPriceBgColor = defLastPriceBgColor;
        priceTextSizePx = defPriceTextSizePx;
        priceTextColor = defPriceTextColor;
        gridColor = defGridColor;
        lastLineWidthPx = defLastLineWidthPx;
        lastLineDashed = defLastDashed;
        lastPriceLabelTextSizePx = defLabelTextSizePx;
        lastPriceLabelTextColor = defLabelTextColor;

        maLines.clear();
        for (MaLine m : defMaLines) maLines.add(new MaLine(m.period, m.color));
        saveMaLines(getContext());

        // Persist defaults again for future loads
        setCandleColors(bullishColor, bearishColor);
        setChartOptions(bodyWidthFraction, wickWidthPx, maLineWidthPx, showGrid, showVolume, visibleCandleCount);
        setChartAppearance(showLastPriceLine, lastPriceLineColor, lastPriceBgColor,
                priceTextSizePx, priceTextColor, gridColor, bgColor, lastLineWidthPx, lastLineDashed);
        setLastPriceLabelAppearance(lastPriceBgColor, lastPriceLabelTextColor, lastPriceLabelTextSizePx);

        initPaints(getContext());
        clampTranslationX();
        invalidate();
        notifyMa();
    }

    // Deprecated: kept for compatibility, now delegates to layout version
    public void resetToDefaults() {
        resetToDefaultsFromLayout();
    }

    // --------------------------------------------------------------------
    // MA persistence
    // --------------------------------------------------------------------
    private void saveMaLines(Context context) {
        try {
            SharedPreferences sp = context.getSharedPreferences(PREFS_MA, Context.MODE_PRIVATE);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < maLines.size(); i++) {
                MaLine m = maLines.get(i);
                if (i > 0) sb.append(";");
                sb.append(m.period).append(",").append(m.color);
            }
            sp.edit().putString(KEY_MA, sb.toString()).apply();
        } catch (Exception e) { /* ignore */ }
    }

    private boolean loadMaLinesFromPrefs(Context context) {
        try {
            SharedPreferences sp = context.getSharedPreferences(PREFS_MA, Context.MODE_PRIVATE);
            String s = sp.getString(KEY_MA, null);
            if (s == null || s.isEmpty()) return false;
            String[] parts = s.split(";");
            List<MaLine> list = new ArrayList<>();
            for (String p : parts) {
                String[] kv = p.split(",");
                if (kv.length!= 2) continue;
                int period = Integer.parseInt(kv[0]);
                int color = Integer.parseInt(kv[1]);
                list.add(new MaLine(period, color));
            }
            if (!list.isEmpty()) {
                maLines = list;
                return true;
            }
        } catch (Exception e) { /* ignore */ }
        return false;
    }

    private void initMaLines(Context context) {
        // Try loading from prefs first
        if (loadMaLinesFromPrefs(context)) return;
        // Otherwise use defaults from layout
        ensureDefaultsLoaded();
        maLines.clear();
        for (MaLine m : defMaLines) maLines.add(new MaLine(m.period, m.color));
    }

    public List<MaLine> getMaLines() {
        return new ArrayList<>(maLines);
    }

    public void setMaLines(List<MaLine> list) {
        if (list == null) return;
        this.maLines = new ArrayList<>(list);
        saveMaLines(getContext());
        initPaints(getContext());
        invalidate();
        notifyMa();
    }

    // --------------------------------------------------------------------
    // Theme helpers
    // --------------------------------------------------------------------
    private int getThemeColor(int attr) {
        TypedValue tv = new TypedValue();
        getContext().getTheme().resolveAttribute(attr, tv, true);
        if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return tv.data;
        } else {
            try {
                return getResources().getColor(tv.resourceId, getContext().getTheme());
            } catch (Exception e) {
                return tv.data;
            }
        }
    }

    // --------------------------------------------------------------------
    // Paint initialization
    // --------------------------------------------------------------------
    private void initPaints(Context context) {
        Resources res = context.getResources();
        int themeBg = 0;
        try { themeBg = getThemeColor(android.R.attr.colorBackground); } catch(Exception ignored){ themeBg = 0xFF121212; }
        if (bgColor == 0) setBackgroundColor(themeBg);
        else setBackgroundColor(bgColor);

        // If defaults not loaded yet, use placeholder colors to avoid NPE
        int bull = bullishColor!= 0? bullishColor : (defaultsLoadedFromLayout? defBullColor : 0xFF26A69A);
        int bear = bearishColor!= 0? bearishColor : (defaultsLoadedFromLayout? defBearColor : 0xFFEF5350);

        bullishPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bullishPaint.setColor(bull);
        bullishPaint.setStyle(Paint.Style.FILL);

        bearishPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bearishPaint.setColor(bear);
        bearishPaint.setStyle(Paint.Style.FILL);

        volumeBullishPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        volumeBullishPaint.setColor(bull);
        volumeBullishPaint.setAlpha(255);
        volumeBullishPaint.setStyle(Paint.Style.FILL);

        volumeBearishPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        volumeBearishPaint.setColor(bear);
        volumeBearishPaint.setAlpha(255);
        volumeBearishPaint.setStyle(Paint.Style.FILL);

        float finalWickWidth = (wickWidthPx > 0f)? wickWidthPx : (defaultsLoadedFromLayout? defWickWidthPx : 2f);

        wickBullishPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wickBullishPaint.setColor(bull);
        wickBullishPaint.setStrokeWidth(finalWickWidth);

        wickBearishPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wickBearishPaint.setColor(bear);
        wickBearishPaint.setStrokeWidth(finalWickWidth);

        int gColor = gridColor!= 0? gridColor : (defaultsLoadedFromLayout? defGridColor : 0xFF424242);
        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(gColor);
        try { gridPaint.setStrokeWidth(res.getDimension(R.dimen.default_grid_width)); } catch(Exception e){ gridPaint.setStrokeWidth(1f); }

        int pColor = priceTextColor!= 0? priceTextColor : (defaultsLoadedFromLayout? defPriceTextColor : 0xFF9E9E9E);
        float pSize = priceTextSizePx > 0? priceTextSizePx : (defaultsLoadedFromLayout? defPriceTextSizePx : 18f);
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(pColor);
        textPaint.setTextSize(pSize);

        int lastLineColor = lastPriceLineColor!= 0? lastPriceLineColor : (defaultsLoadedFromLayout? defLastPriceLineColor : bull);
        float lastW = lastLineWidthPx > 0? lastLineWidthPx : (defaultsLoadedFromLayout? defLastLineWidthPx : 2f);
        lastPriceLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        lastPriceLinePaint.setColor(lastLineColor);
        lastPriceLinePaint.setStrokeWidth(lastW);
        lastPriceLinePaint.setStyle(Paint.Style.STROKE);
        if (lastLineDashed) {
            lastPriceLinePaint.setPathEffect(new DashPathEffect(new float[]{10f, 6f}, 0f));
        } else {
            lastPriceLinePaint.setPathEffect(null);
        }

        int lastBg = lastPriceBgColor!= 0? lastPriceBgColor : (defaultsLoadedFromLayout? defLastPriceBgColor : bull);
        lastPriceBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        lastPriceBgPaint.setColor(lastBg);
        lastPriceBgPaint.setStyle(Paint.Style.FILL);

        int labelTxtColor = lastPriceLabelTextColor!= 0? lastPriceLabelTextColor : (defaultsLoadedFromLayout? defLabelTextColor : 0xFFFFFFFF);
        float labelSize = lastPriceLabelTextSizePx > 0? lastPriceLabelTextSizePx : (defaultsLoadedFromLayout? defLabelTextSizePx : 19f);
        lastPriceTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        lastPriceTextPaint.setColor(labelTxtColor);
        lastPriceTextPaint.setTextSize(labelSize);
        lastPriceTextPaint.setFakeBoldText(true);

        float defaultMaWidth = 2f;
        try { defaultMaWidth = res.getDimension(R.dimen.default_ma_line_width); } catch(Exception ignored){}
        float thin = (maLineWidthPx > 0f)? maLineWidthPx : (defaultsLoadedFromLayout? defMaWidthPx : defaultMaWidth);

        movingAverage5Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        movingAverage5Paint.setStyle(Paint.Style.STROKE);
        movingAverage5Paint.setStrokeWidth(thin);
        movingAverage5Paint.setStrokeCap(Paint.Cap.ROUND);
        movingAverage5Paint.setStrokeJoin(Paint.Join.ROUND);

        movingAverage10Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        movingAverage10Paint.setStyle(Paint.Style.STROKE);
        movingAverage10Paint.setStrokeWidth(thin);
        movingAverage10Paint.setStrokeCap(Paint.Cap.ROUND);
        movingAverage10Paint.setStrokeJoin(Paint.Join.ROUND);

        movingAverage20Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        movingAverage20Paint.setStyle(Paint.Style.STROKE);
        movingAverage20Paint.setStrokeWidth(thin);
        movingAverage20Paint.setStrokeCap(Paint.Cap.ROUND);
        movingAverage20Paint.setStrokeJoin(Paint.Join.ROUND);

        maExtraPaints.clear();
        // Ensure maLines exists
        if (maLines.isEmpty() && defaultsLoadedFromLayout) {
            initMaLines(context);
        }
        for (int i = 0; i < maLines.size(); i++) {
            Paint p;
            if (i == 0) p = movingAverage5Paint;
            else if (i == 1) p = movingAverage10Paint;
            else if (i == 2) p = movingAverage20Paint;
            else {
                p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(thin);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeJoin(Paint.Join.ROUND);
                maExtraPaints.add(p);
            }
            p.setColor(maLines.get(i).color);
        }

        selectedLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        try { selectedLinePaint.setColor(res.getColor(R.color.chart_text, null)); } catch(Exception e){ selectedLinePaint.setColor(0xFF9E9E9E); }
        try { selectedLinePaint.setStrokeWidth(res.getDimension(R.dimen.default_selected_width)); } catch(Exception e){ selectedLinePaint.setStrokeWidth(2f); }
        selectedLinePaint.setAlpha(100);
    }

    // --------------------------------------------------------------------
    // Configuration change handling
    // --------------------------------------------------------------------
    @Override
    protected void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        loadViewDimensions(getContext());
        if (defaultsLoadedFromLayout) {
            loadChartOptions(getContext());
            initCandleColors(getContext());
        }
        initPaints(getContext());
        invalidate();
    }

    public void refreshTheme() {
        loadViewDimensions(getContext());
        if (defaultsLoadedFromLayout) {
            loadChartOptions(getContext());
            initCandleColors(getContext());
        }
        initPaints(getContext());
        invalidate();
    }

    // --------------------------------------------------------------------
    // Gestures
    // --------------------------------------------------------------------
    private void initGestures(Context context) {
        scaleGestureDetector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        visibleCandleCount = (int) (visibleCandleCount / detector.getScaleFactor());
                        clampVisibleCount();
                        clampTranslationX();
                        invalidate();
                        return true;
                    }
                });

        gestureDetector = new GestureDetector(context,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onScroll(MotionEvent e1, MotionEvent e2,
                                            float distanceX, float distanceY) {
                        if (data.isEmpty()) return false;
                        float density = getResources().getDisplayMetrics().density;
                        int priceAxisW = (int) (PRICE_AXIS_WIDTH_DP * density);
                        int chartW = getWidth() - priceAxisW;
                        if (chartW <= 0) return false;
                        translationX -= distanceX;
                        clampTranslationX();
                        if (selectedIndex!= -1) {
                            selectedIndex = -1;
                            if (updateListener!= null) updateListener.onNothingSelected();
                        }
                        invalidate();
                        return true;
                    }

                    @Override
                    public boolean onSingleTapUp(MotionEvent e) {
                        if (data.isEmpty()) return false;
                        float density = getResources().getDisplayMetrics().density;
                        int priceAxisW = (int) (PRICE_AXIS_WIDTH_DP * density);
                        int chartW = getWidth() - priceAxisW;
                        int count = Math.min(visibleCandleCount, data.size());
                        if (count == 0) return false;
                        if (e.getX() > chartW) {
                            if (selectedIndex!= -1) {
                                selectedIndex = -1;
                                if (updateListener!= null) updateListener.onNothingSelected();
                                invalidate();
                            }
                            return false;
                        }
                        float candleWidth = chartW / (float) count;
                        float xWithOffset = e.getX() - extraOffsetX;
                        int index = (int) (xWithOffset / candleWidth) + startIndexCache;
                        if (index >= 0 && index < data.size()) {
                            selectedIndex = index;
                            if (updateListener!= null) {
                                updateListener.onCandleSelected(data.get(index));
                            }
                            if (volumeClickListener!= null) {
                                volumeClickListener.onVolumeClick(data.get(index));
                            }
                            invalidate();
                        } else {
                            selectedIndex = -1;
                            if (updateListener!= null) updateListener.onNothingSelected();
                            invalidate();
                        }
                        return true;
                    }
                });
    }

    private void clampTranslationX() {
        if (data.isEmpty()) {
            translationX = 0f;
            extraOffsetX = 0f;
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        int priceAxisW = (int) (PRICE_AXIS_WIDTH_DP * density);
        int chartW = getWidth() - priceAxisW;
        if (chartW <= 0) return;
        int count = Math.min(visibleCandleCount, data.size());
        float candleWidth = chartW / (float) count;
        float maxScroll = (data.size() - count) * candleWidth;
        float minScroll = -chartW * 0.6f;

        if (translationX < minScroll) translationX = minScroll;
        if (translationX > maxScroll) translationX = maxScroll;

        if (translationX < 0f) {
            extraOffsetX = translationX;
        } else {
            extraOffsetX = 0f;
        }
    }

    // --------------------------------------------------------------------
    // Chart loading and live updates
    // --------------------------------------------------------------------
    public void loadChart(String symbol, String interval) {
        this.currentSymbol = symbol;
        this.currentInterval = interval;
        this.selectedIndex = -1;
        this.translationX = 0f;
        this.extraOffsetX = 0f;
        stopLive();
        fetchCandles();
        startLive();
        startCountdown();
    }

    public void setFiatCode(String code) {
        this.fiatCode = code;
    }

    public float getFiatMultiplier() {
        return fiatMultiplier;
    }

    public void setFiatMultiplier(float mult) {
        if (mult <= 0f) mult = 1f;
        this.fiatMultiplier = mult;
        invalidate();
    }

    public void setCountdown(String text) {
        invalidate();
    }

    private long getIntervalMillis(String interval) {
        if (interval == null) return 60_000L;
        switch (interval) {
            case "1m": return 60_000L;
            case "3m": return 3L * 60_000L;
            case "5m": return 5L * 60_000L;
            case "15m": return 15L * 60_000L;
            case "30m": return 30L * 60_000L;
            case "1h": return 60L * 60_000L;
            case "2h": return 2L * 60L * 60_000L;
            case "4h": return 4L * 60L * 60_000L;
            case "6h": return 6L * 60L * 60_000L;
            case "12h": return 12L * 60L * 60_000L;
            case "1d": return 24L * 60L * 60_000L;
            case "3d": return 3L * 24L * 60L * 60_000L;
            case "1w": return 7L * 24L * 60L * 60_000L;
            case "1M": return 30L * 24L * 60L * 60_000L;
            default: return 60_000L;
        }
    }

    private void startCountdown() {
        if (countdownRunnable!= null) countdownHandler.removeCallbacks(countdownRunnable);
        countdownRunnable = new Runnable() {
            @Override
            public void run() {
                if (currentCandleCloseTime > 0L) {
                    long now = System.currentTimeMillis();
                    long remain = currentCandleCloseTime - now;
                    if (remain < 0L) remain = 0L;
                    long seconds = (remain / 1000L) % 60L;
                    long minutes = (remain / 1000L / 60L) % 60L;
                    long hours = remain / 1000L / 60L / 60L;
                    String text;
                    if (getIntervalMillis(currentInterval) >= 24L * 60L * 60_000L) {
                        text = String.format(Locale.US, "%02d:%02d:%02d:%02d",
                                hours / 24L, hours % 24L, minutes, seconds);
                    } else {
                        text = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
                    }
                    if (updateListener!= null) updateListener.onCountdownUpdate(text);
                }
                countdownHandler.postDelayed(this, COUNTDOWN_INTERVAL_MS);
            }
        };
        countdownHandler.post(countdownRunnable);
    }

    private void startLive() {
        if (liveRunnable!= null) liveHandler.removeCallbacks(liveRunnable);
        liveRunnable = new Runnable() {
            @Override
            public void run() {
                fetchPriceAndCandle();
                liveHandler.postDelayed(this, LIVE_REFRESH_INTERVAL_MS);
            }
        };
        liveHandler.post(liveRunnable);
    }

    private void stopLive() {
        if (liveRunnable!= null) liveHandler.removeCallbacks(liveRunnable);
        if (countdownRunnable!= null) countdownHandler.removeCallbacks(countdownRunnable);
    }

    private void notifyMa() {
        if (data.isEmpty() || updateListener == null) return;
        int last = data.size() - 1;
        List<Float> values = new ArrayList<>();
        for (int i = 0; i < maLines.size(); i++) {
            values.add(calculateMovingAverage(last, maLines.get(i).period));
        }
        updateListener.onMaUpdate(values);
    }

    // --------------------------------------------------------------------
    // Data fetching (Binance API)
    // --------------------------------------------------------------------
    private void fetchCandles() {
        if (currentSymbol == null || currentInterval == null) return;
        new Thread(() -> {
            try {
                String urlString = String.format(Locale.US,
                        "https://api.binance.com/api/v3/klines?symbol=%s&interval=%s&limit=%d",
                        currentSymbol, currentInterval, FETCH_LIMIT);
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()));
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine())!= null) builder.append(line);
                reader.close();
                JSONArray jsonArray = new JSONArray(builder.toString());
                List<Candle> newData = new ArrayList<>(jsonArray.length());
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONArray kline = jsonArray.getJSONArray(i);
                    float open = (float) kline.getDouble(1);
                    float high = (float) kline.getDouble(2);
                    float low = (float) kline.getDouble(3);
                    float close = (float) kline.getDouble(4);
                    float volume = (float) kline.getDouble(5);
                    long openTime = kline.getLong(0);
                    long closeTime = kline.getLong(6);
                    newData.add(new Candle(open, high, low, close, volume, openTime, closeTime));
                }
                mainHandler.post(() -> {
                    data = newData;
                    clampTranslationX();
                    if (!data.isEmpty()) {
                        minPrice = Float.MAX_VALUE;
                        maxPrice = Float.MIN_VALUE;
                        maxVolume = 0f;
                        for (Candle candle : data) {
                            if (candle.low < minPrice) minPrice = candle.low;
                            if (candle.high > maxPrice) maxPrice = candle.high;
                            if (candle.volume > maxVolume) maxVolume = candle.volume;
                        }
                        lastPrice = data.get(data.size() - 1).close;
                        currentCandleCloseTime = data.get(data.size() - 1).closeTime;
                        float padding = (maxPrice - minPrice) * 0.08f;
                        minPrice -= padding;
                        maxPrice += padding;
                        if (updateListener!= null) {
                            updateListener.onPriceUpdate(lastPrice, maxPrice, minPrice);
                        }
                        notifyMa();
                    }
                    invalidate();
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void fetchPriceAndCandle() {
        if (currentSymbol == null) return;
        new Thread(() -> {
            try {
                String tickerUrl = String.format(Locale.US,
                        "https://api.binance.com/api/v3/ticker/24hr?symbol=%s", currentSymbol);
                HttpURLConnection connection = (HttpURLConnection) new URL(tickerUrl).openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()));
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine())!= null) builder.append(line);
                reader.close();
                JSONObject jsonObject = new JSONObject(builder.toString());
                float price = (float) jsonObject.getDouble("lastPrice");
                float high = (float) jsonObject.getDouble("highPrice");
                float low = (float) jsonObject.getDouble("lowPrice");
                float volBtc = (float) jsonObject.getDouble("volume");
                float volUsdt = (float) jsonObject.getDouble("quoteVolume");
                float changePercent = (float) jsonObject.getDouble("priceChangePercent");
                mainHandler.post(() -> {
                    if (!data.isEmpty()) {
                        Candle lastCandle = data.get(data.size() - 1);
                        Candle updatedCandle = new Candle(
                                lastCandle.open,
                                Math.max(lastCandle.high, price),
                                Math.min(lastCandle.low, price),
                                price,
                                lastCandle.volume,
                                lastCandle.openTime,
                                lastCandle.closeTime
                        );
                        data.set(data.size() - 1, updatedCandle);
                        lastPrice = price;
                        currentCandleCloseTime = updatedCandle.closeTime;
                        if (System.currentTimeMillis() >= updatedCandle.closeTime) {
                            fetchCandles();
                        } else {
                            invalidate();
                        }
                        if (updateListener!= null) {
                            updateListener.onPriceUpdate(price, high, low);
                            updateListener.onTickerUpdate(high, low, volBtc, volUsdt, changePercent);
                        }
                        notifyMa();
                    }
                });
            } catch (Exception e) { /* ignore */ }
        }).start();
    }

    // --------------------------------------------------------------------
    // Moving average calculation
    // --------------------------------------------------------------------
    private float calculateMovingAverage(int currentIndex, int period) {
        if (currentIndex < period - 1 || data.isEmpty()) return 0f;
        float sum = 0f;
        for (int i = 0; i < period; i++) {
            sum += data.get(currentIndex - i).close;
        }
        return sum / period;
    }

    // --------------------------------------------------------------------
    // Touch handling
    // --------------------------------------------------------------------
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleGestureDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float density = getResources().getDisplayMetrics().density;
            int priceAxisW = (int) (PRICE_AXIS_WIDTH_DP * density);
            int chartW = getWidth() - priceAxisW;
            if (event.getX() > chartW) {
                if (selectedIndex!= -1) {
                    selectedIndex = -1;
                    if (updateListener!= null) updateListener.onNothingSelected();
                    invalidate();
                }
                return true;
            }
        }
        return true;
    }

    // --------------------------------------------------------------------
    // Drawing
    // --------------------------------------------------------------------
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        int priceAxisWidth = (int) (PRICE_AXIS_WIDTH_DP * density);
        int timeAxisHeight = (int) (20 * density);
        int volumeHeightPx = (int) (VOLUME_CHART_HEIGHT_DP * density);

        int fullWidth = getWidth();
        int fullHeight = getHeight();
        int chartWidth = fullWidth - priceAxisWidth;
        int priceChartHeight = fullHeight - TOP_PADDING_PX - BOTTOM_PADDING_PX
                - VOLUME_TOP_MARGIN_PX - volumeHeightPx - timeAxisHeight;

        if (priceChartHeight <= 0) return;

        drawGrid(canvas, chartWidth, priceChartHeight, volumeHeightPx);

        if (data.isEmpty()) {
            String loadingText = getResources().getString(R.string.chart_loading);
            canvas.drawText(loadingText, chartWidth / 2f - 100f, fullHeight / 2f, textPaint);
            return;
        }

        int count = Math.min(visibleCandleCount, data.size());
        float candleWidth = chartWidth / (float) count;
        int startIndex = calcStartIndex(count, candleWidth);

        DrawInfo info = new DrawInfo();
        info.chartWidth = chartWidth;
        info.fullWidth = fullWidth;
        info.priceChartHeight = priceChartHeight;
        info.volumeHeightPx = volumeHeightPx;
        info.count = count;
        info.startIndex = startIndex;
        info.candleWidth = candleWidth;
        info.displayMin = minPrice * fiatMultiplier;
        info.displayMax = maxPrice * fiatMultiplier;
        if (info.displayMax - info.displayMin == 0f) {
            info.displayMax = info.displayMin + 1f;
        }

        drawCandles(canvas, info);
        drawMovingAverages(canvas, info);
        drawSelectedLine(canvas, info);
        drawLastPriceLine(canvas, info);
        drawPriceAxis(canvas, info);
        drawVolumeAndTime(canvas, info);
    }

    private static class DrawInfo {
        int chartWidth;
        int fullWidth;
        int priceChartHeight;
        int volumeHeightPx;
        int count;
        int startIndex;
        float candleWidth;
        float displayMin;
        float displayMax;
    }

    private int calcStartIndex(int count, float candleWidth) {
        int startIndex;
        if (translationX >= 0f) {
            startIndex = data.size() - count - (int) (translationX / candleWidth);
        } else {
            startIndex = data.size() - count;
        }
        if (startIndex < 0) startIndex = 0;
        if (startIndex + count > data.size()) startIndex = data.size() - count;
        if (startIndex < 0) startIndex = 0;
        startIndexCache = startIndex;
        return startIndex;
    }

    private void drawGrid(Canvas canvas, int chartWidth, int priceChartHeight, int volumeHeightPx) {
        if (!showGrid) {
            canvas.drawLine(chartWidth, 0f, chartWidth, getHeight(), gridPaint);
            return;
        }
        for (int i = 0; i <= 4; i++) {
            float y = TOP_PADDING_PX + priceChartHeight * i / 4f;
            canvas.drawLine(0f, y, chartWidth, y, gridPaint);
        }
        float volumeSeparatorY = TOP_PADDING_PX + priceChartHeight + VOLUME_TOP_MARGIN_PX;
        canvas.drawLine(0f, volumeSeparatorY, chartWidth, volumeSeparatorY, gridPaint);
        canvas.drawLine(chartWidth, 0f, chartWidth, getHeight(), gridPaint);
    }

    private void drawCandles(Canvas canvas, DrawInfo info) {
        float bodyFraction = 0.48f;
        float finalBodyFraction = bodyWidthFraction > 0? bodyWidthFraction : bodyFraction;
        float bodyWidth = info.candleWidth * finalBodyFraction;
        float minBody = 2f;
        float maxBody = 20f;
        try { minBody = getResources().getDimension(R.dimen.default_body_min_width); } catch(Exception ignored){}
        try { maxBody = getResources().getDimension(R.dimen.default_body_max_width); } catch(Exception ignored){}
        if (bodyWidth < minBody) bodyWidth = minBody;
        if (bodyWidth > maxBody) bodyWidth = maxBody;

        float priceRange = info.displayMax - info.displayMin;
        if (priceRange == 0f) priceRange = 1f;

        for (int i = 0; i < info.count; i++) {
            int dataIndex = info.startIndex + i;
            if (dataIndex >= data.size()) break;
            Candle candle = data.get(dataIndex);
            float x = i * info.candleWidth + info.candleWidth / 2f + extraOffsetX;

            float highY = TOP_PADDING_PX + info.priceChartHeight
                    - ((candle.high * fiatMultiplier - info.displayMin) / priceRange * info.priceChartHeight);
            float lowY = TOP_PADDING_PX + info.priceChartHeight
                    - ((candle.low * fiatMultiplier - info.displayMin) / priceRange * info.priceChartHeight);
            float openY = TOP_PADDING_PX + info.priceChartHeight
                    - ((candle.open * fiatMultiplier - info.displayMin) / priceRange * info.priceChartHeight);
            float closeY = TOP_PADDING_PX + info.priceChartHeight
                    - ((candle.close * fiatMultiplier - info.displayMin) / priceRange * info.priceChartHeight);

            boolean isBullish = candle.close >= candle.open;
            Paint currentWickPaint = isBullish? wickBullishPaint : wickBearishPaint;
            Paint bodyPaint = isBullish? bullishPaint : bearishPaint;

            canvas.drawLine(x, highY, x, lowY, currentWickPaint);

            float top = Math.min(openY, closeY);
            float bottom = Math.max(openY, closeY);
            float minH = 2f;
            try { minH = getResources().getDimension(R.dimen.default_candle_min_height); } catch(Exception ignored){}
            if (Math.abs(bottom - top) < minH) {
                bottom = top + minH;
            }
            canvas.drawRect(x - bodyWidth / 2f, top, x + bodyWidth / 2f, bottom, bodyPaint);
        }
    }

    private void drawMovingAverages(Canvas canvas, DrawInfo info) {
        float priceRange = info.displayMax - info.displayMin;
        if (priceRange == 0f) priceRange = 1f;

        for (int maIndex = 0; maIndex < maLines.size(); maIndex++) {
            MaLine maLine = maLines.get(maIndex);
            int period = maLine.period;
            Paint paint;
            if (maIndex == 0) paint = movingAverage5Paint;
            else if (maIndex == 1) paint = movingAverage10Paint;
            else if (maIndex == 2) paint = movingAverage20Paint;
            else {
                int extraIdx = maIndex - 3;
                paint = (extraIdx < maExtraPaints.size())? maExtraPaints.get(extraIdx) : movingAverage20Paint;
            }
            paint.setColor(maLine.color);

            float previousX = 0f, previousY = 0f;
            boolean isFirstPoint = true;
            for (int i = 0; i < info.count; i++) {
                int dataIndex = info.startIndex + i;
                if (dataIndex >= data.size()) break;
                float movingAverage = calculateMovingAverage(dataIndex, period);
                if (movingAverage == 0f) continue;
                float x = i * info.candleWidth + info.candleWidth / 2f + extraOffsetX;
                float y = TOP_PADDING_PX + info.priceChartHeight
                        - ((movingAverage * fiatMultiplier - info.displayMin) / priceRange * info.priceChartHeight);
                if (!isFirstPoint) {
                    canvas.drawLine(previousX, previousY, x, y, paint);
                }
                previousX = x;
                previousY = y;
                isFirstPoint = false;
            }
        }
    }

    private void drawSelectedLine(Canvas canvas, DrawInfo info) {
        if (selectedIndex >= info.startIndex && selectedIndex < info.startIndex + info.count) {
            float selectedX = (selectedIndex - info.startIndex) * info.candleWidth
                    + info.candleWidth / 2f + extraOffsetX;
            canvas.drawLine(selectedX, TOP_PADDING_PX, selectedX,
                    TOP_PADDING_PX + info.priceChartHeight, selectedLinePaint);
        }
    }

    private void drawLastPriceLine(Canvas canvas, DrawInfo info) {
        if (lastPrice <= 0f ||!showLastPriceLine) return;
        float priceRange = info.displayMax - info.displayMin;
        if (priceRange == 0f) priceRange = 1f;

        float lastPriceY = TOP_PADDING_PX + info.priceChartHeight
                - ((lastPrice * fiatMultiplier - info.displayMin) / priceRange * info.priceChartHeight);
        canvas.drawLine(0f, lastPriceY, info.chartWidth, lastPriceY, lastPriceLinePaint);

        boolean isBigFiat = fiatMultiplier > 100f;
        String fmt = isBigFiat? "%,.0f" : "%,.2f";

        float labelH = 12f;
        try { labelH = getResources().getDimension(R.dimen.default_price_text_offset)
                + getResources().getDimension(R.dimen.default_text_size); } catch(Exception ignored){}
        float top = lastPriceY - labelH;
        float bottom = lastPriceY + labelH;
        canvas.drawRect(info.chartWidth, top, info.fullWidth, bottom, lastPriceBgPaint);

        String label = String.format(Locale.US, fmt, lastPrice * fiatMultiplier);
        float tx = info.chartWidth + 4f;
        float ty = lastPriceY + 6f;
        try {
            tx = info.chartWidth + getResources().getDimension(R.dimen.default_price_text_margin) / 2f;
            ty = lastPriceY + getResources().getDimension(R.dimen.default_text_size) / 3f;
        } catch(Exception ignored){}
        canvas.drawText(label, tx, ty, lastPriceTextPaint);
    }

    private void drawPriceAxis(Canvas canvas, DrawInfo info) {
        boolean isBigFiatAxis = fiatMultiplier > 100f;
        String axisFmt = isBigFiatAxis? "%,.0f" : "%,.2f";
        for (int i = 0; i <= 4; i++) {
            float price = info.displayMax - (info.displayMax - info.displayMin) * i / 4f;
            float y = TOP_PADDING_PX + info.priceChartHeight * i / 4f + 8f;
            try { y = TOP_PADDING_PX + info.priceChartHeight * i / 4f
                    + getResources().getDimension(R.dimen.default_price_text_offset); } catch(Exception ignored){}
            String priceText = String.format(Locale.US, axisFmt, price);
            float x = info.chartWidth + 4f;
            try { x = info.chartWidth + getResources().getDimension(R.dimen.default_price_text_margin); } catch(Exception ignored){}
            canvas.drawText(priceText, x, y, textPaint);
        }
    }

    private void drawVolumeAndTime(Canvas canvas, DrawInfo info) {
        float density = getResources().getDisplayMetrics().density;
        float volumeTop = TOP_PADDING_PX + info.priceChartHeight + VOLUME_TOP_MARGIN_PX;
        if (maxVolume == 0f) maxVolume = 1f;

        float bodyFraction = 0.48f;
        float finalBodyFraction = bodyWidthFraction > 0? bodyWidthFraction : bodyFraction;
        float bodyWidth = info.candleWidth * finalBodyFraction;
        float minBody = 2f, maxBody = 20f;
        try { minBody = getResources().getDimension(R.dimen.default_body_min_width); } catch(Exception ignored){}
        try { maxBody = getResources().getDimension(R.dimen.default_body_max_width); } catch(Exception ignored){}
        if (bodyWidth < minBody) bodyWidth = minBody;
        if (bodyWidth > maxBody) bodyWidth = maxBody;

        if (showVolume) {
            for (int i = 0; i < info.count; i++) {
                int dataIndex = info.startIndex + i;
                if (dataIndex >= data.size()) break;
                Candle candle = data.get(dataIndex);
                float x = i * info.candleWidth + info.candleWidth / 2f + extraOffsetX;
                float volumeBarHeight = info.volumeHeightPx * (candle.volume / maxVolume);
                Paint volumePaint = (candle.close >= candle.open)? volumeBullishPaint : volumeBearishPaint;
                canvas.drawRect(x - bodyWidth / 2f,
                        volumeTop + info.volumeHeightPx - volumeBarHeight,
                        x + bodyWidth / 2f,
                        volumeTop + info.volumeHeightPx,
                        volumePaint);
            }
        }

        for (int i = 0; i < info.count; i += Math.max(1, info.count / 4)) {
            int dataIndex = info.startIndex + i;
            if (dataIndex >= data.size()) break;
            float x = i * info.candleWidth + extraOffsetX;
            String timeText = timeFormat.format(new Date(data.get(dataIndex).openTime));
            float timeY = volumeTop + info.volumeHeightPx + 16 * density;
            if (!showVolume) {
                timeY = TOP_PADDING_PX + info.priceChartHeight + VOLUME_TOP_MARGIN_PX + 16 * density;
            }
            canvas.drawText(timeText, x, timeY, textPaint);
        }
    }

    // --------------------------------------------------------------------
    // Lifecycle
    // --------------------------------------------------------------------
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopLive();
    }
}
