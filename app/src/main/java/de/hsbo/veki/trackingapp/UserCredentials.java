package de.hsbo.veki.trackingapp;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Class representing UserCredentials
 */
public class UserCredentials {

    private String userid;
    private String username;
    private String age;
    private String sex;
    private String profession;
    private String vehicle;

    private SharedPreferences sharedPref;


    public UserCredentials(Context context) {

        this.sharedPref = context.getSharedPreferences(Constants.PREFS_NAME, context.MODE_PRIVATE);
        updateUserCredentialsFromSharedPreferences();
    }


    public void updateUserCredentialsFromSharedPreferences() {

        this.userid = sharedPref.getString("UserID", "null");
        this.username = sharedPref.getString("Username", "null");
        this.age = sharedPref.getString("Age", "null");
        this.sex = sharedPref.getString("Sex", "null");
        this.profession = sharedPref.getString("Profession", "null");
        this.vehicle = sharedPref.getString("Vehicle", "null");

    }


    public void setUserCredentialsToSharedPreferences(String variable, String value) {

        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(variable, value);
        editor.apply();

    }

    public String getUserid() {
        return userid;
    }

    public void setUserid(String userid) {
        setUserCredentialsToSharedPreferences("UserID", userid);
        this.userid = userid;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        setUserCredentialsToSharedPreferences("Username", username);
        this.username = username;
    }

    public String getAge() {
        return age;
    }

    public void setAge(String age) {
        setUserCredentialsToSharedPreferences("Age", age);
        this.age = age;
    }

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        setUserCredentialsToSharedPreferences("Sex", sex);
        this.sex = sex;
    }

    public String getProfession() {
        return profession;
    }

    public void setProfession(String profession) {
        setUserCredentialsToSharedPreferences("Profession", profession);
        this.profession = profession;
    }

    public String getVehicle() {
        return vehicle;
    }

    public void setVehicle(String vehicle) {
        setUserCredentialsToSharedPreferences("Vehicle", vehicle);
        setUserCredentialsToSharedPreferences("Vehicle", vehicle);
        this.vehicle = vehicle;
    }

    @Override
    public String toString() {
        return "UserCredentials{" +
                "userid='" + userid + '\'' +
                ", username='" + username + '\'' +
                ", age='" + age + '\'' +
                ", sex='" + sex + '\'' +
                ", profession='" + profession + '\'' +
                ", vehicle='" + vehicle + '\'' +
                '}';
    }
}
