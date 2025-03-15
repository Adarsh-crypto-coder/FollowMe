package com.example.followme;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentActivity;

import com.example.followme.databinding.ActivityTripLeadBinding;
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
import android.content.ComponentName;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class TripLeadActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private static final int LOCATION_REQUEST = 111;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private Polyline llHistoryPolyline;
    private final ArrayList<LatLng> latLonHistory = new ArrayList<>();
    private Marker carMarker;
    public static int screenHeight;
    public static int screenWidth;
    private final float zoomDefault = 15.0f;
    private ObjectAnimator objectAnimator1;
    private ObjectAnimator pulseAnimator;
    private AnimatorSet animatorSet;

    private double totalDistance = 0.0;
    private long tripStartTime = 0;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private String tripStartDateTime;
    private final DecimalFormat distanceFormat = new DecimalFormat("#,##0.00");

    private ActivityTripLeadBinding binding;
    private static final int NOTIFICATION_PERMISSION_CODE = 112;
    private boolean isTrackingPaused = false;

    private String tripId;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());
    private PointReciever locationReceiver;
    private LocationService locationService;
    private boolean serviceBound = false;
    private Intent locationServiceIntent;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            LocationService.LocationBinder binder = (LocationService.LocationBinder) service;
            locationService = binder.getService();
            serviceBound = true;
            Log.d("TripLeadActivity", "Trip location service connected");

            Toast.makeText(TripLeadActivity.this,
                    "Location service connected", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            Log.d("TripLeadActivity", "Trip location service disconnected");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityTripLeadBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        getScreenDimensions();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);

        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_CODE);
            }
        }

        startBackgroundLocationService();

        objectAnimator1 =
                ObjectAnimator.ofFloat(binding.imageView, "alpha", 1.0f, 0.25f);
        binding.imageView.setVisibility(View.VISIBLE);
        binding.awaitingGpsText.setVisibility(View.VISIBLE);
        Intent intent = getIntent();

        tripId = intent.getStringExtra("TRIP_ID");
        binding.tripId.setText("Trip ID: " + tripId);
        sendInitialTripPoint();

        tripStartTime = SystemClock.elapsedRealtime();

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault());
        tripStartDateTime = sdf.format(new Date());
        binding.tripStart.setText("Trip Start: " + tripStartDateTime);

        binding.distance.setText("Distance: 0.00 km");

        startTripTimer();

        startObjectAnimators();
        binding.startStop.setOnClickListener(view -> {
            isTrackingPaused = !isTrackingPaused;

            if (isTrackingPaused) {
                binding.startStop.setImageResource(R.drawable.play);
                Toast.makeText(this, "Location tracking paused", Toast.LENGTH_SHORT).show();
            } else {
                binding.startStop.setImageResource(R.drawable.pause);
                Toast.makeText(this, "Location tracking resumed", Toast.LENGTH_SHORT).show();
            }
        });

        binding.share.setOnClickListener(view -> shareData());
        binding.end.setOnClickListener(view -> {
            endTrip();
        });
        setupBroadcastIndicator();



    }

    private void shareData() {
        try{
            String currentTrip = tripId;
            String userName1 = getUsername();

            String subject = String.format("Follow Me Trip Id: " + currentTrip);
            String shareText = String.format(userName1 + "\" has shared a \\\"Follow Me\\\" Trip ID with you. Use Follow Me Trip ID: \"" + currentTrip);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");

            shareIntent.putExtra(Intent.EXTRA_SUBJECT,subject);
            shareIntent.putExtra(Intent.EXTRA_TEXT,shareText);
            startActivity(Intent.createChooser(shareIntent,"Share Trip Details"));

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void startBackgroundLocationService() {
        Log.d("TripLeadActivity", "Attempting to start location service and register receiver");

        locationReceiver = new PointReciever(this);

        IntentFilter filter = new IntentFilter(LocationService.BROADCAST_ACTION);

        Log.d("TripLeadActivity", "Registering receiver with filter for action: " + LocationService.BROADCAST_ACTION);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(locationReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            Log.d("TripLeadActivity", "Registered with RECEIVER_NOT_EXPORTED flag");
        } else {
            ContextCompat.registerReceiver(
                    this,
                    locationReceiver,
                    new IntentFilter(LocationService.BROADCAST_ACTION),
                    ContextCompat.RECEIVER_NOT_EXPORTED
            );
            Log.d("TripLeadActivity", "Registered without flags (pre-Android 13)");
        }

        Log.d("TripLeadActivity", "Receiver registered");

        locationServiceIntent = new Intent(this, LocationService.class);
        ContextCompat.startForegroundService(this, locationServiceIntent);

        // Bind to the service
        bindService(locationServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        new Handler().postDelayed(this::testBroadcastReception, 3000);

        Log.d("TripLeadActivity", "Background location service started and bound");
    }
    private void stopBackgroundLocationService() {

        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }

        if (locationReceiver != null) {
            try {
                unregisterReceiver(locationReceiver);
                locationReceiver = null;
            } catch (Exception e) {
                Log.e("TripLeadActivity", "Error unregistering trip location receiver: " + e.getMessage());
            }
        }

        if (locationServiceIntent != null) {
            stopService(locationServiceIntent);
            locationServiceIntent = null;
        }

        Log.d("TripLeadActivity", "Background trip location service stopped");
    }


    private void endTrip() {
        stopBackgroundLocationService();

        timerHandler.removeCallbacks(timerRunnable);

        sendFinalTripPoint();

        saveTripSummary();

        Toast.makeText(this, "Trip ended successfully", Toast.LENGTH_SHORT).show();

        finish();
    }

    private void sendFinalTripPoint() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String currentDateTime = sdf.format(new Date());

        executor.execute(() -> {
            try {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("tripId", tripId);
                jsonObject.put("latitude", 0.0);
                jsonObject.put("longitude", 0.0);
                jsonObject.put("datetime", currentDateTime);
                jsonObject.put("userName", getUsername());

                RequestBody requestBody = RequestBody.create(
                        MediaType.parse("application/json"),
                        jsonObject.toString()
                );

                Request request = new Request.Builder()
                        .url("http://christopherhield-001-site4.htempurl.com/api/Datapoints/AddTripPoint")
                        .post(requestBody)
                        .build();

                OkHttpClient client = new OkHttpClient();
                try (Response response = client.newCall(request).execute()) {
                    String responseBody = response.body().string();
                    Log.d("TripLeadActivity", "Final trip point API response: " + response.code() + " - " + responseBody);

                    if (!response.isSuccessful()) {
                        Log.e("TripLeadActivity", "Error sending final location: " + response.code());
                    }
                } catch (IOException e) {
                    Log.e("TripLeadActivity", "Failed to send final location: " + e.getMessage());
                }
            } catch (JSONException e) {
                Log.e("TripLeadActivity", "Error creating JSON for final point: " + e.getMessage());
            }
        });
    }

    private void saveTripSummary() {

        long currentTime = SystemClock.elapsedRealtime();
        long elapsedMillis = currentTime - tripStartTime;
        double distanceKm = totalDistance / 1000.0;

        try {
            JSONObject summary = new JSONObject();
            summary.put("tripId", tripId);
            summary.put("totalDistance", distanceFormat.format(distanceKm));
            summary.put("totalTime", formatTime(elapsedMillis));
            summary.put("startTime", tripStartDateTime);
            summary.put("endTime", new SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault()).format(new Date()));

            SharedPreferences prefs = getSharedPreferences("TripHistory", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("trip_" + tripId, summary.toString());
            editor.apply();

            Log.d("TripLeadActivity", "Trip summary saved: " + summary.toString());
        } catch (JSONException e) {
            Log.e("TripLeadActivity", "Error creating trip summary: " + e.getMessage());
        }
    }

    private String formatTime(long millis) {
        int hours = (int) (millis / 3600000);
        int minutes = (int) (millis - hours * 3600000) / 60000;
        int seconds = (int) (millis - hours * 3600000 - minutes * 60000) / 1000;

        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
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


    private void startTripTimer() {
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                long currentTime = SystemClock.elapsedRealtime();
                long elapsedMillis = currentTime - tripStartTime;

                int hours = (int) (elapsedMillis / 3600000);
                int minutes = (int) (elapsedMillis - hours * 3600000) / 60000;
                int seconds = (int) (elapsedMillis - hours * 3600000 - minutes * 60000) / 1000;

                String timeElapsed = String.format(Locale.getDefault(),
                        "%02d:%02d:%02d", hours, minutes, seconds);
                binding.elapsedTime.setText("Time: " + timeElapsed);
                timerHandler.postDelayed(this, 1000);
            }
        };
        timerHandler.postDelayed(timerRunnable, 0);
    }

    private void startObjectAnimators() {

        objectAnimator1.setDuration(750);
        objectAnimator1.setRepeatCount(ObjectAnimator.INFINITE);
        objectAnimator1.setRepeatMode(ObjectAnimator.REVERSE);

        animatorSet = new AnimatorSet();
        animatorSet.playTogether(objectAnimator1);
        animatorSet.start();
    }

    /**
     * This callback is triggered when the map is ready to be used.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mMap.animateCamera(CameraUpdateFactory.zoomTo(zoomDefault));
        mMap.setBuildingsEnabled(true);
        mMap.getUiSettings().setZoomControlsEnabled(true);

        if (checkPermission()) {
            Location lastKnownLocation = getLastKnownLocation();
            if (lastKnownLocation != null) {
                LatLng lastLatLng = new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(lastLatLng, zoomDefault));
            }
        }
    }

    private Location getLastKnownLocation() {
        if (locationManager == null) {
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        }
        if (checkPermission()) {
            return locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        }
        return null;
    }


    private void getScreenDimensions() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        screenHeight = displayMetrics.heightPixels;
        screenWidth = displayMetrics.widthPixels;
    }

    private boolean checkPermission() {
        if (ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{
                            android.Manifest.permission.ACCESS_FINE_LOCATION
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
                if (permissions[i].equals(android.Manifest.permission.ACCESS_FINE_LOCATION)) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        setupLocationListener();
                    } else {
                        Toast.makeText(this, "Location Permission not Granted", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    }

    private void setupLocationListener() {

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        locationListener = new MyLocListener(this);

        if (checkPermission() && locationManager != null)
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 1000, 15, locationListener);
    }

    @Override
    protected void onPause() {
        super.onPause();

        timerHandler.removeCallbacks(timerRunnable);
    }

    @Override
    protected void onResume() {
        super.onResume();
        timerHandler.postDelayed(timerRunnable, 0);
    }

    @Override
    protected void onDestroy() {
        stopBackgroundLocationService();
        super.onDestroy();
        if (!executor.isShutdown()) {
            executor.shutdown();
        }
    }

    public void updateLocation(Location location) {

        Log.d("TripLeadActivity", "updateLocation called with: " + location.getLatitude() + ", " + location.getLongitude());
        pulseBroadcastIndicator();
        binding.imageView.setVisibility(View.GONE);
        binding.awaitingGpsText.setVisibility(View.GONE);

        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

        if (!latLonHistory.isEmpty()) {
            LatLng prevLatLng = latLonHistory.get(latLonHistory.size() - 1);
            float[] results = new float[1];
            Location.distanceBetween(
                    prevLatLng.latitude, prevLatLng.longitude,
                    latLng.latitude, latLng.longitude,
                    results);

            totalDistance += results[0];
            double distanceKm = totalDistance / 1000.0;
            binding.distance.setText("Distance: " + distanceFormat.format(distanceKm) + " km");
        }

        latLonHistory.add(latLng);
        Log.d("TripLeadActivity", "Sending point to API: " + latLng.latitude + ", " + latLng.longitude);

        if (!isTrackingPaused) {
            Log.d("TripLeadActivity", "Sending point to API: " + latLng.latitude + ", " + latLng.longitude);
            sendLocationToApi(latLng.latitude, latLng.longitude);
        } else {
            Log.d("TripLeadActivity", "Location tracking paused, not sending to API: " + latLng.latitude + ", " + latLng.longitude);
        }


        if (llHistoryPolyline != null) {
            llHistoryPolyline.remove();
        }

        if (latLonHistory.size() == 1) {
            mMap.addMarker(new MarkerOptions().alpha(0.5f).position(latLng).title("My Origin"));
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoomDefault));
            return;
        }

        if (latLonHistory.size() > 1) {
            PolylineOptions polylineOptions = new PolylineOptions();

            for (LatLng ll : latLonHistory) {
                polylineOptions.add(ll);
            }
            llHistoryPolyline = mMap.addPolyline(polylineOptions);
            llHistoryPolyline.setEndCap(new RoundCap());
            llHistoryPolyline.setWidth(12);
            llHistoryPolyline.setColor(Color.BLUE);

            float r = getRadius();
            if (r > 0) {
                Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.car);
                Bitmap resized = Bitmap.createScaledBitmap(icon, (int) r, (int) r, false);
                BitmapDescriptor iconBitmap = BitmapDescriptorFactory.fromBitmap(resized);

                MarkerOptions options = new MarkerOptions();
                options.position(latLng);
                options.icon(iconBitmap);
                options.rotation(location.getBearing());

                if (carMarker != null) {
                    carMarker.remove();
                }

                carMarker = mMap.addMarker(options);
            }
        }
        mMap.animateCamera(CameraUpdateFactory.newLatLng(latLng));
    }

    private void sendLocationToApi(double latitude, double longitude) {

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String currentDateTime = sdf.format(new Date());

        executor.execute(() -> {
            try {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("tripId", tripId);
                jsonObject.put("latitude", latitude);
                jsonObject.put("longitude", longitude);
                jsonObject.put("datetime", currentDateTime);
                jsonObject.put("userName", getUsername());

                RequestBody requestBody = RequestBody.create(
                        MediaType.parse("application/json"),
                        jsonObject.toString()
                );

                Request request = new Request.Builder()
                        .url("http://christopherhield-001-site4.htempurl.com/api/Datapoints/AddTripPoint")
                        .post(requestBody)
                        .build();

                OkHttpClient client = new OkHttpClient();
                try (Response response = client.newCall(request).execute()) {
                    String responseBody = response.body().string();
                    Log.d("TripLeadActivity", "API response: " + response.code() + " - " + responseBody);

                    if (!response.isSuccessful()) {
                        Log.e("TripLeadActivity", "Error sending location: " + response.code());
                    }
                } catch (IOException e) {
                    Log.e("TripLeadActivity", "Failed to send location: " + e.getMessage());
                }
            } catch (JSONException e) {
                Log.e("TripLeadActivity", "Error creating JSON: " + e.getMessage());
            }
        });
    }

    private String getUsername() {
        SharedPreferences sharedPreferences = getSharedPreferences("UserCredentials", Context.MODE_PRIVATE);
        return sharedPreferences.getString("username", "unknown_user");
    }

    private float getRadius() {
        float z = mMap.getCameraPosition().zoom;
        return 15f * z - 130f;
    }

    private void sendInitialTripPoint() {
        Location lastLocation = null;
        if (checkPermission() && locationManager != null) {
            lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        }
        if (lastLocation != null) {
            double latitude = lastLocation.getLatitude();
            double longitude = lastLocation.getLongitude();

            Log.d("TripLeadActivity", "Sending initial trip point with actual location: " +
                    latitude + ", " + longitude);

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            String currentDateTime = sdf.format(new Date());

            SharedPreferences sharedPreferences = getSharedPreferences("UserCredentials", Context.MODE_PRIVATE);
            String username = sharedPreferences.getString("username", "unknown_user");

            executor.execute(() -> {
                try {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("tripId", tripId);
                    jsonObject.put("latitude", latitude);
                    jsonObject.put("longitude", longitude);
                    jsonObject.put("datetime", currentDateTime);
                    jsonObject.put("userName", username);

                    RequestBody requestBody = RequestBody.create(
                            MediaType.parse("application/json"),
                            jsonObject.toString()
                    );

                    Request request = new Request.Builder()
                            .url("http://christopherhield-001-site4.htempurl.com/api/Datapoints/AddTripPoint")
                            .post(requestBody)
                            .build();

                    OkHttpClient client = new OkHttpClient();
                    try (Response response = client.newCall(request).execute()) {
                        if (response.isSuccessful()) {
                            mainThreadHandler.post(() -> {
                                Toast.makeText(TripLeadActivity.this,
                                        "Trip created successfully", Toast.LENGTH_SHORT).show();
                            });
                        }
                    } catch (IOException e) {
                        Log.e("TripLeadActivity", "Network error: " + e.getMessage());
                    }
                } catch (JSONException e) {
                    Log.e("TripLeadActivity", "JSON error: " + e.getMessage());
                }
            });
        } else {
            Log.d("TripLeadActivity", "No valid location yet, waiting for GPS fix before sending initial point");

            if (checkPermission() && locationManager != null) {
                locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, new LocationListener() {
                    @Override
                    public void onLocationChanged(@NonNull Location location) {
                        double latitude = location.getLatitude();
                        double longitude = location.getLongitude();

                        Log.d("TripLeadActivity", "Got first GPS fix, sending initial trip point: " +
                                latitude + ", " + longitude);
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
                        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                        String currentDateTime = sdf.format(new Date());

                        SharedPreferences sharedPreferences = getSharedPreferences("UserCredentials", Context.MODE_PRIVATE);
                        String username = sharedPreferences.getString("username", "unknown_user");

                        executor.execute(() -> {
                            try {
                                JSONObject jsonObject = new JSONObject();
                                jsonObject.put("tripId", tripId);
                                jsonObject.put("latitude", latitude);
                                jsonObject.put("longitude", longitude);
                                jsonObject.put("datetime", currentDateTime);
                                jsonObject.put("userName", username);

                                RequestBody requestBody = RequestBody.create(
                                        MediaType.parse("application/json"),
                                        jsonObject.toString()
                                );

                                Request request = new Request.Builder()
                                        .url("http://christopherhield-001-site4.htempurl.com/api/Datapoints/AddTripPoint")
                                        .post(requestBody)
                                        .build();

                                OkHttpClient client = new OkHttpClient();
                                try (Response response = client.newCall(request).execute()) {
                                    if (response.isSuccessful()) {
                                        mainThreadHandler.post(() -> {
                                            Toast.makeText(TripLeadActivity.this,
                                                    "Trip created successfully with actual location", Toast.LENGTH_SHORT).show();
                                        });
                                    }
                                } catch (IOException e) {
                                    Log.e("TripLeadActivity", "Network error: " + e.getMessage());
                                }
                            } catch (JSONException e) {
                                Log.e("TripLeadActivity", "JSON error: " + e.getMessage());
                            }
                        });
                        locationManager.removeUpdates(this);
                    }

                    @Override
                    public void onStatusChanged(String provider, int status, Bundle extras) {}

                    @Override
                    public void onProviderEnabled(@NonNull String provider) {}

                    @Override
                    public void onProviderDisabled(@NonNull String provider) {}
                }, Looper.getMainLooper());
            }
        }
    }

    private void testBroadcastReception() {
        Log.d("TripLeadActivity", "Testing broadcast reception manually");
        Intent testIntent = new Intent(LocationService.BROADCAST_ACTION);
        testIntent.putExtra("LATITUDE", 41.12345);
        testIntent.putExtra("LONGITUDE", -87.54321);
        testIntent.putExtra("BEARING", 0.0f);
        testIntent.putExtra("SPEED", 0.0f);
        testIntent.putExtra("TIME", System.currentTimeMillis());
        Log.d("TripLeadActivity", "Sending test broadcast");
        sendBroadcast(testIntent);
        Log.d("TripLeadActivity", "Test broadcast sent");
    }
}