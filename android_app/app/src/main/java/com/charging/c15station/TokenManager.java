package com.charging.c15station;

import android.content.Context;
import android.content.SharedPreferences;

public class TokenManager {
    public static final String HARDCODED_TOKEN = "d01fb845-d11f-43e8-a9b2-296197f1ca76";

    private static final String PREF_NAME = "c15_charging_prefs";
    private static final String KEY_TOKEN = "app_token";
    private static final String KEY_PHONE = "user_phone";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_UPDATE_TIME = "token_update_time";
    private static final String KEY_VIEW_MODE = "view_mode";

    private final SharedPreferences prefs;

    public TokenManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveToken(String token, String phone, int userId) {
        prefs.edit()
                .putString(KEY_TOKEN, (token != null && !token.trim().isEmpty()) ? token.trim() : HARDCODED_TOKEN)
                .putString(KEY_PHONE, phone != null ? phone : "")
                .putInt(KEY_USER_ID, userId)
                .putLong(KEY_UPDATE_TIME, System.currentTimeMillis())
                .apply();
    }

    public void saveTokenOnly(String token) {
        prefs.edit()
                .putString(KEY_TOKEN, (token != null && !token.trim().isEmpty()) ? token.trim() : HARDCODED_TOKEN)
                .putLong(KEY_UPDATE_TIME, System.currentTimeMillis())
                .apply();
    }

    public String getToken() {
        return HARDCODED_TOKEN;
    }

    public String getPhone() {
        return prefs.getString(KEY_PHONE, "");
    }

    public int getUserId() {
        return prefs.getInt(KEY_USER_ID, 0);
    }

    public boolean hasToken() {
        return true;
    }

    public void setViewMode(String mode) {
        prefs.edit().putString(KEY_VIEW_MODE, mode).apply();
    }

    public String getViewMode() {
        return prefs.getString(KEY_VIEW_MODE, "map");
    }

    public void clear() {
        prefs.edit().clear().apply();
    }
}
