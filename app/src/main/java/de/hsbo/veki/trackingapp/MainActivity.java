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
import android.provider.Settings;
import android.support.annotation.NonNull;
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
import com.esri.core.geometry.GeometryEngine;
import com.esri.core.geometry.Point;
import com.esri.core.geometry.SpatialReference;
import com.esri.core.map.Feature;
import com.esri.core.map.Graphic;
import com.esri.core.symbol.SimpleMarkerSymbol;
import com.esri.core.table.TableException;
import com.esri.core.tasks.geodatabase.GeodatabaseSyncTask;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Result;
import com.google.android.gms.common.api.ResultCallback;
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
    private static Context context;


    // Attributes for LocalFilegeodatabase
    private GeodatabaseSyncTask gdbSyncTask;
    private File demoDataFile;
    private String offlineDataSDCardDirName;
    private String filename;
    private String OFFLINE_FILE_EXTENSION = ".geodatabase";
    private Callout callout;
    private LocalGeodatabase localGeodatabase;
    private String username;

    private String user_id = null;


    // Attributes for the Map
    public static MapView mapView;
    static String featureLayerURL;
    public static String featureServiceURL;
    static GraphicsLayer graphicsLayer;
    private FeatureLayer offlineFeatureLayer;


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
    private Intent username_intent;
    private UserCredentials userCredentials;


    // Attributes for BackgroundService
    private Intent intent;
    private GPSBroadcastReceiver gpsBroadcastReceiver;
    private BackgroundLocationService mService;
    public static GoogleApiClient client;
    public static LocationRequest mLocationRequest;
    private Boolean mBound = false;
    private Point newLocation;
    private Point oldLocation = null;
    private ActivityDetectionBroadcastReceiver mBroadcastReceiver;
    private ArrayList<DetectedActivity> mDetectedActivities;


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


        // Read Username from Device
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
                Log.i(TAG," set local Geodatabase");
                localGeodatabase = new LocalGeodatabase(createGeodatabaseFilePath(), featureServiceURL, getMainActivity(), mapView);
                offlineFeatureLayer = localGeodatabase.getOfflineFeatureLayer();

                Log.i(TAG,"  local Geodatabase");
                mapView.addLayer(offlineFeatureLayer);
                mapView.getLayer(2).setVisible(false);

                // Create a personal FeatureLayer and add to map
                user_id = userCredentials.getUserid();

                // Load FeatureLayer for user_id
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

                if (user_id != "null") {

                    long[] selectedFeatures = offlineFeatureLayer.getFeatureIDs(x, y, 25, 1);

                    if (selectedFeatures.length > 0) {

                        // Feature is selected
                        offlineFeatureLayer.selectFeatures(selectedFeatures, false);

                        // Get Feature attributes
                        Feature feature = offlineFeatureLayer.getFeature(selectedFeatures[0]);
                        String featureUser_ID = feature.getAttributeValue("UserID").toString();
                        String featureUsername = feature.getAttributeValue("Username").toString();
                        String featureVehicle = feature.getAttributeValue("Vehicle").toString();
                        String featureTime = feature.getAttributeValue("Time").toString();

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

                    offlineFeatureLayer.clearSelection();
                }
            }

        });


        // Initialize background service
        intent = new Intent(this, BackgroundLocationService.class);
        IntentFilter intentFilter = new IntentFilter("android.intent.action.MAIN");

