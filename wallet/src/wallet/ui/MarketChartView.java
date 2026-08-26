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

public class MarketChartView extends View
{
    public static class Candle
    {
        public final float open;
        public final float high;
        public final float low;
        public final float close;
        public final float volume;
        public final long openTime;
        public final long closeTime;

        public Candle(float open, float high, float low, float close, float volume, long openTime, long closeTime)
        {
            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
            this.volume = volume;
            this.openTime = openTime;
            this.closeTime = closeTime;
        }
    }

    public static class MaLine
    {
        public int period;
        public int color;

        public MaLine(int period, int color)
        {
            this.period = period;
            this.color = color;
        }
    }

    public interface OnChartUpdateListener
    {
        void onPriceUpdate(float price, float high24h, float low24h);
        void onTickerUpdate(float high24h, float low24h, float volBtc, float volUsdt, float changePercent);
        void onMaUpdate(List<Float> maValues);
        void onCountdownUpdate(String countdown);
        void onCandleSelected(Candle candle);
        void onNothingSelected();
    }

    public interface OnVolumeClickListener
    {
        void onVolumeClick(Candle candle);
    }

    private OnChartUpdateListener updateListener;
    private OnVolumeClickListener volumeClickListener;

    public void setOnChartUpdateListener(OnChartUpdateListener listener)
    {
        this.updateListener = listener;
    }

    public void setOnVolumeClickListener(OnVolumeClickListener listener)
    {
        this.volumeClickListener = listener;
    }

    private List<Candle> data = new ArrayList<>();
    private List<MaLine> maLines = new ArrayList<>();

    private Paint bullishPaint;
    private Paint bearishPaint;
    private Paint wickPaint;
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

    private int DEFAULT_VISIBLE_CANDLE_COUNT;
    private int MIN_VISIBLE_CANDLE_COUNT;
    private int MAX_VISIBLE_CANDLE_COUNT;
    private int TOP_PADDING_PX;
    private int BOTTOM_PADDING_PX;
    private int VOLUME_CHART_HEIGHT_DP;
    private int VOLUME_TOP_MARGIN_PX;
    private int PRICE_AXIS_WIDTH_DP;
    private int FETCH_LIMIT;
    private long LIVE_REFRESH_INTERVAL_MS;
    private long COUNTDOWN_INTERVAL_MS;
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

    private int visibleCandleCount;
    private float translationX = 0f;
    private float minPrice = 0f;
    private float maxPrice = 0f;
    private float lastPrice = 0f;
    private float maxVolume = 0f;
    private int selectedIndex = -1;
    private int startIndexCache = 0;
    private float extraOffsetX = 0f;

    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetector gestureDetector;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Handler liveHandler = new Handler(Looper.getMainLooper());
    private Handler countdownHandler = new Handler(Looper.getMainLooper());
    private Runnable liveRunnable;
    private Runnable countdownRunnable;

