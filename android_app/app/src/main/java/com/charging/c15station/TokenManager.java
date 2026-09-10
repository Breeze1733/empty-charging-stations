package com.charging.c15station;

import android.content.Context;
import android.content.SharedPreferences;

public class TokenManager {
    private static final String PREF_NAME = "c15_charging_prefs";
    private static final String KEY_TOKEN = "app_token";
    private static final String KEY_PHONE = "user_phone";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_UPDATE_TIME = "token_update_time";

    private final SharedPreferences prefs;

    public TokenManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveToken(String token, String phone, int userId) {
        prefs.edit()
                .putString(KEY_TOKEN, token != null ? token.trim() : "")
                .putString(KEY_PHONE, phone != null ? phone : "")
                .putInt(KEY_USER_ID, userId)
                .putLong(KEY_UPDATE_TIME, System.currentTimeMillis())
                .apply();
    }

    public void saveTokenOnly(String token) {
        prefs.edit()
                .putString(KEY_TOKEN, token != null ? token.trim() : "")
                .putLong(KEY_UPDATE_TIME, System.currentTimeMillis())
                .apply();
    }

    public String getToken() {
        return prefs.getString(KEY_TOKEN, "");
    }

    public String getPhone() {
        return prefs.getString(KEY_PHONE, "");
    }

    public int getUserId() {
        return prefs.getInt(KEY_USER_ID, 0);
    }

    public boolean hasToken() {
        String t = getToken();
        return t != null && !t.trim().isEmpty();
    }

    public void clear() {
        prefs.edit().clear().apply();
    }
}
