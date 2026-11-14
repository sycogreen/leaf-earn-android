
package com.leafearn.app;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.unity3d.ads.IUnityAdsInitializationListener;
import com.unity3d.ads.IUnityAdsLoadListener;
import com.unity3d.ads.IUnityAdsShowListener;
import com.unity3d.ads.UnityAds;
import com.unity3d.ads.UnityAdsShowOptions;

import org.json.JSONObject;

public class MainActivity extends AppCompatActivity
        implements IUnityAdsInitializationListener {

    private static final String TAG = "LeafEarn";

    private static final String APP_URL =
            "https://leaf-earn.hatchable.site";

    private static final String UNITY_GAME_ID =
            "800437007";

    // Must exactly match the Android Ad Unit ID in Unity Dashboard.
    private static final String PLACEMENT_ID =
            "BP_Rewarded_Android";

    // Keep true while testing. Change to false for production
    // after verifying the integration and required ad settings.
    private static final boolean TEST_MODE = true;

    private WebView webView;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private boolean sdkInitialized = false;
    private boolean isAdLoaded = false;
    private boolean isAdLoading = false;
    private boolean isAdShowing = false;
    private boolean activityDestroyed = false;

    private String lastLoadError = "Unity SDK initializing...";

    private final Runnable retryLoadRunnable =
            this::loadRewardedAd;

    private final Runnable retryInitRunnable =
            () -> UnityAds.initialize(
                    MainActivity.this,
                    UNITY_GAME_ID,
                    TEST_MODE,
                    MainActivity.this
            );

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        activityDestroyed = false;

        Window window = getWindow();
        window.setStatusBarColor(
                Color.parseColor("#F0FDF4")
        );

        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        );

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);

        webView.addJavascriptInterface(
                new UnityAdsBridge(),
                "AndroidUnityAds"
        );

        webView.setWebChromeClient(new WebChromeClient());

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageFinished(
                    WebView view,
                    String url
            ) {
                super.onPageFinished(view, url);
                hideHatchableBadge(view);
            }

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    WebResourceRequest request
            ) {
                return handleUrl(request.getUrl());
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    String url
            ) {
                return handleUrl(Uri.parse(url));
            }
        });

        // Initialize Unity Ads using the current Activity.
        UnityAds.initialize(
                this,
                UNITY_GAME_ID,
                TEST_MODE,
                this
        );

        // Load Leaf Earn.
        webView.loadUrl(APP_URL);
    }

    private boolean handleUrl(Uri uri) {

        if (uri == null) {
            return false;
        }

        String host = uri.getHost();

        if (host == null) {
            return false;
        }

        // Keep the app and offerwall inside WebView.
        if (host.equals("leaf-earn.hatchable.site")
                || host.equals("appstorevault.mobi")
                || host.endsWith(".appstorevault.mobi")
                || host.equals("cpalead.com")
                || host.endsWith(".cpalead.com")
                || host.equals("cpx-research.com")
                || host.endsWith(".cpx-research.com")
                || host.equals("timewall.io")
                || host.endsWith(".timewall.io")) {
            return false;
        }

        // Open other external websites in the device browser.
        try {
            Intent intent = new Intent(
                    Intent.ACTION_VIEW,
                    uri
            );

            startActivity(intent);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "External URL error", e);
            return false;
        }
    }

    private void hideHatchableBadge(WebView view) {

        String js =
                "(function() {" +
                " const hide = function() {" +
                "  document.querySelectorAll('a,div,button,span')" +
                "   .forEach(function(el) {" +
                "    if (el.innerText &&" +
                "        el.innerText.trim() === 'Built on Hatchable') {" +
                "      const target = el.closest('a') || el;" +
                "      target.style.setProperty(" +
                "        'display','none','important');" +
                "    }" +
                "   });" +
                " };" +
                " hide();" +
                " const observer = new MutationObserver(hide);" +
                " observer.observe(document.body, {" +
                "  childList:true,subtree:true" +
                " });" +
                "})();";

        view.evaluateJavascript(js, null);
    }

    // ------------------------------------------------
    // UNITY INITIALIZATION
    // ------------------------------------------------

    @Override
    public void onInitializationComplete() {

        runOnUiThread(() -> {

            if (activityDestroyed) return;

            sdkInitialized = true;
            lastLoadError = "Loading rewarded ad...";

            Log.d(TAG, "Unity SDK initialized successfully");

            loadRewardedAd();
        });
    }

    @Override
    public void onInitializationFailed(
            UnityAds.UnityAdsInitializationError error,
            String message
    ) {

        runOnUiThread(() -> {

            sdkInitialized = false;
            isAdLoaded = false;

            lastLoadError =
                    "Initialization failed: "
                    + error.name()
                    + " - "
                    + message;

            Log.e(TAG, lastLoadError);

            handler.removeCallbacks(retryInitRunnable);

            if (!activityDestroyed) {
                handler.postDelayed(
                        retryInitRunnable,
                        10000
                );
            }
        });
    }

    // ------------------------------------------------
    // LOAD REWARDED AD
    // ------------------------------------------------

    private void loadRewardedAd() {

        if (activityDestroyed) return;

        if (!sdkInitialized) {
            lastLoadError = "Unity SDK is not initialized";
            return;
        }

        if (isAdLoaded || isAdLoading || isAdShowing) {
            return;
        }

        isAdLoading = true;
        lastLoadError = "Loading rewarded ad...";

        Log.d(TAG, "Loading Ad Unit: " + PLACEMENT_ID);

        UnityAds.load(
                PLACEMENT_ID,
                new IUnityAdsLoadListener() {

                    @Override
                    public void onUnityAdsAdLoaded(String id) {

                        runOnUiThread(() -> {

                            isAdLoading = false;

                            if (activityDestroyed) return;

                            if (!PLACEMENT_ID.equals(id)) {
                                lastLoadError =
                                        "Unexpected loaded Ad Unit: " + id;
                                isAdLoaded = false;
                                return;
                            }

                            isAdLoaded = true;
                            lastLoadError = "";

                            Log.d(TAG, "Rewarded ad loaded");
                        });
                    }

                    @Override
                    public void onUnityAdsFailedToLoad(
                            String id,
                            UnityAds.UnityAdsLoadError error,
                            String message
                    ) {

                        runOnUiThread(() -> {

                            isAdLoading = false;
                            isAdLoaded = false;

                            lastLoadError =
                                    error.name() + ": " + message;

                            Log.e(
                                    TAG,
                                    "Ad load failed for "
                                            + id
                                            + ": "
                                            + lastLoadError
                            );

                            handler.removeCallbacks(
                                    retryLoadRunnable
                            );

                            if (!activityDestroyed) {
                                handler.postDelayed(
                                        retryLoadRunnable,
                                        10000
                                );
                            }
                        });
                    }
                }
        );
    }

    // ------------------------------------------------
    // JAVASCRIPT BRIDGE
    // ------------------------------------------------

    public class UnityAdsBridge {

        @JavascriptInterface
        public void showRewarded(
                final String requestedPlacementId
        ) {

            runOnUiThread(() -> {

                if (activityDestroyed) return;

                // Validate the requested Ad Unit.
                if (!PLACEMENT_ID.equals(requestedPlacementId)) {

                    notifyWebFailure(
                            "Unity placement ID mismatch"
                    );

                    Log.e(
                            TAG,
                            "Requested placement: "
                                    + requestedPlacementId
                    );

                    return;
                }

                if (!sdkInitialized) {

                    notifyWebFailure(
                            "Unity SDK is not initialized yet"
                    );

                    return;
                }

                if (isAdShowing) {

                    notifyWebFailure(
                            "An ad is already showing"
                    );

                    return;
                }

                if (!isAdLoaded) {

                    loadRewardedAd();

                    notifyWebFailure(
                            "Rewarded ad is not ready. Please try again shortly."
                    );

                    Toast.makeText(
                            MainActivity.this,
                            "Ad is loading. Please try again.",
                            Toast.LENGTH_SHORT
                    ).show();

                    return;
                }

                showLoadedAd();
            });
        }
    }

    // ------------------------------------------------
    // SHOW REWARDED AD
    // ------------------------------------------------

    private void showLoadedAd() {

        if (activityDestroyed || !isAdLoaded || isAdShowing) {
            return;
        }

        isAdLoaded = false;
        isAdShowing = true;

        Log.d(TAG, "Showing rewarded ad");

        UnityAds.show(
                MainActivity.this,
                PLACEMENT_ID,
                new UnityAdsShowOptions(),
                new IUnityAdsShowListener() {

                    @Override
                    public void onUnityAdsShowFailure(
                            String id,
                            UnityAds.UnityAdsShowError error,
                            String message
                    ) {

                        runOnUiThread(() -> {

                            isAdShowing = false;
                            isAdLoaded = false;

                            String reason =
                                    "Show failed: "
                                    + error.name()
                                    + " - "
                                    + message;

                            Log.e(TAG, reason);

                            notifyWebFailure(reason);

                            loadRewardedAd();
                        });
                    }

                    @Override
                    public void onUnityAdsShowStart(String id) {

                        Log.d(TAG, "Rewarded ad started");
                    }

                    @Override
                    public void onUnityAdsShowClick(String id) {

                        Log.d(TAG, "Rewarded ad clicked");
                    }

                    @Override
                    public void onUnityAdsShowComplete(
                            String id,
                            UnityAds.UnityAdsShowCompletionState state
                    ) {

                        runOnUiThread(() -> {

                            isAdShowing = false;

                            Log.d(
                                    TAG,
                                    "Ad completed with state: "
                                            + state.name()
                            );

                            if (state ==
                                    UnityAds.UnityAdsShowCompletionState.COMPLETED) {

                                // Notify web app only after completion.
                                notifyWebComplete();

                            } else {

                                notifyWebFailure(
                                        "Ad was not completed"
                                );
                            }

                            // Preload the next rewarded ad.
                            loadRewardedAd();
                        });
                    }
                }
        );
    }

    // ------------------------------------------------
    // WEB CALLBACKS
    // ------------------------------------------------

    private void notifyWebComplete() {

        if (webView == null || activityDestroyed) return;

        webView.post(() -> {

            if (webView == null || activityDestroyed) return;

            webView.evaluateJavascript(
                    "if (typeof window.onUnityRewardedComplete === 'function') {" +
                            "window.onUnityRewardedComplete();" +
                            "}",
                    null
            );
        });
    }

    private void notifyWebFailure(String reason) {

        if (webView == null || activityDestroyed) return;

        final String safeReason =
                JSONObject.quote(
                        reason == null
                                ? "Unknown Unity Ads error"
                                : reason
                );

        webView.post(() -> {

            if (webView == null || activityDestroyed) return;

            String js =
                    "if (typeof window.onUnityRewardedFailed === 'function') {" +
                    "window.onUnityRewardedFailed(" +
                    safeReason +
                    ");" +
                    "}";

            webView.evaluateJavascript(js, null);
        });
    }

    // ------------------------------------------------
    // ACTIVITY LIFECYCLE
    // ------------------------------------------------

    @Override
    protected void onResume() {
        super.onResume();

        if (webView != null) {
            webView.onResume();
        }

        if (sdkInitialized && !isAdLoaded && !isAdLoading && !isAdShowing) {
            loadRewardedAd();
        }
    }

    @Override
    protected void onPause() {

        if (webView != null) {
            webView.onPause();
        }

        super.onPause();
    }

    @Override
    protected void onDestroy() {

        activityDestroyed = true;

        handler.removeCallbacksAndMessages(null);

        if (webView != null) {

            webView.removeJavascriptInterface(
                    "AndroidUnityAds"
            );

            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.removeAllViews();
            webView.destroy();

            webView = null;
        }

        super.onDestroy();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {

        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
            }
                    
