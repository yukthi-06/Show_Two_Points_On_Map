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

    // Persistent heading directions for each friend in radians (allows actual traveling across the map)
    private double headingRed = Math.random() * 2 * Math.PI;
    private double headingGreen = Math.random() * 2 * Math.PI;
    private double headingBlue = Math.random() * 2 * Math.PI;

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

                // Equilateral Triangle setup for 3 friends with exactly 1050 meters (1.050 km) side length:
                // This targets the exact center of the requested [1000, 1100] meters constraint range!
                double sKm = 1.050; // 1050 meters
                double deltaLat = (sKm / 111.12) * Math.cos(angle);
                double deltaLon = ((sKm / 111.12) * Math.sin(angle)) / Math.cos(Math.toRadians(baseLat));

                redLatLng = new LatLng(baseLat, baseLon);
                greenLatLng = new LatLng(baseLat + deltaLat, baseLon + deltaLon);

                // Math for Blue Point: Place it perpendicular to the Red-Green line from their midpoint
                // at a distance of s * sqrt(3) / 2 (height of equilateral triangle) = ~909.33 meters
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
     * Spawns a repeating 1-second loop where each marker moves in a persistent, correlated
     * random direction by 1 to 5 meters. Dynamic group steering keeps the friends together
     * while PBD inequality range constraints guarantee mutual distances stay strictly in [1000, 1100] meters.
     */
    private void startMovementLoop() {
        if (movementRunnable != null) return; // Already running

        movementRunnable = new Runnable() {
            @Override
            public void run() {
                if (isDestroyed() || redMarker == null || greenMarker == null || blueMarker == null) return;

                // 1. Calculate current group center point
                double centerLat = (redLatLng.getLatitude() + greenLatLng.getLatitude() + blueLatLng.getLatitude()) / 3.0;
                double centerLon = (redLatLng.getLongitude() + greenLatLng.getLongitude() + blueLatLng.getLongitude()) / 3.0;
                LatLng centerPoint = new LatLng(centerLat, centerLon);

                // 2. Red Heading Steering: slightly perturb and steer towards/away from center
                double distR = calculateDistance(redLatLng, centerPoint);
                double bearingR = calculateBearing(redLatLng, centerPoint);
                headingRed += (Math.random() - 0.5) * Math.toRadians(30); // Perturb by +/- 15 degrees
                if (distR > 620.0) {
                    headingRed = blendAngles(headingRed, bearingR, 0.15); // Steer back towards center if too far
                } else if (distR < 580.0) {
                    headingRed = blendAngles(headingRed, bearingR + Math.PI, 0.15); // Steer away if too close
                }

                // 3. Green Heading Steering: slightly perturb and steer towards/away from center
                double distG = calculateDistance(greenLatLng, centerPoint);
                double bearingG = calculateBearing(greenLatLng, centerPoint);
                headingGreen += (Math.random() - 0.5) * Math.toRadians(30); // Perturb by +/- 15 degrees
                if (distG > 620.0) {
                    headingGreen = blendAngles(headingGreen, bearingG, 0.15); // Steer back towards center
                } else if (distG < 580.0) {
                    headingGreen = blendAngles(headingGreen, bearingG + Math.PI, 0.15); // Steer away
                }

                // 4. Blue Heading Steering: slightly perturb and steer towards/away from center
                double distB = calculateDistance(blueLatLng, centerPoint);
                double bearingB = calculateBearing(blueLatLng, centerPoint);
                headingBlue += (Math.random() - 0.5) * Math.toRadians(30); // Perturb by +/- 15 degrees
                if (distB > 620.0) {
                    headingBlue = blendAngles(headingBlue, bearingB, 0.15); // Steer back towards center
                } else if (distB < 580.0) {
                    headingBlue = blendAngles(headingBlue, bearingB + Math.PI, 0.15); // Steer away
                }

                // 5. Move all three friends by a random step of 1 to 5 meters along their persistent headings
                double stepR = 1.0 + Math.random() * 4.0; // 1 to 5 meters
                double deltaLatR = (stepR / 1000.0 / 111.12) * Math.cos(headingRed);
                double deltaLonR = ((stepR / 1000.0 / 111.12) * Math.sin(headingRed)) / Math.cos(Math.toRadians(redLatLng.getLatitude()));
                redLatLng = new LatLng(redLatLng.getLatitude() + deltaLatR, redLatLng.getLongitude() + deltaLonR);

                double stepG = 1.0 + Math.random() * 4.0; // 1 to 5 meters
                double deltaLatG = (stepG / 1000.0 / 111.12) * Math.cos(headingGreen);
                double deltaLonG = ((stepG / 1000.0 / 111.12) * Math.sin(headingGreen)) / Math.cos(Math.toRadians(greenLatLng.getLatitude()));
                greenLatLng = new LatLng(greenLatLng.getLatitude() + deltaLatG, greenLatLng.getLongitude() + deltaLonG);

                double stepB = 1.0 + Math.random() * 4.0; // 1 to 5 meters
                double deltaLatB = (stepB / 1000.0 / 111.12) * Math.cos(headingBlue);
                double deltaLonB = ((stepB / 1000.0 / 111.12) * Math.sin(headingBlue)) / Math.cos(Math.toRadians(blueLatLng.getLatitude()));
                blueLatLng = new LatLng(blueLatLng.getLatitude() + deltaLatB, blueLatLng.getLongitude() + deltaLonB);

                // 6. Apply PBD Inequality Range Constraints (1000 meters to 1100 meters)
                // If distances are inside [1000m, 1100m], the solver does absolutely nothing, letting them wander freely.
                for (int i = 0; i < 3; i++) {
                    LatLng[] rg = adjustPairRange(redLatLng, greenLatLng, 1000.0, 1100.0);
                    redLatLng = rg[0];
                    greenLatLng = rg[1];

                    LatLng[] gb = adjustPairRange(greenLatLng, blueLatLng, 1000.0, 1100.0);
                    greenLatLng = gb[0];
                    blueLatLng = gb[1];

                    LatLng[] br = adjustPairRange(blueLatLng, redLatLng, 1000.0, 1100.0);
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
     * Blends two angles smoothly in the shortest angular direction, handling boundary wrapping around -PI and +PI.
     */
    private double blendAngles(double current, double target, double ratio) {
        double diff = target - current;
        while (diff < -Math.PI) diff += 2 * Math.PI;
        while (diff > Math.PI) diff -= 2 * Math.PI;
        return current + ratio * diff;
    }

    /**
     * High-Accuracy bearing calculation in radians from p1 to p2.
     */
    private double calculateBearing(LatLng p1, LatLng p2) {
        double lat1 = Math.toRadians(p1.getLatitude());
        double lon1 = Math.toRadians(p1.getLongitude());
        double lat2 = Math.toRadians(p2.getLatitude());
        double lon2 = Math.toRadians(p2.getLongitude());

        double dLon = lon2 - lon1;
        double y = Math.sin(dLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) -
                   Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);

        return Math.atan2(y, x);
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
     * PBD Inequality Range constraint relaxation. Adjusts coordinates only if they breach range boundaries.
     */
    private LatLng[] adjustPairRange(LatLng p1, LatLng p2, double minDistance, double maxDistance) {
        double currentDistance = calculateDistance(p1, p2);
        if (currentDistance == 0) return new LatLng[]{p1, p2};

        double diff = 0;
        if (currentDistance < minDistance) {
            diff = currentDistance - minDistance; // Negative diff will push them apart
        } else if (currentDistance > maxDistance) {
            diff = currentDistance - maxDistance; // Positive diff will pull them together
        } else {
            return new LatLng[]{p1, p2}; // Safe range, no action needed!
        }
        
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
