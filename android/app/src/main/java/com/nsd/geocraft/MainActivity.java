package com.nsd.geocraft;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
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

public class MainActivity extends Activity {
    private static final int REQUEST_LOCATION = 101;
    private static final int REQUEST_NOTIFICATIONS = 102;

    private EditText latInput;
    private EditText lonInput;
    private TextView statusText;
    private SharedPreferences prefs;

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
