package com.example.followme;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

/**
 * Broadcast receiver specifically for TripLeadActivity to receive location updates.
 */
public class PointReciever extends BroadcastReceiver {
    private static final String TAG = "PointReciever";
    private final TripLeadActivity tripLeadActivity;

    public PointReciever(TripLeadActivity activity) {
        this.tripLeadActivity = activity;
        Log.d(TAG, "PointReciever constructor - activity reference: " + (activity != null ? "valid" : "null"));
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive method called");

        if (intent == null) {
            Log.e(TAG, "Received null intent");
            return;
        }

        String action = intent.getAction();
        Log.d(TAG, "Intent action: " + (action != null ? action : "null"));

        if (action != null && action.equals(LocationService.BROADCAST_ACTION)) {
            Log.d(TAG, "Action matches BROADCAST_ACTION");

            double latitude = intent.getDoubleExtra("LATITUDE", 0);
            double longitude = intent.getDoubleExtra("LONGITUDE", 0);
            Log.d(TAG, "Extracted location data: lat=" + latitude + ", lng=" + longitude);

            if (tripLeadActivity != null) {
                Log.d(TAG, "TripLeadActivity reference is valid, creating Location object");

                Location location = new Location(LocationManager.GPS_PROVIDER);
                location.setLatitude(latitude);
                location.setLongitude(longitude);
                location.setBearing(intent.getFloatExtra("BEARING", 0));
                location.setSpeed(intent.getFloatExtra("SPEED", 0));
                location.setTime(intent.getLongExtra("TIME", 0));

                Log.d(TAG, "Calling updateLocation in TripLeadActivity");
                tripLeadActivity.updateLocation(location);
                Log.d(TAG, "updateLocation call completed");
            } else {
                Log.e(TAG, "TripLeadActivity reference is null, cannot update location");
            }
        } else {
            Log.d(TAG, "Received intent with unmatched action");
        }
    }
}