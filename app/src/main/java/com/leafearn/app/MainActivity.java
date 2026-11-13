package com.leafearn.app;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
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

public class MainActivity extends AppCompatActivity implements IUnityAdsInitializationListener {

    private WebView webView;
    private static final String UNITY_GAME_ID = "800437007";
    private static final String DEFAULT_PLACEMENT_ID = "BP_Rewarded_Android";
    private static final boolean TEST_MODE = true; // Set to false for production live ads
    private boolean isAdLoaded = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(Color.parseColor("#F0FDF4"));
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);

        webView.addJavascriptInterface(new UnityAdsBridge(), "AndroidUnityAds");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("https://leaf-earn.hatchable.site") ||
                    url.contains("timewall.io") ||
                    url.contains("cpx-research.com") ||
                    url.contains("cpalead.com")) {
                    return false;
                }
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient());

        // Initialize Unity Ads SDK
        UnityAds.initialize(getApplicationContext(), UNITY_GAME_ID, TEST_MODE, this);

        // Load Leaf Earn Web App
        webView.loadUrl("https://leaf-earn.hatchable.site");
    }

    @Override
    public void onInitializationComplete() {
        loadRewardedAd(DEFAULT_PLACEMENT_ID);
    }

    @Override
    public void onInitializationFailed(UnityAds.UnityAdsInitializationError error, String message) {
        isAdLoaded = false;
    }

    private void loadRewardedAd(String placementId) {
        UnityAds.load(placementId, new IUnityAdsLoadListener() {
            @Override
            public void onUnityAdsAdLoaded(String id) {
                isAdLoaded = true;
            }

            @Override
            public void onUnityAdsFailedToLoad(String id, UnityAds.UnityAdsLoadError error, String message) {
                isAdLoaded = false;
            }
        });
    }

    public class UnityAdsBridge {
        @JavascriptInterface
        public void showRewarded(final String placementId) {
            final String activePlacement = (placementId != null && !placementId.isEmpty()) ? placementId : DEFAULT_PLACEMENT_ID;
            runOnUiThread(() -> {
                if (isAdLoaded) {
                    UnityAds.show(MainActivity.this, activePlacement, new UnityAdsShowOptions(), new IUnityAdsShowListener() {
                        @Override
                        public void onUnityAdsShowFailure(String id, UnityAds.UnityAdsShowError error, String message) {
                            isAdLoaded = false;
                            loadRewardedAd(activePlacement);
                            notifyWebFailure("Ad failed to show: " + message);
                        }

                        @Override
                        public void onUnityAdsShowStart(String id) {}

                        @Override
                        public void onUnityAdsShowClick(String id) {}

                        @Override
                        public void onUnityAdsShowComplete(String id, UnityAds.UnityAdsShowCompletionState state) {
                            isAdLoaded = false;
                            loadRewardedAd(activePlacement);
                            if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                                notifyWebComplete();
                            } else {
                                notifyWebFailure("Ad skipped before completion");
                            }
                        }
                    });
                } else {
                    loadRewardedAd(activePlacement);
                    Toast.makeText(MainActivity.this, "Loading ad, please try again in 3 seconds...", Toast.LENGTH_SHORT).show();
                    notifyWebFailure("Ad not ready yet");
                }
            });
        }
    }

    private void notifyWebComplete() {
        webView.post(() -> webView.evaluateJavascript("if(window.onUnityRewardedComplete){window.onUnityRewardedComplete();}", null));
    }

    private void notifyWebFailure(String reason) {
        webView.post(() -> webView.evaluateJavascript("if(window.onUnityRewardedFailed){window.onUnityRewardedFailed('" + reason.replace("'", "") + "');}", null));
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
