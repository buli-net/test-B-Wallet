package wallet.ui.indicator;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import wallet.R;

public class MaSettingsActivity extends Activity {

    private RecyclerView recyclerMa;
    private Button btnReset;
    private Button btnConfirm;
    private List<IndicatorRepository.MaLine> maLines;
    private MaAdapter maAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ma_settings);

        recyclerMa = findViewById(R.id.recycler_ma);
        btnReset = findViewById(R.id.btn_reset);
        btnConfirm = findViewById(R.id.btn_confirm);

        maLines = IndicatorRepository.load(this).main.maLines;

        recyclerMa.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        maAdapter = new MaAdapter(maLines);
        recyclerMa.setAdapter(maAdapter);

        btnReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                for (int i = 0; i < maLines.size(); i++) {
                    IndicatorRepository.MaLine line = maLines.get(i);
                    if (i == 0) {
                        line.enabled = true;
                        line.period = 7;
                    } else if (i == 1) {
                        line.enabled = true;
                        line.period = 25;
                    } else if (i == 2) {
                        line.enabled = true;
                        line.period = 99;
                    } else {
                        line.enabled = false;
                        line.period = 0;
                    }
                    line.width = 1f;
                }
                maAdapter.notifyDataSetChanged();
            }
        });

        btnConfirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                IndicatorRepository.Config config = IndicatorRepository.load(MaSettingsActivity.this);
                config.main.maLines = maLines;
                IndicatorRepository.save(MaSettingsActivity.this, config);
                setResult(RESULT_OK);
                finish();
            }
        });
    }

    public static class MaAdapter extends RecyclerView.Adapter<MaAdapter.MaViewHolder> {

        private List<IndicatorRepository.MaLine> dataList;

        public MaAdapter(List<IndicatorRepository.MaLine> dataList) {
            this.dataList = dataList;
        }

        @NonNull
        @Override
        public MaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ma_line, parent, false);
            return new MaViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull MaViewHolder holder, int position) {
            IndicatorRepository.MaLine line = dataList.get(position);
            holder.cbEnabled.setText("MA" + (position + 1));
            holder.cbEnabled.setChecked(line.enabled);
            if (line.period > 0) {
                holder.etPeriod.setText(String.valueOf(line.period));
            } else {
                holder.etPeriod.setText("");
            }
            holder.viewColor.setBackgroundColor(line.color);

            ArrayAdapter<String> widthAdapter = new ArrayAdapter<>(holder.itemView.getContext(), android.R.layout.simple_spinner_item, new String[]{"1", "2", "3"});
            widthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            holder.spWidth.setAdapter(widthAdapter);
            holder.spWidth.setSelection((int) line.width - 1);

            holder.cbEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
                line.enabled = isChecked;
            });

            holder.etPeriod.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) {
                    try {
                        String text = holder.etPeriod.getText().toString().trim();
                        if (text.isEmpty()) {
                            line.period = 0;
                        } else {
                            line.period = Integer.parseInt(text);
                        }
                    } catch (Exception e) {
                        line.period = 0;
                    }
                }
            });

            holder.spWidth.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                    line.width = pos + 1;
                }
                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });

            holder.viewColor.setOnClickListener(v -> {
                // TODO: show ColorPickerDialog, for now cycle colors
                int[] colors = {0xFFF0B90B, 0xFF9B59B6, 0xFF5DADE2, 0xFF2ECC71, 0xFFE74C3C, 0xFF1ABC9C};
                int idx = position % colors.length;
                line.color = colors[(idx + 1) % colors.length];
                holder.viewColor.setBackgroundColor(line.color);
            });
        }

        @Override
        public int getItemCount() {
            return dataList.size();
        }

        public static class MaViewHolder extends RecyclerView.ViewHolder {
            public CheckBox cbEnabled;
            public EditText etPeriod;
            public Spinner spWidth;
            public View viewColor;

            public MaViewHolder(@NonNull View itemView) {
                super(itemView);
                cbEnabled = itemView.findViewById(R.id.cb_enabled);
                etPeriod = itemView.findViewById(R.id.et_period);
                spWidth = itemView.findViewById(R.id.sp_width);
                viewColor = itemView.findViewById(R.id.view_color);
            }
        }
    }
}
