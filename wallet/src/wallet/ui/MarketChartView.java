package wallet.ui;

import android.content.Context;
import android.content.res.Resources;
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

        public Candle(
                float open,
                float high,
                float low,
                float close,
                float volume,
                long openTime,
                long closeTime
        )
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

    public interface OnChartUpdateListener
    {
        void onPriceUpdate(float price, float high24h, float low24h);
        void onTickerUpdate(float high24h, float low24h, float volBtc, float volUsdt, float changePercent);
        void onMaUpdate(float ma7, float ma25, float ma99);
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

    private Paint bullishPaint;
    private Paint bearishPaint;
    private Paint wickPaint;
    private Paint wickBullishPaint;
    private Paint wickBearishPaint;
    private Paint gridPaint;
    private Paint textPaint;
    private Paint lastPriceLinePaint;
    private Paint movingAverage5Paint;
    private Paint movingAverage10Paint;
    private Paint movingAverage20Paint;
    private Paint volumeBullishPaint;
    private Paint volumeBearishPaint;
    private Paint selectedLinePaint;

    private static final int DEFAULT_VISIBLE_CANDLE_COUNT = 80;
    private static final int MIN_VISIBLE_CANDLE_COUNT = 20;
    private static final int MAX_VISIBLE_CANDLE_COUNT = 150;
    private static final int TOP_PADDING_PX = 12;
    private static final int BOTTOM_PADDING_PX = 30;
    private static final int VOLUME_CHART_HEIGHT_DP = 90;
    private static final int VOLUME_TOP_MARGIN_PX = 12;
    private static final int PRICE_AXIS_WIDTH_DP = 72;
    private static final int FETCH_LIMIT = 200;
    private static final long LIVE_REFRESH_INTERVAL_MS = 1000L;
    private static final long COUNTDOWN_INTERVAL_MS = 1000L;

    private int visibleCandleCount = DEFAULT_VISIBLE_CANDLE_COUNT;
    private float translationX = 0f;
    private float minPrice = 0f;
    private float maxPrice = 0f;
    private float lastPrice = 0f;
    private float maxVolume = 0f;
    private int selectedIndex = -1;
    private int startIndexCache = 0;

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

    public MarketChartView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        initPaints(context);
        initGestures(context);
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

        // Fix nền đổi màu theo theme
        int bgColor = getThemeColor(android.R.attr.colorBackground);
        setBackgroundColor(bgColor);

        bullishPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bullishPaint.setColor(res.getColor(R.color.chart_bull));
        bullishPaint.setStyle(Paint.Style.FILL);

        bearishPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bearishPaint.setColor(res.getColor(R.color.chart_bear));
        bearishPaint.setStyle(Paint.Style.FILL);

        volumeBullishPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        volumeBullishPaint.setColor(res.getColor(R.color.chart_bull));
        volumeBullishPaint.setAlpha(150);
        volumeBullishPaint.setStyle(Paint.Style.FILL);

        volumeBearishPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        volumeBearishPaint.setColor(res.getColor(R.color.chart_bear));
        volumeBearishPaint.setAlpha(150);
        volumeBearishPaint.setStyle(Paint.Style.FILL);

        wickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wickPaint.setColor(res.getColor(R.color.chart_wick));
        wickPaint.setStrokeWidth(res.getDimension(R.dimen.chart_wick_width));

        wickBullishPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wickBullishPaint.setColor(res.getColor(R.color.chart_bull));
        wickBullishPaint.setStrokeWidth(res.getDimension(R.dimen.chart_wick_width));

        wickBearishPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wickBearishPaint.setColor(res.getColor(R.color.chart_bear));
        wickBearishPaint.setStrokeWidth(res.getDimension(R.dimen.chart_wick_width));

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(res.getColor(R.color.chart_grid));
        gridPaint.setStrokeWidth(res.getDimension(R.dimen.chart_grid_width));

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(getThemeColor(android.R.attr.textColorSecondary));
        textPaint.setTextSize(res.getDimension(R.dimen.chart_text_size));

        lastPriceLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        lastPriceLinePaint.setColor(res.getColor(R.color.chart_last_price_line));
        lastPriceLinePaint.setStrokeWidth(res.getDimension(R.dimen.chart_last_price_width));
        lastPriceLinePaint.setStyle(Paint.Style.STROKE);
        lastPriceLinePaint.setPathEffect(new DashPathEffect(new float[]{15f, 10f}, 0f));

        movingAverage5Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        movingAverage5Paint.setColor(res.getColor(R.color.chart_ma5));
        movingAverage5Paint.setStyle(Paint.Style.STROKE);
        movingAverage5Paint.setStrokeWidth(res.getDimension(R.dimen.chart_ma_width));

        movingAverage10Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        movingAverage10Paint.setColor(res.getColor(R.color.chart_ma10));
        movingAverage10Paint.setStyle(Paint.Style.STROKE);
        movingAverage10Paint.setStrokeWidth(res.getDimension(R.dimen.chart_ma_width));

        movingAverage20Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        movingAverage20Paint.setColor(res.getColor(R.color.chart_ma20));
        movingAverage20Paint.setStyle(Paint.Style.STROKE);
        movingAverage20Paint.setStrokeWidth(res.getDimension(R.dimen.chart_ma_width));

        selectedLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        selectedLinePaint.setColor(res.getColor(R.color.chart_text));
        selectedLinePaint.setStrokeWidth(res.getDimension(R.dimen.chart_selected_width));
        selectedLinePaint.setAlpha(100);
    }

    @Override
    protected void onConfigurationChanged(android.content.res.Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);
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
                int index = (int) (e.getX() / candleWidth) + startIndexCache;
                if (index >= 0 && index < data.size())
                {
                    selectedIndex = index;
                    if (updateListener!= null)
                    {
                        updateListener.onCandleSelected(data.get(index));
                    }
                    // Fix volume live click xem được
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

        // Fix bám lề phải: cho phép kéo trái xem nến cũ và hở khoảng phải 50% như Binance
        float maxScroll = (data.size() - count) * candleWidth;
        // Fix lỗi kéo nến cách lề phải: thêm extra right offset
        float minScroll = -chartW * 0.5f;

        if (translationX < minScroll)
        {
            translationX = minScroll;
        }
        if (translationX > maxScroll)
        {
            translationX = maxScroll;
        }
    }

    public void loadChart(String symbol, String interval)
    {
        this.currentSymbol = symbol;
        this.currentInterval = interval;
        this.selectedIndex = -1;
        this.translationX = 0f;
        stopLive();
        fetchCandles();
        startLive();
        startCountdown();
    }

    public void setFiatCode(String code)
    {
        this.fiatCode = code;
    }

    public void setCountdown(String text)
    {
        // Để Activity set text countdown, view chỉ invalidate
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
            case "1m":
            {
                return 60_000L;
            }
            case "3m":
            {
                return 3L * 60_000L;
            }
            case "5m":
            {
                return 5L * 60_000L;
            }
            case "15m":
            {
                return 15L * 60_000L;
            }
            case "30m":
            {
                return 30L * 60_000L;
            }
            case "1h":
            {
                return 60L * 60_000L;
            }
            case "2h":
            {
                return 2L * 60L * 60_000L;
            }
            case "4h":
            {
                return 4L * 60L * 60_000L;
            }
            case "6h":
            {
                return 6L * 60L * 60_000L;
            }
            case "12h":
            {
                return 12L * 60L * 60_000L;
            }
            case "1d":
            {
                return 24L * 60L * 60_000L;
            }
            case "3d":
            {
                return 3L * 24L * 60L * 60_000L;
            }
            case "1w":
            {
                return 7L * 24L * 60L * 60_000L;
            }
            case "1M":
            {
                return 30L * 24L * 60L * 60_000L;
            }
            default:
            {
                return 60_000L;
            }
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
        float ma7 = calculateMovingAverage(last, 7);
        float ma25 = calculateMovingAverage(last, 25);
        float ma99 = calculateMovingAverage(last, 99);
        updateListener.onMaUpdate(ma7, ma25, ma99);
    }

    private void fetchCandles()
    {
        if (currentSymbol == null || currentInterval == null)
        {
            return;
        }
        new Thread(() ->
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
                mainHandler.post(() ->
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
                });
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }).start();
    }

    private void fetchPriceAndCandle()
    {
        if (currentSymbol == null)
        {
            return;
        }
        new Thread(() ->
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
                mainHandler.post(() ->
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
                });
            }
            catch (Exception e)
            {
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
        if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL)
        {
            float density = getResources().getDisplayMetrics().density;
            int priceAxisW = (int) (PRICE_AXIS_WIDTH_DP * density);
            int chartW = getWidth() - priceAxisW;
            int timeAxisH = (int) (20 * density);
            int fullH = getHeight();
            int volumeH = (int) (VOLUME_CHART_HEIGHT_DP * density);
            int priceChartH = fullH - TOP_PADDING_PX - BOTTOM_PADDING_PX - VOLUME_TOP_MARGIN_PX - volumeH - timeAxisH;
            float y = event.getY();
            if (y < TOP_PADDING_PX || y > TOP_PADDING_PX + priceChartH)
            {
                if (selectedIndex!= -1 && event.getAction() == MotionEvent.ACTION_UP)
                {
                    // Fix volume chỉ báo phải live click xem được
                    if (y >= TOP_PADDING_PX + priceChartH)
                    {
                        if (volumeClickListener!= null && selectedIndex >= 0 && selectedIndex < data.size())
                        {
                            volumeClickListener.onVolumeClick(data.get(selectedIndex));
                        }
                    }
                }
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

        for (int i = 0; i <= 4; i++)
        {
            float y = TOP_PADDING_PX + priceChartHeight * i / 4f;
            canvas.drawLine(0f, y, chartWidth, y, gridPaint);
        }

        float volumeSeparatorY = TOP_PADDING_PX + priceChartHeight + VOLUME_TOP_MARGIN_PX;
        canvas.drawLine(0f, volumeSeparatorY, chartWidth, volumeSeparatorY, gridPaint);
        canvas.drawLine(chartWidth, 0f, chartWidth, fullHeight, gridPaint);

        if (data.isEmpty())
        {
            String loadingText = getResources().getString(R.string.chart_loading);
            canvas.drawText(loadingText, chartWidth / 2f - 100f, fullHeight / 2f, textPaint);
            return;
        }

        int count = Math.min(visibleCandleCount, data.size());
        float candleWidth = chartWidth / (float) count;
        int startIndex = data.size() - count - (int) (translationX / candleWidth);
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

        float bodyWidth = candleWidth * 0.60f;
        if (bodyWidth < 3f * density)
        {
            bodyWidth = 3f * density;
        }
        if (bodyWidth > 14f * density)
        {
            bodyWidth = 14f * density;
        }

        float priceRange = maxPrice - minPrice;
        if (priceRange == 0f)
        {
            priceRange = 1f;
        }

        for (int i = 0; i < count; i++)
        {
            int dataIndex = startIndex + i;
            if (dataIndex >= data.size())
            {
                break;
            }
            Candle candle = data.get(dataIndex);
            float x = i * candleWidth + candleWidth / 2f;
            float highY = TOP_PADDING_PX + priceChartHeight - ((candle.high - minPrice) / priceRange * priceChartHeight);
            float lowY = TOP_PADDING_PX + priceChartHeight - ((candle.low - minPrice) / priceRange * priceChartHeight);
            float openY = TOP_PADDING_PX + priceChartHeight - ((candle.open - minPrice) / priceRange * priceChartHeight);
            float closeY = TOP_PADDING_PX + priceChartHeight - ((candle.close - minPrice) / priceRange * priceChartHeight);

            boolean isBullish = candle.close >= candle.open;
            Paint currentWickPaint = isBullish? wickBullishPaint : wickBearishPaint;
            Paint bodyPaint = isBullish? bullishPaint : bearishPaint;

            canvas.drawLine(x, highY, x, lowY, currentWickPaint);

            float top = Math.min(openY, closeY);
            float bottom = Math.max(openY, closeY);
            if (Math.abs(bottom - top) < 2f * density)
            {
                bottom = top + 2f * density;
            }
            canvas.drawRect(x - bodyWidth / 2f, top, x + bodyWidth / 2f, bottom, bodyPaint);
        }

        int[] maPeriods = {7, 25, 99};
        Paint[] maPaints = {movingAverage5Paint, movingAverage10Paint, movingAverage20Paint};
        for (int periodIndex = 0; periodIndex < maPeriods.length; periodIndex++)
        {
            int period = maPeriods[periodIndex];
            Paint paint = maPaints[periodIndex];
            float previousX = 0f;
            float previousY = 0f;
            boolean isFirstPoint = true;
            for (int i = 0; i < count; i++)
            {
                int dataIndex = startIndex + i;
                if (dataIndex >= data.size())
                {
                    break;
                }
                float movingAverage = calculateMovingAverage(dataIndex, period);
                if (movingAverage == 0f)
                {
                    continue;
                }
                float x = i * candleWidth + candleWidth / 2f;
                float y = TOP_PADDING_PX + priceChartHeight - ((movingAverage - minPrice) / priceRange * priceChartHeight);
                if (!isFirstPoint)
                {
                    canvas.drawLine(previousX, previousY, x, y, paint);
                }
                previousX = x;
                previousY = y;
                isFirstPoint = false;
            }
        }

        if (selectedIndex >= startIndex && selectedIndex < startIndex + count)
        {
            float selectedX = (selectedIndex - startIndex) * candleWidth + candleWidth / 2f;
            canvas.drawLine(selectedX, TOP_PADDING_PX, selectedX, TOP_PADDING_PX + priceChartHeight, selectedLinePaint);
        }

        if (lastPrice > 0f)
        {
            float lastPriceY = TOP_PADDING_PX + priceChartHeight - ((lastPrice - minPrice) / priceRange * priceChartHeight);
            canvas.drawLine(0f, lastPriceY, chartWidth, lastPriceY, lastPriceLinePaint);
            String lastPriceText = String.format(Locale.US, "%.2f", lastPrice);
            canvas.drawText(lastPriceText, chartWidth + 8f * density, lastPriceY - 4f * density, textPaint);
        }

        for (int i = 0; i <= 4; i++)
        {
            float price = maxPrice - (maxPrice - minPrice) * i / 4f;
            float y = TOP_PADDING_PX + priceChartHeight * i / 4f + getResources().getDimension(R.dimen.chart_price_text_offset);
            String priceText = String.format(Locale.US, "%.2f", price);
            canvas.drawText(priceText, chartWidth + 8f * density, y, textPaint);
        }

        for (int i = 0; i < count; i += Math.max(1, count / 4))
        {
            int dataIndex = startIndex + i;
            if (dataIndex >= data.size())
            {
                break;
            }
            float x = i * candleWidth;
            String timeText = timeFormat.format(new Date(data.get(dataIndex).openTime));
            canvas.drawText(timeText, x, TOP_PADDING_PX + priceChartHeight + volumeHeightPx + VOLUME_TOP_MARGIN_PX + 16f * density, textPaint);
        }

        float volumeTop = TOP_PADDING_PX + priceChartHeight + VOLUME_TOP_MARGIN_PX;
        float volumeHeight = volumeHeightPx;
        if (maxVolume == 0f)
        {
            maxVolume = 1f;
        }
        for (int i = 0; i < count; i++)
        {
            int dataIndex = startIndex + i;
            if (dataIndex >= data.size())
            {
                break;
            }
            Candle candle = data.get(dataIndex);
            float x = i * candleWidth + candleWidth / 2f;
            float volumeBarHeight = volumeHeight * (candle.volume / maxVolume);
            Paint volumePaint = candle.close >= candle.open? volumeBullishPaint : volumeBearishPaint;
            canvas.drawRect(x - bodyWidth / 2f, volumeTop + volumeHeight - volumeBarHeight, x + bodyWidth / 2f, volumeTop + volumeHeight, volumePaint);
        }
    }

    @Override
    protected void onDetachedFromWindow()
    {
        super.onDetachedFromWindow();
        stopLive();
    }
}
