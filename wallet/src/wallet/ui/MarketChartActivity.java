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

import org.bitcoinj.base.Coin;

import java.text.SimpleDateFormat;
import java.util.Currency;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import wallet.Configuration;
import wallet.R;
import wallet.WalletApplication;
import wallet.exchangerate.ExchangeRateDao;
import wallet.exchangerate.ExchangeRateEntry;
import wallet.exchangerate.ExchangeRatesRepository;

public class MarketChartActivity extends Activity
{
    private MarketChartView marketChartView;
    private TextView textTitle;
    private TextView textCurrentPrice;
    private TextView textFiat;
    private TextView textCountdown;
    private TextView textHigh24h;
    private TextView textLow24h;
    private TextView textVolBtc;
    private TextView textVolFiat;
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

    private final String[] realIntervals =
    {
        "15m",
        "15m",
        "1h",
        "4h",
        "1d",
        "1M",
        "1M"
    };

    private final String[] allIntervals =
    {
        "1m",
        "3m",
        "5m",
        "15m",
        "30m",
        "1h",
        "2h",
        "4h",
        "6h",
        "12h",
        "1D",
        "1W",
        "1M"
    };

    private SimpleDateFormat fullTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    private ExchangeRateDao exchangeRateDao;
    private Configuration config;
    private SharedPreferences prefs;
    private SharedPreferences.OnSharedPreferenceChangeListener prefsListener;
    private String currentFiatCode = "USD";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private float lastDisplayPrice = 0f;

