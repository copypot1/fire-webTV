package com.fireweb.tv.adblock;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;
import android.webkit.WebResourceResponse;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public class AdBlockEngine {
    private static final String TAG = "AdBlockEngine";
    private static AdBlockEngine instance;

    private final Set<String> blockedDomains = new HashSet<>();
    private String cosmeticCss = "";
    private String cosmeticJs = "";
    private boolean initialized = false;

    private AdBlockEngine() {}

    public static synchronized AdBlockEngine getInstance() {
        if (instance == null) {
            instance = new AdBlockEngine();
        }
        return instance;
    }

    public synchronized void init(Context context) {
        if (initialized) return;

        // Load blocked domains
        try {
            InputStream is = context.getAssets().open("adblock_domains.txt");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim().toLowerCase();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    blockedDomains.add(line);
                }
            }
            reader.close();
            Log.d(TAG, "Loaded " + blockedDomains.size() + " ad/tracker domains.");
        } catch (Exception e) {
            Log.e(TAG, "Error loading adblock_domains.txt", e);
        }

        // Load cosmetic CSS
        try {
            InputStream is = context.getAssets().open("adblock_cosmetic.css");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            cosmeticCss = sb.toString();

            // Prepare base64-encoded CSS injection JS script
            String encodedCss = Base64.encodeToString(cosmeticCss.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            cosmeticJs = "(function() {" +
                    "  try {" +
                    "    if (document.getElementById('fireweb-adblock-style')) return;" +
                    "    var style = document.createElement('style');" +
                    "    style.id = 'fireweb-adblock-style';" +
                    "    style.type = 'text/css';" +
                    "    style.innerHTML = atob('" + encodedCss + "');" +
                    "    (document.head || document.documentElement).appendChild(style);" +
                    "    " +
                    "    /* Block malicious popups & window.open spam */" +
                    "    window.open = function(url, name, specs) {" +
                    "      console.log('FireWeb: Blocked popup window to: ' + url);" +
                    "      return null;" +
                    "    };" +
                    "    " +
                    "    /* Auto-dismiss anti-adblock modals if present */" +
                    "    setInterval(function() {" +
                    "      var antiAdElements = document.querySelectorAll('.fc-ab-root, .sp_message_container, [id*=\"adblock-overlay\"], [id*=\"adblock-modal\"]');" +
                    "      antiAdElements.forEach(function(el) { el.remove(); });" +
                    "      if (antiAdElements.length > 0) {" +
                    "        document.body.style.overflow = 'auto';" +
                    "      }" +
                    "    }, 1500);" +
                    "  } catch(e) { console.error('FireWeb cosmetic error: ' + e); }" +
                    "})();";
        } catch (Exception e) {
            Log.e(TAG, "Error loading adblock_cosmetic.css", e);
        }

        initialized = true;
    }

    public boolean isAd(Uri uri) {
        if (uri == null) return false;
        String host = uri.getHost();
        if (host == null) return false;
        host = host.toLowerCase();

        // Exact match check
        if (blockedDomains.contains(host)) {
            Log.d(TAG, "BLOCKED (exact): " + host);
            return true;
        }

        // Subdomain check (e.g. pagead2.googlesyndication.com matches googlesyndication.com)
        int dotIndex = host.indexOf('.');
        while (dotIndex > 0 && dotIndex < host.length() - 1) {
            String parentDomain = host.substring(dotIndex + 1);
            if (blockedDomains.contains(parentDomain)) {
                Log.d(TAG, "BLOCKED (parent domain): " + host + " -> " + parentDomain);
                return true;
            }
            dotIndex = host.indexOf('.', dotIndex + 1);
        }

        // Path or query check for known ad signatures
        String fullUrl = uri.toString().toLowerCase();
        if (fullUrl.contains("/ads.js") || fullUrl.contains("/pagead/") ||
            fullUrl.contains("googlesyndication") || fullUrl.contains("/ad-server/") ||
            fullUrl.contains("/popunder") || fullUrl.contains("adnxs.com")) {
            Log.d(TAG, "BLOCKED (url pattern): " + fullUrl);
            return true;
        }

        return false;
    }

    public WebResourceResponse createEmptyResponse() {
        return new WebResourceResponse(
                "text/plain",
                "UTF-8",
                new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8))
        );
    }

    public String getCosmeticJs() {
        return cosmeticJs;
    }
}
