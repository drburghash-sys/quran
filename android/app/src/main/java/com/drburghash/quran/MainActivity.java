package com.drburghash.quran;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int REQUEST_BACKUP_IMPORT = 9002;
    private static final String HOME_URL = "https://appassets.androidplatform.net/assets/index.html";
    private WebView webView;
    private ValueCallback<Uri[]> fileSelection;
    private WebViewAssetLoader assetLoader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xff0e513f);
        getWindow().setNavigationBarColor(0xff0e513f);

        assetLoader = new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
            .build();

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        webView.addJavascriptInterface(new QuranAndroidBridge(), "QuranAndroid");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("appassets.androidplatform.net".equals(uri.getHost())) return false;
                if ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                    catch (Exception ignored) { }
                    return true;
                }
                return false;
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             WebChromeClient.FileChooserParams params) {
                if (fileSelection != null) fileSelection.onReceiveValue(null);
                fileSelection = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_MIME_TYPES,
                    new String[] { "application/json", "text/plain" });
                try { startActivityForResult(intent, REQUEST_BACKUP_IMPORT); }
                catch (Exception error) {
                    fileSelection = null;
                    callback.onReceiveValue(null);
                    Toast.makeText(MainActivity.this, "تعذر فتح الملفات", Toast.LENGTH_LONG).show();
                }
                return true;
            }
        });

        // Using a stable HTTPS-like app origin allows localStorage and IndexedDB
        // to survive app restarts while the HTML content is bundled offline.
        webView.loadUrl(HOME_URL);
    }

    public class QuranAndroidBridge {
        @JavascriptInterface
        public void saveBackup(String json, String proposedName) {
            try {
                String fileName = proposedName.replaceAll("[^a-zA-Z0-9._-]", "_");
                if (!fileName.endsWith(".json")) fileName += ".json";
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                    values.put(MediaStore.Downloads.MIME_TYPE, "application/json");
                    values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) throw new IllegalStateException("Could not create download");
                    try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                        if (out == null) throw new IllegalStateException("Could not write download");
                        out.write(json.getBytes(StandardCharsets.UTF_8));
                    }
                } else {
                    File folder = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                    if (folder == null) throw new IllegalStateException("Downloads unavailable");
                    try (FileOutputStream out = new FileOutputStream(new File(folder,fileName))) {
                        out.write(json.getBytes(StandardCharsets.UTF_8));
                    }
                }
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                    "تم حفظ النسخة الاحتياطية في التنزيلات", Toast.LENGTH_LONG).show());
            } catch (Exception error) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                    "تعذر حفظ النسخة الاحتياطية", Toast.LENGTH_LONG).show());
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_BACKUP_IMPORT || fileSelection == null) return;
        Uri[] result = resultCode == RESULT_OK && data != null && data.getData() != null
            ? new Uri[] { data.getData() } : null;
        fileSelection.onReceiveValue(result);
        fileSelection = null;
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
