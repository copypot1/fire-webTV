package com.fireweb.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.fireweb.tv.model.WebApp;
import com.fireweb.tv.server.CompanionServer;

import java.util.List;

public class MainActivity extends Activity implements AppManager.AppsChangeListener {
    private static final String TAG = "MainActivity";

    private AppManager appManager;
    private CompanionServer companionServer;
    private GridView appsGridView;
    private AppsAdapter appsAdapter;
    private TextView companionUrlText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        appManager = AppManager.getInstance(this);
        appManager.addListener(this);

        companionServer = new CompanionServer(this);
        companionServer.start();

        companionUrlText = findViewById(R.id.text_companion_url);
        appsGridView = findViewById(R.id.grid_apps);

        updateCompanionUrlDisplay();

        appsAdapter = new AppsAdapter();
        appsGridView.setAdapter(appsAdapter);

        // Click app tile to launch
        appsGridView.setOnItemClickListener((parent, view, position, id) -> {
            if (position == appsAdapter.getCount() - 1) {
                // Last item is "+ Add Website" tile
                showAddAppDialog();
            } else {
                WebApp app = appsAdapter.getItem(position);
                launchApp(app);
            }
        });

        // Long click to remove or edit
        appsGridView.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position < appsAdapter.getCount() - 1) {
                WebApp app = appsAdapter.getItem(position);
                showDeleteDialog(app);
                return true;
            }
            return false;
        });

        findViewById(R.id.btn_add_site).setOnClickListener(v -> showAddAppDialog());
    }

    private void updateCompanionUrlDisplay() {
        String ip = companionServer.getLocalIpAddress();
        companionUrlText.setText("Phone / Laptop URL: http://" + ip + ":" + CompanionServer.PORT);
    }

    private void launchApp(WebApp app) {
        Intent intent = new Intent(this, WebActivity.class);
        intent.putExtra(WebActivity.EXTRA_URL, app.getUrl());
        intent.putExtra(WebActivity.EXTRA_TITLE, app.getName());
        intent.putExtra(WebActivity.EXTRA_ADBLOCK, app.isAdBlockEnabled());
        intent.putExtra(WebActivity.EXTRA_USER_AGENT, app.getUserAgent());
        intent.putExtra(WebActivity.EXTRA_CURSOR_DEFAULT, app.isVirtualCursorDefault());
        startActivity(intent);
    }

    private void showAddAppDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_app, null);
        builder.setView(dialogView);

        EditText inputName = dialogView.findViewById(R.id.edit_app_name);
        EditText inputUrl = dialogView.findViewById(R.id.edit_app_url);
        Switch switchAdblock = dialogView.findViewById(R.id.switch_adblock);
        Switch switchCursor = dialogView.findViewById(R.id.switch_cursor);

        builder.setTitle("Add New Website as TV App");
        builder.setPositiveButton("Add App", (dialog, which) -> {
            String name = inputName.getText().toString().trim();
            String url = inputUrl.getText().toString().trim();

            if (name.isEmpty() || url.isEmpty()) {
                Toast.makeText(MainActivity.this, "Please enter both name and URL", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }

            WebApp newApp = new WebApp(
                    String.valueOf(System.currentTimeMillis()),
                    name,
                    url,
                    "",
                    "Custom",
                    "",
                    switchAdblock.isChecked(),
                    switchCursor.isChecked()
            );

            appManager.addApp(newApp);
            Toast.makeText(MainActivity.this, "Added " + name + "!", Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showDeleteDialog(WebApp app) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Remove App")
                .setMessage("Remove '" + app.getName() + "' from FireWeb?")
                .setPositiveButton("Remove", (dialog, which) -> {
                    appManager.deleteApp(app.getId());
                    Toast.makeText(MainActivity.this, "Removed " + app.getName(), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onAppsChanged() {
        runOnUiThread(() -> {
            if (appsAdapter != null) {
                appsAdapter.notifyDataSetChanged();
            }
            updateCompanionUrlDisplay();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateCompanionUrlDisplay();
        if (appsAdapter != null) {
            appsAdapter.notifyDataSetChanged();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        appManager.removeListener(this);
        if (companionServer != null) {
            companionServer.stop();
        }
    }

    private class AppsAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            // +1 for "+ Add App" tile
            return appManager.getApps().size() + 1;
        }

        @Override
        public WebApp getItem(int position) {
            List<WebApp> apps = appManager.getApps();
            if (position < apps.size()) {
                return apps.get(position);
            }
            return null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_app_card, parent, false);
            }

            TextView titleText = convertView.findViewById(R.id.card_title);
            TextView categoryText = convertView.findViewById(R.id.card_category);
            TextView badgeText = convertView.findViewById(R.id.card_badge);
            ImageView iconView = convertView.findViewById(R.id.card_icon);

            if (position == getCount() - 1) {
                // The "+ Add App" action tile
                titleText.setText("+ Add Website");
                categoryText.setText("Custom URL or Phone");
                badgeText.setVisibility(View.GONE);
                iconView.setImageResource(android.R.drawable.ic_input_add);
                convertView.setBackgroundResource(R.drawable.bg_card_add_selector);
            } else {
                WebApp app = getItem(position);
                titleText.setText(app.getName());
                categoryText.setText(app.getCategory());
                badgeText.setVisibility(View.VISIBLE);
                badgeText.setText(app.isAdBlockEnabled() ? "AD-FREE" : "STANDARD");
                badgeText.setTextColor(app.isAdBlockEnabled() ? Color.parseColor("#34D399") : Color.GRAY);
                iconView.setImageResource(android.R.drawable.ic_menu_compass);
                convertView.setBackgroundResource(R.drawable.bg_card_selector);
            }

            return convertView;
        }
    }
}
