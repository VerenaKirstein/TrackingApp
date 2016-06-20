package de.hsbo.veki.trackingapp;

import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Process;
import android.support.annotation.Nullable;
import android.widget.Toast;

/**
 * Created by Thorsten Kelm on 19.06.2016.
 */
public class BackgroundLocationService_alt extends Service {


    private class LocationListener implements com.google.android.gms.location.LocationListener {


        @Override
        public void onLocationChanged(Location location) {


        }
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    @Override
    public void onCreate() {
        HandlerThread thread = new HandlerThread("CreateService", Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        GPSLocationUpdates gps = new GPSLocationUpdates();
        gps.createGoogleApiClient(10000);
        gps.controlGPS();


    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Toast.makeText(this, "service starting", Toast.LENGTH_SHORT).show();

        // For each start request, send a message to start a job and deliver the
        // start ID so we know which request we're stopping when we finish the job


        // If we get killed, after returning from here, restart
        return START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {
        Toast.makeText(this, "service done", Toast.LENGTH_SHORT).show();
    }
}
