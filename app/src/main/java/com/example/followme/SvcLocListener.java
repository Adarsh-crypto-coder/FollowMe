package com.example.followme;

import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;

public class SvcLocListener implements LocationListener {

    private static final String TAG = "SvcLocListener";
    private final LocationService locationService;

    SvcLocListener(LocationService locationService) {
        this.locationService = locationService;
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        Log.d(TAG, "Location changed: " + location.getLatitude() + ", " + location.getLongitude());
        Log.d(TAG, "Calling broadcastLocation in LocationService");
        locationService.broadcastLocation(location);
        Log.d(TAG, "broadcastLocation called successfully");
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        Log.d(TAG, "Status changed: " + provider + " status: " + status);
    }

    @Override
    public void onProviderEnabled(@NonNull String provider) {
        Log.d(TAG, "Provider enabled: " + provider);
    }

    @Override
    public void onProviderDisabled(@NonNull String provider) {
        Log.d(TAG, "Provider disabled: " + provider);
    }
}