package wallet.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
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
import wallet.R;

public class MarketChartView extends View {

    public static class Candle {
        public float o, h, l, c, vol;
        public long time;
        public Candle(float o, float h, float l, float c, float vol, long time) {
            this.o = o; this.h = h; this.l = l; this.c = c; this.vol = vol; this.time = time;
        }
    }

    private List<Candle> data = new ArrayList<>();
    private Paint bullPaint, bearPaint, wickPaint, gridPaint, textPaint;
    private int visibleCount = 60;
    private float transX = 0f;
    private float minPrice = 0f, maxPrice = 0f;
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    public MarketChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initColors(context);
        initGestures(context);
    }

    private void initColors(Context ctx) {
        int bg, grid, text, bull, bear, wick;
        try {
            bg = ctx.getColor(R.color.chart_bg);
            grid = ctx.getColor(R.color.chart_grid);
            text = ctx.getColor(R.color.chart_text);
            bull = ctx.getColor(R.color.chart_bull);
            bear = ctx.getColor(R.color.chart_bear);
            wick = ctx.getColor(R.color.chart_wick);
        } catch (Exception e) {
            bg = Color.parseColor("#121212");
            grid = Color.parseColor("#333333");
            text = Color.parseColor("#9E9E9E");
            bull = Color.parseColor("#26A69A");
            bear = Color.parseColor("#EF5350");
            wick = Color.parseColor("#BDBDBD");
        }
        setBackgroundColor(bg);
        bullPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bullPaint.setColor(bull);
        bullPaint.setStyle(Paint.Style.FILL);
        bearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bearPaint.setColor(bear);
        bearPaint.setStyle(Paint.Style.FILL);
        wickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wickPaint.setColor(wick);
        wickPaint.setStrokeWidth(2f);
        gridPaint = new Paint();
        gridPaint.setColor(grid);
        gridPaint.setStrokeWidth(1f);
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(text);
        textPaint.setTextSize(28f);
    }

    private void initGestures(Context c) {
        scaleDetector = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                visibleCount = (int) (visibleCount / detector.getScaleFactor());
                if (visibleCount < 20) visibleCount = 20;
                if (visibleCount > 200) visibleCount = 200;
                invalidate();
                return true;
            }
        });
        gestureDetector = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                transX += dx;
                invalidate();
                return true;
            }
        });
    }

    public void loadChart(String symbol, String interval) {
        new Thread(() -> {
            try {
                String urlStr = "https://api.binance.com/api/v3/klines?symbol=" + symbol + "&interval=" + interval + "&limit=150";
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                JSONArray arr = new JSONArray(sb.toString());
                List<Candle> newData = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONArray k = arr.getJSONArray(i);
                    float o = (float) k.getDouble(1);
                    float h = (float) k.getDouble(2);
                    float l = (float) k.getDouble(3);
                    float cc = (float) k.getDouble(4);
                    float v = (float) k.getDouble(5);
                    long t = k.getLong(0);
                    newData.add(new Candle(o, h, l, cc, v, t));
                }
                ((Activity) getContext()).runOnUiThread(() -> {
                    data = newData;
                    if (!data.isEmpty()) {
                        minPrice = Float.MAX_VALUE;
                        maxPrice = Float.MIN_VALUE;
                        for (Candle cd : data) {
                            if (cd.l < minPrice) minPrice = cd.l;
                            if (cd.h > maxPrice) maxPrice = cd.h;
                        }
                        float padding = (maxPrice - minPrice) * 0.1f;
                        minPrice -= padding;
                        maxPrice += padding;
                    }
                    invalidate();
                });
            } catch (Exception e) {
                e.printStackTrace();
                ((Activity) getContext()).runOnUiThread(() -> {
                    textPaint.setColor(Color.RED);
                    invalidate();
                });
            }
        }).start();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (h == 0) return;

        // grid
        for (int i = 1; i < 4; i++) {
            float y = h * i / 4f;
            canvas.drawLine(0, y, w, y, gridPaint);
        }

        if (data.isEmpty()) {
            canvas.drawText("Loading chart...", w / 2f - 80, h / 2f, textPaint);
            return;
        }

        int count = Math.min(visibleCount, data.size());
        int start = Math.max(0, data.size() - count - (int)(transX / (w / (float)visibleCount)));
        if (start < 0) start = 0;
        if (start + count > data.size()) start = data.size() - count;

        float candleWidth = w / (float) count;
        float bodyWidth = candleWidth * 0.7f;

        for (int i = 0; i < count; i++) {
            int idx = start + i;
            if (idx >= data.size()) break;
            Candle c = data.get(idx);
            float x = i * candleWidth + candleWidth / 2f;

            float highY = h - ((c.h - minPrice) / (maxPrice - minPrice) * h);
            float lowY = h - ((c.l - minPrice) / (maxPrice - minPrice) * h);
            float openY = h - ((c.o - minPrice) / (maxPrice - minPrice) * h);
            float closeY = h - ((c.c - minPrice) / (maxPrice - minPrice) * h);

            // wick
            canvas.drawLine(x, highY, x, lowY, wickPaint);

            // body
            float top = Math.min(openY, closeY);
            float bottom = Math.max(openY, closeY);
            if (Math.abs(bottom - top) < 2f) bottom = top + 2f;

            Paint p = c.c >= c.o ? bullPaint : bearPaint;
            canvas.drawRect(x - bodyWidth / 2f, top, x + bodyWidth / 2f, bottom, p);
        }

        // price label
        canvas.drawText(String.format("%.2f", maxPrice), 10, 30, textPaint);
        canvas.drawText(String.format("%.2f", minPrice), 10, h - 10, textPaint);
    }
}
