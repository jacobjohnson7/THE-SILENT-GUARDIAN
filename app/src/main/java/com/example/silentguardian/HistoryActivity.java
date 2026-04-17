package com.example.silentguardian;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;

public class HistoryActivity extends AppCompatActivity {

    private ListView listViewHistory;
    private TextView tvEmptyState;
    private Button btnClear;
    private View tableHeader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        listViewHistory = findViewById(R.id.listViewHistory);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        btnClear = findViewById(R.id.btnClear);
        tableHeader = findViewById(R.id.tableHeader);

        loadData();

        btnClear.setOnClickListener(v -> {
            HistoryManager.clearHistory(this);
            loadData();
            Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show();
        });
    }

    private void loadData() {
        List<HistoryManager.HistoryEvent> events = HistoryManager.loadHistory(this);
        
        if (events.isEmpty()) {
            tvEmptyState.setVisibility(View.VISIBLE);
            listViewHistory.setVisibility(View.GONE);
            tableHeader.setVisibility(View.GONE);
            btnClear.setEnabled(false);
            btnClear.setAlpha(0.5f);
        } else {
            tvEmptyState.setVisibility(View.GONE);
            listViewHistory.setVisibility(View.VISIBLE);
            tableHeader.setVisibility(View.VISIBLE);
            btnClear.setEnabled(true);
            btnClear.setAlpha(1.0f);
            
            HistoryAdapter adapter = new HistoryAdapter(this, events);
            listViewHistory.setAdapter(adapter);
        }
    }

    private class HistoryAdapter extends ArrayAdapter<HistoryManager.HistoryEvent> {
        public HistoryAdapter(android.content.Context context, List<HistoryManager.HistoryEvent> events) {
            super(context, 0, events);
        }

        @Override
        public View getView(int position, View convertView, android.view.ViewGroup parent) {
            if (convertView == null) {
                convertView = android.view.LayoutInflater.from(getContext()).inflate(R.layout.item_history, parent, false);
            }
            HistoryManager.HistoryEvent event = getItem(position);
            TextView tvDate = convertView.findViewById(R.id.tvDate);
            TextView tvSound = convertView.findViewById(R.id.tvSound);
            
            if (event != null) {
                tvDate.setText(event.timestamp);
                tvSound.setText(event.sound);
            }
            return convertView;
        }
    }
}
