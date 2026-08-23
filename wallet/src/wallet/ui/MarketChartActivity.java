package wallet.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import wallet.R;

public class MarketChartActivity extends Activity {

    private MarketChartView marketChartView;
    private TextView textCurrentPrice;
    private TextView textCountdown;
    private TextView textHigh24h;
    private TextView textLow24h;
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
    private final String[] intervals = {"1m","3m","5m","15m","30m","1h","2h","4h","6h","12h","1d","3d","1w","1M"};
    private SimpleDateFormat fullTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_market_chart);

        marketChartView = findViewById(R.id.marketChartView);
        textCurrentPrice = findViewById(R.id.textCurrentPrice);
        textCountdown = findViewById(R.id.textCountdown);
        textHigh24h = findViewById(R.id.textHigh24h);
        textLow24h = findViewById(R.id.textLow24h);
        chipGroupTimeframe = findViewById(R.id.chipGroupTimeframe);
        popupCandleDetail = findViewById(R.id.popupCandleDetail);
        popupTime = findViewById(R.id.popupTime);
        popupOpen = findViewById(R.id.popupOpen);
        popupHigh = findViewById(R.id.popupHigh);
        popupLow = findViewById(R.id.popupLow);
        popupClose = findViewById(R.id.popupClose);
        popupVolume = findViewById(R.id.popupVolume);

        if (getIntent() != null && getIntent().hasExtra("symbol")) {
            currentSymbol = getIntent().getStringExtra("symbol");
        }

        setupTimeframeChips();
        setupChartListener();

        // FIX: check null để không văng nếu MarketChartView chưa init xong
        if (marketChartView != null) {
            try {
                marketChartView.loadChart(currentSymbol, currentInterval);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void setupTimeframeChips() {
        if (chipGroupTimeframe == null) return;
        chipGroupTimeframe.removeAllViews();
        for (String interval : intervals) {
            Button btn = new Button(this);
            btn.setText(interval);
            if (interval.equals(currentInterval)) {
                btn.setAlpha(1f);
            } else {
                btn.setAlpha(0.6f);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(8, 0, 8, 0);
            btn.setLayoutParams(lp);

            btn.setOnClickListener(v -> {
                currentInterval = interval;
                for (int i = 0; i < chipGroupTimeframe.getChildCount(); i++) {
                    View child = chipGroupTimeframe.getChildAt(i);
                    if (child instanceof Button) {
                        Button b = (Button) child;
                        b.setAlpha(b.getText().toString().equals(interval) ? 1f : 0.6f);
                    }
                }
                if (marketChartView != null) {
                    marketChartView.loadChart(currentSymbol, currentInterval);
                }
            });
            chipGroupTimeframe.addView(btn);
        }
    }

    private void setupChartListener() {
        if (marketChartView == null) return;
        marketChartView.setOnChartUpdateListener(new MarketChartView.OnChartUpdateListener() {
            @Override
            public void onPriceUpdate(float price, float high24h, float low24h) {
                runOnUiThread(() -> {
                    try {
                        if (textCurrentPrice != null)
                            textCurrentPrice.setText(String.format(Locale.US, "$%.2f", price));
                        if (textHigh24h != null) {
                            // FIX: fallback nếu string resource chưa có thì không văng
                            try {
                                textHigh24h.setText(getString(R.string.chart_high_label, String.format(Locale.US, "%.2f", high24h)));
                            } catch (Exception e) {
                                textHigh24h.setText("High: " + high24h);
                            }
                        }
                        if (textLow24h != null) {
                            try {
                                textLow24h.setText(getString(R.string.chart_low_label, String.format(Locale.US, "%.2f", low24h)));
                            } catch (Exception e) {
                                textLow24h.setText("Low: " + low24h);
                            }
                        }
                    } catch (Exception e) { e.printStackTrace(); }
                });
            }

            @Override
            public void onCountdownUpdate(String countdown) {
                runOnUiThread(() -> {
                    if (textCountdown == null) return;
                    try {
                        textCountdown.setText(getString(R.string.chart_close_in, countdown));
                    } catch (Exception e) {
                        textCountdown.setText("Close in: " + countdown);
                    }
                });
            }

            @Override
            public void onCandleSelected(MarketChartView.Candle candle) {
                runOnUiThread(() -> {
                    if (popupCandleDetail == null || candle == null) return;
                    try {
                        popupCandleDetail.setVisibility(View.VISIBLE);
                        if (popupTime != null) popupTime.setText(fullTimeFormat.format(new Date(candle.openTime)));
                        if (popupOpen != null) popupOpen.setText("O: " + candle.open);
                        if (popupHigh != null) popupHigh.setText("H: " + candle.high);
                        if (popupLow != null) popupLow.setText("L: " + candle.low);
                        if (popupClose != null) popupClose.setText("C: " + candle.close);
                        if (popupVolume != null) popupVolume.setText("V: " + candle.volume);
                    } catch (Exception e) { e.printStackTrace(); }
                });
            }

            @Override
            public void onNothingSelected() {
                runOnUiThread(() -> {
                    if (popupCandleDetail != null) popupCandleDetail.setVisibility(View.GONE);
                });
            }
        });
    }
}
