package de.hsbo.veki.trackingapp;

import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
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
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Result;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;

import java.util.ArrayList;

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
    public boolean onUnbind(Intent intent) {
        Log.i("Background","Unbind");
        connector.removeActivityUpdates();
        return super.onUnbind(intent);
    }
    @Override
    public synchronized void onCreate() {

        handlerThread = new HandlerThread("Service", Process.THREAD_PRIORITY_BACKGROUND);
        handlerThread.start();


    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int updateInterval = intent.getIntExtra("Update_Interval",15000);
        connector = new ApiConnector();
        Log.i("Interval", " " +updateInterval);
        connector.changeLocationRequestInterval(updateInterval);


        return START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {


        super.onDestroy();
        Log.i("TAG", "ON DESTROY");
        connector.stopLocationUpdates();
        connector.removeActivityUpdates();
        handlerThread.interrupt();
    }



    @Override
    public IBinder onBind(Intent intent) {

          //  connector.requestActivityUpdates();

        return binder;
    }


    public class LocalBinder extends Binder {

        public BackgroundLocationService getService() {
            return BackgroundLocationService.this;
        }
    }

    public class ApiConnector implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener,ResultCallback {

        private LocationManager locationManager1;
        private android.location.LocationListener locationListener1;
        private LocationManager locationManager;
        private LocationListener locationListener;


        public ApiConnector() {
        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            startLocationUpdates();
            requestActivityUpdates();
        }

        @Override
        public void onConnectionSuspended(int i) {

        }
        private PendingIntent getActivityDetectionPendingIntent() {
            Intent intent = new Intent(getBaseContext(), DetectedActivitiesIntentService.class);

            // We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when calling
            // requestActivityUpdates() and removeActivityUpdates().
            return PendingIntent.getService(getBaseContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
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


        public synchronized void createGoogleApiClient(int interval) {
            if (MainActivity.client == null) {
                Log.i("Background","Create Google Api Client");
                MainActivity.client = new GoogleApiClient.Builder(getApplicationContext())
                        .addConnectionCallbacks(this)
                        .addOnConnectionFailedListener(this)
                        .addApi(LocationServices.API).addApi(ActivityRecognition.API)
                        .build();
               // MainActivity.client.connect();
            }


            if (MainActivity.mLocationRequest == null) {

                Log.i("Background","Create LocationRequest");
                MainActivity.mLocationRequest = LocationRequest.create();
                MainActivity.mLocationRequest.setInterval(interval);
                MainActivity.mLocationRequest.setFastestInterval(interval / 2);
                MainActivity.mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

                LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                        .addLocationRequest(MainActivity.mLocationRequest);
                PendingResult<LocationSettingsResult> result =
                        LocationServices.SettingsApi.checkLocationSettings(MainActivity.client,
                                builder.build());

                Log.i("", MainActivity.client.toString());
                Log.i("Background","Connect");
                MainActivity.client.connect();

            } else if (!MainActivity.client.isConnected() && !MainActivity.client.isConnecting()) {
                Log.i("Background","Connect");
                MainActivity.client.connect();
            }
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
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 0.5f, locationListener1);
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 0.5f, locationListener1);

        }

        /**
         * stop receiving location updates
         */
        public void stopSimpleLocationUpdates() {

            if (Build.VERSION.SDK_INT >= 23 &&
                    ContextCompat.checkSelfPermission(MainActivity.getContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            locationManager.removeUpdates(locationListener1);
        }

        public synchronized void changeLocationRequestInterval(int interval) {
            if (MainActivity.client != null) {
                Log.i("Background","Google Api Client nicht null");
                if (MainActivity.client.isConnected() || MainActivity.client.isConnecting()) {
                    MainActivity.client.disconnect();
                }
                MainActivity.client = null;
                MainActivity.mLocationRequest = null;
                createGoogleApiClient(interval);


            } else {
                Log.i("Background","Create Google Api Client2");
                createGoogleApiClient(interval);

            }
        }
        /**
         * request Detected Activities Updates
         */
        public void requestActivityUpdates() {
            if (MainActivity.client != null) {
                if (!MainActivity.client.isConnected()) {
                    Toast.makeText(MainActivity.getContext(), "Not connected",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(
                        MainActivity.client,
                        Constants.DETECTION_INTERVAL_IN_MILLISECONDS,
                        getActivityDetectionPendingIntent()
                ).setResultCallback(this);
            }
        }



        /**
         * stop requesting the activity and remove the activity updates for the PendingIntent
         */
        public void removeActivityUpdates() {
            if (MainActivity.client != null) {
                if (!MainActivity.client.isConnected()) {
                    Toast.makeText(MainActivity.getContext(), "Not Connected", Toast.LENGTH_SHORT).show();
                    return;
                }
                // Remove all activity updates for the PendingIntent that was used to request activity
                // updates.
                ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(
                        MainActivity.client,
                        getActivityDetectionPendingIntent()
                ).setResultCallback(this);
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

        @Override
        public void onResult(@NonNull Result result) {

        }
    }
}
