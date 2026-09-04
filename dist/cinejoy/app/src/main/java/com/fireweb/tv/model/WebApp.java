package com.fireweb.tv.model;

import org.json.JSONException;
import org.json.JSONObject;

public class WebApp {
    private String id;
    private String name;
    private String url;
    private String icon;
    private String category;
    private String userAgent;
    private boolean adBlockEnabled;
    private boolean virtualCursorDefault;

    public WebApp() {
        this.adBlockEnabled = true;
        this.virtualCursorDefault = false;
    }

    public WebApp(String id, String name, String url, String icon, String category,
                  String userAgent, boolean adBlockEnabled, boolean virtualCursorDefault) {
        this.id = id;
        this.name = name;
        this.url = url;
        this.icon = icon;
        this.category = category;
        this.userAgent = userAgent;
        this.adBlockEnabled = adBlockEnabled;
        this.virtualCursorDefault = virtualCursorDefault;
    }

    public static WebApp fromJson(JSONObject obj) {
        WebApp app = new WebApp();
        app.id = obj.optString("id", String.valueOf(System.currentTimeMillis()));
        app.name = obj.optString("name", "Web App");
        app.url = obj.optString("url", "https://google.com");
        app.icon = obj.optString("icon", "");
        app.category = obj.optString("category", "General");
        app.userAgent = obj.optString("userAgent", "");
        app.adBlockEnabled = obj.optBoolean("adBlockEnabled", true);
        app.virtualCursorDefault = obj.optBoolean("virtualCursorDefault", false);
        return app;
    }

    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("id", id);
            obj.put("name", name);
            obj.put("url", url);
            obj.put("icon", icon);
            obj.put("category", category);
            obj.put("userAgent", userAgent);
            obj.put("adBlockEnabled", adBlockEnabled);
            obj.put("virtualCursorDefault", virtualCursorDefault);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return obj;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public boolean isAdBlockEnabled() { return adBlockEnabled; }
    public void setAdBlockEnabled(boolean adBlockEnabled) { this.adBlockEnabled = adBlockEnabled; }

    public boolean isVirtualCursorDefault() { return virtualCursorDefault; }
    public void setVirtualCursorDefault(boolean virtualCursorDefault) { this.virtualCursorDefault = virtualCursorDefault; }
}
