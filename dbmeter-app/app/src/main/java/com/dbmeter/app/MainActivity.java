package com.dbmeter.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private PermissionRequest pendingPermissionRequest;

    // 原生麦克风权限请求
    private final ActivityResultLauncher<String> micPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                // 原生权限返回后，如果有挂起的 WebView 权限请求，继续授予
                if (pendingPermissionRequest != null) {
                    if (isGranted) {
                        // 原生权限已授予，授权 WebView 麦克风访问
                        pendingPermissionRequest.grant(pendingPermissionRequest.getResources());
                    } else {
                        // 原生权限被拒绝，拒绝 WebView 权限
                        pendingPermissionRequest.deny();
                    }
                    pendingPermissionRequest = null;
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        // 允许 WebView 处理导航
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }
        });

        // 关键：重写 onPermissionRequest，处理 WebView 内 getUserMedia 的权限请求
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                // 只处理音频捕获权限
                boolean hasAudio = false;
                for (String res : request.getResources()) {
                    if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(res)) {
                        hasAudio = true;
                        break;
                    }
                }
                if (!hasAudio) {
                    request.deny();
                    return;
                }

                // 先检查原生层是否已有麦克风权限
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED) {
                    // 已有原生权限，直接授权 WebView
                    runOnUiThread(() -> request.grant(request.getResources()));
                } else {
                    // 没有原生权限，先请求原生权限，挂起 WebView 请求等待回调
                    pendingPermissionRequest = request;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
                    }
                }
            }
        });

        // 启动时主动申请麦克风权限（不等用户点击也能提前弹窗）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            }
        }

        // 加载本地页面
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
