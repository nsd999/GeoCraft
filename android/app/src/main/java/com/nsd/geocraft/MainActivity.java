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
import android.view.MotionEvent;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int REQUEST_LOCATION = 101;
    private static final int REQUEST_NOTIFICATIONS = 102;

    private EditText latInput;
    private EditText lonInput;
    private TextView statusText;
    private SharedPreferences prefs;
    private WebView mapWebView;
    private EditText placeSearchInput;
    private Button placeSearchButton;
    private TextView updateStatusText;
    private Button updateButton;
    private boolean updateDialogShowing = false;
    private boolean waitingForInstallPermission = false;
    private String pendingApkUrl = "";
    private String pendingVersionName = "";
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
        return styledButton(value, Color.rgb(86, 78, 220), Color.rgb(118, 110, 255));
    }

    private Button secondaryButton(String value) {
        return styledButton(value, Color.rgb(36, 40, 58), Color.rgb(72, 78, 105));
    }

    private Button dangerButton(String value) {
        return styledButton(value, Color.rgb(150, 52, 72), Color.rgb(205, 82, 108));
    }

    private Button styledButton(String value, int fill, int stroke) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setMinHeight(dp(50));
        b.setPadding(dp(16), dp(8), dp(16), dp(8));
        b.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(fill);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), stroke);
        b.setBackground(bg);
        b.setElevation(dp(2));
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

        if (waitingForInstallPermission && Build.VERSION.SDK_INT >= 26
                && getPackageManager().canRequestPackageInstalls()) {
            waitingForInstallPermission = false;
            String url = pendingApkUrl;
            String name = pendingVersionName;
            pendingApkUrl = "";
            pendingVersionName = "";
            if (!url.isEmpty()) downloadAndInstallUpdate(name, url);
        }

        updateHandler.removeCallbacks(updateCheckRunnable);
        updateHandler.postDelayed(updateCheckRunnable, Long.parseLong(UPDATE_INTERVAL_MS));
    }

    @Override
    protected void onPause() {
        updateHandler.removeCallbacks(updateCheckRunnable);
        super.onPause();
    }

    private void checkForUpdates() {
        if (updateStatusText != null) updateStatusText.setText("Checking for the latest version…");
        if (updateButton != null) {
            updateButton.setEnabled(false);
            updateButton.setText("Checking…");
        }

        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(UPDATE_MANIFEST_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setRequestProperty("User-Agent", "GeoCraft-UpdateChecker");
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new Exception("Update server unavailable");
                }

                StringBuilder jsonText = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) jsonText.append(line);
                }

                JSONObject manifest = new JSONObject(jsonText.toString());
                int latestCode = manifest.optInt("versionCode", 0);
                String latestName = manifest.optString("versionName", "");
                String apkUrl = manifest.optString("apk", "");

                runOnUiThread(() -> {
                    if (updateButton != null) {
                        updateButton.setEnabled(true);
                        updateButton.setText("Check for updates");
                    }

                    if (latestCode > getCurrentVersionCode() && !apkUrl.isEmpty()) {
                        if (updateStatusText != null) {
                            updateStatusText.setText("Update available • GeoCraft " + latestName);
                        }
                        showUpdateDialog(latestName, apkUrl);
                    } else {
                        if (updateStatusText != null) {
                            updateStatusText.setText("You're up to date • GeoCraft " + getCurrentVersionName());
                        }
                        Toast.makeText(this, "GeoCraft is already up to date.", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception ignored) {
                runOnUiThread(() -> {
                    if (updateButton != null) {
                        updateButton.setEnabled(true);
                        updateButton.setText("Check for updates");
                    }
                    if (updateStatusText != null) {
                        updateStatusText.setText("Couldn't check right now • Tap to try again");
                    }
                });
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

    private String getCurrentVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "unknown";
        }
    }

    private void showUpdateDialog(String versionName, String apkUrl) {
        if (isFinishing() || updateDialogShowing) return;
        updateDialogShowing = true;

        new AlertDialog.Builder(this)
                .setTitle("🚀 New GeoCraft update")
                .setMessage("GeoCraft " + versionName + " is available.\n\nDownload and install the newest version now. Your saved locations and settings will remain.")
                .setPositiveButton("Download & Install", (d, w) -> {
                    updateDialogShowing = false;
                    downloadAndInstallUpdate(versionName, apkUrl);
                })
                .setNegativeButton("Later", (d, w) -> updateDialogShowing = false)
                .setOnDismissListener(d -> updateDialogShowing = false)
                .setCancelable(true)
                .show();
    }

    private void downloadAndInstallUpdate(String versionName, String apkUrl) {
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            pendingApkUrl = apkUrl;
            pendingVersionName = versionName;
            waitingForInstallPermission = true;

            new AlertDialog.Builder(this)
                    .setTitle("Allow GeoCraft to install updates")
                    .setMessage("Android needs one-time permission for GeoCraft to open downloaded APK updates. Turn on “Allow from this source”, then return to GeoCraft and the update will continue automatically.")
                    .setPositiveButton("Open install permission", (d, w) -> {
                        try {
                            Intent settings = new Intent(
                                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + getPackageName()));
                            startActivity(settings);
                        } catch (Exception e) {
                            startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
                        }
                    })
                    .setNegativeButton("Cancel", (d, w) -> waitingForInstallPermission = false)
                    .show();
            return;
        }

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

        TextView versionLabel = text("Version " + getCurrentVersionName(), 12, Color.rgb(118, 123, 143), false);
        versionLabel.setGravity(Gravity.CENTER);
        root.addView(versionLabel, new LinearLayout.LayoutParams(-1, dp(24)));

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

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);

        placeSearchInput = new EditText(this);
        configureInput(placeSearchInput, "Search a place (e.g. Charminar)", "");
        placeSearchInput.setSingleLine(true);
        placeSearchInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        searchRow.addView(placeSearchInput, weightParams(0, 1));

        Space searchSpace = new Space(this);
        searchRow.addView(searchSpace, new LinearLayout.LayoutParams(dp(8), 1));

        placeSearchButton = secondaryButton("Search");
        placeSearchButton.setTextSize(13);
        placeSearchButton.setOnClickListener(v -> searchPlace());
        searchRow.addView(placeSearchButton, new LinearLayout.LayoutParams(dp(105), dp(52)));

        placeSearchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                searchPlace();
                return true;
            }
            return false;
        });

        mapCard.addView(searchRow, marginParams(-1, dp(52), 0, 0, 0, dp(10)));

        mapWebView = new MapWebView(this);
        mapWebView.setBackgroundColor(Color.rgb(20, 22, 30));
        mapWebView.setVerticalScrollBarEnabled(false);
        mapWebView.setHorizontalScrollBarEnabled(false);
        mapWebView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        mapWebView.setNestedScrollingEnabled(false);
        WebSettings ws = mapWebView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setBuiltInZoomControls(false);
        ws.setDisplayZoomControls(false);
        mapWebView.setWebViewClient(new WebViewClient());
        mapWebView.addJavascriptInterface(new MapBridge(), "Android");
        mapCard.addView(mapWebView, new LinearLayout.LayoutParams(-1, dp(300)));
        Button useMap = secondaryButton("Use selected map location");
        useMap.setOnClickListener(v -> saveInputs());
        mapCard.addView(useMap, marginParams(-1, dp(52), 0, dp(10), 0, 0));
        Button openMap = secondaryButton("Open map in browser");
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
        Button apply = secondaryButton("Apply coordinates");
        apply.setOnClickListener(v -> saveInputs());
        presets.addView(apply);
        root.addView(presets, marginParams(-1, -2, 0, 0, 0, dp(14)));

        LinearLayout updateCard = card();
        TextView updateTitle = text("App updates", 17, Color.WHITE, true);
        updateCard.addView(updateTitle);

        updateStatusText = text("Checking for the latest version…", 12, Color.rgb(158, 163, 183), false);
        updateCard.addView(updateStatusText, marginParams(-1, -2, 0, dp(5), 0, dp(10)));

        updateButton = button("Check for updates");
        updateButton.setOnClickListener(v -> checkForUpdates());
        updateCard.addView(updateButton, new LinearLayout.LayoutParams(-1, dp(52)));
        root.addView(updateCard, marginParams(-1, -2, 0, 0, 0, dp(14)));

        LinearLayout actions = card();
        TextView actionsTitle = text("Controls", 17, Color.WHITE, true);
        actions.addView(actionsTitle);

        Button start = button("▶  Start mock location");
        start.setOnClickListener(v -> startMock());
        actions.addView(start, marginParams(-1, dp(52), 0, dp(10), 0, 0));

        Button stop = dangerButton("Stop mock location");
        stop.setOnClickListener(v -> stopMock());
        actions.addView(stop, marginParams(-1, dp(52), 0, dp(10), 0, 0));

        Button overlay = secondaryButton("Enable / manage floating control");
        overlay.setOnClickListener(v -> openOverlaySettings());
        actions.addView(overlay, marginParams(-1, dp(52), 0, dp(10), 0, 0));

        Button developer = secondaryButton("Open Android Mock Location settings");
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

    private class MapWebView extends WebView {
        public MapWebView(android.content.Context context) {
            super(context);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    getParent().requestDisallowInterceptTouchEvent(true);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    getParent().requestDisallowInterceptTouchEvent(false);
                    break;
            }
            return super.onTouchEvent(event);
        }
    }

    private void searchPlace() {
        String query = placeSearchInput.getText().toString().trim();
        if (query.isEmpty()) {
            showError("Enter a place name, address, landmark or city to search.");
            return;
        }

        placeSearchButton.setEnabled(false);
        placeSearchButton.setText("Searching…");

        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString());
                URL url = new URL("https://nominatim.openstreetmap.org/search?format=jsonv2&limit=5&q=" + encoded);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setRequestProperty("User-Agent", "GeoCraft/1.0 (com.nsd.geocraft)");

                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new Exception("Search service unavailable");
                }

                StringBuilder body = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) body.append(line);
                }

                org.json.JSONArray results = new org.json.JSONArray(body.toString());
                runOnUiThread(() -> {
                    placeSearchButton.setEnabled(true);
                    placeSearchButton.setText("Search");

                    if (results.length() == 0) {
                        showError("No places found. Try a more specific place name or city.");
                        return;
                    }

                    showSearchResults(results);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    placeSearchButton.setEnabled(true);
                    placeSearchButton.setText("Search");
                    showError("Couldn't search right now. Check your internet connection and try again.");
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private void showSearchResults(org.json.JSONArray results) {
        String[] labels = new String[results.length()];
        for (int i = 0; i < results.length(); i++) {
            try {
                labels[i] = results.getJSONObject(i).optString("display_name", "Result " + (i + 1));
            } catch (Exception e) {
                labels[i] = "Result " + (i + 1);
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Choose a place")
                .setItems(labels, (dialog, which) -> {
                    try {
                        org.json.JSONObject result = results.getJSONObject(which);
                        double lat = Double.parseDouble(result.getString("lat"));
                        double lon = Double.parseDouble(result.getString("lon"));
                        String name = result.optString("display_name", "Selected place");

                        latInput.setText(String.format(java.util.Locale.US, "%.6f", lat));
                        lonInput.setText(String.format(java.util.Locale.US, "%.6f", lon));
                        prefs.edit().putFloat("lat", (float) lat).putFloat("lon", (float) lon).apply();

                        mapWebView.evaluateJavascript(
                                "setLocationFromSearch(" + lat + "," + lon + ");", null);
                        statusText.setText(String.format(java.util.Locale.US,
                                "Place selected: %.6f, %.6f", lat, lon));
                        Toast.makeText(this, name, Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        showError("Could not use that search result.");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void loadMap() {
        String html = "<!doctype html><html><head>" +
                "<meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no'>" +
                "<link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'>" +
                "<style>html,body,#map{height:100%;margin:0;background:#14161e;overflow:hidden;touch-action:none;-webkit-user-select:none;user-select:none} #map{touch-action:none} .leaflet-control-attribution{font-size:9px}</style>" +
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
