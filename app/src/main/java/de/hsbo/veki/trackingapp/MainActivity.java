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
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
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
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.esri.android.map.Callout;
import com.esri.android.map.FeatureLayer;
import com.esri.android.map.GraphicsLayer;
import com.esri.android.map.MapView;
import com.esri.android.map.ags.ArcGISFeatureLayer;
import com.esri.android.map.event.OnSingleTapListener;
import com.esri.android.map.ogc.WMSLayer;
import com.esri.core.ags.FeatureServiceInfo;
import com.esri.core.geodatabase.Geodatabase;
import com.esri.core.geodatabase.GeodatabaseFeature;
import com.esri.core.geodatabase.GeodatabaseFeatureTable;
import com.esri.core.geodatabase.GeodatabaseFeatureTableEditErrors;
import com.esri.core.geometry.Envelope;
import com.esri.core.geometry.GeometryEngine;
import com.esri.core.geometry.LinearUnit;
import com.esri.core.geometry.Point;
import com.esri.core.geometry.SpatialReference;
import com.esri.core.geometry.Unit;
import com.esri.core.map.CallbackListener;
import com.esri.core.map.Feature;
import com.esri.core.map.Graphic;
import com.esri.core.symbol.SimpleMarkerSymbol;
import com.esri.core.table.TableException;
import com.esri.core.tasks.geodatabase.GenerateGeodatabaseParameters;
import com.esri.core.tasks.geodatabase.GeodatabaseStatusCallback;
import com.esri.core.tasks.geodatabase.GeodatabaseStatusInfo;
import com.esri.core.tasks.geodatabase.GeodatabaseSyncTask;
import com.esri.core.tasks.geodatabase.SyncGeodatabaseParameters;
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
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static java.lang.String.*;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {


    // Attributes for Android usage
    private static final String TAG = "SAMPLE_ANDROID_APP";
    private static final String PREFS_NAME = "TRACKINGAPP";
    private SharedPreferences sharedpreferences;
    private static Context mContext;
    private static ProgressDialog mProgressDialog;
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


    // Attributes for the Map
    private static MapView mMapView;
    private String featureLayerURL;
    private String featureServiceURL;
    private ArcGISFeatureLayer featureLayer = null;
    private GraphicsLayer mLocationLayer;
    private FeatureLayer offlineFeatureLayer;


    // Attributes for GPS-Logging
    private GoogleApiClient mGoogleApiClient;
    private TextView mLatitudeText;
    private TextView mBewGeschw;
    private TextView mLongitudeText;
    private int GPS_INTERVAL = 1000;
    private int GPS_FASTEST_INTERVAL = 1000;
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


        // Get resource names
        demoDataFile = Environment.getExternalStorageDirectory();
        offlineDataSDCardDirName = this.getResources().getString(R.string.config_data_sdcard_offline_dir);
        filename = this.getResources().getString(R.string.config_geodatabase_name);

        // Check for Filegeodatabase
        File f = new File(createGeodatabaseFilePath());
        Log.e(getClass().getSimpleName(), "Does the file exist? " + f.exists());

        // Get FeatureURL
        featureServiceURL = this.getResources().getString(R.string.FeatureServiceURL);
        featureLayerURL = this.getResources().getString(R.string.FeatureLayerURL);

        // Create filegeodatabase if no exists
        if (f.exists() == false) {

            mProgressDialog = new ProgressDialog(MainActivity.this);
            mProgressDialog.setTitle("Create local runtime geodatabase");

            createFilegeodatabase(featureServiceURL);

        }

        // Retrieve the map and initial extent from XML layout
        mMapView = (MapView) findViewById(R.id.map);

        mLocationLayer = new GraphicsLayer();
        mMapView.addLayer(mLocationLayer);

        // attribute app and pan across dateline
        mMapView.setEsriLogoVisible(true);
        mMapView.enableWrapAround(true);


        // Add layer to map
        featureLayer = new

                ArcGISFeatureLayer(featureLayerURL, ArcGISFeatureLayer.MODE.ONDEMAND);

        mMapView.addLayer(featureLayer);

        //open the local geodatabase file
        try

        {
            geodatabase = new Geodatabase(createGeodatabaseFilePath());
            //create a feature layer and add it to the map
            geodatabaseFeatureTable = geodatabase.getGeodatabaseFeatureTableByLayerId(0);
            //create a feature layer and add it to the map
            offlineFeatureLayer = new FeatureLayer(geodatabaseFeatureTable);

            mMapView.addLayer(offlineFeatureLayer);
        } catch (
                FileNotFoundException e
                )

        {
            e.printStackTrace();
        }

    //create a feature layer and add it to the map
  //  geodatabaseFeatureTable = geodatabase.getGeodatabaseFeatureTableByLayerId(0);
    //create a feature layer and add it to the map
    //offlineFeatureLayer = new FeatureLayer(geodatabaseFeatureTable);

    //mMapView.addLayer(offlineFeatureLayer);


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

                                                                callout = mMapView.getCallout();

                                                                //Style
                                                                callout.setStyle(R.xml.tracked_point);
                                                                Feature fpktNr = offlineFeatureLayer.getFeature(selectedFeatures[0]);
                                                                String id = fpktNr.getAttributeValue("ID").toString();
                                                                String username = fpktNr.getAttributeValue("Username").toString();
                                                                String vehicle = fpktNr.getAttributeValue("Vehicle").toString();
                                                                Long date = (Long) fpktNr.getAttributeValue("Date");
                                                                Log.e("Date", date.toString());
                                                                callout.setContent(loadView(id, username, vehicle, date));
                                                                //callout.setMaxHeight(240);
                                                                //callout.setMaxWidth(600);
                                                                callout.show((Point) fpktNr.getGeometry());


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


        // Read Username from Device
        sharedpreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String username = sharedpreferences.getString("username", "");

        // TODO: Hier ggf. nicht passend! (in andere Methode)
        if (username.isEmpty()) {
            Toast.makeText(getApplicationContext(), "Bitte Benutzername eingeben!", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(getApplicationContext(), "Eingeloggt als: " + username + ".", Toast.LENGTH_LONG).show();
        }


        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Context context = getApplicationContext();
                Toast.makeText(context, "Beginne Syncronistation!", Toast.LENGTH_SHORT).show();
                try {
                    syncGeodatabase();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                mMapView.removeAll();
                mMapView.addLayer(offlineFeatureLayer);
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
     * Create Filegeodatabase
     *
     * @param featureServiceUrl
     */

    private void createFilegeodatabase(String featureServiceUrl) {
        Log.i(TAG, "Create GeoDatabase");
        // create a dialog to update user on progress
        mProgressDialog.show();
        // create the GeodatabaseTask

        gdbSyncTask = new GeodatabaseSyncTask(featureServiceUrl, null);
        gdbSyncTask.fetchFeatureServiceInfo(new CallbackListener<FeatureServiceInfo>() {

            @Override
            public void onError(Throwable arg0) {
                Log.e(TAG, "Error fetching FeatureServiceInfo");
            }

            @Override
            public void onCallback(FeatureServiceInfo fsInfo) {
                if (fsInfo.isSyncEnabled()) {
                    Log.i(TAG,"IS Sync Enabled, create Geodatabase");
                    createGeodatabase(fsInfo);
                }
            }
        });

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

        String getVehicle = sharedpreferences.getString("vehicle", "");

        switch (getVehicle) {
            case "Auto":
                carCheckbox.setChecked(true);
            case "Fußgänger":
                pedestrianCheckbox.setChecked(true);
            case "Fahrrad":
                bicycleCheckbox.setChecked(true);
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
                    item.setIcon(R.drawable.gps_on);
                    item.getIcon();
                    Toast.makeText(getApplicationContext(), "Start Tracking", Toast.LENGTH_SHORT).show();
                    return true;
                } else {
                    controlGPS();
                    item.setChecked(false);
                    item.setIcon(R.drawable.gps_off);
                    Toast.makeText(getApplicationContext(), "Stop Tracking", Toast.LENGTH_SHORT).show();
                    return true;
                }


            case R.id.carMenuItem:
                setVehicle("Auto");
                carCheckbox.setChecked(true);
                Log.e("carMenuItem", sharedpreferences.getString("vehicle", ""));
                return true;

            case R.id.pedestrianMenuItem:
                setVehicle("Fußgänger");
                pedestrianCheckbox.setChecked(true);
                Log.e("pedestMenuItem", sharedpreferences.getString("vehicle", ""));
                return true;

            case R.id.bicycleMenuItem:
                setVehicle("Fahrrad");
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
        startLocationUpdates();
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

        mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
        mLatitudeText.setText(valueOf(location.getLatitude()));
        mLongitudeText.setText(valueOf(location.getLongitude()));
        Toast.makeText(this, "Updated: " + mLastUpdateTime, Toast.LENGTH_SHORT).show();


        double locY = location.getLatitude();
        double locX = location.getLongitude();

        Point wgsPoint = new Point(locX, locY);

        mLocation = (Point) GeometryEngine.project(wgsPoint,
                SpatialReference.create(4326),
                mMapView.getSpatialReference());


        // create marker symbol to represent location
        SimpleMarkerSymbol resultSymbol = new SimpleMarkerSymbol(Color.RED, 16, SimpleMarkerSymbol.STYLE.CROSS);
        // create graphic object for resulting location
        Graphic resultLocGraphic = new Graphic(mLocation, resultSymbol);
        // add graphic to location layer
        mLocationLayer.addGraphic(resultLocGraphic);


        //make a map of attributes
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put("ID", "1");
        attributes.put("Username", sharedpreferences.getString("username", ""));
        attributes.put("Vehicle", sharedpreferences.getString("vehicle", ""));

        DateFormat df = new SimpleDateFormat("dd.MM.yy");
        String date = df.format(Calendar.getInstance().getTime());

        attributes.put("Date", date);

        // TODO: Datum ist noch falsch in der Attributtabelle gespeichert

        try {
            addFeature(attributes, mLocation);
        } catch (Exception e) {
            e.printStackTrace();
        }


        /**
         * Sync Feature direct on ArcGIS-Feature-Layer
         */
/*         Add Features direct on ArcGISFeatureLayer
        FeatureTemplate template = featureLayer.getTemplates()[0];

        Graphic newFeatureGraphic = featureLayer.createFeatureWithTemplate(template, mLocation);

        // Pass array of additions to applyEdits as first parameter.
        Graphic[] adds = {newFeatureGraphic};
        featureLayer.applyEdits(adds, null, null, new CallbackListener<FeatureEditResult[][]>() {

            public void onError(Throwable error) {
                // Implement error handling code here
            }

            public void onCallback(FeatureEditResult[][] editResult) {
                // Check the response for success or failure
                if (editResult[0] != null && editResult[0][0] != null && editResult[0][0].isSuccess()) {
                    // Implement any required success logic here
                }
            }
        });*/


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
     * Add Feature to local Filegeodatabase
     *
     * @param attr - Metainformation
     * @param p    - ArcGIS Point
     * @throws Exception
     */
    public void addFeature(Map attr, Point p) throws Exception {

        try {
            //Create a feature with these attributes at the given point
            GeodatabaseFeature gdbFeature = new GeodatabaseFeature(attr, p, geodatabaseFeatureTable);

            //Add the feature into the geodatabase table returning the new feature ID
            long fid = geodatabaseFeatureTable.addFeature(gdbFeature);

            String geomStr = geodatabaseFeatureTable.getFeature(fid).getGeometry().toString();
            Log.e(TAG, "added fid = " + fid + " " + geomStr);


        } catch (TableException e) {
            // report errors, e.g. to console
            Log.e(TAG, "", e);
        }

    }

    /**
     * Syncronice local Filegeodatabase with ArcGIS-Feature-Layer
     *
     * @throws Exception
     */
    public void syncGeodatabase() throws Exception {
        //Log.i(TAG, "Sync geodatabase from " + DEFAULT_GDB_PATH);
        SyncGeodatabaseParameters params = geodatabase.getSyncParameters();


        CallbackListener<Map<Integer, GeodatabaseFeatureTableEditErrors>> callback = new CallbackListener<Map<Integer, GeodatabaseFeatureTableEditErrors>>() {
            @Override
            public void onCallback(final Map<Integer, GeodatabaseFeatureTableEditErrors> paramT) {
                Log.i(TAG, "Sync Complete: " + (paramT == null || paramT.size() == 0 ? "Success" : "Fail"));
            }

            @Override
            public void onError(final Throwable paramThrowable) {
                Log.i(TAG, "Sync Error: ", paramThrowable);
            }
        };

        GeodatabaseStatusCallback syncStatusCallback = new GeodatabaseStatusCallback() {
            @Override
            public void statusUpdated(GeodatabaseStatusInfo status) {
                Log.i(TAG, status.getStatus().toString());

            }
        };

        gdbSyncTask = new GeodatabaseSyncTask(featureServiceURL, null);

        Log.e("PARARMS: ", params.toString());
        gdbSyncTask.syncGeodatabase(params, geodatabase, syncStatusCallback, callback);
    }


    /**
     * Create Filegeodatabase
     *
     * @param featureServerInfo
     */
    private static void createGeodatabase(FeatureServiceInfo featureServerInfo) {
        // set up the parameters to generate a geodatabase
        GenerateGeodatabaseParameters params = new GenerateGeodatabaseParameters(
                featureServerInfo, mMapView.getExtent(),
                mMapView.getSpatialReference());

        // a callback which fires when the task has completed or failed.
        CallbackListener<String> gdbResponseCallback = new CallbackListener<String>() {
            @Override
            public void onError(final Throwable e) {
                Log.e(TAG, "Error creating geodatabase");
                mProgressDialog.dismiss();
            }

            @Override
            public void onCallback(String path) {
                Log.i(TAG, "Geodatabase is: " + path);
                mProgressDialog.dismiss();
                // update map with local feature layer from geodatabase
                updateFeatureLayer(path);
                // log the path to the data on device
                Log.i(TAG, "path to geodatabase: " + path);
            }
        };

        // a callback which updates when the status of the task changes
        GeodatabaseStatusCallback statusCallback = new GeodatabaseStatusCallback() {
            @Override
            public void statusUpdated(final GeodatabaseStatusInfo status) {
                // get current status
                String progress = status.getStatus().toString();
                // get activity context
                Context context = MainActivity.getContext();
                // create activity from context
                MainActivity activity = (MainActivity) context;
                // update progress bar on main thread
                showProgressBar(activity, progress);

            }
        };

        // create the fully qualified path for geodatabase file
        localGdbFilePath = createGeodatabaseFilePath();

        // get geodatabase based on params
        submitTask(params, localGdbFilePath, statusCallback,
                gdbResponseCallback);
    }


    /**
     * Request database, poll server to get status, and download the file
     */
    private static void submitTask(GenerateGeodatabaseParameters params,
                                   String file, GeodatabaseStatusCallback statusCallback,
                                   CallbackListener<String> gdbResponseCallback) {
        // submit task
        gdbSyncTask.generateGeodatabase(params, file, false, statusCallback,
                gdbResponseCallback);
    }


    /**
     * Add feature layer from local geodatabase to map
     *
     * @param featureLayerPath
     */
    private static void updateFeatureLayer(String featureLayerPath) {
        // create a new geodatabase
        Geodatabase localGdb = null;
        try {
            localGdb = new Geodatabase(featureLayerPath);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        // Geodatabase contains GdbFeatureTables representing attribute data
        // and/or spatial data. If GdbFeatureTable has geometry add it to
        // the MapView as a Feature Layer
        if (localGdb != null) {
            for (GeodatabaseFeatureTable gdbFeatureTable : localGdb
                    .getGeodatabaseTables()) {
                if (gdbFeatureTable.hasGeometry()) {
                    mMapView.addLayer(new FeatureLayer(gdbFeatureTable));

                }
            }
        }
    }


    /**
     * Show progressbar for creating Filegeodatabase
     *
     * @param activity
     * @param message
     */
    private static void showProgressBar(final MainActivity activity, final String message) {
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


}
