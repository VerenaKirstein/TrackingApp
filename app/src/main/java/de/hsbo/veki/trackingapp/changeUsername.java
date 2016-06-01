package de.hsbo.veki.trackingapp;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class changeUsername extends AppCompatActivity {

    private Button btn_save;
    private Button btn_abort;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_username_layout);

        btn_save = (Button) findViewById(R.id.btn_save_username);
        btn_abort = (Button) findViewById(R.id.btn_abort_username);

        btn_save.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {

                Toast.makeText(getApplicationContext(),
                        "Save Username", Toast.LENGTH_LONG).show();

                // TODO: Save Username on Device

            }
        });

        btn_abort.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {

                Toast.makeText(getApplicationContext(),
                        "No changes, go back", Toast.LENGTH_LONG).show();

                Intent main_activity_intent = new Intent(changeUsername.this, MainActivity.class);
                main_activity_intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(main_activity_intent);

            }
        });
    }
}
