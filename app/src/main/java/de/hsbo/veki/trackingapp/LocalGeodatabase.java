package de.hsbo.veki.trackingapp;

import android.util.Log;

import com.esri.android.map.MapView;
import com.esri.core.ags.FeatureServiceInfo;
import com.esri.core.geodatabase.Geodatabase;
import com.esri.core.geodatabase.GeodatabaseFeature;
import com.esri.core.geodatabase.GeodatabaseFeatureTable;
import com.esri.core.geodatabase.GeodatabaseFeatureTableEditErrors;
import com.esri.core.geometry.Point;
import com.esri.core.map.CallbackListener;
import com.esri.core.table.TableException;
import com.esri.core.tasks.geodatabase.GenerateGeodatabaseParameters;
import com.esri.core.tasks.geodatabase.GeodatabaseStatusCallback;
import com.esri.core.tasks.geodatabase.GeodatabaseStatusInfo;
import com.esri.core.tasks.geodatabase.GeodatabaseSyncTask;
import com.esri.core.tasks.geodatabase.SyncGeodatabaseParameters;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Map;


public class LocalGeodatabase {

    // Attributes for Local Filegeodatabase
    private String filePath;
    private Geodatabase geodatabase = null;
    private GeodatabaseFeatureTable geodatabaseFeatureTable;

    private GeodatabaseSyncTask gdbSyncTask;
    private MainActivity mainActivity;
    private MapView map;


    /**
     * Constructor LocalFilegeodatabase
     *
     * @param filePath          - local path to the filegeodatbase on device
     * @param featureServiceUrl - featureServiceUrl to fetch
     * @param mainActivity      - mainActivity to show progressbar
     * @param map               - map to add layer
     * @throws FileNotFoundException
     */

    public LocalGeodatabase(String filePath, String featureServiceUrl, MainActivity mainActivity, MapView map) throws FileNotFoundException {

        this.filePath = filePath;
        this.mainActivity = mainActivity;
        this.map = map;
        this.gdbSyncTask = new GeodatabaseSyncTask(featureServiceUrl, null);

        // Check for Filegeodatabase
        File f = new File(filePath);

        // check filegeodatabasefile -> if no exists
        if (!f.exists()) {

            Log.i("File", "" + f.exists() + " " + featureServiceUrl);

            // create new filegeodatabase
            createFilegeodatabase();
        } else {

            initializeDatabase();

        }


    }

    /**
     * Method to initialize create Filegeodatabase
     */
    private synchronized void createFilegeodatabase() {

        Log.i("createFilegeodatabase", "Create GeoDatabase");
        // create a dialog to update user on progress

        MainActivity.progressDialog.setTitle("Create local geodatabase");
        MainActivity.progressDialog.show();

        // create the GeodatabaseTask
        Log.i("gdbSyncTask", gdbSyncTask.toString());
        gdbSyncTask.fetchFeatureServiceInfo(new CallbackListener<FeatureServiceInfo>() {

            @Override
            public void onError(Throwable arg0) {
                Log.e(MainActivity.TAG, "Error fetching FeatureServiceInfo");
            }

            @Override
            public void onCallback(FeatureServiceInfo fsInfo) {
                if (fsInfo.isSyncEnabled()) {
                    Log.i(MainActivity.TAG, "IS Sync Enabled, create Geodatabase");
                    Log.i(MainActivity.TAG, fsInfo.toString());

                    createGeodatabase(fsInfo);
                }
            }
        });

    }

