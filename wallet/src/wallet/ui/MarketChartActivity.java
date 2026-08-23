package wallet.ui;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Currency;
import java.util.Date;
import java.util.Locale;
import org.bitcoinj.base.Coin;
import wallet.R;
import wallet.WalletApplication;
import wallet.Configuration;
import wallet.exchangerate.ExchangeRateDao;
import wallet.exchangerate.ExchangeRateEntry;
import wallet.exchangerate.ExchangeRatesRepository;

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

    private ExchangeRateDao exchangeRateDao;
    private Configuration config;
    private SharedPreferences prefs;
    private SharedPreferences.OnSharedPreferenceChangeListener prefsListener;
    private String currentFiatCode = "USD";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private float lastDisplayPrice = 0f;

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

        WalletApplication application = (WalletApplication) getApplication();
        config = application.getConfiguration();
        prefs = application.getSharedPreferences("wallet_preferences", MODE_PRIVATE);
        exchangeRateDao = ExchangeRatesRepository.get(application).exchangeRateDao();

        currentFiatCode = config.getExchangeCurrencyCode();
        if (currentFiatCode == null) currentFiatCode = "USD";

        prefsListener = (sharedPreferences, key) -> {
            if (Configuration.PREFS_KEY_EXCHANGE_CURRENCY.equals(key)) {
                String newCode = config.getExchangeCurrencyCode();
                if (newCode!= null) {
                    currentFiatCode = newCode;
                    loadFiatRate();
                }
            }
        };
        prefs.registerOnSharedPreferenceChangeListener(prefsListener);

        setupTimeframeChips();
        setupChartListener();

        if (marketChartView!= null) {
            marketChartView.loadChart(currentSymbol, currentInterval);
        }

        loadFiatRate();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (prefs!= null && prefsListener!= null) {
            prefs.unregisterOnSharedPreferenceChangeListener(prefsListener);
        }
    }

    private void loadFiatRate() {
        new Thread(() -> {
            mainHandler.post(() -> {
                if (textVND!= null) {
                    textVND.setText(currentFiatCode);
                }
            });
        }).start();
    }

    private double getFiatPerBtc(String fiatCode) {
        ExchangeRateEntry entry = exchangeRateDao.findByCurrencyCode(fiatCode);
        if (entry == null) return 0d;
        try {
            long rateFiat = entry.getRateFiat();
            long rateCoin = entry.getRateCoin();
            if (rateCoin == 0) return 0d;
            int fractionDigits;
            try {
                fractionDigits = Currency.getInstance(fiatCode).getDefaultFractionDigits();
                if (fractionDigits < 0) fractionDigits = 2;
            } catch (Exception e) {
                fractionDigits = 2;
            }
            double fiatMajor = rateFiat / Math.pow(10, fractionDigits);
            double coinMajor = (double) rateCoin / Coin.COIN.value;
            if (coinMajor == 0d) return 0d;
            return fiatMajor / coinMajor;
        } catch (Exception e) {
            return 0d;
        }
    }

    private String getCurrencySymbol(String fiatCode) {
        try {
            Currency currency = Currency.getInstance(fiatCode);
            String symbol = currency.getSymbol(Locale.US);
            if (symbol.equals(fiatCode)) {
                symbol = currency.getSymbol();
            }
            return symbol;
        } catch (Exception e) {
            return fiatCode + " ";
        }
    }

    private void setupTimeframeChips() {
        if (chipGroupTimeframe == null) return;
        Resources res = getResources();
        chipGroupTimeframe.removeAllViews();
        for (int idx = 0; idx < intervals.length; idx++) {
            String label = intervals[idx];
            String realInterval = realIntervals[idx];

            TextView tv = new TextView(this);
            tv.setText(label);
            tv.setTextSize(13f);
            int padH = (int)(16 * res.getDisplayMetrics().density);
            int padV = (int)(6 * res.getDisplayMetrics().density);
            tv.setPadding(padH, padV, padH, padV);

            if (label.equals("1h") || realInterval.equals(currentInterval)) {
                tv.setTextColor(res.getColor(R.color.chart_text_bright));
                tv.setBackgroundResource(R.drawable.bg_time_selected);
            } else {
                tv.setTextColor(res.getColor(R.color.chart_text));
                tv.setBackgroundColor(res.getColor(android.R.color.transparent));
            }

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(4, 0, 4, 0);
            tv.setLayoutParams(lp);

            final String intervalToLoad = realInterval;
            tv.setOnClickListener(v -> {
                currentInterval = intervalToLoad;
                for (int i = 0; i < chipGroupTimeframe.getChildCount(); i++) {
                    View child = chipGroupTimeframe.getChildAt(i);
                    if (child instanceof TextView) {
                        TextView t = (TextView) child;
                        if (t.getText().toString().equals(label)) {
                            t.setTextColor(res.getColor(R.color.chart_text_bright));
                            t.setBackgroundResource(R.drawable.bg_time_selected);
                        } else {
                            t.setTextColor(res.getColor(R.color.chart_text));
                            t.setBackgroundColor(res.getColor(android.R.color.transparent));
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
        Resources res = getResources();
        marketChartView.setOnChartUpdateListener(new MarketChartView.OnChartUpdateListener() {
            @Override
            public void onPriceUpdate(float price, float high24h, float low24h) {
                runOnUiThread(() -> {
                    new Thread(() -> {
                        double fiatPerBtc = getFiatPerBtc(currentFiatCode);
                        String quoteFiat = "USD";
                        if (currentSymbol!= null && currentSymbol.endsWith("USDT")) {
                            quoteFiat = "USD";
                        }
                        double usdPerBtc = getFiatPerBtc(quoteFiat);

                        if (fiatPerBtc == 0d || usdPerBtc == 0d) {
                            return;
                        }

                        double usdToFiat = fiatPerBtc / usdPerBtc;
                        double priceInFiat = price * usdToFiat;
                        double highInFiat = high24h * usdToFiat;
                        double lowInFiat = low24h * usdToFiat;

                        mainHandler.post(() -> {
                            if (textCurrentPrice!= null) {
                                String symbol = getCurrencySymbol(currentFiatCode);
                                textCurrentPrice.setText(String.format(Locale.US, "%s%,.2f", symbol, priceInFiat));

                                int color;
                                if (lastDisplayPrice == 0f) color = res.getColor(R.color.chart_text_bright);
                                else if (priceInFiat > lastDisplayPrice) color = res.getColor(R.color.chart_bull);
                                else if (priceInFiat < lastDisplayPrice) color = res.getColor(R.color.chart_bear);
                                else color = res.getColor(R.color.chart_last_price_line);
                                textCurrentPrice.setTextColor(color);
                                lastDisplayPrice = (float) priceInFiat;
                            }
                            if (textHigh24h!= null) {
                                textHigh24h.setText(String.format(Locale.US, "24h High %,.2f %s", highInFiat, currentFiatCode));
                            }
                            if (textLow24h!= null) {
                                textLow24h.setText(String.format(Locale.US, "24h Low %,.2f %s", lowInFiat, currentFiatCode));
                            }
                            if (textVND!= null) {
                                textVND.setText(currentFiatCode);
                            }
                        });
                    }).start();
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
                    GradientDrawable bg = new GradientDrawable();
                    bg.setColor(res.getColor(R.color.chart_bg));
                    bg.setCornerRadius(12f * res.getDisplayMetrics().density);
                    bg.setStroke((int)(1 * res.getDisplayMetrics().density), res.getColor(R.color.chart_grid));
                    popupCandleDetail.setBackground(bg);
                    popupCandleDetail.setElevation(8f * res.getDisplayMetrics().density);
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
