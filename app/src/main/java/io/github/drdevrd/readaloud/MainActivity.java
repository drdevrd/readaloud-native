package io.github.drdevrd.readaloud;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.PermissionRequest;
import android.webkit.JavascriptInterface;
import android.view.Window;
import android.view.WindowManager;
import android.graphics.Color;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.speech.tts.UtteranceProgressListener;
import android.widget.Toast;
import android.Manifest;
import android.content.pm.PackageManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {

    private WebView webView;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private static final int BROWSER_REQUEST = 1001;
    private static final int PERMISSION_REQUEST = 1002;

    // For chunked audio saving
    private String pendingFilename = null;
    private OutputStream pendingOutputStream = null;
    private android.net.Uri pendingUri = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        tts = new TextToSpeech(this, this);
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
        webView.addJavascriptInterface(new AppInterface(), "AndroidBridge");
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true;
            tts.setLanguage(Locale.US);
            runOnUiThread(() -> {
                String voicesJson = getVoicesJson();
                webView.loadUrl("javascript:onNativeVoicesReady(" + voicesJson + ")");
            });
        }
    }

    private String getVoicesJson() {
        JSONArray arr = new JSONArray();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Set<Voice> voices = tts.getVoices();
                if (voices != null) {
                    for (Voice v : voices) {
                        if (!v.isNetworkConnectionRequired()) {
                            JSONObject obj = new JSONObject();
                            obj.put("name", v.getName());
                            obj.put("lang", v.getLocale().toLanguageTag());
                            arr.put(obj);
                        }
                    }
                }
            }
            if (arr.length() == 0) {
                JSONObject obj = new JSONObject();
                obj.put("name", "Default");
                obj.put("lang", "en-US");
                arr.put(obj);
            }
        } catch (Exception e) {}
        return arr.toString();
    }

    private class AppInterface {
        @JavascriptInterface
        public void openBrowser(String url) {
            runOnUiThread(() -> {
                Intent intent = new Intent(MainActivity.this, BrowserActivity.class);
                intent.putExtra("url", url != null ? url : "https://www.britannica.com");
                startActivityForResult(intent, BROWSER_REQUEST);
            });
        }

        @JavascriptInterface
        public void speakText(String text, String voiceName, float rate, float pitch) {
            if (!ttsReady) return;
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && voiceName != null && !voiceName.isEmpty()) {
                    Set<Voice> voices = tts.getVoices();
                    if (voices != null) {
                        for (Voice v : voices) {
                            if (v.getName().equals(voiceName)) { tts.setVoice(v); break; }
                        }
                    }
                }
                tts.setSpeechRate(rate);
                tts.setPitch(pitch);
                String uttId = "RA_" + System.currentTimeMillis();
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) {}
                    @Override public void onDone(String id) {
                        runOnUiThread(() -> webView.loadUrl("javascript:onNativeTTSDone()"));
                    }
                    @Override public void onError(String id) {
                        runOnUiThread(() -> webView.loadUrl("javascript:onNativeTTSError()"));
                    }
                });
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, uttId);
                } else {
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
                }
            });
        }

        @JavascriptInterface
        public void stopSpeaking() {
            runOnUiThread(() -> { if (ttsReady) tts.stop(); });
        }

        // Called once to start a new audio file
        @JavascriptInterface
        public void startAudioFile(String filename) {
            runOnUiThread(() -> {
                try {
                    pendingFilename = filename;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        android.content.ContentValues values = new android.content.ContentValues();
                        values.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, filename);
                        values.put(android.provider.MediaStore.Downloads.MIME_TYPE, "audio/mpeg");
                        values.put(android.provider.MediaStore.Downloads.IS_PENDING, 1);
                        pendingUri = getContentResolver().insert(
                            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                        if (pendingUri != null) {
                            pendingOutputStream = getContentResolver().openOutputStream(pendingUri);
                        }
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST);
                            return;
                        }
                        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                        if (!dir.exists()) dir.mkdirs();
                        File file = new File(dir, filename);
                        pendingOutputStream = new FileOutputStream(file);
                    }
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Error starting file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Called for each audio chunk
        @JavascriptInterface
        public void appendAudioChunk(String base64Data) {
            try {
                if (pendingOutputStream != null && base64Data != null && !base64Data.isEmpty()) {
                    byte[] data = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT);
                    pendingOutputStream.write(data);
                }
            } catch (Exception e) {}
        }

        // Called when all chunks sent
        @JavascriptInterface
        public void finishAudioFile() {
            runOnUiThread(() -> {
                try {
                    if (pendingOutputStream != null) {
                        pendingOutputStream.flush();
                        pendingOutputStream.close();
                        pendingOutputStream = null;
                    }
                    if (pendingUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        android.content.ContentValues values = new android.content.ContentValues();
                        values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0);
                        getContentResolver().update(pendingUri, values, null, null);
                        pendingUri = null;
                    }
                    Toast.makeText(MainActivity.this, "✓ Saved to Downloads: " + pendingFilename, Toast.LENGTH_LONG).show();
                    webView.loadUrl("javascript:ss('✓ Saved to Downloads: " + pendingFilename + "')");
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Save error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
                pendingFilename = null;
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == BROWSER_REQUEST && resultCode == RESULT_OK && data != null) {
            String text = data.getStringExtra("text");
            String title = data.getStringExtra("title");
            if (text != null && !text.isEmpty()) {
                String escaped = text.replace("\\","\\\\").replace("'","\\'").replace("\n","\\n").replace("\r","");
                String titleEsc = (title != null ? title : "Article").replace("'","\\'");
                webView.loadUrl("javascript:sendToReader('" + escaped + "','" + titleEsc + "')");
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }

    @Override protected void onPause() { super.onPause(); webView.onPause(); }
    @Override protected void onResume() { super.onResume(); webView.onResume(); }
}