    private String currentSymbol;
    private String currentInterval;
    private SimpleDateFormat timeFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.US);
    private long currentCandleCloseTime = 0L;
    private String fiatCode = "USD";
    private float fiatMultiplier = 1f;

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

    public MarketChartView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        loadDefaultsFromXml(context);
        initMaLines(context);
        initCandleColors(context);
        loadChartOptions(context);
        initPaints(context);
        initGestures(context);
    }

    private void loadDefaultsFromXml(Context context)
    {
        Resources res = context.getResources();
        DEFAULT_VISIBLE_CANDLE_COUNT = res.getInteger(R.integer.default_visible_candle_count);
        MIN_VISIBLE_CANDLE_COUNT = res.getInteger(R.integer.default_min_visible_candle_count);
        MAX_VISIBLE_CANDLE_COUNT = res.getInteger(R.integer.default_max_visible_candle_count);
        TOP_PADDING_PX = res.getDimensionPixelSize(R.dimen.chart_top_padding);
        BOTTOM_PADDING_PX = res.getDimensionPixelSize(R.dimen.chart_bottom_padding);
        VOLUME_CHART_HEIGHT_DP = res.getDimensionPixelSize(R.dimen.chart_volume_height);
        VOLUME_TOP_MARGIN_PX = res.getDimensionPixelSize(R.dimen.chart_volume_top_margin);
        PRICE_AXIS_WIDTH_DP = res.getDimensionPixelSize(R.dimen.chart_price_axis_width);
        FETCH_LIMIT = res.getInteger(R.integer.chart_fetch_limit);
        LIVE_REFRESH_INTERVAL_MS = res.getInteger(R.integer.chart_live_interval_ms);
        COUNTDOWN_INTERVAL_MS = res.getInteger(R.integer.chart_countdown_interval_ms);
        visibleCandleCount = DEFAULT_VISIBLE_CANDLE_COUNT;
    }

    private void initCandleColors(Context context)
    {
        try
        {
            SharedPreferences sp = context.getSharedPreferences(PREFS_CANDLE, Context.MODE_PRIVATE);
            Resources res = context.getResources();
            int defBull = res.getColor(R.color.default_bullish, null);
            int defBear = res.getColor(R.color.default_bearish, null);
            TypedArray palette = res.obtainTypedArray(R.array.candle_color_palette);
            if (palette.length() > 3)
            {
                defBull = palette.getColor(3, defBull);
            }
            if (palette.length() > 4)
            {
                defBear = palette.getColor(4, defBear);
            }
            palette.recycle();
            bullishColor = sp.getInt(KEY_BULL, defBull);
            bearishColor = sp.getInt(KEY_BEAR, defBear);
        }
        catch (Exception e)
        {
            try
            {
                Resources res = context.getResources();
                TypedArray palette = res.obtainTypedArray(R.array.candle_color_palette);
                bullishColor = palette.getColor(3, res.getColor(R.color.default_bullish, null));
                bearishColor = palette.getColor(4, res.getColor(R.color.default_bearish, null));
                palette.recycle();
            }
            catch (Exception ex)
            {
                bullishColor = context.getResources().getColor(R.color.default_bullish, null);
                bearishColor = context.getResources().getColor(R.color.default_bearish, null);
            }
        }
    }

    private void loadChartOptions(Context context)
    {
        try
        {
            Resources res = context.getResources();
            SharedPreferences sp = context.getSharedPreferences(PREFS_CHART, Context.MODE_PRIVATE);
            float defBody = res.getInteger(R.integer.default_body_fraction_percent) / 100f;
            float defWick = res.getDimension(R.dimen.default_wick_width);
            float defMaW = res.getDimension(R.dimen.default_ma_line_width);
            float defTxt = res.getDimension(R.dimen.default_price_text_size);
            float defLastW = res.getDimension(R.dimen.default_last_line_width);
            float defLabel = res.getDimension(R.dimen.default_last_label_text_size);
            int defVis = res.getInteger(R.integer.default_visible_candle_count);
            boolean defGrid = res.getBoolean(R.bool.default_show_grid);
            boolean defVol = res.getBoolean(R.bool.default_show_volume);
            boolean defLast = res.getBoolean(R.bool.default_show_last_price);
            boolean defDash = res.getBoolean(R.bool.default_last_line_dashed);
            int defLastColor = res.getColor(R.color.default_last_price_line, null);
            int defLastBg = res.getColor(R.color.default_last_price_bg, null);
            int defGridColor = res.getColor(R.color.default_grid, null);
            int defPriceTxt = res.getColor(R.color.default_price_text, null);
            int defLabelTxt = res.getColor(R.color.default_last_label_text, null);
            int defBg = res.getColor(R.color.default_chart_bg, null);
            bodyWidthFraction = sp.getFloat(KEY_BODY_FRACTION, defBody);
            wickWidthPx = sp.getFloat(KEY_WICK_WIDTH, defWick);
            maLineWidthPx = sp.getFloat(KEY_MA_WIDTH, defMaW);
            showGrid = sp.getBoolean(KEY_SHOW_GRID, defGrid);
            showVolume = sp.getBoolean(KEY_SHOW_VOLUME, defVol);
            visibleCandleCount = sp.getInt(KEY_VISIBLE_COUNT, defVis);
            showLastPriceLine = sp.getBoolean(KEY_SHOW_LAST_PRICE, defLast);
            lastPriceLineColor = sp.getInt(KEY_LAST_PRICE_LINE_COLOR, defLastColor);
            lastPriceBgColor = sp.getInt(KEY_LAST_PRICE_BG_COLOR, defLastBg);
            priceTextSizePx = sp.getFloat(KEY_PRICE_TEXT_SIZE, defTxt);
            priceTextColor = sp.getInt(KEY_PRICE_TEXT_COLOR, defPriceTxt);
            gridColor = sp.getInt(KEY_GRID_COLOR, defGridColor);
            bgColor = sp.getInt(KEY_BG_COLOR, defBg);
            lastLineWidthPx = sp.getFloat(KEY_LAST_LINE_WIDTH, defLastW);
            lastLineDashed = sp.getBoolean(KEY_LAST_LINE_DASH, defDash);
            lastPriceLabelTextSizePx = sp.getFloat(KEY_LAST_LABEL_TEXT_SIZE, defLabel);
            lastPriceLabelTextColor = sp.getInt(KEY_LAST_LABEL_TEXT_COLOR, defLabelTxt);
        }
        catch (Exception e)
        {
            Resources res = context.getResources();
            bodyWidthFraction = res.getInteger(R.integer.default_body_fraction_percent) / 100f;
            wickWidthPx = res.getDimension(R.dimen.default_wick_width);
            maLineWidthPx = res.getDimension(R.dimen.default_ma_line_width);
            showGrid = res.getBoolean(R.bool.default_show_grid);
            showVolume = res.getBoolean(R.bool.default_show_volume);
            visibleCandleCount = res.getInteger(R.integer.default_visible_candle_count);
            showLastPriceLine = res.getBoolean(R.bool.default_show_last_price);
            lastPriceLineColor = res.getColor(R.color.default_last_price_line, null);
            lastPriceBgColor = res.getColor(R.color.default_last_price_bg, null);
            priceTextSizePx = res.getDimension(R.dimen.default_price_text_size);
            priceTextColor = res.getColor(R.color.default_price_text, null);
            gridColor = res.getColor(R.color.default_grid, null);
            bgColor = res.getColor(R.color.default_chart_bg, null);
            lastLineWidthPx = res.getDimension(R.dimen.default_last_line_width);
            lastLineDashed = res.getBoolean(R.bool.default_last_line_dashed);
            lastPriceLabelTextSizePx = res.getDimension(R.dimen.default_last_label_text_size);
            lastPriceLabelTextColor = res.getColor(R.color.default_last_label_text, null);
        }
    }

    public int getBullishColor()
    {
        return bullishColor;
    }

    public int getBearishColor()
    {
        return bearishColor;
    }

    public float getBodyWidthFraction()
    {
        return bodyWidthFraction;
    }

    public float getWickWidthPx()
    {
        return wickWidthPx;
    }

    public float getMaLineWidthPx()
    {
        return maLineWidthPx;
    }

    public boolean isShowGrid()
    {
        return showGrid;
    }

    public boolean isShowVolume()
    {
        return showVolume;
    }

    public int getVisibleCandleCountValue()
    {
        return visibleCandleCount;
    }

    public boolean isShowLastPriceLine()
    {
        return showLastPriceLine;
    }

    public int getLastPriceLineColor()
    {
        return lastPriceLineColor;
    }

    public int getLastPriceBgColor()
    {
        return lastPriceBgColor;
    }

    public float getPriceTextSizePx()
    {
        return priceTextSizePx;
    }

    public int getPriceTextColor()
    {
        return priceTextColor;
    }

    public int getGridColor()
    {
        return gridColor;
    }

    public int getBgColor()
    {
        return bgColor;
    }

    public float getLastLineWidthPx()
    {
        return lastLineWidthPx;
    }

    public boolean isLastLineDashed()
    {
        return lastLineDashed;
    }

    public float getLastPriceLabelTextSizePx()
    {
        return lastPriceLabelTextSizePx;
    }

    public int getLastPriceLabelTextColor()
    {
        return lastPriceLabelTextColor;
    }

    public void setChartAppearance(boolean sLastPrice, int lastLineColor, int lastBgColor, float txtSize, int txtColor, int gColor, int bColor, float lastW, boolean lastDash)
    {
        this.showLastPriceLine = sLastPrice;
        this.lastPriceLineColor = lastLineColor;
        this.lastPriceBgColor = lastBgColor;
        this.priceTextSizePx = txtSize;
        this.priceTextColor = txtColor;
        this.gridColor = gColor;
        this.bgColor = bColor;
        this.lastLineWidthPx = lastW;
        this.lastLineDashed = lastDash;
        try
        {
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
        }
        catch (Exception e)
        {
        }
        initPaints(getContext());
        invalidate();
    }

    public void setChartAppearance(boolean sLastPrice, int lastLineColor, int lastBgColor, float txtSize, int txtColor, int gColor)
    {
        setChartAppearance(sLastPrice, lastLineColor, lastBgColor, txtSize, txtColor, gColor, bgColor, lastLineWidthPx, lastLineDashed);
    }

    public void setLastPriceLabelAppearance(int bgColor, int textColor, float textSizePx)
    {
        this.lastPriceBgColor = bgColor;
        this.lastPriceLabelTextColor = textColor;
        this.lastPriceLabelTextSizePx = textSizePx;
        try
        {
            SharedPreferences sp = getContext().getSharedPreferences(PREFS_CHART, Context.MODE_PRIVATE);
            SharedPreferences.Editor ed = sp.edit();
            ed.putInt(KEY_LAST_PRICE_BG_COLOR, bgColor);
            ed.putInt(KEY_LAST_LABEL_TEXT_COLOR, textColor);
            ed.putFloat(KEY_LAST_LABEL_TEXT_SIZE, textSizePx);
            ed.apply();
        }
        catch (Exception e)
        {
        }
        initPaints(getContext());
        invalidate();
    }

    public void setCandleColors(int bull, int bear)
    {
        this.bullishColor = bull;
        this.bearishColor = bear;
        try
        {
            SharedPreferences sp = getContext().getSharedPreferences(PREFS_CANDLE, Context.MODE_PRIVATE);
            sp.edit().putInt(KEY_BULL, bull).putInt(KEY_BEAR, bear).apply();
        }
        catch (Exception e)
        {
        }
        initPaints(getContext());
        invalidate();
    }

    public void setChartOptions(float bodyFraction, float wickWidth, float maWidth, boolean sGrid, boolean sVolume, int visCount)
    {
        this.bodyWidthFraction = bodyFraction;
        this.wickWidthPx = wickWidth;
        this.maLineWidthPx = maWidth;
        this.showGrid = sGrid;
        this.showVolume = sVolume;
        this.visibleCandleCount = visCount;

        if (this.visibleCandleCount < MIN_VISIBLE_CANDLE_COUNT)
        {
            this.visibleCandleCount = MIN_VISIBLE_CANDLE_COUNT;
        }
        if (this.visibleCandleCount > MAX_VISIBLE_CANDLE_COUNT)
        {
            this.visibleCandleCount = MAX_VISIBLE_CANDLE_COUNT;
        }

        try
        {
            SharedPreferences sp = getContext().getSharedPreferences(PREFS_CHART, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sp.edit();
            editor.putFloat(KEY_BODY_FRACTION, bodyFraction);
            editor.putFloat(KEY_WICK_WIDTH, wickWidth);
            editor.putFloat(KEY_MA_WIDTH, maWidth);
            editor.putBoolean(KEY_SHOW_GRID, sGrid);
            editor.putBoolean(KEY_SHOW_VOLUME, sVolume);
            editor.putInt(KEY_VISIBLE_COUNT, this.visibleCandleCount);
            editor.apply();
        }
        catch (Exception e)
        {
        }

        initPaints(getContext());
        clampTranslationX();
        invalidate();
    }

    public void resetToDefaults()
    {
        try
        {
            getContext().getSharedPreferences(PREFS_CHART, Context.MODE_PRIVATE).edit().clear().apply();
            getContext().getSharedPreferences(PREFS_CANDLE, Context.MODE_PRIVATE).edit().clear().apply();
            getContext().getSharedPreferences(PREFS_MA, Context.MODE_PRIVATE).edit().clear().apply();
        }
        catch (Exception e)
        {
        }

        try
        {
            Resources res = getResources();
            TypedArray palette = res.obtainTypedArray(R.array.candle_color_palette);
            int defaultBull = palette.getColor(3, res.getColor(R.color.default_bullish, null));
            int defaultBear = palette.getColor(4, res.getColor(R.color.default_bearish, null));
            palette.recycle();

            bullishColor = defaultBull;
            bearishColor = defaultBear;
            loadDefaultsFromXml(getContext());
            Resources r = getResources();
            bodyWidthFraction = r.getInteger(R.integer.default_body_fraction_percent) / 100f;
            wickWidthPx = r.getDimension(R.dimen.default_wick_width);
            maLineWidthPx = r.getDimension(R.dimen.default_ma_line_width);
            showGrid = r.getBoolean(R.bool.default_show_grid);
            showVolume = r.getBoolean(R.bool.default_show_volume);
            visibleCandleCount = r.getInteger(R.integer.default_visible_candle_count);
            showLastPriceLine = r.getBoolean(R.bool.default_show_last_price);
            lastPriceLineColor = r.getColor(R.color.default_last_price_line, null);
            lastPriceBgColor = r.getColor(R.color.default_last_price_bg, null);
            priceTextSizePx = r.getDimension(R.dimen.default_price_text_size);
            priceTextColor = r.getColor(R.color.default_price_text, null);
            gridColor = r.getColor(R.color.default_grid, null);
            bgColor = r.getColor(R.color.default_chart_bg, null);
            lastLineWidthPx = r.getDimension(R.dimen.default_last_line_width);
            lastLineDashed = r.getBoolean(R.bool.default_last_line_dashed);
            lastPriceLabelTextSizePx = r.getDimension(R.dimen.default_last_label_text_size);
            lastPriceLabelTextColor = r.getColor(R.color.default_last_label_text, null);

            initMaLines(getContext());
            initCandleColors(getContext());
            loadChartOptions(getContext());
            getContext().getSharedPreferences(PREFS_CHART, Context.MODE_PRIVATE).edit().clear().apply();
            getContext().getSharedPreferences(PREFS_CANDLE, Context.MODE_PRIVATE).edit().clear().apply();

            bodyWidthFraction = r.getInteger(R.integer.default_body_fraction_percent) / 100f;
            visibleCandleCount = r.getInteger(R.integer.default_visible_candle_count);
            showGrid = r.getBoolean(R.bool.default_show_grid);
            showVolume = r.getBoolean(R.bool.default_show_volume);
            showLastPriceLine = r.getBoolean(R.bool.default_show_last_price);
            lastLineDashed = r.getBoolean(R.bool.default_last_line_dashed);
            lastPriceLineColor = r.getColor(R.color.default_last_price_line, null);
            lastPriceBgColor = r.getColor(R.color.default_last_price_bg, null);
            wickWidthPx = r.getDimension(R.dimen.default_wick_width);
            maLineWidthPx = r.getDimension(R.dimen.default_ma_line_width);
            priceTextSizePx = r.getDimension(R.dimen.default_price_text_size);
            lastPriceLabelTextSizePx = r.getDimension(R.dimen.default_last_label_text_size);
            priceTextColor = r.getColor(R.color.default_price_text, null);
            lastPriceLabelTextColor = r.getColor(R.color.default_last_label_text, null);
            gridColor = r.getColor(R.color.default_grid, null);
            bgColor = r.getColor(R.color.default_chart_bg, null);
            lastLineWidthPx = r.getDimension(R.dimen.default_last_line_width);

            TypedArray colors = res.obtainTypedArray(R.array.ma_default_colors);
            int[] periods = res.getIntArray(R.array.ma_default_periods);
            maLines.clear();
            for (int i = 0; i < periods.length; i++)
            {
                int color = colors.getColor(i % colors.length(), res.getColor(R.color.default_last_price_line, null));
                maLines.add(new MaLine(periods[i], color));
            }
            colors.recycle();
            saveMaLines(getContext());

            initCandleColors(getContext());
            loadChartOptions(getContext());
            bullishColor = defaultBull;
            bearishColor = defaultBear;
            initPaints(getContext());
            clampTranslationX();
            invalidate();
            notifyMa();
        }
        catch (Exception e)
        {
        }
    }

    private void saveMaLines(Context context)
    {
        try
        {
            SharedPreferences sp = context.getSharedPreferences(PREFS_MA, Context.MODE_PRIVATE);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < maLines.size(); i++)
            {
                MaLine m = maLines.get(i);
                if (i > 0)
                {
                    sb.append(";");
                }
                sb.append(m.period).append(",").append(m.color);
            }
            sp.edit().putString(KEY_MA, sb.toString()).apply();
        }
        catch (Exception e)
        {
        }
    }

    private boolean loadMaLinesFromPrefs(Context context)
    {
        try
        {
            SharedPreferences sp = context.getSharedPreferences(PREFS_MA, Context.MODE_PRIVATE);
            String s = sp.getString(KEY_MA, null);
            if (s == null || s.isEmpty())
            {
                return false;
            }
            String[] parts = s.split(";");
            List<MaLine> list = new ArrayList<>();
            for (String p : parts)
            {
                String[] kv = p.split(",");
                if (kv.length!= 2)
                {
                    continue;
                }
                int period = Integer.parseInt(kv[0]);
                int color = Integer.parseInt(kv[1]);
                list.add(new MaLine(period, color));
            }
            if (!list.isEmpty())
            {
                maLines = list;
                return true;
            }
        }
        catch (Exception e)
        {
        }
        return false;
    }

    private void initMaLines(Context context)
    {
        if (loadMaLinesFromPrefs(context))
        {
            return;
        }
        try
        {
            Resources res = context.getResources();
            int[] periods = res.getIntArray(R.array.ma_default_periods);
            TypedArray colors = res.obtainTypedArray(R.array.ma_default_colors);
            maLines.clear();
            for (int i = 0; i < periods.length; i++)
            {
                int color = colors.getColor(i % colors.length(), res.getColor(R.color.default_last_price_line, null));
                maLines.add(new MaLine(periods[i], color));
            }
            colors.recycle();
        }
        catch (Resources.NotFoundException e)
        {
            try
            {
                Resources res = context.getResources();
                TypedArray colors = res.obtainTypedArray(R.array.ma_default_colors);
                maLines.clear();
                int[] periods = res.getIntArray(R.array.ma_default_periods);
                for (int i = 0; i < periods.length; i++)
                {
                    maLines.add(new MaLine(periods[i], colors.getColor(i % colors.length(), res.getColor(R.color.default_last_price_line, null))));
                }
                colors.recycle();
            }
            catch (Exception ex)
            {
                Resources res = context.getResources();
                TypedArray colors = res.obtainTypedArray(R.array.ma_default_colors);
                maLines.clear();
                int[] periods = res.getIntArray(R.array.ma_default_periods);
                for (int i = 0; i < periods.length; i++)
                {
                    maLines.add(new MaLine(periods[i], colors.getColor(i % colors.length(), res.getColor(R.color.default_last_price_line, null))));
                }
                colors.recycle();
            }
        }
    }

    public List<MaLine> getMaLines()
    {
        return new ArrayList<>(maLines);
    }

    public void setMaLines(List<MaLine> list)
    {
        if (list == null)
        {
            return;
        }
        this.maLines = new ArrayList<>(list);
        saveMaLines(getContext());
        initPaints(getContext());
        invalidate();
        notifyMa();
    }

    private int getThemeColor(int attr)
    {
        TypedValue tv = new TypedValue();
        getContext().getTheme().resolveAttribute(attr, tv, true);
        if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT)
        {
            return tv.data;
        }
        else
        {
            try
            {
                return getResources().getColor(tv.resourceId, getContext().getTheme());
            }
            catch (Exception e)
            {
                return tv.data;
            }
        }
    }

    private void initPaints(Context context)
    {
        Resources res = context.getResources();
        int themeBg = getThemeColor(android.R.attr.colorBackground);
        setBackgroundColor(themeBg);
        bgColor = themeBg;

        bullishPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bullishPaint.setColor(bullishColor);
        bullishPaint.setStyle(Paint.Style.FILL);

        bearishPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bearishPaint.setColor(bearishColor);
        bearishPaint.setStyle(Paint.Style.FILL);

        volumeBullishPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        volumeBullishPaint.setColor(bullishColor);
        volumeBullishPaint.setAlpha(255);
        volumeBullishPaint.setStyle(Paint.Style.FILL);

        volumeBearishPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        volumeBearishPaint.setColor(bearishColor);
        volumeBearishPaint.setAlpha(255);
        volumeBearishPaint.setStyle(Paint.Style.FILL);

        float defaultWickWidth = res.getDimension(R.dimen.chart_wick_width);
        float finalWickWidth = (wickWidthPx > 0f)? wickWidthPx : defaultWickWidth;

        wickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wickPaint.setColor(res.getColor(R.color.chart_wick, null));
        wickPaint.setStrokeWidth(finalWickWidth);

        wickBullishPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wickBullishPaint.setColor(bullishColor);
        wickBullishPaint.setStrokeWidth(finalWickWidth);

        wickBearishPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wickBearishPaint.setColor(bearishColor);
        wickBearishPaint.setStrokeWidth(finalWickWidth);

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(gridColor);
        gridPaint.setStrokeWidth(res.getDimension(R.dimen.chart_grid_width));

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(priceTextColor);
        textPaint.setTextSize(priceTextSizePx);

        lastPriceLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        lastPriceLinePaint.setColor(lastPriceLineColor);
        lastPriceLinePaint.setStrokeWidth(lastLineWidthPx);
        lastPriceLinePaint.setStyle(Paint.Style.STROKE);
        if (lastLineDashed)
        {
            lastPriceLinePaint.setPathEffect(new DashPathEffect(new float[]{10f, 6f}, 0f));
        }
        else
        {
            lastPriceLinePaint.setPathEffect(null);
        }

        lastPriceBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        lastPriceBgPaint.setColor(lastPriceBgColor);
        lastPriceBgPaint.setStyle(Paint.Style.FILL);

        lastPriceTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        lastPriceTextPaint.setColor(lastPriceLabelTextColor);
        lastPriceTextPaint.setTextSize(lastPriceLabelTextSizePx);
        lastPriceTextPaint.setFakeBoldText(true);

        float defaultMaWidth = res.getDimension(R.dimen.chart_ma_line_width);
        float thin = (maLineWidthPx > 0f)? maLineWidthPx : defaultMaWidth;

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

        for (int i = 0; i < maLines.size(); i++)
        {
            Paint p;
            if (i == 0)
            {
                p = movingAverage5Paint;
            }
            else if (i == 1)
            {
                p = movingAverage10Paint;
            }
            else if (i == 2)
            {
                p = movingAverage20Paint;
            }
            else
            {
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
        selectedLinePaint.setColor(res.getColor(R.color.chart_text, null));
        selectedLinePaint.setStrokeWidth(res.getDimension(R.dimen.chart_selected_width));
        selectedLinePaint.setAlpha(100);
    }

    @Override
    protected void onConfigurationChanged(android.content.res.Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);
        loadDefaultsFromXml(getContext());
        loadChartOptions(getContext());
        bgColor = 0;
        initCandleColors(getContext());
        initPaints(getContext());
        invalidate();
    }

    public void refreshTheme()
    {
        loadDefaultsFromXml(getContext());
        loadChartOptions(getContext());
        bgColor = 0;
        initCandleColors(getContext());
        initPaints(getContext());
        invalidate();
    }

    private void initGestures(Context context)
    {
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener()
        {
            @Override
            public boolean onScale(ScaleGestureDetector detector)
            {
                visibleCandleCount = (int) (visibleCandleCount / detector.getScaleFactor());
                if (visibleCandleCount < MIN_VISIBLE_CANDLE_COUNT)
                {
                    visibleCandleCount = MIN_VISIBLE_CANDLE_COUNT;
                }
                if (visibleCandleCount > MAX_VISIBLE_CANDLE_COUNT)
                {
                    visibleCandleCount = MAX_VISIBLE_CANDLE_COUNT;
                }
                clampTranslationX();
                invalidate();
                return true;
            }
        });

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener()
        {
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY)
            {
                if (data.isEmpty())
                {
                    return false;
                }
                float density = getResources().getDisplayMetrics().density;
                int priceAxisW = (int) (PRICE_AXIS_WIDTH_DP * density);
                int chartW = getWidth() - priceAxisW;
                if (chartW <= 0)
                {
                    return false;
                }
                translationX -= distanceX;
                clampTranslationX();
                if (selectedIndex!= -1)
                {
                    selectedIndex = -1;
                    if (updateListener!= null)
                    {
                        updateListener.onNothingSelected();
                    }
                }
                invalidate();
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e)
            {
                if (data.isEmpty())
                {
                    return false;
                }
                float density = getResources().getDisplayMetrics().density;
                int priceAxisW = (int) (PRICE_AXIS_WIDTH_DP * density);
                int chartW = getWidth() - priceAxisW;
                int count = Math.min(visibleCandleCount, data.size());
                if (count == 0)
                {
                    return false;
                }
                if (e.getX() > chartW)
                {
                    if (selectedIndex!= -1)
                    {
                        selectedIndex = -1;
                        if (updateListener!= null)
                        {
                            updateListener.onNothingSelected();
                        }
                        invalidate();
                    }
                    return false;
                }
                float candleWidth = chartW / (float) count;
                float xWithOffset = e.getX() - extraOffsetX;
                int index = (int) (xWithOffset / candleWidth) + startIndexCache;
                if (index >= 0 && index < data.size())
                {
                    selectedIndex = index;
                    if (updateListener!= null)
                    {
                        updateListener.onCandleSelected(data.get(index));
                    }
                    if (volumeClickListener!= null)
                    {
                        volumeClickListener.onVolumeClick(data.get(index));
                    }
                    invalidate();
                }
                else
                {
                    selectedIndex = -1;
                    if (updateListener!= null)
                    {
                        updateListener.onNothingSelected();
                    }
                    invalidate();
                }
                return true;
            }
        });
    }

    private void clampTranslationX()
    {
        if (data.isEmpty())
        {
            translationX = 0f;
            extraOffsetX = 0f;
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        int priceAxisW = (int) (PRICE_AXIS_WIDTH_DP * density);
        int chartW = getWidth() - priceAxisW;
        if (chartW <= 0)
        {
            return;
        }
        int count = Math.min(visibleCandleCount, data.size());
        float candleWidth = chartW / (float) count;
        float maxScroll = (data.size() - count) * candleWidth;
        float minScroll = -chartW * 0.6f;

        if (translationX < minScroll)
        {
            translationX = minScroll;
        }
        if (translationX > maxScroll)
        {
            translationX = maxScroll;
        }

        if (translationX < 0f)
        {
            extraOffsetX = translationX;
        }
        else
        {
            extraOffsetX = 0f;
        }
    }

    public void loadChart(String symbol, String interval)
    {
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

    public void setFiatCode(String code)
    {
        this.fiatCode = code;
    }

    public float getFiatMultiplier()
    {
        return fiatMultiplier;
    }

    public void setFiatMultiplier(float mult)
    {
        if (mult <= 0f)
        {
            mult = 1f;
        }
        this.fiatMultiplier = mult;
        invalidate();
    }

    public void setCountdown(String text)
    {
        invalidate();
    }

    private long getIntervalMillis(String interval)
    {
        if (interval == null)
        {
            return 60_000L;
        }
        switch (interval)
        {
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

    private void startCountdown()
    {
        if (countdownRunnable!= null)
        {
            countdownHandler.removeCallbacks(countdownRunnable);
        }
        countdownRunnable = new Runnable()
        {
            @Override
            public void run()
            {
                if (currentCandleCloseTime > 0L)
                {
                    long now = System.currentTimeMillis();
                    long remain = currentCandleCloseTime - now;
                    if (remain < 0L)
                    {
                        remain = 0L;
                    }
                    long seconds = (remain / 1000L) % 60L;
                    long minutes = (remain / 1000L / 60L) % 60L;
                    long hours = remain / 1000L / 60L / 60L;
                    String text;
                    if (getIntervalMillis(currentInterval) >= 24L * 60L * 60_000L)
                    {
                        text = String.format(Locale.US, "%02d:%02d:%02d:%02d", hours / 24L, hours % 24L, minutes, seconds);
                    }
                    else
                    {
                        text = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
                    }
                    if (updateListener!= null)
                    {
                        updateListener.onCountdownUpdate(text);
                    }
                }
                countdownHandler.postDelayed(this, COUNTDOWN_INTERVAL_MS);
            }
        };
        countdownHandler.post(countdownRunnable);
    }

    private void startLive()
    {
        if (liveRunnable!= null)
        {
            liveHandler.removeCallbacks(liveRunnable);
        }
        liveRunnable = new Runnable()
        {
            @Override
            public void run()
            {
                fetchPriceAndCandle();
                liveHandler.postDelayed(this, LIVE_REFRESH_INTERVAL_MS);
            }
        };
        liveHandler.post(liveRunnable);
    }

    private void stopLive()
    {
        if (liveRunnable!= null)
        {
            liveHandler.removeCallbacks(liveRunnable);
        }
        if (countdownRunnable!= null)
        {
            countdownHandler.removeCallbacks(countdownRunnable);
        }
    }

    private void notifyMa()
    {
        if (data.isEmpty() || updateListener == null)
        {
            return;
        }
        int last = data.size() - 1;
        List<Float> values = new ArrayList<>();
        for (int i = 0; i < maLines.size(); i++)
        {
            values.add(calculateMovingAverage(last, maLines.get(i).period));
        }
        updateListener.onMaUpdate(values);
    }

    private void fetchCandles()
    {
        if (currentSymbol == null || currentInterval == null)
        {
            return;
        }
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    String urlString = String.format(Locale.US, "https://api.binance.com/api/v3/klines?symbol=%s&interval=%s&limit=%d", currentSymbol, currentInterval, FETCH_LIMIT);
                    URL url = new URL(urlString);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(8000);
                    connection.setReadTimeout(8000);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder builder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine())!= null)
                    {
                        builder.append(line);
                    }
                    reader.close();
                    JSONArray jsonArray = new JSONArray(builder.toString());
                    List<Candle> newData = new ArrayList<>(jsonArray.length());
                    for (int i = 0; i < jsonArray.length(); i++)
                    {
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
                    mainHandler.post(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            data = newData;
                            clampTranslationX();
                            if (!data.isEmpty())
                            {
                                minPrice = Float.MAX_VALUE;
                                maxPrice = Float.MIN_VALUE;
                                maxVolume = 0f;
                                for (Candle candle : data)
                                {
                                    if (candle.low < minPrice)
                                    {
                                        minPrice = candle.low;
                                    }
                                    if (candle.high > maxPrice)
                                    {
                                        maxPrice = candle.high;
                                    }
                                    if (candle.volume > maxVolume)
                                    {
                                        maxVolume = candle.volume;
                                    }
                                }
                                lastPrice = data.get(data.size() - 1).close;
                                currentCandleCloseTime = data.get(data.size() - 1).closeTime;
                                float padding = (maxPrice - minPrice) * 0.08f;
                                minPrice -= padding;
                                maxPrice += padding;
                                if (updateListener!= null)
                                {
                                    updateListener.onPriceUpdate(lastPrice, maxPrice, minPrice);
                                }
                                notifyMa();
                            }
                            invalidate();
                        }
                    });
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void fetchPriceAndCandle()
    {
        if (currentSymbol == null)
        {
            return;
        }
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    String tickerUrl = String.format(Locale.US, "https://api.binance.com/api/v3/ticker/24hr?symbol=%s", currentSymbol);
                    HttpURLConnection connection = (HttpURLConnection) new URL(tickerUrl).openConnection();
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(5000);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder builder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine())!= null)
                    {
                        builder.append(line);
                    }
                    reader.close();
                    JSONObject jsonObject = new JSONObject(builder.toString());
                    float price = (float) jsonObject.getDouble("lastPrice");
                    float high = (float) jsonObject.getDouble("highPrice");
                    float low = (float) jsonObject.getDouble("lowPrice");
                    float volBtc = (float) jsonObject.getDouble("volume");
                    float volUsdt = (float) jsonObject.getDouble("quoteVolume");
                    float changePercent = (float) jsonObject.getDouble("priceChangePercent");
                    mainHandler.post(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            if (!data.isEmpty())
                            {
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
                                if (System.currentTimeMillis() >= updatedCandle.closeTime)
                                {
                                    fetchCandles();
                                }
                                else
                                {
                                    invalidate();
                                }
                                if (updateListener!= null)
                                {
                                    updateListener.onPriceUpdate(price, high, low);
                                    updateListener.onTickerUpdate(high, low, volBtc, volUsdt, changePercent);
                                }
                                notifyMa();
                            }
                        }
                    });
                }
                catch (Exception e)
                {
                }
            }
        }).start();
    }

    private float calculateMovingAverage(int currentIndex, int period)
    {
        if (currentIndex < period - 1 || data.isEmpty())
        {
            return 0f;
        }
        float sum = 0f;
        for (int i = 0; i < period; i++)
        {
            sum += data.get(currentIndex - i).close;
        }
        return sum / period;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
        scaleGestureDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        if (event.getAction() == MotionEvent.ACTION_DOWN)
        {
            float density = getResources().getDisplayMetrics().density;
            int priceAxisW = (int) (PRICE_AXIS_WIDTH_DP * density);
            int chartW = getWidth() - priceAxisW;
            if (event.getX() > chartW)
            {
                if (selectedIndex!= -1)
                {
                    selectedIndex = -1;
                    if (updateListener!= null)
                    {
                        updateListener.onNothingSelected();
                    }
                    invalidate();
                }
                return true;
            }
        }
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        int priceAxisWidth = (int) (PRICE_AXIS_WIDTH_DP * density);
        int timeAxisHeight = (int) (20 * density);
        int volumeHeightPx = (int) (VOLUME_CHART_HEIGHT_DP * density);

        int fullWidth = getWidth();
        int fullHeight = getHeight();
        int chartWidth = fullWidth - priceAxisWidth;
        int priceChartHeight = fullHeight - TOP_PADDING_PX - BOTTOM_PADDING_PX - VOLUME_TOP_MARGIN_PX - volumeHeightPx - timeAxisHeight;

        if (priceChartHeight <= 0)
        {
            return;
        }

        drawGrid(canvas, chartWidth, priceChartHeight, volumeHeightPx);

        if (data.isEmpty())
        {
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
        if (info.displayMax - info.displayMin == 0f)
        {
            info.displayMax = info.displayMin + 1f;
        }

        drawCandles(canvas, info);
        drawMovingAverages(canvas, info);
        drawSelectedLine(canvas, info);
        drawLastPriceLine(canvas, info);
        drawPriceAxis(canvas, info);
        drawVolumeAndTime(canvas, info);
    }

    private static class DrawInfo
    {
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

    private int calcStartIndex(int count, float candleWidth)
    {
        int startIndex;
        if (translationX >= 0f)
        {
            startIndex = data.size() - count - (int) (translationX / candleWidth);
        }
        else
        {
            startIndex = data.size() - count;
        }
        if (startIndex < 0)
        {
            startIndex = 0;
        }
        if (startIndex + count > data.size())
        {
            startIndex = data.size() - count;
        }
        if (startIndex < 0)
        {
            startIndex = 0;
        }
        startIndexCache = startIndex;
        return startIndex;
    }

    private void drawGrid(Canvas canvas, int chartWidth, int priceChartHeight, int volumeHeightPx)
    {
        if (!showGrid)
        {
            canvas.drawLine(chartWidth, 0f, chartWidth, getHeight(), gridPaint);
            return;
        }
        for (int i = 0; i <= 4; i++)
        {
            float y = TOP_PADDING_PX + priceChartHeight * i / 4f;
            canvas.drawLine(0f, y, chartWidth, y, gridPaint);
        }
        float volumeSeparatorY = TOP_PADDING_PX + priceChartHeight + VOLUME_TOP_MARGIN_PX;
        canvas.drawLine(0f, volumeSeparatorY, chartWidth, volumeSeparatorY, gridPaint);
        canvas.drawLine(chartWidth, 0f, chartWidth, getHeight(), gridPaint);
    }

    private void drawCandles(Canvas canvas, DrawInfo info)
    {
        float bodyFraction = 0.48f;
        float finalBodyFraction = bodyWidthFraction > 0? bodyWidthFraction : bodyFraction;
        float bodyWidth = info.candleWidth * finalBodyFraction;
        float minBody = getResources().getDimension(R.dimen.chart_body_min_width);
        float maxBody = getResources().getDimension(R.dimen.chart_body_max_width);
        if (bodyWidth < minBody)
        {
            bodyWidth = minBody;
        }
        if (bodyWidth > maxBody)
        {
            bodyWidth = maxBody;
        }
        float priceRange = info.displayMax - info.displayMin;
        if (priceRange == 0f)
        {
            priceRange = 1f;
        }
        for (int i = 0; i < info.count; i++)
        {
            int dataIndex = info.startIndex + i;
            if (dataIndex >= data.size())
            {
                break;
            }
            Candle candle = data.get(dataIndex);
            float x = i * info.candleWidth + info.candleWidth / 2f + extraOffsetX;

            float highY = TOP_PADDING_PX + info.priceChartHeight - ((candle.high * fiatMultiplier - info.displayMin) / priceRange * info.priceChartHeight);
            float lowY = TOP_PADDING_PX + info.priceChartHeight - ((candle.low * fiatMultiplier - info.displayMin) / priceRange * info.priceChartHeight);
            float openY = TOP_PADDING_PX + info.priceChartHeight - ((candle.open * fiatMultiplier - info.displayMin) / priceRange * info.priceChartHeight);
            float closeY = TOP_PADDING_PX + info.priceChartHeight - ((candle.close * fiatMultiplier - info.displayMin) / priceRange * info.priceChartHeight);

            boolean isBullish = candle.close >= candle.open;
            Paint currentWickPaint = isBullish? wickBullishPaint : wickBearishPaint;
            Paint bodyPaint = isBullish? bullishPaint : bearishPaint;

            canvas.drawLine(x, highY, x, lowY, currentWickPaint);

            float top = Math.min(openY, closeY);
            float bottom = Math.max(openY, closeY);
            float minH = getResources().getDimension(R.dimen.chart_candle_min_height);
            if (Math.abs(bottom - top) < minH)
            {
                bottom = top + minH;
            }
            canvas.drawRect(x - bodyWidth / 2f, top, x + bodyWidth / 2f, bottom, bodyPaint);
        }
    }

    private void drawMovingAverages(Canvas canvas, DrawInfo info)
    {
        float priceRange = info.displayMax - info.displayMin;
        if (priceRange == 0f)
        {
            priceRange = 1f;
        }
        for (int maIndex = 0; maIndex < maLines.size(); maIndex++)
        {
            MaLine maLine = maLines.get(maIndex);
            int period = maLine.period;
            Paint paint;
            if (maIndex == 0)
            {
                paint = movingAverage5Paint;
            }
            else if (maIndex == 1)
            {
                paint = movingAverage10Paint;
            }
            else if (maIndex == 2)
            {
                paint = movingAverage20Paint;
            }
            else
            {
                int extraIdx = maIndex - 3;
                if (extraIdx < maExtraPaints.size())
                {
                    paint = maExtraPaints.get(extraIdx);
                }
                else
                {
                    paint = movingAverage20Paint;
                }
            }
            paint.setColor(maLine.color);
            float previousX = 0f;
            float previousY = 0f;
            boolean isFirstPoint = true;
            for (int i = 0; i < info.count; i++)
            {
                int dataIndex = info.startIndex + i;
                if (dataIndex >= data.size())
                {
                    break;
                }
                float movingAverage = calculateMovingAverage(dataIndex, period);
                if (movingAverage == 0f)
                {
                    continue;
                }
                float x = i * info.candleWidth + info.candleWidth / 2f + extraOffsetX;
                float y = TOP_PADDING_PX + info.priceChartHeight - ((movingAverage * fiatMultiplier - info.displayMin) / priceRange * info.priceChartHeight);
                if (!isFirstPoint)
                {
                    canvas.drawLine(previousX, previousY, x, y, paint);
                }
                previousX = x;
                previousY = y;
                isFirstPoint = false;
            }
        }
    }

    private void drawSelectedLine(Canvas canvas, DrawInfo info)
    {
        if (selectedIndex >= info.startIndex && selectedIndex < info.startIndex + info.count)
        {
            float selectedX = (selectedIndex - info.startIndex) * info.candleWidth + info.candleWidth / 2f + extraOffsetX;
            canvas.drawLine(selectedX, TOP_PADDING_PX, selectedX, TOP_PADDING_PX + info.priceChartHeight, selectedLinePaint);
        }
    }

    private void drawLastPriceLine(Canvas canvas, DrawInfo info)
    {
        if (lastPrice <= 0f ||!showLastPriceLine)
        {
            return;
        }
        float priceRange = info.displayMax - info.displayMin;
        if (priceRange == 0f)
        {
            priceRange = 1f;
        }
        float lastPriceY = TOP_PADDING_PX + info.priceChartHeight - ((lastPrice * fiatMultiplier - info.displayMin) / priceRange * info.priceChartHeight);
        canvas.drawLine(0f, lastPriceY, info.chartWidth, lastPriceY, lastPriceLinePaint);

        boolean isBigFiat = fiatMultiplier > 100f;
        String fmt = isBigFiat? "%,.0f" : "%,.2f";

        float labelH = getResources().getDimension(R.dimen.chart_price_text_offset) + getResources().getDimension(R.dimen.chart_text_size);
        float top = lastPriceY - labelH;
        float bottom = lastPriceY + labelH;
        canvas.drawRect(info.chartWidth, top, info.fullWidth, bottom, lastPriceBgPaint);

        String label = String.format(Locale.US, fmt, lastPrice * fiatMultiplier);
        float tx = info.chartWidth + getResources().getDimension(R.dimen.chart_price_text_margin) / 2f;
        float ty = lastPriceY + getResources().getDimension(R.dimen.chart_text_size) / 3f;
        canvas.drawText(label, tx, ty, lastPriceTextPaint);
    }

    private void drawPriceAxis(Canvas canvas, DrawInfo info)
    {
        boolean isBigFiatAxis = fiatMultiplier > 100f;
        String axisFmt = isBigFiatAxis? "%,.0f" : "%,.2f";
        for (int i = 0; i <= 4; i++)
        {
            float price = info.displayMax - (info.displayMax - info.displayMin) * i / 4f;
            float y = TOP_PADDING_PX + info.priceChartHeight * i / 4f + getResources().getDimension(R.dimen.chart_price_text_offset);
            String priceText = String.format(Locale.US, axisFmt, price);
            canvas.drawText(priceText, info.chartWidth + getResources().getDimension(R.dimen.chart_price_text_margin), y, textPaint);
        }
    }

    private void drawVolumeAndTime(Canvas canvas, DrawInfo info)
    {
        float density = getResources().getDisplayMetrics().density;
        float volumeTop = TOP_PADDING_PX + info.priceChartHeight + VOLUME_TOP_MARGIN_PX;
        if (maxVolume == 0f)
        {
            maxVolume = 1f;
        }
        float bodyFraction = 0.48f;
        float finalBodyFraction = bodyWidthFraction > 0? bodyWidthFraction : bodyFraction;
        float bodyWidth = info.candleWidth * finalBodyFraction;
        float minBody = getResources().getDimension(R.dimen.chart_body_min_width);
        float maxBody = getResources().getDimension(R.dimen.chart_body_max_width);
        if (bodyWidth < minBody)
        {
            bodyWidth = minBody;
        }
        if (bodyWidth > maxBody)
        {
            bodyWidth = maxBody;
        }

        if (showVolume)
        {
            for (int i = 0; i < info.count; i++)
            {
                int dataIndex = info.startIndex + i;
                if (dataIndex >= data.size())
                {
                    break;
                }
                Candle candle = data.get(dataIndex);
                float x = i * info.candleWidth + info.candleWidth / 2f + extraOffsetX;
                float volumeBarHeight = info.volumeHeightPx * (candle.volume / maxVolume);
                Paint volumePaint = candle.close >= candle.open? volumeBullishPaint : volumeBearishPaint;
                canvas.drawRect(x - bodyWidth / 2f, volumeTop + info.volumeHeightPx - volumeBarHeight, x + bodyWidth / 2f, volumeTop + info.volumeHeightPx, volumePaint);
            }
        }

        for (int i = 0; i < info.count; i += Math.max(1, info.count / 4))
        {
            int dataIndex = info.startIndex + i;
            if (dataIndex >= data.size())
            {
                break;
            }
            float x = i * info.candleWidth + extraOffsetX;
            String timeText = timeFormat.format(new Date(data.get(dataIndex).openTime));
            float timeY = volumeTop + info.volumeHeightPx + 16 * density;
            if (!showVolume)
            {
                timeY = TOP_PADDING_PX + info.priceChartHeight + VOLUME_TOP_MARGIN_PX + 16 * density;
            }
            canvas.drawText(timeText, x, timeY, textPaint);
        }
    }

    @Override
    protected void onDetachedFromWindow()
    {
        super.onDetachedFromWindow();
        stopLive();
    }
}
