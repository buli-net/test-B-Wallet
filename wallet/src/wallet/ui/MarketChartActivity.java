package wallet.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.View;
import android.widget.GridLayout;
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
    private TextView textFiat;
    private TextView textCountdown;
    private TextView textHigh24h;
    private TextView textLow24h;
    private TextView textVol24h;
    private TextView textChange24h;
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
    private final String[] allIntervals = {"1m","3m","5m","15m","30m","1h","2h","4h","6h","12h","1D","1W","1M"};
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
        textFiat = findViewById(R.id.textFiat);
        textCountdown = findViewById(R.id.textCountdown);
        textHigh24h = findViewById(R.id.textHigh24h);
        textLow24h = findViewById(R.id.textLow24h);
        textVol24h = findViewById(R.id.textVol24h);
        textChange24h = findViewById(R.id.textChange24h);
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
        mainHandler.post(() -> {
            if (textFiat!= null) textFiat.setText(currentFiatCode);
        });
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
            switch (fiatCode) {
                case "USD": return "$";
                case "EUR": return "€";
                case "VND": return "₫";
                case "GBP": return "£";
                case "JPY": return "¥";
                case "CNY": return "¥";
                case "CHF": return "Fr";
                case "AUD": return "A$";
                case "CAD": return "C$";
                case "SGD": return "S$";
                case "HKD": return "HK$";
                case "KRW": return "₩";
                case "INR": return "₹";
                case "RUB": return "₽";
                case "TRY": return "₺";
                case "BRL": return "R$";
            }
            Currency currency = Currency.getInstance(fiatCode);
            String sym = currency.getSymbol(Locale.US);
            if (sym.equals(fiatCode)) sym = currency.getSymbol();
            if (sym.equals(fiatCode) || sym.length() > 4) return fiatCode + " ";
            return sym;
        } catch (Exception e) {
            return fiatCode + " ";
        }
    }

    private int getThemeColor(int attr) {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(attr, tv, true);
        if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return tv.data;
        } else {
            try {
                return getResources().getColor(tv.resourceId, getTheme());
            } catch (Exception e) {
                return tv.data;
            }
        }
    }

    private void showMoreIntervalsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Intervals");
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        grid.setPadding(32, 32, 32, 32);
        Resources res = getResources();
        for (String interval : allIntervals) {
            TextView tv = new TextView(this);
            tv.setText(interval);
            tv.setTextSize(14f);
            tv.setPadding(32, 20, 32, 20);
            tv.setTextColor(getThemeColor(android.R.attr.textColorPrimary));
            if (interval.equals(currentInterval) || (interval.equals("1D") && currentInterval.equals("1d"))) {
                tv.setBackgroundResource(R.drawable.bg_time_selected);
                tv.setTextColor(getThemeColor(android.R.attr.colorBackground));
            } else {
                tv.setBackgroundColor(res.getColor(android.R.color.transparent));
            }
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.setMargins(8, 8, 8, 8);
            tv.setLayoutParams(lp);
            tv.setOnClickListener(v -> {
                currentInterval = interval.equals("1D")? "1d" : interval.equals("1W")? "1w" : interval;
                if (marketChartView!= null) marketChartView.loadChart(currentSymbol, currentInterval);
                setupTimeframeChips();
            });
            grid.addView(tv);
        }
        builder.setView(grid);
        builder.setNegativeButton("Close", null);
        builder.show();
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
            boolean isSelected = false;
            if (label.equals("15m") && currentInterval.equals("15m")) isSelected = true;
            if (label.equals("1h") && currentInterval.equals("1h")) isSelected = true;
            if (label.equals("4h") && currentInterval.equals("4h")) isSelected = true;
            if (label.equals("1D") && (currentInterval.equals("1d") || currentInterval.equals("1D"))) isSelected = true;
            if (label.equals("1M") && currentInterval.equals("1M")) isSelected = true;
            if (isSelected) {
                tv.setTextColor(getThemeColor(android.R.attr.colorBackground));
                tv.setBackgroundResource(R.drawable.bg_time_selected);
            } else {
                tv.setTextColor(getThemeColor(android.R.attr.textColorPrimary));
                tv.setBackgroundColor(res.getColor(android.R.color.transparent));
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(8, 0, 8, 0);
            tv.setLayoutParams(lp);
            final String intervalToLoad = realInterval;
            final String selectedLabel = label;
            tv.setOnClickListener(v -> {
                if (selectedLabel.equals("Time") || selectedLabel.equals("More")) {
                    showMoreIntervalsDialog();
                    return;
                }
                currentInterval = intervalToLoad;
                for (int i = 0; i < chipGroupTimeframe.getChildCount(); i++) {
                    View child = chipGroupTimeframe.getChildAt(i);
                    if (child instanceof TextView) {
                        TextView t = (TextView) child;
                        if (t.getText().toString().equals(selectedLabel)) {
                            t.setTextColor(getThemeColor(android.R.attr.colorBackground));
                            t.setBackgroundResource(R.drawable.bg_time_selected);
                        } else {
                            t.setTextColor(getThemeColor(android.R.attr.textColorPrimary));
                            t.setBackgroundColor(res.getColor(android.R.color.transparent));
                        }
                    }
                }
                if (marketChartView!= null) marketChartView.loadChart(currentSymbol, currentInterval);
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
                        double basePerBtc = getFiatPerBtc("USD");
                        if (fiatPerBtc == 0d || basePerBtc == 0d) return;
                        double usdToFiat = fiatPerBtc / basePerBtc;
                        double priceInFiat = price * usdToFiat;
                        mainHandler.post(() -> {
                            if (textCurrentPrice!= null) {
                                String symbol = getCurrencySymbol(currentFiatCode);
                                textCurrentPrice.setText(String.format(Locale.US, "%s%,.2f", symbol, priceInFiat));
                                int color;
                                if (lastDisplayPrice == 0f) color = getThemeColor(android.R.attr.textColorPrimary);
                                else if (priceInFiat > lastDisplayPrice) color = res.getColor(R.color.chart_bull);
                                else if (priceInFiat < lastDisplayPrice) color = res.getColor(R.color.chart_bear);
                                else color = res.getColor(R.color.chart_last_price_line);
                                textCurrentPrice.setTextColor(color);
                                lastDisplayPrice = (float) priceInFiat;
                            }
                        });
                    }).start();
                });
            }
            @Override
            public void onTickerUpdate(float high24h, float low24h, float volBtc, float volUsdt, float changePercent) {
                runOnUiThread(() -> {
                    if (textHigh24h!= null) textHigh24h.setText(String.format(Locale.US, "24h High %.2f", high24h));
                    if (textLow24h!= null) textLow24h.setText(String.format(Locale.US, "24h Low %.2f", low24h));
                    if (textVol24h!= null) textVol24h.setText(String.format(Locale.US, "Vol(BTC) %.2f Vol(USDT) %.2fM", volBtc, volUsdt / 1000000f));
                    if (textChange24h!= null) {
                        textChange24h.setText(String.format(Locale.US, "%.2f%%", changePercent));
                        int c = changePercent >= 0? res.getColor(R.color.chart_bull) : res.getColor(R.color.chart_bear);
                        textChange24h.setTextColor(c);
                    }
                });
            }
            @Override
            public void onMaUpdate(float ma7, float ma25, float ma99) {
                runOnUiThread(() -> {
                    if (textMaLabel!= null) {
                        if (ma7 == 0f) {
                            textMaLabel.setText("MA(7): -- MA(25): -- MA(99): --");
                        } else {
                            int colorMa7 = res.getColor(R.color.chart_ma5);
                            int colorMa25 = res.getColor(R.color.chart_ma10);
                            int colorMa99 = res.getColor(R.color.chart_ma20);
                            String s7 = String.format(Locale.US, "MA7: %.2f ", ma7);
                            String s25 = String.format(Locale.US, "MA25: %.2f ", ma25);
                            String s99 = String.format(Locale.US, "MA99: %.2f", ma99);
                            SpannableStringBuilder sb = new SpannableStringBuilder();
                            int start = 0;
                            sb.append(s7);
                            sb.setSpan(new ForegroundColorSpan(colorMa7), start, start + s7.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            start += s7.length();
                            sb.append(s25);
                            sb.setSpan(new ForegroundColorSpan(colorMa25), start, start + s25.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            start += s25.length();
                            sb.append(s99);
                            sb.setSpan(new ForegroundColorSpan(colorMa99), start, start + s99.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            textMaLabel.setText(sb);
                        }
                    }
                });
            }
            @Override
            public void onCountdownUpdate(String countdown) {
                runOnUiThread(() -> {
                    if (textCountdown!= null) textCountdown.setText("Close in: " + countdown);
                });
            }
            @Override
            public void onCandleSelected(MarketChartView.Candle candle) {
                runOnUiThread(() -> {
                    if (popupCandleDetail == null || candle == null) return;
                    popupCandleDetail.setVisibility(View.VISIBLE);
                    GradientDrawable bg = new GradientDrawable();
                    bg.setColor(getThemeColor(android.R.attr.colorBackground));
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
