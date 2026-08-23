package wallet.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import wallet.R;
import wallet.ui.indicator.MaSettingsActivity;

public class IndicatorSettingsActivity extends Activity implements View.OnClickListener {

    private View rowMa;
    private View rowEma;
    private View rowBoll;
    private View rowSar;
    private View rowVol;
    private View rowMacd;
    private View rowRsi;
    private View rowKdj;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_indicator_settings);

        rowMa = findViewById(R.id.row_ma);
        rowEma = findViewById(R.id.row_ema);
        rowBoll = findViewById(R.id.row_boll);
        rowSar = findViewById(R.id.row_sar);
        rowVol = findViewById(R.id.row_vol);
        rowMacd = findViewById(R.id.row_macd);
        rowRsi = findViewById(R.id.row_rsi);
        rowKdj = findViewById(R.id.row_kdj);

        rowMa.setOnClickListener(this);
        rowEma.setOnClickListener(this);
        rowBoll.setOnClickListener(this);
        rowSar.setOnClickListener(this);
        rowVol.setOnClickListener(this);
        rowMacd.setOnClickListener(this);
        rowRsi.setOnClickListener(this);
        rowKdj.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        Intent intent = new Intent(IndicatorSettingsActivity.this, MaSettingsActivity.class);
        if (id == R.id.row_ma) {
            intent.putExtra("indicator_type", "MA");
        } else if (id == R.id.row_ema) {
            intent.putExtra("indicator_type", "EMA");
        } else if (id == R.id.row_boll) {
            intent.putExtra("indicator_type", "BOLL");
        } else if (id == R.id.row_sar) {
            intent.putExtra("indicator_type", "SAR");
        } else if (id == R.id.row_vol) {
            intent.putExtra("indicator_type", "VOL");
        } else if (id == R.id.row_macd) {
            intent.putExtra("indicator_type", "MACD");
        } else if (id == R.id.row_rsi) {
            intent.putExtra("indicator_type", "RSI");
        } else if (id == R.id.row_kdj) {
            intent.putExtra("indicator_type", "KDJ");
        } else {
            return;
        }
        startActivityForResult(intent, 1001);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            setResult(RESULT_OK);
        }
    }
}
