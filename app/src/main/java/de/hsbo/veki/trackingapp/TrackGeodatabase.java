package de.hsbo.veki.trackingapp;

import android.app.ProgressDialog;
import android.content.Context;
import android.util.Log;

import com.esri.android.map.Callout;
import com.esri.android.map.FeatureLayer;
import com.esri.android.map.MapView;
import com.esri.android.map.ags.ArcGISFeatureLayer;
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

/**
 * Created by Thorsten Kelm on 08.06.2016.
 */
public class TrackGeodatabase {

    private static MapView mMapView;
    private static String filePath;
    private String featureServiceUrl;
    private static GeodatabaseSyncTask gdbSyncTask;
    private Geodatabase geodatabase = null;

    public GeodatabaseFeatureTable getGeodatabaseFeatureTable() {
        return geodatabaseFeatureTable;
    }

    private GeodatabaseFeatureTable geodatabaseFeatureTable;
    private FeatureLayer offlineFeatureLayer;
    private static Callout callout;
    public static ProgressDialog mProgressDialog;
    private static Context mContext;


    public TrackGeodatabase(String filePath, String featureServiceUrl, MapView mMapView) throws FileNotFoundException {
        this.filePath = filePath;
        this.featureServiceUrl = featureServiceUrl;
        this.mMapView = mMapView;

        // Check for Filegeodatabase
        File f = new File(filePath);

        TrackGeodatabase.setContext(MainActivity.getContext());
        // Create filegeodatabase if no exists
        if (f.exists() == false) {

            mProgressDialog = new ProgressDialog(MainActivity.getContext());
            mProgressDialog.setTitle("Create local runtime geodatabase");

            createFilegeodatabase(featureServiceUrl);
        }

        geodatabase = new Geodatabase(filePath);
        //create a feature layer and add it to the map
        geodatabaseFeatureTable = geodatabase.getGeodatabaseFeatureTableByLayerId(0);
        //create a feature layer and add it to the map
        offlineFeatureLayer = new FeatureLayer(geodatabaseFeatureTable);

        gdbSyncTask = new GeodatabaseSyncTask(featureServiceUrl, null);
    }

    public FeatureLayer getOfflineFeatureLayer() {
        return offlineFeatureLayer;
    }

    private void createFilegeodatabase(String featureServiceUrl) {
        Log.i(MainActivity.TAG, "Create GeoDatabase");
        // create a dialog to update user on progress
        MainActivity.mProgressDialog.show();
        // create the GeodatabaseTask

        gdbSyncTask.fetchFeatureServiceInfo(new CallbackListener<FeatureServiceInfo>() {

            @Override
            public void onError(Throwable arg0) {
                Log.e(MainActivity.TAG, "Error fetching FeatureServiceInfo");
            }

            @Override
            public void onCallback(FeatureServiceInfo fsInfo) {
                if (fsInfo.isSyncEnabled()) {
                    Log.i(MainActivity.TAG, "IS Sync Enabled, create Geodatabase");
                    createGeodatabase(fsInfo);
                }
            }
        });

    }


    public void addFeature(Map attr, Point p) throws Exception {

        try {
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
     *
     * @throws Exception
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
                Log.i(MainActivity.TAG, status.getStatus().toString());

            }
        };

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
                Log.e(MainActivity.TAG, "Error creating geodatabase");
                mProgressDialog.dismiss();
            }

            @Override
            public void onCallback(String path) {
                Log.i(MainActivity.TAG, "Geodatabase is: " + path);
                mProgressDialog.dismiss();
                // update map with local feature layer from geodatabase
                updateFeatureLayer(path);
                // log the path to the data on device
                Log.i(MainActivity.TAG, "path to geodatabase: " + path);
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
                MainActivity.showProgressBar(activity, progress);

            }
        };



        // get geodatabase based on params
        submitTask(params, filePath, statusCallback,
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
        com.esri.core.geodatabase.Geodatabase localGdb = null;
        try {
            localGdb = new com.esri.core.geodatabase.Geodatabase(featureLayerPath);
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

    // methods to ensure context is available when updating the progress dialog
    public static Context getContext() {
        return mContext;
    }

    public static void setContext(Context context) {
        mContext = context;
    }

}
