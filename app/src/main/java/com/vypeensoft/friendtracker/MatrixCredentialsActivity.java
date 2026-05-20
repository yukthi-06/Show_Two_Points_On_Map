package com.vypeensoft.friendtracker;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.vypeensoft.friendtracker.util.SettingsPersistenceManager;
import java.util.HashMap;
import java.util.Map;

public class MatrixCredentialsActivity extends AppCompatActivity {

    private TextInputEditText editHomeserver, editUsername, editPassword, editDisplayName;
    private MaterialAutoCompleteTextView editPollingPeriod;
    private Button btnSave;

    private static final String[] POLLING_OPTIONS = {"5 Seconds", "10 Seconds", "30 Seconds", "1 Minute", "2 Minutes", "5 Minutes"};
    private static final Map<String, Long> POLLING_VALUES = new HashMap<String, Long>() {{
        put("5 Seconds", 5000L);
        put("10 Seconds", 10000L);
        put("30 Seconds", 30000L);
        put("1 Minute", 60000L);
        put("2 Minutes", 120000L);
        put("5 Minutes", 300000L);
    }};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_matrix_credentials);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        editHomeserver = findViewById(R.id.edit_matrix_homeserver);
        editUsername = findViewById(R.id.edit_matrix_username);
        editDisplayName = findViewById(R.id.edit_matrix_display_name);
        editPassword = findViewById(R.id.edit_matrix_password);
        editPollingPeriod = findViewById(R.id.edit_matrix_polling_period);
        btnSave = findViewById(R.id.btn_save);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, POLLING_OPTIONS);
        editPollingPeriod.setAdapter(adapter);

        loadConfig();

        btnSave.setOnClickListener(v -> saveConfig());
    }

    private void loadConfig() {
        SharedPreferences prefs = getSharedPreferences(MapSettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
        editHomeserver.setText(prefs.getString(MapSettingsActivity.KEY_MATRIX_HOMESERVER, "https://matrix-client.matrix.org"));
        editUsername.setText(prefs.getString(MapSettingsActivity.KEY_MATRIX_USERNAME, ""));
        editDisplayName.setText(prefs.getString(MapSettingsActivity.KEY_MATRIX_DISPLAY_NAME, ""));
        editPassword.setText(prefs.getString(MapSettingsActivity.KEY_MATRIX_PASSWORD, ""));

        long period = prefs.getLong(MapSettingsActivity.KEY_MATRIX_POLLING_PERIOD, 10000L);
        String periodText = "10 Seconds";
        for (Map.Entry<String, Long> entry : POLLING_VALUES.entrySet()) {
            if (entry.getValue() == period) {
                periodText = entry.getKey();
                break;
            }
        }
        editPollingPeriod.setText(periodText, false);
    }

    private void saveConfig() {
        SharedPreferences prefs = getSharedPreferences(MapSettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putString(MapSettingsActivity.KEY_MATRIX_HOMESERVER, editHomeserver.getText().toString().trim());
        editor.putString(MapSettingsActivity.KEY_MATRIX_USERNAME, editUsername.getText().toString().trim());
        editor.putString(MapSettingsActivity.KEY_MATRIX_DISPLAY_NAME, editDisplayName.getText().toString().trim());
        editor.putString(MapSettingsActivity.KEY_MATRIX_PASSWORD, editPassword.getText().toString().trim());

        String periodText = editPollingPeriod.getText().toString();
        long period = POLLING_VALUES.getOrDefault(periodText, 10000L);
        editor.putLong(MapSettingsActivity.KEY_MATRIX_POLLING_PERIOD, period);

        // Clear cached token when credentials change
        editor.remove(MapSettingsActivity.KEY_MATRIX_TOKEN);

        if (editor.commit()) {
            SettingsPersistenceManager.exportSettings(this);
            Toast.makeText(this, "Credentials Saved", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            Toast.makeText(this, "Failed to Save Credentials", Toast.LENGTH_SHORT).show();
        }
    }
}
