package de.hsbo.veki.trackingapp;

import android.Manifest;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.nfc.Tag;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
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
import com.esri.core.tasks.query.QueryTask;
import com.google.android.gms.common.api.Result;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.io.File;
import java.io.FileNotFoundException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static java.lang.String.*;

public class MainActivity extends AppCompatActivity implements LocationListener, ResultCallback {


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
    private TextView mLatitudeText;
    private TextView mBewGeschw;
    private TextView mLongitudeText;
    private LocationRequest mLocationRequest;
    private String mLastUpdateTime;
    private Point mLocation;
    protected ActivityDetectionBroadcastReceiver mBroadcastReceiver;
    private ArrayList<DetectedActivity> mDetectedActivities;
    private GPSLocationUpdates updates;
    private boolean updated = false;


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

        mBroadcastReceiver = new ActivityDetectionBroadcastReceiver();
        mDetectedActivities = new ArrayList<DetectedActivity>();
        // Set the confidence level of each monitored activity to zero.
        for (int i = 0; i < Constants.MONITORED_ACTIVITIES.length; i++) {
            mDetectedActivities.add(new DetectedActivity(Constants.MONITORED_ACTIVITIES[i], 0));
        }


        if (updated == false) {
            updates = new GPSLocationUpdates();
            updated = true;
        }
        checkGPS();


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

    /**
     * check whether the settings allow GPS functionality
     */
    private void checkGPS() {

        LocationManager service = (LocationManager) getSystemService(LOCATION_SERVICE);
        boolean enabled = service
                .isProviderEnabled(LocationManager.GPS_PROVIDER);

        // check if enabled and if not send user to the GSP settings
        if (!enabled) {
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(intent);
        }

    }

