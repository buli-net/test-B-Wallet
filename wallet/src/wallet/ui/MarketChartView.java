package wallet.ui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import org.json.JSONArray;

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

public class MarketChartView extends View {

    public static class Candle {
        public final float open;
        public final float high;
        public final float low;
        public final float close;
        public final float volume;
        public final long openTime;
        public Candle(float o,float h,float l,float c,float v,long t){open=o;high=h;low=l;close=c;volume=v;openTime=t;}
    }

    public interface OnCandleSelectedListener {
        void onCandleSelected(Candle candle);
        void onNothingSelected();
    }

    private OnCandleSelectedListener selectedListener;
    public void setOnCandleSelectedListener(OnCandleSelectedListener l){ this.selectedListener = l; }

    private List<Candle> data = new ArrayList<>();
    private Paint bullishPaint, bearishPaint, wickPaint, gridPaint, textPaint, lastPriceLinePaint;
    private Paint movingAverage5Paint, movingAverage10Paint, movingAverage20Paint;
    private Paint volumeBullishPaint, volumeBearishPaint, selectedLinePaint;

    private static final int DEFAULT_VISIBLE_CANDLE_COUNT = 80;
    private static final int MIN_VISIBLE_CANDLE_COUNT = 20;
    private static final int MAX_VISIBLE_CANDLE_COUNT = 150;
    private static final int TOP_PADDING_PX = 50;
    private static final int BOTTOM_PADDING_PX = 110;
    private static final int VOLUME_CHART_HEIGHT_PX = 180;
    private static final int VOLUME_TOP_MARGIN_PX = 30;
    private static final int FETCH_LIMIT = 200;
    private static final long LIVE_REFRESH_INTERVAL_MS = 10000L;

