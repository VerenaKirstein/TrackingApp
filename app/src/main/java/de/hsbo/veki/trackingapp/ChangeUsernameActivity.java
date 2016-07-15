package de.hsbo.veki.trackingapp;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

public class ChangeUsernameActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {

    // Attributes for UI
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

    // Attribute to receive user credentials
    private Intent main_activity__user_intent = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_username_layout);

        // initialize UI
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

        // save attributes
        btn_save.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {

                // Get Username from Edittext
                username_item = etext_username.getText().toString();
                age_item = etext_age.getText().toString();
                profession_item = etext_profession.getText().toString();
                spinner_sex_item = (String) spinner_sex.getSelectedItem();

                // Create UserID from input parameters
                user_id_item = (username_item + age_item + profession_item + spinner_sex_item).hashCode();

                // all attributes must set
                if (!username_item.isEmpty() && !age_item.isEmpty() && !profession_item.isEmpty()) {

                    // age must between 0 and 100
                    if (Integer.parseInt(age_item) > 0 && Integer.parseInt(age_item) < 100) {

                        // add attributes to intent
                        main_activity__user_intent = new Intent();
                        main_activity__user_intent.putExtra("Username", username_item);
                        main_activity__user_intent.putExtra("UserID", String.valueOf(user_id_item));
                        main_activity__user_intent.putExtra("Age", age_item);
                        main_activity__user_intent.putExtra("Sex", spinner_sex_item);
                        main_activity__user_intent.putExtra("Profession", profession_item);

                        setResult(200, main_activity__user_intent);
                        finish();

                    } else {
                        Toast.makeText(getApplicationContext(),
                                "Alter falsch eingegeben!", Toast.LENGTH_LONG).show();
                    }

                } else {

                    Toast.makeText(getApplicationContext(),
                            "Eingaben unvollständig!", Toast.LENGTH_LONG).show();
                }
            }
        });

        // abort attributes
        btn_abort.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {

                setResult(200, main_activity__user_intent);
                finish();

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
