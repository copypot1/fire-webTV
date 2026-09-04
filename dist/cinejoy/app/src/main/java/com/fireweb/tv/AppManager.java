package com.fireweb.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.fireweb.tv.model.WebApp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class AppManager {
    private static final String TAG = "AppManager";
    private static final String PREFS_NAME = "fireweb_prefs";
    private static final String KEY_APPS_JSON = "saved_web_apps";

    private static AppManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final List<WebApp> cachedApps = new ArrayList<>();

    public interface AppsChangeListener {
        void onAppsChanged();
    }

    private final List<AppsChangeListener> listeners = new ArrayList<>();

    private AppManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadApps();
    }

    public static synchronized AppManager getInstance(Context context) {
        if (instance == null) {
            instance = new AppManager(context);
        }
        return instance;
    }

    public synchronized List<WebApp> getApps() {
        return new ArrayList<>(cachedApps);
    }

    public synchronized WebApp getAppById(String id) {
        for (WebApp app : cachedApps) {
            if (app.getId().equals(id)) {
                return app;
            }
        }
        return null;
    }

    public synchronized void addApp(WebApp app) {
        cachedApps.add(app);
        saveApps();
        notifyListeners();
    }

    public synchronized boolean deleteApp(String id) {
        boolean removed = cachedApps.removeIf(a -> a.getId().equals(id));
        if (removed) {
            saveApps();
            notifyListeners();
        }
        return removed;
    }

    public synchronized void addListener(AppsChangeListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public synchronized void removeListener(AppsChangeListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (AppsChangeListener l : listeners) {
            try {
                l.onAppsChanged();
            } catch (Exception e) {
                Log.e(TAG, "Error in change listener", e);
            }
        }
    }

    private synchronized void loadApps() {
        cachedApps.clear();
        String json = prefs.getString(KEY_APPS_JSON, null);
        if (json == null || json.trim().isEmpty()) {
            // Load defaults from assets
            loadDefaultsFromAssets();
            saveApps();
            return;
        }

        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                cachedApps.add(WebApp.fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed parsing saved apps, restoring defaults", e);
            loadDefaultsFromAssets();
        }
    }

    private void loadDefaultsFromAssets() {
        try {
            InputStream is = context.getAssets().open("default_apps.json");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();

            JSONArray arr = new JSONArray(sb.toString());
            for (int i = 0; i < arr.length(); i++) {
                cachedApps.add(WebApp.fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading default_apps.json", e);
        }
    }

    public synchronized void saveApps() {
        try {
            JSONArray arr = new JSONArray();
            for (WebApp app : cachedApps) {
                arr.put(app.toJson());
            }
            prefs.edit().putString(KEY_APPS_JSON, arr.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error saving apps to SharedPreferences", e);
        }
    }
}
