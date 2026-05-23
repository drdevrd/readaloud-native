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
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {

    private WebView webView;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private static final int BROWSER_REQUEST = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        // Init TTS
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

    // TTS Init callback
    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true;
            tts.setLanguage(Locale.US);
            // Send voice list to JS
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
                        JSONObject obj = new JSONObject();
                        obj.put("name", v.getName());
                        obj.put("lang", v.getLocale().toLanguageTag());
                        arr.put(obj);
                    }
                }
            } else {
                // Fallback for older Android
                JSONObject obj = new JSONObject();
                obj.put("name", "Default Voice");
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
                // Set voice
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && voiceName != null) {
                    Set<Voice> voices = tts.getVoices();
                    if (voices != null) {
                        for (Voice v : voices) {
                            if (v.getName().equals(voiceName)) {
                                tts.setVoice(v);
                                break;
                            }
                        }
                    }
                }
                tts.setSpeechRate(rate);
                tts.setPitch(pitch);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "RA_" + System.currentTimeMillis());
                } else {
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
                }
                // Notify JS when done
                tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                    @Override public void onStart(String id) {}
                    @Override public void onDone(String id) {
                        runOnUiThread(() -> webView.loadUrl("javascript:onNativeTTSDone()"));
                    }
                    @Override public void onError(String id) {
                        runOnUiThread(() -> webView.loadUrl("javascript:onNativeTTSError()"));
                    }
                });
            });
        }

        @JavascriptInterface
        public void stopSpeaking() {
            runOnUiThread(() -> { if(ttsReady) tts.stop(); });
        }

        @JavascriptInterface
        public void pauseSpeaking() {
            runOnUiThread(() -> { if(ttsReady) tts.stop(); });
        }

        @JavascriptInterface
        public void saveAudioFile(String base64Data, String filename) {
            runOnUiThread(() -> {
                try {
                    byte[] data = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT);
                    File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (!dir.exists()) dir.mkdirs();
                    File file = new File(dir, filename);
                    FileOutputStream fos = new FileOutputStream(file);
                    fos.write(data);
                    fos.close();
                    Toast.makeText(MainActivity.this, "Saved to Downloads: " + filename, Toast.LENGTH_LONG).show();
                    // Notify media scanner
                    Intent mediaScan = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    mediaScan.setData(android.net.Uri.fromFile(file));
                    sendBroadcast(mediaScan);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Save failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
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
