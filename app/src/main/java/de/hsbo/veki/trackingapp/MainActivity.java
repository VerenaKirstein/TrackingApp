package de.hsbo.veki.trackingapp;

import android.app.ActivityManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.support.annotation.NonNull;
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
import com.esri.android.map.MapView;
import com.esri.android.map.ags.ArcGISFeatureLayer;
import com.esri.android.map.event.OnSingleTapListener;
import com.esri.core.geodatabase.Geodatabase;
import com.esri.core.geodatabase.GeodatabaseFeatureServiceTable;
import com.esri.core.geodatabase.GeodatabaseFeatureTable;
import com.esri.core.geometry.GeometryEngine;
import com.esri.core.geometry.Point;
import com.esri.core.geometry.SpatialReference;
import com.esri.core.map.Feature;
import com.esri.core.map.Graphic;
import com.esri.core.symbol.SimpleMarkerSymbol;
import com.esri.core.table.TableException;
import com.esri.core.tasks.geodatabase.GeodatabaseSyncTask;
import com.esri.core.tasks.query.QueryParameters;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Result;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.location.ActivityRecognition;
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
import java.util.Hashtable;
import java.util.Map;

import static java.lang.String.valueOf;

public class MainActivity extends AppCompatActivity implements ResultCallback {

    // Attributes for Application
    public static String TAG = "MainActivity";
    private SharedPreferences sharedpreferences;
    public static ProgressDialog progressDialog;
    private Toast toast;
    private static Context context;


    // Attributes for LocalFilegeodatabase
    private GeodatabaseSyncTask gdbSyncTask;
    private File demoDataFile;
    private String offlineDataSDCardDirName;
    private String filename;
    private String localGdbFilePath;
    private String OFFLINE_FILE_EXTENSION = ".geodatabase";
    private static Geodatabase geodatabase = null;
    private GeodatabaseFeatureTable geodatabaseFeatureTable;
    private GeodatabaseFeatureServiceTable featureServiceTable;
    private QueryParameters queryParameters;
    private Callout callout;
    private LocalGeodatabase localGeodatabase;
    private String username;
    private String age;
    private String sex;
    private String profession;
    private String user_id;


    // Attributes for the Map
    public static MapView mapView;
    static String featureLayerURL;
    public static String featureServiceURL;
    private FeatureLayer featureLayer;
    private ArcGISFeatureLayer arcGISFeatureLayer;
    static GraphicsLayer graphicsLayer;
    private FeatureLayer offlineFeatureLayer;
    private QueryFeatureLayer queryFeatureLayer;


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
    public static Integer gpsInterval = 15000;


    // Attributes for BackgroundService
    private Intent intent;
    private GPSBroadcastReceiver gpsBroadcastReceiver;
    private BackgroundLocationService mService;
    public static GoogleApiClient client;
    public static LocationRequest mLocationRequest;
    private Boolean mBound = false;
    private Point newLocation;
    private Point oldLocation = null;

    Button button_start;
    Button button_stop;

    TextView lon;
    TextView lat;



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

        // Load UserInterface-Elements
        latitudeText = (TextView) findViewById(R.id.GPSLatText);
        longitudeText = (TextView) findViewById(R.id.GPSLonText);
        bewGeschw = (TextView) findViewById(R.id.BwGeschw);
        syncButton = (Button) findViewById(R.id.syncButton);

        //button_stop = (Button) findViewById(R.id.button_stop);
        //button_start = (Button) findViewById(R.id.button_start);

        // Read Username from Device
        HashMap<String, String> userCredentials = getSharedpreferences(Constants.PREFS_NAME);

        if (userCredentials.isEmpty()) {
            Toast.makeText(getApplicationContext(), "Bitte Benutzerdaten eingeben!", Toast.LENGTH_LONG).show();
        } else {
            username = userCredentials.get("username");
            Toast.makeText(getApplicationContext(), "Eingeloggt als: " + username + ".", Toast.LENGTH_LONG).show();
        }

        // Get FeatureURL
        featureServiceURL = this.getResources().getString(R.string.FeatureServiceURL);
        featureLayerURL = this.getResources().getString(R.string.FeatureLayerURL);

        // Initialize Map
        mapView = (MapView) findViewById(R.id.map);


        // Get NetworkInfo
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();


        graphicsLayer = new GraphicsLayer();
        mapView.addLayer(graphicsLayer);


