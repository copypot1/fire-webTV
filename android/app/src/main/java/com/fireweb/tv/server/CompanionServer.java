package com.fireweb.tv.server;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import android.util.Log;

import com.fireweb.tv.AppManager;
import com.fireweb.tv.WebActivity;
import com.fireweb.tv.model.WebApp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CompanionServer {
    private static final String TAG = "CompanionServer";
    public static final int PORT = 8080;

    private final Context context;
    private ServerSocket serverSocket;
    private boolean isRunning = false;
    private final ExecutorService threadPool = Executors.newFixedThreadPool(4);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public CompanionServer(Context context) {
        this.context = context.getApplicationContext();
    }

    public synchronized void start() {
        if (isRunning) return;

        threadPool.execute(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                isRunning = true;
                Log.i(TAG, "Companion server running at http://" + getLocalIpAddress() + ":" + PORT);

                while (isRunning && !serverSocket.isClosed()) {
                    Socket client = serverSocket.accept();
                    threadPool.execute(() -> handleClient(client));
                }
            } catch (Exception e) {
                if (isRunning) {
                    Log.e(TAG, "Server socket error", e);
                }
            }
        });
    }

    public synchronized void stop() {
        isRunning = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error closing server socket", e);
        }
    }

    public String getLocalIpAddress() {
        try {
            WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                int ip = wm.getConnectionInfo().getIpAddress();
                return Formatter.formatIpAddress(ip);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving IP", e);
        }
        return "127.0.0.1";
    }

    private void handleClient(Socket socket) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             OutputStream out = socket.getOutputStream()) {

            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) return;

            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return;

            String method = parts[0];
            String uri = parts[1];

            // Read HTTP headers
            Map<String, String> headers = new HashMap<>();
            String line;
            int contentLength = 0;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    String key = line.substring(0, colon).trim().toLowerCase();
                    String val = line.substring(colon + 1).trim();
                    headers.put(key, val);
                    if (key.equals("content-length")) {
                        contentLength = Integer.parseInt(val);
                    }
                }
            }

            // Read body if present
            String body = "";
            if (contentLength > 0) {
                char[] charBuf = new char[contentLength];
                int read = 0;
                while (read < contentLength) {
                    int r = reader.read(charBuf, read, contentLength - read);
                    if (r == -1) break;
                    read += r;
                }
                body = new String(charBuf);
            }

            // Handle OPTIONS pre-flight
            if (method.equalsIgnoreCase("OPTIONS")) {
                sendResponse(out, 204, "No Content", "text/plain", "");
                return;
            }

            // Routing
            if (uri.equals("/") || uri.startsWith("/?")) {
                sendResponse(out, 200, "OK", "text/html; charset=UTF-8", getWebPortalHtml());
            } else if (uri.equals("/api/apps") && method.equalsIgnoreCase("GET")) {
                List<WebApp> apps = AppManager.getInstance(context).getApps();
                JSONArray arr = new JSONArray();
                for (WebApp a : apps) {
                    arr.put(a.toJson());
                }
                sendResponse(out, 200, "OK", "application/json", arr.toString());
            } else if (uri.equals("/api/apps") && method.equalsIgnoreCase("POST")) {
                JSONObject json = new JSONObject(body);
                WebApp newApp = WebApp.fromJson(json);
                AppManager.getInstance(context).addApp(newApp);
                sendResponse(out, 201, "Created", "application/json", "{\"status\":\"ok\",\"id\":\"" + newApp.getId() + "\"}");
            } else if (uri.startsWith("/api/apps") && method.equalsIgnoreCase("DELETE")) {
                String id = extractQueryParam(uri, "id");
                boolean deleted = AppManager.getInstance(context).deleteApp(id);
                sendResponse(out, 200, "OK", "application/json", "{\"status\":\"ok\",\"deleted\":" + deleted + "}");
            } else if (uri.equals("/api/open") && method.equalsIgnoreCase("POST")) {
                JSONObject json = new JSONObject(body);
                String targetUrl = json.getString("url");
                String name = json.optString("name", "FireWeb Page");
                boolean adBlock = json.optBoolean("adBlockEnabled", true);

                mainHandler.post(() -> {
                    Intent intent = new Intent(context, WebActivity.class);
                    intent.putExtra(WebActivity.EXTRA_URL, targetUrl);
                    intent.putExtra(WebActivity.EXTRA_TITLE, name);
                    intent.putExtra(WebActivity.EXTRA_ADBLOCK, adBlock);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                });

                sendResponse(out, 200, "OK", "application/json", "{\"status\":\"ok\",\"launched\":true}");
            } else if (uri.equals("/api/status")) {
                JSONObject status = new JSONObject();
                status.put("status", "online");
                status.put("device", "Amazon Fire TV");
                status.put("ip", getLocalIpAddress());
                status.put("port", PORT);
                status.put("appsCount", AppManager.getInstance(context).getApps().size());
                sendResponse(out, 200, "OK", "application/json", status.toString());
            } else {
                sendResponse(out, 404, "Not Found", "text/plain", "Not Found");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error handling client request", e);
        }
    }

    private String extractQueryParam(String uri, String param) {
        int qIdx = uri.indexOf('?');
        if (qIdx == -1) return "";
        String queryString = uri.substring(qIdx + 1);
        for (String pair : queryString.split("&")) {
            String[] kv = pair.split("=");
            if (kv.length == 2 && kv[0].equals(param)) {
                try {
                    return URLDecoder.decode(kv[1], "UTF-8");
                } catch (Exception ignored) {}
            }
        }
        return "";
    }

    private void sendResponse(OutputStream out, int statusCode, String statusText, String contentType, String content) throws Exception {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 " + statusCode + " " + statusText + "\r\n" +
                "Content-Type: " + contentType + "\r\n" +
                "Content-Length: " + bytes.length + "\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, DELETE, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: Content-Type\r\n" +
                "Connection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.flush();
    }

    private String getWebPortalHtml() {
        return "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "  <meta charset=\"UTF-8\">\n" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "  <title>FireWeb TV Companion Portal</title>\n" +
                "  <style>\n" +
                "    :root { --bg: #0b0f19; --card: #151d30; --accent: #38bdf8; --accent-hover: #0ea5e9; --text: #f8fafc; --sub: #94a3b8; --border: #1e293b; --danger: #ef4444; }\n" +
                "    * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; }\n" +
                "    body { background-color: var(--bg); color: var(--text); padding: 20px 16px; min-height: 100vh; max-width: 600px; margin: 0 auto; }\n" +
                "    header { text-align: center; margin-bottom: 24px; }\n" +
                "    h1 { font-size: 1.6rem; color: #fff; display: flex; align-items: center; justify-content: center; gap: 8px; }\n" +
                "    .badge { background: #0284c7; color: #fff; font-size: 0.75rem; padding: 3px 8px; border-radius: 999px; font-weight: 600; }\n" +
                "    .subtitle { color: var(--sub); font-size: 0.9rem; margin-top: 6px; }\n" +
                "    .card { background: var(--card); border: 1px solid var(--border); border-radius: 14px; padding: 18px; margin-bottom: 20px; box-shadow: 0 4px 20px rgba(0,0,0,0.3); }\n" +
                "    h2 { font-size: 1.15rem; margin-bottom: 14px; color: var(--accent); }\n" +
                "    .form-group { margin-bottom: 12px; }\n" +
                "    label { display: block; font-size: 0.82rem; color: var(--sub); margin-bottom: 5px; text-transform: uppercase; letter-spacing: 0.5px; }\n" +
                "    input[type='text'], input[type='url'], select { width: 100%; padding: 12px; border-radius: 8px; border: 1px solid var(--border); background: #0c1220; color: #fff; font-size: 0.95rem; outline: none; transition: border-color 0.2s; }\n" +
                "    input:focus, select:focus { border-color: var(--accent); }\n" +
                "    .checkbox-group { display: flex; align-items: center; gap: 10px; margin-top: 10px; }\n" +
                "    .checkbox-group input { width: 18px; height: 18px; accent-color: var(--accent); cursor: pointer; }\n" +
                "    .btn { width: 100%; padding: 13px; border: none; border-radius: 8px; background: var(--accent); color: #0b0f19; font-weight: 700; font-size: 1rem; cursor: pointer; margin-top: 10px; transition: 0.2s ease; }\n" +
                "    .btn:hover { background: var(--accent-hover); }\n" +
                "    .btn-secondary { background: #1e293b; color: #f8fafc; margin-top: 0; }\n" +
                "    .app-item { display: flex; align-items: center; justify-content: space-between; background: #0c1220; border: 1px solid var(--border); padding: 12px; border-radius: 10px; margin-bottom: 10px; }\n" +
                "    .app-info { flex: 1; margin-right: 12px; overflow: hidden; }\n" +
                "    .app-title { font-weight: 600; font-size: 0.95rem; }\n" +
                "    .app-url { font-size: 0.78rem; color: var(--sub); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }\n" +
                "    .btn-del { background: transparent; border: 1px solid var(--danger); color: var(--danger); padding: 6px 12px; border-radius: 6px; font-size: 0.8rem; cursor: pointer; }\n" +
                "    .btn-launch { background: #0284c7; border: none; color: #fff; padding: 6px 12px; border-radius: 6px; font-size: 0.8rem; cursor: pointer; margin-right: 6px; }\n" +
                "    .toast { display: none; position: fixed; bottom: 20px; left: 50%; transform: translateX(-50%); background: #10b981; color: #fff; padding: 10px 20px; border-radius: 30px; font-weight: 600; font-size: 0.9rem; z-index: 99; }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "  <header>\n" +
                "    <h1>🔥 FireWeb TV <span class=\"badge\">Ad-Free</span></h1>\n" +
                "    <p class=\"subtitle\">Push websites directly to your Fire TV without typing with the remote</p>\n" +
                "  </header>\n" +
                "\n" +
                "  <div class=\"card\">\n" +
                "    <h2>⚡ Open Instant URL on TV</h2>\n" +
                "    <div class=\"form-group\">\n" +
                "      <label>Website URL</label>\n" +
                "      <input type=\"url\" id=\"instantUrl\" placeholder=\"https://example.com/movie-or-stream\" required />\n" +
                "    </div>\n" +
                "    <div class=\"checkbox-group\">\n" +
                "      <input type=\"checkbox\" id=\"instantAdblock\" checked />\n" +
                "      <label for=\"instantAdblock\" style=\"margin:0;cursor:pointer;\">Enable Ad-Blocker & Popup Shield</label>\n" +
                "    </div>\n" +
                "    <button class=\"btn\" onclick=\"launchInstantUrl()\">🚀 Open on Fire TV</button>\n" +
                "  </div>\n" +
                "\n" +
                "  <div class=\"card\">\n" +
                "    <h2>➕ Save New Web App to TV Hub</h2>\n" +
                "    <div class=\"form-group\">\n" +
                "      <label>App Name</label>\n" +
                "      <input type=\"text\" id=\"appName\" placeholder=\"e.g. Anime Streaming, Sportsurge\" />\n" +
                "    </div>\n" +
                "    <div class=\"form-group\">\n" +
                "      <label>Website URL</label>\n" +
                "      <input type=\"url\" id=\"appUrl\" placeholder=\"https://...\" />\n" +
                "    </div>\n" +
                "    <div class=\"form-group\">\n" +
                "      <label>Category</label>\n" +
                "      <select id=\"appCategory\">\n" +
                "        <option value=\"Video & Media\">Video & Media</option>\n" +
                "        <option value=\"Movies & Series\">Movies & Series</option>\n" +
                "        <option value=\"Live Streaming\">Live Streaming</option>\n" +
                "        <option value=\"Anime & Shows\">Anime & Shows</option>\n" +
                "        <option value=\"Sports\">Sports</option>\n" +
                "        <option value=\"Utilities\">Utilities</option>\n" +
                "      </select>\n" +
                "    </div>\n" +
                "    <div class=\"checkbox-group\">\n" +
                "      <input type=\"checkbox\" id=\"appAdBlock\" checked />\n" +
                "      <label for=\"appAdBlock\" style=\"margin:0;cursor:pointer;\">Block Ads & Popups</label>\n" +
                "    </div>\n" +
                "    <div class=\"checkbox-group\">\n" +
                "      <input type=\"checkbox\" id=\"appCursor\" checked />\n" +
                "      <label for=\"appCursor\" style=\"margin:0;cursor:pointer;\">Default to Virtual Mouse Cursor</label>\n" +
                "    </div>\n" +
                "    <button class=\"btn btn-secondary\" onclick=\"saveNewApp()\">💾 Save to Fire TV Apps</button>\n" +
                "  </div>\n" +
                "\n" +
                "  <div class=\"card\">\n" +
                "    <h2>📺 Saved TV Web Apps</h2>\n" +
                "    <div id=\"appsList\">Loading apps...</div>\n" +
                "  </div>\n" +
                "\n" +
                "  <div id=\"toast\" class=\"toast\"></div>\n" +
                "\n" +
                "  <script>\n" +
                "    function showToast(msg) {\n" +
                "      const t = document.getElementById('toast');\n" +
                "      t.innerText = msg;\n" +
                "      t.style.display = 'block';\n" +
                "      setTimeout(() => { t.style.display = 'none'; }, 2800);\n" +
                "    }\n" +
                "\n" +
                "    async function loadApps() {\n" +
                "      try {\n" +
                "        const res = await fetch('/api/apps');\n" +
                "        const apps = await res.json();\n" +
                "        const container = document.getElementById('appsList');\n" +
                "        if (!apps || apps.length === 0) {\n" +
                "          container.innerHTML = '<p style=\"color:var(--sub);font-size:0.9rem;\">No custom apps yet.</p>';\n" +
                "          return;\n" +
                "        }\n" +
                "        container.innerHTML = apps.map(app => `\n" +
                "          <div class=\"app-item\">\n" +
                "            <div class=\"app-info\">\n" +
                "              <div class=\"app-title\">${escapeHtml(app.name)}</div>\n" +
                "              <div class=\"app-url\">${escapeHtml(app.url)}</div>\n" +
                "            </div>\n" +
                "            <div>\n" +
                "              <button class=\"btn-launch\" onclick=\"launchApp('${escapeHtml(app.url)}', '${escapeHtml(app.name)}')\">Play</button>\n" +
                "              <button class=\"btn-del\" onclick=\"deleteApp('${app.id}')\">✕</button>\n" +
                "            </div>\n" +
                "          </div>\n" +
                "        `).join('');\n" +
                "      } catch (err) {\n" +
                "        document.getElementById('appsList').innerText = 'Error loading apps: ' + err.message;\n" +
                "      }\n" +
                "    }\n" +
                "\n" +
                "    async function launchInstantUrl() {\n" +
                "      const url = document.getElementById('instantUrl').value.trim();\n" +
                "      const adBlock = document.getElementById('instantAdblock').checked;\n" +
                "      if (!url) return alert('Please enter a URL');\n" +
                "      await fetch('/api/open', {\n" +
                "        method: 'POST',\n" +
                "        headers: { 'Content-Type': 'application/json' },\n" +
                "        body: JSON.stringify({ url: url, name: 'Web Link', adBlockEnabled: adBlock })\n" +
                "      });\n" +
                "      showToast('🚀 Opening website on Fire TV!');\n" +
                "      document.getElementById('instantUrl').value = '';\n" +
                "    }\n" +
                "\n" +
                "    async function launchApp(url, name) {\n" +
                "      await fetch('/api/open', {\n" +
                "        method: 'POST',\n" +
                "        headers: { 'Content-Type': 'application/json' },\n" +
                "        body: JSON.stringify({ url: url, name: name, adBlockEnabled: true })\n" +
                "      });\n" +
                "      showToast('🚀 Launched ' + name + ' on Fire TV!');\n" +
                "    }\n" +
                "\n" +
                "    async function saveNewApp() {\n" +
                "      const name = document.getElementById('appName').value.trim();\n" +
                "      const url = document.getElementById('appUrl').value.trim();\n" +
                "      const cat = document.getElementById('appCategory').value;\n" +
                "      const adBlock = document.getElementById('appAdBlock').checked;\n" +
                "      const cursor = document.getElementById('appCursor').checked;\n" +
                "      if (!name || !url) return alert('Name and URL required');\n" +
                "      await fetch('/api/apps', {\n" +
                "        method: 'POST',\n" +
                "        headers: { 'Content-Type': 'application/json' },\n" +
                "        body: JSON.stringify({ name: name, url: url, category: cat, adBlockEnabled: adBlock, virtualCursorDefault: cursor })\n" +
                "      });\n" +
                "      showToast('✅ Saved ' + name + ' to Fire TV!');\n" +
                "      document.getElementById('appName').value = '';\n" +
                "      document.getElementById('appUrl').value = '';\n" +
                "      loadApps();\n" +
                "    }\n" +
                "\n" +
                "    async function deleteApp(id) {\n" +
                "      if (!confirm('Remove this app from Fire TV?')) return;\n" +
                "      await fetch('/api/apps?id=' + encodeURIComponent(id), { method: 'DELETE' });\n" +
                "      showToast('Removed app');\n" +
                "      loadApps();\n" +
                "    }\n" +
                "\n" +
                "    function escapeHtml(str) {\n" +
                "      return (str || '').replace(/[&<>\"']/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '\"': '&quot;', \"'\": '&#39;' }[m]));\n" +
                "    }\n" +
                "\n" +
                "    loadApps();\n" +
                "  </script>\n" +
                "</body>\n" +
                "</html>";
    }
}
