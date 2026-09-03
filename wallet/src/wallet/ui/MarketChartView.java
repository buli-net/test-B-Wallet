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
import android.graphics.Canvas;
import android.graphics.Color;
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
import android.widget.SeekBar;

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
 * All dimensions are loaded from dimens.xml, no hardcoded values.
 * Volume MA (VOL MA5/MA10) is a separate module, keys from strings.xml.
 */
public class MarketChartView extends View {

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

    public static class MaLine {
        public int period;
        public int color;

        public MaLine(int period, int color) {
            this.period = period;
            this.color = color;
        }
    }

    public interface OnChartUpdateListener {
        void onPriceUpdate(float price, float high24h, float low24h);
        void onTickerUpdate(float high24h, float low24h, float volBtc, float volUsdt, float changePercent);
        void onMaUpdate(List<Float> maValues);
        void onCountdownUpdate(String countdown);
        void onCandleSelected(Candle candle);
        void onNothingSelected();
    }

    public interface OnVolumeClickListener {
        void onVolumeClick(Candle candle);
    }

    private OnChartUpdateListener updateListener;
    private OnVolumeClickListener volumeClickListener;

    public void setOnChartUpdateListener(OnChartUpdateListener listener) {
        this.updateListener = listener;
    }

    public void setOnVolumeClickListener(OnVolumeClickListener listener) {
        this.volumeClickListener = listener;
    }

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

    private Paint volMa5Paint;
    private Paint volMa10Paint;
    private Paint volHeaderTextPaint;
    private Paint volHeaderMa5Paint;
    private Paint volHeaderMa10Paint;

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
    private int VOLUME_ALPHA;
    private int SELECTED_ALPHA;
    private int BIG_FIAT_THRESHOLD;
    private int NETWORK_TIMEOUT;
    private float GRID_WIDTH;
    private float DASH_ON;
    private float DASH_OFF;
    private float SELECTED_WIDTH;
    private float MIN_SCROLL_FRACTION;
    private float PRICE_PADDING_FRACTION;
    private int TIME_AXIS_HEIGHT;
    private int BODY_MIN_WIDTH;
    private int BODY_MAX_WIDTH;
    private int CANDLE_MIN_HEIGHT;
    private int PRICE_TEXT_OFFSET;
    private int TEXT_SIZE;
    private int PRICE_TEXT_MARGIN;
    private int TIME_TEXT_OFFSET;
    private int LOADING_TEXT_OFFSET;

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
    private int defLastPriceLineColor;
    private int defLastPriceBgColor;
    private int defPriceTextColor;
    private int defGridColor;
    private int defLabelTextColor;
    private int defSelectedLineColor;
    private float defSelectedLineWidthPx;
    private int defSelectedAlpha;
    private boolean defSelectedDashed;

    private int visibleCandleCount;
    private float translationX = 0f;
    private float minPrice = 0f;
    private float maxPrice = 0f;
    private float lastPrice = 0f;
    private float maxVolume = 0f;
    private int selectedIndex = -1;
    private int startIndexCache = 0;
    private float extraOffsetX = 0f;

    private boolean showVolMa = true;
    private int volMa1Period = 5;
    private int volMa2Period = 10;
    private int volMa1Color = Color.parseColor("#FFC107");
    private int volMa2Color = Color.parseColor("#9C27B0");
    private float volMaWidthPx = 2f;
    private List<Float> volMa1Values = new ArrayList<>();
    private List<Float> volMa2Values = new ArrayList<>();
    private float lastVolValue = 0f;
    private float lastVolMa1Value = 0f;
    private float lastVolMa2Value = 0f;

    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetector gestureDetector;

    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Handler liveHandler = new Handler(Looper.getMainLooper());
    private Handler countdownHandler = new Handler(Looper.getMainLooper());
    private Runnable liveRunnable;
    private Runnable countdownRunnable;

    private String currentSymbol;
    private String currentInterval;
    private SimpleDateFormat timeFormat;
    private long currentCandleCloseTime = 0L;
    private String fiatCode;
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
    private int selectedLineColor;
    private float selectedLineWidthPx;
    private int selectedLineAlpha;
    private boolean selectedLineDashed;

