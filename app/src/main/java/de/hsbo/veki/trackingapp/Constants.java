package de.hsbo.veki.trackingapp;

import com.google.android.gms.location.DetectedActivity;


public final class Constants {

    // Attributes
    public static final String PACKAGE_NAME = "de.hsbo.veki.trackingapp.activityrecognition";
    public static final String BROADCAST_ACTION = PACKAGE_NAME + ".BROADCAST_ACTION";
    public static final String ACTIVITY_EXTRA = PACKAGE_NAME + ".ACTIVITY_EXTRA";
    public static final String TAG = "SAMPLE_ANDROID_APP";
    public static final String PREFS_NAME = "TRACKING";


    /**
     * The desired time between activity detections.
     */
    public static final long DETECTION_INTERVAL_IN_MILLISECONDS = 15000;

    /**
     * List of DetectedActivity types that are monitored
     */
    protected static final int[] MONITORED_ACTIVITIES = {
            DetectedActivity.STILL,
            DetectedActivity.ON_FOOT,
            DetectedActivity.WALKING,
            DetectedActivity.RUNNING,
            DetectedActivity.ON_BICYCLE,
            DetectedActivity.IN_VEHICLE,
            DetectedActivity.TILTING,
            DetectedActivity.UNKNOWN
    };

}
