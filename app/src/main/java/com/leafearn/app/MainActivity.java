package com.leafearn.app;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
    private static final String PRIMARY_PLACEMENT = "BP_Rewarded_Android";
    private static final String FALLBACK_PLACEMENT = "Rewarded_Android";
    private static final boolean TEST_MODE = true;

    private String loadedPlacement = null;
    private String lastLoadError = "Initializing Unity SDK...";
    private final Handler handler = new Handler(Looper.getMainLooper());

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
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Remove "Built on Hatchable" floating badge inside APK
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
            }

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
        lastLoadError = "Loading ad...";
        loadRewardedAd(PRIMARY_PLACEMENT);
    }

    @Override
    public void onInitializationFailed(UnityAds.UnityAdsInitializationError error, String message) {
        lastLoadError = "Init failed: " + error.name();
        handler.postDelayed(() -> UnityAds.initialize(getApplicationContext(), UNITY_GAME_ID, TEST_MODE, this), 5000);
    }

    private void loadRewardedAd(final String placementId) {
        UnityAds.load(placementId, new IUnityAdsLoadListener() {
            @Override
            public void onUnityAdsAdLoaded(String id) {
                loadedPlacement = id;
                lastLoadError = "";
            }

            @Override
            public void onUnityAdsFailedToLoad(String id, UnityAds.UnityAdsLoadError error, String message) {
                lastLoadError = error.name() + ": " + message;
                if (PRIMARY_PLACEMENT.equals(id)) {
                    // Try default Rewarded_Android placement
                    loadRewardedAd(FALLBACK_PLACEMENT);
                } else {
                    // Retry after 5 seconds
                    handler.postDelayed(() -> loadRewardedAd(PRIMARY_PLACEMENT), 5000);
                }
            }
        });
    }

    public class UnityAdsBridge {
        @JavascriptInterface
        public void showRewarded(final String placementId) {
            runOnUiThread(() -> {
                if (loadedPlacement != null) {
                    final String adToShow = loadedPlacement;
                    loadedPlacement = null;
                    UnityAds.show(MainActivity.this, adToShow, new UnityAdsShowOptions(), new IUnityAdsShowListener() {
                        @Override
                        public void onUnityAdsShowFailure(String id, UnityAds.UnityAdsShowError error, String message) {
                            loadRewardedAd(PRIMARY_PLACEMENT);
                            notifyWebFailure("Show failed: " + error.name());
                        }

                        @Override
                        public void onUnityAdsShowStart(String id) {}

                        @Override
                        public void onUnityAdsShowClick(String id) {}

                        @Override
                        public void onUnityAdsShowComplete(String id, UnityAds.UnityAdsShowCompletionState state) {
                            loadRewardedAd(PRIMARY_PLACEMENT);
                            if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                                notifyWebComplete();
                            } else {
                                notifyWebFailure("Ad skipped before completion");
                            }
                        }
                    });
                } else {
                    loadRewardedAd(PRIMARY_PLACEMENT);
                    Toast.makeText(MainActivity.this, "Unity Ad: " + lastLoadError, Toast.LENGTH_LONG).show();
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
