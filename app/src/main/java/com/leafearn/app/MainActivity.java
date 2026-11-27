
package com.leafearn.app;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.messaging.FirebaseMessaging;
import com.unity3d.ads.IUnityAdsInitializationListener;
import com.unity3d.ads.IUnityAdsLoadListener;
import com.unity3d.ads.IUnityAdsShowListener;
import com.unity3d.ads.UnityAds;
import com.unity3d.ads.UnityAdsShowOptions;

import org.json.JSONObject;

public class MainActivity extends AppCompatActivity
        implements IUnityAdsInitializationListener {

    private static final String TAG = "LeafEarnUnity";

    private static final String GAME_ID = "800437007";
    private static final String DEFAULT_PLACEMENT = "BP_Rewarded_Android";

    private static final String CHANNEL_ID = "leaf_earn_alerts";
    private static final int NOTIFICATION_PERMISSION_REQUEST = 1001;

    private static final boolean TEST_MODE = false;

    private WebView webView;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private boolean initialized = false;
    private boolean loading = false;
    private boolean showing = false;
    private boolean adLoaded = false;
    private boolean fcmTokenDialogShown = false;

    private String activePlacement = DEFAULT_PLACEMENT;
    private String lastError = "Unity SDK initializing...";

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(Color.parseColor("#F0FDF4"));
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        );

        createNotificationChannel();
        requestNotificationPermission();

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);

        webView.addJavascriptInterface(
                new UnityAdsBridge(),
                "AndroidUnityAds"
        );

        webView.addJavascriptInterface(
                new AppNotificationBridge(),
                "Android"
        );

        webView.setWebChromeClient(new WebChromeClient());

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                String hideBadgeJs =
                        "(function() {" +
                        "  function cleanBadge() {" +
                        "    document.querySelectorAll('a, div, button, span').forEach(function(el) {" +
                        "      if (el.innerText && el.innerText.trim() === 'Built on Hatchable') {" +
                        "        var target = el.closest('a') || el.closest('div[style*=\"fixed\"]') || el;" +
                        "        target.style.setProperty('display', 'none', 'important');" +
                        "      }" +
                        "    });" +
                        "  }" +
                        "  cleanBadge();" +
                        "  setInterval(cleanBadge, 500);" +
                        "})();";

                view.evaluateJavascript(hideBadgeJs, null);

                // Automatically fetch the FCM token after the page loads.
                fetchAndShowFcmToken();
            }

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view, String url) {

                if (url.startsWith(
                        "https://leaf-earn.hatchable.site")) {
                    return false;
                }

                try {
                    Intent intent = new Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(url)
                    );
                    startActivity(intent);
                    return true;

                } catch (Exception e) {
                    Log.e(TAG, "External URL error", e);
                    return true;
                }
            }
        });

        UnityAds.initialize(
                this,
                GAME_ID,
                TEST_MODE,
                this
        );

        webView.loadUrl("https://leaf-earn.hatchable.site");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm =
                    (NotificationManager) getSystemService(
                            Context.NOTIFICATION_SERVICE
                    );

            if (nm != null) {
                NotificationChannel channel =
                        new NotificationChannel(
                                CHANNEL_ID,
                                "Leaf Earn Alerts",
                                NotificationManager.IMPORTANCE_HIGH
                        );

                channel.setDescription(
                        "Important updates from Leaf Earn"
                );

                nm.createNotificationChannel(channel);
            }
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(
                    android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                        new String[]{
                                android.Manifest.permission.POST_NOTIFICATIONS
                        },
                        NOTIFICATION_PERMISSION_REQUEST
                );
            }
        }
    }

    // Fetch FCM token and display it in a copyable dialog.
    private void fetchAndShowFcmToken() {

        FirebaseMessaging.getInstance()
                .getToken()
                .addOnCompleteListener(task -> {

                    if (!task.isSuccessful()) {
                        Log.e(
                                TAG,
                                "FCM token retrieval failed",
                                task.getException()
                        );
                        return;
                    }

                    String token = task.getResult();

                    if (token == null || token.trim().isEmpty()) {
                        Log.e(TAG, "FCM token is empty");
                        return;
                    }

                    Log.d(TAG, "FCM token generated successfully");

                    // Send token to the web app callback if it exists.
                    if (webView != null) {
                        String safeToken = JSONObject.quote(token);

                        webView.post(() ->
                                webView.evaluateJavascript(
                                        "if(window.onLeafEarnFcmToken){" +
                                        "window.onLeafEarnFcmToken(" +
                                        safeToken +
                                        ");}",
                                        null
                                )
                        );
                    }

                    // Show the token popup only once per app session.
                    runOnUiThread(() -> {
                        if (isFinishing()
                                || isDestroyed()
                                || fcmTokenDialogShown) {
                            return;
                        }

                        fcmTokenDialogShown = true;
                        showFcmTokenDialog(token);
                    });
                });
    }

    private void showFcmTokenDialog(String token) {

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Leaf Earn FCM Token")
                .setMessage(
                        "Your device FCM token is ready.\n\n" +
                        token +
                        "\n\nCopy this token for Firebase test notification."
                )
                .setPositiveButton("Copy Token", (d, which) -> {

                    ClipboardManager clipboard =
                            (ClipboardManager) getSystemService(
                                    Context.CLIPBOARD_SERVICE
                            );

                    if (clipboard != null) {
                        ClipData clip = ClipData.newPlainText(
                                "Leaf Earn FCM Token",
                                token
                        );

                        clipboard.setPrimaryClip(clip);
                    }

                    new AlertDialog.Builder(MainActivity.this)
                            .setMessage("FCM token copied to clipboard.")
                            .setPositiveButton("OK", null)
                            .show();
                })
                .setNegativeButton("Close", null)
                .setCancelable(false)
                .create();

        dialog.show();
    }

    public class AppNotificationBridge {

        @JavascriptInterface
        public void showNotification(
                String title,
                String message,
                String copyText) {

            runOnUiThread(() -> {
                try {
                    Context ctx = MainActivity.this;

                    NotificationManager nm =
                            (NotificationManager)
                                    ctx.getSystemService(
                                            Context.NOTIFICATION_SERVICE
                                    );

                    if (nm == null) return;

                    if (copyText != null
                            && !copyText.trim().isEmpty()) {

                        ClipboardManager clipboard =
                                (ClipboardManager)
                                        ctx.getSystemService(
                                                Context.CLIPBOARD_SERVICE
                                        );

                        if (clipboard != null) {
                            ClipData clip =
                                    ClipData.newPlainText(
                                            "Redeem Code",
                                            copyText.trim()
                                    );

                            clipboard.setPrimaryClip(clip);
                        }
                    }

                    Intent intent =
                            new Intent(ctx, MainActivity.class);

                    intent.setFlags(
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    );

                    int flags =
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                                    ? PendingIntent.FLAG_UPDATE_CURRENT
                                    | PendingIntent.FLAG_IMMUTABLE
                                    : PendingIntent.FLAG_UPDATE_CURRENT;

                    PendingIntent pi =
                            PendingIntent.getActivity(
                                    ctx,
                                    (int) (System.currentTimeMillis() % 100000),
                                    intent,
                                    flags
                            );

                    Notification.Builder builder;

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        builder = new Notification.Builder(
                                ctx,
                                CHANNEL_ID
                        );
                    } else {
                        builder = new Notification.Builder(ctx);
                    }

                    String safeTitle =
                            title != null ? title : "Leaf Earn";

                    String safeMessage =
                            message != null ? message : "";

                    Notification notification =
                            builder
                                    .setSmallIcon(R.drawable.ic_launcher)
                                    .setContentTitle(safeTitle)
                                    .setContentText(safeMessage)
                                    .setStyle(
                                            new Notification.BigTextStyle()
                                                    .bigText(safeMessage)
                                    )
                                    .setAutoCancel(true)
                                    .setContentIntent(pi)
                                    .build();

                    nm.notify(
                            (int) (System.currentTimeMillis() % 100000),
                            notification
                    );

                } catch (Exception e) {
                    Log.e(TAG, "Notification error", e);
                }
            });
        }

        @JavascriptInterface
        public void getFCMToken() {
            fetchAndShowFcmToken();
        }

        @JavascriptInterface
        public void requestNotificationPermission() {
            runOnUiThread(
                    MainActivity.this::requestNotificationPermission
            );
        }
    }

    @Override
    public void onInitializationComplete() {
        initialized = true;
        lastError = "";

        Log.d(TAG, "Unity initialization successful");

        loadRewardedAd(activePlacement);
    }

    @Override
    public void onInitializationFailed(
            UnityAds.UnityAdsInitializationError error,
            String message) {

        initialized = false;

        lastError = "Initialization failed: "
                + error.name() + " - " + message;

        Log.e(TAG, lastError);

        handler.postDelayed(() -> {
            if (!initialized) {
                UnityAds.initialize(
                        this,
                        GAME_ID,
                        TEST_MODE,
                        this
                );
            }
        }, 10000);
    }

    private void loadRewardedAd(String placement) {
        if (!initialized || loading || showing) {
            return;
        }

        if (placement == null || placement.trim().isEmpty()) {
            placement = DEFAULT_PLACEMENT;
        }

        activePlacement = placement.trim();
        loading = true;
        adLoaded = false;

        Log.d(TAG, "Loading placement: " + activePlacement);

        UnityAds.load(
                activePlacement,
                new IUnityAdsLoadListener() {

                    @Override
                    public void onUnityAdsAdLoaded(String id) {
                        loading = false;
                        adLoaded = true;
                        lastError = "";

                        Log.d(TAG, "Ad loaded: " + id);
                    }

                    @Override
                    public void onUnityAdsFailedToLoad(
                            String id,
                            UnityAds.UnityAdsLoadError error,
                            String message) {

                        loading = false;
                        adLoaded = false;

                        lastError = error.name() + ": " + message;

                        Log.e(TAG, "Ad load failed: " + lastError);

                        handler.postDelayed(() -> {
                            if (initialized && !showing && !adLoaded) {
                                loadRewardedAd(activePlacement);
                            }
                        }, 10000);
                    }
                }
        );
    }

    public class UnityAdsBridge {

        @JavascriptInterface
        public void showRewarded(String requestedPlacement) {
            runOnUiThread(() -> {

                String placement =
                        requestedPlacement == null
                                || requestedPlacement.trim().isEmpty()
                                ? DEFAULT_PLACEMENT
                                : requestedPlacement.trim();

                if (!initialized) {
                    notifyWebFailure(
                            "Unity SDK is not initialized yet."
                    );
                    return;
                }

                if (showing) {
                    notifyWebFailure(
                            "An ad is already playing."
                    );
                    return;
                }

                if (!placement.equals(activePlacement)) {
                    adLoaded = false;
                    loadRewardedAd(placement);

                    notifyWebFailure(
                            "Ad is loading. Please try again shortly."
                    );
                    return;
                }

                if (!adLoaded) {
                    loadRewardedAd(placement);

                    notifyWebFailure(
                            "Ad is not ready. " + lastError
                    );
                    return;
                }

                adLoaded = false;
                showing = true;

                Log.d(TAG, "Showing placement: " + placement);

                UnityAds.show(
                        MainActivity.this,
                        placement,
                        new UnityAdsShowOptions(),
                        new IUnityAdsShowListener() {

                            @Override
                            public void onUnityAdsShowStart(String id) {
                                Log.d(TAG, "Ad started: " + id);
                            }

                            @Override
                            public void onUnityAdsShowClick(String id) {
                                Log.d(TAG, "Ad clicked: " + id);
                            }

                            @Override
                            public void onUnityAdsShowFailure(
                                    String id,
                                    UnityAds.UnityAdsShowError error,
                                    String message) {

                                showing = false;

                                String reason =
                                        error.name() + ": " + message;

                                Log.e(TAG, "Show failed: " + reason);

                                notifyWebFailure(reason);
                                loadRewardedAd(activePlacement);
                            }

                            @Override
                            public void onUnityAdsShowComplete(
                                    String id,
                                    UnityAds.UnityAdsShowCompletionState state) {

                                showing = false;

                                if (state ==
                                        UnityAds.UnityAdsShowCompletionState.COMPLET                                )
                                    .setAutoCancel(true)
                                    .setContentIntent(pi)
                                    .build();

                    nm.notify(
                            (int) (System.currentTimeMillis() % 100000),
                            notification
                    );

                } catch (Exception e) {
                    Log.e(TAG, "Notification error", e);
                }
            });
        }

        @JavascriptInterface
        public void getFCMToken() {
            FirebaseMessaging.getInstance()
                    .getToken()
                    .addOnCompleteListener(task -> {

                        if (!task.isSuccessful()) {
                            Log.e(TAG, "FCM token retrieval failed",
                                    task.getException());
                            return;
                        }

                        String token = task.getResult();

                        if (token == null || webView == null) {
                            return;
                        }

                        String safeToken = JSONObject.quote(token);

                        runOnUiThread(() -> {
                            if (webView != null) {
                                webView.evaluateJavascript(
                                        "if(window.onLeafEarnFcmToken){" +
                                                "window.onLeafEarnFcmToken(" +
                                                safeToken +
                                                ");}",
                                        null
                                );
                            }
                        });
                    });
        }

        @JavascriptInterface
        public void requestNotificationPermission() {
            runOnUiThread(
                    MainActivity.this::requestNotificationPermission
            );
        }
    }

    @Override
    public void onInitializationComplete() {
        initialized = true;
        lastError = "";

        Log.d(TAG, "Unity initialization successful");

        loadRewardedAd(activePlacement);
    }

    @Override
    public void onInitializationFailed(
            UnityAds.UnityAdsInitializationError error,
            String message) {

        initialized = false;

        lastError = "Initialization failed: "
                + error.name() + " - " + message;

        Log.e(TAG, lastError);

        handler.postDelayed(() -> {
            if (!initialized) {
                UnityAds.initialize(
                        this,
                        GAME_ID,
                        TEST_MODE,
                        this
                );
            }
        }, 10000);
    }

    private void loadRewardedAd(String placement) {
        if (!initialized || loading || showing) {
            return;
        }

        if (placement == null || placement.trim().isEmpty()) {
            placement = DEFAULT_PLACEMENT;
        }

        activePlacement = placement.trim();
        loading = true;
        adLoaded = false;

        Log.d(TAG, "Loading placement: " + activePlacement);

        UnityAds.load(
                activePlacement,
                new IUnityAdsLoadListener() {

                    @Override
                    public void onUnityAdsAdLoaded(String id) {
                        loading = false;
                        adLoaded = true;
                        lastError = "";

                        Log.d(TAG, "Ad loaded: " + id);
                    }

                    @Override
                    public void onUnityAdsFailedToLoad(
                            String id,
                            UnityAds.UnityAdsLoadError error,
                            String message) {

                        loading = false;
                        adLoaded = false;

                        lastError = error.name() + ": " + message;

                        Log.e(TAG, "Ad load failed: " + lastError);

                        handler.postDelayed(() -> {
                            if (initialized && !showing && !adLoaded) {
                                loadRewardedAd(activePlacement);
                            }
                        }, 10000);
                    }
                }
        );
    }

    public class UnityAdsBridge {

        @JavascriptInterface
        public void showRewarded(String requestedPlacement) {
            runOnUiThread(() -> {

                String placement =
                        requestedPlacement == null
                                || requestedPlacement.trim().isEmpty()
                                ? DEFAULT_PLACEMENT
                                : requestedPlacement.trim();

                if (!initialized) {
                    notifyWebFailure(
                            "Unity SDK is not initialized yet."
                    );
                    return;
                }

                if (showing) {
                    notifyWebFailure(
                            "An ad is already playing."
                    );
                    return;
                }

                if (!placement.equals(activePlacement)) {
                    adLoaded = false;
                    loadRewardedAd(placement);

                    notifyWebFailure(
                            "Ad is loading. Please try again shortly."
                    );
                    return;
                }

                if (!adLoaded) {
                    loadRewardedAd(placement);

                    notifyWebFailure(
                            "Ad is not ready. " + lastError
                    );
                    return;
                }

                adLoaded = false;
                showing = true;

                Log.d(TAG, "Showing placement: " + placement);

                UnityAds.show(
                        MainActivity.this,
                        placement,
                        new UnityAdsShowOptions(),
                        new IUnityAdsShowListener() {

                            @Override
                            public void onUnityAdsShowStart(String id) {
                                Log.d(TAG, "Ad started: " + id);
                            }

                            @Override
                            public void onUnityAdsShowClick(String id) {
                                Log.d(TAG, "Ad clicked: " + id);
                            }

                            @Override
                            public void onUnityAdsShowFailure(
                                    String id,
                                    UnityAds.UnityAdsShowError error,
                                    String message) {

                                showing = false;

                                String reason =
                                        error.name() + ": " + message;

                                Log.e(TAG, "Show failed: " + reason);

                                notifyWebFailure(reason);
                                loadRewardedAd(activePlacement);
                            }

                            @Override
                            public void onUnityAdsShowComplete(
                                    String id,
                                    UnityAds.UnityAdsShowCompletionState state) {

                                showing = false;

                                if (state ==
                                        UnityAds.UnityAdsShowCompletionState.COMPLETED) {

                                    Log.d(TAG, "Ad completed");
                                    notifyWebComplete();

                                } else {
                                    notifyWebFailure(
                                            "Ad was not completed."
                                    );
                                }

                                loadRewardedAd(activePlacement);
                            }
                        }
                );
            });
        }
    }

    private void notifyWebComplete() {
        if (webView == null) return;

        webView.post(() -> webView.evaluateJavascript(
                "if(window.onUnityRewardedComplete)"
                        + "{window.onUnityRewardedComplete();}",
                null
        ));
    }

    private void notifyWebFailure(String reason) {
        if (webView == null) return;

        String safeReason = JSONObject.quote(
                reason == null ? "Unity ad failed." : reason
        );

        webView.post(() -> webView.evaluateJavascript(
                "if(window.onUnityRewardedFailed)"
                        + "{window.onUnityRewardedFailed("
                        + safeReason + ");}",
                null
        ));
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);

        if (webView != null) {
            webView.removeJavascriptInterface("AndroidUnityAds");
            webView.removeJavascriptInterface("Android");
            webView.destroy();
            webView = null;
        }

        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
        }
            
