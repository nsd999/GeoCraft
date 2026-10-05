package com.nsd.geocraft;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Environment;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private static final int REQUEST_LOCATION = 101;
    private static final int REQUEST_NOTIFICATIONS = 102;

    private EditText latInput;
    private EditText lonInput;
    private TextView statusText;
    private SharedPreferences prefs;
    private WebView mapWebView;
    private final Handler updateHandler = new Handler(Looper.getMainLooper());
    private final Runnable updateCheckRunnable = this::checkForUpdates;
    private static final String UPDATE_MANIFEST_URL =
            "https://github.com/nsd999/GeoCraft/releases/download/v1.0.0/update.json";
    private static final String UPDATE_INTERVAL_MS = "1800000";

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(size);
        tv.setTextColor(color);
        tv.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        return tv;
    }

    private Button button(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        b.setMinHeight(dp(48));
        b.setPadding(dp(16), dp(8), dp(16), dp(8));
        return b;
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(14), dp(16), dp(14));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.rgb(23, 25, 34));
        bg.setCornerRadius(dp(18));
        box.setBackground(bg);
        return box;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("geocraft", MODE_PRIVATE);
        buildUi();
        requestPermissionsIfNeeded();
        checkForUpdates();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateHandler.removeCallbacks(updateCheckRunnable);
        updateHandler.postDelayed(updateCheckRunnable, Long.parseLong(UPDATE_INTERVAL_MS));
    }

    @Override
    protected void onPause() {
        updateHandler.removeCallbacks(updateCheckRunnable);
        super.onPause();
    }

    private void checkForUpdates() {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(UPDATE_MANIFEST_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setRequestProperty("User-Agent", "GeoCraft-UpdateChecker");
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return;

                StringBuilder jsonText = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) jsonText.append(line);
                }

                JSONObject manifest = new JSONObject(jsonText.toString());
                int latestCode = manifest.optInt("versionCode", 0);
                String latestName = manifest.optString("versionName", "");
                String apkUrl = manifest.optString("apk", "");

                if (latestCode > getCurrentVersionCode() && !apkUrl.isEmpty()) {
                    runOnUiThread(() -> showUpdateDialog(latestName, apkUrl));
                }
            } catch (Exception ignored) {
                // Update checking is best-effort and must never interrupt normal app use.
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();

        updateHandler.removeCallbacks(updateCheckRunnable);
        updateHandler.postDelayed(updateCheckRunnable, Long.parseLong(UPDATE_INTERVAL_MS));
    }

    private int getCurrentVersionCode() {
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                return (int) getPackageManager().getPackageInfo(getPackageName(), 0).getLongVersionCode();
            }
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    private void showUpdateDialog(String versionName, String apkUrl) {
        if (isFinishing()) return;

        new AlertDialog.Builder(this)
                .setTitle("🚀 New GeoCraft update available")
                .setMessage("GeoCraft " + versionName + " is ready.\n\nUpdate now to get the newest version. Your existing GeoCraft settings and saved locations will remain.")
                .setPositiveButton("Update now", (d, w) -> downloadAndInstallUpdate(versionName, apkUrl))
                .setNegativeButton("Later", null)
                .setCancelable(false)
                .show();
    }

    private void downloadAndInstallUpdate(String versionName, String apkUrl) {
        try {
            Uri uri = Uri.parse(apkUrl);
            DownloadManager.Request request = new DownloadManager.Request(uri);
            request.setTitle("GeoCraft " + versionName + " update");
            request.setDescription("Downloading the latest GeoCraft APK…");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS,
                    "GeoCraft-" + versionName + ".apk");
            request.setMimeType("application/vnd.android.package-archive");
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);

            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            long downloadId = manager.enqueue(request);

            Toast.makeText(this, "Downloading GeoCraft " + versionName + "…", Toast.LENGTH_LONG).show();

            android.content.BroadcastReceiver receiver = new android.content.BroadcastReceiver() {
                @Override
                public void onReceive(android.content.Context context, Intent intent) {
                    if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())
                            && intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) == downloadId) {
                        try {
                            android.database.Cursor cursor = manager.query(
                                    new DownloadManager.Query().setFilterById(downloadId));
                            if (cursor != null && cursor.moveToFirst()) {
                                int status = cursor.getInt(cursor.getColumnIndexOrThrow(
                                        DownloadManager.COLUMN_STATUS));
                                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                    Uri fileUri = manager.getUriForDownloadedFile(downloadId);
                                    if (fileUri != null) {
                                        Intent install = new Intent(Intent.ACTION_VIEW);
                                        install.setDataAndType(fileUri, "application/vnd.android.package-archive");
                                        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                        install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                        startActivity(install);
                                    }
                                } else {
                                    Toast.makeText(MainActivity.this,
                                            "GeoCraft update download failed. Please try again.",
                                            Toast.LENGTH_LONG).show();
                                }
                            }
                            if (cursor != null) cursor.close();
                        } catch (Exception e) {
                            Toast.makeText(MainActivity.this,
                                    "Could not open the downloaded update.",
                                    Toast.LENGTH_LONG).show();
                        } finally {
                            try { unregisterReceiver(this); } catch (Exception ignored) {}
                        }
                    }
                }
            };

            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(receiver,
                        new android.content.IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                        android.content.Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(receiver,
                        new android.content.IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
            }
        } catch (Exception e) {
            Toast.makeText(this,
                    "Unable to start the update. Please use the download link manually.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(16, 17, 23));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(28));
        scroll.addView(root);

        ImageView logo = new ImageView(this);
        logo.setImageResource(com.nsd.geocraft.R.drawable.geocraft_logo);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        root.addView(logo, new LinearLayout.LayoutParams(-1, dp(150)));

        TextView title = text("GeoCraft", 30, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(44)));

        TextView subtitle = text("Controlled mock-location toolkit for testing, QA and location-aware apps", 14, Color.rgb(158, 163, 183), false);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(dp(8), 0, dp(8), dp(10));
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout statusCard = card();
        statusText = text("Ready — choose a target location.", 14, Color.rgb(244, 245, 251), true);
        statusCard.addView(statusText, new LinearLayout.LayoutParams(-1, dp(44)));
        TextView statusHint = text("GeoCraft must be selected as Android's Mock Location App before starting.", 12, Color.rgb(158, 163, 183), false);
        statusCard.addView(statusHint);
        root.addView(statusCard, marginParams(-1, -2, 0, 0, 0, dp(14)));

        LinearLayout locCard = card();
        TextView locTitle = text("Target location", 17, Color.WHITE, true);
        locCard.addView(locTitle);
        TextView locHelp = text("Enter the latitude and longitude that other apps should receive.", 12, Color.rgb(158, 163, 183), false);
        locCard.addView(locHelp, marginParams(-1, -2, 0, dp(4), 0, dp(10)));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        latInput = new EditText(this);
        lonInput = new EditText(this);
        configureInput(latInput, "Latitude", String.valueOf(prefs.getFloat("lat", 17.3850f)));
        configureInput(lonInput, "Longitude", String.valueOf(prefs.getFloat("lon", 78.4867f)));
        row.addView(latInput, weightParams(0, 1));
        Space space = new Space(this);
        row.addView(space, new LinearLayout.LayoutParams(dp(10), 1));
        row.addView(lonInput, weightParams(0, 1));
        locCard.addView(row);
        root.addView(locCard, marginParams(-1, -2, 0, 0, 0, dp(14)));

        LinearLayout mapCard = card();
        TextView mapTitle = text("Pick on map", 17, Color.WHITE, true);
        mapCard.addView(mapTitle);
        TextView mapHelp = text("Tap anywhere on the map to select a location. The coordinates will fill automatically.", 12, Color.rgb(158, 163, 183), false);
        mapCard.addView(mapHelp, marginParams(-1, -2, 0, dp(4), 0, dp(10)));

        mapWebView = new WebView(this);
        mapWebView.setBackgroundColor(Color.rgb(20, 22, 30));
        WebSettings ws = mapWebView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setBuiltInZoomControls(false);
        ws.setDisplayZoomControls(false);
        mapWebView.setWebViewClient(new WebViewClient());
        mapWebView.addJavascriptInterface(new MapBridge(), "Android");
        mapCard.addView(mapWebView, new LinearLayout.LayoutParams(-1, dp(300)));
        Button useMap = button("Use selected map location");
        useMap.setOnClickListener(v -> saveInputs());
        mapCard.addView(useMap, marginParams(-1, dp(52), 0, dp(10), 0, 0));
        Button openMap = button("Open map in browser");
        openMap.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org"))); }
            catch (Exception ignored) {}
        });
        mapCard.addView(openMap, marginParams(-1, dp(52), 0, 0, 0, 0));
        root.addView(mapCard, marginParams(-1, -2, 0, 0, 0, dp(14)));
        loadMap();

        LinearLayout presets = card();
        TextView presetsTitle = text("Quick presets", 17, Color.WHITE, true);
        presets.addView(presetsTitle);
        LinearLayout pr = new LinearLayout(this);
        pr.setOrientation(LinearLayout.HORIZONTAL);
        addPreset(pr, "Hyderabad", 17.3850, 78.4867);
        Space s1 = new Space(this);
        pr.addView(s1, new LinearLayout.LayoutParams(dp(6), 1));
        addPreset(pr, "Delhi", 28.6139, 77.2090);
        Space s2 = new Space(this);
        pr.addView(s2, new LinearLayout.LayoutParams(dp(6), 1));
        addPreset(pr, "Mumbai", 19.0760, 72.8777);
        presets.addView(pr, marginParams(-1, dp(52), 0, dp(10), 0, 0));
        Button apply = button("Apply coordinates");
        apply.setOnClickListener(v -> saveInputs());
        presets.addView(apply);
        root.addView(presets, marginParams(-1, -2, 0, 0, 0, dp(14)));

        LinearLayout actions = card();
        TextView actionsTitle = text("Controls", 17, Color.WHITE, true);
        actions.addView(actionsTitle);

        Button start = button("Start mock location");
        start.setOnClickListener(v -> startMock());
        actions.addView(start, marginParams(-1, dp(52), 0, dp(10), 0, 0));

        Button stop = button("Stop mock location");
        stop.setOnClickListener(v -> stopMock());
        actions.addView(stop, marginParams(-1, dp(52), 0, dp(10), 0, 0));

        Button overlay = button("Enable / manage floating control");
        overlay.setOnClickListener(v -> openOverlaySettings());
        actions.addView(overlay, marginParams(-1, dp(52), 0, dp(10), 0, 0));

        Button update = button("Check for updates");
        update.setOnClickListener(v -> checkForUpdates());
        actions.addView(update, marginParams(-1, dp(52), 0, dp(10), 0, 0));

        Button developer = button("Open Android Mock Location settings");
        developer.setOnClickListener(v -> openDeveloperOptions());
        actions.addView(developer, marginParams(-1, dp(52), 0, 0, 0, 0));

        root.addView(actions, marginParams(-1, -2, 0, 0, 0, dp(14)));

        LinearLayout note = card();
        TextView noteTitle = text("Important", 16, Color.WHITE, true);
        note.addView(noteTitle);
        TextView noteText = text("Android marks injected locations as mock locations. Apps can detect or reject them. GeoCraft does not bypass an app's anti-spoofing checks.", 12, Color.rgb(158, 163, 183), false);
        note.addView(noteText, marginParams(-1, -2, 0, dp(5), 0, 0));
        root.addView(note);

        setContentView(scroll);
    }

    private LinearLayout.LayoutParams marginParams(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private LinearLayout.LayoutParams weightParams(int w, float weight) {
        return new LinearLayout.LayoutParams(dp(0), dp(52), weight);
    }

    private void configureInput(EditText input, String hint, String value) {
        input.setHint(hint);
        input.setText(value);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.rgb(130, 135, 150));
        input.setTextSize(15);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setSingleLine(true);
        input.setPadding(dp(12), 0, dp(12), 0);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.rgb(29, 32, 48));
        bg.setCornerRadius(dp(12));
        input.setBackground(bg);
    }

    private void addPreset(LinearLayout row, String name, double lat, double lon) {
        Button b = button(name);
        b.setTextSize(12);
        b.setOnClickListener(v -> {
            latInput.setText(String.valueOf(lat));
            lonInput.setText(String.valueOf(lon));
            saveInputs();
        });
        row.addView(b, new LinearLayout.LayoutParams(0, dp(48), 1));
    }

    private void loadMap() {
        String html = "<!doctype html><html><head>" +
                "<meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no'>" +
                "<link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'>" +
                "<style>html,body,#map{height:100%;margin:0;background:#14161e} .leaflet-control-attribution{font-size:9px}</style>" +
                "</head><body><div id='map'></div>" +
                "<script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script>" +
                "<script>" +
                "const savedLat=" + prefs.getFloat("lat",17.3850f) + ",savedLon=" + prefs.getFloat("lon",78.4867f) + ";" +
                "const map=L.map('map').setView([savedLat,savedLon],13);" +
                "L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'&copy; OpenStreetMap contributors'}).addTo(map);" +
                "let marker=L.marker([savedLat,savedLon]).addTo(map);" +
                "function pick(lat,lon){marker.setLatLng([lat,lon]);Android.selectLocation(lat,lon);}" +
                "map.on('click',e=>pick(e.latlng.lat,e.latlng.lng));" +
                "window.addEventListener('resize',()=>map.invalidateSize());" +
                "</script></body></html>";
        mapWebView.loadDataWithBaseURL("https://geocraft.local/", html, "text/html", "UTF-8", null);
    }

    private class MapBridge {
        @JavascriptInterface
        public void selectLocation(final double lat, final double lon) {
            runOnUiThread(() -> {
                latInput.setText(String.format(java.util.Locale.US, "%.6f", lat));
                lonInput.setText(String.format(java.util.Locale.US, "%.6f", lon));
                statusText.setText(String.format(java.util.Locale.US, "Map selected: %.6f, %.6f", lat, lon));
            });
        }
    }

    private void saveInputs() {
        try {
            double lat = Double.parseDouble(latInput.getText().toString().trim());
            double lon = Double.parseDouble(lonInput.getText().toString().trim());
            if (lat < -90 || lat > 90 || lon < -180 || lon > 180) throw new NumberFormatException();
            prefs.edit().putFloat("lat", (float) lat).putFloat("lon", (float) lon).apply();
            sendUpdate(lat, lon);
            statusText.setText(String.format("Target saved: %.6f, %.6f", lat, lon));
        } catch (Exception e) {
            showError("Enter a valid latitude (-90..90) and longitude (-180..180).");
        }
    }

    private boolean hasLocationPermissions() {
        return Build.VERSION.SDK_INT < 23 || (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED);
    }

    private void requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 23 && !hasLocationPermissions()) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        }
    }

    private void startMock() {
        if (!hasLocationPermissions()) {
            requestPermissionsIfNeeded();
            showError("Location permission is required for the mock-location service.");
            return;
        }
        try {
            double lat = Double.parseDouble(latInput.getText().toString().trim());
            double lon = Double.parseDouble(lonInput.getText().toString().trim());
            if (lat < -90 || lat > 90 || lon < -180 || lon > 180) throw new NumberFormatException();
            prefs.edit().putFloat("lat", (float) lat).putFloat("lon", (float) lon).apply();

            if (!Settings.canDrawOverlays(this)) {
                new AlertDialog.Builder(this)
                        .setTitle("Floating control")
                        .setMessage("GeoCraft can keep a compact floating control above other apps while mock location is active. Allow the overlay permission for the full cross-app experience.")
                        .setPositiveButton("Open settings", (d, w) -> openOverlaySettings())
                        .setNegativeButton("Start without it", (d, w) -> launchService(lat, lon))
                        .show();
                return;
            }
            launchService(lat, lon);
        } catch (Exception e) {
            showError("Enter a valid latitude and longitude first.");
        }
    }

    private void launchService(double lat, double lon) {
        Intent intent = new Intent(this, MockLocationService.class);
        intent.setAction(MockLocationService.ACTION_START);
        intent.putExtra(MockLocationService.EXTRA_LAT, lat);
        intent.putExtra(MockLocationService.EXTRA_LON, lon);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent); else startService(intent);
        statusText.setText(String.format("Mock location active: %.6f, %.6f", lat, lon));
        Toast.makeText(this, "GeoCraft mock location started", Toast.LENGTH_SHORT).show();
    }

    private void sendUpdate(double lat, double lon) {
        Intent intent = new Intent(this, MockLocationService.class);
        intent.setAction(MockLocationService.ACTION_UPDATE);
        intent.putExtra(MockLocationService.EXTRA_LAT, lat);
        intent.putExtra(MockLocationService.EXTRA_LON, lon);
        try { startService(intent); } catch (Exception ignored) {}
    }

    private void stopMock() {
        Intent intent = new Intent(this, MockLocationService.class);
        intent.setAction(MockLocationService.ACTION_STOP);
        startService(intent);
        statusText.setText("Stopped — real device location is no longer being overridden by GeoCraft.");
        Toast.makeText(this, "GeoCraft stopped", Toast.LENGTH_SHORT).show();
    }

    private void openOverlaySettings() {
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            try { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)); } catch (Exception ignored) {}
        }
    }

    private void openDeveloperOptions() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private void showError(String message) {
        new AlertDialog.Builder(this).setTitle("GeoCraft").setMessage(message).setPositiveButton("OK", null).show();
    }
}
