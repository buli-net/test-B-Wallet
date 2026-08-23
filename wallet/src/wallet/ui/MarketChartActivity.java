package wallet.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
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
        put("RON", "lei");
        put("LEU", "lei");
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

        if (textMaLabel!= null)
        {
            textMaLabel.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    showMaSettingsPopup();
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

    private void showMaSettingsPopup()
    {
        if (marketChartView == null)
        {
            return;
        }
        Dialog dialog = new Dialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_ma_settings, null);
        RecyclerView recycler = view.findViewById(R.id.recycler_ma_popup);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setNestedScrollingEnabled(false);

        List<MarketChartView.MaLine> tempList = new ArrayList<>(marketChartView.getMaLines());
        MaPopupAdapter adapter = new MaPopupAdapter(tempList);
        recycler.setAdapter(adapter);

        View btnAdd = view.findViewById(R.id.btn_add_ma);
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

        View btnApply = view.findViewById(R.id.btn_apply);
        btnApply.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                marketChartView.setMaLines(tempList);
                dialog.dismiss();
            }
        });

        dialog.setContentView(view);
        if (dialog.getWindow()!= null)
        {
            int width = ViewGroup.LayoutParams.MATCH_PARENT;
            int height = (int) (getResources().getDisplayMetrics().heightPixels * 0.9f);
            dialog.getWindow().setLayout(width, height);
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
            h.color.setBackgroundColor(line.color);

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
                    h.color.setBackgroundColor(next);
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
        mainHandler.post(() ->
        {
            if (textFiat!= null)
            {
                textFiat.setText(currentFiatCode);
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
        root.setBackgroundColor(getThemeColor(android.R.attr.colorBackground));
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
            if (!realLoad[i].equals("1m") &&!realLoad[i].equals("1M"))
            {
                isSelected = realLoad[i].equalsIgnoreCase(currentInterval);
            }

            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(10f * res.getDisplayMetrics().density);
            bg.setColor(getThemeColor(android.R.attr.colorBackground));
            if (isSelected)
            {
                bg.setStroke((int) (2 * res.getDisplayMetrics().density), getThemeColor(android.R.attr.textColorPrimary));
                tv.setTextColor(getThemeColor(android.R.attr.textColorPrimary));
            }
            else
            {
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
            tv.setOnClickListener(v ->
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
            tv.setOnClickListener(v ->
            {
                currentInterval = load;
                if (marketChartView!= null)
                {
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
        Resources res = getResources();

        marketChartView.setOnVolumeClickListener(candle ->
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
                                    color = res.getColor(R.color.chart_bull, null);
                                }
                                else if (priceInFiat < lastDisplayPrice)
                                {
                                    color = res.getColor(R.color.chart_bear, null);
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
                            c = res.getColor(R.color.chart_bull, null);
                        }
                        else
                        {
                            c = res.getColor(R.color.chart_bear, null);
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
            public void onMaUpdate(List<Float> maValues)
            {
                runOnUiThread(() ->
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
                                if (i >= maValues.size()) break;
                                float value = maValues.get(i);
                                if (value == 0f) continue;
                                String label = String.format(Locale.US, "MA%d: %.2f", lines.get(i).period, value);
                                if (sb.length() > 0) sb.append(" • ");
                                int start = sb.length();
                                sb.append(label);
                                sb.setSpan(new ForegroundColorSpan(lines.get(i).color), start, start + label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            }
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
                    bg.setStroke((int) (1 * res.getDisplayMetrics().density), res.getColor(R.color.chart_grid, null));
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
