package com.example.followme;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentActivity;

import com.example.followme.databinding.ActivityTripFollowerBinding;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.RoundCap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class TripFollowerActivity extends FragmentActivity implements OnMapReadyCallback {

    private static final String TAG = "FollowMe";
    private static final String BASE_URL = "http://christopherhield-001-site4.htempurl.com/api/";
    private GoogleMap mMap;
    private static final int LOCATION_REQUEST = 111;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private Polyline tripPathPolyline;
    private final ArrayList<LatLng> pathPoints = new ArrayList<>();
    private Marker leaderMarker;
    private String tripId;
    private boolean isTripEnded = false;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
    private Date lastFetchedPointTime;


    private final Handler updateHandler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;
    private final long UPDATE_INTERVAL = 3000;

    private int tripExistsRetryCount = 0;
    private final int MAX_RETRY_COUNT = 5;

    public static int screenHeight;
    public static int screenWidth;
    private final float zoomDefault = 15.0f;
    private final OkHttpClient client = new OkHttpClient();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private double totalTripDistance = 0.0;
    private ObjectAnimator pulseAnimator;
    private String tripStartDateTime = "--";
    private long tripStartTime = 0;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private boolean isAutoCenterEnabled = true;
    private final java.text.DecimalFormat distanceFormat = new java.text.DecimalFormat("#,##0.00");

    private ActivityTripFollowerBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityTripFollowerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        setupAutoCenterToggle();

        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));


        tripId = getIntent().getStringExtra("TRIP_ID");
        if (tripId == null || tripId.isEmpty()) {
            showErrorAndFinish("Invalid Trip ID");
            return;
        }

        Log.d(TAG, "Starting to follow trip with ID: " + tripId);
        binding.ftripId.setText("Following Trip: " + tripId);

        binding.fdistance.setText("Distance: 0.00 km");
        binding.felapsedTime.setText("Elapsed: 00:00:00");

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault());
        tripStartDateTime = sdf.format(new Date());
        binding.ftripStart.setText("Trip Start: " + tripStartDateTime);

        getScreenDimensions();

        updateRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isTripEnded) {
                    fetchLatestLocation();
                    updateHandler.postDelayed(this, UPDATE_INTERVAL);
                }
            }
        };


        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        setupBroadcastIndicator();

    }

    private void setupBroadcastIndicator() {
        pulseAnimator = ObjectAnimator.ofFloat(binding.broadcastIndicator, "alpha", 1.0f, 0.25f, 1.0f);
        pulseAnimator.setDuration(750);
    }

    private void pulseBroadcastIndicator() {
        if (pulseAnimator != null && !pulseAnimator.isRunning()) {
            pulseAnimator.start();
        }
    }


    /**
     * Manipulates the map once available.
     */
    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setBuildingsEnabled(true);
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.animateCamera(CameraUpdateFactory.zoomTo(zoomDefault));
        pathPoints.clear();
        fetchEntireTripPath();

        if (checkPermission()) {
            setupLocationListener();
        }
    }

    private void fetchEntireTripPath() {
        Log.d(TAG, "Fetching entire trip path for ID: " + tripId);
        Request request = new Request.Builder()
                .url(BASE_URL + "Datapoints/GetTrip/" + tripId)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Network error fetching trip path: " + e.getMessage());
                mainHandler.post(() -> showToast("Network error: " + e.getMessage()));

                checkIfTripExists();
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "Empty response";
                Log.d(TAG, "GetTrip response code: " + response.code());

                if (response.code() == 404) {
                    Log.d(TAG, "Trip not found (404), checking if trip exists");

                    checkIfTripExists();
                    return;
                }

                if (!response.isSuccessful()) {
                    Log.e(TAG, "Server error: " + response.code());
                    mainHandler.post(() -> showToast("Server error: " + response.code()));
                    return;
                }

                Log.d(TAG, "Got trip data, length: " + responseBody.length());
                processInitialTripData(responseBody);
            }
        });
    }

    private void checkIfTripExists() {
        Log.d(TAG, "Checking if trip exists: " + tripId + " (Attempt " + (tripExistsRetryCount + 1) + ")");

        Request request = new Request.Builder()
                .url(BASE_URL + "Datapoints/TripExists/" + tripId)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Network error checking trip: " + e.getMessage());
                retryTripExistsCheck();
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseData = response.body() != null ? response.body().string().trim() : "";
                Log.d(TAG, "TripExists response: " + responseData + " (code: " + response.code() + ")");

                if (!response.isSuccessful()) {
                    Log.e(TAG, "Server error: " + response.code());
                    mainHandler.post(() -> showToast("Server error: " + response.code()));
                    retryTripExistsCheck();
                    return;
                }

                if (responseData.equalsIgnoreCase("true")) {
                    Log.d(TAG, "Trip exists, starting updates");
                    tripExistsRetryCount = 0;
                    mainHandler.post(() -> startLocationUpdates());
                    checkTripEnd();
                } else if (tripExistsRetryCount < MAX_RETRY_COUNT) {
                    retryTripExistsCheck();
                } else {
                    Log.e(TAG, "Trip ID does not exist after " + MAX_RETRY_COUNT + " attempts");
                    mainHandler.post(() -> showErrorAndFinish("Trip ID does not exist"));
                }
            }
        });
    }

    private void checkTripEnd() {
        Request request = new Request.Builder()
                .url(BASE_URL + "Datapoints/GetTrip/" + tripId)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Failed to check for end coordinates: " + e.getMessage());
                tripExistsRetryCount = 0;
                mainHandler.post(() -> startLocationUpdates());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "Error checking end coordinates: " + response.code());
                    tripExistsRetryCount = 0;
                    mainHandler.post(() -> startLocationUpdates());
                    return;
                }

                String responseData = response.body() != null ? response.body().string() : "";

                try {
                    JSONArray points = new JSONArray(responseData);
                    if (points.length() > 0) {
                        // Check the most recent point
                        JSONObject lastPoint = points.getJSONObject(points.length() - 1);
                        if (lastPoint.getDouble("latitude") == 0.0 &&
                                lastPoint.getDouble("longitude") == 0.0) {

                            Log.d(TAG, "Trip has end coordinates, showing dialog");
                            mainHandler.post(() -> showTripEndedByUserDialog());
                            return;
                        }
                    }

                    // If we get here, no end coordinates were found
                    tripExistsRetryCount = 0;
                    mainHandler.post(() -> startLocationUpdates());

                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing trip data: " + e.getMessage());
                    tripExistsRetryCount = 0;
                    mainHandler.post(() -> startLocationUpdates());
                }
            }
        });
    }

    private void retryTripExistsCheck() {
        tripExistsRetryCount++;
        if (tripExistsRetryCount <= MAX_RETRY_COUNT) {
            Log.d(TAG, "Retrying trip exists check in 3 seconds... (Attempt " + tripExistsRetryCount + ")");
            mainHandler.postDelayed(this::checkIfTripExists, 3000);
        } else {
            Log.e(TAG, "Max retry attempts reached");
            mainHandler.post(() -> showErrorAndFinish("Unable to connect to the trip after multiple attempts"));
        }
    }

    private void processInitialTripData(String jsonData) {
        try {
            JSONArray pointsArray = new JSONArray(jsonData);
            Log.d(TAG, "Processing initial trip data with " + pointsArray.length() + " points");


            if (pointsArray.length() == 0) {
                mainHandler.post(() -> {
                    showToast("No trip data available yet");
                    startLocationUpdates();
                });
                return;
            }

            List<LatLng> points = new ArrayList<>();
            JSONObject lastPoint = null;
            boolean foundEndCoordinates = false;

            // First pass: check if trip has ended
            for (int i = 0; i < pointsArray.length(); i++) {
                JSONObject pointObject = pointsArray.getJSONObject(i);
                double lat = pointObject.getDouble("latitude");
                double lng = pointObject.getDouble("longitude");

                if (lat == 0.0 && lng == 0.0) {
                    Log.d(TAG, "Found end coordinates while processing initial data");
                    foundEndCoordinates = true;
                    // Don't break, we'll still process the remaining valid points
                }
            }

            // Second pass: collect valid points
            for (int i = 0; i < pointsArray.length(); i++) {
                JSONObject pointObject = pointsArray.getJSONObject(i);
                double lat = pointObject.getDouble("latitude");
                double lng = pointObject.getDouble("longitude");

                // Skip invalid coordinates
                if (lat == 0.0 && lng == 0.0) {
                    continue;
                }

                // Skip obviously invalid coordinates (close to 0,0 or null island)
                if (Math.abs(lat) < 0.1 && Math.abs(lng) < 0.1) {
                    continue;
                }

                // Skip coordinates outside Earth's bounds
                if (Math.abs(lat) > 90.0 || Math.abs(lng) > 180.0) {
                    continue;
                }

                LatLng point = new LatLng(lat, lng);

                // Check for duplicates
                boolean isDuplicate = false;
                for (LatLng existingPoint : points) {
                    if (isPointNearby(existingPoint, point, 0.00001)) {
                        isDuplicate = true;
                        break;
                    }
                }

                if (!isDuplicate) {
                    points.add(point);
                    lastPoint = pointObject;
                }
            }

            if (lastPoint != null) {
                try {
                    String dateTimeStr = lastPoint.getString("datetime");
                    Log.d(TAG, "Last point datetime: " + dateTimeStr);

                    try {
                        lastFetchedPointTime = dateFormat.parse(dateTimeStr);
                    } catch (ParseException e) {
                        try {
                            SimpleDateFormat altFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
                            altFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                            lastFetchedPointTime = altFormat.parse(dateTimeStr);
                        } catch (ParseException e2) {
                            Log.e(TAG, "Error parsing datetime with alternate format: " + e2.getMessage());
                        }
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing datetime: " + e.getMessage());
                }
            }

            final List<LatLng> finalPoints = points;
            final JSONObject finalLastPoint = lastPoint;
            final boolean finalFoundEndCoordinates = foundEndCoordinates;

            if (!points.isEmpty() && pointsArray.length() > 0) {
                try {
                    JSONObject firstPoint = pointsArray.getJSONObject(0);
                    if (firstPoint != null) {
                        String dateTimeStr = firstPoint.getString("datetime");
                        if (dateTimeStr != null && !dateTimeStr.isEmpty()) {
                            Date startDate = null;

                            try {
                                // Parse using the format that matches the API response: "2024-12-15T20:05:23"
                                startDate = dateFormat.parse(dateTimeStr);
                                Log.d(TAG, "Successfully parsed datetime: " + dateTimeStr);
                            } catch (ParseException e) {
                                Log.e(TAG, "Failed to parse datetime: " + dateTimeStr + " Error: " + e.getMessage());
                            }

                            if (startDate != null) {
                                // Convert UTC time to local device time
                                long utcTime = startDate.getTime();
                                tripStartTime = utcTime;

                                Log.d(TAG, "Trip start datetime parsed: " + startDate);
                                Log.d(TAG, "Trip start time set to: " + tripStartTime);

                                // Format for display using local time zone
                                SimpleDateFormat displayFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault());
                                tripStartDateTime = displayFormat.format(new Date(tripStartTime));

                                // Calculate total distance along the path
                                totalTripDistance = 0.0;
                                for (int i = 1; i < points.size(); i++) {
                                    totalTripDistance += calculateDistance(points.get(i-1), points.get(i));
                                }
                            } else {
                                Log.e(TAG, "Could not parse datetime string: " + dateTimeStr);
                            }
                        }
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Error extracting trip start info: " + e.getMessage());
                }
            }

            mainHandler.post(() -> {
                // Clear existing points
                pathPoints.clear();

                // Skip processing if we have no valid points
                if (finalPoints.isEmpty()) {
                    showToast("No valid trip points found");
                    startLocationUpdates();
                    return;
                }
                binding.ftripStart.setText("Trip Start: " + tripStartDateTime);
                binding.fdistance.setText("Distance: " + distanceFormat.format(totalTripDistance/1000.0) + " km");
                startTripTimer();

                // Add all valid points to the path
                pathPoints.addAll(finalPoints);

                // Draw the path if we have at least 2 points
                if (pathPoints.size() >= 2) {
                    drawTripPath();
                }

                // If trip has ended, show the dialog
                if (finalFoundEndCoordinates) {
                    showTripEndedByUserDialog();
                }

                // If we have a valid last point, use it
                if (finalLastPoint != null) {
                    try {
                        double lat = finalLastPoint.getDouble("latitude");
                        double lng = finalLastPoint.getDouble("longitude");

                        if (lat != 0.0 && lng != 0.0) {
                            LatLng lastLatLng = new LatLng(lat, lng);
                            Log.d(TAG, "Last position: " + lat + ", " + lng);

                            // Place leader marker at the last known position
                            placeLeaderMarker(lastLatLng, 0);

                            // Animate camera to the last known position
                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(lastLatLng, zoomDefault));
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error processing last point: " + e.getMessage());
                    }
                }

                // Start location updates
                startLocationUpdates();
            });

        } catch (JSONException e) {
            Log.e(TAG, "Error parsing JSON: " + e.getMessage());
            mainHandler.post(() -> showToast("Error processing trip data"));
        }
    }

    private void startLocationUpdates() {
        Log.d(TAG, "Starting location updates");
        stopLocationUpdates();
        updateHandler.post(updateRunnable);
    }

    private void stopLocationUpdates() {
        Log.d(TAG, "Stopping location updates");
        updateHandler.removeCallbacks(updateRunnable);
    }

    private void fetchLatestLocation() {
        Log.d(TAG, "Fetching latest location for trip: " + tripId);

        Request request = new Request.Builder()
                .url(BASE_URL + "Datapoints/GetTrip/" + tripId)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Failed to fetch latest location: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.code() == 404) {
                    Log.d(TAG, "Trip not found (404), checking if ended");
                    checkIfTripEnded();
                    return;
                }

                if (!response.isSuccessful()) {
                    Log.e(TAG, "Error fetching trip data: " + response.code());
                    return;
                }

                String responseData = response.body() != null ? response.body().string() : "";

                // Check for end coordinates immediately
                try {
                    JSONArray pointsArray = new JSONArray(responseData);
                    if (pointsArray.length() > 0) {
                        JSONObject lastPoint = pointsArray.getJSONObject(pointsArray.length() - 1);
                        if (lastPoint.getDouble("latitude") == 0.0 &&
                                lastPoint.getDouble("longitude") == 0.0) {
                            Log.d(TAG, "End coordinates detected in fetchLatestLocation");
                            mainHandler.post(() -> showTripEndedByUserDialog());
                            return;
                        }
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Error checking for end coordinates: " + e.getMessage());
                }

                Log.d(TAG, "Fetched trip data response length: " + responseData.length());
                processUpdatedTripData(responseData);
            }
        });
    }

    private void processUpdatedTripData(String jsonData) {
        try {
            JSONArray pointsArray = new JSONArray(jsonData);
            Log.d(TAG, "Received " + pointsArray.length() + " points in the trip data");
            Log.d(TAG, "Full response data: " + jsonData);

            for (int i = 0; i < pointsArray.length() && i < 5; i++) {
                JSONObject point = pointsArray.getJSONObject(i);
                Log.d(TAG, "Point " + i + ": lat=" + point.getDouble("latitude") +
                        ", lng=" + point.getDouble("longitude") +
                        ", time=" + point.getString("datetime"));

                if (point.getDouble("latitude") == 0.0 && point.getDouble("longitude") == 0.0) {
                    Log.d(TAG, "Detected end trip coordinates (0.0, 0.0)");
                    mainHandler.post(() -> showTripEndedByUserDialog());
                    return;
                }
            }


            if (pointsArray.length() == 0) {
                Log.d(TAG, "No points in updated trip data");
                return;
            }


            if (pathPoints.isEmpty()) {
                Log.d(TAG, "Path was empty, processing all points");
                processInitialTripData(jsonData);
                return;
            }

            JSONObject latestPoint = pointsArray.getJSONObject(pointsArray.length() - 1);
            double lat = latestPoint.getDouble("latitude");
            double lng = latestPoint.getDouble("longitude");
            String dateTimeStr = latestPoint.getString("datetime");
            Log.d(TAG, "Latest point: " + lat + ", " + lng + " at " + dateTimeStr);

            Date pointTime;
            try {

                pointTime = dateFormat.parse(dateTimeStr);
            } catch (ParseException e) {
                try {

                    SimpleDateFormat altFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
                    altFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                    pointTime = altFormat.parse(dateTimeStr);
                } catch (ParseException e2) {
                    Log.e(TAG, "Error parsing point time with alternate format: " + e2.getMessage());
                    e2.printStackTrace();
                    return;
                }
            }


            if (lastFetchedPointTime == null || pointTime.after(lastFetchedPointTime)) {
                Log.d(TAG, "New point is newer than last fetched point");


                lastFetchedPointTime = pointTime;
                final LatLng newPoint = new LatLng(lat, lng);

                mainHandler.post(() -> {

                    boolean pointExists = false;
                    for (LatLng point : pathPoints) {

                        if (isPointNearby(point, newPoint, 0.00001)) {
                            pointExists = true;
                            break;
                        }
                    }

                    if (!pointExists) {
                        Log.d(TAG, "Adding new point to path");
                        pulseBroadcastIndicator();
                        if (pathPoints.size() > 0) {
                            LatLng prevPoint = pathPoints.get(pathPoints.size() - 1);
                            double segmentDistance = calculateDistance(prevPoint, newPoint);
                            totalTripDistance += segmentDistance;

                            binding.fdistance.setText("Distance: " + distanceFormat.format(totalTripDistance / 1000.0) + " km");
                        }
                        pathPoints.add(newPoint);
                        drawTripPath();

                        float bearing = 0;
                        if (pathPoints.size() > 1) {

                            LatLng prevPoint = pathPoints.get(pathPoints.size() - 2);
                            bearing = calculateBearing(prevPoint, newPoint);
                            Log.d(TAG, "Calculated bearing: " + bearing);
                        }

                        placeLeaderMarker(newPoint, bearing);

                        if (isAutoCenterEnabled) {
                            mMap.animateCamera(CameraUpdateFactory.newLatLng(newPoint));
                        }
                    } else {
                        Log.d(TAG, "Point already exists in path, skipping");
                    }
                });
            } else {
                Log.d(TAG, "Point is not newer than our last point, skipping");
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing updated trip data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showTripEndedByUserDialog() {
        if (!isTripEnded) {
            Log.d(TAG, "Trip has been ended by the leader");
            isTripEnded = true;
            stopLocationUpdates();

            new AlertDialog.Builder(this)
                    .setTitle("Trip Ended")
                    .setMessage("This trip has been ended by the leader.")
                    .setPositiveButton("OK", (dialog, which) -> {

                    })
                    .setCancelable(false)
                    .show();
        }
    }

    private boolean isPointNearby(LatLng point1, LatLng point2, double threshold) {
        return Math.abs(point1.latitude - point2.latitude) < threshold &&
                Math.abs(point1.longitude - point2.longitude) < threshold;
    }

    private void checkIfTripEnded() {
        if (!isTripEnded) {
            Log.d(TAG, "Trip has ended");
            isTripEnded = true;
            mainHandler.post(() -> {
                stopLocationUpdates();
                showAlertDialog("Trip Ended",
                        "The trip you are following has ended. The map will show the final path.");
            });
        }
    }

    private void drawTripPath() {
        if (tripPathPolyline != null) {
            tripPathPolyline.remove();
        }

        if (pathPoints.size() > 1) {
            PolylineOptions polylineOptions = new PolylineOptions();
            polylineOptions.addAll(pathPoints);
            tripPathPolyline = mMap.addPolyline(polylineOptions);
            tripPathPolyline.setEndCap(new RoundCap());
            tripPathPolyline.setWidth(12);
            tripPathPolyline.setColor(Color.BLUE);
            Log.d(TAG, "Drew trip path with " + pathPoints.size() + " points");
        }
    }

    private void placeLeaderMarker(LatLng position, float bearing) {

        if (leaderMarker != null) {
            leaderMarker.remove();
        }

        float r = getIconRadius();
        if (r > 0) {
            Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.car);
            Bitmap resized = Bitmap.createScaledBitmap(icon, (int) r, (int) r, false);
            BitmapDescriptor iconBitmap = BitmapDescriptorFactory.fromBitmap(resized);

            MarkerOptions options = new MarkerOptions()
                    .position(position)
                    .icon(iconBitmap)
                    .rotation(bearing);

            leaderMarker = mMap.addMarker(options);
            Log.d(TAG, "Placed leader marker at " + position + " with bearing " + bearing);
            if (isAutoCenterEnabled) {
                mMap.animateCamera(CameraUpdateFactory.newLatLng(position));
            }
        }
    }

    private float calculateBearing(LatLng start, LatLng end) {
        double startLat = Math.toRadians(start.latitude);
        double startLng = Math.toRadians(start.longitude);
        double endLat = Math.toRadians(end.latitude);
        double endLng = Math.toRadians(end.longitude);

        double dLng = endLng - startLng;

        double y = Math.sin(dLng) * Math.cos(endLat);
        double x = Math.cos(startLat) * Math.sin(endLat) -
                Math.sin(startLat) * Math.cos(endLat) * Math.cos(dLng);

        double bearing = Math.toDegrees(Math.atan2(y, x));
        return (float) ((bearing + 360) % 360);
    }

    private void getScreenDimensions() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        screenHeight = displayMetrics.heightPixels;
        screenWidth = displayMetrics.widthPixels;
    }

    private boolean checkPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION
                    }, LOCATION_REQUEST);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_REQUEST) {
            for (int i = 0; i < permissions.length; i++) {
                if (permissions[i].equals(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        setupLocationListener();
                    } else {
                        showToast("Location Permission not Granted");
                    }
                }
            }
        }
    }

    private void setupLocationListener() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationListener = new MyLocListener1(this);

        if (checkPermission() && locationManager != null) {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 1000, 15, locationListener);
            Log.d(TAG, "Location listener set up");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "Activity paused");

        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
        }

        stopLocationUpdates();
        if (timerHandler != null && timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "Activity resumed");

        if (checkPermission() && locationManager != null && locationListener != null) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 10, locationListener);
        }

        if (!isTripEnded && tripId != null) {
            startLocationUpdates();
        }

        if (tripStartTime > 0) {
            startTripTimer();
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Activity destroyed");
        stopLocationUpdates();
        if (timerHandler != null && timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
    }

    private float getIconRadius() {
        float z = mMap.getCameraPosition().zoom;
        return 15f * z - 130f;
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Toast: " + message);
    }

    private void showErrorAndFinish(String message) {
        Log.e(TAG, "Error: " + message);
        new AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage(message)
                .setPositiveButton("OK", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    private void showAlertDialog(String title, String message) {
        Log.d(TAG, "Alert: " + title + " - " + message);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private void startTripTimer() {
        // Clear any existing callbacks first
        if (timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }

        // Only start if we have a valid start time
        if (tripStartTime > 0) {
            Log.d(TAG, "Starting trip timer with start time: " + tripStartTime);
            Log.d(TAG, "Current time: " + System.currentTimeMillis());

            timerRunnable = new Runnable() {
                @Override
                public void run() {
                    if (tripStartTime > 0) {
                        // Get current time
                        long currentTime = System.currentTimeMillis();
                        // Calculate elapsed time
                        long elapsedMillis = currentTime - tripStartTime;

                        // Ensure we don't show negative time
                        if (elapsedMillis < 0) {
                            Log.w(TAG, "Negative elapsed time detected: " + elapsedMillis);
                            elapsedMillis = 0;
                        }

                        int hours = (int) (elapsedMillis / 3600000);
                        int minutes = (int) ((elapsedMillis % 3600000) / 60000);
                        int seconds = (int) ((elapsedMillis % 60000) / 1000);

                        String timeElapsed = String.format(Locale.getDefault(),
                                "%02d:%02d:%02d", hours, minutes, seconds);
                        binding.felapsedTime.setText("Elapsed: " + timeElapsed);

                        // Log every 10 seconds to reduce log spam
                        if (seconds % 10 == 0) {
                            Log.d(TAG, "Timer update - current: " + currentTime +
                                    ", start: " + tripStartTime +
                                    ", elapsed: " + elapsedMillis +
                                    ", formatted: " + timeElapsed);
                        }

                        timerHandler.postDelayed(this, 1000);
                    }
                }
            };
            timerHandler.post(timerRunnable);
        } else {
            Log.e(TAG, "Cannot start timer - invalid trip start time: " + tripStartTime);
        }
    }

    // Add this method to calculate distance between points
    private double calculateDistance(LatLng point1, LatLng point2) {
        float[] results = new float[1];
        android.location.Location.distanceBetween(
                point1.latitude, point1.longitude,
                point2.latitude, point2.longitude,
                results);
        return results[0]; // Distance in meters
    }

    private void setupAutoCenterToggle() {

        ImageView startStopButton = binding.startStop;

        // Set up the click listener
        startStopButton.setOnClickListener(v -> {
            // Toggle the auto-center state
            isAutoCenterEnabled = !isAutoCenterEnabled;

            if (isAutoCenterEnabled) {
                // Auto-center is enabled - use normal image (full opacity)
                startStopButton.setAlpha(1.0f);

                // If we have a leader position, center the map on it
                if (leaderMarker != null) {
                    mMap.animateCamera(CameraUpdateFactory.newLatLng(leaderMarker.getPosition()));
                }

                showToast("Auto-center enabled");
            } else {
                // Auto-center is disabled - gray it out (reduced opacity)
                startStopButton.setAlpha(0.5f);

                showToast("Auto-center disabled");
            }
        });
    }
}