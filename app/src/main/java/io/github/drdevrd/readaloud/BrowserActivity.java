package io.github.drdevrd.readaloud;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.PermissionRequest;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;

public class BrowserActivity extends Activity {

    private WebView webView;
    private EditText addressBar;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        getWindow().getDecorView().setBackgroundColor(Color.parseColor("#0a0a0f"));

        // Build UI programmatically
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0a0a0f"));

        // Top bar
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setBackgroundColor(Color.parseColor("#13131a"));
        topBar.setPadding(dp(8), dp(8), dp(8), dp(8));

        // Back button
        ImageButton backBtn = new ImageButton(this);
        backBtn.setText("‹");
        backBtn.setTextSize(22);
        backBtn.setBackgroundColor(Color.parseColor("#1c1c26"));
        backBtn.setTextColor(Color.parseColor("#f0f0f4"));
        backBtn.setPadding(dp(12), dp(8), dp(12), dp(8));
        backBtn.setOnClickListener(v -> { if(webView.canGoBack()) webView.goBack(); });

        // Address bar
        addressBar = new EditText(this);
        addressBar.setHint("Enter URL...");
        addressBar.setHintTextColor(Color.parseColor("#8888a0"));
        addressBar.setTextColor(Color.parseColor("#f0f0f4"));
        addressBar.setBackgroundColor(Color.parseColor("#1c1c26"));
        addressBar.setPadding(dp(12), dp(10), dp(12), dp(10));
        addressBar.setImeOptions(EditorInfo.IME_ACTION_GO);
        addressBar.setSingleLine(true);
        addressBar.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_URI | android.text.InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams addrParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        addrParams.setMargins(dp(6), 0, dp(6), 0);
        addressBar.setLayoutParams(addrParams);
        addressBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                navigate(addressBar.getText().toString().trim());
                hideKeyboard();
                return true;
            }
            return false;
        });

        // Go button
        TextView goBtn = new TextView(this);
        goBtn.setText("Go");
        goBtn.setTextColor(Color.parseColor("#0a0a0f"));
        goBtn.setBackgroundColor(Color.parseColor("#6ee7b7"));
        goBtn.setPadding(dp(16), dp(10), dp(16), dp(10));
        goBtn.setOnClickListener(v -> { navigate(addressBar.getText().toString().trim()); hideKeyboard(); });

        topBar.addView(backBtn);
        topBar.addView(addressBar);
        topBar.addView(goBtn);

        // Extract bar
        LinearLayout extractBar = new LinearLayout(this);
        extractBar.setOrientation(LinearLayout.HORIZONTAL);
        extractBar.setBackgroundColor(Color.parseColor("#13131a"));
        extractBar.setPadding(dp(10), dp(6), dp(10), dp(6));

        TextView extractBtn = new TextView(this);
        extractBtn.setText("📖  Extract & Load into ReadAloud");
        extractBtn.setTextColor(Color.parseColor("#0a0a0f"));
        extractBtn.setBackgroundColor(Color.parseColor("#6ee7b7"));
        extractBtn.setPadding(dp(16), dp(10), dp(16), dp(10));
        extractBtn.setTextSize(13);
        extractBtn.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams extParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        extParams.setMargins(0, 0, dp(8), 0);
        extractBtn.setLayoutParams(extParams);
        extractBtn.setOnClickListener(v -> extractContent());

        statusText = new TextView(this);
        statusText.setText("Browse any site");
        statusText.setTextColor(Color.parseColor("#8888a0"));
        statusText.setTextSize(11);
        statusText.setMaxWidth(dp(120));

        extractBar.addView(extractBtn);
        extractBar.addView(statusText);

        // WebView
        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams wvParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        webView.setLayoutParams(wvParams);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUserAgentString("Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                request.grant(request.getResources());
            }
            @Override
            public void onProgressChanged(WebView view, int progress) {
                if(progress < 100) statusText.setText("Loading " + progress + "%...");
                else statusText.setText("✓ Tap Extract to read");
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                view.loadUrl(url);
                addressBar.setText(url);
                return true;
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                addressBar.setText(url);
                statusText.setText("✓ Tap Extract to read");
            }
        });

        root.addView(topBar);
        root.addView(extractBar);
        root.addView(webView);
        setContentView(root);

        // Load URL passed from MainActivity or default
        String url = getIntent().getStringExtra("url");
        if(url != null && !url.isEmpty()) {
            webView.loadUrl(url);
            addressBar.setText(url);
        }
    }

    private void navigate(String input) {
        if(input.isEmpty()) return;
        String url;
        if(input.startsWith("http://") || input.startsWith("https://")) {
            url = input;
        } else if(input.contains(".")) {
            url = "https://" + input;
        } else {
            url = "https://www.google.com/search?q=" + android.net.Uri.encode(input);
        }
        webView.loadUrl(url);
        addressBar.setText(url);
    }

    private void extractContent() {
        statusText.setText("Extracting...");
        // Inject JS to extract article text
        String js = "javascript:(function(){" +
            "var article = document.querySelector('article') || document.querySelector('[role=\"main\"]') || document.querySelector('main') || document.body;" +
            "var clone = article.cloneNode(true);" +
            "var remove = clone.querySelectorAll('script,style,nav,header,footer,aside,iframe,noscript,button,form,input,[class*=\"ad\"],[class*=\"sidebar\"],[class*=\"menu\"],[class*=\"cookie\"],[class*=\"popup\"],[class*=\"share\"],[class*=\"social\"]');" +
            "for(var i=0;i<remove.length;i++) remove[i].parentNode.removeChild(remove[i]);" +
            "var paras = clone.querySelectorAll('p');" +
            "var text = '';" +
            "for(var i=0;i<paras.length;i++){" +
            "  var t = paras[i].innerText.trim();" +
            "  if(t.length > 60) text += t + '\\n\\n';" +
            "}" +
            "if(text.length < 100) text = article.innerText || document.body.innerText;" +
            "var title = document.title || '';" +
            "window.location.href = 'readaloud://extract?title=' + encodeURIComponent(title.substring(0,100)) + '&text=' + encodeURIComponent(text.substring(0,50000));" +
            "})()";
        webView.loadUrl(js);
    }

    @Override
    public void onBackPressed() {
        if(webView.canGoBack()) webView.goBack();
        else finish();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if(imm != null) imm.hideSoftInputFromWindow(addressBar.getWindowToken(), 0);
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    // Fake TextView that acts as a button
    private static class ImageButton extends TextView {
        public ImageButton(Context ctx) { super(ctx); }
    }
}
