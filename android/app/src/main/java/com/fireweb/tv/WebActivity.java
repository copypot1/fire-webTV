package com.fireweb.tv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.fireweb.tv.adblock.AdBlockEngine;
import com.fireweb.tv.ui.VirtualCursorOverlay;

public class WebActivity extends Activity {
    private static final String TAG = "WebActivity";

    public static final String EXTRA_URL = "extra_url";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_ADBLOCK = "extra_adblock";
    public static final String EXTRA_USER_AGENT = "extra_user_agent";
    public static final String EXTRA_CURSOR_DEFAULT = "extra_cursor_default";

    private WebView webView;
    private VirtualCursorOverlay cursorOverlay;
    private FrameLayout customViewContainer;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private View customView;
    private ProgressBar progressBar;

    private boolean isAdBlockEnabled = true;
    private AdBlockEngine adBlockEngine;

    private static final String DEFAULT_DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep screen on for TV viewing
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUI();

        setContentView(R.layout.activity_web);

        webView = findViewById(R.id.web_view);
        cursorOverlay = findViewById(R.id.cursor_overlay);
        customViewContainer = findViewById(R.id.custom_view_container);
        progressBar = findViewById(R.id.progress_bar);

        adBlockEngine = AdBlockEngine.getInstance();
        adBlockEngine.init(this);

        cursorOverlay.setTargetWebView(webView);

        String targetUrl = getIntent().getStringExtra(EXTRA_URL);
        if (targetUrl == null || targetUrl.trim().isEmpty()) {
            targetUrl = "https://duckduckgo.com";
        }
        if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
            targetUrl = "https://" + targetUrl;
        }

        isAdBlockEnabled = getIntent().getBooleanExtra(EXTRA_ADBLOCK, true);
        boolean defaultCursor = getIntent().getBooleanExtra(EXTRA_CURSOR_DEFAULT, true);
        String customUa = getIntent().getStringExtra(EXTRA_USER_AGENT);

        cursorOverlay.setCursorEnabled(defaultCursor);

        configureWebView(customUa);

        Log.i(TAG, "Loading URL: " + targetUrl + " (AdBlock: " + isAdBlockEnabled + ")");
        webView.loadUrl(targetUrl);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView(String customUa) {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(false);
        s.setSupportMultipleWindows(false); // Disallow popup windows
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setMediaPlaybackRequiresUserGesture(false); // Auto-start streams on TV
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }

        if (customUa != null && !customUa.trim().isEmpty()) {
            s.setUserAgentString(customUa);
        } else {
            s.setUserAgentString(DEFAULT_DESKTOP_UA);
        }

        webView.setWebViewClient(new FireWebViewClient());
        webView.setWebChromeClient(new FireWebChromeClient());
    }

    private class FireWebViewClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            if (isAdBlockEnabled && request != null) {
                Uri url = request.getUrl();
                if (adBlockEngine.isAd(url)) {
                    return adBlockEngine.createEmptyResponse();
                }
            }
            return super.shouldInterceptRequest(view, request);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            if (request == null || request.getUrl() == null) return false;
            String scheme = request.getUrl().getScheme();
            if (scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                if (isAdBlockEnabled && adBlockEngine.isAd(request.getUrl())) {
                    Log.d(TAG, "Blocked redirect to ad domain: " + request.getUrl());
                    return true;
                }
                return false;
            }
            // Block external scheme hijacking (intent://, market://, etc.)
            return true;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            progressBar.setVisibility(View.VISIBLE);
            if (isAdBlockEnabled) {
                view.evaluateJavascript(adBlockEngine.getCosmeticJs(), null);
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            progressBar.setVisibility(View.GONE);
            if (isAdBlockEnabled) {
                view.evaluateJavascript(adBlockEngine.getCosmeticJs(), null);
            }
        }
    }

    private class FireWebChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            progressBar.setProgress(newProgress);
            if (newProgress >= 100) {
                progressBar.setVisibility(View.GONE);
            }
        }

        @Override
        public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
            Log.d("WebConsole", consoleMessage.message() + " -- From line "
                    + consoleMessage.lineNumber() + " of "
                    + consoleMessage.sourceId());
            return true;
        }

        // Fullscreen video playback handling
        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (customView != null) {
                callback.onCustomViewHidden();
                return;
            }

            customView = view;
            customViewCallback = callback;

            webView.setVisibility(View.GONE);
            cursorOverlay.setVisibility(View.GONE);

            customViewContainer.addView(customView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            customViewContainer.setVisibility(View.VISIBLE);

            hideSystemUI();
        }

        @Override
        public void onHideCustomView() {
            if (customView == null) return;

            customViewContainer.removeView(customView);
            customViewContainer.setVisibility(View.GONE);
            customView = null;

            if (customViewCallback != null) {
                customViewCallback.onCustomViewHidden();
                customViewCallback = null;
            }

            webView.setVisibility(View.VISIBLE);
            if (cursorOverlay.isCursorEnabled()) {
                cursorOverlay.setVisibility(View.VISIBLE);
            }

            hideSystemUI();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();

        // 1. Back button handling
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
            if (customView != null) {
                // Exit fullscreen video
                if (webView.getWebChromeClient() != null) {
                    ((FireWebChromeClient) webView.getWebChromeClient()).onHideCustomView();
                }
                return true;
            } else if (webView.canGoBack()) {
                webView.goBack();
                return true;
            } else {
                finish();
                return true;
            }
        }

        // 2. Menu button toggles Virtual Mouse Cursor
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                cursorOverlay.toggleCursor();
            }
            return true;
        }

        // 3. Media playback controls (Play, Pause, Fast Forward, Rewind)
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                toggleHtml5VideoPlayback();
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD && event.getAction() == KeyEvent.ACTION_UP) {
            seekHtml5Video(10);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_REWIND && event.getAction() == KeyEvent.ACTION_UP) {
            seekHtml5Video(-10);
            return true;
        }

        // 4. Virtual Cursor handling for D-Pad
        if (cursorOverlay.handleDpadKey(keyCode, event)) {
            return true;
        }

        return super.dispatchKeyEvent(event);
    }

    private void toggleHtml5VideoPlayback() {
        String script = "(function() {" +
                "  var v = document.querySelector('video');" +
                "  if (v) {" +
                "    if (v.paused) v.play(); else v.pause();" +
                "  }" +
                "})();";
        webView.evaluateJavascript(script, null);
    }

    private void seekHtml5Video(int seconds) {
        String script = "(function() {" +
                "  var v = document.querySelector('video');" +
                "  if (v) {" +
                "    v.currentTime += " + seconds + ";" +
                "  }" +
                "})();";
        webView.evaluateJavascript(script, null);
    }

    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) webView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
    }
}
