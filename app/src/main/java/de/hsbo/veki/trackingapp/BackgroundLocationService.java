package de.hsbo.veki.trackingapp;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Process;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
/**
 * Created by Thorsten Kelm on 19.06.2016.
 */
public class BackgroundLocationService extends Service {

    //public GoogleApiClient mGoogleApiClient= null;
    public Location mLastLocation;
    ApiConnector connector;
    HandlerThread handlerThread;

    public final IBinder binder = new LocalBinder();

    public static final String BROADCAST_ACTION = "de.hsbo.veki.trackingapp.BROADCAST";
    public static final String EXTENDED_DATA_STATUS = "de.hsbo.veki.trackingapp.STATUS";


    @Override
    public synchronized void onCreate() {

        handlerThread = new HandlerThread("Service", Process.THREAD_PRIORITY_BACKGROUND);
        handlerThread.start();

        connector = new ApiConnector();
        connector.createGoogleApiClient();
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {


        super.onDestroy();
        Log.i("TAG", "ON DESTROY");
        connector.stopLocationUpdates();
        handlerThread.interrupt();
    }



    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }


    public class LocalBinder extends Binder {

        public BackgroundLocationService getService() {
            return BackgroundLocationService.this;
        }
    }

    public class ApiConnector implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {


        private LocationManager locationManager;
        private LocationListener locationListener;

        private int GPS_INTERVAL = 3000;

        public ApiConnector() {
            if (MainActivity.client == null) {
                MainActivity.client = new GoogleApiClient.Builder(getApplicationContext())
                        .addConnectionCallbacks(this)
                        .addOnConnectionFailedListener(this)
                        .addApi(LocationServices.API)
                        .build();
                MainActivity.client.connect();
            }
        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            startLocationUpdates();
        }

        @Override
        public void onConnectionSuspended(int i) {

        }

        @Override
        public void onLocationChanged(Location location) {
            mLastLocation = location;
            if (mLastLocation != null) {
                Intent localIntent = new Intent(BROADCAST_ACTION)
                        .putExtra("Lat", mLastLocation.getLatitude())
                        .putExtra("Lon", mLastLocation.getLongitude());

                Log.i("Background", mLastLocation.toString());
                LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(localIntent);
            }

        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

        }


        public synchronized void createGoogleApiClient() {
            if (MainActivity.mLocationRequest == null) {


                MainActivity.mLocationRequest = LocationRequest.create();
                MainActivity.mLocationRequest.setInterval(GPS_INTERVAL);
                MainActivity.mLocationRequest.setFastestInterval(GPS_INTERVAL / 2);
                MainActivity.mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

                LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                        .addLocationRequest(MainActivity.mLocationRequest);
                PendingResult<LocationSettingsResult> result =
                        LocationServices.SettingsApi.checkLocationSettings(MainActivity.client,
                                builder.build());

                Log.i("CLient", MainActivity.client.toString());

            } else if (!MainActivity.client.isConnected() && !MainActivity.client.isConnecting()) {
                MainActivity.client.connect();
            }
        }


        /**
         * stop Location updates for GoogleApiClient
         */
        protected void stopLocationUpdates() {
            if (MainActivity.client.isConnected()) {

                LocationServices.FusedLocationApi.removeLocationUpdates(MainActivity.client, this);//PendingIntent.getActivity(this,0,new Intent(),PendingIntent.FLAG_UPDATE_CURRENT));
                Log.i("TAG", "stop Location on connect");

                   MainActivity.client.disconnect();
            }
        }

        /**
         * start Location updates for GoogleApiClient
         */
        private void startLocationUpdates() {
            if (MainActivity.client.isConnected()) {
                Log.i("TAG", "start Location on connect");
                if (Build.VERSION.SDK_INT >= 23 &&
                        ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                LocationServices.FusedLocationApi.requestLocationUpdates(MainActivity.client, MainActivity.mLocationRequest, this);


            } else {

            }

        }
    }
}
