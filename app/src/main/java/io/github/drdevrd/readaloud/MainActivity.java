package io.github.drdevrd.readaloud;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.PermissionRequest;
import android.view.Window;
import android.view.WindowManager;
import android.graphics.Color;

public class MainActivity extends Activity {

    private WebView webView;
    private static final int BROWSER_REQUEST = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        webView = new WebView(this);
        webView.setBackgroundColor(Color.parseColor("#0a0a0f"));
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                request.grant(request.getResources());
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    Intent intent = new Intent(MainActivity.this, BrowserActivity.class);
                    intent.putExtra("url", url);
                    startActivityForResult(intent, BROWSER_REQUEST);
                    return true;
                }
                view.loadUrl(url);
                return true;
            }
        });

        webView.addJavascriptInterface(new Object() {
            @android.webkit.JavascriptInterface
            public void openBrowser(String url) {
                runOnUiThread(() -> {
                    Intent intent = new Intent(MainActivity.this, BrowserActivity.class);
                    intent.putExtra("url", url != null ? url : "https://www.britannica.com");
                    startActivityForResult(intent, BROWSER_REQUEST);
                });
            }
        }, "AndroidBridge");

        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == BROWSER_REQUEST && resultCode == RESULT_OK && data != null) {
            String text = data.getStringExtra("text");
            String title = data.getStringExtra("title");
            if (text != null && !text.isEmpty()) {
                String escaped = text.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "");
                String titleEsc = (title != null ? title : "Article").replace("'", "\\'");
                webView.loadUrl("javascript:sendToReader('" + escaped + "','" + titleEsc + "')");
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onPause() { super.onPause(); webView.onPause(); }
    @Override protected void onResume() { super.onResume(); webView.onResume(); }
}
