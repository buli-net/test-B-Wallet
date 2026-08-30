/*
 * Copyright (c) 2024
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package wallet.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.graphics.Color;
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
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
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

import java.lang.reflect.Field;
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

/**
 * Activity that displays a market chart with various settings and indicators.
 * This version uses a fully XML-based chart settings popup with expandable sections.
 */
public class MarketChartActivity extends Activity implements ViewModelStoreOwner, LifecycleOwner {

    // ------------------------------------------------------------------------
    // UI Views
    // ------------------------------------------------------------------------
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

    // ------------------------------------------------------------------------
    // Chart state persistence (interval)
    // ------------------------------------------------------------------------
    private static final String PREFS_CHART_STATE = "chart_state_prefs";
    private static final String KEY_INTERVAL = "interval";

    private String currentSymbol = "BTCUSDT";
    private String currentInterval = "15m";

    // ------------------------------------------------------------------------
    // Date/Time formatting
    // ------------------------------------------------------------------------
    private SimpleDateFormat fullTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    // ------------------------------------------------------------------------
    // Exchange rate and currency
    // ------------------------------------------------------------------------
    private ExchangeRateDao exchangeRateDao;
    private Configuration config;
    private SharedPreferences prefs;
    private SharedPreferences.OnSharedPreferenceChangeListener prefsListener;
    private String currentFiatCode = "USD";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private float lastDisplayPrice = 0f;

    // ------------------------------------------------------------------------
    // ViewModel and Lifecycle
    // ------------------------------------------------------------------------
    private ViewModelStore viewModelStore = new ViewModelStore();
    private LifecycleRegistry lifecycleRegistry = new LifecycleRegistry(this);

    private WalletBalanceViewModel balanceViewModel;
    private Coin currentBalance = null;
    private ExchangeRateEntry currentExchangeRate = null;
    private boolean isBlockchainSynced = false;

    // Live chart price used for fiat conversion
    private float currentMarketPriceFiat = 0f;

    // ------------------------------------------------------------------------
    // Helper: create a color view with a 1dp border
    // ------------------------------------------------------------------------
    private GradientDrawable createColorViewDrawable(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(8f);
        drawable.setColor(color);
        float density = getResources().getDisplayMetrics().density;
        int borderColor = getResources().getColor(R.color.chart_grid, getTheme());
        drawable.setStroke((int) (1 * density), borderColor);
        return drawable;
    }

