package de.hsbo.veki.trackingapp;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.location.Location;
import android.location.LocationManager;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.MenuItem;

import com.esri.core.geometry.Point;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;

/**
 * Created by Verena Rabea on 15.06.2016.
 */
public class GPSLocationUpdates implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    public GoogleApiClient mGoogleApiClient;
    public static final String TAG = "GPSLocationUpdates";
    public LocationRequest mLocationRequest;
    private Point mLocation;
    public boolean mGPSActive = false;
    public boolean useGooglePlayService = true;
    private LocationManager locationManager;
    private android.location.LocationListener locationListener;
    public Location mLastLocation;
    private int GPS_INTERVAL = 10000;

    public GPSLocationUpdates() {

    }

    /**
     * create the googleApiClient and start LocationRequest
     */
    protected synchronized void createGoogleApiClient(int interval) {
        if (mGoogleApiClient == null) {
            Log.i(TAG,"Create GoogleApiClient");
            mGoogleApiClient = new GoogleApiClient.Builder(MainActivity.getContext())
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API).addApi(ActivityRecognition.API)
                    .build();
            createLocationRequest(interval);
        }
    }

    /**
     * create the Location Request for Updating the GPS Location
     * set the GPS Interval and the piority
     */
    protected synchronized void createLocationRequest(int interval) {
        if (mLocationRequest == null) {
            Log.i(TAG, "CreateLocationRequest");
            mLocationRequest = LocationRequest.create();
            mLocationRequest.setInterval(interval);
            mLocationRequest.setFastestInterval(interval / 2);
            mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

            LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                    .addLocationRequest(mLocationRequest);
            PendingResult<LocationSettingsResult> result =
                    LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient,
                            builder.build());

            mGoogleApiClient.connect();
        }
    }

    /**
     *
     *
     * @param bundle
     */
    public synchronized void onConnected(Bundle bundle) {

        if (ActivityCompat.checkSelfPermission(MainActivity.getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(MainActivity.getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(
                mGoogleApiClient);
    }


    @Override
    public void onConnectionSuspended(int i) {
        Log.i(TAG, "Connection Suspended");
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.i(TAG, "Connection failed. Error: " + connectionResult.getErrorCode());
    }

    /**
     * if no GooglePlayService is available the location is received by the locationManager
     */
    public void startSimpleLocationUpdates() {
        if (Build.VERSION.SDK_INT >= 23 &&
                ContextCompat.checkSelfPermission(MainActivity.getContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        // Register the listener to receive location updates
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 0.5f, locationListener);
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 0.5f, locationListener);

    }

    /**
     * stop receiving location updates
     */
    public void stopSimpleLocationUpdates() {

        if (Build.VERSION.SDK_INT >= 23 &&
                ContextCompat.checkSelfPermission(MainActivity.getContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        locationManager.removeUpdates(locationListener);
    }

    public synchronized void changeLocationRequestInterval(int interval) {
        if (mGoogleApiClient != null) {
            Log.i(TAG,"ChangeInterval Client != null");
            if (mGoogleApiClient.isConnected()) {
                mGoogleApiClient.disconnect();
            }
            mGoogleApiClient = null;
            mLocationRequest= null;
            createGoogleApiClient(interval);


        } else {
            Log.i(TAG,"ChangeInterval Client == null");
            createGoogleApiClient(interval);

        }
    }

}