    private static final Map<String, String> FIAT_SYMBOLS = new HashMap<String, String>()
    {{
        put("USD", "$");
        put("EUR", "€");
        put("VND", "₫");
        put("GBP", "£");
        put("JPY", "¥");
        put("CNY", "¥");
        put("CHF", "Fr");
        put("AUD", "A$");
        put("CAD", "C$");
        put("SGD", "S$");
        put("HKD", "HK$");
        put("KRW", "₩");
        put("INR", "₹");
        put("RUB", "₽");
        put("TRY", "₺");
        put("BRL", "R$");
        put("RON", "lei");
        put("LEU", "lei");
        put("SEK", "kr");
        put("NOK", "kr");
        put("DKK", "kr");
        put("PLN", "zł");
        put("THB", "฿");
        put("PHP", "₱");
        put("CZK", "Kč");
        put("HUF", "Ft");
        put("ILS", "₪");
        put("ZAR", "R");
        put("MXN", "$");
        put("NZD", "NZ$");
        put("AED", "AED");
        put("AFN", "؋");
        put("ALL", "L");
        put("AMD", "֏");
        put("ANG", "ƒ");
        put("AOA", "Kz");
        put("ARS", "$");
        put("AWG", "ƒ");
        put("AZN", "₼");
        put("BAM", "KM");
        put("BBD", "$");
        put("BDT", "৳");
        put("BGN", "лв");
        put("BHD", ".د.ب");
        put("BIF", "FBu");
        put("BMD", "$");
        put("BND", "$");
        put("BOB", "Bs");
        put("BSD", "$");
        put("BTN", "Nu");
        put("BWP", "P");
        put("BYN", "Br");
        put("BZD", "BZ$");
        put("CDF", "FC");
        put("CLP", "$");
        put("COP", "$");
        put("CRC", "₡");
        put("CUP", "₱");
        put("CVE", "$");
        put("DJF", "Fdj");
        put("DOP", "RD$");
        put("DZD", "د.ج");
        put("EGP", "E£");
        put("ERN", "Nfk");
        put("ETB", "Br");
        put("FJD", "$");
        put("FKP", "£");
        put("GEL", "₾");
        put("GHS", "₵");
        put("GIP", "£");
        put("GMD", "D");
        put("GNF", "FG");
        put("GTQ", "Q");
        put("GYD", "$");
        put("HNL", "L");
        put("HRK", "kn");
        put("HTG", "G");
        put("IDR", "Rp");
        put("IQD", "ع.د");
        put("IRR", "﷼");
        put("JMD", "J$");
        put("JOD", "JD");
        put("KES", "KSh");
        put("KGS", "с");
        put("KHR", "៛");
        put("KMF", "CF");
        put("KPW", "₩");
        put("KWD", "KD");
        put("KYD", "$");
        put("KZT", "₸");
        put("LAK", "₭");
        put("LBP", "£");
        put("LKR", "Rs");
        put("LRD", "$");
        put("LSL", "M");
        put("LYD", "LD");
        put("MAD", "MAD");
        put("MDL", "L");
        put("MGA", "Ar");
        put("MKD", "ден");
        put("MMK", "K");
        put("MNT", "₮");
        put("MOP", "MOP$");
        put("MRU", "UM");
        put("MUR", "₨");
        put("MVR", "Rf");
        put("MWK", "MK");
        put("MYR", "RM");
        put("MZN", "MT");
        put("NAD", "$");
        put("NGN", "₦");
        put("NIO", "C$");
        put("NPR", "Rs");
        put("OMR", "﷼");
        put("PAB", "B/.");
        put("PEN", "S/");
        put("PGK", "K");
        put("PKR", "₨");
        put("PYG", "Gs");
        put("QAR", "﷼");
        put("RSD", "Дин");
        put("RWF", "RF");
        put("SAR", "﷼");
        put("SBD", "$");
        put("SCR", "₨");
        put("SDG", "ج.س.");
        put("SHP", "£");
        put("SLL", "Le");
        put("SOS", "S");
        put("SRD", "$");
        put("SSP", "£");
        put("STN", "Db");
        put("SVC", "$");
        put("SYP", "£E");
        put("SZL", "E");
        put("TJS", "SM");
        put("TMT", "T");
        put("TND", "د.ت");
        put("TOP", "T$");
        put("TTD", "TT$");
        put("TWD", "NT$");
        put("TZS", "TSh");
        put("UAH", "₴");
        put("UGX", "USh");
        put("UYU", "$U");
        put("UZS", "лв");
        put("VES", "Bs");
        put("VUV", "VT");
        put("WST", "WS$");
        put("XAF", "FCFA");
        put("XCD", "$");
        put("XOF", "CFA");
        put("XPF", "₣");
        put("YER", "﷼");
        put("ZMW", "ZK");
        put("ZWL", "$");
    }};

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_market_chart);

        marketChartView = findViewById(R.id.marketChartView);
        textTitle = findViewById(R.id.textMarketTitle);
        textCurrentPrice = findViewById(R.id.textCurrentPrice);
        textFiat = findViewById(R.id.textFiat);
        textCountdown = findViewById(R.id.textCountdown);
        textHigh24h = findViewById(R.id.textHigh24h);
        textLow24h = findViewById(R.id.textLow24h);
        textVolBtc = findViewById(R.id.textVolBtc);
        textVolFiat = findViewById(R.id.textVolFiat);
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

        if (getIntent()!= null && getIntent().hasExtra("symbol"))
        {
            currentSymbol = getIntent().getStringExtra("symbol");
        }

        WalletApplication application = (WalletApplication) getApplication();
        config = application.getConfiguration();
        prefs = application.getSharedPreferences("wallet_preferences", MODE_PRIVATE);
        exchangeRateDao = ExchangeRatesRepository.get(application).exchangeRateDao();

        currentFiatCode = config.getExchangeCurrencyCode();
        if (currentFiatCode == null)
        {
            currentFiatCode = "USD";
        }

        prefsListener = (sharedPreferences, key) ->
        {
            if (Configuration.PREFS_KEY_EXCHANGE_CURRENCY.equals(key))
            {
                String newCode = config.getExchangeCurrencyCode();
                if (newCode!= null)
                {
                    currentFiatCode = newCode;
                    loadFiatRate();
                }
            }
        };

        prefs.registerOnSharedPreferenceChangeListener(prefsListener);

        setupTimeframeChips();
        setupChartListener();

        if (marketChartView!= null)
        {
            marketChartView.loadChart(currentSymbol, currentInterval);
            marketChartView.setFiatCode(currentFiatCode);
        }

        loadFiatRate();
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        if (prefs!= null && prefsListener!= null)
        {
            prefs.unregisterOnSharedPreferenceChangeListener(prefsListener);
        }
    }

    private void loadFiatRate()
    {
        mainHandler.post(() ->
        {
            if (textFiat!= null)
            {
                textFiat.setText(currentFiatCode);
            }
            if (textTitle!= null)
            {
                textTitle.setText("BTC/" + currentFiatCode);
            }
            if (marketChartView!= null)
            {
                marketChartView.setFiatCode(currentFiatCode);
            }
        });
    }

    private double getFiatPerBtc(String fiatCode)
    {
        ExchangeRateEntry entry = exchangeRateDao.findByCurrencyCode(fiatCode);
        if (entry == null)
        {
            return 0d;
        }
        try
        {
            long rateFiat = entry.getRateFiat();
            long rateCoin = entry.getRateCoin();
            if (rateCoin == 0)
            {
                return 0d;
            }
            int fractionDigits;
            try
            {
                fractionDigits = Currency.getInstance(fiatCode).getDefaultFractionDigits();
                if (fractionDigits < 0)
                {
                    fractionDigits = 2;
                }
            }
            catch (Exception e)
            {
                fractionDigits = 2;
            }
            double fiatMajor = rateFiat / Math.pow(10, fractionDigits);
            double coinMajor = (double) rateCoin / Coin.COIN.value;
            if (coinMajor == 0d)
            {
                return 0d;
            }
            return fiatMajor / coinMajor;
        }
        catch (Exception e)
        {
            return 0d;
        }
    }

    private String getCurrencySymbol(String fiatCode)
    {
        try
        {
            if (FIAT_SYMBOLS.containsKey(fiatCode))
            {
                return FIAT_SYMBOLS.get(fiatCode);
            }
            Currency currency = Currency.getInstance(fiatCode);
            String sym = currency.getSymbol(Locale.US);
            if (sym.equals(fiatCode))
            {
                sym = currency.getSymbol();
            }
            if (sym.equals(fiatCode) || sym.length() > 6)
            {
                return fiatCode + " ";
            }
            return sym;
        }
        catch (Exception e)
        {
            return fiatCode + " ";
        }
    }

    private int getThemeColor(int attr)
    {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(attr, tv, true);
        if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT)
        {
            return tv.data;
        }
        else
        {
            try
            {
                return getResources().getColor(tv.resourceId, getTheme());
            }
            catch (Exception e)
            {
                return tv.data;
            }
        }
    }

    private void showMoreIntervalsDialog()
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.intervals_title);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        grid.setUseDefaultMargins(false);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        grid.setPadding(24, 24, 24, 24);
        grid.setBackgroundColor(getThemeColor(android.R.attr.colorBackground));

        Resources res = getResources();

        String[] intervalValues =
        {
            "1m",
            "3m",
            "5m",
            "15m",
            "30m",
            "1h",
            "2h",
            "4h",
            "6h",
            "12h",
            "1D",
            "1W",
            "1M"
        };

        int[] intervalLabels =
        {
            R.string.interval_1m,
            R.string.interval_3m,
            R.string.interval_5m,
            R.string.interval_15m,
            R.string.interval_30m,
            R.string.interval_1h,
            R.string.interval_2h,
            R.string.interval_4h,
            R.string.interval_6h,
            R.string.interval_12h,
            R.string.interval_1d,
            R.string.interval_1w,
            R.string.interval_1M
        };

        for (int i = 0; i < intervalValues.length; i++)
        {
            String interval = intervalValues[i];
            TextView tv = new TextView(this);
            tv.setText(intervalLabels[i]);
            tv.setTextSize(14f);
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setPadding(0, 28, 0, 28);
            tv.setTextColor(getThemeColor(android.R.attr.textColorPrimary));

            boolean isSelected = interval.equals(currentInterval) || (interval.equals("1D") && currentInterval.equals("1d"));
            if (isSelected)
            {
                tv.setBackgroundResource(R.drawable.bg_time_selected);
                tv.setTextColor(getThemeColor(android.R.attr.colorBackground));
            }
            else
            {
                tv.setBackgroundColor(res.getColor(android.R.color.transparent));
            }

            GridLayout.Spec rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            GridLayout.Spec colSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(rowSpec, colSpec);
            lp.width = 0;
            lp.height = GridLayout.LayoutParams.WRAP_CONTENT;
            lp.setGravity(android.view.Gravity.FILL);
            lp.setMargins(12, 12, 12, 12);
            tv.setLayoutParams(lp);

            final String intervalToLoad;
            if (interval.equals("1D"))
            {
                intervalToLoad = "1d";
            }
            else if (interval.equals("1W"))
            {
                intervalToLoad = "1w";
            }
            else
            {
                intervalToLoad = interval;
            }

            tv.setOnClickListener(v ->
            {
                currentInterval = intervalToLoad;
                if (marketChartView!= null)
                {
                    marketChartView.loadChart(currentSymbol, currentInterval);
                }
                setupTimeframeChips();
            });

            grid.addView(tv);
        }

        builder.setView(grid);
        builder.setNegativeButton(R.string.close, (dialog, which) ->
        {
            dialog.dismiss();
        });
        builder.show();
    }

    private void setupTimeframeChips()
    {
        if (chipGroupTimeframe == null)
        {
            return;
        }

        Resources res = getResources();
        chipGroupTimeframe.removeAllViews();

        int[] labelResIds =
        {
            R.string.time,
            R.string.interval_15m,
            R.string.interval_1h,
            R.string.interval_4h,
            R.string.interval_1d,
            R.string.interval_1M,
            R.string.more
        };

        String[] realValues =
        {
            "",
            "15m",
            "1h",
            "4h",
            "1d",
            "1M",
            ""
        };

        for (int idx = 0; idx < labelResIds.length; idx++)
        {
            int resId = labelResIds[idx];
            String realInterval = realValues[idx];
            String label = getString(resId);

            TextView tv = new TextView(this);
            tv.setText(label);
            tv.setTextSize(13f);
            int padH = (int) (16 * res.getDisplayMetrics().density);
            int padV = (int) (6 * res.getDisplayMetrics().density);
            tv.setPadding(padH, padV, padH, padV);

            boolean isSelected = false;
            if (resId == R.string.interval_15m && currentInterval.equals("15m"))
            {
                isSelected = true;
            }
            if (resId == R.string.interval_1h && currentInterval.equals("1h"))
            {
                isSelected = true;
            }
            if (resId == R.string.interval_4h && currentInterval.equals("4h"))
            {
                isSelected = true;
            }
            if (resId == R.string.interval_1d && (currentInterval.equals("1d") || currentInterval.equals("1D")))
            {
                isSelected = true;
            }
            if (resId == R.string.interval_1M && currentInterval.equals("1M"))
            {
                isSelected = true;
            }

            if (isSelected)
            {
                tv.setTextColor(getThemeColor(android.R.attr.colorBackground));
                tv.setBackgroundResource(R.drawable.bg_time_selected);
            }
            else
            {
                tv.setTextColor(getThemeColor(android.R.attr.textColorPrimary));
                tv.setBackgroundColor(res.getColor(android.R.color.transparent));
            }

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(8, 0, 8, 0);
            tv.setLayoutParams(lp);

            final String intervalToLoad = realInterval;
            final int finalResId = resId;

            tv.setOnClickListener(v ->
            {
                if (finalResId == R.string.time || finalResId == R.string.more)
                {
                    showMoreIntervalsDialog();
                    return;
                }

                currentInterval = intervalToLoad;

                for (int i = 0; i < chipGroupTimeframe.getChildCount(); i++)
                {
                    View child = chipGroupTimeframe.getChildAt(i);
                    if (child instanceof TextView)
                    {
                        TextView t = (TextView) child;
                        int childResId = labelResIds[i];
                        if (childResId == finalResId)
                        {
                            t.setTextColor(getThemeColor(android.R.attr.colorBackground));
                            t.setBackgroundResource(R.drawable.bg_time_selected);
                        }
                        else
                        {
                            t.setTextColor(getThemeColor(android.R.attr.textColorPrimary));
                            t.setBackgroundColor(res.getColor(android.R.color.transparent));
                        }
                    }
                }

                if (marketChartView!= null)
                {
                    marketChartView.loadChart(currentSymbol, currentInterval);
                }
            });

            chipGroupTimeframe.addView(tv);
        }
    }

    private void setupChartListener()
    {
        if (marketChartView == null)
        {
            return;
        }

        Resources res = getResources();

        marketChartView.setOnChartUpdateListener(new MarketChartView.OnChartUpdateListener()
        {
            @Override
            public void onPriceUpdate(float price, float high24h, float low24h)
            {
                runOnUiThread(() ->
                {
                    new Thread(() ->
                    {
                        double fiatPerBtc = getFiatPerBtc(currentFiatCode);
                        double basePerBtc = getFiatPerBtc("USD");
                        if (fiatPerBtc == 0d || basePerBtc == 0d)
                        {
                            return;
                        }
                        double usdToFiat = fiatPerBtc / basePerBtc;
                        double priceInFiat = price * usdToFiat;

                        mainHandler.post(() ->
                        {
                            if (textCurrentPrice!= null)
                            {
                                String symbol = getCurrencySymbol(currentFiatCode);
                                textCurrentPrice.setText(String.format(Locale.US, "%s%,.2f", symbol, priceInFiat));

                                int color;
                                if (lastDisplayPrice == 0f)
                                {
                                    color = getThemeColor(android.R.attr.textColorPrimary);
                                }
                                else if (priceInFiat > lastDisplayPrice)
                                {
                                    color = res.getColor(R.color.chart_bull);
                                }
                                else if (priceInFiat < lastDisplayPrice)
                                {
                                    color = res.getColor(R.color.chart_bear);
                                }
                                else
                                {
                                    color = res.getColor(R.color.chart_last_price_line);
                                }

                                textCurrentPrice.setTextColor(color);
                                lastDisplayPrice = (float) priceInFiat;
                            }
                        });
                    }).start();
                });
            }

            @Override
            public void onTickerUpdate(float high24h, float low24h, float volBtc, float volUsdt, float changePercent)
            {
                runOnUiThread(() ->
                {
                    if (textHigh24h!= null)
                    {
                        textHigh24h.setText(getString(R.string.chart_high_label, String.format(Locale.US, "%.2f", high24h)));
                    }

                    if (textLow24h!= null)
                    {
                        textLow24h.setText(getString(R.string.chart_low_label, String.format(Locale.US, "%.2f", low24h)));
                    }

                    if (textChange24h!= null)
                    {
                        textChange24h.setText(String.format(Locale.US, "%.2f%%", changePercent));
                        int c;
                        if (changePercent >= 0)
                        {
                            c = res.getColor(R.color.chart_bull);
                        }
                        else
                        {
                            c = res.getColor(R.color.chart_bear);
                        }
                        textChange24h.setTextColor(c);
                    }

                    new Thread(() ->
                    {
                        double fiatPerBtc = getFiatPerBtc(currentFiatCode);
                        double basePerBtc = getFiatPerBtc("USD");
                        if (fiatPerBtc == 0d || basePerBtc == 0d)
                        {
                            return;
                        }
                        double usdToFiat = fiatPerBtc / basePerBtc;
                        double volFiat = volUsdt * usdToFiat;

                        String baseAsset = currentSymbol;
                        if (baseAsset.endsWith("USDT"))
                        {
                            baseAsset = baseAsset.substring(0, baseAsset.length() - 4);
                        }
                        else if (baseAsset.endsWith("BUSD"))
                        {
                            baseAsset = baseAsset.substring(0, baseAsset.length() - 4);
                        }
                        else if (baseAsset.length() > 3)
                        {
                            baseAsset = baseAsset.substring(0, 3);
                        }

                        String volBtcStr = getString(R.string.chart_vol_base_format, baseAsset, String.format(Locale.US, "%.2f", volBtc));
                        String volFiatStr;

                        if (volFiat >= 1_000_000_000)
                        {
                            volFiatStr = getString(R.string.chart_vol_quote_format, currentFiatCode, String.format(Locale.US, "%.2fB", volFiat / 1_000_000_000));
                        }
                        else if (volFiat >= 1_000_000)
                        {
                            volFiatStr = getString(R.string.chart_vol_quote_format, currentFiatCode, String.format(Locale.US, "%.2fM", volFiat / 1_000_000));
                        }
                        else
                        {
                            volFiatStr = getString(R.string.chart_vol_quote_format, currentFiatCode, String.format(Locale.US, "%.2f", volFiat));
                        }

                        mainHandler.post(() ->
                        {
                            if (textVolBtc!= null)
                            {
                                textVolBtc.setText(volBtcStr);
                            }
                            if (textVolFiat!= null)
                            {
                                textVolFiat.setText(volFiatStr);
                            }
                        });
                    }).start();
                });
            }

            @Override
            public void onMaUpdate(float ma7, float ma25, float ma99)
            {
                runOnUiThread(() ->
                {
                    if (textMaLabel!= null)
                    {
                        if (ma7 == 0f)
                        {
                            textMaLabel.setText(getString(R.string.chart_ma_default));
                        }
                        else
                        {
                            int colorMa7 = res.getColor(R.color.chart_ma5);
                            int colorMa25 = res.getColor(R.color.chart_ma10);
                            int colorMa99 = res.getColor(R.color.chart_ma20);

                            String s7 = String.format(Locale.US, "MA7: %.2f", ma7);
                            String sep = " • ";
                            String s25 = String.format(Locale.US, "MA25: %.2f", ma25);
                            String s99 = String.format(Locale.US, "MA99: %.2f", ma99);

                            SpannableStringBuilder sb = new SpannableStringBuilder();
                            int start = 0;

                            sb.append(s7);
                            sb.setSpan(new ForegroundColorSpan(colorMa7), start, start + s7.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            start += s7.length();
                            sb.append(sep);
                            start += sep.length();
                            sb.append(s25);
                            sb.setSpan(new ForegroundColorSpan(colorMa25), start, start + s25.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            start += s25.length();
                            sb.append(sep);
                            start += sep.length();
                            sb.append(s99);
                            sb.setSpan(new ForegroundColorSpan(colorMa99), start, start + s99.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                            textMaLabel.setText(sb);
                        }
                    }
                });
            }

            @Override
            public void onCountdownUpdate(String countdown)
            {
                runOnUiThread(() ->
                {
                    if (textCountdown!= null)
                    {
                        textCountdown.setText(getString(R.string.chart_close_in, countdown));
                    }
                    if (marketChartView!= null)
                    {
                        marketChartView.setCountdown(countdown);
                    }
                });
            }

            @Override
            public void onCandleSelected(MarketChartView.Candle candle)
            {
                runOnUiThread(() ->
                {
                    if (popupCandleDetail == null || candle == null)
                    {
                        return;
                    }

                    popupCandleDetail.setVisibility(View.VISIBLE);

                    GradientDrawable bg = new GradientDrawable();
                    bg.setColor(getThemeColor(android.R.attr.colorBackground));
                    bg.setCornerRadius(12f * res.getDisplayMetrics().density);
                    bg.setStroke((int) (1 * res.getDisplayMetrics().density), res.getColor(R.color.chart_grid));
                    popupCandleDetail.setBackground(bg);
                    popupCandleDetail.setElevation(8f * res.getDisplayMetrics().density);

                    if (popupTime!= null)
                    {
                        popupTime.setText(fullTimeFormat.format(new Date(candle.openTime)));
                    }
                    if (popupOpen!= null)
                    {
                        popupOpen.setText(getString(R.string.chart_open_label, String.format(Locale.US, "%.2f", candle.open)));
                    }
                    if (popupHigh!= null)
                    {
                        popupHigh.setText(getString(R.string.chart_high_detail, String.format(Locale.US, "%.2f", candle.high)));
                    }
                    if (popupLow!= null)
                    {
                        popupLow.setText(getString(R.string.chart_low_detail, String.format(Locale.US, "%.2f", candle.low)));
                    }
                    if (popupClose!= null)
                    {
                        popupClose.setText(getString(R.string.chart_close_label, String.format(Locale.US, "%.2f", candle.close)));
                    }
                    if (popupVolume!= null)
                    {
                        popupVolume.setText(getString(R.string.chart_volume_label, String.format(Locale.US, "%.2f", candle.volume)));
                    }
                });
            }

            @Override
            public void onNothingSelected()
            {
                runOnUiThread(() ->
                {
                    if (popupCandleDetail!= null)
                    {
                        popupCandleDetail.setVisibility(View.GONE);
                    }
                });
            }
        });
    }
}
