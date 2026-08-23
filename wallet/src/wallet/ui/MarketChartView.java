package wallet.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
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
        public Candle() {}
    }

    private List<Candle> data = new ArrayList<>();

    private Paint bullPaint;
    private Paint bearPaint;
    private Paint wickPaint;
    private Paint gridPaint;
    private Paint textPaint;

    // Không set cứng - tự tính theo màn hình
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
        int bgColor, gridColor, textColor, bullColor, bearColor, wickColor;
        try {
            bgColor = ctx.getColor(R.color.chart_bg);
            gridColor = ctx.getColor(R.color.chart_grid);
            textColor = ctx.getColor(R.color.chart_text);
            bullColor = ctx.getColor(R.color.chart_bull);
            bearColor = ctx.getColor(R.color.chart_bear);
            wickColor = ctx.getColor(R.color.chart_wick);
        } catch (Exception e) {
            // fallback nếu chưa thêm colors.xml
            bgColor = Color.parseColor("#121212");
            gridColor = Color.parseColor("#333333");
            textColor = Color.parseColor("#9E9E9E");
            bullColor = Color.parseColor("#26A69A");
            bearColor = Color.parseColor("#EF5350");
            wickColor = Color.parseColor("#BDBDBD");
        }

        setBackgroundColor(bgColor);

        bullPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bullPaint.setColor(bullColor);
        bullPaint.setStyle(Paint.Style.FILL);

        bearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bearPaint.setColor(bearColor);
        bearPaint.setStyle(Paint.Style.FILL);

        wickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wickPaint.setColor(wickColor);
        wickPaint.setStrokeWidth(2f);

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(gridColor);
        gridPaint.setStrokeWidth(1f);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(textColor);
        textPaint.setTextSize(30f);
    }

    private void initGestures(Context ctx) {
        scaleDetector = new ScaleGestureDetector(ctx, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float factor = detector.getScaleFactor();
                visibleCount = (int) (visibleCount / factor);
                visibleCount = Math.max(15, Math.min(200, visibleCount));
                invalidate();
                return true;
            }
        });

        gestureDetector = new GestureDetector(ctx, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                transX -= distanceX;
                // Giới hạn kéo không cho trống
                float maxTrans = 0;
                float minTrans = -getWidth() * 1.5f;
                transX = Math.max(minTrans, Math.min(maxTrans, transX));
                invalidate();
                return true;
            }
        });
    }

    public void setData(List<Candle> d) {
        this.data = d != null ? d : new ArrayList<>();
        if (!this.data.isEmpty()) {
            minPrice = Float.MAX_VALUE;
            maxPrice = Float.MIN_VALUE;
            for (Candle c : this.data) {
                if (c.l < minPrice) minPrice = c.l;
                if (c.h > maxPrice) maxPrice = c.h;
            }
            float padding = (maxPrice - minPrice) * 0.1f;
            if (padding == 0) padding = maxPrice * 0.02f;
            minPrice -= padding;
            maxPrice += padding;
        }
        invalidate();
    }

    // Cho phép tùy chỉnh màu thủ công nếu muốn
    public void setCustomColors(int bull, int bear, int bg, int grid, int text, int wick) {
        bullPaint.setColor(bull);
        bearPaint.setColor(bear);
        wickPaint.setColor(wick);
        gridPaint.setColor(grid);
        textPaint.setColor(text);
        setBackgroundColor(bg);
        invalidate();
    }

    @Override
    protected void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Khi đổi theme sáng/tối thì load lại màu từ values-night
        initColors(getContext());
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        boolean scale = scaleDetector.onTouchEvent(ev);
        boolean scroll = gestureDetector.onTouchEvent(ev);
        return scale || scroll || super.onTouchEvent(ev);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();

        // Vẽ lưới nhẹ
        for (int i = 1; i < 4; i++) {
            float y = h * i / 4f;
            canvas.drawLine(0, y, w, y, gridPaint);
        }

        if (data.isEmpty()) {
            canvas.drawText("Loading chart...", w / 3f, h / 2f, textPaint);
            return;
        }

        float priceRange = maxPrice - minPrice;
        if (priceRange == 0) return;

        // Tự tính width 1 nến = width / số nến hiển thị
        float totalCandleSpace = w / visibleCount;
        float candleW = totalCandleSpace * 0.7f;
        float space = totalCandleSpace * 0.3f;

        int startIndex = Math.max(0, (int) (-transX / totalCandleSpace));
        int endIndex = Math.min(data.size(), startIndex + visibleCount + 3);

        // Vẽ nến từ start -> end để tiết kiệm
        for (int i = startIndex; i < endIndex; i++) {
            float x = transX + (i - startIndex) * totalCandleSpace + space / 2f;
            if (x < -candleW || x > w) continue;

            Candle c = data.get(i);

            float oY = h * (1f - (c.o - minPrice) / priceRange);
            float cY = h * (1f - (c.c - minPrice) / priceRange);
            float hY = h * (1f - (c.h - minPrice) / priceRange);
            float lY = h * (1f - (c.l - minPrice) / priceRange);

            boolean isBull = c.c >= c.o;
            Paint bodyPaint = isBull ? bullPaint : bearPaint;

            // râu nến
            canvas.drawLine(x + candleW / 2f, hY, x + candleW / 2f, lY, wickPaint);
            // thân nến
            float top = Math.min(oY, cY);
            float bottom = Math.max(oY, cY);
            if (Math.abs(bottom - top) < 2f) bottom = top + 2f; // nến doji không bị mất
            canvas.drawRect(x, top, x + candleW, bottom, bodyPaint);
        }

        // Vẽ giá max/min
        canvas.drawText(String.format("%.2f", maxPrice), 10f, 35f, textPaint);
        canvas.drawText(String.format("%.2f", minPrice), 10f, h - 10f, textPaint);
    }
}
