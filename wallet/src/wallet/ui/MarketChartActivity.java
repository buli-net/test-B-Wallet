/*
 * Copyright (c) 2024
 *
 * Modified version for MarketChartActivity - fixed ViewModel/Lifecycle issues
 * Uses plain Activity with manual ViewModelStoreOwner and LifecycleOwner implementation.
 * Fixed ViewModel instantiation with AndroidViewModelFactory.
 * Now uses live chart price to calculate fiat balance when available.
 * Fixed interval persistence: saves and restores selected time interval.
 * Fixed color view borders: added 1dp stroke to all color picker views.
 */

package wallet.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.TypedArray;
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

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStore;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.bitcoinj.base.Coin;
import org.bitcoinj.base.utils.Fiat;

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
import wallet.service.BlockchainState;

public class MarketChartActivity extends Activity implements ViewModelStoreOwner, LifecycleOwner
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
    private TextView textWalletBalance;
    private LinearLayout chipGroupTimeframe;
    private View popupCandleDetail;
    private TextView popupTime;
    private TextView popupOpen;
    private TextView popupHigh;
    private TextView popupLow;
    private TextView popupClose;
    private TextView popupVolume;
    private View btnChartSettings;

    // ======== FIX: Lưu interval ========
    private static final String PREFS_CHART_STATE = "chart_state_prefs";
    private static final String KEY_INTERVAL = "interval";

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

    // ViewModel và Lifecycle
    private ViewModelStore viewModelStore = new ViewModelStore();
    private LifecycleRegistry lifecycleRegistry = new LifecycleRegistry(this);

    private WalletBalanceViewModel balanceViewModel;
    private Coin currentBalance = null;
    private ExchangeRateEntry currentExchangeRate = null;
    private boolean isBlockchainSynced = false;

    // Biến lưu giá chart
    private float currentMarketPriceFiat = 0f;

    // ========== Helper: create color view with 1dp border ==========
    private GradientDrawable createColorViewDrawable(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(8f);
        drawable.setColor(color);
        float density = getResources().getDisplayMetrics().density;
        int borderColor = getResources().getColor(R.color.chart_grid, getTheme());
        drawable.setStroke((int) (1 * density), borderColor);
        return drawable;
    }

    private static class ChartSettingsState
    {
        int[] candlePalette;
        int[] curBull = new int[1];
        int[] curBear = new int[1];
        int[] bullIdx = new int[1];
        int[] bearIdx = new int[1];
        float[] curWick = new float[1];
        float[] curMaW = new float[1];
        float[] curTxtSize = new float[1];
        float[] curLastW = new float[1];
        float[] curLabelSize = new float[1];
        int[] curLastColor = new int[1];
        int[] curGridColor = new int[1];
        int[] curPriceTxtColor = new int[1];
        int[] curLabelBg = new int[1];
        int[] curLabelTextColorFinal = new int[1];
        float[] finalTxtSize = new float[1];
        float[] finalLabelSize = new float[1];
        List<MarketChartView.MaLine> tempList;
        android.widget.SeekBar sbBody;
        android.widget.SeekBar sbWick;
        android.widget.SeekBar sbMaW;
        android.widget.SeekBar sbVis;
        android.widget.SeekBar sbTxtSize;
        android.widget.SeekBar sbLastW;
        android.widget.SeekBar sbLabelSize;
        android.widget.Switch swGrid;
        android.widget.Switch swVol;
        android.widget.Switch swLast;
        android.widget.Switch swDash;
        RecyclerView recycler;
    }

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

    // ===== Implement ViewModelStoreOwner =====
    @Override
    public ViewModelStore getViewModelStore() {
        return viewModelStore;
    }

    // ===== Implement LifecycleOwner =====
    @Override
    public Lifecycle getLifecycle() {
        return lifecycleRegistry;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE);
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
        textWalletBalance = findViewById(R.id.textWalletBalance);
        chipGroupTimeframe = findViewById(R.id.chipGroupTimeframe);
        popupCandleDetail = findViewById(R.id.popupCandleDetail);
        popupTime = findViewById(R.id.popupTime);
        popupOpen = findViewById(R.id.popupOpen);
        popupHigh = findViewById(R.id.popupHigh);
        popupLow = findViewById(R.id.popupLow);
        popupClose = findViewById(R.id.popupClose);
        popupVolume = findViewById(R.id.popupVolume);
        btnChartSettings = findViewById(R.id.btnChartSettings);

        if (btnChartSettings != null)
        {
            btnChartSettings.setOnClickListener(v -> showChartSettingsPopup());
        }

        if (getIntent() != null && getIntent().hasExtra("symbol"))
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

        prefsListener = (sharedPreferences, key) -> {
            if (Configuration.PREFS_KEY_EXCHANGE_CURRENCY.equals(key))
            {
                String newCode = config.getExchangeCurrencyCode();
                if (newCode != null)
                {
                    currentFiatCode = newCode;
                    loadFiatRate();
                }
            }
        };
        prefs.registerOnSharedPreferenceChangeListener(prefsListener);

        // ======== FIX: Khôi phục interval đã lưu ========
        SharedPreferences statePrefs = getSharedPreferences(PREFS_CHART_STATE, MODE_PRIVATE);
        String savedInterval = statePrefs.getString(KEY_INTERVAL, "15m");
        if (savedInterval != null && !savedInterval.isEmpty()) {
            currentInterval = savedInterval;
        }

        setupTimeframeChips();
        setupChartListener();

        if (marketChartView != null)
        {
            marketChartView.loadChart(currentSymbol, currentInterval);
        }

        loadFiatRate();

        // ========== KHỞI TẠO VIEWMODEL ĐÚNG CÁCH ==========
        balanceViewModel = new ViewModelProvider(
                this,
                new ViewModelProvider.AndroidViewModelFactory(application)
        ).get(WalletBalanceViewModel.class);

        // Observe balance
        balanceViewModel.getBalance().observe(this, balance -> {
            currentBalance = balance;
            updateBalanceDisplay();
        });

        // Observe exchange rate
        balanceViewModel.getExchangeRate().observe(this, exchangeRate -> {
            currentExchangeRate = exchangeRate;
            updateBalanceDisplay();
        });

        // Observe blockchain state
        application.blockchainState.observe(this, blockchainState -> {
            if (blockchainState != null) {
                isBlockchainSynced = !blockchainState.replaying;
                updateBalanceDisplay();
            }
        });

        // Set initial text
        if (textWalletBalance != null) {
            textWalletBalance.setText(getString(R.string.balance_loading_wallet));
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START);
    }

    @Override
    protected void onResume() {
        super.onResume();
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME);
    }

    @Override
    protected void onPause() {
        super.onPause();
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY);
        viewModelStore.clear();
        if (prefs != null && prefsListener != null) {
            prefs.unregisterOnSharedPreferenceChangeListener(prefsListener);
        }
    }

    // ========== CẬP NHẬT HIỂN THỊ SỐ DƯ ==========
    private void updateBalanceDisplay() {
        if (textWalletBalance == null) return;

        if (!isBlockchainSynced) {
            textWalletBalance.setText(getString(R.string.balance_syncing));
            return;
        }

        if (currentBalance == null) {
            textWalletBalance.setText(getString(R.string.balance_loading));
            return;
        }

        try {
            double btcBalance = currentBalance.toBtc().doubleValue();
            String btcStr = String.format(Locale.US, "%.8f", btcBalance);

            // Ưu tiên giá chart nếu có
            if (currentMarketPriceFiat > 0) {
                double fiatVal = btcBalance * currentMarketPriceFiat;
                String symbol = getCurrencySymbol(currentFiatCode);
                textWalletBalance.setText(String.format(Locale.US, "%s BTC ≈ %s%,.2f", btcStr, symbol, fiatVal));
            } else {
                boolean showLocal = getResources().getBoolean(R.bool.show_local_balance) && config.isEnableExchangeRates();
                if (showLocal && currentExchangeRate != null) {
                    Fiat fiatValue = currentExchangeRate.exchangeRate().coinToFiat(currentBalance);
                    String fiatStr = fiatValue.toFriendlyString();
                    textWalletBalance.setText(String.format("%s BTC ≈ %s", btcStr, fiatStr));
                } else {
                    textWalletBalance.setText(String.format("%s BTC", btcStr));
                }
            }
            textWalletBalance.invalidate();
        } catch (Exception e) {
            e.printStackTrace();
            textWalletBalance.setText(getString(R.string.balance_error));
        }
    }

    // ========== CÁC PHƯƠNG THỨC KHÁC ==========
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

        final ChartSettingsState state = new ChartSettingsState();
        Resources resPal = getResources();
        TypedArray palTa = resPal.obtainTypedArray(R.array.chart_color_palette);
        state.candlePalette = new int[palTa.length()];
        for (int i = 0; i < palTa.length(); i++)
        {
            state.candlePalette[i] = palTa.getColor(i, 0);
        }
        palTa.recycle();
        state.curBull[0] = marketChartView.getBullishColor();
        state.curBear[0] = marketChartView.getBearishColor();

        for (int i = 0; i < state.candlePalette.length; i++)
        {
            if (state.candlePalette[i] == state.curBull[0])
            {
                state.bullIdx[0] = i;
                break;
            }
        }
        for (int i = 0; i < state.candlePalette.length; i++)
        {
            if (state.candlePalette[i] == state.curBear[0])
            {
                state.bearIdx[0] = i;
                break;
            }
        }

        state.curWick[0] = marketChartView.getWickWidthPx() > 0 ? marketChartView.getWickWidthPx() : getResources().getDimension(R.dimen.default_wick_width);
        state.curMaW[0] = marketChartView.getMaLineWidthPx() > 0 ? marketChartView.getMaLineWidthPx() : getResources().getDimension(R.dimen.default_ma_line_width);
        state.curTxtSize[0] = marketChartView.getPriceTextSizePx() > 0 ? marketChartView.getPriceTextSizePx() : getResources().getDimension(R.dimen.default_price_text_size);
        state.curLastW[0] = marketChartView.getLastLineWidthPx() > 0 ? marketChartView.getLastLineWidthPx() : getResources().getDimension(R.dimen.default_last_price_line_width);
        state.curLabelSize[0] = marketChartView.getLastPriceLabelTextSizePx() > 0 ? marketChartView.getLastPriceLabelTextSizePx() : getResources().getDimension(R.dimen.default_price_text_size);
        state.curLastColor[0] = marketChartView.getLastPriceLineColor();
        state.curGridColor[0] = marketChartView.getGridColor() != -1 ? marketChartView.getGridColor() : getResources().getColor(R.color.chart_grid, getTheme());
        state.curPriceTxtColor[0] = marketChartView.getPriceTextColor() != -1 ? marketChartView.getPriceTextColor() : getThemeColor(android.R.attr.textColorSecondary);
        state.curLabelBg[0] = marketChartView.getLastPriceBgColor() != -1 ? marketChartView.getLastPriceBgColor() : getResources().getColor(R.color.chart_last_price_line, getTheme());
        state.curLabelTextColorFinal[0] = marketChartView.getLastPriceLabelTextColor() != -1 ? marketChartView.getLastPriceLabelTextColor() : getThemeColor(android.R.attr.textColorPrimaryInverse);
        state.finalTxtSize[0] = state.curTxtSize[0];
        state.finalLabelSize[0] = state.curLabelSize[0];

        state.tempList = new ArrayList<MarketChartView.MaLine>();
        List<MarketChartView.MaLine> origLines = marketChartView.getMaLines();
        for (int i = 0; i < origLines.size(); i++)
        {
            MarketChartView.MaLine o = origLines.get(i);
            state.tempList.add(new MarketChartView.MaLine(o.period, o.color));
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

        TypedValue outValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);

        addCandleSection(root, state, outValue);
        addDivider(root);
        addMaSection(root, state, outValue);
        addDivider(root);
        addChartOptionsSection(root, state, outValue);
        addApplyButton(root, state, dialog);
        addResetButton(root, state, dialog);

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

    private void addDivider(LinearLayout root)
    {
        View divider = new View(this);
        LinearLayout.LayoutParams lpDiv = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (1 * getResources().getDisplayMetrics().density));
        lpDiv.topMargin = 16;
        lpDiv.bottomMargin = 16;
        divider.setLayoutParams(lpDiv);
        divider.setBackgroundColor(getResources().getColor(R.color.chart_grid, getTheme()));
        root.addView(divider);
    }

    private void addCandleSection(LinearLayout root, final ChartSettingsState state, TypedValue outValue)
    {
        LinearLayout candleHeader = new LinearLayout(this);
        candleHeader.setOrientation(LinearLayout.HORIZONTAL);
        candleHeader.setGravity(Gravity.CENTER_VERTICAL);
        candleHeader.setPadding(0, 16, 0, 16);
        candleHeader.setClickable(true);
        candleHeader.setFocusable(true);
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
        viewBull.setBackground(createColorViewDrawable(state.curBull[0]));
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
        viewBear.setBackground(createColorViewDrawable(state.curBear[0]));
        rowBear.addView(lbBear);
        rowBear.addView(viewBear);
        containerCandle.addView(rowBear);

        viewBull.setOnClickListener(v -> {
            state.bullIdx[0] = (state.bullIdx[0] + 1) % state.candlePalette.length;
            int next = state.candlePalette[state.bullIdx[0]];
            state.curBull[0] = next;
            v.setBackground(createColorViewDrawable(next));
        });

        viewBear.setOnClickListener(v -> {
            state.bearIdx[0] = (state.bearIdx[0] + 1) % state.candlePalette.length;
            int next = state.candlePalette[state.bearIdx[0]];
            state.curBear[0] = next;
            v.setBackground(createColorViewDrawable(next));
        });

        root.addView(containerCandle);

        final boolean[] isCandleExpanded = {false};
        candleHeader.setOnClickListener(v -> {
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
        });
    }

    private void addMaSection(LinearLayout root, final ChartSettingsState state, TypedValue outValue)
    {
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
        state.recycler = maView.findViewById(R.id.recycler_ma_popup);
        state.recycler.setLayoutManager(new LinearLayoutManager(this));
        state.recycler.setNestedScrollingEnabled(false);

        final MaPopupAdapter adapter = new MaPopupAdapter(state.tempList);
        state.recycler.setAdapter(adapter);

        View btnAdd = maView.findViewById(R.id.btn_add_ma);
        GradientDrawable addBg = new GradientDrawable();
        addBg.setCornerRadius(12f);
        addBg.setColor(getResources().getColor(R.color.bg_level2, getTheme()));
        btnAdd.setBackground(addBg);
        btnAdd.setOnClickListener(v -> {
            if (state.tempList.size() >= 6)
            {
                Toast.makeText(v.getContext(), getString(R.string.max_ma_reached), Toast.LENGTH_SHORT).show();
                return;
            }
            Resources res = getResources();
            TypedArray ta = res.obtainTypedArray(R.array.chart_color_palette);
            int[] colors = new int[ta.length()];
            for (int i = 0; i < ta.length(); i++)
            {
                colors[i] = ta.getColor(i, 0);
            }
            ta.recycle();
            int color = colors[state.tempList.size() % colors.length];
            state.tempList.add(new MarketChartView.MaLine(20, color));
            adapter.notifyDataSetChanged();
        });

        View btnApplyOld = maView.findViewById(R.id.btn_apply);
        if (btnApplyOld != null)
        {
            btnApplyOld.setVisibility(View.GONE);
        }

        containerMa.addView(maView);
        root.addView(containerMa);

        final boolean[] isMaExpanded = {false};
        maHeader.setOnClickListener(v -> {
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
        });
    }

    private void addChartOptionsSection(LinearLayout root, final ChartSettingsState state, TypedValue outValue)
    {
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

        buildBodyWidthControl(containerChart, state);
        buildWickWidthControl(containerChart, state);
        buildMaWidthControl(containerChart, state);
        buildGridVolumeSwitches(containerChart, state);
        buildVisibleCountControl(containerChart, state);
        buildLastPriceLineSection(containerChart, state);
        buildPriceTextSection(containerChart, state);
        buildGridColorSection(containerChart, state);
        buildPriceTextColorSection(containerChart, state);
        buildLastLineWidthSection(containerChart, state);
        buildDashedSwitchSection(containerChart, state);
        buildCurrentPriceLabelSection(containerChart, state);

        root.addView(containerChart);

        final boolean[] isChartExpanded = {false};
        chartHeader.setOnClickListener(v -> {
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
        });
    }

    private void buildBodyWidthControl(LinearLayout container, final ChartSettingsState state)
    {
        TextView lbBody = new TextView(this);
        lbBody.setText(getString(R.string.chart_body_width, String.valueOf(marketChartView.getBodyWidthFraction())));
        lbBody.setTextSize(12f);
        container.addView(lbBody);
        android.widget.SeekBar sbBody = new android.widget.SeekBar(this);
        sbBody.setMax(70);
        sbBody.setProgress((int) ((marketChartView.getBodyWidthFraction() - 0.3f) * 100));
        container.addView(sbBody);
        state.sbBody = sbBody;
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
    }

    private void buildWickWidthControl(LinearLayout container, final ChartSettingsState state)
    {
        TextView lbWick = new TextView(this);
        lbWick.setText(getString(R.string.chart_wick_width, (int) state.curWick[0]));
        lbWick.setTextSize(12f);
        container.addView(lbWick);
        android.widget.SeekBar sbWick = new android.widget.SeekBar(this);
        sbWick.setMax(20);
        sbWick.setProgress((int) state.curWick[0]);
        container.addView(sbWick);
        state.sbWick = sbWick;
        sbWick.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener()
        {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser)
            {
                if (progress < 1) progress = 1;
                state.curWick[0] = progress;
                lbWick.setText(getString(R.string.chart_wick_width, progress));
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });
    }

    private void buildMaWidthControl(LinearLayout container, final ChartSettingsState state)
    {
        TextView lbMaW = new TextView(this);
        lbMaW.setText(getString(R.string.chart_ma_line_width, (int) state.curMaW[0]));
        lbMaW.setTextSize(12f);
        container.addView(lbMaW);
        android.widget.SeekBar sbMaW = new android.widget.SeekBar(this);
        sbMaW.setMax(20);
        sbMaW.setProgress((int) state.curMaW[0]);
        container.addView(sbMaW);
        state.sbMaW = sbMaW;
        sbMaW.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener()
        {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser)
            {
                if (progress < 1) progress = 1;
                state.curMaW[0] = progress;
                lbMaW.setText(getString(R.string.chart_ma_line_width, progress));
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });
    }

    private void buildGridVolumeSwitches(LinearLayout container, final ChartSettingsState state)
    {
        LinearLayout rowGrid = new LinearLayout(this);
        rowGrid.setOrientation(LinearLayout.HORIZONTAL);
        rowGrid.setGravity(Gravity.CENTER_VERTICAL);
        TextView lbGrid = new TextView(this);
        lbGrid.setText(getString(R.string.chart_show_grid));
        lbGrid.setTextSize(12f);
        lbGrid.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final android.widget.Switch swGrid = new android.widget.Switch(this);
        swGrid.setChecked(marketChartView.isShowGrid());
        rowGrid.addView(lbGrid);
        rowGrid.addView(swGrid);
        container.addView(rowGrid);
        state.swGrid = swGrid;

        LinearLayout rowVol = new LinearLayout(this);
        rowVol.setOrientation(LinearLayout.HORIZONTAL);
        rowVol.setGravity(Gravity.CENTER_VERTICAL);
        TextView lbVol = new TextView(this);
        lbVol.setText(getString(R.string.chart_show_volume));
        lbVol.setTextSize(12f);
        lbVol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final android.widget.Switch swVol = new android.widget.Switch(this);
        swVol.setChecked(marketChartView.isShowVolume());
        rowVol.addView(lbVol);
        rowVol.addView(swVol);
        container.addView(rowVol);
        state.swVol = swVol;
    }

    private void buildVisibleCountControl(LinearLayout container, final ChartSettingsState state)
    {
        TextView lbVis = new TextView(this);
        lbVis.setText(getString(R.string.chart_visible_candles, marketChartView.getVisibleCandleCountValue()));
        lbVis.setTextSize(12f);
        container.addView(lbVis);
        android.widget.SeekBar sbVis = new android.widget.SeekBar(this);
        sbVis.setMax(130);
        sbVis.setProgress(marketChartView.getVisibleCandleCountValue() - 20);
        container.addView(sbVis);
        state.sbVis = sbVis;
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
    }

    private void buildLastPriceLineSection(LinearLayout container, final ChartSettingsState state)
    {
        TextView lbLastPrice = new TextView(this);
        lbLastPrice.setText(getString(R.string.chart_last_price_section));
        lbLastPrice.setTextSize(12f);
        lbLastPrice.setTypeface(null, Typeface.BOLD);
        lbLastPrice.setPadding(0, 16, 0, 8);
        container.addView(lbLastPrice);

        LinearLayout rowLast = new LinearLayout(this);
        rowLast.setOrientation(LinearLayout.HORIZONTAL);
        rowLast.setGravity(Gravity.CENTER_VERTICAL);
        TextView lbShowLast = new TextView(this);
        lbShowLast.setText(getString(R.string.chart_show_last_price));
        lbShowLast.setTextSize(12f);
        lbShowLast.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final android.widget.Switch swLast = new android.widget.Switch(this);
        swLast.setChecked(marketChartView.isShowLastPriceLine());
        rowLast.addView(lbShowLast);
        rowLast.addView(swLast);
        container.addView(rowLast);
        state.swLast = swLast;

        LinearLayout rowLastColor = new LinearLayout(this);
        rowLastColor.setOrientation(LinearLayout.HORIZONTAL);
        rowLastColor.setGravity(Gravity.CENTER_VERTICAL);
        rowLastColor.setPadding(0, 8, 0, 8);
        TextView lbLastColor = new TextView(this);
        lbLastColor.setText(getString(R.string.chart_last_price_color));
        lbLastColor.setTextSize(12f);
        lbLastColor.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final View viewLastColor = new View(this);
        viewLastColor.setLayoutParams(new LinearLayout.LayoutParams(48, 48));
        viewLastColor.setBackground(createColorViewDrawable(state.curLastColor[0]));
        rowLastColor.addView(lbLastColor);
        rowLastColor.addView(viewLastColor);
        container.addView(rowLastColor);

        viewLastColor.setOnClickListener(v -> {
            int idx = 0;
            for (int i = 0; i < state.candlePalette.length; i++)
            {
                if (state.candlePalette[i] == state.curLastColor[0])
                {
                    idx = i;
                    break;
                }
            }
            int next = state.candlePalette[(idx + 1) % state.candlePalette.length];
            state.curLastColor[0] = next;
            v.setBackground(createColorViewDrawable(next));
        });
    }

    private void buildPriceTextSection(LinearLayout container, final ChartSettingsState state)
    {
        TextView lbTxtSize = new TextView(this);
        lbTxtSize.setText(getString(R.string.chart_price_text_size, (int) state.curTxtSize[0]));
        lbTxtSize.setTextSize(12f);
        container.addView(lbTxtSize);
        android.widget.SeekBar sbTxtSize = new android.widget.SeekBar(this);
        sbTxtSize.setMax(30);
        sbTxtSize.setProgress((int) state.curTxtSize[0]);
        container.addView(sbTxtSize);
        state.sbTxtSize = sbTxtSize;
        sbTxtSize.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener()
        {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser)
            {
                if (progress < 8) progress = 8;
                state.finalTxtSize[0] = progress;
                lbTxtSize.setText(getString(R.string.chart_price_text_size, progress));
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });
    }

    private void buildGridColorSection(LinearLayout container, final ChartSettingsState state)
    {
        LinearLayout rowGridColor = new LinearLayout(this);
        rowGridColor.setOrientation(LinearLayout.HORIZONTAL);
        rowGridColor.setGravity(Gravity.CENTER_VERTICAL);
        rowGridColor.setPadding(0, 8, 0, 8);
        TextView lbGridColor = new TextView(this);
        lbGridColor.setText(getString(R.string.chart_grid_color));
        lbGridColor.setTextSize(13f);
        lbGridColor.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final View viewGridColor = new View(this);
        viewGridColor.setLayoutParams(new LinearLayout.LayoutParams(48, 48));
        viewGridColor.setBackground(createColorViewDrawable(state.curGridColor[0]));
        rowGridColor.addView(lbGridColor);
        rowGridColor.addView(viewGridColor);
        container.addView(rowGridColor);

        viewGridColor.setOnClickListener(v -> {
            int idx = 0;
            for (int i = 0; i < state.candlePalette.length; i++)
            {
                if (state.candlePalette[i] == state.curGridColor[0])
                {
                    idx = i;
                    break;
                }
            }
            int next = state.candlePalette[(idx + 1) % state.candlePalette.length];
            state.curGridColor[0] = next;
            v.setBackground(createColorViewDrawable(next));
        });
    }

    private void buildPriceTextColorSection(LinearLayout container, final ChartSettingsState state)
    {
        LinearLayout rowTxtColor = new LinearLayout(this);
        rowTxtColor.setOrientation(LinearLayout.HORIZONTAL);
        rowTxtColor.setGravity(Gravity.CENTER_VERTICAL);
        rowTxtColor.setPadding(0, 8, 0, 8);
        TextView lbTxtColor = new TextView(this);
        lbTxtColor.setText(getString(R.string.chart_price_text_color));
        lbTxtColor.setTextSize(13f);
        lbTxtColor.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final View viewTxtColor = new View(this);
        viewTxtColor.setLayoutParams(new LinearLayout.LayoutParams(48, 48));
        viewTxtColor.setBackground(createColorViewDrawable(state.curPriceTxtColor[0]));
        rowTxtColor.addView(lbTxtColor);
        rowTxtColor.addView(viewTxtColor);
        container.addView(rowTxtColor);

        viewTxtColor.setOnClickListener(v -> {
            int idx = 0;
            for (int i = 0; i < state.candlePalette.length; i++)
            {
                if (state.candlePalette[i] == state.curPriceTxtColor[0])
                {
                    idx = i;
                    break;
                }
            }
            int next = state.candlePalette[(idx + 1) % state.candlePalette.length];
            state.curPriceTxtColor[0] = next;
            v.setBackground(createColorViewDrawable(next));
        });
    }

    private void buildLastLineWidthSection(LinearLayout container, final ChartSettingsState state)
    {
        TextView lbLastW = new TextView(this);
        lbLastW.setText(getString(R.string.chart_last_line_width, (int) state.curLastW[0]));
        lbLastW.setTextSize(12f);
        container.addView(lbLastW);
        android.widget.SeekBar sbLastW = new android.widget.SeekBar(this);
        sbLastW.setMax(10);
        sbLastW.setProgress((int) state.curLastW[0]);
        container.addView(sbLastW);
        state.sbLastW = sbLastW;
        sbLastW.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener()
        {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser)
            {
                if (progress < 1) progress = 1;
                state.curLastW[0] = progress;
                lbLastW.setText(getString(R.string.chart_last_line_width, progress));
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });
    }

    private void buildDashedSwitchSection(LinearLayout container, final ChartSettingsState state)
    {
        LinearLayout rowDash = new LinearLayout(this);
        rowDash.setOrientation(LinearLayout.HORIZONTAL);
        rowDash.setGravity(Gravity.CENTER_VERTICAL);
        TextView lbDash = new TextView(this);
        lbDash.setText(getString(R.string.chart_last_line_dashed));
        lbDash.setTextSize(12f);
        lbDash.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final android.widget.Switch swDash = new android.widget.Switch(this);
        swDash.setChecked(marketChartView.isLastLineDashed());
        rowDash.addView(lbDash);
        rowDash.addView(swDash);
        container.addView(rowDash);
        state.swDash = swDash;
    }

    private void buildCurrentPriceLabelSection(LinearLayout container, final ChartSettingsState state)
    {
        View dividerLabel = new View(this);
        LinearLayout.LayoutParams lpDivLabel = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (1 * getResources().getDisplayMetrics().density));
        lpDivLabel.topMargin = 16;
        lpDivLabel.bottomMargin = 8;
        dividerLabel.setLayoutParams(lpDivLabel);
        dividerLabel.setBackgroundColor(getResources().getColor(R.color.chart_grid, getTheme()));
        container.addView(dividerLabel);

        TextView lbLabelSection = new TextView(this);
        lbLabelSection.setText(getString(R.string.chart_last_price_label_section));
        lbLabelSection.setTextSize(12f);
        lbLabelSection.setTypeface(null, Typeface.BOLD);
        lbLabelSection.setPadding(0, 8, 0, 8);
        container.addView(lbLabelSection);

        LinearLayout rowLabelBg = new LinearLayout(this);
        rowLabelBg.setOrientation(LinearLayout.HORIZONTAL);
        rowLabelBg.setGravity(Gravity.CENTER_VERTICAL);
        rowLabelBg.setPadding(0, 8, 0, 8);
        TextView lbLabelBg = new TextView(this);
        lbLabelBg.setText(getString(R.string.chart_last_price_label_bg));
        lbLabelBg.setTextSize(13f);
        lbLabelBg.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final View viewLabelBg = new View(this);
        viewLabelBg.setLayoutParams(new LinearLayout.LayoutParams(48, 48));
        viewLabelBg.setBackground(createColorViewDrawable(state.curLabelBg[0]));
        rowLabelBg.addView(lbLabelBg);
        rowLabelBg.addView(viewLabelBg);
        container.addView(rowLabelBg);

        LinearLayout rowLabelTextColor = new LinearLayout(this);
        rowLabelTextColor.setOrientation(LinearLayout.HORIZONTAL);
        rowLabelTextColor.setGravity(Gravity.CENTER_VERTICAL);
        rowLabelTextColor.setPadding(0, 8, 0, 8);
        TextView lbLabelTextColor = new TextView(this);
        lbLabelTextColor.setText(getString(R.string.chart_last_price_label_text_color));
        lbLabelTextColor.setTextSize(13f);
        lbLabelTextColor.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final View viewLabelTextColor = new View(this);
        viewLabelTextColor.setLayoutParams(new LinearLayout.LayoutParams(48, 48));
        viewLabelTextColor.setBackground(createColorViewDrawable(state.curLabelTextColorFinal[0]));
        rowLabelTextColor.addView(lbLabelTextColor);
        rowLabelTextColor.addView(viewLabelTextColor);
        container.addView(rowLabelTextColor);

        TextView lbLabelSize = new TextView(this);
        lbLabelSize.setText(getString(R.string.chart_last_price_label_text_size, (int) state.curLabelSize[0]));
        lbLabelSize.setTextSize(12f);
        container.addView(lbLabelSize);

        android.widget.SeekBar sbLabelSize = new android.widget.SeekBar(this);
        sbLabelSize.setMax(30);
        sbLabelSize.setProgress((int) state.curLabelSize[0]);
        container.addView(sbLabelSize);
        state.sbLabelSize = sbLabelSize;

        viewLabelBg.setOnClickListener(v -> {
            int idx = 0;
            for (int i = 0; i < state.candlePalette.length; i++)
            {
                if (state.candlePalette[i] == state.curLabelBg[0])
                {
                    idx = i;
                    break;
                }
            }
            int next = state.candlePalette[(idx + 1) % state.candlePalette.length];
            state.curLabelBg[0] = next;
            v.setBackground(createColorViewDrawable(next));
        });

        viewLabelTextColor.setOnClickListener(v -> {
            int idx = 0;
            for (int i = 0; i < state.candlePalette.length; i++)
            {
                if (state.candlePalette[i] == state.curLabelTextColorFinal[0])
                {
                    idx = i;
                    break;
                }
            }
            int next = state.candlePalette[(idx + 1) % state.candlePalette.length];
            state.curLabelTextColorFinal[0] = next;
            v.setBackground(createColorViewDrawable(next));
        });

        sbLabelSize.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener()
        {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser)
            {
                if (progress < 8) progress = 8;
                state.finalLabelSize[0] = progress;
                lbLabelSize.setText(getString(R.string.chart_last_price_label_text_size, progress));
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });
    }

    private void addApplyButton(LinearLayout root, final ChartSettingsState state, final Dialog dialog)
    {
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
        btnApply.setOnClickListener(v -> applyChartSettings(state, dialog));
        root.addView(btnApply);
    }

    private void addResetButton(LinearLayout root, final ChartSettingsState state, final Dialog dialog)
    {
        TextView btnReset = new TextView(this);
        btnReset.setText(getString(R.string.chart_settings_reset));
        btnReset.setTextSize(13f);
        btnReset.setTypeface(null, Typeface.BOLD);
        btnReset.setGravity(Gravity.CENTER);
        btnReset.setPadding(0, 28, 0, 28);
        btnReset.setTextColor(getResources().getColor(R.color.palette_red, getTheme()));
        GradientDrawable bgReset = new GradientDrawable();
        bgReset.setCornerRadius(12f);
        bgReset.setStroke((int) (1 * getResources().getDisplayMetrics().density), getResources().getColor(R.color.palette_red, getTheme()));
        bgReset.setColor(getResources().getColor(android.R.color.transparent, getTheme()));
        btnReset.setBackground(bgReset);
        LinearLayout.LayoutParams lpReset = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpReset.topMargin = 12;
        btnReset.setLayoutParams(lpReset);
        btnReset.setOnClickListener(v -> showResetConfirm(dialog));
        root.addView(btnReset);
    }

    private void applyChartSettings(ChartSettingsState state, Dialog dialog)
    {
        try
        {
            if (dialog.getCurrentFocus() != null)
            {
                dialog.getCurrentFocus().clearFocus();
            }
            if (state.recycler != null)
            {
                state.recycler.clearFocus();
                for (int i = 0; i < state.recycler.getChildCount(); i++)
                {
                    RecyclerView.ViewHolder vh = state.recycler.getChildViewHolder(state.recycler.getChildAt(i));
                    if (vh instanceof MaPopupAdapter.Holder)
                    {
                        MaPopupAdapter.Holder h = (MaPopupAdapter.Holder) vh;
                        int pos = h.getAdapterPosition();
                        if (pos >= 0 && pos < state.tempList.size())
                        {
                            String txt = h.et.getText().toString().trim();
                            if (!txt.isEmpty())
                            {
                                int period = Integer.parseInt(txt);
                                if (period > 0)
                                {
                                    state.tempList.get(pos).period = period;
                                }
                            }
                        }
                    }
                }
            }
            for (int i = 0; i < state.tempList.size(); i++)
            {
                if (state.tempList.get(i).period <= 0)
                {
                    state.tempList.get(i).period = 20;
                }
            }
        }
        catch (Exception e)
        {
        }

        float bodyFraction = 0.3f + state.sbBody.getProgress() / 100f;
        float wickW = state.sbWick.getProgress();
        if (wickW < 1) wickW = 1;
        float maW = state.sbMaW.getProgress();
        if (maW < 1) maW = 1;
        int visCount = 20 + state.sbVis.getProgress();
        boolean showG = state.swGrid.isChecked();
        boolean showV = state.swVol.isChecked();
        boolean showLast = state.swLast.isChecked();

        marketChartView.setCandleColors(state.curBull[0], state.curBear[0]);
        marketChartView.setChartOptions(bodyFraction, wickW, maW, showG, showV, visCount);
        int bgColorForApply = getThemeColor(android.R.attr.colorBackground);
        marketChartView.setChartAppearance(showLast, state.curLastColor[0], state.curLabelBg[0], state.finalTxtSize[0], state.curPriceTxtColor[0], state.curGridColor[0], bgColorForApply, state.curLastW[0], state.swDash.isChecked());
        marketChartView.setLastPriceLabelAppearance(state.curLabelBg[0], state.curLabelTextColorFinal[0], state.finalLabelSize[0]);
        marketChartView.setMaLines(state.tempList);
        dialog.dismiss();
    }

    private void showResetConfirm(final Dialog settingsDialog)
    {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.chart_reset_confirm_title))
                .setMessage(getString(R.string.chart_reset_confirm_message))
                .setPositiveButton(getString(R.string.chart_reset), (d, which) -> {
                    if (marketChartView != null) {
                        marketChartView.resetToDefaults();
                    }
                    settingsDialog.dismiss();
                    Toast.makeText(MarketChartActivity.this, getString(R.string.chart_settings_reset), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(getString(R.string.close), null)
                .show();
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

            h.et.setOnFocusChangeListener((v, hasFocus) -> {
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
            });

            h.color.setOnClickListener(v -> {
                Resources res = v.getContext().getResources();
                TypedArray ta = res.obtainTypedArray(R.array.chart_color_palette);
                int[] colors = new int[ta.length()];
                for (int i = 0; i < ta.length(); i++)
                {
                    colors[i] = ta.getColor(i, 0);
                }
                ta.recycle();
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
            });

            h.del.setOnClickListener(v -> {
                int p = h.getAdapterPosition();
                if (p >= 0 && p < list.size())
                {
                    list.remove(p);
                    notifyDataSetChanged();
                }
            });
        }

        @Override
        public int getItemCount()
        {
            return list.size();
        }
    }

    private void loadFiatRate()
    {
        new Thread(() -> {
            double fiatPerBtc = getFiatPerBtc(currentFiatCode);
            double basePerBtc = getFiatPerBtc("USD");
            if (fiatPerBtc == 0d || basePerBtc == 0d)
            {
                return;
            }
            double usdToFiat = fiatPerBtc / basePerBtc;
            mainHandler.post(() -> {
                if (textFiat != null)
                {
                    textFiat.setText(currentFiatCode);
                }
                if (marketChartView != null)
                {
                    marketChartView.setFiatCode(currentFiatCode);
                    marketChartView.setFiatMultiplier((float) usdToFiat);
                }
                updateBalanceDisplay();
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

    // ======== FIX: Lưu interval khi chọn ========
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

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(root)
                .setNegativeButton(R.string.close, (d, w) -> d.dismiss())
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
            if (!realLoad[i].equals("1m") && !realLoad[i].equals("1M"))
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
            tv.setOnClickListener(v -> {
                if (load.isEmpty()) return;
                currentInterval = load;
                getSharedPreferences(PREFS_CHART_STATE, MODE_PRIVATE)
                        .edit().putString(KEY_INTERVAL, currentInterval).apply();
                if (marketChartView != null)
                {
                    marketChartView.loadChart(currentSymbol, currentInterval);
                }
                setupTimeframeChips();
                dialog.dismiss();
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
            if (v.equals(currentInterval) || v.equalsIgnoreCase(currentInterval) && !currentInterval.equals("1m"))
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
        tvTime.setOnClickListener(v -> showMoreIntervalsDialog());
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
            tv.setOnClickListener(v -> {
                currentInterval = load;
                getSharedPreferences(PREFS_CHART_STATE, MODE_PRIVATE)
                        .edit().putString(KEY_INTERVAL, currentInterval).apply();
                if (marketChartView != null) {
                    marketChartView.loadChart(currentSymbol, currentInterval);
                }
                setupTimeframeChips();
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
        tvMore.setOnClickListener(v -> showMoreIntervalsDialog());
        chipGroupTimeframe.addView(tvMore);
    }

    private void setupChartListener()
    {
        if (marketChartView == null)
        {
            return;
        }
        final Resources res = getResources();

        marketChartView.setOnVolumeClickListener(candle -> runOnUiThread(() -> {
            if (popupCandleDetail == null || candle == null) return;
            popupCandleDetail.setVisibility(View.VISIBLE);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(getResources().getColor(R.color.chart_bg, getTheme()));
            bg.setCornerRadius(0f);
            bg.setStroke((int) (1 * res.getDisplayMetrics().density), res.getColor(R.color.chart_grid, null));
            popupCandleDetail.setBackground(bg);
            popupCandleDetail.setElevation(8f * res.getDisplayMetrics().density);
            if (popupTime != null) popupTime.setText(fullTimeFormat.format(new Date(candle.openTime)));
            if (popupVolume != null) popupVolume.setText(getString(R.string.chart_volume_label, String.format(Locale.US, "%.2f", candle.volume)));
        }));

        marketChartView.setOnChartUpdateListener(new MarketChartView.OnChartUpdateListener()
        {
            @Override
            public void onPriceUpdate(final float price, float high24h, float low24h)
            {
                runOnUiThread(() -> new Thread(() -> {
                    double fiatPerBtc = getFiatPerBtc(currentFiatCode);
                    double basePerBtc = getFiatPerBtc("USD");
                    if (fiatPerBtc == 0d || basePerBtc == 0d) {
                        final double priceInFiat = price;
                        mainHandler.post(() -> updatePriceDisplay(priceInFiat));
                        return;
                    }
                    double usdToFiat = fiatPerBtc / basePerBtc;
                    final double priceInFiat = price * usdToFiat;
                    mainHandler.post(() -> {
                        updatePriceDisplay(priceInFiat);
                        currentMarketPriceFiat = (float) priceInFiat;
                        updateBalanceDisplay();
                    });
                }).start());
            }

            private void updatePriceDisplay(double priceInFiat) {
                if (textCurrentPrice != null)
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
            }

            @Override
            public void onTickerUpdate(final float high24h, final float low24h, final float volBtc, final float volUsdt, final float changePercent)
            {
                runOnUiThread(() -> {
                    if (textChange24h != null)
                    {
                        textChange24h.setText(String.format(Locale.US, "%.2f%%", changePercent));
                        int c = changePercent >= 0 ? res.getColor(R.color.palette_green, null) : res.getColor(R.color.palette_red, null);
                        textChange24h.setTextColor(c);
                    }
                    new Thread(() -> {
                        double fiatPerBtc = getFiatPerBtc(currentFiatCode);
                        double basePerBtc = getFiatPerBtc("USD");
                        if (fiatPerBtc == 0d || basePerBtc == 0d) return;
                        double usdToFiat = fiatPerBtc / basePerBtc;
                        final double highFiat = high24h * usdToFiat;
                        final double lowFiat = low24h * usdToFiat;
                        final double volFiat = volUsdt * usdToFiat;
                        String baseAsset = currentSymbol;
                        if (baseAsset.endsWith("USDT")) baseAsset = baseAsset.substring(0, baseAsset.length() - 4);
                        else if (baseAsset.endsWith("BUSD")) baseAsset = baseAsset.substring(0, baseAsset.length() - 4);
                        else if (baseAsset.length() > 3) baseAsset = baseAsset.substring(0, 3);
                        final String highStr = getString(R.string.chart_high_label, String.format(Locale.US, "%,.2f", highFiat));
                        final String lowStr = getString(R.string.chart_low_label, String.format(Locale.US, "%,.2f", lowFiat));
                        final String volBtcStr = getString(R.string.chart_vol_base_format, baseAsset, String.format(Locale.US, "%.2f", volBtc));
                        final String volFiatStr;
                        if (volFiat >= 1_000_000_000)
                            volFiatStr = getString(R.string.chart_vol_quote_format, currentFiatCode, String.format(Locale.US, "%.2fB", volFiat / 1_000_000_000));
                        else if (volFiat >= 1_000_000)
                            volFiatStr = getString(R.string.chart_vol_quote_format, currentFiatCode, String.format(Locale.US, "%.2fM", volFiat / 1_000_000));
                        else
                            volFiatStr = getString(R.string.chart_vol_quote_format, currentFiatCode, String.format(Locale.US, "%.2f", volFiat));
                        mainHandler.post(() -> {
                            if (textHigh24h != null) textHigh24h.setText(highStr);
                            if (textLow24h != null) textLow24h.setText(lowStr);
                            if (textVolBtc != null) textVolBtc.setText(volBtcStr);
                            if (textVolFiat != null) textVolFiat.setText(volFiatStr);
                        });
                    }).start();
                });
            }

            @Override
            public void onMaUpdate(final List<Float> maValues)
            {
                runOnUiThread(() -> new Thread(() -> {
                    double fiatPerBtc = getFiatPerBtc(currentFiatCode);
                    double basePerBtc = getFiatPerBtc("USD");
                    double usdToFiat = 1d;
                    if (fiatPerBtc != 0d && basePerBtc != 0d) usdToFiat = fiatPerBtc / basePerBtc;
                    final double finalUsdToFiat = usdToFiat;
                    mainHandler.post(() -> {
                        if (textMaLabel != null)
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
                                    if (i >= maValues.size()) break;
                                    float value = maValues.get(i);
                                    if (value == 0f) continue;
                                    double fiatVal = value * finalUsdToFiat;
                                    String label = String.format(Locale.US, "MA%d: %,.2f", lines.get(i).period, fiatVal);
                                    if (sb.length() > 0) sb.append(" \u2022 ");
                                    int start = sb.length();
                                    sb.append(label);
                                    sb.setSpan(new ForegroundColorSpan(lines.get(i).color), start, start + label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                }
                                textMaLabel.setText(sb);
                            }
                        }
                    });
                }).start());
            }

            @Override
            public void onCountdownUpdate(final String countdown)
            {
                runOnUiThread(() -> {
                    if (textCountdown != null) textCountdown.setText(getString(R.string.chart_close_in, countdown));
                    if (marketChartView != null) marketChartView.setCountdown(countdown);
                });
            }

            @Override
            public void onCandleSelected(final MarketChartView.Candle candle)
            {
                runOnUiThread(() -> {
                    if (popupCandleDetail == null || candle == null) return;
                    new Thread(() -> {
                        double fiatPerBtc = getFiatPerBtc(currentFiatCode);
                        double basePerBtc = getFiatPerBtc("USD");
                        double usdToFiat = 1d;
                        if (fiatPerBtc != 0d && basePerBtc != 0d) usdToFiat = fiatPerBtc / basePerBtc;
                        final double openFiat = candle.open * usdToFiat;
                        final double highFiat = candle.high * usdToFiat;
                        final double lowFiat = candle.low * usdToFiat;
                        final double closeFiat = candle.close * usdToFiat;
                        mainHandler.post(() -> {
                            popupCandleDetail.setVisibility(View.VISIBLE);
                            GradientDrawable bg = new GradientDrawable();
                            bg.setColor(getResources().getColor(R.color.chart_bg, getTheme()));
                            bg.setCornerRadius(0f);
                            bg.setStroke((int) (1 * res.getDisplayMetrics().density), res.getColor(R.color.chart_grid, null));
                            popupCandleDetail.setBackground(bg);
                            popupCandleDetail.setElevation(8f * res.getDisplayMetrics().density);
                            if (popupTime != null) popupTime.setText(fullTimeFormat.format(new Date(candle.openTime)));
                            if (popupOpen != null) popupOpen.setText(getString(R.string.chart_open_label, String.format(Locale.US, "%,.2f", openFiat)));
                            if (popupHigh != null) popupHigh.setText(getString(R.string.chart_high_detail, String.format(Locale.US, "%,.2f", highFiat)));
                            if (popupLow != null) popupLow.setText(getString(R.string.chart_low_detail, String.format(Locale.US, "%,.2f", lowFiat)));
                            if (popupClose != null) popupClose.setText(getString(R.string.chart_close_label, String.format(Locale.US, "%,.2f", closeFiat)));
                            if (popupVolume != null) popupVolume.setText(getString(R.string.chart_volume_label, String.format(Locale.US, "%.2f", candle.volume)));
                        });
                    }).start();
                });
            }

            @Override
            public void onNothingSelected()
            {
                runOnUiThread(() -> {
                    if (popupCandleDetail != null) popupCandleDetail.setVisibility(View.GONE);
                });
            }
        });
    }
}
