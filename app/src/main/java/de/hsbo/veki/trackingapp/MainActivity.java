package de.hsbo.veki.trackingapp;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.esri.android.map.Callout;
import com.esri.android.map.FeatureLayer;
import com.esri.android.map.GraphicsLayer;
import com.esri.android.map.MapOptions;
import com.esri.android.map.MapView;
import com.esri.android.map.event.OnSingleTapListener;
import com.esri.core.geodatabase.Geodatabase;
import com.esri.core.geodatabase.GeodatabaseFeatureServiceTable;
import com.esri.core.geodatabase.GeodatabaseFeatureTable;
import com.esri.core.geometry.Envelope;
import com.esri.core.geometry.GeometryEngine;
import com.esri.core.geometry.LinearUnit;
import com.esri.core.geometry.Point;
import com.esri.core.geometry.SpatialReference;
import com.esri.core.geometry.Unit;
import com.esri.core.map.CallbackListener;
import com.esri.core.map.Feature;
import com.esri.core.map.FeatureResult;
import com.esri.core.map.Graphic;
import com.esri.core.symbol.SimpleMarkerSymbol;
import com.esri.core.tasks.geodatabase.GeodatabaseSyncTask;
import com.esri.core.tasks.query.QueryParameters;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;

import java.io.File;
import java.io.FileNotFoundException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import static java.lang.String.*;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {


    // Attributes for Android usage
    public static final String TAG = "SAMPLE_ANDROID_APP";
    private static final String PREFS_NAME = "TRACKINGAPP";
    private SharedPreferences sharedpreferences;
    private static Context mContext;
    public static ProgressDialog mProgressDialog;
    private Toast toast;


    // Attributes for Filegeodatabase
    private static GeodatabaseSyncTask gdbSyncTask;
    private static File demoDataFile;
    private static String offlineDataSDCardDirName;
    private static String filename;
    private static String localGdbFilePath;
    private static String OFFLINE_FILE_EXTENSION = ".geodatabase";
    private static Geodatabase geodatabase = null;
    private GeodatabaseFeatureTable geodatabaseFeatureTable;
    private static Callout callout;
    private TrackGeodatabase trackGeodatabase;
    private String username;


    // Attributes for the Map
    private static MapView mMapView;
    protected static String featureLayerURL;



    protected static String featureServiceURL;
    private FeatureLayer featureLayer;
    protected static GraphicsLayer mLocationLayer;
    private FeatureLayer offlineFeatureLayer;
    private QueryFeatureLayer queryFeatureLayer;


    // Attributes for GPS-Logging
    private GoogleApiClient mGoogleApiClient;
    private TextView mLatitudeText;
    private TextView mBewGeschw;
    private TextView mLongitudeText;
    private int GPS_INTERVAL = 10000;
    private int GPS_FASTEST_INTERVAL = 2000;
    private LocationRequest mLocationRequest;
    private String mLastUpdateTime;
    private Point mLocation;
    private boolean mGPSActive = false;
    private boolean useGooglePlayService = true;
    private LocationManager locationManager;
    private android.location.LocationListener locationListener;
    private Location mLastLocation;

    // Attributes for UI
    private Button button;
    private Menu menu;
    private MenuItem carCheckbox; //= null;
    private MenuItem pedestrianCheckbox = null;
    private MenuItem bicycleCheckbox = null;
    private MenuItem autoCheckbox = null;
    private MapOptions mapOptions = new MapOptions(MapOptions.MapType.OSM);
    public GeodatabaseFeatureServiceTable featureServiceTable;
    private QueryParameters q;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);


        // Set Context
        MainActivity.setContext(this);

        // Load TextViews
        mLatitudeText = (TextView) findViewById(R.id.GPSLatText);
        mLongitudeText = (TextView) findViewById(R.id.GPSLonText);
        mBewGeschw = (TextView) findViewById(R.id.BwGeschw);

        // Load Button
        button = (Button) findViewById(R.id.button);

        checkGPS();
        createGoogleApiClient();

        // Read Username from Device
        sharedpreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        username = sharedpreferences.getString("username", "");

        // TODO: Hier ggf. nicht passend! (in andere Methode)
        if (username.isEmpty()) {
            Toast.makeText(getApplicationContext(), "Bitte Benutzername eingeben!", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(getApplicationContext(), "Eingeloggt als: " + username + ".", Toast.LENGTH_LONG).show();
        }

        // Get resource names
        demoDataFile = Environment.getExternalStorageDirectory();
        offlineDataSDCardDirName = this.getResources().getString(R.string.config_data_sdcard_offline_dir);
        filename = this.getResources().getString(R.string.config_geodatabase_name);

        // Check for Filegeodatabase
        //File f = new File(createGeodatabaseFilePath());
        //Log.e(getClass().getSimpleName(), "Does the file exist? " + f.exists());

        // Get FeatureURL
        featureServiceURL = this.getResources().getString(R.string.FeatureServiceURL);
        featureLayerURL = this.getResources().getString(R.string.FeatureLayerURL);

        /*// Create filegeodatabase if no exists
        if (f.exists() == false) {

            mProgressDialog = new ProgressDialog(MainActivity.this);
            mProgressDialog.setTitle("Create local runtime geodatabase");

            createFilegeodatabase(featureServiceURL);

        } else {
            //geodatabase.createClientDelta(featureLayerURL);
        }*/


        // Retrieve the map and initial extent from XML layout
        mMapView = (MapView) findViewById(R.id.map);

        // attribute app and pan across dateline
        mMapView.setEsriLogoVisible(true);
        mMapView.enableWrapAround(true);


        try {

            trackGeodatabase = new TrackGeodatabase(createGeodatabaseFilePath(), featureServiceURL, mMapView);
            offlineFeatureLayer = trackGeodatabase.getOfflineFeatureLayer();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }


        mLocationLayer = new GraphicsLayer();
        mMapView.addLayer(mLocationLayer);

        // Create a personal FeatureLayer and add to map
        new QueryFeatureLayer().execute(username);

        //open the local geodatabase file
        try

        {
            trackGeodatabase.getOfflineFeatureLayer();
            geodatabase = new Geodatabase(createGeodatabaseFilePath());

            // Create a GeodatabaseFeatureServiceTable from the URL of a feature service.
            featureServiceTable = new GeodatabaseFeatureServiceTable(featureServiceURL, 0);


            geodatabaseFeatureTable = trackGeodatabase.getGeodatabaseFeatureTable();


            //create a feature layer and add it to the map
            offlineFeatureLayer = new FeatureLayer(geodatabaseFeatureTable);

            q = new QueryParameters();
            q.setWhere("Username=" + username);
            offlineFeatureLayer.selectFeatures(q, FeatureLayer.SelectionMode.NEW, new CallbackListener<FeatureResult>() {
                @Override
                public void onCallback(FeatureResult objects) {

                    // Define a new marker symbol for the result graphics
                    SimpleMarkerSymbol sms = new SimpleMarkerSymbol(Color.GREEN, 10, SimpleMarkerSymbol.STYLE.CROSS);

                    for (Object objFeature : objects) {
                        Feature feature = (Feature) objFeature;
                        Graphic graphic = new Graphic(feature.getGeometry(), sms, feature.getAttributes());

                        // add it to the layer
                        mLocationLayer.addGraphic(graphic);
                    }

                }

                @Override
                public void onError(Throwable throwable) {
                    Log.e(TAG, throwable.toString());
                }
            });

            // mMapView.addLayer(offlineFeatureLayer);
        } catch (
                FileNotFoundException e
                )

        {
            e.printStackTrace();
        }

        mMapView.addLayer(offlineFeatureLayer);
        mMapView.getLayer(2).setVisible(false);




        // Add Listener
        mMapView.setOnSingleTapListener(new

                                                OnSingleTapListener() {

                                                    private static final long serialVersionUID = 1L;

                                                    @Override
                                                    public void onSingleTap(float x, float y) {

                                                        long[] selectedFeatures = offlineFeatureLayer.getFeatureIDs(x, y, 25, 1);

                                                        if (selectedFeatures.length > 0) {

                                                            // Feature is selected
                                                            offlineFeatureLayer.selectFeatures(selectedFeatures, false);

                                                            if (selectedFeatures != null && selectedFeatures.length > 0) {


                                                                Feature fpktNr = offlineFeatureLayer.getFeature(selectedFeatures[0]);
                                                                String pntId = fpktNr.getAttributeValue("ID").toString();
                                                                String pntUsername = fpktNr.getAttributeValue("Username").toString();
                                                                String pntVehicle = fpktNr.getAttributeValue("Vehicle").toString();
                                                                Long pntDate = (Long) fpktNr.getAttributeValue("Date");
                                                                Log.e("Date", pntDate.toString());

                                                                if (pntUsername.equals(username)) {
                                                                    callout = mMapView.getCallout();
                                                                    callout.setStyle(R.xml.tracked_point);
                                                                    callout.setContent(loadView(pntId, username, pntVehicle, pntDate));
                                                                    callout.show((Point) fpktNr.getGeometry());
                                                                } else {
                                                                    if (callout != null && callout.isShowing()) {
                                                                        callout.hide();
                                                                    }
                                                                }



                                                            } else {
                                                                if (callout != null && callout.isShowing()) {
                                                                    callout.hide();
                                                                }
                                                            }

                                                        } else {
                                                            // Wenn kein Punkt getroffen wurde wird nachfolgende Meldung gezeigt
                                                            // Toast.makeText(getApplicationContext(), "No Point selected", Toast.LENGTH_SHORT).show();

                                                            callout.hide();

                                                            // Selektion aufheben
                                                            offlineFeatureLayer.clearSelection();
                                                        }
                                                    }

                                                    private View loadView(String id, String username, String vehicle, Long date) {
                                                        View view = LayoutInflater.from(MainActivity.this).inflate(R.layout.pointinfo, null);
                                                        final TextView textNummer = (TextView) view.findViewById(R.id.popup);
                                                        String out = "ID: " + id + "\nUsername: " + username + "\nVehicle: " + vehicle + "\nDate: " + convertDateToString(date);
                                                        textNummer.setText(out); //" ID: " + id + "\n Username: " + username + "\n Vehrkehrsmittel: " + vehicle + "\n Datum: " + date);

                                                        return view;
                                                    }


                                                });



        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Context context = getApplicationContext();
                Toast.makeText(context, "Beginne Syncronistation!", Toast.LENGTH_SHORT).show();
                try {
                    trackGeodatabase.syncGeodatabase();

                    // Remove and add offlineLayer
                    mLocationLayer.removeAll();
                    new QueryFeatureLayer().execute(username);

                } catch (Exception e) {
                    e.printStackTrace();
                }



            }
        });

    }



    /*
    * Create the geodatabase file location and name structure
    */
    static String createGeodatabaseFilePath() {
        StringBuilder sb = new StringBuilder();
        sb.append(demoDataFile.getAbsolutePath());
        sb.append(File.separator);
        sb.append(offlineDataSDCardDirName);
        sb.append(File.separator);
        sb.append(filename);
        sb.append(OFFLINE_FILE_EXTENSION);

        Log.e("String", sb.toString());
        return sb.toString();
    }


    /**
     * Converts long Date to String Date
     *
     * @param date
     * @return
     */
    static String convertDateToString(Long date) {

        SimpleDateFormat df2 = new SimpleDateFormat("dd.MM.yy");
        String dateText = df2.format(date);
        return dateText;
    }





    private void checkGPS() {

        LocationManager service = (LocationManager) getSystemService(LOCATION_SERVICE);
        boolean enabled = service
                .isProviderEnabled(LocationManager.GPS_PROVIDER);

        // check if enabled and if not send user to the GSP settings
        // Better solution would be to display a dialog and suggesting to
        // go to the settings
        if (!enabled) {
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(intent);
        }

    }

    protected synchronized void createGoogleApiClient() {
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();

        }
    }

    protected void createLocationRequest() {
        mLocationRequest = LocationRequest.create();
        mLocationRequest.setInterval(GPS_INTERVAL);
        mLocationRequest.setFastestInterval(GPS_FASTEST_INTERVAL);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(mLocationRequest);
        PendingResult<LocationSettingsResult> result =
                LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient,
                        builder.build());
    }


    /*private void updateUI() {
        mLatitudeText.setText(String.valueOf(mLastLocation.getLatitude()));
        mLongitudeText.setText(String.valueOf(mLastLocation.getLongitude()));
        mBewGeschw.setText(String.valueOf(mLastLocation.getSpeed()));

    }*/

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        this.menu=menu;
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);

        carCheckbox = menu.getItem(1).getSubMenu().getItem(0);
        pedestrianCheckbox = menu.getItem(1).getSubMenu().getItem(1);
        bicycleCheckbox = menu.getItem(1).getSubMenu().getItem(2);
        autoCheckbox = menu.getItem(1).getSubMenu().getItem(3);

        String getVehicle = sharedpreferences.getString("vehicle", "");

        switch (getVehicle) {
            case "Auto":
                carCheckbox.setChecked(true);
            case "Fußgänger":
                pedestrianCheckbox.setChecked(true);
            case "Fahrrad":
                bicycleCheckbox.setChecked(true);
            case "Automatisch erkennen":
                autoCheckbox.setChecked(true);
            default:
                pedestrianCheckbox.setChecked(true);
                setVehicle("Fußgänger");

        }

        getVehicle = sharedpreferences.getString("vehicle", "");
        Toast.makeText(getApplicationContext(), "oncreate: " + getVehicle + " gesetzt", Toast.LENGTH_LONG).show();
        return true;

    }

    public void setVehicle(String vehicle) {
        SharedPreferences.Editor editor = sharedpreferences.edit();

        String username = sharedpreferences.getString("username", "");

        editor.clear();

        editor.putString("vehicle", vehicle);
        editor.putString("username", username);
        editor.commit();
    }

    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    protected void onStop() {
        super.onStop();
        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Wir prüfen, ob Menü-Element mit der ID "action_daten_aktualisieren"
        // ausgewählt wurde und geben eine Meldung aus

        switch (item.getItemId()) {

            case R.id.action_username:
                Intent username_intent = new Intent(this, ChangeUsernameActivity.class);
                startActivity(username_intent);
                return true;

            case R.id.action_location_found:
                if (!item.isChecked()) {
                    controlGPS();
                    item.setChecked(true);
                    item.setIcon(R.drawable.gps_on_highres);
                    item.getIcon();
                    Toast.makeText(getApplicationContext(), "Start Tracking", Toast.LENGTH_SHORT).show();
                    return true;
                } else {
                    controlGPS();
                    item.setChecked(false);
                    item.setIcon(R.drawable.gps_off_highres);
                    Toast.makeText(getApplicationContext(), "Stop Tracking", Toast.LENGTH_SHORT).show();
                    return true;
                }


            case R.id.carMenuItem:
                setVehicle("Auto");
                carCheckbox.setChecked(true);
                mLocationRequest.setInterval(2000);
                Log.e("carMenuItem", sharedpreferences.getString("vehicle", ""));
                return true;

            case R.id.pedestrianMenuItem:
                setVehicle("Fußgänger");
                mLocationRequest.setInterval(10000);
                pedestrianCheckbox.setChecked(true);
                Log.e("pedestMenuItem", sharedpreferences.getString("vehicle", ""));
                return true;

            case R.id.bicycleMenuItem:
                setVehicle("Fahrrad");
                mLocationRequest.setInterval(3000);
                bicycleCheckbox.setChecked(true);
                Log.e("bicyMenuItem", sharedpreferences.getString("vehicle", ""));
                return true;

            default:
                return super.onOptionsItemSelected(item);

        }


    }



    public void onConnected(Bundle bundle) {


        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        createLocationRequest();
        if(menu.getItem(0).isChecked()){
        startLocationUpdates();}
        else {
            stopLocationUpdates();
        }
        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(
                mGoogleApiClient);
        // LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }


    private void startSimpleLocationUpdates() {
        // Register the listener with the Location Manager to receive location updates
        if (Build.VERSION.SDK_INT >= 23 &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        // Register the listener with the Location Manager to receive location updates
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 0.5f, locationListener);
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 0.5f, locationListener);

    }

    private void stopSimpleLocationUpdates() {

        if (Build.VERSION.SDK_INT >= 23 &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        // Remove the listener you previously added
        locationManager.removeUpdates(locationListener);
    }

    protected void stopLocationUpdates() {
        if (mGoogleApiClient.isConnected()) {

            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
            mGPSActive = false;
        }
    }

    private void startLocationUpdates() {
        if (mGoogleApiClient.isConnected()) {
            if (Build.VERSION.SDK_INT >= 23 &&
                    ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
            mGPSActive = true;

        }
    }

    public void controlGPS() {
        if (useGooglePlayService) {
            if (mGPSActive == true) {
                stopLocationUpdates();
            } else {
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
    public void onConnectionSuspended(int i) {
        Log.i(TAG, "Connection Suspended");
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.i(TAG, "Connection failed. Error: " + connectionResult.getErrorCode());
    }

    @Override
    public void onLocationChanged(Location location) {

        double locY = location.getLatitude();
        double locX = location.getLongitude();

        Point wgsPoint = new Point(locX, locY);

        mLocation = (Point) GeometryEngine.project(wgsPoint,
                SpatialReference.create(4326),
                mMapView.getSpatialReference());

        if(mLocation!=null)
            updateGraphic(mLocation);

        mLatitudeText.setText(valueOf(location.getLatitude()));
        mLongitudeText.setText(valueOf(location.getLongitude()));
        double distance = Math.sqrt(Math.pow(mLastLocation.getLatitude()-location.getLatitude(),2)+ Math.pow(mLastLocation.getLongitude()-location.getLongitude(),2));
       //TODO calculate time difference
        int time =(int)Calendar.getInstance().getTime().getTime() - (int)Calendar.getInstance().getTime().getTime();
        if(time!= 0) {
            double geschw = distance / time;
            mBewGeschw.setText(valueOf(geschw));
        }
        else{
            mBewGeschw.setText(valueOf(0.0));
        }
        Toast.makeText(this, "Updated: " + mLastUpdateTime, Toast.LENGTH_SHORT).show();


        //make a map of attributes
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put("ID", "1");
        attributes.put("Username", sharedpreferences.getString("username", ""));
        attributes.put("Vehicle", sharedpreferences.getString("vehicle", ""));

        DateFormat datef = new SimpleDateFormat("dd.MM.yyyy");
        String date = datef.format(Calendar.getInstance().getTime());

        attributes.put("Date", date);


        try {
            trackGeodatabase.addFeature(attributes, mLocation);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void updateGraphic(Point nLocation) {
       //TODO symbol sollte sich ändern;

        SimpleMarkerSymbol resultSymbolact = new SimpleMarkerSymbol(Color.RED, 16, SimpleMarkerSymbol.STYLE.DIAMOND);

        Graphic resultLocGraphic = new Graphic(nLocation, resultSymbolact);
        // add graphic to location layer
        try{
            mLocationLayer.updateGraphic(mLocationLayer.getGraphicIDs()[mLocationLayer.getNumberOfGraphics()-1],resultLocGraphic);
        } catch (NullPointerException e) {
            e.getStackTrace();
        }


    }

    /**
     * Zoom to a given Location
     *
     * @param loc - Centerpoint
     */

    public void zoomToLocation(Point loc) {

        Unit mapUnit = mMapView.getSpatialReference().getUnit();
        double zoomWidth = Unit.convertUnits(1000, Unit.create(LinearUnit.Code.KILOMETER), mapUnit);
        Envelope zoomExtent = new Envelope(loc, zoomWidth / 10, zoomWidth / 10);
        mMapView.setExtent(zoomExtent);

    }


    /**
     * Show progressbar for creating Filegeodatabase
     *
     * @param activity
     * @param message
     */
    protected static void showProgressBar(final MainActivity activity, final String message) {
        activity.runOnUiThread(new Runnable() {

            @Override
            public void run() {
                mProgressDialog.setMessage(message);
            }

        });
    }


    // methods to ensure context is available when updating the progress dialog
    public static Context getContext() {
        return mContext;
    }

    public static void setContext(Context context) {
        mContext = context;
    }



    /*private class QueryFeatureLayer extends AsyncTask<String, Void, FeatureResult> {
        @Override
        protected FeatureResult doInBackground(String... params) {

            String whereClause = "Username='" + params[0] + "'";

            // Define a new query and set parameters
            QueryParameters mParams = new QueryParameters();
            mParams.setWhere(whereClause);
            mParams.setReturnGeometry(true);

            // Define the new instance of QueryTask
            QueryTask queryTask = new QueryTask(featureLayerURL);
            FeatureResult results;

            try {
                // run the querytask
                results = queryTask.execute(mParams);
                return results;
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(FeatureResult results) {

            // Remove the result from previously run query task
            mLocationLayer.removeAll();

            // Define a new marker symbol for the result graphics
            SimpleMarkerSymbol sms = new SimpleMarkerSymbol(Color.BLUE, 10, SimpleMarkerSymbol.STYLE.CIRCLE);

            // Envelope to focus on the map extent on the results
            Envelope extent = new Envelope();

            // iterate through results
            for (Object element : results) {
                // if object is feature cast to feature
                if (element instanceof Feature) {
                    Feature feature = (Feature) element;
                    // convert feature to graphic
                    Graphic graphic = new Graphic(feature.getGeometry(), sms, feature.getAttributes());
                    // merge extent with point
                    extent.merge((Point)graphic.getGeometry());
                    // add it to the layer
                    mLocationLayer.addGraphic(graphic);
                }
            }

            // Set the map extent to the envelope containing the result graphics
            mMapView.setExtent(extent, 100);
        }
    }*/


    public void showToast(final String message) {
        // Show toast message on the main thread only; this function can be
        // called from query callbacks that run on background threads.
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }
}

