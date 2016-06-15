package de.hsbo.veki.trackingapp;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.io.Serializable;
import java.util.UUID;

/**
 * Created by Verena Rabea on 14.06.2016.
 */
public class ChangeGpsUpdateInterval extends AppCompatActivity {

    private EditText interval;
    private Button set_Interval;
    public static final String TAG = "GPS_Interval";

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gps_interval_layout);

        set_Interval = (Button) findViewById(R.id.btn_set_interval);
        interval = (EditText) findViewById(R.id.editNumber_interval);

        set_Interval.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {

                // Get Username from Edittext
                int intervalNum = Integer.parseInt(interval.getText().toString());

                Toast.makeText(getApplicationContext(),
                        "GPS Interval auf: " + intervalNum + " Millisekunden gesetzt!", Toast.LENGTH_LONG).show();

                //set Result
                Intent main_activity_intent = new Intent();
                main_activity_intent.putExtra("connected", intervalNum);
                setResult(100, main_activity_intent);
                // Back to MainActivity
                finish();
            }
        });


    }


}