//        // Initialize Userinput Inent
//        username_intent = new Intent(this, ChangeUsernameActivity.class);

        gpsBroadcastReceiver = new GPSBroadcastReceiver();
        mBroadcastReceiver = new ActivityDetectionBroadcastReceiver();
        mDetectedActivities = new ArrayList<DetectedActivity>();

        // Set the confidence level of each monitored activity to zero.
        for (int i = 0; i < Constants.MONITORED_ACTIVITIES.length; i++) {

            Log.i(TAG, "Set confidence level");
            mDetectedActivities.add(new DetectedActivity(Constants.MONITORED_ACTIVITIES[i], 0));
        }

        if (isMyServiceRunning(BackgroundLocationService.class)) {

            bindService(intent, mServerConn, Context.BIND_AUTO_CREATE);
            LocalBroadcastManager.getInstance(this).registerReceiver(gpsBroadcastReceiver, new IntentFilter(BackgroundLocationService.BROADCAST_ACTION));

        }


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

        autoCheckbox = menu.getItem(3).getSubMenu().getItem(0);
        pedestrianCheckbox = menu.getItem(3).getSubMenu().getItem(1);
        bicycleCheckbox = menu.getItem(3).getSubMenu().getItem(2);
        carCheckbox = menu.getItem(3).getSubMenu().getItem(3);

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
                userCredentials.setVehicle("Automatisch erkennen");
        }

        return true;

    }


    @Override
    protected void onDestroy() {

        unbindService(mServerConn);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(gpsBroadcastReceiver);
        super.onDestroy();
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {

            case R.id.action_username:

                Intent interval_intent_username = new Intent(getApplicationContext(), ChangeUsernameActivity.class);
                startActivityForResult(interval_intent_username, 200);
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

                user_id = userCredentials.getUserid();

                final LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

                if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {


                    if (user_id == "null") {

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


            case R.id.carMenuItem:
                userCredentials.setVehicle("Car");
                menu.getItem(0).setIcon(R.drawable.car);
                carCheckbox.setChecked(true);
                return true;

            case R.id.pedestrianMenuItem:
                userCredentials.setVehicle("Walking");
                menu.getItem(0).setIcon(R.drawable.walking);
                pedestrianCheckbox.setChecked(true);
                return true;

            case R.id.bicycleMenuItem:
                userCredentials.setVehicle("Bike");
                menu.getItem(0).setIcon(R.drawable.bike);
                bicycleCheckbox.setChecked(true);
                return true;

            case R.id.auto:
                userCredentials.setVehicle(mDetectedActivities.get(0).toString());
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
            double geschw = distance / (3.6);
            geschw = Math.round(geschw * 100) / 100.0;
            bewGeschw.setText(roundValue(geschw, 1) + " m/s");
        } else {
            bewGeschw.setText(valueOf(0.0 + " m/s"));
        }

        latitudeText.setText(roundValue(lat, 7));
        longitudeText.setText(roundValue(lon, 6));

        if (newLocation != null) {
            updateGraphic(newLocation);
            Log.e("newLoc", newLocation.toString());
            addFeatureToLocalgeodatabase(newLocation);
        }





    }

    public String roundValue(double value, int i) {
        return String.valueOf(new BigDecimal(value).round(new MathContext(i)));
    }


    private void addFeatureToLocalgeodatabase(Point newLocation) {

        DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.MEDIUM);
        String newLocationTime = dateFormat.format(Calendar.getInstance().getTime());

        //make a map of attributes
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("UserID", userCredentials.getUserid());
        attributes.put("Username", userCredentials.getUsername());
        attributes.put("Sex", userCredentials.getSex());
        attributes.put("Age", userCredentials.getAge());
        attributes.put("Profession", userCredentials.getProfession());
        attributes.put("Vehicle", userCredentials.getVehicle());
        attributes.put("Time", newLocationTime);
        attributes.put("Speed", bewGeschw.getText().toString());

        Log.e("attributes", attributes.toString());

        try {
            localGeodatabase.addFeature(attributes, newLocation);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Log all Features in Database
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);


        switch (resultCode) {
            case 100:
                Log.i(TAG, "Result 100");
                gpsInterval = data.getExtras().getInt("connected");
                intent.putExtra("Update_Interval", gpsInterval);

                if (!menu.getItem(1).isChecked()) {
                    LocalBroadcastManager.getInstance(this).registerReceiver(gpsBroadcastReceiver, new IntentFilter(BackgroundLocationService.BROADCAST_ACTION));
                    bindService(intent, mServerConn, Context.BIND_AUTO_CREATE);
                    startService(intent);
                }


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

        //menu.getItem(0).setVisible(true);

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
            Log.i(TAG, "Activity Received");
            setIconActivity(updatedActivities);

        }
    }



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


    public void updateQueryFeatureLayer() {
        String user_id = userCredentials.getUserid();
        new QueryFeatureLayer().execute(user_id);
    }
}