        try {

            if (networkInfo != null && networkInfo.isConnected()) {

                localGeodatabase = new LocalGeodatabase(createGeodatabaseFilePath(), featureServiceURL, getMainActivity(), mapView);
                offlineFeatureLayer = localGeodatabase.getOfflineFeatureLayer();

                mapView.addLayer(offlineFeatureLayer);
                mapView.getLayer(mapView.getLayers().length - 1).setVisible(false);

                Log.e("DB", offlineFeatureLayer.getFeatureTable().toString());
                Log.e("DB", offlineFeatureLayer.getFeatureTable().getFields().toString());


                Log.e("UserCred", userCredentials.toString());
                // Create a personal FeatureLayer and add to map
                user_id = userCredentials.get("user_id");


                new QueryFeatureLayer().execute(user_id);

            } else {

                Toast.makeText(getApplicationContext(), "Bitte mit dem Internet verbinden!", Toast.LENGTH_SHORT).show();
            }


        } catch (FileNotFoundException e) {
            Log.e("File", "not found");
            e.printStackTrace();
        }


        mapView.setOnSingleTapListener(new OnSingleTapListener() {
            @Override
            public void onSingleTap(float x, float y) {

                long[] selectedFeatures = offlineFeatureLayer.getFeatureIDs(x, y, 25, 1);

                if (selectedFeatures.length > 0) {

                    // Feature is selected
                    offlineFeatureLayer.selectFeatures(selectedFeatures, false);


                    Feature feature = offlineFeatureLayer.getFeature(selectedFeatures[0]);
                    String featureUser_ID = feature.getAttributeValue("UserID").toString();
                    String featureUsername = feature.getAttributeValue("Username").toString();
                    String featureVehicle = feature.getAttributeValue("Vehicle").toString();
                    String featureTime = feature.getAttributeValue("Time").toString();

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

                offlineFeatureLayer.clearSelection();
            }

        });


        intent = new Intent(this, BackgroundLocationService.class);
        IntentFilter intentFilter = new IntentFilter("android.intent.action.MAIN");

        gpsBroadcastReceiver = new GPSBroadcastReceiver();
        //  LocalBroadcastManager.getInstance(this).registerReceiver(gpsBroadcastReceiver, new IntentFilter(BackgroundLocationService.BROADCAST_ACTION));

        if (isMyServiceRunning(BackgroundLocationService.class)) {
            bindService(intent, mServerConn, Context.BIND_AUTO_CREATE);
            LocalBroadcastManager.getInstance(this).registerReceiver(gpsBroadcastReceiver, new IntentFilter(BackgroundLocationService.BROADCAST_ACTION));


            syncButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {

                    Context context = getApplicationContext();
                    Toast.makeText(context, "Beginne Syncronistation!", Toast.LENGTH_SHORT).show();
                    try {
                        localGeodatabase.syncGeodatabase(user_id);

                        // Remove and add offlineLayer
                        //graphicsLayer.removeAll();
                        //new QueryFeatureLayer().execute(user_id);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }


                }
            });

        }

    }
    public static void setContext(Context mainContext) {
        MainActivity.context = mainContext;
    }

    public static Context getContext() {
        return context;
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        this.menu = menu;
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);

        pedestrianCheckbox = menu.getItem(3).getSubMenu().getItem(1);
        bicycleCheckbox = menu.getItem(3).getSubMenu().getItem(2);
        autoCheckbox = menu.getItem(3).getSubMenu().getItem(3);
        carCheckbox = menu.getItem(3).getSubMenu().getItem(0);

        if (isMyServiceRunning(BackgroundLocationService.class)) {
            menu.getItem(1).setChecked(false).setIcon(R.drawable.gps_on_highres);
        }

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

        getVehicle = getSharedpreferences(Constants.PREFS_NAME).get("vehicle");
        Toast.makeText(getApplicationContext(), "oncreate: " + getVehicle + " gesetzt", Toast.LENGTH_LONG).show();
        return true;

    }

    public void setVehicle(String vehicle) {

        SharedPreferences.Editor editor = sharedpreferences.edit();
        editor.putString("vehicle", vehicle);
        editor.apply();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServerConn);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {

            case R.id.action_username:
                Intent username_intent = new Intent(this, ChangeUsernameActivity.class);
                startActivity(username_intent);
                return true;
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

            case R.id.action_location_found:
                if (item.isChecked()) {
                    LocalBroadcastManager.getInstance(this).registerReceiver(gpsBroadcastReceiver, new IntentFilter(BackgroundLocationService.BROADCAST_ACTION));
                    item.setChecked(false);
                    bindService(intent, mServerConn, Context.BIND_AUTO_CREATE);
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
                        item.setChecked(true);
                        item.setIcon(R.drawable.gps_off_highres);

                        mBound = false;
                    }
                    return true;
                }


