package de.hsbo.veki.trackingapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ChangeUsernameActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {

    public static final String PREFS_NAME = "TRACKINGAPP";
    public static final String USERNAME = "username";
    public static final String AGE = "age";
    public static final String SEX = "sex";
    public static final String PROFESSION = "profession";
    public static final String USER_ID = "user_id";
    SharedPreferences sharedpreferences;

    private Button btn_save;
    private Button btn_abort;
    private EditText etext_username;
    private EditText etext_age;
    private Spinner spinner_sex;
    private EditText etext_profession;

    private String spinner_sex_item = null;
    private String username_item = null;
    private String age_item = null;
    private String profession_item = null;
    private Integer user_id_item = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_username_layout);

        btn_save = (Button) findViewById(R.id.btn_save_username);
        btn_abort = (Button) findViewById(R.id.btn_abort_username);

        etext_username = (EditText) findViewById(R.id.editText_username);
        etext_age = (EditText) findViewById(R.id.editText_age);
        etext_profession = (EditText) findViewById(R.id.editText_profession);
        spinner_sex = (Spinner) findViewById(R.id.spinner_sex);

        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
        R.array.sex_array, android.R.layout.simple_spinner_item);
        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        spinner_sex.setAdapter(adapter);
        //spinner_sex.setSelection(0);

        sharedpreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        btn_save.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {

                // Get Username from Edittext
                username_item = etext_username.getText().toString();
                age_item = etext_age.getText().toString();
                profession_item = etext_profession.getText().toString();
                user_id_item = (username_item+age_item+profession_item).hashCode();

                spinner_sex_item = (String) spinner_sex.getSelectedItem();



                if (!username_item.isEmpty() && !age_item.isEmpty() && !profession_item.isEmpty()) {


                    if (Integer.parseInt(age_item) > 0 && Integer.parseInt(age_item) < 100) {

                        // Set Username on Device
                        SharedPreferences.Editor editor = sharedpreferences.edit();

                        // Delete last username
                        editor.clear();

                        editor.putString(USERNAME, username_item);
                        editor.putString(AGE, age_item.toString());
                        editor.putString(PROFESSION, profession_item);
                        editor.putString(SEX, spinner_sex_item);
                        editor.putString(USER_ID, user_id_item.toString());

                        editor.commit();

                        Toast.makeText(getApplicationContext(),
                                "Benutzername: " + username_item + " gesetzt!", Toast.LENGTH_LONG).show();

                        // Back to MainActivity
                        Intent main_activity_intent = new Intent(ChangeUsernameActivity.this, MainActivity.class);
                        main_activity_intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(main_activity_intent);

                    } else {
                        Toast.makeText(getApplicationContext(),
                                "Alter falsch eingegeben", Toast.LENGTH_LONG).show();
                    }



                } else {
                    Toast.makeText(getApplicationContext(),
                            "Eingaben unvollständig!", Toast.LENGTH_LONG).show();
                }



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


    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long l) {

        spinner_sex_item = (String) adapterView.getItemAtPosition(pos);
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }
}
