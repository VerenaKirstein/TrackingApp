package de.hsbo.veki.trackingapp;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
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
import android.widget.TextView;
import android.widget.Toast;

import com.esri.android.map.FeatureLayer;
import com.esri.android.map.GraphicsLayer;
import com.esri.android.map.MapView;
import com.esri.core.geometry.GeometryEngine;
import com.esri.core.geometry.Point;
import com.esri.core.geometry.SpatialReference;
import com.esri.core.map.Graphic;
import com.esri.core.symbol.SimpleMarkerSymbol;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import static java.lang.String.valueOf;

/**
 * Created by Verena Rabea on 15.06.2016.
 */
public class GPSLocationUpdates implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener ,LocationListener{

    // Attributes for the Map
    private static MapView mMapView;
    protected static String featureLayerURL;
    private SharedPreferences sharedpreferences;
    private static final String PREFS_NAME = "TRACKINGAPP";

    protected static String featureServiceURL;
    private FeatureLayer featureLayer;
    protected static GraphicsLayer mLocationLayer;
    private FeatureLayer offlineFeatureLayer;
    private QueryFeatureLayer queryFeatureLayer;


    // Attributes for GPS-Logging
    private TextView mLatitudeText;
    private TextView mBewGeschw;
    private TextView mLongitudeText;
    private String mLastUpdateTime;

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
    /**
     * stop Location updates for GoogleApiClient
     */
    protected void stopLocationUpdates() {
        if (mGoogleApiClient.isConnected()) {

            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, PendingIntent.getActivity(MainActivity.getContext(),0,new Intent(),PendingIntent.FLAG_UPDATE_CURRENT));
            mGPSActive = false;
        }
    }

    /**
     * start Location updates for GoogleApiClient
     */
    private void startLocationUpdates() {
        if (mGoogleApiClient.isConnected()) {
            Log.i(TAG,"start Location on connect");
            if (Build.VERSION.SDK_INT >= 23 &&
                    ContextCompat.checkSelfPermission(MainActivity.getContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
            mGPSActive = true;

        }
        else{
            mGPSActive=false;
        }

    }

    /**
     * defines how the location updates shall be received
     */
    public void controlGPS() {
        if (useGooglePlayService) {
            if (mGPSActive == true) {
                stopLocationUpdates();
            } else {
                Log.i(TAG, "StartLocationUpdates");
                startLocationUpdates();
            }
        } else {
            if (mGPSActive == true) {
                stopSimpleLocationUpdates();
            } else {
                startSimpleLocationUpdates();
            }
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        Point oldLoc = new Point();
        double locY = location.getLatitude();
        double locX = location.getLongitude();

        Point wgsPoint = new Point(locX, locY);

        if (mLocation != null) {
            oldLoc = mLocation;
        }

        mLocation = (Point) GeometryEngine.project(wgsPoint,
                SpatialReference.create(4326),
                mMapView.getSpatialReference());

        if (mLocation != null) {
            updateGraphic(mLocation);
        }
        if (oldLoc.isEmpty() == false) {
            double distance = Math.sqrt(Math.pow(oldLoc.getX() - mLocation.getX(), 2) + Math.pow(oldLoc.getY() - mLocation.getY(), 2));
            double geschw = distance / (60);
            geschw = Math.round(geschw * 100) / 100.0;
            mBewGeschw.setText(valueOf(geschw + " m/s"));
        } else {
            mBewGeschw.setText(valueOf(0.0 + " m/s"));
        }

        mLatitudeText.setText(valueOf(location.getLatitude()));
        mLongitudeText.setText(valueOf(location.getLongitude()));

        mLastLocation = location;
        sharedpreferences =MainActivity.getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        //make a map of attributes
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put("ID", "1");
        attributes.put("Username", sharedpreferences.getString("username", ""));
        attributes.put("Vehicle", sharedpreferences.getString("vehicle", ""));

        DateFormat datef = new SimpleDateFormat("dd.MM.yyyy");
        String date = datef.format(Calendar.getInstance().getTime());
        mLastUpdateTime = date;

        attributes.put("Date", mLastUpdateTime);
        Toast.makeText(MainActivity.getContext(), "Updated: " + mLastUpdateTime, Toast.LENGTH_SHORT).show();

        try {
           // trackGeodatabase.addFeature(attributes, mLocation);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void updateGraphic(Point nLocation) {

        SimpleMarkerSymbol resultSymbolact = new SimpleMarkerSymbol(Color.RED, 16, SimpleMarkerSymbol.STYLE.DIAMOND);

        Graphic resultLocGraphic = new Graphic(nLocation, resultSymbolact);
        // add graphic to location layer
        try {
            mLocationLayer.updateGraphic(mLocationLayer.getGraphicIDs()[mLocationLayer.getNumberOfGraphics() - 1], resultLocGraphic);
        } catch (NullPointerException e) {
            e.getStackTrace();
        }


    }

}