//                    if(updates.mGPSActive==true) {
//                        item.setChecked(false);
//                        item.setIcon(R.drawable.gps_on_highres);
//                        Toast.makeText(getApplicationContext(), "Start Tracking", Toast.LENGTH_SHORT).show();
//                        if(autoCheckbox.isChecked()){
//                            requestActivityUpdates();
//                        }
//                    }
//                    return true;
//                } else {
//                    controlGPS();
//                    removeActivityUpdates();
//                    item.setChecked(true);
//                    item.setIcon(R.drawable.gps_off_highres);
//                    Toast.makeText(getApplicationContext(), "Stop Tracking", Toast.LENGTH_SHORT).show();
//                    return true;
//                }


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

    @Override
    public void onResult(@NonNull Result result) {
        if (result.getStatus().isSuccess()) {
        } else {
            Log.e(TAG, "Error adding or removing activity detection: " + result.getStatus().getStatusMessage());
        }
    }

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



    public void createPoint(double lon, double lat) {

        Point wgsPoint = new Point(lon, lat);

        if (newLocation != null) {
            oldLocation = newLocation;
        }

        newLocation = (Point) GeometryEngine.project(wgsPoint,
                SpatialReference.create(4326),
                mapView.getSpatialReference());

        Log.e("newLoc", "" + newLocation.toString());

        if (oldLocation != null) {
            double distance = Math.sqrt(Math.pow(oldLocation.getX() - newLocation.getX(), 2) + Math.pow(oldLocation.getY() - newLocation.getY(), 2));
            double geschw = distance / (60);
            geschw = Math.round(geschw * 100) / 100.0;
            bewGeschw.setText(roundValue(geschw, 2) + " m/s");
        } else {
            bewGeschw.setText(valueOf(0.0 + " m/s"));
        }

        latitudeText.setText(roundValue(lat, 7));
        longitudeText.setText(roundValue(lon, 6));

        if (newLocation != null) {
            updateGraphic(newLocation);
        }

        Log.e("newLoc", newLocation.toString());

        addFeatureToLocalgeodatabase(newLocation);


    }

    public String roundValue(double value, int i) {
        return String.valueOf(new BigDecimal(value).round(new MathContext(i)));
    }


    private void addFeatureToLocalgeodatabase(Point newLocation) {

        HashMap<String, String> userCredentials = getSharedpreferences(Constants.PREFS_NAME);

        Log.e("userCred", userCredentials.toString());

        DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.MEDIUM);
        String newLocationTime = dateFormat.format(Calendar.getInstance().getTime());

        //make a map of attributes
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("UserID", userCredentials.get("user_id"));
        attributes.put("Username", sharedpreferences.getString("username", ""));
        attributes.put("Sex", sharedpreferences.getString("sex", ""));
        attributes.put("Age", sharedpreferences.getString("age", ""));
        attributes.put("Profession", sharedpreferences.getString("profession", ""));
        attributes.put("Vehicle", sharedpreferences.getString("vehicle", ""));
        attributes.put("Time", newLocationTime);

        Log.e("attributes", attributes.toString());

        try {
            localGeodatabase.addFeature(attributes, newLocation);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            long size = localGeodatabase.getGeodatabaseFeatureTable().getNumberOfFeatures();

            Log.e("Number Features", "" + size);

            for (long k = 0; k <= size; k++) {
                try {
                    Log.e("Feature", "" + localGeodatabase.getGeodatabaseFeatureTable().getFeature(k));
                } catch (TableException e) {
                    e.printStackTrace();
                }

            }
        } catch (NullPointerException e) {
            e.getStackTrace();
        }


    }

    private void updateGraphic(Point newLocation) {

        SimpleMarkerSymbol resultSymbolact = new SimpleMarkerSymbol(Color.RED, 16, SimpleMarkerSymbol.STYLE.CROSS);
        Graphic graphic = new Graphic(newLocation, resultSymbolact);
        graphicsLayer.addGraphic(graphic);


    }


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


    public HashMap<String, String> getSharedpreferences(String prefs_name) {

        sharedpreferences = getSharedPreferences(prefs_name, Context.MODE_PRIVATE);

        Hashtable<String, String> userCredentialsTable = new Hashtable<>();
        HashMap<String, String> userCredentials = new HashMap<>(userCredentialsTable);
        userCredentials.put("username", sharedpreferences.getString("username", ""));
        userCredentials.put("age", sharedpreferences.getString("age", ""));
        userCredentials.put("sex", sharedpreferences.getString("sex", ""));
        userCredentials.put("profession", sharedpreferences.getString("profession", ""));
        userCredentials.put("user_id", sharedpreferences.getString("user_id", ""));
        userCredentials.put("vehicle", sharedpreferences.getString("vehicle", ""));

        return userCredentials;
    }


    /*
    * Create the geodatabase file location and name structure
    */
    public String createGeodatabaseFilePath() {
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

    @Override
    protected void onActivityResult(int requestCode,
                                    int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == 100) {

            // Storing result in a variable called myvar
            // get("website") 'website' is the key value result data
            Log.i(TAG, "Result");
            gpsInterval = data.getExtras().getInt("connected");
            intent.putExtra("Update_Interval", gpsInterval);

            if (!menu.getItem(1).isChecked()) {
                LocalBroadcastManager.getInstance(this).registerReceiver(gpsBroadcastReceiver, new IntentFilter(BackgroundLocationService.BROADCAST_ACTION));
                bindService(intent, mServerConn, Context.BIND_AUTO_CREATE);
                startService(intent);
            }
            //changeLocationRequestInterval(gpsInterval);
            //controlGPS();

        }
        else if (resultCode == 250) {

            String state = data.getExtras().getString("state");
            Log.e("state", state);
            Toast.makeText(getApplicationContext(), state, Toast.LENGTH_LONG).show();
        }

    }

