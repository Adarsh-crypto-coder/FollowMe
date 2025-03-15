package com.example.followme;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

public class LocationService extends Service {

    private static final String TAG = "LocationService";
    private Notification notification;

    private final String channelId = "LOCATION_CHANNEL";
    private LocationManager locationManager;
    private LocationListener locationListener;
    private MediaPlayer mediaPlayer;

    // Add a binder for binding activities
    private final IBinder binder = new LocationBinder();

    // Tracking parameters
    private static final long UPDATE_INTERVAL = 1000; // 1 second
    private static final float MIN_DISTANCE = 0;     // any movement

    // Broadcast action specifically for TripLeadActivity
    public static final String BROADCAST_ACTION = "com.example.followme.TRIP_LOCATION_UPDATE";

    public class LocationBinder extends Binder {
        LocationService getService() {
            return LocationService.this;
        }
    }

    // In LocationService.java - onCreate method
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate: Service is being created");

        // Create notification channel
        createNotificationChannel();

        // Create a notification required for foreground service
        notification = createNotification();

        // Initialize audio for service start sound
        initializeAudio();

        // Log the broadcast action that will be used
        Log.d(TAG, "Service will broadcast location updates with action: " + BROADCAST_ACTION);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: " + intent);

        try {
            // Start the service in the foreground with the appropriate type
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Log.d(TAG, "Starting foreground service with FOREGROUND_SERVICE_TYPE_LOCATION");
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            } else {
                Log.d(TAG, "Starting foreground service (pre-Android 10)");
                startForeground(1, notification);
            }

            // Play the start sound
            playSound();

            // Set up location tracking
            setupLocationListener();

            Log.d(TAG, "onStartCommand: Service started in foreground successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error starting foreground service: " + e.getMessage());
            e.printStackTrace();
            stopSelf();
            return START_NOT_STICKY;
        }

        // If the service is killed, restart it
        return Service.START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, TripLeadActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, channelId)
                .setContentTitle("Follow Me Active")
                .setContentText("Tracking your trip location")
                .setSmallIcon(R.drawable.car)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Uri soundUri = Uri.parse("android.resource://" +
                    this.getPackageName() + "/" +
                    R.raw.notif_sound);
            AudioAttributes att = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build();

            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel mChannel = new NotificationChannel(channelId, "Trip Location Tracking", importance);
            mChannel.setSound(soundUri, att);
            mChannel.setLightColor(Color.BLUE);
            mChannel.setVibrationPattern(new long[]{0, 300, 100, 300});
            mChannel.setDescription("Tracks your location for trip sharing");

            NotificationManager mNotificationManager = getSystemService(NotificationManager.class);
            mNotificationManager.createNotificationChannel(mChannel);
            Log.d(TAG, "Notification channel created");
        }
    }

    private void initializeAudio() {
        try {
            // Create MediaPlayer and set it up with your sound file
            mediaPlayer = MediaPlayer.create(this, R.raw.notif_sound);

            // Optional: Set to loop if needed
            mediaPlayer.setLooping(false);

            // Optional: Set volume
            mediaPlayer.setVolume(1.0f, 1.0f);

            // Set an OnCompletionListener if needed
            mediaPlayer.setOnCompletionListener(mp -> {
                // Do something when playback completes
                Log.d(TAG, "Sound playback completed");
            });

            Log.d(TAG, "Audio initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing MediaPlayer: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void playSound() {
        try {
            if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
                mediaPlayer.start();
                Log.d(TAG, "Sound playback started");
            } else {
                Log.d(TAG, "MediaPlayer is null or already playing");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error playing sound: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setupLocationListener() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationListener = new SvcLocListener(this);

        if (ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permission not granted");
            return;
        }

        if (locationManager != null) {
            Log.d(TAG, "Requesting location updates");
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    UPDATE_INTERVAL,
                    MIN_DISTANCE,
                    locationListener);
        }
    }

    /**
     * Broadcasts location updates specifically for TripLeadActivity
     */
    public void broadcastLocation(Location location) {
        Log.d(TAG, "Creating broadcast intent with action: " + BROADCAST_ACTION);

        Intent intent = new Intent(BROADCAST_ACTION);
        intent.setPackage(getPackageName());
        intent.putExtra("LATITUDE", location.getLatitude());
        intent.putExtra("LONGITUDE", location.getLongitude());
        intent.putExtra("BEARING", location.getBearing());
        intent.putExtra("SPEED", location.getSpeed());
        intent.putExtra("ACCURACY", location.getAccuracy());
        intent.putExtra("TIME", location.getTime());

        Log.d(TAG, "Sending explicit broadcast to package: " + getPackageName());
        sendBroadcast(intent);
        Log.d(TAG, "Trip location broadcast sent");
        Log.d(TAG, "Trip location broadcast: " + location.getLatitude() + ", " + location.getLongitude());
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy: Stopping trip location service");
        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
        }

        // Clean up MediaPlayer resources
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
                mediaPlayer = null;
                Log.d(TAG, "MediaPlayer released");
            } catch (Exception e) {
                Log.e(TAG, "Error releasing MediaPlayer: " + e.getMessage());
            }
        }

        Log.d(TAG, "TRIP LOCATION SERVICE DESTROYED");
        super.onDestroy();
    }
}