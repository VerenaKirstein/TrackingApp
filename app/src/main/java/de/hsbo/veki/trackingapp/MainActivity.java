package de.hsbo.veki.trackingapp;

import android.app.ActivityManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
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
import com.esri.android.map.MapView;
import com.esri.android.map.event.OnSingleTapListener;
import com.esri.core.geodatabase.GeodatabaseFeatureServiceTable;
import com.esri.core.geometry.GeometryEngine;
import com.esri.core.geometry.Point;
import com.esri.core.geometry.SpatialReference;
import com.esri.core.map.CallbackListener;
import com.esri.core.map.Feature;
import com.esri.core.map.Graphic;
import com.esri.core.symbol.SimpleMarkerSymbol;
import com.esri.core.table.TableException;
import com.esri.core.tasks.geodatabase.GeodatabaseSyncTask;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.LocationRequest;

import java.io.File;
import java.io.FileNotFoundException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import static java.lang.String.valueOf;

public class MainActivity extends AppCompatActivity {

    // Attributes for Application
    public static String TAG = "MainActivity";
    public static ProgressDialog progressDialog;
    public static GoogleApiClient client;
    public static LocationRequest mLocationRequest;
    protected static String featureLayerURL;
    protected static GraphicsLayer graphicsLayer;
    private static Context context;
    // Attributes for LocalFilegeodatabase
    private GeodatabaseSyncTask gdbSyncTask;
    private File demoDataFile;
    private String offlineDataSDCardDirName;
    private String filename;
    private String OFFLINE_FILE_EXTENSION;
    private Callout callout;
    private LocalGeodatabase localGeodatabase;
    private GeodatabaseFeatureServiceTable featureServiceTable;
    private FeatureLayer fLayer;
    private String user_id = null;
    private DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.MEDIUM);
    private String newLocationTime;
    // Attributes for the Map
    private MapView mapView;
    private String featureServiceURL;
    // Attributes for UserInterface
    private TextView latitudeText;
    private TextView longitudeText;
    private TextView bewGeschw;
    private Button syncButton;
    private Menu menu;
    private MenuItem carCheckbox = null;
    private MenuItem pedestrianCheckbox = null;
    private MenuItem bicycleCheckbox = null;
    private MenuItem autoCheckbox = null;
    private Integer gpsInterval = 15000;
    private UserCredentials userCredentials;
    // Attributes for BackgroundService
    private Intent intent;
    private GPSBroadcastReceiver gpsBroadcastReceiver;
    private BackgroundLocationService mService;
    private Boolean mBound = false;
    /**
     * Initialize server connection
     */
    protected ServiceConnection mServerConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {

            BackgroundLocationService.LocalBinder binder = (BackgroundLocationService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
        }


        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d("TAG", "onServiceDisconnected");
        }
    };
    private Point newLocation;
    private Point oldLocation = null;
    private ActivityDetectionBroadcastReceiver mBroadcastReceiver;
    private ArrayList<DetectedActivity> mDetectedActivities;

    public static Context getContext() {
        return context;
    }

    public static void setContext(Context mainContext) {
        MainActivity.context = mainContext;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        MainActivity.setContext(this);
        progressDialog = new ProgressDialog(MainActivity.this);

        // Get resource names
        demoDataFile = Environment.getExternalStorageDirectory();
        offlineDataSDCardDirName = this.getResources().getString(R.string.config_data_sdcard_offline_dir);
        filename = this.getResources().getString(R.string.config_geodatabase_name);
        OFFLINE_FILE_EXTENSION = this.getResources().getString(R.string.OFFLINE_FILE_EXTENSION);

        // Load UserInterface-Elements
        latitudeText = (TextView) findViewById(R.id.GPSLatText);
        longitudeText = (TextView) findViewById(R.id.GPSLonText);
        bewGeschw = (TextView) findViewById(R.id.BwGeschw);
        syncButton = (Button) findViewById(R.id.syncButton);

        // Read user credentials from device
        userCredentials = new UserCredentials(getContext());

        // No User? Show alert to get User information
        if (userCredentials.getUserid().equals("null")) {
            alertMessageNoUser();
        }

        Log.i("userCredentials", userCredentials.getProfession());

        // Get FeatureURL
        featureServiceURL = this.getResources().getString(R.string.FeatureServiceURL);
        featureLayerURL = this.getResources().getString(R.string.FeatureLayerURL);

        // Initialize Map
        mapView = (MapView) findViewById(R.id.map);
        graphicsLayer = new GraphicsLayer();
        mapView.addLayer(graphicsLayer);

        // Get NetworkInfo
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();


        try {

            if (networkInfo != null && networkInfo.isConnected()) {
                Log.i(TAG, " set local Geodatabase");
                localGeodatabase = new LocalGeodatabase(createGeodatabaseFilePath(), featureServiceURL, getMainActivity(), mapView);

                // Get featureServiceTable from feature service
                featureServiceTable = new GeodatabaseFeatureServiceTable(featureServiceURL, 0);

                // initialize the table asynchronously
                featureServiceTable.initialize(
                        new CallbackListener<GeodatabaseFeatureServiceTable.Status>() {

                            @Override
                            public void onError(Throwable e) {
                                // report/handle error as desired
                                Log.i(TAG, featureServiceTable.getInitializationError());
                            }

                            @Override
                            public void onCallback(GeodatabaseFeatureServiceTable.Status status) {

                                // if featureServiceTable is initialized
                                if (status == GeodatabaseFeatureServiceTable.Status.INITIALIZED) {

                                    // add featurelayer to map
                                    fLayer = new FeatureLayer(featureServiceTable);
                                    mapView.addLayer(fLayer);

                                    // add single tab listener
                                    mapView.setOnSingleTapListener(new OnSingleTapListener() {

                                        @Override
                                        public void onSingleTap(float x, float y) {

                                            // if user_id is set
                                            if (user_id.equals("null")) {

                                                long[] selectedFeatures = fLayer.getFeatureIDs(x, y, 25, 1);

                                                if (selectedFeatures.length > 0) {

                                                    // Feature is selected
                                                    fLayer.selectFeatures(selectedFeatures, false);

                                                    // Get Feature attributes
                                                    Feature feature = fLayer.getFeature(selectedFeatures[0]);
                                                    String featureUser_ID = feature.getAttributeValue("UserID").toString();
                                                    String featureUsername = feature.getAttributeValue("Username").toString();
                                                    String featureVehicle = feature.getAttributeValue("Vehicle").toString();
                                                    String featureTime = feature.getAttributeValue("Time").toString();

                                                    Log.e("Feature selected", "" + feature.getAttributes());

                                                    // Show information if user is owner of the point
                                                    if (user_id.equals(featureUser_ID)) {
                                                        callout = mapView.getCallout();
                                                        callout.setStyle(R.xml.tracked_point);
                                                        callout.setContent(loadView(featureUser_ID, featureUsername, featureVehicle, featureTime));
                                                        callout.show((Point) feature.getGeometry());
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

                                                fLayer.clearSelection();
                                            }
                                        }

                                    });
                                }
                            }
                        });


                // Create a personal FeatureLayer and add to map
                user_id = userCredentials.getUserid();

                if (user_id.equals("null")) {
                    // Load FeatureLayer for user_id
                    new QueryFeatureLayer().execute(user_id);
                }


            } else {

                Toast.makeText(getApplicationContext(), "Bitte mit dem Internet verbinden!", Toast.LENGTH_SHORT).show();
            }


        } catch (FileNotFoundException e) {
            Log.e("File", "not found");
            e.printStackTrace();
        }


        // Initialize background service
        intent = new Intent(this, BackgroundLocationService.class);
        //IntentFilter intentFilter = new IntentFilter("android.intent.action.MAIN");

        gpsBroadcastReceiver = new GPSBroadcastReceiver();
        mBroadcastReceiver = new ActivityDetectionBroadcastReceiver();
        mDetectedActivities = new ArrayList<DetectedActivity>();

        // Set the confidence level of each monitored activity to zero.
        for (int i = 0; i < Constants.MONITORED_ACTIVITIES.length; i++) {

            Log.i(TAG, "Set confidence level");
            mDetectedActivities.add(new DetectedActivity(Constants.MONITORED_ACTIVITIES[i], 0));
        }

        // if service is running in background -> bind to activity
        if (isMyServiceRunning(BackgroundLocationService.class)) {

            bindService(intent, mServerConn, Context.BIND_AUTO_CREATE);
            LocalBroadcastManager.getInstance(this).registerReceiver(gpsBroadcastReceiver, new IntentFilter(BackgroundLocationService.BROADCAST_ACTION));

        }

        // start sync
        syncButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Toast.makeText(context, "Beginne Syncronistation!", Toast.LENGTH_SHORT).show();
                try {

                    localGeodatabase.syncGeodatabase(user_id);

                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        });


    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        this.menu = menu;
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);

        autoCheckbox = menu.getItem(3).getSubMenu().getItem(0);
        pedestrianCheckbox = menu.getItem(3).getSubMenu().getItem(1);
        bicycleCheckbox = menu.getItem(3).getSubMenu().getItem(2);
        carCheckbox = menu.getItem(3).getSubMenu().getItem(3);

        // check background service for chosen image
        if (isMyServiceRunning(BackgroundLocationService.class)) {
            menu.getItem(1).setChecked(false).setIcon(R.drawable.gps_on_highres);
            menu.getItem(0).setVisible(true);
        }

        String getVehicle = userCredentials.getVehicle();

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
                autoCheckbox.setChecked(true);
                userCredentials.setVehicle("Unkown");
        }

        return true;

    }

    /**
     * Method to unbind service before app is destroy
     */
    @Override
    protected void onDestroy() {

        unbindService(mServerConn);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(gpsBroadcastReceiver);
        super.onDestroy();
    }

    /**
     * Method to handle Action Overflow interaction
     *
     * @param item - selected option
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {

            // Change username
            case R.id.action_username:

                Intent interval_intent_username = new Intent(getApplicationContext(), ChangeUsernameActivity.class);
                startActivityForResult(interval_intent_username, 200);
                return true;

            // Change intervall
            case R.id.action_interval:

                if (mBound) {
                    stopService(intent);
                    unbindService(mServerConn);
                    LocalBroadcastManager.getInstance(this).unregisterReceiver(gpsBroadcastReceiver);
                    mBound = false;
                }

                Intent interval_intent = new Intent(getApplicationContext(), ChangeGpsUpdateInterval.class);
                startActivityForResult(interval_intent, 100);
                return true;

            // Start or stop tracking
            case R.id.action_location_found:

                user_id = userCredentials.getUserid();

                final LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

                if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {


                    if (user_id.equals("null")) {

                        alertMessageNoUser();

                    } else {

                        if (item.isChecked()) {

                            LocalBroadcastManager.getInstance(this).registerReceiver(gpsBroadcastReceiver, new IntentFilter(BackgroundLocationService.BROADCAST_ACTION));
                            item.setChecked(false);
                            bindService(intent, mServerConn, Context.BIND_AUTO_CREATE);
                            menu.getItem(0).setVisible(true);
                            startService(intent);
                            Log.i("start", "" + isMyServiceRunning(BackgroundLocationService.class));
                            item.setIcon(R.drawable.gps_on_highres);
                            return true;

                        } else {

                            if (mBound) {

                                stopService(intent);
                                unbindService(mServerConn);
                                LocalBroadcastManager.getInstance(context).unregisterReceiver(gpsBroadcastReceiver);
                                Log.i("stop", "" + isMyServiceRunning(BackgroundLocationService.class));
                                menu.getItem(0).setVisible(false);
                                item.setChecked(true);
                                item.setIcon(R.drawable.gps_off_highres);

                                mBound = false;
                            }
                            return true;
                        }
                    }
                } else {

                    alertMessageNoGps();
                }

                // Vehicle car selected
            case R.id.carMenuItem:
                userCredentials.setVehicle("Car");
                menu.getItem(0).setIcon(R.drawable.car);
                carCheckbox.setChecked(true);
                return true;

            // Vehicle pedestrian selected
            case R.id.pedestrianMenuItem:
                userCredentials.setVehicle("Walking");
                menu.getItem(0).setIcon(R.drawable.walking);
                pedestrianCheckbox.setChecked(true);
                return true;

            // Vehicle bicycle selected
            case R.id.bicycleMenuItem:
                userCredentials.setVehicle("Bike");
                menu.getItem(0).setIcon(R.drawable.bike);
                bicycleCheckbox.setChecked(true);
                return true;

            // Vehicle "automatic" selected
            case R.id.auto:
                userCredentials.setVehicle(mDetectedActivities.get(0).toString());
                autoCheckbox.setChecked(true);
                return true;

            default:
                return super.onOptionsItemSelected(item);

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

    /**
     * Method to create new point
     *
     * @param lon - longitude value
     * @param lat - latitude value
     */
    public void createPoint(double lon, double lat) {

        // Save values as point
        Point wgsPoint = new Point(lon, lat);

        if (newLocation != null) {
            oldLocation = newLocation;
        }

        // Project from WGS84 to map reference system
        newLocation = (Point) GeometryEngine.project(wgsPoint,
                SpatialReference.create(4326),
                mapView.getSpatialReference());

        Log.i("newLoc", "" + newLocation.toString());

        // Calculate and set speed
        if (oldLocation != null) {
            double distance = Math.sqrt(Math.pow(oldLocation.getX() - newLocation.getX(), 2) + Math.pow(oldLocation.getY() - newLocation.getY(), 2));
            double geschw = distance / (3.6);
            geschw = Math.round(geschw * 100) / 100.0;
            String bewGeschwText = roundValue(geschw, 1) + " m/s";
            bewGeschw.setText(bewGeschwText);
        } else {
            bewGeschw.setText(valueOf(0.0 + " m/s"));
        }

        // Round and set coordinates
        latitudeText.setText(roundValue(lat, 7));
        longitudeText.setText(roundValue(lon, 6));

        // update graphic and add point to local Filegeodatabase
        if (newLocation != null) {
            updateGraphic(newLocation);
            Log.e("newLoc", newLocation.toString());
            addFeatureToLocalgeodatabase(newLocation);
        }


    }

    /**
     * Method to round double values
     *
     * @param value - number to round
     * @param i     - decimal point number
     * @return - rounded value
     */
    public String roundValue(double value, int i) {
        return String.valueOf(new BigDecimal(value).round(new MathContext(i)));
    }

    /**
     * Method to prepare attributes of points to add into Filegeodatabase
     * @param newLocation - tracked point
     */
    private void addFeatureToLocalgeodatabase(Point newLocation) {

        newLocationTime = dateFormat.format(Calendar.getInstance().getTime());

        // make a map of attributes
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("UserID", userCredentials.getUserid());
        attributes.put("Username", userCredentials.getUsername());
        attributes.put("Sex", userCredentials.getSex());
        attributes.put("Age", userCredentials.getAge());
        attributes.put("Profession", userCredentials.getProfession());
        attributes.put("Vehicle", userCredentials.getVehicle());
        attributes.put("Time", newLocationTime);
        attributes.put("Speed", bewGeschw.getText().toString());

        Log.i("attributes", attributes.toString());
        Log.i("loc", newLocation.toString());

        try {
            localGeodatabase.addFeature(attributes, newLocation);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * Method to log all features in Filegeodatabase
     */
    private void logFeaturesInDatabase() {
        // Log all Features in Database
        try {
            long size = localGeodatabase.getGeodatabaseFeatureTable().getNumberOfFeatures();

            Log.e("Number Features", "" + size);

            for (long k = 0; k <= size; k++) {
                try {
                    Log.i("Feature", "" + localGeodatabase.getGeodatabaseFeatureTable().getFeature(k));
                } catch (TableException e) {
                    e.printStackTrace();
                }

            }
        } catch (NullPointerException e) {
            e.getStackTrace();
        }
    }

    /**
     * Method to add point to graphics layer
     * @param newLocation - tracked point
     */
    private void updateGraphic(Point newLocation) {

        SimpleMarkerSymbol resultSymbolact = new SimpleMarkerSymbol(Color.RED, 16, SimpleMarkerSymbol.STYLE.CROSS);
        Graphic graphic = new Graphic(newLocation, resultSymbolact);
        graphicsLayer.addGraphic(graphic);

    }

    /**
     * Method to identify running services
     * @param serviceClass - service class to check
     */
    private boolean isMyServiceRunning(Class<?> serviceClass) {

        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                Log.i(TAG, service.service.getClassName());
                return true;

            }
        }
        return false;
    }

    /*
    * Create the geodatabase file location and name structure
    */
    private String createGeodatabaseFilePath() {
        StringBuilder sb = new StringBuilder();
        sb.append(demoDataFile.getAbsolutePath());
        sb.append(File.separator);
        sb.append(offlineDataSDCardDirName);
        sb.append(File.separator);
        sb.append(filename);
        sb.append(OFFLINE_FILE_EXTENSION);

        return sb.toString();
    }

    /**
     * Method to handle ActivityResults
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);


        switch (resultCode) {

            // set intervall
            case 100:
                Log.i(TAG, "Result 100");
                gpsInterval = data.getExtras().getInt("connected");
                intent.putExtra("Update_Interval", gpsInterval);

                if (!menu.getItem(1).isChecked()) {
                    LocalBroadcastManager.getInstance(this).registerReceiver(gpsBroadcastReceiver, new IntentFilter(BackgroundLocationService.BROADCAST_ACTION));
                    bindService(intent, mServerConn, Context.BIND_AUTO_CREATE);
                    startService(intent);
                }

                // set user credentials
            case 200:

                if (data != null) {
                    Log.i(TAG, "Result 200");
                    userCredentials.setUsername(data.getExtras().getString("Username"));
                    userCredentials.setUserid(data.getExtras().getString("UserID"));
                    userCredentials.setAge(data.getExtras().getString("Age"));
                    userCredentials.setProfession(data.getExtras().getString("Profession"));
                    userCredentials.setSex(data.getExtras().getString("Sex"));

                    Toast.makeText(getApplicationContext(), "Benutzerdaten geändert. Username: " +
                            userCredentials.getUsername() + "!", Toast.LENGTH_LONG).show();

                    Log.e("resultcode 200: ", userCredentials.toString());
                } else {
                    Toast.makeText(getApplicationContext(),
                            "Benutzerdaten ändern abgebrochen!", Toast.LENGTH_LONG).show();
                }


        }

    }

    /**
     * Method to show toasts
     * @param message - text to show
     */
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
     * Method to adjust onSingleTab result
     */
    private View loadView(String id, String username, String vehicle, String time) {
        View view = LayoutInflater.from(MainActivity.this).inflate(R.layout.pointinfo, null);
        final TextView textNummer = (TextView) view.findViewById(R.id.popup);
        String out = "Username: " + username + "\nVehicle: " + vehicle + "\nTime: " + time;
        textNummer.setText(out);

        return view;
    }

    /**
     * set the correct Item depending on the activity detected
     */
    public boolean setIconActivity(ArrayList<DetectedActivity> detectedActivities) {

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
                userCredentials.setVehicle("Bike");
                return true;

            case 0:
                menu.getItem(0).setIcon(R.drawable.car);
                userCredentials.setVehicle("Car");
                return true;

            case 2:
                menu.getItem(0).setIcon(R.drawable.walking);
                userCredentials.setVehicle("Walking");
                return true;

            case 8:
                menu.getItem(0).setIcon(R.drawable.running);
                userCredentials.setVehicle("Running");
                return true;

            case 3:
                menu.getItem(0).setIcon(R.drawable.still);
                userCredentials.setVehicle("Still");
                return true;

            case 5:
                menu.getItem(0).setIcon(R.drawable.tilting);
                userCredentials.setVehicle("Unkown");
                return true;

            case 4:
                menu.getItem(0).setIcon(R.drawable.questionmark);
                userCredentials.setVehicle("Unkown");
                return true;

            default:
                menu.getItem(0).setIcon(R.drawable.questionmark);
                userCredentials.setVehicle("Unkown");
                return true;


        }

    }

    /**
     * Method to invite user to turn gps on
     */
    private void alertMessageNoGps() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Dein GPS ist ausgeschaltet, möchtest du es einschalten?")
                .setCancelable(false)
                .setPositiveButton("Ja", new DialogInterface.OnClickListener() {
                    public void onClick(@SuppressWarnings("unused") final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                        startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    }
                })
                .setNegativeButton("Nein", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                        dialog.cancel();
                    }
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }

    /**
     * Method to invite user to set user credentials
     */
    private void alertMessageNoUser() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Es wurden noch keine Benutzerdaten eingegeben, möchtest du das jetzt erledigen?")
                .setCancelable(false)
                .setPositiveButton("Ja", new DialogInterface.OnClickListener() {
                    public void onClick(@SuppressWarnings("unused") final DialogInterface dialog, @SuppressWarnings("unused") final int id) {

                        Intent interval_intent = new Intent(getApplicationContext(), ChangeUsernameActivity.class);
                        startActivityForResult(interval_intent, 200);

                    }
                })
                .setNegativeButton("Nein", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                        dialog.cancel();
                    }
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }

    /**
     * Method to initialize new QueryFeatureLayer
     */
    public void updateQueryFeatureLayer() {
        String user_id = userCredentials.getUserid();
        new QueryFeatureLayer().execute(user_id);
    }

    /**
     * Getter Method for MainActivity
     */
    public MainActivity getMainActivity() {
        return this;
    }

    /**
     * Inner Class to initialize GPSBroadcastReceiver
     */
    public class GPSBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

            // Data you need to pass to activity
            Log.i(TAG, "" + intent.getExtras().getDouble(BackgroundLocationService.EXTENDED_DATA_STATUS));
            double lat = intent.getExtras().getDouble("Lat");
            double lon = intent.getExtras().getDouble("Lon");

            Log.e("onReceveive", "" + lat + " " + lon);
            createPoint(lon, lat);
        }
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
            Log.i(TAG, "Activity Received");
            setIconActivity(updatedActivities);

        }
    }
}


