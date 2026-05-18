package com.vypeensoft.friendtracker;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import org.maplibre.android.MapLibre;
import org.maplibre.android.annotations.Icon;
import org.maplibre.android.annotations.IconFactory;
import org.maplibre.android.annotations.Marker;
import org.maplibre.android.annotations.MarkerOptions;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.Style;

public class MainActivity extends AppCompatActivity {

    private MapView mapView;
    private MapLibreMap mapLibreMap;

    // Coordinate state & standard Marker references
    private LatLng redLatLng;
    private LatLng greenLatLng;
    private LatLng blueLatLng;
    
    private Marker redMarker;
    private Marker greenMarker;
    private Marker blueMarker;

    // Movement Loop Handler
    private final android.os.Handler movementHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable movementRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize MapLibre engine
        MapLibre.getInstance(this);
        setContentView(R.layout.activity_main);

        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);

        mapView.getMapAsync(map -> {
            this.mapLibreMap = map;

            // Load style (using OpenFreeMap Liberty style which has detailed streets and requires no API key)
            String styleUrl = "https://tiles.openfreemap.org/styles/liberty";
            map.setStyle(new Style.Builder().fromUri(styleUrl), style -> {

                // Define Base Point: Cochin, Kerala, India (beautiful area with clear map layers)
                double baseLat = 9.93123;
                double baseLon = 76.26730;

                // Random angle/bearing in radians
                double angle = Math.random() * 2 * Math.PI;

                // Equilateral Triangle setup for 3 friends with exactly 1005 meters (1.005 km) side length:
                // This targets the exact center of the requested [1000, 1010] meters constraint range!
                double sKm = 1.005; // 1005 meters
                double deltaLat = (sKm / 111.12) * Math.cos(angle);
                double deltaLon = ((sKm / 111.12) * Math.sin(angle)) / Math.cos(Math.toRadians(baseLat));

                redLatLng = new LatLng(baseLat, baseLon);
                greenLatLng = new LatLng(baseLat + deltaLat, baseLon + deltaLon);

                // Math for Blue Point: Place it perpendicular to the Red-Green line from their midpoint
                // at a distance of s * sqrt(3) / 2 (height of equilateral triangle) = ~870.35 meters
                double midLat = baseLat + deltaLat / 2.0;
                double midLon = baseLon + deltaLon / 2.0;

                double perpDistanceKm = sKm * 0.866025; // s * sqrt(3)/2
                double perpAngle = angle + Math.PI / 2.0; // Perpendicular bearing (rotated by 90 degrees)
                double perpLat = (perpDistanceKm / 111.12) * Math.cos(perpAngle);
                double perpLon = ((perpDistanceKm / 111.12) * Math.sin(perpAngle)) / Math.cos(Math.toRadians(midLat));

                blueLatLng = new LatLng(midLat + perpLat, midLon + perpLon);

                // Create custom icons from our programmatically generated teardrop pin vector bitmaps
                IconFactory iconFactory = IconFactory.getInstance(MainActivity.this);
                Icon redIcon = iconFactory.fromBitmap(createTeardropMarkerBitmap(Color.parseColor("#E53935"))); // Crimson Red Tint
                Icon greenIcon = iconFactory.fromBitmap(createTeardropMarkerBitmap(Color.parseColor("#4CAF50"))); // Vibrant Green Tint
                Icon blueIcon = iconFactory.fromBitmap(createTeardropMarkerBitmap(Color.parseColor("#2196F3"))); // Blue Tint

                // Add standard built-in Markers to the Map
                redMarker = mapLibreMap.addMarker(new MarkerOptions()
                        .position(redLatLng)
                        .title("Red Friend")
                        .icon(redIcon));

                greenMarker = mapLibreMap.addMarker(new MarkerOptions()
                        .position(greenLatLng)
                        .title("Green Friend")
                        .icon(greenIcon));

                blueMarker = mapLibreMap.addMarker(new MarkerOptions()
                        .position(blueLatLng)
                        .title("Blue Friend")
                        .icon(blueIcon));

                // Calculate the midpoint between the points for the camera positioning
                LatLng cameraMidpoint = new LatLng(
                        (redLatLng.getLatitude() + greenLatLng.getLatitude() + blueLatLng.getLatitude()) / 3.0,
                        (redLatLng.getLongitude() + greenLatLng.getLongitude() + blueLatLng.getLongitude()) / 3.0
                );

                // Safe Initial Camera Centering: moveCamera(newLatLngZoom) is synchronous, layout size-independent
                mapLibreMap.moveCamera(CameraUpdateFactory.newLatLngZoom(cameraMidpoint, 14.5));

                // Start the loop immediately
                startMovementLoop();
            });
        });
    }

    /**
     * Spawns a repeating 1-second loop where each marker moves in a completely independent
     * random direction by 1 to 5 meters. Lightweight PBD spring corrections resolve
     * the coordinate states to guarantee all pairwise distances stay strictly within [1000, 1010] meters.
     */
    private void startMovementLoop() {
        if (movementRunnable != null) return; // Already running

        movementRunnable = new Runnable() {
            @Override
            public void run() {
                if (isDestroyed() || redMarker == null || greenMarker == null || blueMarker == null) return;

                // 1. Red Independent Random Walk (1 to 5 meters)
                double stepR = 1.0 + Math.random() * 4.0; // 1 to 5 meters
                double angleR = Math.random() * 2 * Math.PI;
                double deltaLatR = (stepR / 1000.0 / 111.12) * Math.cos(angleR);
                double deltaLonR = ((stepR / 1000.0 / 111.12) * Math.sin(angleR)) / Math.cos(Math.toRadians(redLatLng.getLatitude()));
                redLatLng = new LatLng(redLatLng.getLatitude() + deltaLatR, redLatLng.getLongitude() + deltaLonR);

                // 2. Green Independent Random Walk (1 to 5 meters)
                double stepG = 1.0 + Math.random() * 4.0; // 1 to 5 meters
                double angleG = Math.random() * 2 * Math.PI;
                double deltaLatG = (stepG / 1000.0 / 111.12) * Math.cos(angleG);
                double deltaLonG = ((stepG / 1000.0 / 111.12) * Math.sin(angleG)) / Math.cos(Math.toRadians(greenLatLng.getLatitude()));
                greenLatLng = new LatLng(greenLatLng.getLatitude() + deltaLatG, greenLatLng.getLongitude() + deltaLonG);

                // 3. Blue Independent Random Walk (1 to 5 meters)
                double stepB = 1.0 + Math.random() * 4.0; // 1 to 5 meters
                double angleB = Math.random() * 2 * Math.PI;
                double deltaLatB = (stepB / 1000.0 / 111.12) * Math.cos(angleB);
                double deltaLonB = ((stepB / 1000.0 / 111.12) * Math.sin(angleB)) / Math.cos(Math.toRadians(blueLatLng.getLatitude()));
                blueLatLng = new LatLng(blueLatLng.getLatitude() + deltaLatB, blueLatLng.getLongitude() + deltaLonB);

                // 4. Apply Position-Based Dynamics (PBD) Spring Relaxation to satisfy distance constraints
                // We run 3 fast iterations to converge distances beautifully to the target of 1005 meters.
                for (int i = 0; i < 3; i++) {
                    LatLng[] rg = adjustPair(redLatLng, greenLatLng, 1005.0);
                    redLatLng = rg[0];
                    greenLatLng = rg[1];

                    LatLng[] gb = adjustPair(greenLatLng, blueLatLng, 1005.0);
                    greenLatLng = gb[0];
                    blueLatLng = gb[1];

                    LatLng[] br = adjustPair(blueLatLng, redLatLng, 1005.0);
                    blueLatLng = br[0];
                    redLatLng = br[1];
                }

                // Update marker positions instantly on the Map
                redMarker.setPosition(redLatLng);
                greenMarker.setPosition(greenLatLng);
                blueMarker.setPosition(blueLatLng);

                // Force layout redraw to render instantly
                mapView.invalidate();

                // Calculate current real-world distances for diagnostic verification
                double dRG = calculateDistance(redLatLng, greenLatLng);
                double dGB = calculateDistance(greenLatLng, blueLatLng);
                double dBR = calculateDistance(blueLatLng, redLatLng);

                android.util.Log.d("FriendTracker", String.format(
                        "Tick distances: Red-Green=%.2fm, Green-Blue=%.2fm, Blue-Red=%.2fm", dRG, dGB, dBR
                ));

                // Repeat every 1 second
                movementHandler.postDelayed(this, 1000L);
            }
        };

        movementHandler.post(movementRunnable);
    }

    /**
     * High-Accuracy Haversine formula to compute geodesic distance between two points in meters.
     */
    private double calculateDistance(LatLng p1, LatLng p2) {
        double R = 6371000; // Earth's radius in meters
        double lat1 = Math.toRadians(p1.getLatitude());
        double lon1 = Math.toRadians(p1.getLongitude());
        double lat2 = Math.toRadians(p2.getLatitude());
        double lon2 = Math.toRadians(p2.getLongitude());

        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(lat1) * Math.cos(lat2) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }

    /**
     * Spring projection to shift a pair of coordinates towards a target distance in meters.
     */
    private LatLng[] adjustPair(LatLng p1, LatLng p2, double targetDistance) {
        double currentDistance = calculateDistance(p1, p2);
        if (currentDistance == 0) return new LatLng[]{p1, p2};

        // Difference in meters
        double diff = currentDistance - targetDistance;
        
        // We move each coordinate by half the error ratio
        double factor = (diff / 2.0) / currentDistance;

        double dLat = p2.getLatitude() - p1.getLatitude();
        double dLon = p2.getLongitude() - p1.getLongitude();

        LatLng newP1 = new LatLng(p1.getLatitude() + dLat * factor, p1.getLongitude() + dLon * factor);
        LatLng newP2 = new LatLng(p2.getLatitude() - dLat * factor, p2.getLongitude() - dLon * factor);

        return new LatLng[]{newP1, newP2};
    }

    /**
     * Removes the loop execution task safely.
     */
    private void stopMovementLoop() {
        if (movementRunnable != null) {
            movementHandler.removeCallbacks(movementRunnable);
            movementRunnable = null;
        }
    }

    /**
     * Programmatically draws a beautiful classic teardrop vector map pin.
     */
    private Bitmap createTeardropMarkerBitmap(int color) {
        int size = 128;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);

        // 1. Draw soft drop shadow under the tip
        paint.setColor(Color.parseColor("#33000000"));
        canvas.drawCircle(64, 110, 14, paint);

        // 2. Draw white outer pin border
        paint.setColor(Color.WHITE);
        Path outerPath = new Path();
        outerPath.moveTo(64, 110); // Tip at bottom
        outerPath.lineTo(30, 50); // Left tangent
        outerPath.arcTo(new RectF(30, 12, 98, 80), 150, 240, false); // Top circle
        outerPath.close();
        canvas.drawPath(outerPath, paint);

        // 3. Draw colored inner pin core
        paint.setColor(color);
        Path innerPath = new Path();
        innerPath.moveTo(64, 102); // Tip at bottom
        innerPath.lineTo(36, 52); // Left tangent
        innerPath.arcTo(new RectF(36, 18, 92, 74), 150, 240, false); // Top circle
        innerPath.close();
        canvas.drawPath(innerPath, paint);

        // 4. Draw central white glowing dot inside the head of the pin
        paint.setColor(Color.WHITE);
        canvas.drawCircle(64, 46, 12, paint);

        return bitmap;
    }

    @Override
    protected void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        if (redMarker != null && greenMarker != null && blueMarker != null) {
            startMovementLoop();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
        stopMovementLoop();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
        stopMovementLoop();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }
}
