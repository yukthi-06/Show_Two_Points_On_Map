package com.vypeensoft.friendtracker;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class CloudflareSettingsActivity extends AppCompatActivity {

    private EditText editUrl;
    private EditText editInterval;
    private Button btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cloudflare_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Cloudflare Settings");
        }

        editUrl = findViewById(R.id.edit_cloudflare_url);
        editInterval = findViewById(R.id.edit_cloudflare_polling_interval);
        btnSave = findViewById(R.id.btn_save_cloudflare);

        loadSettings();

        btnSave.setOnClickListener(v -> saveSettings());
    }

    private void loadSettings() {
        File file = getSettingsFile();
        if (file != null && file.exists()) {
            try {
                BufferedReader br = new BufferedReader(new FileReader(file));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                br.close();

                JSONObject json = new JSONObject(sb.toString());
                if (json.has("cloudflareUrl")) {
                    editUrl.setText(json.getString("cloudflareUrl"));
                }
                if (json.has("cloudflarePollingInterval")) {
                    editInterval.setText(String.valueOf(json.getInt("cloudflarePollingInterval")));
                }
            } catch (Exception e) {
                android.util.Log.e("FriendTracker", "Error loading Cloudflare settings", e);
            }
        }
    }

    private void saveSettings() {
        String url = editUrl.getText().toString().trim();
        String intervalStr = editInterval.getText().toString().trim();

        if (url.isEmpty()) {
            Toast.makeText(this, "Please enter a Server URL", Toast.LENGTH_SHORT).show();
            return;
        }

        int interval = 5; // default fallback
        if (!intervalStr.isEmpty()) {
            try {
                interval = Integer.parseInt(intervalStr);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Please enter a valid number for polling interval", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        try {
            JSONObject json = new JSONObject();
            json.put("cloudflareUrl", url);
            json.put("cloudflarePollingInterval", interval);

            File file = getSettingsFile();
            if (file != null) {
                File dir = file.getParentFile();
                if (dir != null && !dir.exists()) {
                    dir.mkdirs();
                }

                FileWriter writer = new FileWriter(file, false);
                writer.write(json.toString(4));
                writer.flush();
                writer.close();

                Toast.makeText(this, "Settings saved successfully", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(this, "Could not find storage directory to save settings", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            android.util.Log.e("FriendTracker", "Error saving Cloudflare settings", e);
            Toast.makeText(this, "Error saving settings: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private File getSettingsFile() {
        File primaryDir = new File("/sdcard/Vypeensoft/Friends_Location_Tracker/settings");
        File primaryFile = new File(primaryDir, "cloudflare.json");
        
        // If the primary directory exists or can be created, use the primary file path
        if (primaryDir.exists() || primaryDir.mkdirs()) {
            return primaryFile;
        }
        
        // Otherwise, fall back to external storage directory
        File fallbackDir = new File(android.os.Environment.getExternalStorageDirectory(), "Vypeensoft/Friends_Location_Tracker/settings");
        if (!fallbackDir.exists()) {
            fallbackDir.mkdirs();
        }
        return new File(fallbackDir, "cloudflare.json");
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