    private int[] loadPaletteFromColorsXml() {
        String prefix = getString(R.string.palette_prefix);
        if (prefix == null || prefix.trim().isEmpty()) {
            throw new IllegalStateException(getString(R.string.err_palette_prefix_missing));
        }
        List<Integer> colors = new ArrayList<>();
        try {
            Field[] fields = R.color.class.getFields();
            for (Field f : fields) {
                String name = f.getName();
                if (name.startsWith(prefix)) {
                    int resId = f.getInt(null);
                    int c = getResources().getColor(resId, getTheme());
                    colors.add(c);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException(getString(R.string.err_palette_load_failed, prefix), e);
        }
        if (colors.isEmpty()) {
            throw new IllegalStateException(getString(R.string.err_palette_not_found, prefix));
        }
        int[] arr = new int[colors.size()];
        for (int i = 0; i < colors.size(); i++) {
            arr[i] = colors.get(i);
        }
        return arr;
    }

    // ------------------------------------------------------------------------
    // Chart Settings State (holds all temporary UI state)
    // ------------------------------------------------------------------------
    private static class ChartSettingsState {
        // Candle colors
        int[] candlePalette;
        int[] curBull = new int[1];
        int[] curBear = new int[1];
        int[] bullIdx = new int[1];
        int[] bearIdx = new int[1];

        // Various dimensions (wick, MA width, text sizes, etc.)
        float[] curWick = new float[1];
        float[] curMaW = new float[1];
        float[] curTxtSize = new float[1];
        float[] curLastW = new float[1];
        float[] curLabelSize = new float[1];

        // Colors
        int[] curLastColor = new int[1];
        int[] curGridColor = new int[1];
        int[] curPriceTxtColor = new int[1];
        int[] curLabelBg = new int[1];
        int[] curLabelTextColorFinal = new int[1];

        // Final text sizes (after user adjustments)
        float[] finalTxtSize = new float[1];
        float[] finalLabelSize = new float[1];

        // MA lines
        List<MarketChartView.MaLine> tempList;

        // UI controls
        SeekBar sbBody;
        SeekBar sbWick;
        SeekBar sbMaW;
        SeekBar sbVis;
        SeekBar sbTxtSize;
        SeekBar sbLastW;
        SeekBar sbLabelSize;
        Switch swGrid;
        Switch swVol;
        Switch swLast;
        Switch swDash;
        RecyclerView recycler;
    }

    // ------------------------------------------------------------------------
    // Fiat symbol mapping
    // ------------------------------------------------------------------------
    private static final Map<String, String> FIAT_SYMBOLS = new HashMap<String, String>() {{
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

    // ------------------------------------------------------------------------
    // ViewModelStore / LifecycleOwner implementation
    // ------------------------------------------------------------------------
    @Override
    public ViewModelStore getViewModelStore() {
        return viewModelStore;
    }

    @Override
    public Lifecycle getLifecycle() {
        return lifecycleRegistry;
    }

    // ------------------------------------------------------------------------
    // Default interval from resources
    // ------------------------------------------------------------------------
    private String getDefaultInterval() {
        return getString(R.string.default_interval);
    }

    // ------------------------------------------------------------------------
    // Reset interval to default
    // ------------------------------------------------------------------------
    private void resetToDefaultInterval() {
        String defaultInterval = getDefaultInterval();
        currentInterval = defaultInterval;
        getSharedPreferences(PREFS_CHART_STATE, MODE_PRIVATE)
               .edit()
               .remove(KEY_INTERVAL)
               .apply();
        if (marketChartView!= null) {
            marketChartView.loadChart(currentSymbol, currentInterval);
        }
        setupTimeframeChips();
    }

    // ------------------------------------------------------------------------
    // FIX: helper đọc màu từ tag - hỗ trợ cả Integer resource và String #...
    // KHÔNG set cứng #FFFFFF nữa, thiếu là crash
    // ------------------------------------------------------------------------
    private int getColorFromTag(View v) {
        if (v == null || v.getTag() == null) {
            throw new IllegalStateException("View tag missing color");
        }
        Object tag = v.getTag();
        if (tag instanceof Integer) {
            // tag = @color/xxx -> resource ID
            return ContextCompat.getColor(this, (Integer) tag);
        } else {
            String s = tag.toString().trim();
            if (s.startsWith("#")) {
                return Color.parseColor(s);
            } else if (s.startsWith("@color/")) {
                int resId = getResources().getIdentifier(
                        s.replace("@color/", ""),
                        "color",
                        getPackageName()
                );
                if (resId!= 0) {
                    return ContextCompat.getColor(this, resId);
                }
                throw new IllegalStateException("Color resource not found for tag: " + s);
            } else {
                // có thể là chuỗi số resource
                try {
                    int resId = Integer.parseInt(s);
                    return ContextCompat.getColor(this, resId);
                } catch (Exception e) {
                    return Color.parseColor(s);
                }
            }
        }
    }

    // ------------------------------------------------------------------------
    // FIXED: 100% layout - set từng layout con riêng, không đọc từ xml cha
    // Mỗi include là 1 layout riêng: chart_settings_candle, chart_settings_ma...
    // ------------------------------------------------------------------------
    private void loadDefaultsFromLayoutAndApply() {
        LayoutInflater inflater = getLayoutInflater();

        // Inflate từng layout con riêng - không dùng xml cha
        View candleRoot = inflater.inflate(R.layout.chart_settings_candle, null);
        View maRoot = inflater.inflate(R.layout.chart_settings_ma, null);
        View optionsRoot = inflater.inflate(R.layout.chart_settings_options, null);
        View lastPriceRoot = inflater.inflate(R.layout.chart_settings_last_price, null);
        View labelRoot = inflater.inflate(R.layout.chart_settings_label, null);

        // Candle layout con
        View defBull = candleRoot.findViewById(R.id.viewBull);
        View defBear = candleRoot.findViewById(R.id.viewBear);
        SeekBar defWickFromCandle = candleRoot.findViewById(R.id.sbWick);

        // Options layout con
        SeekBar defBody = optionsRoot.findViewById(R.id.sbBody);
        SeekBar defWick = optionsRoot.findViewById(R.id.sbWick);
        if (defWick == null) {
            defWick = defWickFromCandle;
        }
        SeekBar defMaW = optionsRoot.findViewById(R.id.sbMaW);
        SeekBar defVis = optionsRoot.findViewById(R.id.sbVis);
        Switch defGrid = optionsRoot.findViewById(R.id.swGrid);
        Switch defVol = optionsRoot.findViewById(R.id.swVol);

        // LastPrice layout con
        SeekBar defTxt = lastPriceRoot.findViewById(R.id.sbTxtSize);
        SeekBar defLastW = lastPriceRoot.findViewById(R.id.sbLastW);
        Switch defLast = lastPriceRoot.findViewById(R.id.swLast);
        Switch defDash = lastPriceRoot.findViewById(R.id.swDash);
        View defLastColor = lastPriceRoot.findViewById(R.id.viewLastColor);
        View defGridColor = lastPriceRoot.findViewById(R.id.viewGridColor);
        View defTxtColor = lastPriceRoot.findViewById(R.id.viewTxtColor);

        // Label layout con
        SeekBar defLabel = labelRoot.findViewById(R.id.sbLabelSize);
        View defLabelBg = labelRoot.findViewById(R.id.viewLabelBg);
        View defLabelTextColor = labelRoot.findViewById(R.id.viewLabelTextColor);

        // MA layout con
        TextView tvPeriods = maRoot.findViewById(R.id.tvDefMaPeriods);
        TextView tvColors = maRoot.findViewById(R.id.tvDefMaColors);

        // Strict validation - no fallback
        if (defBody == null
                || defWick == null
                || defMaW == null
                || defVis == null
                || defGrid == null
                || defVol == null
                || defTxt == null
                || defLastW == null
                || defLabel == null
                || defLast == null
                || defDash == null
                || defBull == null
                || defBear == null
                || defLastColor == null
                || defGridColor == null
                || defTxtColor == null
                || defLabelBg == null
                || defLabelTextColor == null
                || tvPeriods == null
                || tvColors == null) {
            throw new IllegalStateException(getString(R.string.err_missing_default_view));
        }

        if (defBull.getTag() == null
                || defBear.getTag() == null
                || defLastColor.getTag() == null
                || defGridColor.getTag() == null
                || defTxtColor.getTag() == null
                || defLabelBg.getTag() == null
                || defLabelTextColor.getTag() == null) {
            throw new IllegalStateException(getString(R.string.err_bull_bear_tag_missing));
        }

        float bodyFrac = 0.3f + defBody.getProgress() / 100f;
        float wickW = defWick.getProgress();
        float maW = defMaW.getProgress();
        int visCount = defVis.getProgress();
        boolean showG = defGrid.isChecked();
        boolean showV = defVol.isChecked();
        boolean showLast = defLast.isChecked();
        boolean dashed = defDash.isChecked();
        float txtSize = defTxt.getProgress();
        float lastW = defLastW.getProgress();
        float labelSize = defLabel.getProgress();

        int bullColor = getColorFromTag(defBull);
        int bearColor = getColorFromTag(defBear);
        int lastColor = getColorFromTag(defLastColor);
        int gridColor = getColorFromTag(defGridColor);
        int txtColor = getColorFromTag(defTxtColor);
        int labelBg = getColorFromTag(defLabelBg);
        int labelText = getColorFromTag(defLabelTextColor);

        List<MarketChartView.MaLine> defMa = new ArrayList<>();
        String[] pArr = tvPeriods.getText().toString().split(",");
        String[] cArr = tvColors.getText().toString().split(",");
        if (pArr.length == 0 || pArr[0].trim().isEmpty()) {
            throw new IllegalStateException(getString(R.string.err_ma_periods_empty));
        }
        for (int i = 0; i < pArr.length; i++) {
            int per = Integer.parseInt(pArr[i].trim());
            int col = Color.parseColor(cArr[i % cArr.length].trim());
            defMa.add(new MarketChartView.MaLine(per, col));
        }

        // Apply - lấy dimen từ chính MarketChartView layout, không từ xml cha
        if (marketChartView!= null) {
            int topPad = marketChartView.getPaddingTop();
            int bottomPad = marketChartView.getPaddingBottom();

            if (topPad == 0) {
                topPad = (int) (20 * getResources().getDisplayMetrics().density);
            }
            if (bottomPad == 0) {
                bottomPad = (int) (20 * getResources().getDisplayMetrics().density);
            }

            int volumeHeight = 80;
            int volumeTopMargin = 10;

            View volSpace = optionsRoot.findViewById(R.id.volumeChartSpace);
            if (volSpace!= null && volSpace.getLayoutParams()!= null) {
                if (volSpace.getLayoutParams().height > 0) {
                    volumeHeight = volSpace.getLayoutParams().height;
                }
                if (volSpace.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                    volumeTopMargin = ((ViewGroup.MarginLayoutParams) volSpace.getLayoutParams()).topMargin;
                }
            }

            marketChartView.setViewDimensionsFromLayout(
                    topPad,
                    bottomPad,
                    volumeHeight,
                    volumeTopMargin,
                    60,
                    20,
                    500,
                    3000L,
                    1000L,
                    10,
                    200,
                    visCount,
                    80,
                    100,
                    10000,
                    10000,
                    1f,
                    8f,
                    8f,
                    1f,
                    0.5f,
                    0.05f,
                    2,
                    20,
                    1,
                    4,
                    12,
                    4,
                    4,
                    50
            );

            marketChartView.setDefaultsFromLayout(
                    bodyFrac,
                    wickW,
                    maW,
                    visCount,
                    showG,
                    showV,
                    showLast,
                    dashed,
                    txtSize,
                    lastW,
                    labelSize,
                    bullColor,
                    bearColor,
                    lastColor,
                    gridColor,
                    txtColor,
                    labelBg,
                    labelText,
                    defMa
            );
        }
    }

    // ------------------------------------------------------------------------
    // Lifecycle callbacks
    // ------------------------------------------------------------------------
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE);
        setContentView(R.layout.activity_market_chart);

        // Find views
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

        // Chart settings button
        if (btnChartSettings!= null) {
            btnChartSettings.setOnClickListener(v -> showChartSettingsPopup());
        }

        // Get symbol from intent if provided
        if (getIntent()!= null && getIntent().hasExtra("symbol")) {
            currentSymbol = getIntent().getStringExtra("symbol");
        }

        // Initialize app components
        WalletApplication application = (WalletApplication) getApplication();
        config = application.getConfiguration();
        prefs = application.getSharedPreferences("wallet_preferences", MODE_PRIVATE);
        exchangeRateDao = ExchangeRatesRepository.get(application).exchangeRateDao();

        // Set current fiat currency
        currentFiatCode = config.getExchangeCurrencyCode();
        if (currentFiatCode == null) {
            currentFiatCode = "USD";
        }

        // Listen for fiat currency changes
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

        // Restore saved interval
        SharedPreferences statePrefs = getSharedPreferences(PREFS_CHART_STATE, MODE_PRIVATE);
        String savedInterval = statePrefs.getString(KEY_INTERVAL, null);
        if (savedInterval!= null &&!savedInterval.isEmpty()) {
            currentInterval = savedInterval;
        } else {
            currentInterval = getDefaultInterval();
        }

        // Setup UI components
        setupTimeframeChips();
        setupChartListener();

        // NEW: load defaults from layout into MarketChartView (no chart_defaults.xml)
        loadDefaultsFromLayoutAndApply();

        // Load chart
        if (marketChartView!= null) {
            marketChartView.loadChart(currentSymbol, currentInterval);
        }

        loadFiatRate();

        // Initialize ViewModel for wallet balance
        balanceViewModel = new ViewModelProvider(
                this,
                new ViewModelProvider.AndroidViewModelFactory(application)
        ).get(WalletBalanceViewModel.class);

        // Observe balance changes
        balanceViewModel.getBalance().observe(this, balance -> {
            currentBalance = balance;
            updateBalanceDisplay();
        });

        // Observe exchange rate changes
        balanceViewModel.getExchangeRate().observe(this, exchangeRate -> {
            currentExchangeRate = exchangeRate;
            updateBalanceDisplay();
        });

        // Observe blockchain sync state
        application.blockchainState.observe(this, blockchainState -> {
            if (blockchainState!= null) {
                isBlockchainSynced =!blockchainState.replaying;
                updateBalanceDisplay();
            }
        });

        // Set initial balance text
        if (textWalletBalance!= null) {
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
        if (prefs!= null && prefsListener!= null) {
            prefs.unregisterOnSharedPreferenceChangeListener(prefsListener);
        }
    }

    // ------------------------------------------------------------------------
    // Wallet balance display
    // ------------------------------------------------------------------------
    private void updateBalanceDisplay() {
        if (textWalletBalance == null) {
            return;
        }

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

            // Use live chart price if available for real-time fiat conversion
            if (currentMarketPriceFiat > 0) {
                double fiatVal = btcBalance * currentMarketPriceFiat;
                String symbol = getCurrencySymbol(currentFiatCode);
                textWalletBalance.setText(
                        String.format(Locale.US, "%s BTC ≈ %s%,.2f", btcStr, symbol, fiatVal)
                );
            } else {
                // Fallback to stored exchange rate
                boolean showLocal = getResources().getBoolean(R.bool.show_local_balance)
                        && config.isEnableExchangeRates();
                if (showLocal && currentExchangeRate!= null) {
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

    // ------------------------------------------------------------------------
    // Chart Settings Popup (XML-based)
    // ------------------------------------------------------------------------
    private void showChartSettingsPopup() {
        if (marketChartView == null) {
            return;
        }

        // Initialize state with current values from chart
        final ChartSettingsState state = new ChartSettingsState();

        // Load color palette
        state.candlePalette = loadPaletteFromColorsXml();

        // Current candle colors
        state.curBull[0] = marketChartView.getBullishColor();
        state.curBear[0] = marketChartView.getBearishColor();

        // Find indices for current colors
        for (int i = 0; i < state.candlePalette.length; i++) {
            if (state.candlePalette[i] == state.curBull[0]) {
                state.bullIdx[0] = i;
                break;
            }
        }
        for (int i = 0; i < state.candlePalette.length; i++) {
            if (state.candlePalette[i] == state.curBear[0]) {
                state.bearIdx[0] = i;
                break;
            }
        }

        // Other current settings
        state.curWick[0] = marketChartView.getWickWidthPx() > 0? marketChartView.getWickWidthPx() : 2f;
        state.curMaW[0] = marketChartView.getMaLineWidthPx() > 0? marketChartView.getMaLineWidthPx() : 2f;
        state.curTxtSize[0] = marketChartView.getPriceTextSizePx() > 0? marketChartView.getPriceTextSizePx() : 18f;
        state.curLastW[0] = marketChartView.getLastLineWidthPx() > 0? marketChartView.getLastLineWidthPx() : 2f;
        state.curLabelSize[0] = marketChartView.getLastPriceLabelTextSizePx() > 0
               ? marketChartView.getLastPriceLabelTextSizePx()
                : 19f;
        state.curLastColor[0] = marketChartView.getLastPriceLineColor();
        state.curGridColor[0] = marketChartView.getGridColor()!= -1
               ? marketChartView.getGridColor()
                : getResources().getColor(R.color.chart_grid, getTheme());
        state.curPriceTxtColor[0] = marketChartView.getPriceTextColor()!= -1
               ? marketChartView.getPriceTextColor()
                : getThemeColor(android.R.attr.textColorSecondary);
        state.curLabelBg[0] = marketChartView.getLastPriceBgColor()!= -1
               ? marketChartView.getLastPriceBgColor()
                : getResources().getColor(R.color.chart_last_price_line, getTheme());
        state.curLabelTextColorFinal[0] = marketChartView.getLastPriceLabelTextColor()!= -1
               ? marketChartView.getLastPriceLabelTextColor()
                : ContextCompat.getColor(this, R.color.last_label_text);
        state.finalTxtSize[0] = state.curTxtSize[0];
        state.finalLabelSize[0] = state.curLabelSize[0];

        // Copy current MA lines
        state.tempList = new ArrayList<>();
        List<MarketChartView.MaLine> origLines = marketChartView.getMaLines();
        for (MarketChartView.MaLine o : origLines) {
            state.tempList.add(new MarketChartView.MaLine(o.period, o.color));
        }

        // Create dialog and inflate the XML layout
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        View content = getLayoutInflater().inflate(R.layout.chart_settings_popup, null);
        dialog.setContentView(content);

        // Configure dialog window
        if (dialog.getWindow()!= null) {
            dialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.9f),
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            dialog.getWindow().setGravity(Gravity.CENTER);
        }

        // --------------------------------------------------------------------
        // 1. CANDLE SETTINGS
        // --------------------------------------------------------------------
        View headerCandle = content.findViewById(R.id.headerCandle);
        TextView arrowCandle = content.findViewById(R.id.arrowCandle);
        View containerCandle = content.findViewById(R.id.containerCandle);

        View viewBull = content.findViewById(R.id.viewBull);
        View viewBear = content.findViewById(R.id.viewBear);

        if (viewBull!= null) {
            viewBull.setBackground(createColorViewDrawable(state.curBull[0]));
            viewBull.setOnClickListener(v -> {
                state.bullIdx[0] = (state.bullIdx[0] + 1) % state.candlePalette.length;
                int next = state.candlePalette[state.bullIdx[0]];
                state.curBull[0] = next;
                v.setBackground(createColorViewDrawable(next));
            });
        }

        if (viewBear!= null) {
            viewBear.setBackground(createColorViewDrawable(state.curBear[0]));
            viewBear.setOnClickListener(v -> {
                state.bearIdx[0] = (state.bearIdx[0] + 1) % state.candlePalette.length;
                int next = state.candlePalette[state.bearIdx[0]];
                state.curBear[0] = next;
                v.setBackground(createColorViewDrawable(next));
            });
        }

        // Candle section expand/collapse (collapsed by default)
        if (headerCandle!= null && containerCandle!= null) {
            final boolean[] candleExpanded = {false};
            containerCandle.setVisibility(View.GONE);
            if (arrowCandle!= null) {
                arrowCandle.setText(getString(R.string.arrow_collapsed));
            }
            headerCandle.setOnClickListener(v -> {
                candleExpanded[0] =!candleExpanded[0];
                containerCandle.setVisibility(candleExpanded[0]? View.VISIBLE : View.GONE);
                if (arrowCandle!= null) {
                    arrowCandle.setText(getString(candleExpanded[0]
                           ? R.string.arrow_expanded
                            : R.string.arrow_collapsed));
                }
            });
        }

        // --------------------------------------------------------------------
        // 2. MA SETTINGS
        // --------------------------------------------------------------------
        View headerMa = content.findViewById(R.id.headerMa);
        TextView arrowMa = content.findViewById(R.id.arrowMa);
        View containerMa = content.findViewById(R.id.containerMa);

        RecyclerView recycler = content.findViewById(R.id.recycler_ma_popup);
        View btnAddMa = content.findViewById(R.id.btn_add_ma);

        if (recycler!= null) {
            state.recycler = recycler;
            recycler.setLayoutManager(new LinearLayoutManager(this));
            recycler.setNestedScrollingEnabled(false);
            final MaPopupAdapter adapter = new MaPopupAdapter(state.tempList, state.candlePalette);
            recycler.setAdapter(adapter);
        }

        if (btnAddMa!= null) {
            btnAddMa.setOnClickListener(v -> {
                if (state.tempList.size() >= 6) {
                    Toast.makeText(
                            v.getContext(),
                            getString(R.string.max_ma_reached),
                            Toast.LENGTH_SHORT
                    ).show();
                    return;
                }
                int[] colors = state.candlePalette;
                int color = colors[state.tempList.size() % colors.length];
                state.tempList.add(new MarketChartView.MaLine(20, color));
                if (state.recycler!= null && state.recycler.getAdapter()!= null) {
                    state.recycler.getAdapter().notifyDataSetChanged();
                }
            });
        }

        // MA section expand/collapse (collapsed by default)
        if (headerMa!= null && containerMa!= null) {
            final boolean[] maExpanded = {false};
            containerMa.setVisibility(View.GONE);
            if (arrowMa!= null) {
                arrowMa.setText(getString(R.string.arrow_collapsed));
            }
            headerMa.setOnClickListener(v -> {
                maExpanded[0] =!maExpanded[0];
                containerMa.setVisibility(maExpanded[0]? View.VISIBLE : View.GONE);
                if (arrowMa!= null) {
                    arrowMa.setText(getString(maExpanded[0]
                           ? R.string.arrow_expanded
                            : R.string.arrow_collapsed));
                }
            });
        }

        // --------------------------------------------------------------------
        // 3. CHART OPTIONS
        // --------------------------------------------------------------------
        View headerOptions = content.findViewById(R.id.headerOptions);
        TextView arrowOptions = content.findViewById(R.id.arrowOptions);
        View containerOptions = content.findViewById(R.id.containerOptions);

        state.sbBody = content.findViewById(R.id.sbBody);
        state.sbWick = content.findViewById(R.id.sbWick);
        state.sbMaW = content.findViewById(R.id.sbMaW);
        state.sbVis = content.findViewById(R.id.sbVis);
        state.swGrid = content.findViewById(R.id.swGrid);
        state.swVol = content.findViewById(R.id.swVol);

        TextView lbBody = content.findViewById(R.id.lbBody);
        TextView lbWick = content.findViewById(R.id.lbWick);
        TextView lbMaW = content.findViewById(R.id.lbMaW);
        TextView lbVis = content.findViewById(R.id.lbVis);

        // Fixed: visible count = progress directly (progress IS the value defined in xml)
        if (state.sbBody!= null) {
            state.sbBody.setProgress((int) ((marketChartView.getBodyWidthFraction() - 0.3f) * 100));
        }
        if (state.sbWick!= null) {
            state.sbWick.setProgress((int) marketChartView.getWickWidthPx());
        }
        if (state.sbMaW!= null) {
            state.sbMaW.setProgress((int) marketChartView.getMaLineWidthPx());
        }
        if (state.sbVis!= null) {
            state.sbVis.setProgress(marketChartView.getVisibleCandleCountValue());
        }
        if (state.swGrid!= null) {
            state.swGrid.setChecked(marketChartView.isShowGrid());
        }
        if (state.swVol!= null) {
            state.swVol.setChecked(marketChartView.isShowVolume());
        }

        if (lbBody!= null && state.sbBody!= null) {
            float fraction = 0.3f + state.sbBody.getProgress() / 100f;
            lbBody.setText(getString(
                    R.string.chart_body_width,
                    String.format(Locale.US, "%.2f", fraction)
            ));
        }
        if (lbWick!= null && state.sbWick!= null) {
            int p = state.sbWick.getProgress();
            if (p < 1) {
                p = 1;
            }
            lbWick.setText(getString(R.string.chart_wick_width, p));
        }
        if (lbMaW!= null && state.sbMaW!= null) {
            int p = state.sbMaW.getProgress();
            if (p < 1) {
                p = 1;
            }
            lbMaW.setText(getString(R.string.chart_ma_line_width, p));
        }
        if (lbVis!= null && state.sbVis!= null) {
            int count = state.sbVis.getProgress();
            lbVis.setText(getString(R.string.chart_visible_candles, count));
        }

        if (state.sbBody!= null && lbBody!= null) {
            state.sbBody.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    float fraction = 0.3f + progress / 100f;
                    lbBody.setText(getString(
                            R.string.chart_body_width,
                            String.format(Locale.US, "%.2f", fraction)
                    ));
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
        }

        if (state.sbWick!= null && lbWick!= null) {
            state.sbWick.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (progress < 1) {
                        progress = 1;
                    }
                    lbWick.setText(getString(R.string.chart_wick_width, progress));
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
        }

        if (state.sbMaW!= null && lbMaW!= null) {
            state.sbMaW.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (progress < 1) {
                        progress = 1;
                    }
                    lbMaW.setText(getString(R.string.chart_ma_line_width, progress));
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
        }

        if (state.sbVis!= null && lbVis!= null) {
            state.sbVis.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    lbVis.setText(getString(R.string.chart_visible_candles, progress));
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
        }

        if (headerOptions!= null && containerOptions!= null) {
            final boolean[] optionsExpanded = {false};
            containerOptions.setVisibility(View.GONE);
            if (arrowOptions!= null) {
                arrowOptions.setText(getString(R.string.arrow_collapsed));
            }
            headerOptions.setOnClickListener(v -> {
                optionsExpanded[0] =!optionsExpanded[0];
                containerOptions.setVisibility(optionsExpanded[0]? View.VISIBLE : View.GONE);
                if (arrowOptions!= null) {
                    arrowOptions.setText(getString(optionsExpanded[0]
                           ? R.string.arrow_expanded
                            : R.string.arrow_collapsed));
                }
            });
        }

        // --------------------------------------------------------------------
        // 4. LAST PRICE LINE
        // --------------------------------------------------------------------
        View headerLastPrice = content.findViewById(R.id.headerLastPrice);
        TextView arrowLastPrice = content.findViewById(R.id.arrowLastPrice);
        View containerLastPrice = content.findViewById(R.id.containerLastPrice);

        state.swLast = content.findViewById(R.id.swLast);
        state.sbTxtSize = content.findViewById(R.id.sbTxtSize);
        state.sbLastW = content.findViewById(R.id.sbLastW);
        state.swDash = content.findViewById(R.id.swDash);

        View viewLastColor = content.findViewById(R.id.viewLastColor);
        View viewGridColor = content.findViewById(R.id.viewGridColor);
        View viewTxtColor = content.findViewById(R.id.viewTxtColor);

        if (state.swLast!= null) {
            state.swLast.setChecked(marketChartView.isShowLastPriceLine());
        }
        if (state.sbTxtSize!= null) {
            state.sbTxtSize.setProgress((int) marketChartView.getPriceTextSizePx());
        }
        if (state.sbLastW!= null) {
            state.sbLastW.setProgress((int) marketChartView.getLastLineWidthPx());
        }
        if (state.swDash!= null) {
            state.swDash.setChecked(marketChartView.isLastLineDashed());
        }

        TextView lbTxtSize = content.findViewById(R.id.lbTxtSize);
        TextView lbLastW = content.findViewById(R.id.lbLastW);

        if (lbTxtSize!= null && state.sbTxtSize!= null) {
            int p = state.sbTxtSize.getProgress();
            if (p < 8) {
                p = 8;
            }
            lbTxtSize.setText(getString(R.string.chart_price_text_size, p));
        }
        if (lbLastW!= null && state.sbLastW!= null) {
            int p = state.sbLastW.getProgress();
            if (p < 1) {
                p = 1;
            }
            lbLastW.setText(getString(R.string.chart_last_line_width, p));
        }

        if (state.sbTxtSize!= null && lbTxtSize!= null) {
            state.sbTxtSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (progress < 8) {
                        progress = 8;
                    }
                    state.finalTxtSize[0] = progress;
                    lbTxtSize.setText(getString(R.string.chart_price_text_size, progress));
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
        }

        if (state.sbLastW!= null && lbLastW!= null) {
            state.sbLastW.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (progress < 1) {
                        progress = 1;
                    }
                    state.curLastW[0] = progress;
                    lbLastW.setText(getString(R.string.chart_last_line_width, progress));
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
        }

        if (viewLastColor!= null) {
            viewLastColor.setBackground(createColorViewDrawable(state.curLastColor[0]));
            viewLastColor.setOnClickListener(v -> {
                int idx = 0;
                for (int i = 0; i < state.candlePalette.length; i++) {
                    if (state.candlePalette[i] == state.curLastColor[0]) {
                        idx = i;
                        break;
                    }
                }
                int next = state.candlePalette[(idx + 1) % state.candlePalette.length];
                state.curLastColor[0] = next;
                v.setBackground(createColorViewDrawable(next));
            });
        }

        if (viewGridColor!= null) {
            viewGridColor.setBackground(createColorViewDrawable(state.curGridColor[0]));
            viewGridColor.setOnClickListener(v -> {
                int idx = 0;
                for (int i = 0; i < state.candlePalette.length; i++) {
                    if (state.candlePalette[i] == state.curGridColor[0]) {
                        idx = i;
                        break;
                    }
                }
                int next = state.candlePalette[(idx + 1) % state.candlePalette.length];
                state.curGridColor[0] = next;
                v.setBackground(createColorViewDrawable(next));
            });
        }

        if (viewTxtColor!= null) {
            viewTxtColor.setBackground(createColorViewDrawable(state.curPriceTxtColor[0]));
            viewTxtColor.setOnClickListener(v -> {
                int idx = 0;
                for (int i = 0; i < state.candlePalette.length; i++) {
                    if (state.candlePalette[i] == state.curPriceTxtColor[0]) {
                        idx = i;
                        break;
                    }
                }
                int next = state.candlePalette[(idx + 1) % state.candlePalette.length];
                state.curPriceTxtColor[0] = next;
                v.setBackground(createColorViewDrawable(next));
            });
        }

        if (headerLastPrice!= null && containerLastPrice!= null) {
            final boolean[] lastPriceExpanded = {false};
            containerLastPrice.setVisibility(View.GONE);
            if (arrowLastPrice!= null) {
                arrowLastPrice.setText(getString(R.string.arrow_collapsed));
            }
            headerLastPrice.setOnClickListener(v -> {
                lastPriceExpanded[0] =!lastPriceExpanded[0];
                containerLastPrice.setVisibility(lastPriceExpanded[0]? View.VISIBLE : View.GONE);
                if (arrowLastPrice!= null) {
                    arrowLastPrice.setText(getString(lastPriceExpanded[0]
                           ? R.string.arrow_expanded
                            : R.string.arrow_collapsed));
                }
            });
        }

        // --------------------------------------------------------------------
        // 5. CURRENT PRICE LABEL
        // --------------------------------------------------------------------
        View headerLabel = content.findViewById(R.id.headerLabel);
        TextView arrowLabel = content.findViewById(R.id.arrowLabel);
        View containerLabel = content.findViewById(R.id.containerLabel);

        state.sbLabelSize = content.findViewById(R.id.sbLabelSize);
        View viewLabelBg = content.findViewById(R.id.viewLabelBg);
        View viewLabelTextColor = content.findViewById(R.id.viewLabelTextColor);

        if (state.sbLabelSize!= null) {
            state.sbLabelSize.setProgress((int) marketChartView.getLastPriceLabelTextSizePx());
        }

        TextView lbLabelSize = content.findViewById(R.id.lbLabelSize);

        if (lbLabelSize!= null && state.sbLabelSize!= null) {
            int p = state.sbLabelSize.getProgress();
            if (p < 8) {
                p = 8;
            }
            lbLabelSize.setText(getString(R.string.chart_last_price_label_text_size, p));
        }

        if (state.sbLabelSize!= null && lbLabelSize!= null) {
            state.sbLabelSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (progress < 8) {
                        progress = 8;
                    }
                    state.finalLabelSize[0] = progress;
                    lbLabelSize.setText(getString(R.string.chart_last_price_label_text_size, progress));
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
        }

        if (viewLabelBg!= null) {
            viewLabelBg.setBackground(createColorViewDrawable(state.curLabelBg[0]));
            viewLabelBg.setOnClickListener(v -> {
                int idx = 0;
                for (int i = 0; i < state.candlePalette.length; i++) {
                    if (state.candlePalette[i] == state.curLabelBg[0]) {
                        idx = i;
                        break;
                    }
                }
                int next = state.candlePalette[(idx + 1) % state.candlePalette.length];
                state.curLabelBg[0] = next;
                v.setBackground(createColorViewDrawable(next));
            });
        }

        if (viewLabelTextColor!= null) {
            viewLabelTextColor.setBackground(createColorViewDrawable(state.curLabelTextColorFinal[0]));
            viewLabelTextColor.setOnClickListener(v -> {
                int idx = 0;
                for (int i = 0; i < state.candlePalette.length; i++) {
                    if (state.candlePalette[i] == state.curLabelTextColorFinal[0]) {
                        idx = i;
                        break;
                    }
                }
                int next = state.candlePalette[(idx + 1) % state.candlePalette.length];
                state.curLabelTextColorFinal[0] = next;
                v.setBackground(createColorViewDrawable(next));
            });
        }

        if (headerLabel!= null && containerLabel!= null) {
            final boolean[] labelExpanded = {false};
            containerLabel.setVisibility(View.GONE);
            if (arrowLabel!= null) {
                arrowLabel.setText(getString(R.string.arrow_collapsed));
            }
            headerLabel.setOnClickListener(v -> {
                labelExpanded[0] =!labelExpanded[0];
                containerLabel.setVisibility(labelExpanded[0]? View.VISIBLE : View.GONE);
                if (arrowLabel!= null) {
                    arrowLabel.setText(getString(labelExpanded[0]
                           ? R.string.arrow_expanded
                            : R.string.arrow_collapsed));
                }
            });
        }

        // --------------------------------------------------------------------
        // 6. APPLY AND RESET BUTTONS
        // --------------------------------------------------------------------
        Button btnApply = content.findViewById(R.id.btnApply);
        Button btnReset = content.findViewById(R.id.btnReset);

        if (btnApply!= null) {
            btnApply.setOnClickListener(v -> applyChartSettings(state, dialog));
        }

        if (btnReset!= null) {
            btnReset.setOnClickListener(v -> showResetConfirm(dialog));
        }

        dialog.show();
    }

    // ------------------------------------------------------------------------
    // Apply chart settings from the popup
    // ------------------------------------------------------------------------
    private void applyChartSettings(ChartSettingsState state, Dialog dialog) {
        try {
            if (dialog.getCurrentFocus()!= null) {
                dialog.getCurrentFocus().clearFocus();
            }

            if (state.recycler!= null) {
                state.recycler.clearFocus();
                for (int i = 0; i < state.recycler.getChildCount(); i++) {
                    RecyclerView.ViewHolder vh = state.recycler.getChildViewHolder(
                            state.recycler.getChildAt(i)
                    );
                    if (vh instanceof MaPopupAdapter.Holder) {
                        MaPopupAdapter.Holder h = (MaPopupAdapter.Holder) vh;
                        int pos = h.getAdapterPosition();
                        if (pos >= 0 && pos < state.tempList.size()) {
                            String txt = h.et.getText().toString().trim();
                            if (!txt.isEmpty()) {
                                int period = Integer.parseInt(txt);
                                if (period > 0) {
                                    state.tempList.get(pos).period = period;
                                }
                            }
                        }
                    }
                }
            }
            for (int i = 0; i < state.tempList.size(); i++) {
                if (state.tempList.get(i).period <= 0) {
                    state.tempList.get(i).period = 20;
                }
            }
        } catch (Exception e) {
            // ignore parse errors
        }

        float bodyFraction = 0.3f + state.sbBody.getProgress() / 100f;
        float wickW = state.sbWick.getProgress();
        if (wickW < 1) {
            wickW = 1;
        }
        float maW = state.sbMaW.getProgress();
        if (maW < 1) {
            maW = 1;
        }
        int visCount = state.sbVis.getProgress();
        boolean showG = state.swGrid.isChecked();
        boolean showV = state.swVol.isChecked();
        boolean showLast = state.swLast.isChecked();

        marketChartView.setCandleColors(state.curBull[0], state.curBear[0]);
        marketChartView.setChartOptions(bodyFraction, wickW, maW, showG, showV, visCount);

        int bgColorForApply = getThemeColor(android.R.attr.colorBackground);
        marketChartView.setChartAppearance(
                showLast,
                state.curLastColor[0],
                state.curLabelBg[0],
                state.finalTxtSize[0],
                state.curPriceTxtColor[0],
                state.curGridColor[0],
                bgColorForApply,
                state.curLastW[0],
                state.swDash.isChecked()
        );
        marketChartView.setLastPriceLabelAppearance(
                state.curLabelBg[0],
                state.curLabelTextColorFinal[0],
                state.finalLabelSize[0]
        );
        marketChartView.setMaLines(state.tempList);

        dialog.dismiss();
        Toast.makeText(this, getString(R.string.chart_settings_applied), Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------------------
    // Reset confirmation dialog
    // ------------------------------------------------------------------------
    private void showResetConfirm(final Dialog settingsDialog) {
        new AlertDialog.Builder(this)
               .setTitle(getString(R.string.chart_reset_confirm_title))
               .setMessage(getString(R.string.chart_reset_confirm_message))
               .setPositiveButton(getString(R.string.chart_reset), (d, which) -> {
                    if (marketChartView!= null) {
                        // NEW: reset from layout-stored defaults, not from chart_defaults.xml
                        marketChartView.resetToDefaultsFromLayout();
                    }
                    resetToDefaultInterval();
                    settingsDialog.dismiss();
                    Toast.makeText(
                            MarketChartActivity.this,
                            getString(R.string.chart_settings_reset),
                            Toast.LENGTH_SHORT
                    ).show();
                })
               .setNegativeButton(getString(R.string.close), null)
               .show();
    }

    // ------------------------------------------------------------------------
    // MA Popup Adapter for RecyclerView - FIXED STATIC CONTEXT
    // ------------------------------------------------------------------------
    static class MaPopupAdapter extends RecyclerView.Adapter<MaPopupAdapter.Holder> {
        List<MarketChartView.MaLine> list;
        int[] palette;

        MaPopupAdapter(List<MarketChartView.MaLine> list, int[] palette) {
            this.list = list;
            this.palette = palette;
        }

        static class Holder extends RecyclerView.ViewHolder {
            EditText et;
            View color;
            View del;

            Holder(View v) {
                super(v);
                et = v.findViewById(R.id.et_period);
                color = v.findViewById(R.id.view_color);
                del = v.findViewById(R.id.btn_delete);
            }
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup p, int t) {
            View v = LayoutInflater.from(p.getContext())
                   .inflate(R.layout.item_ma_popup, p, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(Holder h, int pos) {
            MarketChartView.MaLine line = list.get(pos);
            h.et.setText(String.valueOf(line.period));

            GradientDrawable gd = new GradientDrawable();
            gd.setCornerRadius(0f);
            gd.setColor(line.color);
            h.color.setBackground(gd);

            h.et.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) {
                    try {
                        String txt = h.et.getText().toString().trim();
                        if (!txt.isEmpty()) {
                            line.period = Integer.parseInt(txt);
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                }
            });

            h.color.setOnClickListener(v -> {
                int[] colors = palette;
                if (colors == null || colors.length == 0) {
                    return;
                }
                int idx = 0;
                for (int i = 0; i < colors.length; i++) {
                    if (colors[i] == line.color) {
                        idx = i;
                        break;
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
                if (p >= 0 && p < list.size()) {
                    list.remove(p);
                    notifyDataSetChanged();
                }
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }
    }

    // ------------------------------------------------------------------------
    // Fiat rate loading
    // ------------------------------------------------------------------------
    private void loadFiatRate() {
        new Thread(() -> {
            double fiatPerBtc = getFiatPerBtc(currentFiatCode);
            double basePerBtc = getFiatPerBtc("USD");
            if (fiatPerBtc == 0d || basePerBtc == 0d) {
                return;
            }
            double usdToFiat = fiatPerBtc / basePerBtc;
            mainHandler.post(() -> {
                if (textFiat!= null) {
                    textFiat.setText(currentFiatCode);
                }
                if (marketChartView!= null) {
                    marketChartView.setFiatCode(currentFiatCode);
                    marketChartView.setFiatMultiplier((float) usdToFiat);
                }
                updateBalanceDisplay();
            });
        }).start();
    }

    private double getFiatPerBtc(String fiatCode) {
        ExchangeRateEntry entry = exchangeRateDao.findByCurrencyCode(fiatCode);
        if (entry == null) {
            return 0d;
        }
        try {
            long rateFiat = entry.getRateFiat();
            long rateCoin = entry.getRateCoin();
            if (rateCoin == 0) {
                return 0d;
            }
            int fractionDigits;
            try {
                Currency currency = Currency.getInstance(fiatCode);
                fractionDigits = currency.getDefaultFractionDigits();
                if (fractionDigits < 0) {
                    fractionDigits = 2;
                } else if (fractionDigits == 0) {
                    fractionDigits = 2;
                }
            } catch (Exception e) {
                fractionDigits = 2;
            }
            double fiatMajor = rateFiat / Math.pow(10, fractionDigits);
            double coinMajor = (double) rateCoin / Coin.COIN.value;
            if (coinMajor == 0d) {
                return 0d;
            }
            return fiatMajor / coinMajor;
        } catch (Exception e) {
            return 0d;
        }
    }

    // ------------------------------------------------------------------------
    // Currency symbol helper
    // ------------------------------------------------------------------------
    private String getCurrencySymbol(String fiatCode) {
        try {
            if (FIAT_SYMBOLS.containsKey(fiatCode)) {
                return FIAT_SYMBOLS.get(fiatCode);
            }
            Currency currency = Currency.getInstance(fiatCode);
            String sym = currency.getSymbol(Locale.US);
            if (sym.equals(fiatCode)) {
                sym = currency.getSymbol();
            }
            if (sym.equals(fiatCode) || sym.length() > 6) {
                return fiatCode + " ";
            }
            return sym;
        } catch (Exception e) {
            return fiatCode + " ";
        }
    }

    // ------------------------------------------------------------------------
    // Theme color helper
    // ------------------------------------------------------------------------
    private int getThemeColor(int attr) {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(attr, tv, true);
        if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT
                && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return tv.data;
        } else {
            try {
                return getResources().getColor(tv.resourceId, getTheme());
            } catch (Exception e) {
                return tv.data;
            }
        }
    }

    // ------------------------------------------------------------------------
    // Interval label helper
    // ------------------------------------------------------------------------
    private int getLabelResForInterval(String interval) {
        if (interval == null) {
            return R.string.more;
        }
        switch (interval) {
            case "1m":
                return R.string.interval_1m;
            case "3m":
                return R.string.interval_3m;
            case "5m":
                return R.string.interval_5m;
            case "15m":
                return R.string.interval_15m;
            case "30m":
                return R.string.interval_30m;
            case "1h":
                return R.string.interval_1h;
            case "2h":
                return R.string.interval_2h;
            case "4h":
                return R.string.interval_4h;
            case "6h":
                return R.string.interval_6h;
            case "12h":
                return R.string.interval_12h;
            case "1d":
            case "1D":
                return R.string.interval_1d;
            case "1w":
            case "1W":
                return R.string.interval_1w;
            case "1M":
                return R.string.interval_1M;
            default:
                return R.string.more;
        }
    }

    // ------------------------------------------------------------------------
    // Interval selection dialog (shows all intervals)
    // ------------------------------------------------------------------------
    private void showMoreIntervalsDialog() {
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
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        titleLp.bottomMargin = pad;
        title.setLayoutParams(titleLp);
        root.addView(title);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        grid.setUseDefaultMargins(false);

        android.content.res.Resources res = getResources();
        String[] realLoad = {"", "1m", "3m", "5m", "15m", "30m", "1h", "2h", "4h", "6h", "12h", "1d", "1w", "1M"};
        int[] intervalLabels = {
                R.string.time,
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

        final AlertDialog dialog = new AlertDialog.Builder(this)
               .setView(root)
               .setNegativeButton(R.string.close, (d, w) -> d.dismiss())
               .create();

        for (int i = 0; i < realLoad.length; i++) {
            TextView tv = new TextView(this);
            tv.setText(intervalLabels[i]);
            tv.setTextSize(13f);
            tv.setGravity(Gravity.CENTER);
            tv.setSingleLine(true);
            int vPad = (int) (14 * res.getDisplayMetrics().density);
            tv.setPadding(0, vPad, 0, vPad);

            boolean isSelected = realLoad[i].equalsIgnoreCase(currentInterval);
            if (realLoad[i].equals("1m") && currentInterval.equals("1m")) {
                isSelected = true;
            }
            if (realLoad[i].equals("1M") && currentInterval.equals("1M")) {
                isSelected = true;
            }
            if (!realLoad[i].equals("1m") &&!realLoad[i].equals("1M")) {
                isSelected = realLoad[i].equalsIgnoreCase(currentInterval);
            }

            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(0f);
            if (isSelected) {
                bg.setColor(res.getColor(android.R.color.white, null));
                tv.setTextColor(res.getColor(android.R.color.black, null));
            } else {
                bg.setColor(getResources().getColor(R.color.chart_bg, getTheme()));
                bg.setStroke(
                        (int) (1 * res.getDisplayMetrics().density),
                        res.getColor(R.color.chart_grid, null)
                );
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
                if (load.isEmpty()) {
                    return;
                }
                currentInterval = load;
                getSharedPreferences(PREFS_CHART_STATE, MODE_PRIVATE)
                       .edit()
                       .putString(KEY_INTERVAL, currentInterval)
                       .apply();
                if (marketChartView!= null) {
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

    // ------------------------------------------------------------------------
    // Setup timeframe chip group
    // ------------------------------------------------------------------------
    private void setupTimeframeChips() {
        if (chipGroupTimeframe == null) {
            return;
        }

        android.content.res.Resources res = getResources();
        chipGroupTimeframe.removeAllViews();

        String[] outerValues = {"15m", "1h", "4h", "1d", "1M"};
        int[] outerLabels = {
                R.string.interval_15m,
                R.string.interval_1h,
                R.string.interval_4h,
                R.string.interval_1d,
                R.string.interval_1M
        };

        boolean isOuter = false;
        for (String v : outerValues) {
            if (v.equalsIgnoreCase(currentInterval)) {
                if (v.equals("1M") && currentInterval.equals("1m")) {
                    continue;
                }
                isOuter = true;
                break;
            }
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
        LinearLayout.LayoutParams lpTime = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lpTime.setMargins(2, 0, 2, 0);
        tvTime.setLayoutParams(lpTime);
        tvTime.setOnClickListener(v -> showMoreIntervalsDialog());
        chipGroupTimeframe.addView(tvTime);

        for (int idx = 0; idx < outerValues.length; idx++) {
            String realInterval = outerValues[idx];
            int resId = outerLabels[idx];
            TextView tv = new TextView(this);
            tv.setText(resId);
            tv.setTextSize(13f);
            tv.setSingleLine(true);
            tv.setPadding(padH, padV, padH, padV);

            boolean isSelected = realInterval.equalsIgnoreCase(currentInterval);
            if (realInterval.equals("1M") && currentInterval.equals("1m")) {
                isSelected = false;
            }
            if (realInterval.equalsIgnoreCase("1d") && currentInterval.equalsIgnoreCase("1d")) {
                isSelected = true;
            }

            if (isSelected) {
                tv.setTextColor(getThemeColor(android.R.attr.colorBackground));
                tv.setBackgroundResource(R.drawable.bg_time_selected);
            } else {
                tv.setTextColor(getThemeColor(android.R.attr.textColorSecondary));
                tv.setBackgroundColor(res.getColor(android.R.color.transparent, null));
            }

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(2, 0, 2, 0);
            tv.setLayoutParams(lp);

            final String load = realInterval;
            tv.setOnClickListener(v -> {
                currentInterval = load;
                getSharedPreferences(PREFS_CHART_STATE, MODE_PRIVATE)
                       .edit()
                       .putString(KEY_INTERVAL, currentInterval)
                       .apply();
                if (marketChartView!= null) {
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
        LinearLayout.LayoutParams lpMore = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lpMore.setMargins(2, 0, 2, 0);
        tvMore.setLayoutParams(lpMore);

        if (isOuter) {
            tvMore.setText(R.string.more);
            tvMore.setTextColor(getThemeColor(android.R.attr.textColorSecondary));
            tvMore.setBackgroundColor(res.getColor(android.R.color.transparent, null));
        } else {
            int labelRes = getLabelResForInterval(currentInterval);
            tvMore.setText(labelRes);
            tvMore.setTextColor(getThemeColor(android.R.attr.colorBackground));
            tvMore.setBackgroundResource(R.drawable.bg_time_selected);
        }
        tvMore.setOnClickListener(v -> showMoreIntervalsDialog());
        chipGroupTimeframe.addView(tvMore);
    }

    // ------------------------------------------------------------------------
    // Chart listener setup (price, ticker, MA, countdown, candle selection)
    // ------------------------------------------------------------------------
    private void setupChartListener() {
        if (marketChartView == null) {
            return;
        }

        final android.content.res.Resources res = getResources();

        marketChartView.setOnVolumeClickListener(candle -> runOnUiThread(() -> {
            if (popupCandleDetail == null || candle == null) {
                return;
            }
            popupCandleDetail.setVisibility(View.VISIBLE);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(getResources().getColor(R.color.chart_bg, getTheme()));
            bg.setCornerRadius(0f);
            bg.setStroke(
                    (int) (1 * res.getDisplayMetrics().density),
                    res.getColor(R.color.chart_grid, null)
            );
            popupCandleDetail.setBackground(bg);
            popupCandleDetail.setElevation(8f * res.getDisplayMetrics().density);
            if (popupTime!= null) {
                popupTime.setText(fullTimeFormat.format(new Date(candle.openTime)));
            }
            if (popupVolume!= null) {
                popupVolume.setText(getString(
                        R.string.chart_volume_label,
                        String.format(Locale.US, "%.2f", candle.volume)
                ));
            }
        }));

        marketChartView.setOnChartUpdateListener(new MarketChartView.OnChartUpdateListener() {
            @Override
            public void onPriceUpdate(final float price, float high24h, float low24h) {
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
                if (textCurrentPrice!= null) {
                    String symbol = getCurrencySymbol(currentFiatCode);
                    textCurrentPrice.setText(
                            String.format(Locale.US, "%s%,.2f", symbol, priceInFiat)
                    );
                    int color;
                    if (lastDisplayPrice == 0f) {
                        color = getThemeColor(android.R.attr.textColorPrimary);
                    } else if (priceInFiat > lastDisplayPrice) {
                        color = res.getColor(R.color.palette_green, null);
                    } else if (priceInFiat < lastDisplayPrice) {
                        color = res.getColor(R.color.palette_red, null);
                    } else {
                        color = res.getColor(R.color.chart_last_price_line, null);
                    }
                    textCurrentPrice.setTextColor(color);
                    lastDisplayPrice = (float) priceInFiat;
                }
            }

            @Override
            public void onTickerUpdate(final float high24h,
                                       final float low24h,
                                       final float volBtc,
                                       final float volUsdt,
                                       final float changePercent) {
                runOnUiThread(() -> {
                    if (textChange24h!= null) {
                        textChange24h.setText(String.format(Locale.US, "%.2f%%", changePercent));
                        int c = changePercent >= 0
                               ? res.getColor(R.color.palette_green, null)
                                : res.getColor(R.color.palette_red, null);
                        textChange24h.setTextColor(c);
                    }
                    new Thread(() -> {
                        double fiatPerBtc = getFiatPerBtc(currentFiatCode);
                        double basePerBtc = getFiatPerBtc("USD");
                        if (fiatPerBtc == 0d || basePerBtc == 0d) {
                            return;
                        }
                        double usdToFiat = fiatPerBtc / basePerBtc;
                        final double highFiat = high24h * usdToFiat;
                        final double lowFiat = low24h * usdToFiat;
                        final double volFiat = volUsdt * usdToFiat;

                        String baseAsset = currentSymbol;
                        if (baseAsset.endsWith("USDT")) {
                            baseAsset = baseAsset.substring(0, baseAsset.length() - 4);
                        } else if (baseAsset.endsWith("BUSD")) {
                            baseAsset = baseAsset.substring(0, baseAsset.length() - 4);
                        } else if (baseAsset.length() > 3) {
                            baseAsset = baseAsset.substring(0, 3);
                        }

                        final String highStr = getString(
                                R.string.chart_high_label,
                                String.format(Locale.US, "%,.2f", highFiat)
                        );
                        final String lowStr = getString(
                                R.string.chart_low_label,
                                String.format(Locale.US, "%,.2f", lowFiat)
                        );
                        final String volBtcStr = getString(
                                R.string.chart_vol_base_format,
                                baseAsset,
                                String.format(Locale.US, "%.2f", volBtc)
                        );
                        final String volFiatStr;
                        if (volFiat >= 1_000_000_000) {
                            volFiatStr = getString(
                                    R.string.chart_vol_quote_format,
                                    currentFiatCode,
                                    String.format(Locale.US, "%.2fB", volFiat / 1_000_000_000)
                            );
                        } else if (volFiat >= 1_000_000) {
                            volFiatStr = getString(
                                    R.string.chart_vol_quote_format,
                                    currentFiatCode,
                                    String.format(Locale.US, "%.2fM", volFiat / 1_000_000)
                            );
                        } else {
                            volFiatStr = getString(
                                    R.string.chart_vol_quote_format,
                                    currentFiatCode,
                                    String.format(Locale.US, "%.2f", volFiat)
                            );
                        }
                        mainHandler.post(() -> {
                            if (textHigh24h!= null) {
                                textHigh24h.setText(highStr);
                            }
                            if (textLow24h!= null) {
                                textLow24h.setText(lowStr);
                            }
                            if (textVolBtc!= null) {
                                textVolBtc.setText(volBtcStr);
                            }
                            if (textVolFiat!= null) {
                                textVolFiat.setText(volFiatStr);
                            }
                        });
                    }).start();
                });
            }

            @Override
            public void onMaUpdate(final List<Float> maValues) {
                runOnUiThread(() -> new Thread(() -> {
                    double fiatPerBtc = getFiatPerBtc(currentFiatCode);
                    double basePerBtc = getFiatPerBtc("USD");
                    double usdToFiat = 1d;
                    if (fiatPerBtc!= 0d && basePerBtc!= 0d) {
                        usdToFiat = fiatPerBtc / basePerBtc;
                    }
                    final double finalUsdToFiat = usdToFiat;
                    mainHandler.post(() -> {
                        if (textMaLabel!= null) {
                            if (maValues == null || maValues.isEmpty()) {
                                textMaLabel.setText(getString(R.string.chart_ma_default));
                            } else {
                                List<MarketChartView.MaLine> lines = marketChartView.getMaLines();
                                SpannableStringBuilder sb = new SpannableStringBuilder();
                                for (int i = 0; i < lines.size(); i++) {
                                    if (i >= maValues.size()) {
                                        break;
                                    }
                                    float value = maValues.get(i);
                                    if (value == 0f) {
                                        continue;
                                    }
                                    double fiatVal = value * finalUsdToFiat;
                                    String label = String.format(
                                            Locale.US,
                                            "MA%d: %,.2f",
                                            lines.get(i).period,
                                            fiatVal
                                    );
                                    if (sb.length() > 0) {
                                        sb.append(getString(R.string.bullet_separator));
                                    }
                                    int start = sb.length();
                                    sb.append(label);
                                    sb.setSpan(
                                            new ForegroundColorSpan(lines.get(i).color),
                                            start,
                                            start + label.length(),
                                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                    );
                                }
                                textMaLabel.setText(sb);
                            }
                        }
                    });
                }).start());
            }

            @Override
            public void onCountdownUpdate(final String countdown) {
                runOnUiThread(() -> {
                    if (textCountdown!= null) {
                        textCountdown.setText(getString(R.string.chart_close_in, countdown));
                    }
                    if (marketChartView!= null) {
                        marketChartView.setCountdown(countdown);
                    }
                });
            }

            @Override
            public void onCandleSelected(final MarketChartView.Candle candle) {
                runOnUiThread(() -> {
                    if (popupCandleDetail == null || candle == null) {
                        return;
                    }
                    new Thread(() -> {
                        double fiatPerBtc = getFiatPerBtc(currentFiatCode);
                        double basePerBtc = getFiatPerBtc("USD");
                        double usdToFiat = 1d;
                        if (fiatPerBtc!= 0d && basePerBtc!= 0d) {
                            usdToFiat = fiatPerBtc / basePerBtc;
                        }
                        final double openFiat = candle.open * usdToFiat;
                        final double highFiat = candle.high * usdToFiat;
                        final double lowFiat = candle.low * usdToFiat;
                        final double closeFiat = candle.close * usdToFiat;
                        mainHandler.post(() -> {
                            popupCandleDetail.setVisibility(View.VISIBLE);
                            GradientDrawable bg = new GradientDrawable();
                            bg.setColor(getResources().getColor(R.color.chart_bg, getTheme()));
                            bg.setCornerRadius(0f);
                            bg.setStroke(
                                    (int) (1 * res.getDisplayMetrics().density),
                                    res.getColor(R.color.chart_grid, null)
                            );
                            popupCandleDetail.setBackground(bg);
                            popupCandleDetail.setElevation(
                                    8f * res.getDisplayMetrics().density
                            );
                            if (popupTime!= null) {
                                popupTime.setText(fullTimeFormat.format(
                                        new Date(candle.openTime)
                                ));
                            }
                            if (popupOpen!= null) {
                                popupOpen.setText(getString(
                                        R.string.chart_open_label,
                                        String.format(Locale.US, "%,.2f", openFiat)
                                ));
                            }
                            if (popupHigh!= null) {
                                popupHigh.setText(getString(
                                        R.string.chart_high_detail,
                                        String.format(Locale.US, "%,.2f", highFiat)
                                ));
                            }
                            if (popupLow!= null) {
                                popupLow.setText(getString(
                                        R.string.chart_low_detail,
                                        String.format(Locale.US, "%,.2f", lowFiat)
                                ));
                            }
                            if (popupClose!= null) {
                                popupClose.setText(getString(
                                        R.string.chart_close_label,
                                        String.format(Locale.US, "%,.2f", closeFiat)
                                ));
                            }
                            if (popupVolume!= null) {
                                popupVolume.setText(getString(
                                        R.string.chart_volume_label,
                                        String.format(Locale.US, "%.2f", candle.volume)
                                ));
                            }
                        });
                    }).start();
                });
            }

            @Override
            public void onNothingSelected() {
                runOnUiThread(() -> {
                    if (popupCandleDetail!= null) {
                        popupCandleDetail.setVisibility(View.GONE);
                    }
                });
            }
        });
    }
}
