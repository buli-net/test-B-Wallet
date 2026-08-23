package wallet.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import wallet.R;

public class MarketChartActivity extends AppCompatActivity {

    private MarketChartView marketChartView;
    private TextView textCurrentPrice;
    private TextView textCountdown;
    private TextView textHigh24h;
    private TextView textLow24h;
    private ChipGroup chipGroupTimeframe;
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

        marketChartView.loadChart(currentSymbol, currentInterval);
    }

    private void setupTimeframeChips() {
        chipGroupTimeframe.removeAllViews();
        for (String interval : intervals) {
            Chip chip = new Chip(this);
            chip.setText(interval);
            chip.setCheckable(true);
            chip.setChecked(interval.equals(currentInterval));
            chip.setOnClickListener(v -> {
                currentInterval = interval;
                for (int i = 0; i < chipGroupTimeframe.getChildCount(); i++) {
                    Chip childChip = (Chip) chipGroupTimeframe.getChildAt(i);
                    childChip.setChecked(childChip.getText().toString().equals(interval));
                }
                marketChartView.loadChart(currentSymbol, currentInterval);
            });
            chipGroupTimeframe.addView(chip);
        }
    }

    private void setupChartListener() {
        marketChartView.setOnChartUpdateListener(new MarketChartView.OnChartUpdateListener() {
            @Override
            public void onPriceUpdate(float price, float high24h, float low24h) {
                runOnUiThread(() -> {
                    textCurrentPrice.setText(String.format(Locale.US, "$%.2f", price));
                    textHigh24h.setText(getString(R.string.chart_high_label, String.format(Locale.US, "%.2f", high24h)));
                    textLow24h.setText(getString(R.string.chart_low_label, String.format(Locale.US, "%.2f", low24h)));
                });
            }

            @Override
            public void onCountdownUpdate(String countdown) {
                runOnUiThread(() -> {
                    textCountdown.setText(getString(R.string.chart_close_in, countdown));
                });
            }

            @Override
            public void onCandleSelected(MarketChartView.Candle candle) {
                runOnUiThread(() -> {
                    popupCandleDetail.setVisibility(View.VISIBLE);
                    popupTime.setText(fullTimeFormat.format(new Date(candle.openTime)));
                    popupOpen.setText(getString(R.string.chart_open_label, String.valueOf(candle.open)));
                    popupHigh.setText(getString(R.string.chart_high_detail, String.valueOf(candle.high)));
                    popupLow.setText(getString(R.string.chart_low_detail, String.valueOf(candle.low)));
                    popupClose.setText(getString(R.string.chart_close_label, String.valueOf(candle.close)));
                    popupVolume.setText(getString(R.string.chart_volume_label, String.valueOf(candle.volume)));
                });
            }

            @Override
            public void onNothingSelected() {
                runOnUiThread(() -> {
                    popupCandleDetail.setVisibility(View.GONE);
                });
            }
        });
    }
}