//    public static class QueryFeatureLayer extends AsyncTask<String, Void, FeatureResult> {
//        @Override
//        protected FeatureResult doInBackground(String... params) {
//
//            String whereClause = "UserID='" + params[0] + "'";
//
//            Log.e("Where", whereClause);
//
//            // Define a new query and set parameters
//            QueryParameters mParams = new QueryParameters();
//            mParams.setWhere(whereClause);
//            mParams.setReturnGeometry(true);
//
//            // Define the new instance of QueryTask
//            QueryTask queryTask = new QueryTask(featureLayerURL);
//            FeatureResult results;
//
//            try {
//                // run the querytask
//                results = queryTask.execute(mParams);
//                return results;
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//            return null;
//        }
//
//        @Override
//        protected void onPostExecute(FeatureResult results) {
//
//            // Remove the result from previously run query task
//            graphicsLayer.removeAll();
//
//            // Define a new marker symbol for the result graphics
//            SimpleMarkerSymbol sms = new SimpleMarkerSymbol(Color.BLUE, 10, SimpleMarkerSymbol.STYLE.CIRCLE);
//
//            // Envelope to focus on the map extent on the results
//            Envelope extent = new Envelope();
//
//            // iterate through results
//            for (Object element : results) {
//                // if object is feature cast to feature
//                if (element instanceof Feature) {
//                    Feature feature = (Feature) element;
//                    // convert feature to graphic
//                    Graphic graphic = new Graphic(feature.getGeometry(), sms, feature.getAttributes());
//                    // merge extent with point
//                    extent.merge((Point) graphic.getGeometry());
//                    // add it to the layer
//                    graphicsLayer.addGraphic(graphic);
//                }
//            }
//
//            // Set the map extent to the envelope containing the result graphics
//            mapView.setExtent(extent, 100);
//        }
//    }

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


    private View loadView(String id, String username, String vehicle, String time) {
        View view = LayoutInflater.from(MainActivity.this).inflate(R.layout.pointinfo, null);
        final TextView textNummer = (TextView) view.findViewById(R.id.popup);
        String out = "Username: " + username + "\nVehicle: " + vehicle + "\nTime: " + time;
        textNummer.setText(out);

        return view;
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

    public MainActivity getMainActivity() {
        return this;
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

    /**
     * request Detected Activities Updates
     */
    public void requestActivityUpdates() {
        if (client != null) {
            if (!client.isConnected()) {
                Toast.makeText(this, "Not connected",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(
                    client,
                    Constants.DETECTION_INTERVAL_IN_MILLISECONDS,
                    getActivityDetectionPendingIntent()
            ).setResultCallback(this);
        }
    }

    private PendingIntent getActivityDetectionPendingIntent() {
        Intent intent = new Intent(this, DetectedActivitiesIntentService.class);

        // We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when calling
        // requestActivityUpdates() and removeActivityUpdates().
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * stop requesting the activity and remove the activity updates for the PendingIntent
     */
    public void removeActivityUpdates() {
        if (menu.getItem(0) != null && menu.getItem(0).isVisible())
            menu.getItem(0).setVisible(false);
        if (client != null) {
            if (!client.isConnected()) {
                Toast.makeText(this, "Not Connected", Toast.LENGTH_SHORT).show();
                return;
            }
            // Remove all activity updates for the PendingIntent that was used to request activity
            // updates.
            ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(
                    client,
                    getActivityDetectionPendingIntent()
            ).setResultCallback(this);
        }
    }

}


