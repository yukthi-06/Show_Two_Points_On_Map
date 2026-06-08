package com.vypeensoft.friendtracker;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import java.util.HashSet;
import java.util.Set;

public class FriendsActivity extends AppCompatActivity {

    private LinearLayout col1Container, col2Container;
    private Button btnTrackThem;
    private final java.util.List<CheckBox> checkBoxes = new java.util.ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friends);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Friends");
        }

        col1Container = findViewById(R.id.col1_container);
        col2Container = findViewById(R.id.col2_container);
        btnTrackThem = findViewById(R.id.btn_track_them);

        // Load friends from JSON (or fallback to hardcoded list)
        java.util.List<String> friends = loadFriendsFromJson();
        int col1Size = (friends.size() + 1) / 2;

        // Load saved selection
        SharedPreferences prefs = getSharedPreferences("friend_tracker_prefs", MODE_PRIVATE);
        Set<String> trackedFriends = prefs.getStringSet("tracked_friends", null);

        // Load configured Current User
        SharedPreferences appConfigPrefs = getSharedPreferences("AppConfig", MODE_PRIVATE);
        String currentUser = appConfigPrefs.getString("current_user", "").trim();

        // Dynamically populate columns with CheckBoxes
        for (int i = 0; i < friends.size(); i++) {
            String name = friends.get(i);
            CheckBox cb = new CheckBox(this);
            cb.setText(name);
            cb.setTextSize(16);
            cb.setPadding(8, 8, 8, 8);
            cb.setTextColor(android.graphics.Color.parseColor("#212121"));
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                cb.setButtonTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#1976D2")));
            }
            if (!currentUser.isEmpty() && name.equalsIgnoreCase(currentUser)) {
                cb.setChecked(true);
                cb.setEnabled(false); // Make it un-selectable
            } else if (trackedFriends != null && trackedFriends.contains(name)) {
                cb.setChecked(true);
            }
            checkBoxes.add(cb);

            if (i < col1Size) {
                col1Container.addView(cb);
            } else {
                col2Container.addView(cb);
            }
        }

        btnTrackThem.setOnClickListener(v -> {
            Set<String> selected = new HashSet<>();
            for (CheckBox cb : checkBoxes) {
                if (cb.isChecked()) {
                    selected.add(cb.getText().toString());
                }
            }
            prefs.edit().putStringSet("tracked_friends", selected).apply();
            
            android.widget.Toast.makeText(this, "Tracking selected friends...", android.widget.Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private java.util.List<String> loadFriendsFromJson() {
        java.util.List<String> friends = new java.util.ArrayList<>();
        java.io.File file = new java.io.File("/sdcard/Vypeensoft/Friends_Location_Tracker/settings/friends.json");
        if (!file.exists()) {
            file = new java.io.File(android.os.Environment.getExternalStorageDirectory(), "Vypeensoft/Friends_Location_Tracker/settings/friends.json");
        }

        if (file.exists()) {
            try {
                java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(file));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                br.close();

                String jsonStr = sb.toString().trim();
                if (jsonStr.startsWith("[")) {
                    org.json.JSONArray array = new org.json.JSONArray(jsonStr);
                    for (int i = 0; i < array.length(); i++) {
                        friends.add(array.getString(i));
                    }
                } else if (jsonStr.startsWith("{")) {
                    org.json.JSONObject obj = new org.json.JSONObject(jsonStr);
                    if (obj.has("friends")) {
                        org.json.JSONArray array = obj.getJSONArray("friends");
                        for (int i = 0; i < array.length(); i++) {
                            friends.add(array.getString(i));
                        }
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("FriendTracker", "Error loading friends from JSON", e);
            }
        }

        if (friends.isEmpty()) {
            friends.add("Shibu");
            friends.add("Syama");
            friends.add("Yukthi");
            friends.add("Lakshya");
            friends.add("Suseela");
            friends.add("Binu");
            friends.add("Sheeba");
            friends.add("Neha");
            friends.add("Nisha");
        }
        return friends;
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
