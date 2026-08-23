package wallet.ui;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import wallet.R;

public class MarketChartActivity extends Activity {

    private MarketChartView marketChartView;
    private TextView textCurrentPrice;
    private TextView textVND;
    private TextView textCountdown;
    private TextView textHigh24h;
    private TextView textLow24h;
    private TextView textMaLabel;
    private LinearLayout chipGroupTimeframe;
    private View popupCandleDetail;
    private TextView popupTime;
    private TextView popupOpen;
    private TextView popupHigh;
    private TextView popupLow;
    private TextView popupClose;
    private TextView popupVolume;

    private String currentSymbol = "BTCUSDT";
    private String currentInterval = "15m";
    private final String[] intervals = {"Time","15m","1h","4h","1D","1M","More"};
    private final String[] realIntervals = {"15m","15m","1h","4h","1d","1M","1M"};
    private SimpleDateFormat fullTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_market_chart);

        marketChartView = findViewById(R.id.marketChartView);
        textCurrentPrice = findViewById(R.id.textCurrentPrice);
        textVND = findViewById(R.id.textVND);
        textCountdown = findViewById(R.id.textCountdown);
        textHigh24h = findViewById(R.id.textHigh24h);
        textLow24h = findViewById(R.id.textLow24h);
        textMaLabel = findViewById(R.id.textMaLabel);
        chipGroupTimeframe = findViewById(R.id.chipGroupTimeframe);
        popupCandleDetail = findViewById(R.id.popupCandleDetail);
        popupTime = findViewById(R.id.popupTime);
        popupOpen = findViewById(R.id.popupOpen);
        popupHigh = findViewById(R.id.popupHigh);
        popupLow = findViewById(R.id.popupLow);
        popupClose = findViewById(R.id.popupClose);
        popupVolume = findViewById(R.id.popupVolume);

        if (getIntent()!= null && getIntent().hasExtra("symbol")) {
            currentSymbol = getIntent().getStringExtra("symbol");
        }

        setupTimeframeChips();
        setupChartListener();

        if (marketChartView!= null) {
            marketChartView.loadChart(currentSymbol, currentInterval);
        }
    }

    private void setupTimeframeChips() {
        if (chipGroupTimeframe == null) return;
        chipGroupTimeframe.removeAllViews();
        for (int idx = 0; idx < intervals.length; idx++) {
            String label = intervals[idx];
            String realInterval = realIntervals[idx];

            TextView tv = new TextView(this);
            tv.setText(label);
            tv.setTextSize(13f);
            int padH = (int)(16 * getResources().getDisplayMetrics().density);
            int padV = (int)(6 * getResources().getDisplayMetrics().density);
            tv.setPadding(padH, padV, padH, padV);

            if (label.equals("1h") || realInterval.equals(currentInterval)) {
                tv.setTextColor(Color.WHITE);
                tv.setBackgroundResource(R.drawable.bg_time_selected);
            } else {
                tv.setTextColor(Color.parseColor("#848E9C"));
                tv.setBackgroundColor(Color.TRANSPARENT);
            }

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(4, 0, 4, 0);
            tv.setLayoutParams(lp);

            final String intervalToLoad = realInterval;
            tv.setOnClickListener(v -> {
                currentInterval = intervalToLoad;
                // update all chips color
                for (int i = 0; i < chipGroupTimeframe.getChildCount(); i++) {
                    View child = chipGroupTimeframe.getChildAt(i);
                    if (child instanceof TextView) {
                        TextView t = (TextView) child;
                        if (t.getText().toString().equals(label)) {
                            t.setTextColor(Color.WHITE);
                            t.setBackgroundResource(R.drawable.bg_time_selected);
                        } else {
                            t.setTextColor(Color.parseColor("#848E9C"));
                            t.setBackgroundColor(Color.TRANSPARENT);
                        }
                    }
                }
                if (marketChartView!= null) {
                    marketChartView.loadChart(currentSymbol, currentInterval);
                }
            });
            chipGroupTimeframe.addView(tv);
        }
    }

    private void setupChartListener() {
        if (marketChartView == null) return;
        marketChartView.setOnChartUpdateListener(new MarketChartView.OnChartUpdateListener() {
            @Override
            public void onPriceUpdate(float price, float high24h, float low24h) {
                runOnUiThread(() -> {
                    if (textCurrentPrice!= null)
                        textCurrentPrice.setText(String.format(Locale.US, "$%,.2f", price));
                    if (textHigh24h!= null)
                        textHigh24h.setText(String.format(Locale.US, "24h High %,.2f", high24h));
                    if (textLow24h!= null)
                        textLow24h.setText(String.format(Locale.US, "24h Low %,.2f", low24h));
                    if (textVND!= null) {
                        // fake VND như Binance
                        double vnd = price * 26100;
                        textVND.setText(String.format(Locale.US, "₫%,.2f -1.38%%", vnd));
                    }
                    if (textMaLabel!= null) {
                        textMaLabel.setText(String.format(Locale.US, "MA(7): %.2f MA(25): %.2f MA(99): %.2f", price*0.998f, price*1.01f, price*0.96f));
                    }
                });
            }

            @Override
            public void onCountdownUpdate(String countdown) {
                runOnUiThread(() -> {
                    if (textCountdown!= null) {
                        textCountdown.setText("Close in: " + countdown);
                    }
                });
            }

            @Override
            public void onCandleSelected(MarketChartView.Candle candle) {
                runOnUiThread(() -> {
                    if (popupCandleDetail == null || candle == null) return;
                    popupCandleDetail.setVisibility(View.VISIBLE);
                    if (popupTime!= null) popupTime.setText(fullTimeFormat.format(new Date(candle.openTime)));
                    if (popupOpen!= null) popupOpen.setText(String.format(Locale.US, "Open %.2f", candle.open));
                    if (popupHigh!= null) popupHigh.setText(String.format(Locale.US, "High %.2f", candle.high));
                    if (popupLow!= null) popupLow.setText(String.format(Locale.US, "Low %.2f", candle.low));
                    if (popupClose!= null) popupClose.setText(String.format(Locale.US, "Close %.2f", candle.close));
                    if (popupVolume!= null) popupVolume.setText(String.format(Locale.US, "Vol %.2f", candle.volume));
                });
            }

            @Override
            public void onNothingSelected() {
                runOnUiThread(() -> {
                    if (popupCandleDetail!= null) popupCandleDetail.setVisibility(View.GONE);
                });
            }
        });
    }
}
