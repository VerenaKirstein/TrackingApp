package de.hsbo.veki.trackingapp;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

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
