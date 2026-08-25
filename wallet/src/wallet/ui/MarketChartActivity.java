package wallet.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.bitcoinj.base.Coin;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Currency;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
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
    private View btnChartSettings;

    private String currentSymbol = "BTCUSDT";
    private String currentInterval = "15m";

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
        put("KRW", "₩");
        put("INR", "₹");
        put("RUB", "₽");
        put("TRY", "₺");
        put("UAH", "₴");
        put("THB", "฿");
        put("PHP", "₱");
        put("ILS", "₪");
        put("PLN", "zł");
        put("RON", "lei");
        put("LEU", "lei");
        put("BGN", "лв");
        put("CZK", "Kč");
        put("DKK", "kr");
        put("SEK", "kr");
        put("NOK", "kr");
        put("HUF", "Ft");
        put("CHF", "CHF");
        put("AUD", "A$");
        put("CAD", "C$");
        put("NZD", "NZ$");
        put("SGD", "S$");
        put("HKD", "HK$");
        put("TWD", "NT$");
        put("MYR", "RM");
        put("IDR", "Rp");
        put("BRL", "R$");
        put("MXN", "$");
        put("ARS", "$");
        put("CLP", "$");
        put("COP", "$");
        put("PEN", "S/");
        put("UYU", "$U");
        put("BOB", "Bs");
        put("PYG", "₲");
        put("ZAR", "R");
        put("EGP", "E£");
        put("NGN", "₦");
        put("KES", "KSh");
        put("GHS", "₵");
        put("MAD", "DH");
        put("TND", "DT");
        put("DZD", "DA");
        put("AED", "AED");
        put("SAR", "﷼");
        put("QAR", "QR");
        put("KWD", "KD");
        put("BHD", "BD");
        put("OMR", "﷼");
        put("JOD", "JD");
        put("LBP", "L£");
        put("PKR", "₨");
        put("BDT", "৳");
        put("LKR", "Rs");
        put("NPR", "₨");
        put("MMK", "K");
        put("KHR", "៛");
        put("LAK", "₭");
        put("MNT", "₮");
        put("KZT", "₸");
        put("UZS", "soʻm");
        put("GEL", "₾");
        put("AZN", "₼");
        put("AMD", "֏");
        put("BYN", "Br");
        put("MDL", "L");
        put("HRK", "kn");
        put("RSD", "din");
        put("BAM", "KM");
        put("MKD", "den");
        put("ALL", "L");
        put("ISK", "kr");
        put("AFN", "؋");
        put("IRR", "﷼");
        put("IQD", "ع.د");
        put("SYP", "£");
        put("YER", "﷼");
        put("LYD", "LD");
        put("SDG", "SDG");
        put("ETB", "Br");
        put("TZS", "TSh");
        put("UGX", "USh");
        put("RWF", "FRw");
        put("BIF", "FBu");
        put("MUR", "₨");
        put("SCR", "₨");
        put("MZN", "MT");
        put("AOA", "Kz");
        put("BWP", "P");
        put("NAD", "N$");
        put("ZMW", "ZK");
        put("ZWL", "Z$");
        put("GMD", "D");
        put("SLL", "Le");
        put("LRD", "L$");
        put("GNF", "FG");
        put("XOF", "CFA");
        put("XAF", "FCFA");
        put("XPF", "₣");
        put("CDF", "FC");
        put("DJF", "Fdj");
        put("KMF", "CF");
        put("MGA", "Ar");
        put("MWK", "MK");
        put("LSL", "L");
        put("SZL", "L");
        put("GIP", "£");
        put("FKP", "£");
        put("SHP", "£");
        put("JMD", "J$");
        put("BBD", "Bds$");
        put("TTD", "TT$");
        put("BSD", "B$");
        put("BZD", "BZ$");
        put("GTQ", "Q");
        put("HNL", "L");
        put("NIO", "C$");
        put("CRC", "₡");
        put("PAB", "B/.");
        put("DOP", "RD$");
        put("HTG", "G");
        put("CUP", "$");
        put("CUC", "$");
        put("VES", "Bs.S");
        put("GYD", "G$");
        put("SRD", "$");
        put("FJD", "FJ$");
        put("PGK", "K");
        put("SBD", "SI$");
        put("VUV", "VT");
        put("WST", "WS$");
        put("TOP", "T$");
        put("MOP", "MOP$");
        put("BND", "B$");
        put("BTN", "Nu.");
        put("MVR", "Rf");
        put("KGS", "с");
        put("TJS", "SM");
        put("TMT", "m");
    }};

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_market_chart);

        marketChartView = findViewById(R.id.marketChartView);
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
        btnChartSettings = findViewById(R.id.btnChartSettings);

        if (btnChartSettings!= null)
        {
            btnChartSettings.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    showChartSettingsPopup();
                }
            });
        }

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

        prefsListener = new SharedPreferences.OnSharedPreferenceChangeListener()
        {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
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
            }
        };

        prefs.registerOnSharedPreferenceChangeListener(prefsListener);

        setupTimeframeChips();
        setupChartListener();

        if (marketChartView!= null)
        {
            marketChartView.loadChart(currentSymbol, currentInterval);
        }

        loadFiatRate();
    }


    private void showMaSettingsPopup()
    {
        showChartSettingsPopup();
    }

    private void showChartSettingsPopup()
    {
        if (marketChartView == null)
        {
            return;
        }

        final Dialog dialog = new Dialog(this);
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scrollView.setLayoutParams(scrollLp);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);
        GradientDrawable rootBg = new GradientDrawable();
        rootBg.setCornerRadius(24f);
        rootBg.setColor(getResources().getColor(R.color.chart_bg, getTheme()));
        root.setBackground(rootBg);

        TextView title = new TextView(this);
        title.setText(getString(R.string.chart_settings_title));
        title.setTextSize(18f);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, 0, 0, 24);
        root.addView(title);

        final int[] candlePalette = getResources().getIntArray(R.array.candle_color_palette);
        final int[] curBull = {marketChartView.getBullishColor()};
        final int[] curBear = {marketChartView.getBearishColor()};

        // ===================== CANDLE SECTION =====================
        LinearLayout candleHeader = new LinearLayout(this);
        candleHeader.setOrientation(LinearLayout.HORIZONTAL);
        candleHeader.setGravity(Gravity.CENTER_VERTICAL);
        candleHeader.setPadding(0, 16, 0, 16);
        candleHeader.setClickable(true);
        candleHeader.setFocusable(true);
        TypedValue outValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        candleHeader.setBackgroundResource(outValue.resourceId);

        final TextView titleCandle = new TextView(this);
        titleCandle.setText("\u25B6 " + getString(R.string.chart_settings_candle));
        titleCandle.setTextSize(14f);
        titleCandle.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams lpTitleCandle = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleCandle.setLayoutParams(lpTitleCandle);

        TextView arrowCandle = new TextView(this);
        arrowCandle.setText("\u25BC");
        arrowCandle.setTextSize(12f);

        candleHeader.addView(titleCandle);
        candleHeader.addView(arrowCandle);
        root.addView(candleHeader);

        final LinearLayout containerCandle = new LinearLayout(this);
        containerCandle.setOrientation(LinearLayout.VERTICAL);
        containerCandle.setVisibility(View.GONE);

        LinearLayout rowBull = new LinearLayout(this);
        rowBull.setOrientation(LinearLayout.HORIZONTAL);
        rowBull.setGravity(Gravity.CENTER_VERTICAL);
        rowBull.setPadding(0, 8, 0, 8);
        TextView lbBull = new TextView(this);
        lbBull.setText(getString(R.string.chart_settings_bullish));
        lbBull.setTextSize(13f);
        lbBull.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final View viewBull = new View(this);
        viewBull.setLayoutParams(new LinearLayout.LayoutParams(48, 48));
        GradientDrawable gdBull = new GradientDrawable();
        gdBull.setCornerRadius(8f);
        gdBull.setColor(curBull[0]);
        viewBull.setBackground(gdBull);
        rowBull.addView(lbBull);
        rowBull.addView(viewBull);
        containerCandle.addView(rowBull);

        LinearLayout rowBear = new LinearLayout(this);
        rowBear.setOrientation(LinearLayout.HORIZONTAL);
        rowBear.setGravity(Gravity.CENTER_VERTICAL);
        rowBear.setPadding(0, 8, 0, 8);
        TextView lbBear = new TextView(this);
        lbBear.setText(getString(R.string.chart_settings_bearish));
        lbBear.setTextSize(13f);
        lbBear.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final View viewBear = new View(this);
        viewBear.setLayoutParams(new LinearLayout.LayoutParams(48, 48));
        GradientDrawable gdBear = new GradientDrawable();
        gdBear.setCornerRadius(8f);
        gdBear.setColor(curBear[0]);
        viewBear.setBackground(gdBear);
        rowBear.addView(lbBear);
        rowBear.addView(viewBear);
        containerCandle.addView(rowBear);

        final int[] bullIdx = {0};
        final int[] bearIdx = {0};
        // init idx
        for (int i = 0; i < candlePalette.length; i++) { if (candlePalette[i] == curBull[0]) { bullIdx[0]=i; break; } }
        for (int i = 0; i < candlePalette.length; i++) { if (candlePalette[i] == curBear[0]) { bearIdx[0]=i; break; } }

        viewBull.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                bullIdx[0] = (bullIdx[0] + 1) % candlePalette.length;
                int next = candlePalette[bullIdx[0]];
                curBull[0] = next;
                GradientDrawable gd = new GradientDrawable();
                gd.setCornerRadius(8f);
                gd.setColor(next);
                v.setBackground(gd);
            }
        });

        viewBear.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                bearIdx[0] = (bearIdx[0] + 1) % candlePalette.length;
                int next = candlePalette[bearIdx[0]];
                curBear[0] = next;
                GradientDrawable gd = new GradientDrawable();
                gd.setCornerRadius(8f);
                gd.setColor(next);
                v.setBackground(gd);
            }
        });

        root.addView(containerCandle);

        View divider1 = new View(this);
        LinearLayout.LayoutParams lpDiv1 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (1 * getResources().getDisplayMetrics().density));
        lpDiv1.topMargin = 16;
        lpDiv1.bottomMargin = 16;
        divider1.setLayoutParams(lpDiv1);
        divider1.setBackgroundColor(getResources().getColor(R.color.chart_grid, getTheme()));
        root.addView(divider1);

        // ===================== MA SECTION =====================
        LinearLayout maHeader = new LinearLayout(this);
        maHeader.setOrientation(LinearLayout.HORIZONTAL);
        maHeader.setGravity(Gravity.CENTER_VERTICAL);
        maHeader.setPadding(0, 16, 0, 16);
        maHeader.setClickable(true);
        maHeader.setFocusable(true);
        maHeader.setBackgroundResource(outValue.resourceId);

        final TextView titleMa = new TextView(this);
        titleMa.setText("\u25B6 " + getString(R.string.chart_settings_ma));
        titleMa.setTextSize(14f);
        titleMa.setTypeface(null, Typeface.BOLD);
        titleMa.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView arrowMa = new TextView(this);
        arrowMa.setText("\u25BC");
        arrowMa.setTextSize(12f);

        maHeader.addView(titleMa);
        maHeader.addView(arrowMa);
        root.addView(maHeader);

        final LinearLayout containerMa = new LinearLayout(this);
        containerMa.setOrientation(LinearLayout.VERTICAL);
        containerMa.setVisibility(View.GONE);

        View maView = getLayoutInflater().inflate(R.layout.bottom_sheet_ma_settings, null);
        final RecyclerView recycler = maView.findViewById(R.id.recycler_ma_popup);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setNestedScrollingEnabled(false);

        final List<MarketChartView.MaLine> tempList = new ArrayList<MarketChartView.MaLine>();
        List<MarketChartView.MaLine> origLines = marketChartView.getMaLines();
        for (int i = 0; i < origLines.size(); i++)
        {
            MarketChartView.MaLine o = origLines.get(i);
            tempList.add(new MarketChartView.MaLine(o.period, o.color));
        }
        final MaPopupAdapter adapter = new MaPopupAdapter(tempList);
        recycler.setAdapter(adapter);

        View btnAdd = maView.findViewById(R.id.btn_add_ma);
        GradientDrawable addBg = new GradientDrawable();
        addBg.setCornerRadius(12f);
        addBg.setColor(getResources().getColor(R.color.bg_level2, getTheme()));
        btnAdd.setBackground(addBg);
        btnAdd.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (tempList.size() >= 6)
                {
                    Toast.makeText(v.getContext(), getString(R.string.max_ma_reached), Toast.LENGTH_SHORT).show();
                    return;
                }
                int[] colors = getResources().getIntArray(R.array.ma_default_colors);
                int color = colors[tempList.size() % colors.length];
                tempList.add(new MarketChartView.MaLine(20, color));
                adapter.notifyDataSetChanged();
            }
        });

        View btnApplyOld = maView.findViewById(R.id.btn_apply);
        if (btnApplyOld != null)
        {
            btnApplyOld.setVisibility(View.GONE);
        }

        containerMa.addView(maView);
        root.addView(containerMa);

        View divider2 = new View(this);
        LinearLayout.LayoutParams lpDiv2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (1 * getResources().getDisplayMetrics().density));
        lpDiv2.topMargin = 16;
        lpDiv2.bottomMargin = 16;
        divider2.setLayoutParams(lpDiv2);
        divider2.setBackgroundColor(getResources().getColor(R.color.chart_grid, getTheme()));
        root.addView(divider2);

        // ===================== CHART OPTIONS SECTION =====================
        LinearLayout chartHeader = new LinearLayout(this);
        chartHeader.setOrientation(LinearLayout.HORIZONTAL);
        chartHeader.setGravity(Gravity.CENTER_VERTICAL);
        chartHeader.setPadding(0, 16, 0, 16);
        chartHeader.setClickable(true);
        chartHeader.setFocusable(true);
        chartHeader.setBackgroundResource(outValue.resourceId);

        final TextView titleChart = new TextView(this);
        titleChart.setText("\u25B6 " + getString(R.string.chart_settings_chart_options));
        titleChart.setTextSize(14f);
        titleChart.setTypeface(null, Typeface.BOLD);
        titleChart.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView arrowChart = new TextView(this);
        arrowChart.setText("\u25BC");
        arrowChart.setTextSize(12f);

        chartHeader.addView(titleChart);
        chartHeader.addView(arrowChart);
        root.addView(chartHeader);

        final LinearLayout containerChart = new LinearLayout(this);
        containerChart.setOrientation(LinearLayout.VERTICAL);
        containerChart.setVisibility(View.GONE);
        containerChart.setPadding(0, 8, 0, 8);

        // Body fraction
        TextView lbBody = new TextView(this);
        lbBody.setText(getString(R.string.chart_body_width, String.valueOf(marketChartView.getBodyWidthFraction())));
        lbBody.setTextSize(12f);
        containerChart.addView(lbBody);

        android.widget.SeekBar sbBody = new android.widget.SeekBar(this);
        sbBody.setMax(70);
        sbBody.setProgress((int) ((marketChartView.getBodyWidthFraction() - 0.3f) * 100));
        containerChart.addView(sbBody);

        // Wick width
        final float[] curWick = {marketChartView.getWickWidthPx() > 0 ? marketChartView.getWickWidthPx() : getResources().getDimension(R.dimen.chart_wick_width)};
        TextView lbWick = new TextView(this);
        lbWick.setText(getString(R.string.chart_wick_width, (int)curWick[0]));
        lbWick.setTextSize(12f);
        containerChart.addView(lbWick);

        android.widget.SeekBar sbWick = new android.widget.SeekBar(this);
        sbWick.setMax(20);
        sbWick.setProgress((int) curWick[0]);
        containerChart.addView(sbWick);

        // MA width
        final float[] curMaW = {marketChartView.getMaLineWidthPx() > 0 ? marketChartView.getMaLineWidthPx() : getResources().getDimension(R.dimen.chart_ma_line_width)};
        TextView lbMaW = new TextView(this);
        lbMaW.setText(getString(R.string.chart_ma_line_width, (int)curMaW[0]));
        lbMaW.setTextSize(12f);
        containerChart.addView(lbMaW);

        android.widget.SeekBar sbMaW = new android.widget.SeekBar(this);
        sbMaW.setMax(20);
        sbMaW.setProgress((int) curMaW[0]);
        containerChart.addView(sbMaW);

        // Show Grid Switch
        LinearLayout rowGrid = new LinearLayout(this);
        rowGrid.setOrientation(LinearLayout.HORIZONTAL);
        rowGrid.setGravity(Gravity.CENTER_VERTICAL);
        TextView lbGrid = new TextView(this);
        lbGrid.setText(getString(R.string.chart_show_grid));
        lbGrid.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final android.widget.Switch swGrid = new android.widget.Switch(this);
        swGrid.setChecked(marketChartView.isShowGrid());
        rowGrid.addView(lbGrid);
        rowGrid.addView(swGrid);
        containerChart.addView(rowGrid);

        // Show Volume Switch
        LinearLayout rowVol = new LinearLayout(this);
        rowVol.setOrientation(LinearLayout.HORIZONTAL);
        rowVol.setGravity(Gravity.CENTER_VERTICAL);
        TextView lbVol = new TextView(this);
        lbVol.setText(getString(R.string.chart_show_volume));
        lbVol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final android.widget.Switch swVol = new android.widget.Switch(this);
        swVol.setChecked(marketChartView.isShowVolume());
        rowVol.addView(lbVol);
        rowVol.addView(swVol);
        containerChart.addView(rowVol);

        // Visible count
        TextView lbVis = new TextView(this);
        lbVis.setText(getString(R.string.chart_visible_candles, marketChartView.getVisibleCandleCountValue()));
        lbVis.setTextSize(12f);
        containerChart.addView(lbVis);

        android.widget.SeekBar sbVis = new android.widget.SeekBar(this);
        sbVis.setMax(130);
        sbVis.setProgress(marketChartView.getVisibleCandleCountValue() - 20);
        containerChart.addView(sbVis);

        sbBody.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener()
        {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser)
            {
                float fraction = 0.3f + progress / 100f;
                lbBody.setText(getString(R.string.chart_body_width, String.format(Locale.US, "%.2f", fraction)));
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });

        sbWick.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener()
        {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser)
            {
                if (progress < 1) progress = 1;
                curWick[0] = progress;
                lbWick.setText(getString(R.string.chart_wick_width, progress));
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });

        sbMaW.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener()
        {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser)
            {
                if (progress < 1) progress = 1;
                curMaW[0] = progress;
                lbMaW.setText(getString(R.string.chart_ma_line_width, progress));
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });

        sbVis.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener()
        {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser)
            {
                int count = 20 + progress;
                lbVis.setText(getString(R.string.chart_visible_candles, count));
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });


        // === LAST PRICE LINE SETTINGS ===
        TextView lbLastPrice = new TextView(this);
        lbLastPrice.setText(getString(R.string.chart_last_price_section));
        lbLastPrice.setTextSize(12f);
        lbLastPrice.setTypeface(null, Typeface.BOLD);
        lbLastPrice.setPadding(0,16,0,8);
        containerChart.addView(lbLastPrice);

        LinearLayout rowLast = new LinearLayout(this);
        rowLast.setOrientation(LinearLayout.HORIZONTAL);
        rowLast.setGravity(Gravity.CENTER_VERTICAL);
        TextView lbShowLast = new TextView(this);
        lbShowLast.setText(getString(R.string.chart_show_volume).replace("Volume","Last Price"));
        lbShowLast.setText(getString(R.string.chart_show_last_price));
        lbShowLast.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final android.widget.Switch swLast = new android.widget.Switch(this);
        swLast.setChecked(marketChartView.isShowLastPriceLine());
        rowLast.addView(lbShowLast);
        rowLast.addView(swLast);
        containerChart.addView(rowLast);

        LinearLayout rowLastColor = new LinearLayout(this);
        rowLastColor.setOrientation(LinearLayout.HORIZONTAL);
        rowLastColor.setGravity(Gravity.CENTER_VERTICAL);
        rowLastColor.setPadding(0,8,0,8);
        TextView lbLastColor = new TextView(this);
        lbLastColor.setText(getString(R.string.chart_last_price_color));
        lbLastColor.setTextSize(13f);
        lbLastColor.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final View viewLastColor = new View(this);
        viewLastColor.setLayoutParams(new LinearLayout.LayoutParams(48,48));
        GradientDrawable gdLast = new GradientDrawable(); gdLast.setCornerRadius(8f); gdLast.setColor(marketChartView.getLastPriceLineColor());
        viewLastColor.setBackground(gdLast);
        rowLastColor.addView(lbLastColor);
        rowLastColor.addView(viewLastColor);
        containerChart.addView(rowLastColor);

        // Price text size
        TextView lbTxtSize = new TextView(this);
        final float[] curTxtSize = {marketChartView.getPriceTextSizePx() > 0 ? marketChartView.getPriceTextSizePx() : getResources().getDimension(R.dimen.chart_text_size)};
        lbTxtSize.setText(getString(R.string.chart_price_text_size, (int)curTxtSize[0]));
        lbTxtSize.setTextSize(12f);
        containerChart.addView(lbTxtSize);
        android.widget.SeekBar sbTxtSize = new android.widget.SeekBar(this);
        sbTxtSize.setMax(30);
        sbTxtSize.setProgress((int)curTxtSize[0]);
        containerChart.addView(sbTxtSize);

        // Grid color
        LinearLayout rowGridColor = new LinearLayout(this);
        rowGridColor.setOrientation(LinearLayout.HORIZONTAL);
        rowGridColor.setGravity(Gravity.CENTER_VERTICAL);
        rowGridColor.setPadding(0,8,0,8);
        TextView lbGridColor = new TextView(this);
        lbGridColor.setText(getString(R.string.chart_grid_color));
        lbGridColor.setTextSize(13f);
        lbGridColor.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final View viewGridColor = new View(this);
        viewGridColor.setLayoutParams(new LinearLayout.LayoutParams(48,48));
        GradientDrawable gdGrid = new GradientDrawable(); gdGrid.setCornerRadius(8f);
        int curGrid = marketChartView.getGridColor() != -1 ? marketChartView.getGridColor() : getResources().getColor(R.color.chart_grid, getTheme());
        gdGrid.setColor(curGrid);
        viewGridColor.setBackground(gdGrid);
        rowGridColor.addView(lbGridColor);
        rowGridColor.addView(viewGridColor);
        containerChart.addView(rowGridColor);

        final int[] curLastColor = {marketChartView.getLastPriceLineColor()};
        final int[] curGridColor = {curGrid};
        final float[] finalTxtSize = {curTxtSize[0]};

                // Price text color
        LinearLayout rowTxtColor = new LinearLayout(this);
        rowTxtColor.setOrientation(LinearLayout.HORIZONTAL);
        rowTxtColor.setGravity(Gravity.CENTER_VERTICAL);
        rowTxtColor.setPadding(0,8,0,8);
        TextView lbTxtColor = new TextView(this);
        lbTxtColor.setText(getString(R.string.chart_price_text_color));
        lbTxtColor.setTextSize(13f);
        lbTxtColor.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final View viewTxtColor = new View(this);
        viewTxtColor.setLayoutParams(new LinearLayout.LayoutParams(48,48));
        GradientDrawable gdTxtC = new GradientDrawable(); gdTxtC.setCornerRadius(8f);
        int curTxtC = marketChartView.getPriceTextColor() != -1 ? marketChartView.getPriceTextColor() : getThemeColor(android.R.attr.textColorSecondary);
        gdTxtC.setColor(curTxtC);
        viewTxtColor.setBackground(gdTxtC);
        rowTxtColor.addView(lbTxtColor);
        rowTxtColor.addView(viewTxtColor);
        containerChart.addView(rowTxtColor);

        // Last line width
        TextView lbLastW = new TextView(this);
        final float[] curLastW = {marketChartView.getLastLineWidthPx() > 0 ? marketChartView.getLastLineWidthPx() : getResources().getDimension(R.dimen.chart_last_price_line_width)};
        lbLastW.setText(getString(R.string.chart_last_line_width, (int)curLastW[0]));
        lbLastW.setTextSize(12f);
        containerChart.addView(lbLastW);
        android.widget.SeekBar sbLastW = new android.widget.SeekBar(this);
        sbLastW.setMax(10);
        sbLastW.setProgress((int)curLastW[0]);
        containerChart.addView(sbLastW);

        // Dashed switch
        LinearLayout rowDash = new LinearLayout(this);
        rowDash.setOrientation(LinearLayout.HORIZONTAL);
        rowDash.setGravity(Gravity.CENTER_VERTICAL);
        TextView lbDash = new TextView(this);
        lbDash.setText(getString(R.string.chart_last_line_dashed));
        lbDash.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final android.widget.Switch swDash = new android.widget.Switch(this);
        swDash.setChecked(marketChartView.isLastLineDashed());
        rowDash.addView(lbDash);
        rowDash.addView(swDash);
        containerChart.addView(rowDash);

        // bg removed - use theme
        final int[] curPriceTxtColor = {curTxtC};


        viewLastColor.setOnClickListener(new View.OnClickListener(){
            @Override public void onClick(View v){
                int idx=0; for(int i=0;i<candlePalette.length;i++){ if(candlePalette[i]==curLastColor[0]){idx=i;break;}}
                int next=candlePalette[(idx+1)%candlePalette.length]; curLastColor[0]=next;
                GradientDrawable gd=new GradientDrawable(); gd.setCornerRadius(8f); gd.setColor(next); v.setBackground(gd);
            }
        });
        viewGridColor.setOnClickListener(new View.OnClickListener(){
            @Override public void onClick(View v){
                int idx=0; for(int i=0;i<candlePalette.length;i++){ if(candlePalette[i]==curGridColor[0]){idx=i;break;}}
                int next=candlePalette[(idx+1)%candlePalette.length]; curGridColor[0]=next;
                GradientDrawable gd=new GradientDrawable(); gd.setCornerRadius(8f); gd.setColor(next); v.setBackground(gd);
            }
        });

        viewBgColor.setOnClickListener(new View.OnClickListener(){
            @Override public void onClick(View v){
                int idx=0; for(int i=0;i<candlePalette.length;i++){ if(candlePalette[i]==curBgColor[0]){idx=i;break;}}
                int next=candlePalette[(idx+1)%candlePalette.length]; curBgColor[0]=next;
                GradientDrawable gd=new GradientDrawable(); gd.setCornerRadius(8f); gd.setColor(next); v.setBackground(gd);
            }
        });
        viewTxtColor.setOnClickListener(new View.OnClickListener(){
            @Override public void onClick(View v){
                int idx=0; for(int i=0;i<candlePalette.length;i++){ if(candlePalette[i]==curPriceTxtColor[0]){idx=i;break;}}
                int next=candlePalette[(idx+1)%candlePalette.length]; curPriceTxtColor[0]=next;
                GradientDrawable gd=new GradientDrawable(); gd.setCornerRadius(8f); gd.setColor(next); v.setBackground(gd);
            }
        });
        sbLastW.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser){
                if(progress<1) progress=1; curLastW[0]=progress; lbLastW.setText(getString(R.string.chart_last_line_width, progress));
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar){}
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar){}
        });

        sbTxtSize.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser){
                if(progress<8) progress=8; finalTxtSize[0]=progress; lbTxtSize.setText(getString(R.string.chart_price_text_size, progress));
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar){}
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar){}
        });


        root.addView(containerChart);

        // ===================== TOGGLE LOGIC =====================
        final boolean[] isCandleExpanded = {false};
        final boolean[] isMaExpanded = {false};
        final boolean[] isChartExpanded = {false};

        candleHeader.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                isCandleExpanded[0] = !isCandleExpanded[0];
                if (isCandleExpanded[0])
                {
                    containerCandle.setVisibility(View.VISIBLE);
                    titleCandle.setText("\u25BC " + getString(R.string.chart_settings_candle));
                }
                else
                {
                    containerCandle.setVisibility(View.GONE);
                    titleCandle.setText("\u25B6 " + getString(R.string.chart_settings_candle));
                }
            }
        });

        maHeader.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                isMaExpanded[0] = !isMaExpanded[0];
                if (isMaExpanded[0])
                {
                    containerMa.setVisibility(View.VISIBLE);
                    titleMa.setText("\u25BC " + getString(R.string.chart_settings_ma));
                }
                else
                {
                    containerMa.setVisibility(View.GONE);
                    titleMa.setText("\u25B6 " + getString(R.string.chart_settings_ma));
                }
            }
        });

        chartHeader.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                isChartExpanded[0] = !isChartExpanded[0];
                if (isChartExpanded[0])
                {
                    containerChart.setVisibility(View.VISIBLE);
                    titleChart.setText("\u25BC " + getString(R.string.chart_settings_chart_options));
                }
                else
                {
                    containerChart.setVisibility(View.GONE);
                    titleChart.setText("\u25B6 " + getString(R.string.chart_settings_chart_options));
                }
            }
        });

        // ===================== APPLY BUTTON =====================
        TextView btnApply = new TextView(this);
        btnApply.setText(getString(R.string.chart_settings_apply));
        btnApply.setTextSize(14f);
        btnApply.setTypeface(null, Typeface.BOLD);
        btnApply.setGravity(Gravity.CENTER);
        btnApply.setPadding(0, 28, 0, 28);
        btnApply.setTextColor(getResources().getColor(android.R.color.white, getTheme()));
        GradientDrawable bgApply = new GradientDrawable();
        bgApply.setCornerRadius(12f);
        bgApply.setColor(getThemeColor(android.R.attr.colorPrimary));
        btnApply.setBackground(bgApply);
        LinearLayout.LayoutParams lpApply = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpApply.topMargin = 24;
        btnApply.setLayoutParams(lpApply);
        btnApply.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                try
                {
                    if (dialog.getCurrentFocus() != null)
                    {
                        dialog.getCurrentFocus().clearFocus();
                    }
                    recycler.clearFocus();
                    for (int i = 0; i < recycler.getChildCount(); i++)
                    {
                        RecyclerView.ViewHolder vh = recycler.getChildViewHolder(recycler.getChildAt(i));
                        if (vh instanceof MaPopupAdapter.Holder)
                        {
                            MaPopupAdapter.Holder h = (MaPopupAdapter.Holder) vh;
                            int pos = h.getAdapterPosition();
                            if (pos >= 0 && pos < tempList.size())
                            {
                                String txt = h.et.getText().toString().trim();
                                if (!txt.isEmpty())
                                {
                                    int period = Integer.parseInt(txt);
                                    if (period > 0)
                                    {
                                        tempList.get(pos).period = period;
                                    }
                                }
                            }
                        }
                    }
                    // Also check focused EditText that may not be in child list
                    for (int i = 0; i < tempList.size(); i++)
                    {
                        // ensure no zero period left
                        if (tempList.get(i).period <= 0)
                        {
                            tempList.get(i).period = 20;
                        }
                    }
                }
                catch (Exception e)
                {
                }

                float bodyFraction = 0.3f + sbBody.getProgress() / 100f;
                float wickW = sbWick.getProgress();
                if (wickW < 1) wickW = 1;
                float maW = sbMaW.getProgress();
                if (maW < 1) maW = 1;
                int visCount = 20 + sbVis.getProgress();
                boolean showG = swGrid.isChecked();
                boolean showV = swVol.isChecked();
                boolean showLast = swLast.isChecked();

                marketChartView.setCandleColors(curBull[0], curBear[0]);
                marketChartView.setChartOptions(bodyFraction, wickW, maW, showG, showV, visCount);
                marketChartView.setChartAppearance(showLast, curLastColor[0], curLastColor[0], finalTxtSize[0], curPriceTxtColor[0], curGridColor[0], -1, curLastW[0], swDash.isChecked()); // -1 = follow theme for bg
                marketChartView.setMaLines(tempList);
                dialog.dismiss();
            }
        });
        root.addView(btnApply);

        scrollView.addView(root);
        dialog.setContentView(scrollView);

        if (dialog.getWindow() != null)
        {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setGravity(Gravity.CENTER);
        }

        dialog.show();
    }


    static class MaPopupAdapter extends RecyclerView.Adapter<MaPopupAdapter.Holder>
    {
        List<MarketChartView.MaLine> list;

        MaPopupAdapter(List<MarketChartView.MaLine> list)
        {
            this.list = list;
        }

        static class Holder extends RecyclerView.ViewHolder
        {
            EditText et;
            View color;
            View del;

            Holder(View v)
            {
                super(v);
                et = v.findViewById(R.id.et_period);
                color = v.findViewById(R.id.view_color);
                del = v.findViewById(R.id.btn_delete);
            }
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup p, int t)
        {
            View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_ma_popup, p, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(Holder h, int pos)
        {
            MarketChartView.MaLine line = list.get(pos);
            h.et.setText(String.valueOf(line.period));
            GradientDrawable gd = new GradientDrawable();
            gd.setCornerRadius(0f);
            gd.setColor(line.color);
            h.color.setBackground(gd);

            h.et.setOnFocusChangeListener(new View.OnFocusChangeListener()
            {
                @Override
                public void onFocusChange(View v, boolean hasFocus)
                {
                    if (!hasFocus)
                    {
                        try
                        {
                            String txt = h.et.getText().toString().trim();
                            if (!txt.isEmpty())
                            {
                                line.period = Integer.parseInt(txt);
                            }
                        }
                        catch (Exception e)
                        {
                        }
                    }
                }
            });

            h.color.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    Resources res = v.getContext().getResources();
                    int[] colors = res.getIntArray(R.array.ma_default_colors);
                    int idx = 0;
                    for (int i = 0; i < colors.length; i++)
                    {
                        if (colors[i] == line.color)
                        {
                            idx = i;
                        }
                    }
                    int next = colors[(idx + 1) % colors.length];
                    line.color = next;
                    GradientDrawable ngd = new GradientDrawable();
                    ngd.setCornerRadius(0f);
                    ngd.setColor(next);
                    h.color.setBackground(ngd);
                }
            });

            h.del.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    int p = h.getAdapterPosition();
                    if (p >= 0 && p < list.size())
                    {
                        list.remove(p);
                        notifyDataSetChanged();
                    }
                }
            });
        }

        @Override
        public int getItemCount()
        {
            return list.size();
        }
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
        new Thread(() ->
        {
            double fiatPerBtc = getFiatPerBtc(currentFiatCode);
            double basePerBtc = getFiatPerBtc("USD");
            if (fiatPerBtc == 0d || basePerBtc == 0d)
            {
                return;
            }
            double usdToFiat = fiatPerBtc / basePerBtc;
            mainHandler.post(() ->
            {
                if (textFiat!= null)
                {
                    textFiat.setText(currentFiatCode);
                }
                if (marketChartView!= null)
                {
                    marketChartView.setFiatCode(currentFiatCode);
                    marketChartView.setFiatMultiplier((float) usdToFiat);
                }
            });
        }).start();
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
                Currency currency = Currency.getInstance(fiatCode);
                fractionDigits = currency.getDefaultFractionDigits();
                if (fractionDigits < 0)
                {
                    fractionDigits = 2;
                }
                else if (fractionDigits == 0)
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

    private int getLabelResForInterval(String interval)
    {
        if (interval == null)
        {
            return R.string.more;
        }
        switch (interval)
        {
            case "1m": return R.string.interval_1m;
            case "3m": return R.string.interval_3m;
            case "5m": return R.string.interval_5m;
            case "15m": return R.string.interval_15m;
            case "30m": return R.string.interval_30m;
            case "1h": return R.string.interval_1h;
            case "2h": return R.string.interval_2h;
            case "4h": return R.string.interval_4h;
            case "6h": return R.string.interval_6h;
            case "12h": return R.string.interval_12h;
            case "1d":
            case "1D": return R.string.interval_1d;
            case "1w":
            case "1W": return R.string.interval_1w;
            case "1M": return R.string.interval_1M;
            default: return R.string.more;
        }
    }

    private void showMoreIntervalsDialog()
    {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getResources().getColor(R.color.chart_bg, getTheme()));
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText(R.string.intervals_title);
        title.setTextSize(18f);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(getThemeColor(android.R.attr.textColorPrimary));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.bottomMargin = pad;
        title.setLayoutParams(titleLp);
        root.addView(title);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        grid.setUseDefaultMargins(false);

        Resources res = getResources();
        String[] realLoad = {"", "1m", "3m", "5m", "15m", "30m", "1h", "2h", "4h", "6h", "12h", "1d", "1w", "1M"};
        int[] intervalLabels = {R.string.time, R.string.interval_1m, R.string.interval_3m, R.string.interval_5m, R.string.interval_15m, R.string.interval_30m, R.string.interval_1h, R.string.interval_2h, R.string.interval_4h, R.string.interval_6h, R.string.interval_12h, R.string.interval_1d, R.string.interval_1w, R.string.interval_1M};

        AlertDialog dialog = new AlertDialog.Builder(this)
     .setView(root)
     .setNegativeButton(R.string.close, new android.content.DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick(android.content.DialogInterface d, int w)
                        {
                            d.dismiss();
                        }
                    })
     .create();

        for (int i = 0; i < realLoad.length; i++)
        {
            TextView tv = new TextView(this);
            tv.setText(intervalLabels[i]);
            tv.setTextSize(13f);
            tv.setGravity(Gravity.CENTER);
            tv.setSingleLine(true);
            int vPad = (int) (14 * res.getDisplayMetrics().density);
            tv.setPadding(0, vPad, 0, vPad);

            boolean isSelected = realLoad[i].equalsIgnoreCase(currentInterval);
            if (realLoad[i].equals("1m") && currentInterval.equals("1m")) isSelected = true;
            if (realLoad[i].equals("1M") && currentInterval.equals("1M")) isSelected = true;
            if (!realLoad[i].equals("1m") &&!realLoad[i].equals("1M"))
            {
                isSelected = realLoad[i].equalsIgnoreCase(currentInterval);
            }

            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(0f);
            if (isSelected)
            {
                bg.setColor(res.getColor(android.R.color.white, null));
                tv.setTextColor(res.getColor(android.R.color.black, null));
            }
            else
            {
                bg.setColor(getResources().getColor(R.color.chart_bg, getTheme()));
                bg.setStroke((int) (1 * res.getDisplayMetrics().density), res.getColor(R.color.chart_grid, null));
                tv.setTextColor(getThemeColor(android.R.attr.textColorSecondary));
            }
            tv.setBackground(bg);

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = GridLayout.LayoutParams.WRAP_CONTENT;
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            lp.setMargins(8, 8, 8, 8);
            tv.setLayoutParams(lp);

            final String load = realLoad[i];
            tv.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    if (load.isEmpty())
                    {
                        return;
                    }
                    currentInterval = load;
                    if (marketChartView!= null)
                    {
                        marketChartView.loadChart(currentSymbol, currentInterval);
                    }
                    setupTimeframeChips();
                    dialog.dismiss();
                }
            });
            grid.addView(tv);
        }

        root.addView(grid);
        dialog.show();
    }

    private void setupTimeframeChips()
    {
        if (chipGroupTimeframe == null)
        {
            return;
        }
        Resources res = getResources();
        chipGroupTimeframe.removeAllViews();

        String[] outerValues = {"15m", "1h", "4h", "1d", "1M"};
        int[] outerLabels = {R.string.interval_15m, R.string.interval_1h, R.string.interval_4h, R.string.interval_1d, R.string.interval_1M};

        boolean isOuter = false;
        for (String v : outerValues)
        {
            if (v.equals(currentInterval) || v.equalsIgnoreCase(currentInterval) &&!currentInterval.equals("1m"))
            {
                if (v.equals("1M") && currentInterval.equals("1m"))
                {
                    continue;
                }
                if (v.equalsIgnoreCase(currentInterval))
                {
                    if (currentInterval.equals("1m") && v.equals("1M")) { }
                    else { isOuter = true; break; }
                }
            }
        }
        if (currentInterval.equals("15m") || currentInterval.equals("1h") || currentInterval.equals("4h") || currentInterval.equals("1d") || currentInterval.equals("1M"))
        {
            isOuter = true;
        }

        int padH = (int) (12 * res.getDisplayMetrics().density);
        int padV = (int) (8 * res.getDisplayMetrics().density);

        TextView tvTime = new TextView(this);
        tvTime.setText(R.string.time);
        tvTime.setTextSize(13f);
        tvTime.setSingleLine(true);
        tvTime.setPadding(padH, padV, padH, padV);
        tvTime.setTextColor(getThemeColor(android.R.attr.textColorSecondary));
        tvTime.setBackgroundColor(res.getColor(android.R.color.transparent, null));
        LinearLayout.LayoutParams lpTime = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpTime.setMargins(2, 0, 2, 0);
        tvTime.setLayoutParams(lpTime);
        tvTime.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    showMoreIntervalsDialog();
                }
            });
        chipGroupTimeframe.addView(tvTime);

        for (int idx = 0; idx < outerValues.length; idx++)
        {
            String realInterval = outerValues[idx];
            int resId = outerLabels[idx];
            TextView tv = new TextView(this);
            tv.setText(resId);
            tv.setTextSize(13f);
            tv.setSingleLine(true);
            tv.setPadding(padH, padV, padH, padV);

            boolean isSelected = realInterval.equals(currentInterval);
            if (realInterval.equals("1d") && currentInterval.equalsIgnoreCase("1d")) isSelected = true;

            if (isSelected)
            {
                tv.setTextColor(getThemeColor(android.R.attr.colorBackground));
                tv.setBackgroundResource(R.drawable.bg_time_selected);
            }
            else
            {
                tv.setTextColor(getThemeColor(android.R.attr.textColorSecondary));
                tv.setBackgroundColor(res.getColor(android.R.color.transparent, null));
            }

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(2, 0, 2, 0);
            tv.setLayoutParams(lp);

            final String load = realInterval;
            tv.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    currentInterval = load;
                    if (marketChartView!= null)
                    {
                        marketChartView.loadChart(currentSymbol, currentInterval);
                    }
                    setupTimeframeChips();
                }
            });
            chipGroupTimeframe.addView(tv);
        }

        TextView tvMore = new TextView(this);
        tvMore.setTextSize(13f);
        tvMore.setSingleLine(true);
        tvMore.setPadding(padH, padV, padH, padV);
        LinearLayout.LayoutParams lpMore = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpMore.setMargins(2, 0, 2, 0);
        tvMore.setLayoutParams(lpMore);

        if (isOuter)
        {
            tvMore.setText(R.string.more);
            tvMore.setTextColor(getThemeColor(android.R.attr.textColorSecondary));
            tvMore.setBackgroundColor(res.getColor(android.R.color.transparent, null));
        }
        else
        {
            int labelRes = getLabelResForInterval(currentInterval);
            tvMore.setText(labelRes);
            tvMore.setTextColor(getThemeColor(android.R.attr.colorBackground));
            tvMore.setBackgroundResource(R.drawable.bg_time_selected);
        }
        tvMore.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    showMoreIntervalsDialog();
                }
            });
        chipGroupTimeframe.addView(tvMore);
    }

    private void setupChartListener()
    {
        if (marketChartView == null)
        {
            return;
        }
        Resources res = getResources();

        marketChartView.setOnVolumeClickListener(new MarketChartView.OnVolumeClickListener()
            {
                @Override
                public void onVolumeClick(MarketChartView.Candle candle)
                {
                    runOnUiThread(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                if (popupCandleDetail == null || candle == null)
                {
                    return;
                }
                popupCandleDetail.setVisibility(View.VISIBLE);
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(getResources().getColor(R.color.chart_bg, getTheme()));
                bg.setCornerRadius(0f);
                bg.setStroke((int) (1 * res.getDisplayMetrics().density), res.getColor(R.color.chart_grid, null));
                popupCandleDetail.setBackground(bg);
                popupCandleDetail.setElevation(8f * res.getDisplayMetrics().density);
                if (popupTime!= null)
                {
                    popupTime.setText(fullTimeFormat.format(new Date(candle.openTime)));
                }
                if (popupVolume!= null)
                {
                    popupVolume.setText(getString(R.string.chart_volume_label, String.format(Locale.US, "%.2f", candle.volume)));
                }
            });
        });

        marketChartView.setOnChartUpdateListener(new MarketChartView.OnChartUpdateListener()
        {
            @Override
            public void onPriceUpdate(float price, float high24h, float low24h)
            {
                runOnUiThread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                    new Thread(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                        double fiatPerBtc = getFiatPerBtc(currentFiatCode);
                        double basePerBtc = getFiatPerBtc("USD");
                        if (fiatPerBtc == 0d || basePerBtc == 0d)
                        {
                            return;
                        }
                        double usdToFiat = fiatPerBtc / basePerBtc;
                        double priceInFiat = price * usdToFiat;
                        mainHandler.post(new Runnable()
                        {
                            @Override
                            public void run()
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
                                    color = res.getColor(R.color.palette_green, null);
                                }
                                else if (priceInFiat < lastDisplayPrice)
                                {
                                    color = res.getColor(R.color.palette_red, null);
                                }
                                else
                                {
                                    color = res.getColor(R.color.chart_last_price_line, null);
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
                runOnUiThread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                    if (textChange24h!= null)
                    {
                        textChange24h.setText(String.format(Locale.US, "%.2f%%", changePercent));
                        int c;
                        if (changePercent >= 0)
                        {
                            c = res.getColor(R.color.palette_green, null);
                        }
                        else
                        {
                            c = res.getColor(R.color.palette_red, null);
                        }
                        textChange24h.setTextColor(c);
                    }

                    new Thread(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                        double fiatPerBtc = getFiatPerBtc(currentFiatCode);
                        double basePerBtc = getFiatPerBtc("USD");
                        if (fiatPerBtc == 0d || basePerBtc == 0d)
                        {
                            return;
                        }
                        double usdToFiat = fiatPerBtc / basePerBtc;
                        double highFiat = high24h * usdToFiat;
                        double lowFiat = low24h * usdToFiat;
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

                        String highStr = getString(R.string.chart_high_label, String.format(Locale.US, "%,.2f", highFiat));
                        String lowStr = getString(R.string.chart_low_label, String.format(Locale.US, "%,.2f", lowFiat));
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

                        mainHandler.post(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                            if (textHigh24h!= null)
                            {
                                textHigh24h.setText(highStr);
                            }
                            if (textLow24h!= null)
                            {
                                textLow24h.setText(lowStr);
                            }
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
            public void onMaUpdate(List<Float> maValues)
            {
                runOnUiThread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                    new Thread(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                        double fiatPerBtc = getFiatPerBtc(currentFiatCode);
                        double basePerBtc = getFiatPerBtc("USD");
                        double usdToFiat = 1d;
                        if (fiatPerBtc!= 0d && basePerBtc!= 0d)
                        {
                            usdToFiat = fiatPerBtc / basePerBtc;
                        }

                        double finalUsdToFiat = usdToFiat;
                        mainHandler.post(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                            if (textMaLabel!= null)
                            {
                                if (maValues == null || maValues.isEmpty())
                                {
                                    textMaLabel.setText(getString(R.string.chart_ma_default));
                                }
                                else
                                {
                                    List<MarketChartView.MaLine> lines = marketChartView.getMaLines();
                                    SpannableStringBuilder sb = new SpannableStringBuilder();
                                    for (int i = 0; i < lines.size(); i++)
                                    {
                                        if (i >= maValues.size())
                                        {
                                            break;
                                        }
                                        float value = maValues.get(i);
                                        if (value == 0f)
                                        {
                                            continue;
                                        }
                                        double fiatVal = value * finalUsdToFiat;
                                        String label = String.format(Locale.US, "MA%d: %,.2f", lines.get(i).period, fiatVal);
                                        if (sb.length() > 0)
                                        {
                                            sb.append(" • ");
                                        }
                                        int start = sb.length();
                                        sb.append(label);
                                        sb.setSpan(new ForegroundColorSpan(lines.get(i).color), start, start + label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                    }
                                    textMaLabel.setText(sb);
                                }
                            }
                        });
                    }).start();
                });
            }

            @Override
            public void onCountdownUpdate(String countdown)
            {
                runOnUiThread(new Runnable()
                {
                    @Override
                    public void run()
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
                runOnUiThread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                    if (popupCandleDetail == null || candle == null)
                    {
                        return;
                    }

                    new Thread(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                        double fiatPerBtc = getFiatPerBtc(currentFiatCode);
                        double basePerBtc = getFiatPerBtc("USD");
                        double usdToFiat = 1d;
                        if (fiatPerBtc!= 0d && basePerBtc!= 0d)
                        {
                            usdToFiat = fiatPerBtc / basePerBtc;
                        }

                        double openFiat = candle.open * usdToFiat;
                        double highFiat = candle.high * usdToFiat;
                        double lowFiat = candle.low * usdToFiat;
                        double closeFiat = candle.close * usdToFiat;

                        mainHandler.post(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                            popupCandleDetail.setVisibility(View.VISIBLE);
                            GradientDrawable bg = new GradientDrawable();
                            bg.setColor(getResources().getColor(R.color.chart_bg, getTheme()));
                            bg.setCornerRadius(0f);
                            bg.setStroke((int) (1 * res.getDisplayMetrics().density), res.getColor(R.color.chart_grid, null));
                            popupCandleDetail.setBackground(bg);
                            popupCandleDetail.setElevation(8f * res.getDisplayMetrics().density);

                            if (popupTime!= null)
                            {
                                popupTime.setText(fullTimeFormat.format(new Date(candle.openTime)));
                            }
                            if (popupOpen!= null)
                            {
                                popupOpen.setText(getString(R.string.chart_open_label, String.format(Locale.US, "%,.2f", openFiat)));
                            }
                            if (popupHigh!= null)
                            {
                                popupHigh.setText(getString(R.string.chart_high_detail, String.format(Locale.US, "%,.2f", highFiat)));
                            }
                            if (popupLow!= null)
                            {
                                popupLow.setText(getString(R.string.chart_low_detail, String.format(Locale.US, "%,.2f", lowFiat)));
                            }
                            if (popupClose!= null)
                            {
                                popupClose.setText(getString(R.string.chart_close_label, String.format(Locale.US, "%,.2f", closeFiat)));
                            }
                            if (popupVolume!= null)
                            {
                                popupVolume.setText(getString(R.string.chart_volume_label, String.format(Locale.US, "%.2f", candle.volume)));
                            }
                        });
                    }).start();
                });
            }

            @Override
            public void onNothingSelected()
            {
                runOnUiThread(new Runnable()
                {
                    @Override
                    public void run()
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
