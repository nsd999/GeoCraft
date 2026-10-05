package com.nsd.geocraft;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MockLocationService extends Service {
    public static final String ACTION_START = "com.nsd.geocraft.action.START";
    public static final String ACTION_UPDATE = "com.nsd.geocraft.action.UPDATE";
    public static final String ACTION_STOP = "com.nsd.geocraft.action.STOP";
    public static final String EXTRA_LAT = "lat";
    public static final String EXTRA_LON = "lon";

    private static final String CHANNEL_ID = "geocraft_mock_location";
    private static final String[] PROVIDERS = new String[]{
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
            "fused"
    };

    private LocationManager locationManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<String> activeProviders = new ArrayList<>();
    private WindowManager windowManager;
    private View overlay;
    private TextView overlayCoords;
    private boolean running = false;
    private double latitude = 17.3850;
    private double longitude = 78.4867;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            pushLocation();
            handler.postDelayed(this, 1000L);
        }
    };

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopMocking();
            return START_NOT_STICKY;
        }

        if (intent != null && intent.hasExtra(EXTRA_LAT)) latitude = intent.getDoubleExtra(EXTRA_LAT, latitude);
        if (intent != null && intent.hasExtra(EXTRA_LON)) longitude = intent.getDoubleExtra(EXTRA_LON, longitude);

        try {
            startAsForeground();
        } catch (Exception e) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_UPDATE.equals(action) && running) {
            pushLocation();
            updateOverlay();
            updateNotification();
            return START_STICKY;
        }

        if (!running) startMocking();
        return START_STICKY;
    }

    private void startAsForeground() {
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1001, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(1001, notification);
        }
    }

    private void startMocking() {
        try {
            if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                throw new SecurityException("Fine location permission is missing");
            }
            activeProviders.clear();
            for (String provider : PROVIDERS) {
                try {
                    locationManager.addTestProvider(
                            provider,
                            false,
                            provider.equals(LocationManager.GPS_PROVIDER),
                            false,
                            false,
                            true,
                            true,
                            true,
                            Criteria.POWER_LOW,
                            Criteria.ACCURACY_FINE
                    );
                    locationManager.setTestProviderEnabled(provider, true);
                    activeProviders.add(provider);
                } catch (IllegalArgumentException | SecurityException ignored) {
                    // Some devices/providers reject individual test providers; continue with others.
                }
            }

            if (activeProviders.isEmpty()) {
                throw new SecurityException("No test providers could be enabled. GeoCraft is probably not selected as the Mock Location App.");
            }

            running = true;
            pushLocation();
            handler.removeCallbacks(tick);
            handler.post(tick);
            showOverlayIfAllowed();
            updateNotification();
        } catch (SecurityException e) {
            running = false;
            updateNotification("Select GeoCraft in Developer Options → Select mock location app, then try again.");
            handler.removeCallbacks(tick);
        } catch (Exception e) {
            running = false;
            updateNotification("Mock location could not start on this device.");
            handler.removeCallbacks(tick);
        }
    }

    private void pushLocation() {
        if (!running) return;
        long now = System.currentTimeMillis();
        long elapsed = SystemClock.elapsedRealtimeNanos();
        for (String provider : activeProviders) {
            try {
                Location l = new Location(provider);
                l.setLatitude(latitude);
                l.setLongitude(longitude);
                l.setAltitude(0.0);
                l.setAccuracy(1.0f);
                l.setSpeed(0.0f);
                l.setBearing(0.0f);
                l.setTime(now);
                l.setElapsedRealtimeNanos(elapsed);
                locationManager.setTestProviderLocation(provider, l);
            } catch (Exception ignored) {}
        }
        updateOverlay();
    }

    private void stopMocking() {
        running = false;
        handler.removeCallbacks(tick);
        for (String provider : activeProviders) {
            try { locationManager.setTestProviderEnabled(provider, false); } catch (Exception ignored) {}
            try { locationManager.removeTestProvider(provider); } catch (Exception ignored) {}
        }
        activeProviders.clear();
        removeOverlay();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "GeoCraft mock location", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Keeps GeoCraft running while the test location is active.");
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 10, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String text = running ? String.format(Locale.US, "Mock: %.5f, %.5f", latitude, longitude) : "Preparing test location";
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("GeoCraft")
                .setContentText(text)
                .setOngoing(true)
                .setContentIntent(pi)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setShowWhen(false);
        return b.build();
    }

    private void updateNotification() {
        updateNotification(null);
    }

    private void updateNotification(String override) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 10, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String text = override != null ? override : (running
                ? String.format(Locale.US, "Mock: %.5f, %.5f", latitude, longitude)
                : "Ready");
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("GeoCraft")
                .setContentText(text)
                .setOngoing(running)
                .setContentIntent(pi)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setShowWhen(false);
        nm.notify(1001, b.build());
    }

    private void showOverlayIfAllowed() {
        if (!Settings.canDrawOverlays(this)) return;
        if (overlay != null) return;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(8), dp(8), dp(8), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(16, 17, 23));
        bg.setStroke(dp(1), Color.rgb(71, 216, 255));
        bg.setCornerRadius(dp(18));
        root.setBackground(bg);

        ImageView icon = new ImageView(this);
        icon.setImageResource(com.nsd.geocraft.R.drawable.geocraft_logo);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10), dp(7), dp(10), dp(7));
        panel.setVisibility(View.GONE);

        TextView heading = makeText("GeoCraft • Active", 14, Color.WHITE, true);
        panel.addView(heading);
        overlayCoords = makeText("", 11, Color.rgb(158,163,183), false);
        panel.addView(overlayCoords, new LinearLayout.LayoutParams(-1, dp(32)));

        Button stop = new Button(this);
        stop.setText("Stop");
        stop.setAllCaps(false);
        stop.setTextColor(Color.WHITE);
        stop.setTextSize(12);
        stop.setOnClickListener(v -> stopMocking());
        panel.addView(stop, new LinearLayout.LayoutParams(-1, dp(42)));
        root.addView(panel, new LinearLayout.LayoutParams(dp(220), -2));

        icon.setOnClickListener(v -> panel.setVisibility(panel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.x = dp(14);
        lp.y = dp(120);

        try {
            windowManager.addView(root, lp);
            overlay = root;
            attachDrag(icon, lp);
            updateOverlay();
        } catch (Exception ignored) {}
    }

    private TextView makeText(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return t;
    }

    private void attachDrag(View handle, WindowManager.LayoutParams lp) {
        handle.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;
            int startX, startY;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        startX = lp.x;
                        startY = lp.y;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        lp.x = startX + (int)(downX - e.getRawX());
                        lp.y = startY + (int)(e.getRawY() - downY);
                        try { windowManager.updateViewLayout(overlay, lp); } catch (Exception ignored) {}
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (Math.abs(e.getRawX() - downX) < dp(12) && Math.abs(e.getRawY() - downY) < dp(12)) {
                            v.performClick();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void updateOverlay() {
        if (overlayCoords != null && running) {
            overlayCoords.setText(String.format(Locale.US, "%.6f, %.6f", latitude, longitude));
        }
    }

    private void removeOverlay() {
        if (overlay != null) {
            try { windowManager.removeView(overlay); } catch (Exception ignored) {}
            overlay = null;
            overlayCoords = null;
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacks(tick);
        removeOverlay();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
