
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

    private static final String TAG = "LeafEarnUnity";

    private static final String GAME_ID = "800437007";
    private static final String DEFAULT_PLACEMENT =
            "BP_Rewarded_Android";

    // Development testing only. Set false for production release.
    private static final boolean TEST_MODE = true;

    private WebView webView;
    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private boolean initialized = false;
    private boolean loading = false;
    private boolean showing = false;
    private boolean adLoaded = false;

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

        webView.setWebChromeClient(new WebChromeClient());

        webView.setWebViewClient(new WebViewClient() {
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

        webView.loadUrl(
                "https://leaf-earn.hatchable.site"
        );
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
                                        