    /**
     * request Detected Activities Updates
     */
    public void requestActivityUpdates() {
        if (!updates.mGoogleApiClient.isConnected()) {
            Toast.makeText(this, "Not connected",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(
                updates.mGoogleApiClient,
                Constants.DETECTION_INTERVAL_IN_MILLISECONDS,
                getActivityDetectionPendingIntent()
        ).setResultCallback(this);
    }

    /**
     * stop requesting the activity and remove the activity updates for the PendingIntent
     */
    public void removeActivityUpdates() {
        if (menu.getItem(0) != null && menu.getItem(0).isVisible())
            menu.getItem(0).setVisible(false);

        if (!updates.mGoogleApiClient.isConnected()) {
            Toast.makeText(this, "Not Connected", Toast.LENGTH_SHORT).show();
            return;
        }
        // Remove all activity updates for the PendingIntent that was used to request activity
        // updates.
        ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(
                updates.mGoogleApiClient,
                getActivityDetectionPendingIntent()
        ).setResultCallback(this);
    }

    private PendingIntent getActivityDetectionPendingIntent() {
        Intent intent = new Intent(this, DetectedActivitiesIntentService.class);

        // We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when calling
        // requestActivityUpdates() and removeActivityUpdates().
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }


    /*private void updateUI() {
        mLatitudeText.setText(String.valueOf(mLastLocation.getLatitude()));
        mLongitudeText.setText(String.valueOf(mLastLocation.getLongitude()));
        mBewGeschw.setText(String.valueOf(mLastLocation.getSpeed()));

    }*/
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        this.menu = menu;
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);

        carCheckbox = menu.getItem(3).getSubMenu().getItem(0);
        pedestrianCheckbox = menu.getItem(3).getSubMenu().getItem(1);
        bicycleCheckbox = menu.getItem(3).getSubMenu().getItem(2);
        autoCheckbox = menu.getItem(3).getSubMenu().getItem(3);

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

    /**
     * if the activity is started connect the GoogleApiClient
     */
    protected void onStart() {
        super.onStart();
        Log.i(TAG, "Started");
        if (updates.mGoogleApiClient != null) {
            updates.mGoogleApiClient.connect();
            Log.i(TAG, "Connected");
        }
    }

    /**
     * if the activity is stopped disconnect the GoogleApiClient
     */
    protected void onStop() {
        super.onStop();
        Log.i(TAG, "Stopped");
        if (updates.mGoogleApiClient != null) {
            if (updates.mGoogleApiClient.isConnected()) {
                updates.mGoogleApiClient.disconnect();
            }
            removeActivityUpdates();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {

            case R.id.action_username:
                Intent username_intent = new Intent(this, ChangeUsernameActivity.class);
                startActivity(username_intent);
                return true;
            case R.id.action_interval:
                if (updates.mGPSActive == true) {
                    stopLocationUpdates();
                }
                Intent interval_intent = new Intent(getApplicationContext(), ChangeGpsUpdateInterval.class);
                startActivityForResult(interval_intent,100);
                return true;

            case R.id.action_location_found:
                if (item.isChecked()) {
                    if (updates.mGoogleApiClient == null) {
                        Log.i(TAG, "GoogleApiClient == null Create ApiClient");
                        updates.changeLocationRequestInterval(10000);
                        updates.mGoogleApiClient.connect();
                        if(updates.mGoogleApiClient.isConnected())
                            controlGPS();
                    }else {
                        controlGPS();
                    }
                    if(updates.mGPSActive==true) {
                        item.setChecked(false);
                        item.setIcon(R.drawable.gps_on_highres);
                        Toast.makeText(getApplicationContext(), "Start Tracking", Toast.LENGTH_SHORT).show();
                        if(autoCheckbox.isChecked()){
                            requestActivityUpdates();
                        }
                    }
                    return true;
                } else {
                    controlGPS();
                    removeActivityUpdates();
                    item.setChecked(true);
                    item.setIcon(R.drawable.gps_off_highres);
                    Toast.makeText(getApplicationContext(), "Stop Tracking", Toast.LENGTH_SHORT).show();
                    return true;
                }


            case R.id.carMenuItem:
                setVehicle("Auto");
                removeActivityUpdates();
                carCheckbox.setChecked(true);
                Log.e("carMenuItem", sharedpreferences.getString("vehicle", ""));
                return true;

            case R.id.pedestrianMenuItem:
                setVehicle("Fußgänger");
                removeActivityUpdates();
                pedestrianCheckbox.setChecked(true);
                Log.e("pedestMenuItem", sharedpreferences.getString("vehicle", ""));
                return true;

            case R.id.bicycleMenuItem:
                setVehicle("Fahrrad");
                removeActivityUpdates();
                bicycleCheckbox.setChecked(true);
                Log.e("bicyMenuItem", sharedpreferences.getString("vehicle", ""));
                return true;

            case R.id.auto:
                setVehicle("Automatisch erkennen");
                requestActivityUpdates();
                autoCheckbox.setChecked(true);
                return true;

            default:
                return super.onOptionsItemSelected(item);


        }


    }


    /**
     * set the correct Item depending on the activity detected
     *
     * @param detectedActivities
     * @return
     */
    public boolean setIconActivity(ArrayList<DetectedActivity> detectedActivities) {

        menu.getItem(0).setVisible(true);

        int confidenceLevel = 0;
        int number = 0;
        // find the most suitable DetectedActivity to display the correct Item
        for (int i = 0; i < detectedActivities.size(); i++) {

            int confidence = detectedActivities.get(i).getConfidence();

            if (confidenceLevel < confidence) {
                confidenceLevel = confidence;
                number = i;
                Log.i(TAG, "Number " + i + " conf " + confidenceLevel);
            }

        }

        switch (detectedActivities.get(number).getType()) {

            case 1:
                menu.getItem(0).setIcon(R.drawable.bike);
                return true;

            case 0:
                menu.getItem(0).setIcon(R.drawable.car);
                return true;

            case 2:
                menu.getItem(0).setIcon(R.drawable.walking);
                return true;

            case 8:
                menu.getItem(0).setIcon(R.drawable.running);
                return true;

            case 3:
                menu.getItem(0).setIcon(R.drawable.still);
                return true;

            case 5:
                menu.getItem(0).setIcon(R.drawable.tilting);
                return true;

            case 4:
                menu.getItem(0).setIcon(R.drawable.questionmark);
                return true;

            default:
                menu.getItem(0).setIcon(R.drawable.questionmark);
                return true;


        }

    }

    /**
     * stop Location updates for GoogleApiClient
     */
    protected void stopLocationUpdates() {
        if (updates.mGoogleApiClient.isConnected()) {

            LocationServices.FusedLocationApi.removeLocationUpdates(updates.mGoogleApiClient, this);
            updates.mGPSActive = false;
        }
    }

    /**
     * start Location updates for GoogleApiClient
     */
    private void startLocationUpdates() {
        if (updates.mGoogleApiClient.isConnected()) {
            Log.i(TAG,"start Location on connect");
            if (Build.VERSION.SDK_INT >= 23 &&
                    ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            LocationServices.FusedLocationApi.requestLocationUpdates(updates.mGoogleApiClient, updates.mLocationRequest, this);
            updates.mGPSActive = true;

        }
        else{
            updates.mGPSActive=false;
        }

    }

    /**
     * defines how the location updates shall be received
     */
    public void controlGPS() {
        if (updates.useGooglePlayService) {
            if (updates.mGPSActive == true) {
                stopLocationUpdates();
            } else {
                Log.i(TAG, "StartLocationUpdates");
                startLocationUpdates();
            }
        } else {
            if (updates.mGPSActive == true) {
                updates.stopSimpleLocationUpdates();
            } else {
                updates.startSimpleLocationUpdates();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Register the broadcast receiver that informs this activity of the DetectedActivity
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver,
                new IntentFilter(Constants.BROADCAST_ACTION));
    }

    @Override
    protected void onPause() {
        // Unregister the broadcast receiver that was registered during onResume().
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
        super.onPause();
    }
    @Override
    protected void onActivityResult(int requestCode,
                                    int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(resultCode == 100){

            // Storing result in a variable called myvar
            // get("website") 'website' is the key value result data
            Log.i(TAG,"Result");
            int gpsInterval =data.getExtras().getInt("connected");
            updates.changeLocationRequestInterval(gpsInterval);
            controlGPS();

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

        updates.mLastLocation = location;

        //make a map of attributes
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put("ID", "1");
        attributes.put("Username", sharedpreferences.getString("username", ""));
        attributes.put("Vehicle", sharedpreferences.getString("vehicle", ""));

        DateFormat datef = new SimpleDateFormat("dd.MM.yyyy");
        String date = datef.format(Calendar.getInstance().getTime());
        mLastUpdateTime = date;

        attributes.put("Date", date);
        Toast.makeText(this, "Updated: " + mLastUpdateTime, Toast.LENGTH_SHORT).show();

        try {
            trackGeodatabase.addFeature(attributes, mLocation);
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

    @Override
    public void onResult(@NonNull Result result) {
        if (result.getStatus().isSuccess()) {
        } else {
            Log.e(TAG, "Error adding or removing activity detection: " + result.getStatus().getStatusMessage());
        }
    }


    private class QueryFeatureLayer extends AsyncTask<String, Void, FeatureResult> {
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
                    extent.merge((Point) graphic.getGeometry());
                    // add it to the layer
                    mLocationLayer.addGraphic(graphic);
                }
            }

            // Set the map extent to the envelope containing the result graphics
            mMapView.setExtent(extent, 100);
        }
    }


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

    /**
     * BroadCast Receiver for the IntentService to receive the DetectedActivity
     */
    public class ActivityDetectionBroadcastReceiver extends BroadcastReceiver {
        protected static final String TAG = "activity-det-resp-rec";

        @Override
        public void onReceive(Context context, Intent intent) {
            ArrayList<DetectedActivity> updatedActivities =
                    intent.getParcelableArrayListExtra(Constants.ACTIVITY_EXTRA);
            setIconActivity(updatedActivities);

        }
    }
}
