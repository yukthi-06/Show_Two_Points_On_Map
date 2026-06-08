package com.vypeensoft.friendtracker.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.vypeensoft.friendtracker.model.LocationMessage;
import com.vypeensoft.friendtracker.network.MatrixClient;
import com.vypeensoft.friendtracker.MapSettingsActivity;
import com.vypeensoft.friendtracker.util.AppLogger;
import android.content.SharedPreferences;
import android.content.Context;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

public class LocationService extends Service {
    private static final String TAG = "LocationService";
    private static final String CHANNEL_ID = "LocationServiceChannel";
    private static final int NOTIFICATION_ID = 123;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private MatrixClient matrixClient;
    private String userId = "user_" + Build.ID; // Simple default userId
    private final android.os.Handler cloudflareHandler = new android.os.Handler(Looper.getMainLooper());
    private Runnable cloudflareRunnable;
    private Location lastKnownLocation;

    @Override
    public void onCreate() {
        super.onCreate();
        AppLogger.log(this, TAG, "LocationService created");
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        matrixClient = new MatrixClient(this);
        createNotificationChannel();
        
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;
                for (Location location : locationResult.getLocations()) {
                    lastKnownLocation = location;
                    onLocationUpdated(location);
                }
            }
        };
    }

    @SuppressLint("MissingPermission")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        AppLogger.log(this, TAG, "LocationService starting...");
        startForeground(NOTIFICATION_ID, getNotification());
        requestLocationUpdates();
        startCloudflareLoop();
        return START_STICKY;
    }

    private void requestLocationUpdates() {
        SharedPreferences prefs = getSharedPreferences(MapSettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
        long gpsIntervalSec = prefs.getLong(MapSettingsActivity.KEY_GPS_REFRESH_INTERVAL, 10L);
        long gpsIntervalMs = gpsIntervalSec * 1000L;
        if (gpsIntervalMs < 1000L) {
            gpsIntervalMs = 10000L;
        }
        
        Log.d(TAG, "Requesting location updates with customized GPS refresh interval: " + gpsIntervalMs + "ms");

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, gpsIntervalMs)
                .setMinUpdateIntervalMillis(gpsIntervalMs / 2)
                .build();

        try {
            // Remove previous updates before requesting new ones to prevent overlaps
            fusedLocationClient.removeLocationUpdates(locationCallback);
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission missing: " + e.getMessage());
        }
    }

    private void onLocationUpdated(Location location) {
        AppLogger.log(this, TAG, "GPS Polled - Coordinates: " + location.getLatitude() + ", " + location.getLongitude());
        
        // Refresh config to pick up the latest active room choice
        matrixClient.loadConfig(this);
        
        String currentUserId = userId;
        String displayName = matrixClient.getDisplayName();
        if (displayName != null && !displayName.isEmpty()) {
            currentUserId = displayName;
        }
        
        LocationMessage message = new LocationMessage(currentUserId, location.getLatitude(), location.getLongitude());
        matrixClient.sendLocation(message);
        
        // Clean sender for session file name
        String cleanSender = currentUserId;
        if (cleanSender.contains(":")) {
            cleanSender = cleanSender.split(":")[0];
        }
        if (cleanSender.startsWith("@")) {
            cleanSender = cleanSender.substring(1);
        }
        cleanSender = cleanSender.replaceAll("[^a-zA-Z0-9_.-]", "_");
        
        // Write our own GPS location to sessions folder
        writeSelfLocationToSessions(cleanSender, currentUserId, location.getLatitude(), location.getLongitude());
        
        // Broadcast to Activity if it's running
        Intent intent = new Intent("com.vypeensoft.friendtracker.LOCATION_UPDATE");
        intent.putExtra("latitude", location.getLatitude());
        intent.putExtra("longitude", location.getLongitude());
        sendBroadcast(intent);
    }

    private void writeSelfLocationToSessions(String cleanSender, String displayName, double lat, double lon) {
        String[] paths = {
            "/sdcard/Vypeensoft/Friends_Location_Tracker/sessions"
        };

        for (String path : paths) {
            java.io.File dir = new java.io.File(path);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            if (!dir.exists()) {
                dir = new java.io.File(android.os.Environment.getExternalStorageDirectory(), path.replace("/sdcard/", ""));
                if (!dir.exists()) {
                    dir.mkdirs();
                }
            }

            java.io.File file = new java.io.File(dir, cleanSender + ".txt");
            try {
                String content = displayName + "|" + lat + "|" + lon + "|#1976D2";
                java.io.FileWriter writer = new java.io.FileWriter(file, false); // false to overwrite
                writer.write(content);
                writer.close();
                Log.i(TAG, "Successfully wrote self location to " + file.getAbsolutePath());
            } catch (Exception e) {
                Log.e(TAG, "Error writing self location to sessions folder: " + path, e);
            }
        }
    }

    private Notification getNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Friend Tracker")
                .setContentText("Tracking location in background...")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Location Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        AppLogger.log(this, TAG, "LocationService destroyed");
        fusedLocationClient.removeLocationUpdates(locationCallback);
        stopCloudflareLoop();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startCloudflareLoop() {
        stopCloudflareLoop();
        AppLogger.log(this, TAG, "Cloudflare tracking loop starting...");

        cloudflareRunnable = new Runnable() {
            @Override
            public void run() {
                String url = loadCloudflareUrl();
                int intervalSec = loadCloudflareInterval();
                if (intervalSec < 1) intervalSec = 5;

                AppLogger.log(LocationService.this, TAG, "Cloudflare loop triggered. Configured URL: " + url + ", Polling Interval: " + intervalSec + "s");

                if (!url.isEmpty() && lastKnownLocation != null) {
                    try {
                        SharedPreferences friendPrefs = getSharedPreferences("friend_tracker_prefs", MODE_PRIVATE);
                        java.util.Set<String> trackedFriends = friendPrefs.getStringSet("tracked_friends", null);
                        
                        AppLogger.log(LocationService.this, TAG, "Tracked friends retrieved: " + (trackedFriends != null ? trackedFriends.toString() : "null"));

                        String sessionId = "";
                        if (trackedFriends != null && !trackedFriends.isEmpty()) {
                            java.util.List<String> list = new java.util.ArrayList<>();
                            for (String f : trackedFriends) {
                                list.add(f.trim().toLowerCase());
                            }
                            java.util.Collections.sort(list);
                            StringBuilder sb = new StringBuilder();
                            for (int i = 0; i < list.size(); i++) {
                                sb.append(list.get(i));
                                if (i < list.size() - 1) {
                                    sb.append("_");
                                }
                            }
                            sessionId = sb.toString();
                        }

                        SharedPreferences appConfig = getSharedPreferences("AppConfig", MODE_PRIVATE);
                        String userName = appConfig.getString("current_user", "");

                        AppLogger.log(LocationService.this, TAG, "Constructed session ID: " + sessionId + ", User Name: " + userName);

                        JSONObject payload = new JSONObject();
                        payload.put("sessionid", sessionId);
                        payload.put("userName", userName);
                        payload.put("latitude", lastKnownLocation.getLatitude());
                        payload.put("longitude", lastKnownLocation.getLongitude());
                        payload.put("timestamp", System.currentTimeMillis());

                        AppLogger.log(LocationService.this, TAG, "Prepared payload: " + payload.toString());

                        sendCloudflareUpdate(url, payload);
                    } catch (Exception e) {
                        AppLogger.logError(LocationService.this, TAG, "Error in Cloudflare tracking loop", e);
                    }
                } else {
                    if (url.isEmpty()) {
                        AppLogger.log(LocationService.this, TAG, "Cloudflare tracking loop: skipped run because URL is empty.");
                    }
                    if (lastKnownLocation == null) {
                        AppLogger.log(LocationService.this, TAG, "Cloudflare tracking loop: skipped run because no GPS location signal is available yet.");
                    }
                }

                cloudflareHandler.postDelayed(this, intervalSec * 1000L);
            }
        };

        cloudflareHandler.post(cloudflareRunnable);
    }

    private void stopCloudflareLoop() {
        if (cloudflareRunnable != null) {
            AppLogger.log(this, TAG, "Stopping Cloudflare tracking loop");
            cloudflareHandler.removeCallbacks(cloudflareRunnable);
            cloudflareRunnable = null;
        }
    }

    private String loadCloudflareUrl() {
        try {
            File file = new File("/sdcard/Vypeensoft/Friends_Location_Tracker/settings/cloudflare.json");
            if (!file.exists()) {
                file = new File(android.os.Environment.getExternalStorageDirectory(), "Vypeensoft/Friends_Location_Tracker/settings/cloudflare.json");
            }
            if (file.exists()) {
                BufferedReader br = new BufferedReader(new FileReader(file));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                br.close();
                JSONObject json = new JSONObject(sb.toString());
                return json.optString("cloudflareUrl", "");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading cloudflare url", e);
        }
        return "";
    }

    private int loadCloudflareInterval() {
        try {
            File file = new File("/sdcard/Vypeensoft/Friends_Location_Tracker/settings/cloudflare.json");
            if (!file.exists()) {
                file = new File(android.os.Environment.getExternalStorageDirectory(), "Vypeensoft/Friends_Location_Tracker/settings/cloudflare.json");
            }
            if (file.exists()) {
                BufferedReader br = new BufferedReader(new FileReader(file));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                br.close();
                JSONObject json = new JSONObject(sb.toString());
                return json.optInt("cloudflarePollingInterval", 5);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading cloudflare interval", e);
        }
        return 5;
    }

    private void sendCloudflareUpdate(String cloudflareUrl, JSONObject payload) {
        new Thread(() -> {
            java.net.HttpURLConnection conn = null;
            try {
                String urlStr = cloudflareUrl.trim();
                if (!urlStr.endsWith("/update")) {
                    if (urlStr.endsWith("/")) {
                        urlStr += "update";
                    } else {
                        urlStr += "/update";
                    }
                }
                AppLogger.log(LocationService.this, TAG, "Sending POST update to: " + urlStr);
                java.net.URL url = new java.net.URL(urlStr);
                conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                java.io.OutputStream os = conn.getOutputStream();
                os.write(payload.toString().getBytes("UTF-8"));
                os.close();

                int responseCode = conn.getResponseCode();
                AppLogger.log(LocationService.this, TAG, "Cloudflare POST response code: " + responseCode);

                if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
                    java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder response = new StringBuilder();
                    String inputLine;
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();

                    String jsonResponse = response.toString().trim();
                    AppLogger.log(LocationService.this, TAG, "Cloudflare POST response: " + jsonResponse);

                    processCloudflareResponse(jsonResponse);
                }
            } catch (Exception e) {
                AppLogger.logError(LocationService.this, TAG, "Error sending Cloudflare post request", e);
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }

    private void processCloudflareResponse(String jsonResponse) {
        try {
            if (jsonResponse.startsWith("[")) {
                org.json.JSONArray array = new org.json.JSONArray(jsonResponse);
                for (int i = 0; i < array.length(); i++) {
                    org.json.JSONObject obj = array.getJSONObject(i);
                    String name = obj.optString("userName", "").trim();
                    if (name.isEmpty()) {
                        name = obj.optString("username", "").trim();
                    }
                    double lat = obj.optDouble("latitude", 0.0);
                    double lon = obj.optDouble("longitude", 0.0);

                    if (!name.isEmpty() && lat != 0.0 && lon != 0.0) {
                        // Check if this is self
                        SharedPreferences appConfig = getSharedPreferences("AppConfig", MODE_PRIVATE);
                        String userName = appConfig.getString("current_user", "").trim();
                        if (name.equalsIgnoreCase(userName)) {
                            continue; // Skip self
                        }

                        // Clean username for session file name
                        String cleanSender = name;
                        if (cleanSender.contains(":")) {
                            cleanSender = cleanSender.split(":")[0];
                        }
                        if (cleanSender.startsWith("@")) {
                            cleanSender = cleanSender.substring(1);
                        }
                        cleanSender = cleanSender.replaceAll("[^a-zA-Z0-9_.-]", "_");

                        // Write to sessions
                        writeParticipantLocationToSessions(cleanSender, name, lat, lon);
                    }
                }
            }
        } catch (Exception e) {
            AppLogger.logError(this, TAG, "Error parsing Cloudflare response", e);
        }
    }

    private void writeParticipantLocationToSessions(String cleanSender, String displayName, double lat, double lon) {
        String[] paths = {
            "/sdcard/Vypeensoft/Friends_Location_Tracker/sessions"
        };

        for (String path : paths) {
            java.io.File dir = new java.io.File(path);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            if (!dir.exists()) {
                dir = new java.io.File(android.os.Environment.getExternalStorageDirectory(), path.replace("/sdcard/", ""));
                if (!dir.exists()) {
                    dir.mkdirs();
                }
            }

            java.io.File file = new java.io.File(dir, cleanSender + ".txt");
            try {
                String content = displayName + "|" + lat + "|" + lon + "|blue";
                java.io.FileWriter writer = new java.io.FileWriter(file, false); // false to overwrite
                writer.write(content);
                writer.close();
                Log.d(TAG, "Wrote participant " + displayName + " location to " + file.getAbsolutePath());
            } catch (Exception e) {
                Log.e(TAG, "Error writing participant location to sessions folder: " + path, e);
            }
        }
    }
}