    // Each part of Selected Line has its own user_set flag - auto according to theme if not set
    private static final String KEY_SELECTED_COLOR_USER_SET = "selected_line_color_user_set";
    private static final String KEY_SELECTED_WIDTH_USER_SET = "selected_width_user_set";
    private static final String KEY_SELECTED_ALPHA_USER_SET = "selected_alpha_user_set";
    private static final String KEY_SELECTED_DASHED_USER_SET = "selected_dashed_user_set";

    public MarketChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        fiatCode = context.getString(R.string.fiat_usd);
        timeFormat = new SimpleDateFormat(
                context.getString(R.string.time_format),
                Locale.US
        );
        loadViewDimensions(context);
        visibleCandleCount = DEFAULT_VISIBLE_CANDLE_COUNT;
        initGestures(context);
        initPaints(context);
    }

    private void loadViewDimensions(Context context) {
        TOP_PADDING_PX = (int) context.getResources()
               .getDimension(R.dimen.default_top_padding);
        BOTTOM_PADDING_PX = (int) context.getResources()
               .getDimension(R.dimen.default_bottom_padding);
        VOLUME_CHART_HEIGHT_DP = (int) context.getResources()
               .getDimension(R.dimen.default_volume_height);
        VOLUME_TOP_MARGIN_PX = (int) context.getResources()
               .getDimension(R.dimen.default_volume_top_margin);
        PRICE_AXIS_WIDTH_DP = (int) context.getResources()
               .getDimension(R.dimen.default_price_axis_width);
        TIME_AXIS_HEIGHT = (int) context.getResources()
               .getDimension(R.dimen.default_time_axis_height);
        PRICE_TEXT_MARGIN = (int) context.getResources()
               .getDimension(R.dimen.default_price_text_margin);
        PRICE_TEXT_OFFSET = (int) context.getResources()
               .getDimension(R.dimen.default_price_text_offset);
        GRID_WIDTH = context.getResources()
               .getDimension(R.dimen.default_grid_width);
        BODY_MIN_WIDTH = (int) context.getResources()
               .getDimension(R.dimen.default_body_min_width);
        BODY_MAX_WIDTH = (int) context.getResources()
               .getDimension(R.dimen.default_body_max_width);
        TEXT_SIZE = (int) context.getResources()
               .getDimension(R.dimen.default_text_size);
        SELECTED_WIDTH = context.getResources()
               .getDimension(R.dimen.default_selected_width);
        DASH_ON = context.getResources()
               .getDimension(R.dimen.dash_on);
        DASH_OFF = context.getResources()
               .getDimension(R.dimen.dash_off);
        TIME_TEXT_OFFSET = (int) context.getResources()
               .getDimension(R.dimen.time_text_offset);
        LOADING_TEXT_OFFSET = (int) context.getResources()
               .getDimension(R.dimen.loading_text_offset);
        CANDLE_MIN_HEIGHT = (int) context.getResources()
               .getDimension(R.dimen.default_candle_min_height);
        FETCH_LIMIT = context.getResources()
               .getInteger(R.integer.default_fetch_limit);
        // MIN_VISIBLE_CANDLE_COUNT và MAX_VISIBLE_CANDLE_COUNT được lấy từ layout qua setDefaultsFromLayout()
        // Không đọc từ integers.xml nữa
        VOLUME_ALPHA = context.getResources()
               .getInteger(R.integer.volume_alpha);
        SELECTED_ALPHA = context.getResources()
               .getInteger(R.integer.selected_alpha);
        BIG_FIAT_THRESHOLD = context.getResources()
               .getInteger(R.integer.big_fiat_threshold);
        NETWORK_TIMEOUT = context.getResources()
               .getInteger(R.integer.network_timeout);
        LIVE_REFRESH_INTERVAL_MS = context.getResources()
               .getInteger(R.integer.live_refresh_interval);
        COUNTDOWN_INTERVAL_MS = context.getResources()
               .getInteger(R.integer.countdown_interval);
        MIN_SCROLL_FRACTION = context.getResources()
               .getFraction(R.fraction.min_scroll_fraction, 1, 1);
        PRICE_PADDING_FRACTION = context.getResources()
               .getFraction(R.fraction.price_padding_fraction, 1, 1);

        volMa1Period = context.getResources().getInteger(R.integer.default_vol_ma1_period);
        volMa2Period = context.getResources().getInteger(R.integer.default_vol_ma2_period);
        volMaWidthPx = context.getResources().getDimension(R.dimen.default_vol_ma_width);
        
        // Giá trị mặc định cho clamp, sẽ được ghi đè bởi setDefaultsFromLayout()
        MIN_VISIBLE_CANDLE_COUNT = 1;
        MAX_VISIBLE_CANDLE_COUNT = 10000;
        DEFAULT_VISIBLE_CANDLE_COUNT = 70;
    }

    public void setViewDimensionsFromLayout(int topPad, int bottomPad, int volumeHeight,
                                            int volumeTopMargin, int priceAxisWidth,
                                            int timeAxisHeight, int priceTextMargin,
                                            int priceTextOffset, int gridWidth,
                                            int bodyMinWidth, int bodyMaxWidth,
                                            int candleMinWidth, int candleMinHeight,
                                            float dashOn, float dashOff,
                                            float timeTextOffset, float loadingTextOffset,
                                            float defaultTextSize, float selectedWidth,
                                            float popupTimeSize, float popupSize) {
        TOP_PADDING_PX = topPad;
        BOTTOM_PADDING_PX = bottomPad;
        VOLUME_CHART_HEIGHT_DP = volumeHeight;
        VOLUME_TOP_MARGIN_PX = volumeTopMargin;
        PRICE_AXIS_WIDTH_DP = priceAxisWidth;
        TIME_AXIS_HEIGHT = timeAxisHeight;
        PRICE_TEXT_MARGIN = priceTextMargin;
        PRICE_TEXT_OFFSET = priceTextOffset;
        GRID_WIDTH = gridWidth;
        BODY_MIN_WIDTH = bodyMinWidth;
        BODY_MAX_WIDTH = bodyMaxWidth;
        DASH_ON = dashOn;
        DASH_OFF = dashOff;
        TIME_TEXT_OFFSET = (int) timeTextOffset;
        LOADING_TEXT_OFFSET = (int) loadingTextOffset;
        TEXT_SIZE = (int) defaultTextSize;
        SELECTED_WIDTH = selectedWidth;
        visibleCandleCount = DEFAULT_VISIBLE_CANDLE_COUNT;
    }

    public void setDefaultsFromLayout(float bodyFrac, float wickW, float maW, int visCount,
                                      boolean showG, boolean showV, boolean showLast, boolean dashed,
                                      float txtSize, float lastW, float labelSize,
                                      int bullColor, int bearColor, int lastColor, int gridColor,
                                      int txtColor, int labelBg, int labelTextColor,
                                      List<MaLine> maDefaults,
                                      int selectedColor, float selectedWidth,
                                      int selectedAlpha, boolean selectedDashed) {
        if (defaultsLoadedFromLayout) {
            cleanupAutoIfNotUserSet();
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
        this.defLastPriceLineColor = lastColor;
        this.defLastPriceBgColor = labelBg;
        this.defGridColor = gridColor;
        this.defPriceTextColor = txtColor;
        this.defLabelTextColor = labelTextColor;
        this.defSelectedLineColor = selectedColor;
        this.defSelectedLineWidthPx = selectedWidth;
        this.defSelectedAlpha = selectedAlpha;
        this.defSelectedDashed = selectedDashed;

        this.defMaLines = new ArrayList<>();
        if (maDefaults!= null) {
            for (MaLine m : maDefaults) {
                this.defMaLines.add(
                        new MaLine(m.period, m.color)
                );
            }
        }

        DEFAULT_VISIBLE_CANDLE_COUNT = visCount;
        visibleCandleCount = defVisibleCount;
        defaultsLoadedFromLayout = true;

        if (maDefaults!= null && maDefaults.size() >= 2) {
            volMa1Color = maDefaults.get(0).color;
            volMa2Color = maDefaults.get(1).color;
        }

        initCandleColors(getContext());
        initMaLines(getContext());
        loadChartOptions(getContext());
        initPaints(getContext());
        invalidate();
    }

    public void setDefaultsFromLayout(float bodyFrac, float wickW, float maW, int visCount,
                                      boolean showG, boolean showV, boolean showLast, boolean dashed,
                                      float txtSize, float lastW, float labelSize,
                                      int bullColor, int bearColor, int lastColor, int gridColor,
                                      int txtColor, int labelBg, int labelTextColor,
                                      List<MaLine> maDefaults) {
        int fallbackSelectedColor = getContext().getResources()
               .getColor(R.color.chart_selected_line, getContext().getTheme());
        float fallbackSelectedWidth = getContext().getResources()
               .getDimension(R.dimen.default_selected_width);
        int fallbackSelectedAlpha = getContext().getResources()
               .getInteger(R.integer.selected_alpha);
        boolean fallbackSelectedDashed = false;

        setDefaultsFromLayout(
                bodyFrac,
                wickW,
                maW,
                visCount,
                showG,
                showV,
                showLast,
                dashed,
                txtSize,
                lastW,
                labelSize,
                bullColor,
                bearColor,
                lastColor,
                gridColor,
                txtColor,
                labelBg,
                labelTextColor,
                maDefaults,
                fallbackSelectedColor,
                fallbackSelectedWidth,
                fallbackSelectedAlpha,
                fallbackSelectedDashed
        );
    }

    /**
     * Load defaults from layout files (chart_settings_*.xml).
     * Uses old select logic: each field keeps def if view not found.
     */
    public void loadDefaultsFromSettingsView(View root) {
        View viewBull = null;
        View viewBear = null;
        View viewSelectedLine = null;
        SeekBar sbWick = null;
        SeekBar sbBody = null;
        SeekBar sbSelectedWidth = null;
        SeekBar sbSelectedAlpha = null;

        try {
            viewBull = root.findViewById(R.id.viewBull);
        } catch (Exception e) {
        }
        try {
            viewBear = root.findViewById(R.id.viewBear);
        } catch (Exception e) {
        }
        try {
            viewSelectedLine = root.findViewById(R.id.viewSelectedLine);
        } catch (Exception e) {
        }
        try {
            sbWick = root.findViewById(R.id.sbWick);
        } catch (Exception e) {
        }
        try {
            sbBody = root.findViewById(R.id.sbBody);
        } catch (Exception e) {
        }
        try {
            sbSelectedWidth = root.findViewById(R.id.sbSelectedWidth);
        } catch (Exception e) {
        }
        try {
            sbSelectedAlpha = root.findViewById(R.id.sbSelectedAlpha);
        } catch (Exception e) {
        }

        int bullColor = defBullColor;
        int bearColor = defBearColor;
        int selectedColor = defSelectedLineColor;

        if (viewBull!= null && viewBull.getTag()!= null) {
            try {
                String bullTag = viewBull.getTag().toString();
                String bullName = bullTag.replace("@color/", "").replace("@", "");
                int bullResId = getContext().getResources().getIdentifier(bullName, "color", getContext().getPackageName());
                if (bullResId!= 0) {
                    bullColor = getContext().getResources().getColor(bullResId, getContext().getTheme());
                }
            } catch (Exception e) {
            }
        }

        if (viewBear!= null && viewBear.getTag()!= null) {
            try {
                String bearTag = viewBear.getTag().toString();
                String bearName = bearTag.replace("@color/", "").replace("@", "");
                int bearResId = getContext().getResources().getIdentifier(bearName, "color", getContext().getPackageName());
                if (bearResId!= 0) {
                    bearColor = getContext().getResources().getColor(bearResId, getContext().getTheme());
                }
            } catch (Exception e) {
            }
        }

        if (viewSelectedLine!= null && viewSelectedLine.getTag()!= null) {
            try {
                String selectedTag = viewSelectedLine.getTag().toString();
                String selectedName = selectedTag.replace("@color/", "").replace("@", "");
                int selectedResId = getContext().getResources().getIdentifier(selectedName, "color", getContext().getPackageName());
                if (selectedResId!= 0) {
                    selectedColor = getContext().getResources().getColor(selectedResId, getContext().getTheme());
                }
            } catch (Exception e) {
            }
        }

        float wickWidth = defWickWidthPx;
        float bodyFraction = defBodyFraction;
        float selectedWidth = defSelectedLineWidthPx;
        int selectedAlpha = defSelectedAlpha;

        if (sbWick!= null) {
            try {
                wickWidth = sbWick.getProgress() + 1f;
                if (wickWidth <= 0f) {
                    wickWidth = defWickWidthPx;
                }
            } catch (Exception e) {
            }
        }

        if (sbBody!= null) {
            try {
                bodyFraction = sbBody.getProgress() / 100f;
                if (bodyFraction <= 0f) {
                    bodyFraction = defBodyFraction;
                }
            } catch (Exception e) {
            }
        }

        if (sbSelectedWidth!= null) {
            try {
                float w = sbSelectedWidth.getProgress();
                if (w <= 0f) {
                    w = defSelectedLineWidthPx;
                }
                selectedWidth = w;
            } catch (Exception e) {
            }
        }

        if (sbSelectedAlpha!= null) {
            try {
                int a = sbSelectedAlpha.getProgress();
                if (a <= 0) {
                    a = defSelectedAlpha;
                }
                if (a > 255) {
                    a = 255;
                }
                selectedAlpha = a;
            } catch (Exception e) {
            }
        }

        setDefaultsFromLayout(
                bodyFraction,
                wickWidth,
                defMaWidthPx,
                defVisibleCount,
                defShowGrid,
                defShowVolume,
                defShowLastPrice,
                defLastDashed,
                defPriceTextSizePx,
                defLastLineWidthPx,
                defLabelTextSizePx,
                bullColor,
                bearColor,
                defLastPriceLineColor,
                defGridColor,
                defPriceTextColor,
                defLastPriceBgColor,
                defLabelTextColor,
                defMaLines,
                selectedColor,
                selectedWidth,
                selectedAlpha,
                defSelectedDashed
        );
    }

    private void reloadDefaultColorsFromCurrentTheme() {
        Context ctx = getContext();
        defGridColor = ctx.getResources()
               .getColor(R.color.chart_grid, ctx.getTheme());
        defPriceTextColor = ctx.getResources()
               .getColor(R.color.chart_text, ctx.getTheme());
        defSelectedLineColor = ctx.getResources()
               .getColor(R.color.chart_selected_line, ctx.getTheme());
        defLabelTextColor = ctx.getResources()
               .getColor(R.color.last_label_text, ctx.getTheme());
        defLastPriceLineColor = ctx.getResources()
               .getColor(R.color.chart_last_price_line, ctx.getTheme());
        defLastPriceBgColor = ctx.getResources()
               .getColor(R.color.chart_last_price_label_bg, ctx.getTheme());
        defBullColor = ctx.getResources()
               .getColor(R.color.chart_bull_default, ctx.getTheme());
        defBearColor = ctx.getResources()
               .getColor(R.color.chart_bear_default, ctx.getTheme());
        // Width / Alpha / Dashed defaults are from dimens/integers, keep as is for auto
    }

    /**
     * Fix for user action: which part user set is saved, which not set stays auto according to theme.
     * When changing theme, saved parts are redrawn, auto parts remain auto.
     * Selected line has 4 parts: color, width, alpha, dashed.
     */
    private void cleanupAutoIfNotUserSet() {
        try {
            Context ctx = getContext();
            SharedPreferences sp = ctx.getSharedPreferences(
                    ctx.getString(R.string.prefs_chart),
                    Context.MODE_PRIVATE
            );
            SharedPreferences.Editor ed = sp.edit();

            if (!sp.getBoolean(KEY_SELECTED_COLOR_USER_SET, false)) {
                ed.remove(ctx.getString(R.string.key_selected_line_color));
            }
            if (!sp.getBoolean(KEY_SELECTED_WIDTH_USER_SET, false)) {
                ed.remove(ctx.getString(R.string.key_selected_line_width));
            }
            if (!sp.getBoolean(KEY_SELECTED_ALPHA_USER_SET, false)) {
                ed.remove(ctx.getString(R.string.key_selected_line_alpha));
            }
            if (!sp.getBoolean(KEY_SELECTED_DASHED_USER_SET, false)) {
                ed.remove(ctx.getString(R.string.key_selected_line_dash));
            }

            ed.commit();
        } catch (Exception e) {
        }
    }

    private void initCandleColors(Context context) {
        try {
            SharedPreferences sp = context.getSharedPreferences(
                    context.getString(R.string.prefs_candle),
                    Context.MODE_PRIVATE
            );
            bullishColor = sp.contains(context.getString(R.string.key_bull))?
                    sp.getInt(context.getString(R.string.key_bull), defBullColor) :
                    defBullColor;
            bearishColor = sp.contains(context.getString(R.string.key_bear))?
                    sp.getInt(context.getString(R.string.key_bear), defBearColor) :
                    defBearColor;
        } catch (Exception e) {
            bullishColor = defBullColor;
            bearishColor = defBearColor;
        }
    }

    private float getFloatCompat(SharedPreferences sp, String key, float defVal) {
        try {
            if (!sp.contains(key)) {
                return defVal;
            }
            return sp.getFloat(key, defVal);
        } catch (ClassCastException e) {
            try {
                return (float) sp.getInt(key, (int) defVal);
            } catch (ClassCastException e2) {
                return defVal;
            }
        } catch (Exception e) {
            return defVal;
        }
    }

    private int getIntCompat(SharedPreferences sp, String key, int defVal) {
        try {
            if (!sp.contains(key)) {
                return defVal;
            }
            return sp.getInt(key, defVal);
        } catch (Exception e) {
            return defVal;
        }
    }

    private void loadChartOptions(Context context) {
        try {
            SharedPreferences sp = context.getSharedPreferences(
                    context.getString(R.string.prefs_chart),
                    Context.MODE_PRIVATE
            );

            bodyWidthFraction = sp.contains(context.getString(R.string.key_body_fraction))?
                    getFloatCompat(sp, context.getString(R.string.key_body_fraction), defBodyFraction) :
                    defBodyFraction;

            wickWidthPx = sp.contains(context.getString(R.string.key_wick_width))?
                    getFloatCompat(sp, context.getString(R.string.key_wick_width), defWickWidthPx) :
                    defWickWidthPx;

            maLineWidthPx = sp.contains(context.getString(R.string.key_ma_width))?
                    getFloatCompat(sp, context.getString(R.string.key_ma_width), defMaWidthPx) :
                    defMaWidthPx;

            showGrid = sp.contains(context.getString(R.string.key_show_grid))?
                    sp.getBoolean(context.getString(R.string.key_show_grid), defShowGrid) :
                    defShowGrid;

            showVolume = sp.contains(context.getString(R.string.key_show_volume))?
                    sp.getBoolean(context.getString(R.string.key_show_volume), defShowVolume) :
                    defShowVolume;

            visibleCandleCount = sp.contains(context.getString(R.string.key_visible_count))?
                    sp.getInt(context.getString(R.string.key_visible_count), defVisibleCount) :
                    defVisibleCount;

            showLastPriceLine = sp.contains(context.getString(R.string.key_show_last_price))?
                    sp.getBoolean(context.getString(R.string.key_show_last_price), defShowLastPrice) :
                    defShowLastPrice;

            if (sp.contains(context.getString(R.string.key_last_price_line_color))) {
                lastPriceLineColor = sp.getInt(
                        context.getString(R.string.key_last_price_line_color),
                        defLastPriceLineColor
                );
            } else {
                lastPriceLineColor = defLastPriceLineColor;
            }

            if (sp.contains(context.getString(R.string.key_last_price_bg_color))) {
                lastPriceBgColor = sp.getInt(
                        context.getString(R.string.key_last_price_bg_color),
                        defLastPriceBgColor
                );
            } else {
                lastPriceBgColor = defLastPriceBgColor;
            }

            priceTextSizePx = sp.contains(context.getString(R.string.key_price_text_size))?
                    getFloatCompat(sp, context.getString(R.string.key_price_text_size), defPriceTextSizePx) :
                    defPriceTextSizePx;

            if (sp.contains(context.getString(R.string.key_price_text_color))) {
                priceTextColor = sp.getInt(
                        context.getString(R.string.key_price_text_color),
                        defPriceTextColor
                );
            } else {
                priceTextColor = defPriceTextColor;
            }

            if (sp.contains(context.getString(R.string.key_grid_color))) {
                gridColor = sp.getInt(context.getString(R.string.key_grid_color), defGridColor);
            } else {
                gridColor = defGridColor;
            }

            bgColor = sp.contains(context.getString(R.string.key_bg_color))?
                    sp.getInt(context.getString(R.string.key_bg_color), 0) :
                    0;

            lastLineWidthPx = sp.contains(context.getString(R.string.key_last_line_width))?
                    getFloatCompat(sp, context.getString(R.string.key_last_line_width), defLastLineWidthPx) :
                    defLastLineWidthPx;

            lastLineDashed = sp.contains(context.getString(R.string.key_last_line_dash))?
                    sp.getBoolean(context.getString(R.string.key_last_line_dash), defLastDashed) :
                    defLastDashed;

            lastPriceLabelTextSizePx = sp.contains(context.getString(R.string.key_last_label_text_size))?
                    getFloatCompat(sp, context.getString(R.string.key_last_label_text_size), defLabelTextSizePx) :
                    defLabelTextSizePx;

            if (sp.contains(context.getString(R.string.key_last_label_text_color))) {
                lastPriceLabelTextColor = sp.getInt(
                        context.getString(R.string.key_last_label_text_color),
                        defLabelTextColor
                );
            } else {
                lastPriceLabelTextColor = defLabelTextColor;
            }

            if (sp.contains(context.getString(R.string.key_selected_line_color))) {
                selectedLineColor = sp.getInt(
                        context.getString(R.string.key_selected_line_color),
                        defSelectedLineColor
                );
            } else {
                selectedLineColor = defSelectedLineColor;
            }

            selectedLineWidthPx = sp.contains(context.getString(R.string.key_selected_line_width))?
                    getFloatCompat(sp, context.getString(R.string.key_selected_line_width), defSelectedLineWidthPx) :
                    defSelectedLineWidthPx;

            selectedLineAlpha = sp.contains(context.getString(R.string.key_selected_line_alpha))?
                    getIntCompat(sp, context.getString(R.string.key_selected_line_alpha), defSelectedAlpha) :
                    defSelectedAlpha;

            selectedLineDashed = sp.contains(context.getString(R.string.key_selected_line_dash))?
                    sp.getBoolean(
                            context.getString(R.string.key_selected_line_dash),
                            defSelectedDashed
                    ) :
                    defSelectedDashed;

            showVolMa = sp.contains(context.getString(R.string.key_vol_show_ma))?
                    sp.getBoolean(context.getString(R.string.key_vol_show_ma), true) :
                    true;

            volMa1Period = sp.contains(context.getString(R.string.key_vol_ma1_period))?
                    sp.getInt(context.getString(R.string.key_vol_ma1_period), volMa1Period) :
                    volMa1Period;

            volMa2Period = sp.contains(context.getString(R.string.key_vol_ma2_period))?
                    sp.getInt(context.getString(R.string.key_vol_ma2_period), volMa2Period) :
                    volMa2Period;

            if (sp.contains(context.getString(R.string.key_vol_ma1_color))) {
                volMa1Color = sp.getInt(context.getString(R.string.key_vol_ma1_color), volMa1Color);
            }

            if (sp.contains(context.getString(R.string.key_vol_ma2_color))) {
                volMa2Color = sp.getInt(context.getString(R.string.key_vol_ma2_color), volMa2Color);
            }

            volMaWidthPx = sp.contains(context.getString(R.string.key_vol_ma_width))?
                    getFloatCompat(sp, context.getString(R.string.key_vol_ma_width), volMaWidthPx) :
                    volMaWidthPx;

            SharedPreferences sp2 = context.getSharedPreferences(
                    "chart_settings",
                    Context.MODE_PRIVATE
            );
            if (sp2.contains("label_bg")) {
                lastPriceBgColor = sp2.getInt("label_bg", lastPriceBgColor);
            } else if (sp2.contains("current_price_label_bg")) {
                lastPriceBgColor = sp2.getInt("current_price_label_bg", lastPriceBgColor);
            }

            if (sp2.contains("label_text_color")) {
                lastPriceLabelTextColor = sp2.getInt("label_text_color", lastPriceLabelTextColor);
            } else if (sp2.contains("current_price_label_text")) {
                lastPriceLabelTextColor = sp2.getInt("current_price_label_text", lastPriceLabelTextColor);
            }

            if (sp2.contains("grid_color")) {
                gridColor = sp2.getInt("grid_color", gridColor);
            }
            if (sp2.contains("price_text_color")) {
                priceTextColor = sp2.getInt("price_text_color", priceTextColor);
            }

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
            selectedLineColor = defSelectedLineColor;
            selectedLineWidthPx = defSelectedLineWidthPx;
            selectedLineAlpha = defSelectedAlpha;
            selectedLineDashed = defSelectedDashed;
            showVolMa = true;
        }
    }

    public int getBullishColor() {
        return bullishColor;
    }

    public int getBearishColor() {
        return bearish
