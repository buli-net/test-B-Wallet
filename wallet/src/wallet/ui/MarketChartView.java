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

    private float getDimenFromTag(Context context, View v) {
        if (v == null) {
            throw new IllegalStateException("View tag missing dimen");
        }
        Object tag = v.getTag();
        if (tag == null) {
            throw new IllegalStateException("View tag missing dimen");
        }
        if (tag instanceof Integer) {
            return context.getResources().getDimension((Integer) tag);
        }
        String s = tag.toString().trim();
        if (s.startsWith("@dimen/")) {
            String name = s.replace("@dimen/", "");
            int resId = context.getResources().getIdentifier(name, "dimen", context.getPackageName());
            if (resId!= 0) {
                return context.getResources().getDimension(resId, context.getTheme());
            }
        }
        try {
            int resId = Integer.parseInt(s);
            return context.getResources().getDimension(resId, context.getTheme());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid dimen tag: " + s);
        }
    }

    private int getIntegerFromTag(Context context, View v) {
        if (v == null) {
            throw new IllegalStateException("View tag missing integer");
        }
        Object tag = v.getTag();
        if (tag == null) {
            throw new IllegalStateException("View tag missing integer");
        }
        if (tag instanceof Integer) {
            try {
                return context.getResources().getInteger((Integer) tag);
            } catch (Exception e) {
                return (int) context.getResources().getDimension((Integer) tag);
            }
        }
        String s = tag.toString().trim();
        if (s.startsWith("@integer/")) {
            String name = s.replace("@integer/", "");
            int resId = context.getResources().getIdentifier(name, "integer", context.getPackageName());
            if (resId!= 0) {
                return context.getResources().getInteger(resId);
            }
        }
        try {
            int resId = Integer.parseInt(s);
            try {
                return context.getResources().getInteger(resId);
            } catch (Exception ex) {
                return (int) context.getResources().getDimension(resId);
            }
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid integer tag: " + s);
        }
    }

    private float getFractionFromTag(Context context, View v, int base, int pbase) {
        if (v == null) {
            throw new IllegalStateException("View tag missing fraction");
        }
        Object tag = v.getTag();
        if (tag == null) {
            throw new IllegalStateException("View tag missing fraction");
        }
        if (tag instanceof Integer) {
            return context.getResources().getFraction((Integer) tag, base, pbase);
        }
        String s = tag.toString().trim();
        if (s.startsWith("@fraction/")) {
            String name = s.replace("@fraction/", "");
            int resId = context.getResources().getIdentifier(name, "fraction", context.getPackageName());
            if (resId!= 0) {
                return context.getResources().getFraction(resId, base, pbase);
            }
        }
        if (s.startsWith("@dimen/")) {
            String name = s.replace("@dimen/", "");
            int resId = context.getResources().getIdentifier(name, "dimen", context.getPackageName());
            if (resId!= 0) {
                return context.getResources().getDimension(resId);
            }
        }
        try {
            int resId = Integer.parseInt(s);
            return context.getResources().getFraction(resId, base, pbase);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid fraction tag: " + s);
        }
    }

    private int getColorFromTag(Context context, View v) {
        if (v == null) {
            throw new IllegalStateException("View tag missing color");
        }
        Object tag = v.getTag();
        if (tag == null) {
            throw new IllegalStateException("View tag missing color");
        }
        if (tag instanceof Integer) {
            try {
                return context.getResources().getColor((Integer) tag, context.getTheme());
            } catch (Exception e) {
                return (Integer) tag;
            }
        }
        String s = tag.toString().trim();
        if (s.startsWith("#")) {
            return Color.parseColor(s);
        }
        if (s.startsWith("@color/")) {
            String colorName = s.replace("@color/", "");
            int resId = context.getResources().getIdentifier(colorName, "color", context.getPackageName());
            if (resId!= 0) {
                return context.getResources().getColor(resId, context.getTheme());
            }
        }
        try {
            int resId = Integer.parseInt(s);
            return context.getResources().getColor(resId, context.getTheme());
        } catch (NumberFormatException e) {
            return Color.parseColor(s);
        }
    }

    private void loadViewDimensions(Context context) {
        // Law: all dimen / integer / fraction / color must come via chart_settings_*.xml tag, not direct R.dimen / R.integer / R.fraction
        try {
            View candleRoot = View.inflate(context, R.layout.chart_settings_candle, null);
            View volRoot = View.inflate(context, R.layout.chart_settings_vol_ma, null);
            View gridRoot = View.inflate(context, R.layout.chart_settings_grid, null);
            View lastRoot = View.inflate(context, R.layout.chart_settings_last_price, null);
            View selectedRoot = View.inflate(context, R.layout.chart_settings_selected, null);

            TOP_PADDING_PX = (int) getDimenFromTag(context, candleRoot.findViewById(R.id.def_top_padding));
            BOTTOM_PADDING_PX = (int) getDimenFromTag(context, candleRoot.findViewById(R.id.def_bottom_padding));
            VOLUME_CHART_HEIGHT_DP = (int) getDimenFromTag(context, candleRoot.findViewById(R.id.def_volume_height));
            VOLUME_TOP_MARGIN_PX = (int) getDimenFromTag(context, candleRoot.findViewById(R.id.def_volume_top_margin));
            PRICE_AXIS_WIDTH_DP = (int) getDimenFromTag(context, candleRoot.findViewById(R.id.def_price_axis_width));
            TIME_AXIS_HEIGHT = (int) getDimenFromTag(context, candleRoot.findViewById(R.id.def_time_axis_height));
            PRICE_TEXT_MARGIN = (int) getDimenFromTag(context, candleRoot.findViewById(R.id.def_price_text_margin));
            PRICE_TEXT_OFFSET = (int) getDimenFromTag(context, candleRoot.findViewById(R.id.def_price_text_offset));
            GRID_WIDTH = getDimenFromTag(context, candleRoot.findViewById(R.id.def_grid_width));
            BODY_MIN_WIDTH = (int) getDimenFromTag(context, candleRoot.findViewById(R.id.def_body_min_width));
            BODY_MAX_WIDTH = (int) getDimenFromTag(context, candleRoot.findViewById(R.id.def_body_max_width));
            TEXT_SIZE = (int) getDimenFromTag(context, candleRoot.findViewById(R.id.def_default_text_size));
            SELECTED_WIDTH = getDimenFromTag(context, candleRoot.findViewById(R.id.def_selected_width));
            DASH_ON = getDimenFromTag(context, candleRoot.findViewById(R.id.def_dash_on));
            DASH_OFF = getDimenFromTag(context, candleRoot.findViewById(R.id.def_dash_off));
            TIME_TEXT_OFFSET = (int) getDimenFromTag(context, candleRoot.findViewById(R.id.def_time_text_offset));
            LOADING_TEXT_OFFSET = (int) getDimenFromTag(context, candleRoot.findViewById(R.id.def_loading_text_offset));
            CANDLE_MIN_HEIGHT = (int) getDimenFromTag(context, candleRoot.findViewById(R.id.def_candle_min_height));

            FETCH_LIMIT = getIntegerFromTag(context, candleRoot.findViewById(R.id.def_fetch_limit));
            MIN_VISIBLE_CANDLE_COUNT = getIntegerFromTag(context, candleRoot.findViewById(R.id.def_min_vis));
            MAX_VISIBLE_CANDLE_COUNT = getIntegerFromTag(context, candleRoot.findViewById(R.id.def_max_vis));
            DEFAULT_VISIBLE_CANDLE_COUNT = getIntegerFromTag(context, candleRoot.findViewById(R.id.def_default_vis));
            VOLUME_ALPHA = getIntegerFromTag(context, candleRoot.findViewById(R.id.def_volume_alpha));
            SELECTED_ALPHA = getIntegerFromTag(context, candleRoot.findViewById(R.id.def_selected_alpha));
            BIG_FIAT_THRESHOLD = getIntegerFromTag(context, candleRoot.findViewById(R.id.def_big_fiat_threshold));
            NETWORK_TIMEOUT = getIntegerFromTag(context, candleRoot.findViewById(R.id.def_network_timeout));
            LIVE_REFRESH_INTERVAL_MS = getIntegerFromTag(context, candleRoot.findViewById(R.id.def_live_refresh_interval));
            COUNTDOWN_INTERVAL_MS = getIntegerFromTag(context, candleRoot.findViewById(R.id.def_countdown_interval));

            MIN_SCROLL_FRACTION = getFractionFromTag(context, candleRoot.findViewById(R.id.def_min_scroll_fraction), 1, 1);
            PRICE_PADDING_FRACTION = getFractionFromTag(context, candleRoot.findViewById(R.id.def_price_padding_fraction), 1, 1);

            volMa1Period = getIntegerFromTag(context, volRoot.findViewById(R.id.def_vol_ma1_period));
            volMa2Period = getIntegerFromTag(context, volRoot.findViewById(R.id.def_vol_ma2_period));
            volMaWidthPx = getDimenFromTag(context, volRoot.findViewById(R.id.def_vol_ma_width));

        } catch (Exception e) {
            // Fallback to safe defaults if chart_settings layout missing - do not use R.dimen directly
            TOP_PADDING_PX = 10;
            BOTTOM_PADDING_PX = 10;
            VOLUME_CHART_HEIGHT_DP = 80;
            VOLUME_TOP_MARGIN_PX = 10;
            PRICE_AXIS_WIDTH_DP = 80;
            TIME_AXIS_HEIGHT = 30;
            PRICE_TEXT_MARGIN = 4;
            PRICE_TEXT_OFFSET = 4;
            GRID_WIDTH = 1f;
            BODY_MIN_WIDTH = 2;
            BODY_MAX_WIDTH = 20;
            TEXT_SIZE = 24;
            SELECTED_WIDTH = 2f;
            DASH_ON = 8f;
            DASH_OFF = 4f;
            TIME_TEXT_OFFSET = 4;
            LOADING_TEXT_OFFSET = 20;
            CANDLE_MIN_HEIGHT = 2;
            FETCH_LIMIT = 300;
            MIN_VISIBLE_CANDLE_COUNT = 10;
            MAX_VISIBLE_CANDLE_COUNT = 200;
            DEFAULT_VISIBLE_CANDLE_COUNT = 100;
            VOLUME_ALPHA = 80;
            SELECTED_ALPHA = 150;
            BIG_FIAT_THRESHOLD = 1000;
            NETWORK_TIMEOUT = 10000;
            LIVE_REFRESH_INTERVAL_MS = 5000;
            COUNTDOWN_INTERVAL_MS = 1000;
            MIN_SCROLL_FRACTION = 0.2f;
            PRICE_PADDING_FRACTION = 0.1f;
        }
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
        // Law: no direct R.color / R.dimen - use def values already loaded from layout
        int fallbackSelectedColor = defSelectedLineColor!= 0? defSelectedLineColor : Color.parseColor("#2196F3");
        float fallbackSelectedWidth = defSelectedLineWidthPx!= 0f? defSelectedLineWidthPx : SELECTED_WIDTH;
        int fallbackSelectedAlpha = defSelectedAlpha!= 0? defSelectedAlpha : SELECTED_ALPHA;
        boolean fallbackSelectedDashed = defSelectedDashed;

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
                bullColor = getColorFromTag(getContext(), viewBull);
            } catch (Exception e) {
            }
        }

        if (viewBear!= null && viewBear.getTag()!= null) {
            try {
                bearColor = getColorFromTag(getContext(), viewBear);
            } catch (Exception e) {
            }
        }

        if (viewSelectedLine!= null && viewSelectedLine.getTag()!= null) {
            try {
                selectedColor = getColorFromTag(getContext(), viewSelectedLine);
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
        // Law: reload colors via tag from chart_settings layouts, not direct R.color
        try {
            Context ctx = getContext();
            View candleRoot = View.inflate(ctx, R.layout.chart_settings_candle, null);
            View gridRoot = View.inflate(ctx, R.layout.chart_settings_grid, null);
            View lastRoot = View.inflate(ctx, R.layout.chart_settings_last_price, null);
            View selectedRoot = View.inflate(ctx, R.layout.chart_settings_selected, null);

            View gridColorView = gridRoot.findViewById(R.id.viewGridColor);
            View priceTextView = lastRoot.findViewById(R.id.viewTxtColor);
            View selectedLineView = selectedRoot.findViewById(R.id.viewSelectedLine);
            View labelTextView = candleRoot.findViewById(R.id.viewLabelTextColor);
            View lastLineView = lastRoot.findViewById(R.id.viewLastColor);
            View lastBgView = candleRoot.findViewById(R.id.viewLabelBg);
            View bullView = candleRoot.findViewById(R.id.viewBull);
            View bearView = candleRoot.findViewById(R.id.viewBear);

            if (gridColorView!= null) defGridColor = getColorFromTag(ctx, gridColorView);
            if (priceTextView!= null) defPriceTextColor = getColorFromTag(ctx, priceTextView);
            if (selectedLineView!= null) defSelectedLineColor = getColorFromTag(ctx, selectedLineView);
            if (labelTextView!= null) defLabelTextColor = getColorFromTag(ctx, labelTextView);
            if (lastLineView!= null) defLastPriceLineColor = getColorFromTag(ctx, lastLineView);
            if (lastBgView!= null) defLastPriceBgColor = getColorFromTag(ctx, lastBgView);
            if (bullView!= null) defBullColor = getColorFromTag(ctx, bullView);
            if (bearView!= null) defBearColor = getColorFromTag(ctx, bearView);

        } catch (Exception e) {
            // fallback uses theme attributes, not direct R.color
            Context ctx = getContext();
            defGridColor = getThemeColor(android.R.attr.textColorSecondary);
            defPriceTextColor = getThemeColor(android.R.attr.textColorPrimary);
            defSelectedLineColor = getThemeColor(android.R.attr.colorAccent);
            defLabelTextColor = getThemeColor(android.R.attr.textColorPrimaryInverse);
            defLastPriceLineColor = getThemeColor(android.R.attr.colorAccent);
            defLastPriceBgColor = getThemeColor(android.R.attr.colorBackground);
            defBullColor = Color.parseColor("#26A69A");
            defBearColor = Color.parseColor("#EF5350");
        }
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
        return bearishColor;
    }

    public float getBodyWidthFraction() {
        return bodyWidthFraction;
    }

    public float getWickWidthPx() {
        return wickWidthPx;
    }

    public float getMaLineWidthPx() {
        return maLineWidthPx;
    }

    public boolean isShowGrid() {
        return showGrid;
    }

    public boolean isShowVolume() {
        return showVolume;
    }

    public int getVisibleCandleCountValue() {
        return visibleCandleCount;
    }

    public boolean isShowLastPriceLine() {
        return showLastPriceLine;
    }

    public int getLastPriceLineColor() {
        return lastPriceLineColor;
    }

    public int getLastPriceBgColor() {
        return lastPriceBgColor;
    }

    public float getPriceTextSizePx() {
        return priceTextSizePx;
    }

    public int getPriceTextColor() {
        return priceTextColor;
    }

    public int getGridColor() {
        return gridColor;
    }

    public int getBgColor() {
        return bgColor;
    }

    public float getLastLineWidthPx() {
        return lastLineWidthPx;
    }

    public boolean isLastLineDashed() {
        return lastLineDashed;
    }

    public float getLastPriceLabelTextSizePx() {
        return lastPriceLabelTextSizePx;
    }

    public int getLastPriceLabelTextColor() {
        return lastPriceLabelTextColor;
    }

    public int getSelectedLineColor() {
        return selectedLineColor;
    }

    public float getSelectedLineWidthPx() {
        return selectedLineWidthPx;
    }

    public int getSelectedLineAlpha() {
        return selectedLineAlpha;
    }

    public boolean isSelectedLineDashed() {
        return selectedLineDashed;
    }

    public boolean isShowVolMa() {
        return showVolMa;
    }

    public int getVolMa1Period() {
        return volMa1Period;
    }

    public int getVolMa2Period() {
        return volMa2Period;
    }

    public int getVolMa1Color() {
        return volMa1Color;
    }

    public int getVolMa2Color() {
        return volMa2Color;
    }

    public float getVolMaWidthPx() {
        return volMaWidthPx;
    }

    public void setBodyFraction(float fraction) {
        this.bodyWidthFraction = fraction;
        try {
            SharedPreferences sp = getContext().getSharedPreferences(
                    getContext().getString(R.string.prefs_chart),
                    Context.MODE_PRIVATE
            );
            sp.edit()
                  .putFloat(getContext().getString(R.string.key_body_fraction), fraction)
                  .commit();
        } catch (Exception e) {
        }
        initPaints(getContext());
        invalidate();
    }

    public void setWickWidthPx(float widthPx) {
        this.wickWidthPx = widthPx;
        try {
            SharedPreferences sp = getContext().getSharedPreferences(
                    getContext().getString(R.string.prefs_chart),
                    Context.MODE_PRIVATE
            );
            sp.edit()
                  .putFloat(getContext().getString(R.string.key_wick_width), widthPx)
                  .commit();
        } catch (Exception e) {
        }
        initPaints(getContext());
        invalidate();
    }

    public void setMaLineWidthPx(float widthPx) {
        this.maLineWidthPx = widthPx;
        try {
            SharedPreferences sp = getContext().getSharedPreferences(
                    getContext().getString(R.string.prefs_chart),
                    Context.MODE_PRIVATE
            );
            sp.edit()
                  .putFloat(getContext().getString(R.string.key_ma_width), widthPx)
                  .commit();
        } catch (Exception e) {
        }
        initPaints(getContext());
        invalidate();
    }

    public void setShowGrid(boolean show) {
        this.showGrid = show;
        try {
            SharedPreferences sp = getContext().getSharedPreferences(
                    getContext().getString(R.string.prefs_chart),
                    Context.MODE_PRIVATE
            );
            sp.edit()
                  .putBoolean(getContext().getString(R.string.key_show_grid), show)
                  .commit();
        } catch (Exception e) {
        }
        invalidate();
    }

    public void setShowVolume(boolean show) {
        this.showVolume = show;
        try {
            SharedPreferences sp = getContext().getSharedPreferences(
                    getContext().getString(R.string.prefs_chart),
                    Context.MODE_PRIVATE
            );
            sp.edit()
                  .putBoolean(getContext().getString(R.string.key_show_volume), show)
                  .commit();
        } catch (Exception e) {
        }
        invalidate();
    }

    public void setVisibleCandleCount(int count) {
        this.visibleCandleCount = count;
        clampVisibleCount();
        try {
            SharedPreferences sp = getContext().getSharedPreferences(
                    getContext().getString(R.string.prefs_chart),
                    Context.MODE_PRIVATE
            );
            sp.edit()
                  .putInt(getContext().getString(R.string.key_visible_count), this.visibleCandleCount)
                  .commit();
        } catch (Exception e) {
        }
        clampTranslationX();
        invalidate();
    }

    public void setShowLastPriceLine(boolean show) {
        this.showLastPriceLine = show;
        try {
            SharedPreferences sp = getContext().getSharedPreferences(
                    getContext().getString(R.string.prefs_chart),
                    Context.MODE_PRIVATE
            );
            sp.edit()
                  .putBoolean(getContext().getString(R.string.key_show_last_price), show)
                  .commit();
        } catch (Exception e) {
        }
        invalidate();
    }

    public void setLastPriceLineColor(int color) {
        this.lastPriceLineColor = color;
        try {
            SharedPreferences sp = getContext().getSharedPreferences(
                    getContext().getString(R.string.prefs_chart),
                    Context.MODE_PRIVATE
            );
            sp.edit()
                  .putInt(getContext().getString(R.string.key_last_price_line_color), color)
                  .commit();
        } catch (Exception e) {
        }
        initPaints(getContext());
        invalidate();
    }

    public void setBgColor(int color) {
        this.bgColor = color;
        try {
            SharedPreferences sp = getContext().getSharedPreferences(
                    getContext().getString(R.string.prefs_chart),
                    Context.MODE_PRIVATE
            );
            if (color == 0) {
                sp.edit()
                      .remove(getContext().getString(R.string.key_bg_color))
                      .commit();
            } else {
                sp.edit()
                      .putInt(getContext().getString(R.string.key_bg_color), color)
                      .commit();
            }
        } catch (Exception e) {
        }
        initPaints(getContext());
        invalidate();
    }

    public void setPriceTextSizePx(float sizePx) {
        this.priceTextSizePx = sizePx;
        try {
            SharedPreferences sp = getContext().getSharedPreferences(
                    getContext().getString(R.string.prefs_chart),
                    Context.MODE_PRIVATE
            );
            sp.edit()
                  .putFloat(getContext().getString(R.string.key_price_text_size), sizePx)
                  .commit();
        } catch (Exception e) {
        }
        initPaints(getContext());
        invalidate();
    }

    public void setLastLineWidthPx(float widthPx) {
        this.lastLineWidthPx = widthPx;
        try {
            SharedPreferences sp = getContext().getSharedPreferences(
                    getContext().getString(R.string.prefs_chart),
                    Context.MODE_PRIVATE
            );
            sp.edit()
                  .putFloat(getContext().getString(R.string.key_last_line_width), widthPx)
                  .commit();
        } catch (Exception e) {
        }
        initPaints(getContext());
        invalidate();
    }

    public void setLastLineDashed(boolean dashed) {
        this.lastLineDashed = dashed;
        try {
            SharedPreferences sp = getContext().getSharedPreferences(
                    getContext().getString(R.string.prefs_chart),
                    Context.MODE_PRIVATE
            );
            sp.edit()
                  .putBoolean(getContext().getString(R.string.key_last_line_dash), dashed)
                  .commit();
        } catch (Exception e) {
        }
        initPaints(getContext());
        invalidate();
    }

    public void setCurrentPriceLabelTextSizePx(float sizePx) {
        this.lastPriceLabelTextSizePx = sizePx;
        try {
            SharedPreferences sp = getContext().getSharedPreferences(
                    getContext().getString(R.string.prefs_chart),
                    Context.MODE_PRIVATE
            );
            sp.edit()
                  .putFloat(getContext().getString(R.string.key_last_label_text_size), sizePx)
                  .commit();
        } catch (Exception e) {
        }
        initPaints(getContext());
        invalidate();
    }

    public void setGridColor(int color) {
        this.gridColor = color;
        try {
            SharedPreferences sp = getContext().getSharedPreferences(
                    getContext().getString(R.string.prefs_chart),
                    Context.MODE_PRIVATE
            );
            sp.edit()
                  .putInt(getContext().getString(R.string.key_grid_color), color)
                  .commit();
        } catch (Exception e) {
        }
        initPaints(getContext());
        invalidate();
    }

    public void setPriceTextColor(int color) {
        this.priceTextColor = color;
        try {
            SharedPreferences sp = getContext().getSharedPreferences(
                    getContext().getString(R.string.prefs_chart),
                    Context.MODE_PRIVATE
            );
            sp.edit()
                  .putInt(getContext().getString(R.string.key_price_text_color), color)
                  .commit();
        } catch (Exception e) {
        }
        initPaints(getContext());
        invalidate();
    }

    public void setCurrentPriceLabelBackground(int color) {
        this.lastPriceBgColor = color;
        try {
            SharedPreferences sp = getContext().getSharedPreferences(
                    getContext().getString(R.string.prefs_chart),
                    Context.MODE_PRIVATE
            );
            sp.edit()
                  .putInt(getContext().getString(R.string.key_last_price_bg_color), color)
                  .commit();

            SharedPreferences sp2 = getContext().getSharedPreferences(
                    "chart_settings",
                    Context.MODE_PRIVATE
            );
            sp2.edit()
                  .putInt("label_bg", color)
                  .putInt("current_price_label_bg", color)
                  .commit();

        } catch (Exception e) {
        }
        initPaints(getContext());
        invalidate();
    }

    public void setCurrentPriceLabelTextColor(int color) {
        this.lastPriceLabelTextColor = color;
        try {
            SharedPreferences sp = getContext().getSharedPreferences(
                    getContext().getString(R.string.prefs_chart),
                    Context.MODE_PRIVATE
            );
            sp.edit()
                  .putInt(getContext().getString(R.string.key_last_label_text_color), color)
                  .commit();

            SharedPreferences sp2 = getContext().getSharedPreferences(
                    "chart_settings",
                    Context.MODE_PRIVATE
            );
            sp2.edit()
                  .putInt("label_text_color", color)
                  .putInt("current_price_label_text", color)
                  .commit();

        } catch (Exception e) {
        }
        initPaints(getContext());
        invalidate();
    }

    public void setLastPriceLabelAppearance(int bgColor, int textColor, float textSizePx) {
        setCurrentPriceLabelBackground(bgColor);
        setCurrentPriceLabelTextColor(textColor);
        setCurrentPriceLabelTextSizePx(textSizePx);
    }

    // Auto version: do NOT set user flag, but still save
    public void setSelectedLineColor(int color) {
        this.selectedLineColor = color;
        try {
            SharedPreferences sp = getContext().getSharedPreferences(
                    getContext().getString(R.string.prefs_chart),
                    Context.MODE_PRIVATE
            );
            sp.edit()
                  .putInt(getContext().getString(R.string.key_selected_line_color), color)
                  .commit();
        } catch (Exception e) {
        }
        initPaints(getContext());
        invalidate();
    }

    // User set - save regardless of color, any color must be saved
    public void setSelectedLineColorByUser(int color) {
        this.selectedLineColor = color;
        try {
            SharedPreferences sp = getContext().getSharedPreferences(
                    getContext().getString(R.string.prefs_chart),
                    Context.MODE_PRIVATE
            );
            sp.edit()
                  .putInt(getContext().getString(R.string.key_selected_line_color), color)
                  .putBoolean(KEY_SELECTED_COLOR_USER_SET, true)
                  .commit();
        } catch (Exception e) {
        }
        initPaints(getContext());
        invalidate();
    }

    public void setSelectedLineWidthPx(float widthPx) {
        if (widthPx <= 0f) {
            widthPx = defSelectedLineWidthPx;
        }
        this.selectedLineWidthPx = widthPx;
        try {
            SharedPreferences sp = getContext().getSharedPreferences(
                    getContext().getString(R.string.prefs_chart),
                    Context.MODE_PRIVATE
            );
            sp.edit()
                  .putFloat(getContext().getString(R.string.key_selected_line_width), widthPx)
                  .commit();
        } catch (Exception e) {
        }
        initPaints(getContext());
        invalidate();
    }

    public void setSelectedLineWidthByUser(float widthPx) {
        if (widthPx <= 0f) {
            widthPx = defSelectedLineWidthPx;
        }
        this.selectedLineWidthPx = widthPx;
        try {
            SharedPreferences sp = getContext().getSharedPreferences(
                    getContext().getString(R.string.prefs_chart),
                    Context.MODE_PRIVATE
            );
            sp.edit()
                  .putFloat(getContext().getString(R.string.key_selected_line_width), widthPx)
                  .putBoolean(KEY_SELECTED_WIDTH_USER_SET, true)
                  .commit();
        } catch (Exception e) {
        }
        initPaints(getContext());
        invalidate();
    }

    public void setSelectedLineAlpha(int alpha) {
        if (alpha < 0) {
            alpha = 0;
        }
        if (alpha > 255) {
            alpha = 255;
        }
        this.selectedLineAlpha = alpha;
        try {
            SharedPreferences sp = getContext().getSharedPreferences(
                    getContext().getString(R.string.prefs_chart),
                    Context.MODE_PRIVATE
            );
            sp.edit()
                  .putInt(getContext().getString(R.string.key_selected_line_alpha), alpha)
                  .commit();
        } catch (Exception e) {
        }
        initPaints(getContext());
        invalidate();
    }

    public void setSelectedLineAlphaByUser(int alpha) {
        if (alpha < 0) {
            alpha = 0;
        }
        if (alpha > 255) {
            alpha = 255;
        }
        this.selectedLineAlpha = alpha;
        try {
            SharedPreferences sp = getContext().getSharedPreferences(
                    getContext().getString(R.string.prefs_chart),
                    Context.MODE_PRIVATE
            );
            sp.edit()
                  .putInt(getContext().getString(R.string.key_selected_line_alpha), alpha)
                  .putBoolean(KEY_SELECTED_ALPHA_USER_SET, true)
                  .commit();
        } catch (Exception e) {
        }
        initPaints(getContext());
        invalidate();
    }

    public void setSelectedLineDashed(boolean dashed) {
        this.selectedLineDashed = dashed;
        try {
            SharedPreferences sp = getContext().getSharedPreferences(
                    getContext().getString(R.string.prefs_chart),
                    Context.MODE_PRIVATE
            );
            sp.edit()
                  .putBoolean(getContext().getString(R.string.key_selected_line_dash), dashed)
                  .commit();
        } catch (Exception e) {
        }
        initPaints(getContext());
        invalidate();
    }

    public void setSelectedLineDashedByUser(boolean dashed) {
        this.selectedLineDashed = dashed;
        try {
            SharedPreferences sp = getContext().getSharedPreferences(
                    getContext().getString(R.string.prefs_chart),
                    Context.MODE_PRIVATE
            );
            sp.edit()
                  .putBoolean(getContext().getString(R.string.key_selected_line_dash), dashed)
                  .putBoolean(KEY_SELECTED_DASHED_USER_SET, true)
                  .commit();
        } catch (Exception e) {
        }
        initPaints(getContext());
        invalidate();
    }

    // Called when user did NOT touch picker - DO NOT save that part, keep auto
    public void setSelectedLineAppearance(int color, float widthPx, int alpha, boolean dashed) {
        if (widthPx <= 0f) {
            widthPx = defSelectedLineWidthPx;
        }
        if (alpha < 0) {
            alpha = 0;
        }
        if (alpha > 255) {
            alpha = 255;
        }
        setSelectedLineColor(color);
        setSelectedLineWidthPx(widthPx);
        setSelectedLineAlpha(alpha);
        setSelectedLineDashed(dashed);
    }

    // Called only when user touched picker - save only touched parts, called from Activity
    public void setSelectedLineAppearanceByUser(int color, float widthPx, int alpha, boolean dashed,
                                                boolean colorTouched, boolean widthTouched,
                                                boolean alphaTouched, boolean dashedTouched) {
        if (colorTouched) {
            setSelectedLineColorByUser(color);
        }
        if (widthTouched) {
            if (widthPx <= 0f) {
                widthPx = defSelectedLineWidthPx;
            }
            setSelectedLineWidthByUser(widthPx);
        }
        if (alphaTouched) {
            if (alpha < 0) {
                alpha = 0;
            }
            if (alpha > 255) {
                alpha = 255;
            }
            setSelectedLineAlphaByUser(alpha);
        }
        if (dashedTouched) {
            setSelectedLineDashedByUser(dashed);
        }
    }

    public void setVolMaPeriods(int period1, int period2) {
        if (period1 <= 0) {
            period1 = volMa1Period> 0? volMa1Period : 5;
        }
        if (period2 <= 0) {
            period2 = volMa2Period> 0? volMa2Period : 10;
        }
        this.volMa1Period = period1;
        this.volMa2Period = period2;
        try {
            SharedPreferences sp = getContext().getSharedPreferences(
                    getContext().getString(R.string.prefs_chart),
                    Context.MODE_PRIVATE
            );
            sp.edit()
                  .putInt(getContext().getString(R.string.key_vol_ma1_period), period1)
                  .putInt(getContext().getString(R.string.key_vol_ma2_period), period2)
                  .commit();
        } catch (Exception e) {
        }
        calculateVolumeMas();
        invalidate();
    }

    public void setShowVolMa(boolean show) {
        this.showVolMa = show;
        try {
            SharedPreferences sp = getContext().getSharedPreferences(
                    getContext().getString(R.string.prefs_chart),
                    Context.MODE_PRIVATE
            );
            sp.edit()
                  .putBoolean(getContext().getString(R.string.key_vol_show_ma), show)
                  .commit();
        } catch (Exception e) {
        }
        invalidate();
    }

    public void setVolMa1Color(int color) {
        if (color == 0) {
            return;
        }
        this.volMa1Color = color;
        try {
            SharedPreferences sp = getContext().getSharedPreferences(
                    getContext().getString(R.string.prefs_chart),
                    Context.MODE_PRIVATE
            );
            sp.edit()
                  .putInt(getContext().getString(R.string.key_vol_ma1_color), color)
                  .commit();
        } catch (Exception e) {
        }
        initPaints(getContext());
        invalidate();
    }

    public void setVolMa2Color(int color) {
        if (color == 0) {
            return;
        }
        this.volMa2Color = color;
        try {
            SharedPreferences sp = getContext().getSharedPreferences(
                    getContext().getString(R.string.prefs_chart),
                    Context.MODE_PRIVATE
            );
            sp.edit()
                  .putInt(getContext().getString(R.string.key_vol_ma2_color), color)
                  .commit();
        } catch (Exception e) {
        }
        initPaints(getContext());
        invalidate();
    }

    public void setVolMaWidthPx(float widthPx) {
        if (widthPx <= 0f) {
            widthPx = volMaWidthPx> 0f? volMaWidthPx : defMaWidthPx;
        }
        this.volMaWidthPx = widthPx;
        try {
            SharedPreferences sp = getContext().getSharedPreferences(
                    getContext().getString(R.string.prefs_chart),
                    Context.MODE_PRIVATE
            );
            sp.edit()
                  .putFloat(getContext().getString(R.string.key_vol_ma_width), widthPx)
                  .commit();
        } catch (Exception e) {
        }
        initPaints(getContext());
        invalidate();
    }

    public void setVolMaAppearance(boolean show, int color1, int color2, float widthPx, int period1, int period2) {
        setShowVolMa(show);
        if (color1!= 0) {
            setVolMa1Color(color1);
        }
        if (color2!= 0) {
            setVolMa2Color(color2);
        }
        setVolMaWidthPx(widthPx);
        setVolMaPeriods(period1, period2);
    }

    public void setCandleColors(int bull, int bear) {
        this.bullishColor = bull;
        this.bearishColor = bear;
        try {
            SharedPreferences sp = getContext().getSharedPreferences(
                    getContext().getString(R.string.prefs_candle),
                    Context.MODE_PRIVATE
            );
            sp.edit()
                  .putInt(getContext().getString(R.string.key_bull), bull)
                  .putInt(getContext().getString(R.string.key_bear), bear)
                  .commit();
        } catch (Exception e) {
        }
        initPaints(getContext());
        invalidate();
    }

    public void setChartOptions(float bodyFraction, float wickWidth, float maWidth,
                                boolean sGrid, boolean sVolume, int visCount) {
        setBodyFraction(bodyFraction);
        setWickWidthPx(wickWidth);
        setMaLineWidthPx(maWidth);
        setShowGrid(sGrid);
        setShowVolume(sVolume);
        setVisibleCandleCount(visCount);
    }

    public void setChartAppearance(boolean sLastPrice, int lastLineColor, int lastBgColor,
                                   float txtSize, int txtColor, int gColor, int bColor,
                                   float lastW, boolean lastDash) {
        setShowLastPriceLine(sLastPrice);
        if (lastLineColor!= 0) {
            setLastPriceLineColor(lastLineColor);
        }
        if (lastBgColor!= 0) {
            setCurrentPriceLabelBackground(lastBgColor);
        }
        if (txtSize > 0) {
            setPriceTextSizePx(txtSize);
        }
        if (lastW > 0) {
            setLastLineWidthPx(lastW);
        }
        setLastLineDashed(lastDash);
        if (gColor!= 0) {
            setGridColor(gColor);
        }
        if (txtColor!= 0) {
            setPriceTextColor(txtColor);
        }
        if (bColor!= 0) {
            setBgColor(bColor);
        } else {
            try {
                SharedPreferences sp = getContext().getSharedPreferences(
                        getContext().getString(R.string.prefs_chart),
                        Context.MODE_PRIVATE
                );
                sp.edit()
                      .remove(getContext().getString(R.string.key_bg_color))
                      .commit();
                bgColor = 0;
                initPaints(getContext());
                invalidate();
            } catch (Exception e) {
            }
        }
    }

    public void setChartAppearance(boolean sLastPrice, int lastLineColor, int lastBgColor,
                                   float txtSize, int txtColor, int gColor) {
        setShowLastPriceLine(sLastPrice);
        if (lastLineColor!= 0) {
            setLastPriceLineColor(lastLineColor);
        }
        if (lastBgColor!= 0) {
            setCurrentPriceLabelBackground(lastBgColor);
        }
        if (txtSize > 0) {
            setPriceTextSizePx(txtSize);
        }
        if (gColor!= 0) {
            setGridColor(gColor);
        }
        if (txtColor!= 0) {
            setPriceTextColor(txtColor);
        }
    }

    private void clampVisibleCount() {
        if (visibleCandleCount < MIN_VISIBLE_CANDLE_COUNT) {
            visibleCandleCount = MIN_VISIBLE_CANDLE_COUNT;
        }
        if (visibleCandleCount > MAX_VISIBLE_CANDLE_COUNT) {
            visibleCandleCount = MAX_VISIBLE_CANDLE_COUNT;
        }
    }

    public void clearSavedSettings() {
        try {
            getContext().getSharedPreferences(
                    getContext().getString(R.string.prefs_chart),
                    Context.MODE_PRIVATE
            ).edit().clear().commit();

            getContext().getSharedPreferences(
                    getContext().getString(R.string.prefs_candle),
                    Context.MODE_PRIVATE
            ).edit().clear().commit();

            getContext().getSharedPreferences(
                    getContext().getString(R.string.prefs_ma),
                    Context.MODE_PRIVATE
            ).edit().clear().commit();

            getContext().getSharedPreferences(
                    "chart_settings",
                    Context.MODE_PRIVATE
            ).edit().clear().commit();

            getContext().getSharedPreferences(
                    "chart_state_prefs",
                    Context.MODE_PRIVATE
            ).edit().clear().commit();
        } catch (Exception e) {
        }
    }

    public void resetToDefaultsFromLayout() {
        reloadDefaultColorsFromCurrentTheme();
        clearSavedSettings();
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
        bgColor = 0;
        lastLineWidthPx = defLastLineWidthPx;
        lastLineDashed = defLastDashed;
        lastPriceLabelTextSizePx = defLabelTextSizePx;
        lastPriceLabelTextColor = defLabelTextColor;
        selectedLineColor = defSelectedLineColor;
        selectedLineWidthPx = defSelectedLineWidthPx;
        selectedLineAlpha = defSelectedAlpha;
        selectedLineDashed = defSelectedDashed;
        selectedIndex = -1;
        translationX = 0f;
        extraOffsetX = 0f;
        // Use def values from layout, not direct R.dimen / R.integer
        volMa1Period = defMaLines.size()>= 1? 5 : 5;
        volMa2Period = defMaLines.size()>= 2? 10 : 10;
        volMaWidthPx = defMaWidthPx;
        showVolMa = true;
        maLines.clear();
        for (MaLine m : defMaLines) {
            maLines.add(
                    new MaLine(m.period, m.color)
            );
        }
        if (defMaLines.size() >= 2) {
            volMa1Color = defMaLines.get(0).color;
            volMa2Color = defMaLines.get(1).color;
        }
        initPaints(getContext());
        clampVisibleCount();
        clampTranslationX();
        invalidate();
        notifyMa();
        if (updateListener!= null) {
            updateListener.onNothingSelected();
        }
    }

    public void resetToDefaults() {
        resetToDefaultsFromLayout();
    }

    private void saveMaLines(Context context) {
        try {
            SharedPreferences sp = context.getSharedPreferences(
                    context.getString(R.string.prefs_ma),
                    Context.MODE_PRIVATE
            );
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < maLines.size(); i++) {
                MaLine m = maLines.get(i);
                if (i > 0) {
                    sb.append(context.getString(R.string.sep_semicolon));
                }
                sb.append(m.period)
                      .append(context.getString(R.string.sep_comma))
                      .append(m.color);
            }
            sp.edit()
                  .putString(context.getString(R.string.key_ma), sb.toString())
                  .commit();
        } catch (Exception e) {
        }
    }

    private boolean loadMaLinesFromPrefs(Context context) {
        try {
            SharedPreferences sp = context.getSharedPreferences(
                    context.getString(R.string.prefs_ma),
                    Context.MODE_PRIVATE
            );
            String s = sp.getString(context.getString(R.string.key_ma), null);
            if (s == null || s.isEmpty()) {
                return false;
            }
            String[] parts = s.split(
                    context.getString(R.string.sep_semicolon_regex)
            );
            List<MaLine> list = new ArrayList<>();
            for (String p : parts) {
                String[] kv = p.split(
                        context.getString(R.string.sep_comma_regex)
                );
                if (kv.length!= 2) {
                    continue;
                }
                int period = Integer.parseInt(kv[0]);
                int color = Integer.parseInt(kv[1]);
                list.add(new MaLine(period, color));
            }
            if (!list.isEmpty()) {
                maLines = list;
                return true;
            }
        } catch (Exception e) {
        }
        return false;
    }

    private void initMaLines(Context context) {
        if (loadMaLinesFromPrefs(context)) {
            return;
        }
        maLines.clear();
        for (MaLine m : defMaLines) {
            maLines.add(
                    new MaLine(m.period, m.color)
            );
        }
    }

    public List<MaLine> getMaLines() {
        return new ArrayList<>(maLines);
    }

    public void setMaLines(List<MaLine> list) {
        if (list == null) {
            return;
        }
        this.maLines = new ArrayList<>(list);
        saveMaLines(getContext());
        initPaints(getContext());
        invalidate();
        notifyMa();
    }

    private int getThemeColor(int attr) {
        TypedValue tv = new TypedValue();
        getContext().getTheme().resolveAttribute(attr, tv, true);
        if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT &&
                tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return tv.data;
        } else {
            try {
                return getResources().getColor(tv.resourceId, getContext().getTheme());
            } catch (Exception e) {
                return tv.data;
            }
        }
    }

    private void initPaints(Context context) {
        if (!defaultsLoadedFromLayout && bullishPaint == null) {
            return;
        }
        if (!defaultsLoadedFromLayout) {
            return;
        }

        int themeBg;
        try {
            themeBg = getThemeColor(android.R.attr.colorBackground);
        } catch (Exception ignored) {
            return;
        }

        if (bgColor!= 0) {
            setBackgroundColor(bgColor);
        }

        if (bullishColor == 0) {
            bullishColor = defBullColor;
        }
        if (bearishColor == 0) {
            bearishColor = defBearColor;
        }
        if (gridColor == 0) {
            gridColor = defGridColor;
        }
        if (priceTextColor == 0) {
            priceTextColor = defPriceTextColor;
        }
        if (lastPriceLineColor == 0) {
            lastPriceLineColor = defLastPriceLineColor;
        }
        if (lastPriceBgColor == 0) {
            lastPriceBgColor = defLastPriceBgColor;
        }
        if (lastPriceLabelTextColor == 0) {
            lastPriceLabelTextColor = defLabelTextColor;
        }
        if (selectedLineColor == 0) {
            selectedLineColor = defSelectedLineColor;
        }
        if (volMa1Color == 0 && defMaLines.size() > 0) {
            volMa1Color = defMaLines.get(0).color;
        }
        if (volMa2Color == 0 && defMaLines.size() > 1) {
            volMa2Color = defMaLines.get(1).color;
        }

        bullishPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bullishPaint.setColor(bullishColor);
        bullishPaint.setStyle(Paint.Style.FILL);

        bearishPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bearishPaint.setColor(bearishColor);
        bearishPaint.setStyle(Paint.Style.FILL);

        volumeBullishPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        volumeBullishPaint.setColor(bullishColor);
        volumeBullishPaint.setAlpha(VOLUME_ALPHA);
        volumeBullishPaint.setStyle(Paint.Style.FILL);

        volumeBearishPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        volumeBearishPaint.setColor(bearishColor);
        volumeBearishPaint.setAlpha(VOLUME_ALPHA);
        volumeBearishPaint.setStyle(Paint.Style.FILL);

        wickBullishPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wickBullishPaint.setColor(bullishColor);
        wickBullishPaint.setStrokeWidth(wickWidthPx);

        wickBearishPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wickBearishPaint.setColor(bearishColor);
        wickBearishPaint.setStrokeWidth(wickWidthPx);

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(gridColor);
        gridPaint.setStrokeWidth(GRID_WIDTH);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(priceTextColor);
        textPaint.setTextSize(priceTextSizePx);

        lastPriceLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        lastPriceLinePaint.setColor(lastPriceLineColor);
        lastPriceLinePaint.setStrokeWidth(lastLineWidthPx);
        lastPriceLinePaint.setStyle(Paint.Style.STROKE);
        if (lastLineDashed) {
            lastPriceLinePaint.setPathEffect(
                    new DashPathEffect(new float[]{DASH_ON, DASH_OFF}, 0f)
            );
        } else {
            lastPriceLinePaint.setPathEffect(null);
        }

        lastPriceBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        lastPriceBgPaint.setColor(lastPriceBgColor);
        lastPriceBgPaint.setStyle(Paint.Style.FILL);

        lastPriceTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        lastPriceTextPaint.setColor(lastPriceLabelTextColor);
        lastPriceTextPaint.setTextSize(lastPriceLabelTextSizePx);
        lastPriceTextPaint.setFakeBoldText(true);

        movingAverage5Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        movingAverage5Paint.setStyle(Paint.Style.STROKE);
        movingAverage5Paint.setStrokeWidth(maLineWidthPx);
        movingAverage5Paint.setStrokeCap(Paint.Cap.ROUND);
        movingAverage5Paint.setStrokeJoin(Paint.Join.ROUND);

        movingAverage10Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        movingAverage10Paint.setStyle(Paint.Style.STROKE);
        movingAverage10Paint.setStrokeWidth(maLineWidthPx);
        movingAverage10Paint.setStrokeCap(Paint.Cap.ROUND);
        movingAverage10Paint.setStrokeJoin(Paint.Join.ROUND);

        movingAverage20Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        movingAverage20Paint.setStyle(Paint.Style.STROKE);
        movingAverage20Paint.setStrokeWidth(maLineWidthPx);
        movingAverage20Paint.setStrokeCap(Paint.Cap.ROUND);
        movingAverage20Paint.setStrokeJoin(Paint.Join.ROUND);

        volMa5Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        volMa5Paint.setStyle(Paint.Style.STROKE);
        volMa5Paint.setStrokeWidth(volMaWidthPx);
        volMa5Paint.setStrokeCap(Paint.Cap.ROUND);
        volMa5Paint.setStrokeJoin(Paint.Join.ROUND);
        volMa5Paint.setColor(volMa1Color);

        volMa10Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        volMa10Paint.setStyle(Paint.Style.STROKE);
        volMa10Paint.setStrokeWidth(volMaWidthPx);
        volMa10Paint.setStrokeCap(Paint.Cap.ROUND);
        volMa10Paint.setStrokeJoin(Paint.Join.ROUND);
        volMa10Paint.setColor(volMa2Color);

        volHeaderTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        volHeaderTextPaint.setColor(priceTextColor);
        volHeaderTextPaint.setTextSize(priceTextSizePx * 0.85f);

        volHeaderMa5Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        volHeaderMa5Paint.setColor(volMa1Color);
        volHeaderMa5Paint.setTextSize(priceTextSizePx * 0.85f);

        volHeaderMa10Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        volHeaderMa10Paint.setColor(volMa2Color);
        volHeaderMa10Paint.setTextSize(priceTextSizePx * 0.85f);

        maExtraPaints.clear();
        if (maLines.isEmpty()) {
            initMaLines(context);
        }
        for (int i = 0; i < maLines.size(); i++) {
            Paint p;
            if (i == 0) {
                p = movingAverage5Paint;
            } else if (i == 1) {
                p = movingAverage10Paint;
            } else if (i == 2) {
                p = movingAverage20Paint;
            } else {
                p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(maLineWidthPx);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeJoin(Paint.Join.ROUND);
                maExtraPaints.add(p);
            }
            if (maLines.get(i).color == 0 && defMaLines.size() > 0) {
                maLines.get(i).color = defMaLines.get(
                        i % defMaLines.size()
                ).color;
            }
            p.setColor(maLines.get(i).color);
        }

        try {
            SharedPreferences sp = context.getSharedPreferences(
                    context.getString(R.string.prefs_chart),
                    Context.MODE_PRIVATE
            );
            if (!sp.contains(context.getString(R.string.key_vol_ma1_color)) && maLines.size() > 0) {
                volMa1Color = maLines.get(0).color;
            }
            if (!sp.contains(context.getString(R.string.key_vol_ma2_color)) && maLines.size() > 1) {
                volMa2Color = maLines.get(1).color;
            }
        } catch (Exception e) {
        }
        volMa5Paint.setColor(volMa1Color);
        volMa10Paint.setColor(volMa2Color);
        volHeaderMa5Paint.setColor(volMa1Color);
        volHeaderMa10Paint.setColor(volMa2Color);

        selectedLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        selectedLinePaint.setColor(selectedLineColor);
        selectedLinePaint.setStrokeWidth(selectedLineWidthPx);
        selectedLinePaint.setAlpha(selectedLineAlpha);
        selectedLinePaint.setStyle(Paint.Style.STROKE);
        if (selectedLineDashed) {
            selectedLinePaint.setPathEffect(
                    new DashPathEffect(new float[]{DASH_ON, DASH_OFF}, 0f)
            );
        } else {
            selectedLinePaint.setPathEffect(null);
        }
    }

    @Override
    protected void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (defaultsLoadedFromLayout) {
            reloadDefaultColorsFromCurrentTheme();
            cleanupAutoIfNotUserSet();
            loadChartOptions(getContext());
            initCandleColors(getContext());
        }
        initPaints(getContext());
        invalidate();
    }

    public void refreshTheme() {
        if (defaultsLoadedFromLayout) {
            reloadDefaultColorsFromCurrentTheme();
            cleanupAutoIfNotUserSet();
            loadChartOptions(getContext());
            initCandleColors(getContext());
        }
        initPaints(getContext());
        invalidate();
    }

    private void initGestures(Context context) {
        scaleGestureDetector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        visibleCandleCount = (int) (
                                visibleCandleCount / detector.getScaleFactor()
                        );
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
                        if (data.isEmpty()) {
                            return false;
                        }
                        int priceAxisW = PRICE_AXIS_WIDTH_DP;
                        int chartW = getWidth() - priceAxisW;
                        if (chartW <= 0) {
                            return false;
                        }
                        translationX -= distanceX;
                        clampTranslationX();
                        if (selectedIndex!= -1) {
                            selectedIndex = -1;
                            if (updateListener!= null) {
                                updateListener.onNothingSelected();
                            }
                        }
                        invalidate();
                        return true;
                    }

                    @Override
                    public boolean onSingleTapUp(MotionEvent e) {
                        if (data.isEmpty()) {
                            return false;
                        }
                        int priceAxisW = PRICE_AXIS_WIDTH_DP;
                        int chartW = getWidth() - priceAxisW;
                        int count = Math.min(visibleCandleCount, data.size());
                        if (count == 0) {
                            return false;
                        }
                        if (e.getX() > chartW) {
                            if (selectedIndex!= -1) {
                                selectedIndex = -1;
                                if (updateListener!= null) {
                                    updateListener.onNothingSelected();
                                }
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
                            if (updateListener!= null) {
                                updateListener.onNothingSelected();
                            }
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
        int priceAxisW = PRICE_AXIS_WIDTH_DP;
        int chartW = getWidth() - priceAxisW;
        if (chartW <= 0) {
            return;
        }
        int count = Math.min(visibleCandleCount, data.size());
        float candleWidth = chartW / (float) count;
        float maxScroll = (data.size() - count) * candleWidth;
        float minScroll = -chartW * MIN_SCROLL_FRACTION;

        if (translationX < minScroll) {
            translationX = minScroll;
        }
        if (translationX > maxScroll) {
            translationX = maxScroll;
        }

        if (translationX < 0f) {
            extraOffsetX = translationX;
        } else {
            extraOffsetX = 0f;
        }
    }

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
        if (mult <= 0f) {
            return;
        }
        this.fiatMultiplier = mult;
        invalidate();
    }

    public void setCountdown(String text) {
        invalidate();
    }

    private long getIntervalMillis(String interval) {
        if (interval == null) {
            return 15L * 60_000L;
        }
        switch (interval) {
            case "1m":
                return 60_000L;
            case "3m":
                return 3L * 60_000L;
            case "5m":
                return 5L * 60_000L;
            case "15m":
                return 15L * 60_000L;
            case "30m":
                return 30L * 60_000L;
            case "1h":
                return 60L * 60_000L;
            case "2h":
                return 2L * 60L * 60_000L;
            case "4h":
                return 4L * 60L * 60_000L;
            case "6h":
                return 6L * 60L * 60_000L;
            case "12h":
                return 12L * 60L * 60_000L;
            case "1d":
                return 24L * 60L * 60_000L;
            case "3d":
                return 3L * 24L * 60L * 60_000L;
            case "1w":
                return 7L * 24L * 60L * 60_000L;
            case "1M":
                return 30L * 24L * 60L * 60_000L;
            default:
                return 15L * 60_000L;
        }
    }

    private void startCountdown() {
        if (countdownRunnable!= null) {
            countdownHandler.removeCallbacks(countdownRunnable);
        }
        countdownRunnable = new Runnable() {
            @Override
            public void run() {
                if (currentCandleCloseTime > 0L) {
                    long now = System.currentTimeMillis();
                    long remain = currentCandleCloseTime - now;
                    if (remain < 0L) {
                        remain = 0L;
                    }
                    long seconds = (remain / 1000L) % 60L;
                    long minutes = (remain / 1000L / 60L) % 60L;
                    long hours = remain / 1000L / 60L / 60L;
                    String text;
                    if (getIntervalMillis(currentInterval) >= 24L * 60L * 60_000L) {
                        text = String.format(
                                Locale.US,
                                getContext().getString(R.string.fmt_dhms),
                                hours / 24L,
                                hours % 24L,
                                minutes,
                                seconds
                        );
                    } else {
                        text = String.format(
                                Locale.US,
                                getContext().getString(R.string.fmt_hms),
                                hours,
                                minutes,
                                seconds
                        );
                    }
                    if (updateListener!= null) {
                        updateListener.onCountdownUpdate(text);
                    }
                }
                countdownHandler.postDelayed(this, COUNTDOWN_INTERVAL_MS);
            }
        };
        countdownHandler.post(countdownRunnable);
    }

    private void startLive() {
        if (liveRunnable!= null) {
            liveHandler.removeCallbacks(liveRunnable);
        }
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
        if (liveRunnable!= null) {
            liveHandler.removeCallbacks(liveRunnable);
        }
        if (countdownRunnable!= null) {
            countdownHandler.removeCallbacks(countdownRunnable);
        }
    }

    private void notifyMa() {
        if (data.isEmpty() || updateListener == null) {
            return;
        }
        int last = data.size() - 1;
        List<Float> values = new ArrayList<>();
        for (int i = 0; i < maLines.size(); i++) {
            values.add(
                    calculateMovingAverage(last, maLines.get(i).period)
            );
        }
        updateListener.onMaUpdate(values);
    }

    private void fetchCandles() {
        if (currentSymbol == null || currentInterval == null) {
            return;
        }
        new Thread(() -> {
            try {
                String urlString = getContext().getString(
                        R.string.url_klines,
                        currentSymbol,
                        currentInterval,
                        FETCH_LIMIT
                );
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(NETWORK_TIMEOUT);
                connection.setReadTimeout(NETWORK_TIMEOUT);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream())
                );
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine())!= null) {
                    builder.append(line);
                }
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
                    newData.add(
                            new Candle(open, high, low, close, volume, openTime, closeTime)
                    );
                }
                mainHandler.post(() -> {
                    data = newData;
                    calculateVolumeMas();
                    clampTranslationX();
                    if (!data.isEmpty()) {
                        minPrice = Float.MAX_VALUE;
                        maxPrice = Float.MIN_VALUE;
                        maxVolume = 0f;
                        for (Candle candle : data) {
                            if (candle.low < minPrice) {
                                minPrice = candle.low;
                            }
                            if (candle.high > maxPrice) {
                                maxPrice = candle.high;
                            }
                            if (candle.volume > maxVolume) {
                                maxVolume = candle.volume;
                            }
                        }
                        lastPrice = data.get(data.size() - 1).close;
                        currentCandleCloseTime = data.get(data.size() - 1).closeTime;
                        float padding = (maxPrice - minPrice) * PRICE_PADDING_FRACTION;
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
        if (currentSymbol == null) {
            return;
        }
        new Thread(() -> {
            try {
                String tickerUrl = getContext().getString(
                        R.string.url_ticker,
                        currentSymbol
                );
                HttpURLConnection connection = (HttpURLConnection)
                        new URL(tickerUrl).openConnection();
                connection.setConnectTimeout(NETWORK_TIMEOUT);
                connection.setReadTimeout(NETWORK_TIMEOUT);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream())
                );
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine())!= null) {
                    builder.append(line);
                }
                reader.close();
                JSONObject jsonObject = new JSONObject(builder.toString());
                float price = (float) jsonObject.getDouble(
                        getContext().getString(R.string.json_lastPrice)
                );
                float high = (float) jsonObject.getDouble(
                        getContext().getString(R.string.json_highPrice)
                );
                float low = (float) jsonObject.getDouble(
                        getContext().getString(R.string.json_lowPrice)
                );
                float volBtc = (float) jsonObject.getDouble(
                        getContext().getString(R.string.json_volume)
                );
                float volUsdt = (float) jsonObject.getDouble(
                        getContext().getString(R.string.json_quoteVolume)
                );
                float changePercent = (float) jsonObject.getDouble(
                        getContext().getString(R.string.json_priceChangePercent)
                );
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
                        calculateVolumeMas();
                        if (System.currentTimeMillis() >= updatedCandle.closeTime) {
                            fetchCandles();
                        } else {
                            invalidate();
                        }
                        if (updateListener!= null) {
                            updateListener.onPriceUpdate(price, high, low);
                            updateListener.onTickerUpdate(
                                    high, low, volBtc, volUsdt, changePercent
                            );
                        }
                        notifyMa();
                    }
                });
            } catch (Exception e) {
            }
        }).start();
    }

    private float calculateMovingAverage(int currentIndex, int period) {
        if (currentIndex < period - 1 || data.isEmpty()) {
            return 0f;
        }
        float sum = 0f;
        for (int i = 0; i < period; i++) {
            sum += data.get(currentIndex - i).close;
        }
        return sum / period;
    }

    private float calculateVolumeMaAt(int currentIndex, int period) {
        if (currentIndex < period - 1 || data.isEmpty()) {
            return 0f;
        }
        float sum = 0f;
        for (int i = 0; i < period; i++) {
            sum += data.get(currentIndex - i).volume;
        }
        return sum / period;
    }

    private void calculateVolumeMas() {
        volMa1Values.clear();
        volMa2Values.clear();
        if (data.isEmpty()) {
            return;
        }
        for (int i = 0; i < data.size(); i++) {
            volMa1Values.add(calculateVolumeMaAt(i, volMa1Period));
            volMa2Values.add(calculateVolumeMaAt(i, volMa2Period));
        }
        int lastIdx = data.size() - 1;
        lastVolValue = data.get(lastIdx).volume;
        lastVolMa1Value = volMa1Values.size() > lastIdx? volMa1Values.get(lastIdx) : 0f;
        lastVolMa2Value = volMa2Values.size() > lastIdx? volMa2Values.get(lastIdx) : 0f;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleGestureDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            int priceAxisW = PRICE_AXIS_WIDTH_DP;
            int chartW = getWidth() - priceAxisW;
            if (event.getX() > chartW) {
                if (selectedIndex!= -1) {
                    selectedIndex = -1;
                    if (updateListener!= null) {
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
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int priceAxisWidth = PRICE_AXIS_WIDTH_DP;
        int timeAxisHeight = TIME_AXIS_HEIGHT;
        int volumeHeightPx = VOLUME_CHART_HEIGHT_DP;

        int fullWidth = getWidth();
        int fullHeight = getHeight();
        int chartWidth = fullWidth - priceAxisWidth;

        int priceChartHeight = fullHeight - TOP_PADDING_PX - BOTTOM_PADDING_PX
                - VOLUME_TOP_MARGIN_PX - volumeHeightPx - timeAxisHeight;

        if (priceChartHeight <= 0) {
            return;
        }

        drawGrid(canvas, chartWidth, priceChartHeight, volumeHeightPx);

        if (data.isEmpty()) {
            String loadingText = getResources().getString(R.string.chart_loading);
            canvas.drawText(
                    loadingText,
                    chartWidth / 2f - LOADING_TEXT_OFFSET,
                    fullHeight / 2f,
                    textPaint
            );
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
        info.timeAxisHeight = timeAxisHeight;
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
        drawPriceAxis(canvas, info);
        drawLastPriceLine(canvas, info);
        drawVolumeAndTime(canvas, info);
    }

    private static class DrawInfo {
        int chartWidth;
        int fullWidth;
        int priceChartHeight;
        int volumeHeightPx;
        int timeAxisHeight;
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
        if (startIndex < 0) {
            startIndex = 0;
        }
        if (startIndex + count > data.size()) {
            startIndex = data.size() - count;
        }
        if (startIndex < 0) {
            startIndex = 0;
        }
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
        float bodyWidth = info.candleWidth * bodyWidthFraction;
        float minBody = BODY_MIN_WIDTH;
        float maxBody = BODY_MAX_WIDTH;
        if (bodyWidth < minBody) {
            bodyWidth = minBody;
        }
        if (bodyWidth > maxBody) {
            bodyWidth = maxBody;
        }

        float priceRange = info.displayMax - info.displayMin;
        if (priceRange == 0f) {
            priceRange = 1f;
        }

        for (int i = 0; i < info.count; i++) {
            int dataIndex = info.startIndex + i;
            if (dataIndex >= data.size()) {
                break;
            }
            Candle candle = data.get(dataIndex);
            float x = i * info.candleWidth + info.candleWidth / 2f + extraOffsetX;

            float highY = TOP_PADDING_PX + info.priceChartHeight
                    - ((candle.high * fiatMultiplier - info.displayMin)
                    / priceRange * info.priceChartHeight);
            float lowY = TOP_PADDING_PX + info.priceChartHeight
                    - ((candle.low * fiatMultiplier - info.displayMin)
                    / priceRange * info.priceChartHeight);
            float openY = TOP_PADDING_PX + info.priceChartHeight
                    - ((candle.open * fiatMultiplier - info.displayMin)
                    / priceRange * info.priceChartHeight);
            float closeY = TOP_PADDING_PX + info.priceChartHeight
                    - ((candle.close * fiatMultiplier - info.displayMin)
                    / priceRange * info.priceChartHeight);

            boolean isBullish = candle.close >= candle.open;
            Paint currentWickPaint = isBullish? wickBullishPaint : wickBearishPaint;
            Paint bodyPaint = isBullish? bullishPaint : bearishPaint;

            canvas.drawLine(x, highY, x, lowY, currentWickPaint);

            float top = Math.min(openY, closeY);
            float bottom = Math.max(openY, closeY);
            float minH = CANDLE_MIN_HEIGHT;
            if (Math.abs(bottom - top) < minH) {
                bottom = top + minH;
            }
            canvas.drawRect(
                    x - bodyWidth / 2f,
                    top,
                    x + bodyWidth / 2f,
                    bottom,
                    bodyPaint
            );
        }
    }

    private void drawMovingAverages(Canvas canvas, DrawInfo info) {
        float priceRange = info.displayMax - info.displayMin;
        if (priceRange == 0f) {
            priceRange = 1f;
        }

        for (int maIndex = 0; maIndex < maLines.size(); maIndex++) {
            MaLine maLine = maLines.get(maIndex);
            int period = maLine.period;
            Paint paint;
            if (maIndex == 0) {
                paint = movingAverage5Paint;
            } else if (maIndex == 1) {
                paint = movingAverage10Paint;
            } else if (maIndex == 2) {
                paint = movingAverage20Paint;
            } else {
                int extraIdx = maIndex - 3;
                paint = (extraIdx < maExtraPaints.size())?
                        maExtraPaints.get(extraIdx) : movingAverage20Paint;
            }
            paint.setColor(maLine.color);

            float previousX = 0f;
            float previousY = 0f;
            boolean isFirstPoint = true;
            for (int i = 0; i < info.count; i++) {
                int dataIndex = info.startIndex + i;
                if (dataIndex >= data.size()) {
                    break;
                }
                float movingAverage = calculateMovingAverage(dataIndex, period);
                if (movingAverage == 0f) {
                    continue;
                }
                float x = i * info.candleWidth + info.candleWidth / 2f + extraOffsetX;
                float y = TOP_PADDING_PX + info.priceChartHeight
                        - ((movingAverage * fiatMultiplier - info.displayMin)
                        / priceRange * info.priceChartHeight);
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
        if (selectedIndex >= info.startIndex &&
                selectedIndex < info.startIndex + info.count) {
            float selectedX = (selectedIndex - info.startIndex) * info.candleWidth
                    + info.candleWidth / 2f + extraOffsetX;
            canvas.drawLine(
                    selectedX,
                    TOP_PADDING_PX,
                    selectedX,
                    TOP_PADDING_PX + info.priceChartHeight,
                    selectedLinePaint
            );
        }
    }

    private void drawLastPriceLine(Canvas canvas, DrawInfo info) {
        if (lastPrice <= 0f ||!showLastPriceLine) {
            return;
        }
        float priceRange = info.displayMax - info.displayMin;
        if (priceRange == 0f) {
            priceRange = 1f;
        }

        float lastPriceY = TOP_PADDING_PX + info.priceChartHeight
                - ((lastPrice * fiatMultiplier - info.displayMin)
                / priceRange * info.priceChartHeight);
        canvas.drawLine(
                0f,
                lastPriceY,
                info.chartWidth,
                lastPriceY,
                lastPriceLinePaint
        );

        boolean isBigFiat = fiatMultiplier > BIG_FIAT_THRESHOLD;
        String fmt = isBigFiat?
                getContext().getString(R.string.fmt_price_0) :
                getContext().getString(R.string.fmt_price_2);

        float labelH = PRICE_TEXT_OFFSET + TEXT_SIZE;
        float top = lastPriceY - labelH;
        float bottom = lastPriceY + labelH;
        canvas.drawRect(
                info.chartWidth,
                top,
                info.fullWidth,
                bottom,
                lastPriceBgPaint
        );

        String label = String.format(
                Locale.US,
                fmt,
                lastPrice * fiatMultiplier
        );
        float tx = info.chartWidth + PRICE_TEXT_MARGIN / 2f;
        float ty = lastPriceY + TEXT_SIZE / 3f;
        canvas.drawText(label, tx, ty, lastPriceTextPaint);
    }

    private void drawPriceAxis(Canvas canvas, DrawInfo info) {
        boolean isBigFiatAxis = fiatMultiplier > BIG_FIAT_THRESHOLD;
        String axisFmt = isBigFiatAxis?
                getContext().getString(R.string.fmt_price_0) :
                getContext().getString(R.string.fmt_price_2);
        for (int i = 0; i <= 4; i++) {
            float price = info.displayMax
                    - (info.displayMax - info.displayMin) * i / 4f;
            float y = TOP_PADDING_PX + info.priceChartHeight * i / 4f + PRICE_TEXT_OFFSET;
            String priceText = String.format(Locale.US, axisFmt, price);
            float x = info.chartWidth + PRICE_TEXT_MARGIN;
            canvas.drawText(priceText, x, y, textPaint);
        }
    }

    private void drawVolumeAndTime(Canvas canvas, DrawInfo info) {
        float volumeTop = TOP_PADDING_PX + info.priceChartHeight + VOLUME_TOP_MARGIN_PX;
        float volumeBottom = volumeTop + info.volumeHeightPx;
        float timeTop = volumeBottom;

        if (maxVolume == 0f) {
            maxVolume = 1f;
        }

        float bodyWidth = info.candleWidth * bodyWidthFraction;
        float minBody = BODY_MIN_WIDTH;
        float maxBody = BODY_MAX_WIDTH;
        if (bodyWidth < minBody) {
            bodyWidth = minBody;
        }
        if (bodyWidth > maxBody) {
            bodyWidth = maxBody;
        }

        if (showVolume) {
            float headerY = volumeTop - 6f;
            if (headerY < TOP_PADDING_PX + info.priceChartHeight + 2f) {
                headerY = volumeTop - 2f;
            }
            drawVolumeHeader(canvas, headerY);
        }

        if (showVolume) {
            for (int i = 0; i < info.count; i++) {
                int dataIndex = info.startIndex + i;
                if (dataIndex >= data.size()) {
                    break;
                }
                Candle candle = data.get(dataIndex);
                float x = i * info.candleWidth + info.candleWidth / 2f + extraOffsetX;
                float volumeBarHeight = info.volumeHeightPx
                        * (candle.volume / maxVolume);
                float barTop = volumeBottom - volumeBarHeight;
                if (barTop < volumeTop) {
                    barTop = volumeTop;
                }
                Paint volumePaint = (candle.close >= candle.open)?
                        volumeBullishPaint : volumeBearishPaint;
                canvas.drawRect(
                        x - bodyWidth / 2f,
                        barTop,
                        x + bodyWidth / 2f,
                        volumeBottom,
                        volumePaint
                );
            }

            if (showVolMa) {
                drawVolumeMaLines(canvas, info, volumeTop, volumeBottom);
            }
        }

        float timeBaseline = timeTop
                + (info.timeAxisHeight / 2f)
                + (TEXT_SIZE / 3f);
        for (int i = 0; i < info.count; i += Math.max(1, info.count / 4)) {
            int dataIndex = info.startIndex + i;
            if (dataIndex >= data.size()) {
                break;
            }
            float x = i * info.candleWidth + extraOffsetX;
            String timeText = timeFormat.format(
                    new Date(data.get(dataIndex).openTime)
            );
            canvas.drawText(timeText, x, timeBaseline, textPaint);
        }
    }

    private void drawVolumeHeader(Canvas canvas, float y) {
        if (data.isEmpty()) {
            return;
        }
        String volLabel = getContext().getString(R.string.vol_label);
        String maFormat = getContext().getString(R.string.vol_ma_format);

        String volStr = String.format(Locale.US, volLabel, lastVolValue);
        String ma5Str = String.format(Locale.US, maFormat, volMa1Period, lastVolMa1Value);
        String ma10Str = String.format(Locale.US, maFormat, volMa2Period, lastVolMa2Value);

        float x = 4f;
        canvas.drawText(volStr, x, y, volHeaderTextPaint);
        x += volHeaderTextPaint.measureText(volStr);

        if (showVolMa) {
            canvas.drawText(ma5Str, x, y, volHeaderMa5Paint);
            x += volHeaderMa5Paint.measureText(ma5Str);
            canvas.drawText(ma10Str, x, y, volHeaderMa10Paint);
        }
    }

    private void drawVolumeMaLines(Canvas canvas, DrawInfo info, float volumeTop, float volumeBottom) {
        if (volMa1Values.isEmpty() || volMa2Values.isEmpty()) {
            return;
        }
        if (maxVolume == 0f) {
            return;
        }

        float prevX1 = 0f;
        float prevY1 = 0f;
        float prevX2 = 0f;
        float prevY2 = 0f;
        boolean first1 = true;
        boolean first2 = true;

        for (int i = 0; i < info.count; i++) {
            int dataIndex = info.startIndex + i;
            if (dataIndex >= data.size()) {
                break;
            }
            if (dataIndex >= volMa1Values.size() || dataIndex >= volMa2Values.size()) {
                continue;
            }

            float x = i * info.candleWidth + info.candleWidth / 2f + extraOffsetX;

            float ma1 = volMa1Values.get(dataIndex);
            if (ma1 > 0f) {
                float y = volumeBottom - (ma1 / maxVolume) * info.volumeHeightPx;
                if (y < volumeTop) {
                    y = volumeTop;
                }
                if (!first1) {
                    canvas.drawLine(prevX1, prevY1, x, y, volMa5Paint);
                }
                prevX1 = x;
                prevY1 = y;
                first1 = false;
            }

            float ma2 = volMa2Values.get(dataIndex);
            if (ma2 > 0f) {
                float y = volumeBottom - (ma2 / maxVolume) * info.volumeHeightPx;
                if (y < volumeTop) {
                    y = volumeTop;
                }
                if (!first2) {
                    canvas.drawLine(prevX2, prevY2, x, y, volMa10Paint);
                }
                prevX2 = x;
                prevY2 = y;
                first2 = false;
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopLive();
    }
}
