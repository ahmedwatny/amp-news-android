package com.arabmediaplatform.news;

import android.app.Activity;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.graphics.Bitmap;
import android.net.Uri;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import androidx.core.content.ContextCompat;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.webkit.JavascriptInterface;
import android.graphics.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final String BASE_URL = "https://news.arabmediaplatform.com/";
    private static final String SITE_HOST = "news.arabmediaplatform.com";
    private WebView webView;
    private ProgressBar progressBar;
    private BroadcastReceiver radioStateReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(155, 20, 32));
        getWindow().setNavigationBarColor(Color.BLACK);

        FrameLayout root = new FrameLayout(this);
        webView = new WebView(this);
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(190, 25, 40)));
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(3));
        root.addView(progressBar, progressParams);
        setContentView(root);
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 4103);
        }

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        webView.addJavascriptInterface(new RadioBridge(), "AMPRadio");
        radioStateReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                boolean playing = intent.getBooleanExtra(RadioPlaybackService.EXTRA_IS_PLAYING, false);
                if (webView != null) webView.evaluateJavascript("if(window.onNativeRadioStateChanged){window.onNativeRadioStateChanged(" + playing + ");}", null);
            }
        };
        ContextCompat.registerReceiver(this, radioStateReceiver,
                new IntentFilter(RadioPlaybackService.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return routeUrl(request.getUrl().toString());
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return routeUrl(url);
            }
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(5);
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setProgress(100);
                progressBar.setVisibility(View.GONE);
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                if (newProgress >= 100) progressBar.setVisibility(View.GONE);
            }
        });

        if (savedInstanceState != null && webView.restoreState(savedInstanceState) != null) {
            // Restored the existing WebView navigation state.
        } else {
            loadHomeAsset();
        }
    }

    private void loadHomeAsset() {
        try (InputStream input = getAssets().open("index.html");
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            String html = new String(output.toByteArray(), StandardCharsets.UTF_8);
            // Using the site as base URL gives the local homepage the site's origin for WP REST API requests.
            webView.loadDataWithBaseURL(BASE_URL, html, "text/html", "UTF-8", BASE_URL);
        } catch (Exception e) {
            webView.loadDataWithBaseURL(BASE_URL,
                    "<html><meta charset='utf-8'><body style='background:#111216;color:white;font-family:sans-serif;padding:24px;direction:rtl'>تعذّر فتح صفحة البداية. حاول إعادة تثبيت التطبيق.</body></html>",
                    "text/html", "UTF-8", BASE_URL);
        }
    }

    public class RadioBridge {
        @JavascriptInterface
        public boolean isPlaying() {
            return getSharedPreferences("amp_radio", MODE_PRIVATE).getBoolean("was_playing", false);
        }
        @JavascriptInterface
        public void play(String streamUrl, int volume) {
            Intent intent = new Intent(MainActivity.this, RadioPlaybackService.class);
            intent.setAction(RadioPlaybackService.ACTION_PLAY);
            intent.putExtra(RadioPlaybackService.EXTRA_URL, streamUrl);
            intent.putExtra(RadioPlaybackService.EXTRA_VOLUME, Math.max(0, Math.min(100, volume)) / 100f);
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
            else startService(intent);
        }
        @JavascriptInterface
        public void stop() {
            Intent intent = new Intent(MainActivity.this, RadioPlaybackService.class);
            intent.setAction(RadioPlaybackService.ACTION_STOP);
            startService(intent);
        }
        @JavascriptInterface
        public void setVolume(int volume) {
            Intent intent = new Intent(MainActivity.this, RadioPlaybackService.class);
            intent.setAction(RadioPlaybackService.ACTION_VOLUME);
            intent.putExtra(RadioPlaybackService.EXTRA_VOLUME, Math.max(0, Math.min(100, volume)) / 100f);
            startService(intent);
        }
    }

    private boolean routeUrl(String rawUrl) {
        try {
            Uri uri = Uri.parse(rawUrl);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
            if ((scheme.equals("https") || scheme.equals("http")) && SITE_HOST.equalsIgnoreCase(uri.getHost())) {
                return false;
            }
            if (scheme.equals("http") || scheme.equals("https") || scheme.equals("mailto") ||
                    scheme.equals("tel") || scheme.equals("whatsapp")) {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
                return true;
            }
        } catch (Exception ignored) {
            // Unsupported links are blocked instead of crashing the app.
        }
        return true;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else loadHomeAsset();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (radioStateReceiver != null) { try { unregisterReceiver(radioStateReceiver); } catch (Exception ignored) {} radioStateReceiver = null; }
        if (webView != null) {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
