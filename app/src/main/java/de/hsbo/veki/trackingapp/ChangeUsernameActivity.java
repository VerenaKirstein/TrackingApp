package de.hsbo.veki.trackingapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ChangeUsernameActivity extends AppCompatActivity {

    public static final String PREFS_NAME = "TRACKINGAPP";
    public static final String USERNAME = "username";
    public static final String RADNDOM_ID = "id";
    SharedPreferences sharedpreferences;

    private Button btn_save;
    private Button btn_abort;
    private EditText etext_username;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_username_layout);

        btn_save = (Button) findViewById(R.id.btn_save_username);
        btn_abort = (Button) findViewById(R.id.btn_abort_username);

        etext_username = (EditText) findViewById(R.id.editText_username);

        sharedpreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        btn_save.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {

                // Get Username from Edittext
                String username = etext_username.getText().toString();
                String id = UUID.randomUUID().toString();

                // Set Username on Device
                SharedPreferences.Editor editor = sharedpreferences.edit();

                // Delete last username
                editor.clear();

                editor.putString(USERNAME, username);
                editor.putString(RADNDOM_ID, id);
                editor.commit();

                Toast.makeText(getApplicationContext(),
                        "Benutzername: " + username + " gesetzt!", Toast.LENGTH_LONG).show();

                // Back to MainActivity
                Intent main_activity_intent = new Intent(ChangeUsernameActivity.this, MainActivity.class);
                main_activity_intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(main_activity_intent);
            }
        });

        btn_abort.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {

                Toast.makeText(getApplicationContext(),
                        "Änderung abgebrochen", Toast.LENGTH_LONG).show();

                // Back to MainActivity
                Intent main_activity_intent = new Intent(ChangeUsernameActivity.this, MainActivity.class);
                main_activity_intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(main_activity_intent);

            }
        });
    }


}