    private int visibleCandleCount = DEFAULT_VISIBLE_CANDLE_COUNT;
    private float translationX = 0f;
    private float minPrice = 0f, maxPrice = 0f, lastPrice = 0f, maxVolume = 0f;
    private int selectedIndex = -1;
    private int startIndexCache = 0;

    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetector gestureDetector;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Handler liveHandler = new Handler(Looper.getMainLooper());
    private Runnable liveRunnable;
    private String currentSymbol, currentInterval;
    private SimpleDateFormat timeFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.US);

    public MarketChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPaints(context);
        initGestures(context);
    }

    private void initPaints(Context context) {
        Resources res = context.getResources();
        // ĐỔI MÀU THEO THEME - LẤY TỪ R.color, KHÔNG FIX CỨNG
        setBackgroundColor(res.getColor(R.color.chart_bg));

        bullishPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bullishPaint.setColor(res.getColor(R.color.chart_bull));
        bullishPaint.setStyle(Paint.Style.FILL);

        bearishPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bearishPaint.setColor(res.getColor(R.color.chart_bear));
        bearishPaint.setStyle(Paint.Style.FILL);

        volumeBullishPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        volumeBullishPaint.setColor(res.getColor(R.color.chart_bull));
        volumeBullishPaint.setAlpha(150);

        volumeBearishPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        volumeBearishPaint.setColor(res.getColor(R.color.chart_bear));
        volumeBearishPaint.setAlpha(150);

        wickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wickPaint.setColor(res.getColor(R.color.chart_wick));
        wickPaint.setStrokeWidth(res.getDimension(R.dimen.chart_wick_width));

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(res.getColor(R.color.chart_grid));
        gridPaint.setStrokeWidth(res.getDimension(R.dimen.chart_grid_width));

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(res.getColor(R.color.chart_text));
        textPaint.setTextSize(res.getDimension(R.dimen.chart_text_size));

        lastPriceLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        lastPriceLinePaint.setColor(res.getColor(R.color.chart_last_price_line));
        lastPriceLinePaint.setStrokeWidth(res.getDimension(R.dimen.chart_last_price_width));
        lastPriceLinePaint.setStyle(Paint.Style.STROKE);
        lastPriceLinePaint.setPathEffect(new DashPathEffect(new float[]{15f, 10f}, 0f));

        movingAverage5Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        movingAverage5Paint.setColor(res.getColor(R.color.chart_ma5));
        movingAverage5Paint.setStyle(Paint.Style.STROKE);
        movingAverage5Paint.setStrokeWidth(2.5f);

        movingAverage10Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        movingAverage10Paint.setColor(res.getColor(R.color.chart_ma10));
        movingAverage10Paint.setStyle(Paint.Style.STROKE);
        movingAverage10Paint.setStrokeWidth(2.5f);

        movingAverage20Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        movingAverage20Paint.setColor(res.getColor(R.color.chart_ma20));
        movingAverage20Paint.setStyle(Paint.Style.STROKE);
        movingAverage20Paint.setStrokeWidth(2.5f);

        selectedLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        selectedLinePaint.setColor(res.getColor(R.color.chart_text));
        selectedLinePaint.setStrokeWidth(1f);
        selectedLinePaint.setAlpha(100);
    }

    @Override
    protected void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // KHI ĐỔI THEME, LOAD LẠI MÀU
        initPaints(getContext());
        invalidate();
    }

    private void initGestures(Context context) {
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                visibleCandleCount = (int) (visibleCandleCount / detector.getScaleFactor());
                if (visibleCandleCount < MIN_VISIBLE_CANDLE_COUNT) visibleCandleCount = MIN_VISIBLE_CANDLE_COUNT;
                if (visibleCandleCount > MAX_VISIBLE_CANDLE_COUNT) visibleCandleCount = MAX_VISIBLE_CANDLE_COUNT;
                invalidate();
                return true;
            }
        });

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                translationX -= distanceX; // FIX NGƯỢC
                invalidate();
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (data.isEmpty()) return false;
                int width = getWidth();
                int count = Math.min(visibleCandleCount, data.size());
                float candleWidth = width / (float) count;
                int index = (int) (e.getX() / candleWidth) + startIndexCache;
                if (index >= 0 && index < data.size()) {
                    selectedIndex = index;
                    if (selectedListener != null) selectedListener.onCandleSelected(data.get(index));
                    invalidate();
                } else {
                    selectedIndex = -1;
                    if (selectedListener != null) selectedListener.onNothingSelected();
                    invalidate();
                }
                return true;
            }
        });
    }

    public void loadChart(String symbol, String interval) {
        this.currentSymbol = symbol;
        this.currentInterval = interval;
        this.selectedIndex = -1;
        fetchCandles();
        if (liveRunnable == null) {
            liveRunnable = new Runnable() {
                @Override
                public void run() {
                    fetchCandles();
                    liveHandler.postDelayed(this, LIVE_REFRESH_INTERVAL_MS);
                }
            };
            liveHandler.postDelayed(liveRunnable, LIVE_REFRESH_INTERVAL_MS);
        }
    }

    private void fetchCandles() {
        if (currentSymbol == null || currentInterval == null) return;
        new Thread(() -> {
            try {
                String urlString = String.format(Locale.US, "https://api.binance.com/api/v3/klines?symbol=%s&interval=%s&limit=%d", currentSymbol, currentInterval, FETCH_LIMIT);
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) builder.append(line);
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
                    newData.add(new Candle(open, high, low, close, volume, openTime));
                }
                mainHandler.post(() -> {
                    data = newData;
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
                        float padding = (maxPrice - minPrice) * 0.1f;
                        minPrice -= padding;
                        maxPrice += padding;
                    }
                    translationX = 0f;
                    invalidate();
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private float calculateMovingAverage(int currentIndex, int period) {
        if (currentIndex < period - 1) return 0f;
        float sum = 0f;
        for (int i = 0; i < period; i++) sum += data.get(currentIndex - i).close;
        return sum / period;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleGestureDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        int priceChartHeight = height - TOP_PADDING_PX - BOTTOM_PADDING_PX - VOLUME_CHART_HEIGHT_PX;
        if (priceChartHeight <= 0) return;

        for (int i = 0; i <= 4; i++) {
            float y = TOP_PADDING_PX + priceChartHeight * i / 4f;
            canvas.drawLine(0f, y, width, y, gridPaint);
        }

        float volumeSeparatorY = TOP_PADDING_PX + priceChartHeight + VOLUME_TOP_MARGIN_PX;
        canvas.drawLine(0f, volumeSeparatorY, width, volumeSeparatorY, gridPaint);

        if (data.isEmpty()) {
            String loadingText = getResources().getString(R.string.chart_loading);
            canvas.drawText(loadingText, width / 2f - 100f, height / 2f, textPaint);
            return;
        }

        int count = Math.min(visibleCandleCount, data.size());
        float candleWidth = width / (float) count;
        int startIndex = data.size() - count - (int) (translationX / candleWidth);
        if (startIndex < 0) startIndex = 0;
        if (startIndex + count > data.size()) startIndex = data.size() - count;
        if (startIndex < 0) startIndex = 0;
        startIndexCache = startIndex;

        float bodyWidth = candleWidth * 0.7f;
        float priceRange = maxPrice - minPrice;
        if (priceRange == 0) priceRange = 1f;

        for (int i = 0; i < count; i++) {
            int dataIndex = startIndex + i;
            if (dataIndex >= data.size()) break;
            Candle candle = data.get(dataIndex);
            float x = i * candleWidth + candleWidth / 2f;
            float highY = TOP_PADDING_PX + priceChartHeight - ((candle.high - minPrice) / priceRange * priceChartHeight);
            float lowY = TOP_PADDING_PX + priceChartHeight - ((candle.low - minPrice) / priceRange * priceChartHeight);
            float openY = TOP_PADDING_PX + priceChartHeight - ((candle.open - minPrice) / priceRange * priceChartHeight);
            float closeY = TOP_PADDING_PX + priceChartHeight - ((candle.close - minPrice) / priceRange * priceChartHeight);
            canvas.drawLine(x, highY, x, lowY, wickPaint);
            float top = Math.min(openY, closeY);
            float bottom = Math.max(openY, closeY);
            if (Math.abs(bottom - top) < 3f) bottom = top + 3f;
            Paint bodyPaint = candle.close >= candle.open ? bullishPaint : bearishPaint;
            canvas.drawRect(x - bodyWidth / 2f, top, x + bodyWidth / 2f, bottom, bodyPaint);
        }

        for (int periodIndex = 0; periodIndex < 3; periodIndex++) {
            int period = periodIndex == 0 ? 5 : periodIndex == 1 ? 10 : 20;
            Paint paint = periodIndex == 0 ? movingAverage5Paint : periodIndex == 1 ? movingAverage10Paint : movingAverage20Paint;
            float previousX = 0f, previousY = 0f;
            boolean isFirstPoint = true;
            for (int i = 0; i < count; i++) {
                int dataIndex = startIndex + i;
                if (dataIndex >= data.size()) break;
                float movingAverage = calculateMovingAverage(dataIndex, period);
                if (movingAverage == 0f) continue;
                float x = i * candleWidth + candleWidth / 2f;
                float y = TOP_PADDING_PX + priceChartHeight - ((movingAverage - minPrice) / priceRange * priceChartHeight);
                if (!isFirstPoint) canvas.drawLine(previousX, previousY, x, y, paint);
                previousX = x; previousY = y; isFirstPoint = false;
            }
        }

        if (selectedIndex >= startIndex && selectedIndex < startIndex + count) {
            float selectedX = (selectedIndex - startIndex) * candleWidth + candleWidth / 2f;
            canvas.drawLine(selectedX, TOP_PADDING_PX, selectedX, height, selectedLinePaint);
        }

        if (lastPrice > 0f) {
            float lastPriceY = TOP_PADDING_PX + priceChartHeight - ((lastPrice - minPrice) / priceRange * priceChartHeight);
            canvas.drawLine(0f, lastPriceY, width, lastPriceY, lastPriceLinePaint);
            String lastPriceText = String.format(Locale.US, "%.2f", lastPrice);
            float textWidth = textPaint.measureText(lastPriceText);
            canvas.drawText(lastPriceText, width - textWidth - 8f, lastPriceY - 10f, textPaint);
        }

        for (int i = 0; i <= 4; i++) {
            float price = maxPrice - (maxPrice - minPrice) * i / 4f;
            float y = TOP_PADDING_PX + priceChartHeight * i / 4f + 12f;
            String priceText = String.format(Locale.US, "%.2f", price);
            float textWidth = textPaint.measureText(priceText);
            canvas.drawText(priceText, width - textWidth - 8f, y, textPaint);
        }

        for (int i = 0; i < count; i += Math.max(1, count / 4)) {
            int dataIndex = startIndex + i;
            if (dataIndex >= data.size()) break;
            Candle candle = data.get(dataIndex);
            float x = i * candleWidth;
            String timeText = timeFormat.format(new Date(candle.openTime));
            canvas.drawText(timeText, x, TOP_PADDING_PX + priceChartHeight + VOLUME_CHART_HEIGHT_PX + 50f, textPaint);
        }

        float volumeTop = TOP_PADDING_PX + priceChartHeight + VOLUME_TOP_MARGIN_PX + 10f;
        float volumeHeight = VOLUME_CHART_HEIGHT_PX - 20f;
        if (maxVolume == 0f) maxVolume = 1f;
        for (int i = 0; i < count; i++) {
            int dataIndex = startIndex + i;
            if (dataIndex >= data.size()) break;
            Candle candle = data.get(dataIndex);
            float x = i * candleWidth + candleWidth / 2f;
            float volumeBarHeight = volumeHeight * (candle.volume / maxVolume);
            Paint volumePaint = candle.close >= candle.open ? volumeBullishPaint : volumeBearishPaint;
            canvas.drawRect(x - bodyWidth / 2f, volumeTop + volumeHeight - volumeBarHeight, x + bodyWidth / 2f, volumeTop + volumeHeight, volumePaint);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (liveRunnable != null) liveHandler.removeCallbacks(liveRunnable);
    }
}
