package wallet.ui;

import android.app.Activity;
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
import java.util.ArrayList;
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
        public Candle(float open, float high, float low, float close, float volume, long openTime) {
            this.open = open; this.high = high; this.low = low; this.close = close; this.volume = volume; this.openTime = openTime;
        }
    }

    private List<Candle> data = new ArrayList<>();
    private Paint bullishPaint;
    private Paint bearishPaint;
    private Paint wickPaint;
    private Paint gridPaint;
    private Paint textPaint;
    private Paint lastPriceLinePaint;

    private static final int DEFAULT_VISIBLE_CANDLE_COUNT = 80;
    private static final int MIN_VISIBLE_CANDLE_COUNT = 20;
    private static final int MAX_VISIBLE_CANDLE_COUNT = 200;
    private static final int TOP_PADDING_PX = 80;
    private static final int BOTTOM_PADDING_PX = 60;
    private static final int FETCH_LIMIT = 150;
    private static final long LIVE_REFRESH_INTERVAL_MS = 10000L;

    private int visibleCandleCount = DEFAULT_VISIBLE_CANDLE_COUNT;
    private float translationX = 0f;
    private float minPrice = 0f;
    private float maxPrice = 0f;
    private float lastPrice = 0f;

    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetector gestureDetector;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Handler liveHandler = new Handler(Looper.getMainLooper());
    private Runnable liveRunnable;
    private String currentSymbol;
    private String currentInterval;

    public MarketChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPaints(context);
        initGestures(context);
    }

    private void initPaints(Context context) {
        Resources res = context.getResources();
        int colorBackground = res.getColor(R.color.chart_bg);
        int colorGrid = res.getColor(R.color.chart_grid);
        int colorText = res.getColor(R.color.chart_text);
        int colorBull = res.getColor(R.color.chart_bull);
        int colorBear = res.getColor(R.color.chart_bear);
        int colorWick = res.getColor(R.color.chart_wick);
        int colorLastPriceLine = res.getColor(R.color.chart_last_price_line);

        setBackgroundColor(colorBackground);

        bullishPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bullishPaint.setColor(colorBull);
        bullishPaint.setStyle(Paint.Style.FILL);

        bearishPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bearishPaint.setColor(colorBear);
        bearishPaint.setStyle(Paint.Style.FILL);

        wickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wickPaint.setColor(colorWick);
        wickPaint.setStrokeWidth(res.getDimension(R.dimen.chart_wick_width));

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(colorGrid);
        gridPaint.setStrokeWidth(res.getDimension(R.dimen.chart_grid_width));

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(colorText);
        textPaint.setTextSize(res.getDimension(R.dimen.chart_text_size));

        lastPriceLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        lastPriceLinePaint.setColor(colorLastPriceLine);
        lastPriceLinePaint.setStrokeWidth(res.getDimension(R.dimen.chart_last_price_width));
        lastPriceLinePaint.setStyle(Paint.Style.STROKE);
        lastPriceLinePaint.setPathEffect(new DashPathEffect(new float[]{15f, 10f}, 0f));
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
                translationX += distanceX;
                invalidate();
                return true;
            }
        });
    }

    public void loadChart(String symbol, String interval) {
        this.currentSymbol = symbol;
        this.currentInterval = interval;
        fetchCandles();
        if (liveRunnable == null) {
            liveRunnable = new Runnable() {
                @Override public void run() {
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
                        for (Candle candle : data) {
                            if (candle.low < minPrice) minPrice = candle.low;
                            if (candle.high > maxPrice) maxPrice = candle.high;
                        }
                        lastPrice = data.get(data.size() - 1).close;
                        float padding = (maxPrice - minPrice) * 0.08f;
                        minPrice -= padding;
                        maxPrice += padding;
                    }
                    invalidate();
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
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
        int chartHeight = height - TOP_PADDING_PX - BOTTOM_PADDING_PX;
        if (chartHeight <= 0) return;

        for (int i = 0; i <= 4; i++) {
            float y = TOP_PADDING_PX + chartHeight * i / 4f;
            canvas.drawLine(0f, y, width, y, gridPaint);
        }

        if (data.isEmpty()) {
            canvas.drawText(getResources().getString(R.string.chart_loading), width / 2f - 100f, height / 2f, textPaint);
            return;
        }

        int count = Math.min(visibleCandleCount, data.size());
        float candleWidth = width / (float) count;
        int startIndex = data.size() - count - (int) (translationX / candleWidth);
        if (startIndex < 0) startIndex = 0;
        if (startIndex + count > data.size()) startIndex = data.size() - count;

        float bodyWidth = candleWidth * 0.75f;
        float priceRange = maxPrice - minPrice;
        if (priceRange == 0) priceRange = 1f;

        for (int i = 0; i < count; i++) {
            int dataIndex = startIndex + i;
            if (dataIndex >= data.size()) break;
            Candle candle = data.get(dataIndex);
            float x = i * candleWidth + candleWidth / 2f;
            float highY = TOP_PADDING_PX + chartHeight - ((candle.high - minPrice) / priceRange * chartHeight);
            float lowY = TOP_PADDING_PX + chartHeight - ((candle.low - minPrice) / priceRange * chartHeight);
            float openY = TOP_PADDING_PX + chartHeight - ((candle.open - minPrice) / priceRange * chartHeight);
            float closeY = TOP_PADDING_PX + chartHeight - ((candle.close - minPrice) / priceRange * chartHeight);

            canvas.drawLine(x, highY, x, lowY, wickPaint);

            float top = Math.min(openY, closeY);
            float bottom = Math.max(openY, closeY);
            if (Math.abs(bottom - top) < 3f) bottom = top + 3f;

            Paint bodyPaint = candle.close >= candle.open ? bullishPaint : bearishPaint;
            canvas.drawRect(x - bodyWidth / 2f, top, x + bodyWidth / 2f, bottom, bodyPaint);
        }

        if (lastPrice > 0f) {
            float lastPriceY = TOP_PADDING_PX + chartHeight - ((lastPrice - minPrice) / priceRange * chartHeight);
            canvas.drawLine(0f, lastPriceY, width, lastPriceY, lastPriceLinePaint);
            String lastPriceText = String.format(Locale.US, "%.2f", lastPrice);
            canvas.drawText(lastPriceText, 12f, lastPriceY - 12f, textPaint);
        }

        canvas.drawText(String.format(Locale.US, "%.2f", maxPrice), 12f, TOP_PADDING_PX + 30f, textPaint);
        canvas.drawText(String.format(Locale.US, "%.2f", minPrice), 12f, height - 12f, textPaint);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (liveRunnable != null) liveHandler.removeCallbacks(liveRunnable);
    }
}
