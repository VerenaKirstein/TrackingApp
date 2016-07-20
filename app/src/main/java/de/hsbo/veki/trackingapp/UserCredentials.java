package de.hsbo.veki.trackingapp;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Class representing UserCredentials
 */
public class UserCredentials {

    // Attributes
    private String userid;
    private String username;
    private String age;
    private String sex;
    private String profession;
    private String vehicle;

    private SharedPreferences sharedPref;

    /**
     * Constructor UserCredentials
     *
     * @param context - context from MainActivity
     */
    public UserCredentials(Context context) {
        this.sharedPref = context.getSharedPreferences(
                Constants.PREFS_NAME, Context.MODE_PRIVATE);
        updateFromSharedPreferences();
    }

    /**
     * Method to get all attributes from sharedPreferences
     */
    public void updateFromSharedPreferences() {
        this.userid = sharedPref.getString("UserID", "null");
        this.username = sharedPref.getString("Username", "null");
        this.age = sharedPref.getString("Age", "null");
        this.sex = sharedPref.getString("Sex", "null");
        this.profession = sharedPref.getString("Profession", "null");
        this.vehicle = sharedPref.getString("Vehicle", "null");
    }

    /**
     * Method to set attributes to sharedPreferences
     *
     * @param variable - attribute to set
     * @param value    - value to set
     */
    public void setToSharedPreferences(String variable, String value) {
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(variable, value);
        editor.apply();
    }

    
    public String getUserid() {
        return userid;
    }

    public void setUserid(String userid) {
        setToSharedPreferences("UserID", userid);
        this.userid = userid;
    }

/**
     * Getter methods
     * @return - attribute
     */
     
    /**
     * Setter methods
     * @param userid - value to set
     */
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        setToSharedPreferences("Username", username);
        this.username = username;
    }

    public String getAge() {
        return age;
    }

    public void setAge(String age) {
        setToSharedPreferences("Age", age);
        this.age = age;
    }

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        setToSharedPreferences("Sex", sex);
        this.sex = sex;
    }

    public String getProfession() {
        return profession;
    }

    public void setProfession(String profession) {
        setToSharedPreferences("Profession", profession);
        this.profession = profession;
    }

    public String getVehicle() {
        return vehicle;
    }

    public void setVehicle(String vehicle) {
        setToSharedPreferences("Vehicle", vehicle);
        this.vehicle = vehicle;
    }

    /**
     * toString method
     * @return - string of all attributes
     */
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