    /**
     * Method to load geodatabase
     */
    private void initializeDatabase() {

        try {
            // initialize geodatabase
            geodatabase = new Geodatabase(filePath);
            // create a feature layer
            geodatabaseFeatureTable = geodatabase.getGeodatabaseFeatureTableByLayerId(0);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

    }

    /**
     * Method to add new features to the Filegeodatabase
     *
     * @param attr - feature attributes
     * @param p    - location
     * @throws Exception
     */
    public void addFeature(Map<String, Object> attr, Point p) throws Exception {

        try {

            if (geodatabaseFeatureTable == null)
                initializeDatabase();

            //Create a feature with these attributes at the given point
            GeodatabaseFeature gdbFeature = new GeodatabaseFeature(attr, p, geodatabaseFeatureTable);

            //Add the feature into the geodatabase table returning the new feature ID
            long fid = geodatabaseFeatureTable.addFeature(gdbFeature);

            String geomStr = geodatabaseFeatureTable.getFeature(fid).getGeometry().toString();
            Log.e(MainActivity.TAG, "added fid = " + fid + " " + geomStr);


        } catch (TableException e) {
            // report errors, e.g. to console
            Log.e(MainActivity.TAG, "", e);
        }

    }


    /**
     * Syncronice local Filegeodatabase with ArcGIS-Feature-Layer
     */
    public void syncGeodatabase() throws Exception {

        Log.i(MainActivity.TAG, "Sync geodatabase from " + this.filePath);
        SyncGeodatabaseParameters params = geodatabase.getSyncParameters();

        CallbackListener<Map<Integer, GeodatabaseFeatureTableEditErrors>> callback = new CallbackListener<Map<Integer, GeodatabaseFeatureTableEditErrors>>() {
            @Override
            public void onCallback(final Map<Integer, GeodatabaseFeatureTableEditErrors> paramT) {
                Log.i(MainActivity.TAG, "Sync Complete: " + (paramT == null || paramT.size() == 0 ? "Success" : "Fail"));
            }

            @Override
            public void onError(final Throwable paramThrowable) {
                Log.i(MainActivity.TAG, "Sync Error: ", paramThrowable);
            }
        };

        GeodatabaseStatusCallback syncStatusCallback = new GeodatabaseStatusCallback() {
            @Override
            public void statusUpdated(GeodatabaseStatusInfo status) {
                String sucessState = status.getStatus().toString();
                Log.i(MainActivity.TAG, sucessState);

                // load current query feature layer
                if (sucessState.equals("Completed")) {
                    Log.i(MainActivity.TAG, status.getStatus().toString());
                    mainActivity.showToast("Syncronisation abgeschlossen");

                    // clean graphics layer
                    mainActivity.cleanGraphicLayer();
                    // Add user tracked points to graphic
                    mainActivity.addUserPointsToGraphic();

                }

            }
        };

        Log.e("PARARMS: ", params.toString());
        gdbSyncTask.syncGeodatabase(params, geodatabase, syncStatusCallback, callback);

    }

    /**
     * Method to create local Filegeodatabase
     *
     * @param featureServerInfo - feature service attributes
     */
    private synchronized void createGeodatabase(FeatureServiceInfo featureServerInfo) {
        // set up the parameters to generate a geodatabase
        GenerateGeodatabaseParameters params = new GenerateGeodatabaseParameters(
                featureServerInfo, map.getExtent(),
                map.getSpatialReference());

        Log.i(MainActivity.TAG, params.toString());


        // a callback which fires when the task has completed or failed.
        CallbackListener<String> gdbResponseCallback = new CallbackListener<String>() {
            @Override
            public void onError(final Throwable e) {
                Log.e(MainActivity.TAG, "Error creating geodatabase " + e);
                MainActivity.progressDialog.dismiss();
            }

            @Override
            public void onCallback(String path) {
                Log.i(MainActivity.TAG, "Geodatabase is: " + path);
                MainActivity.progressDialog.dismiss();
                // log the path to the data on device
                Log.i(MainActivity.TAG, "path to geodatabase: " + path);
            }
        };

        // a callback which updates when the status of the task changes
        GeodatabaseStatusCallback statusCallback = new GeodatabaseStatusCallback() {
            @Override
            public void statusUpdated(final GeodatabaseStatusInfo status) {
                // get current status

                Log.e("status", status.getStatus().toString());
                String progress = status.getStatus().toString();
                showProgressBar(mainActivity, progress);

                //String sucessState = status.getStatus().toString();

                if (status.getStatus() == GeodatabaseStatusInfo.Status.COMPLETED) {
                    mainActivity.addUserPointsToGraphic();


                }

                /*if (sucessState.equals("Completed")) {
                    mainActivity.updateQueryFeatureLayer();
                }*/

            }
        };

        // get geodatabase based on params
        submitTask(params, filePath, statusCallback,
                gdbResponseCallback);
    }


    /**
     * Request database, poll server to get status, and download the file
     */
    private synchronized void submitTask(GenerateGeodatabaseParameters params,
                                         String file, GeodatabaseStatusCallback statusCallback,
                                         CallbackListener<String> gdbResponseCallback) {
        // submit task
        gdbSyncTask.generateGeodatabase(params, file, false, statusCallback,
                gdbResponseCallback);

        initializeDatabase();

    }


    /**
     * Show progress bar
     *
     * @param activity - MainActivity to show
     * @param message  - shown text
     */
    protected void showProgressBar(final MainActivity activity, final String message) {
        activity.runOnUiThread(new Runnable() {

            @Override
            public void run() {
                MainActivity.progressDialog.setMessage(message);
            }

        });
    }

    /**
     * Getter methods
     */
    public GeodatabaseFeatureTable getGeodatabaseFeatureTable() {
        return geodatabaseFeatureTable;
    }

}
