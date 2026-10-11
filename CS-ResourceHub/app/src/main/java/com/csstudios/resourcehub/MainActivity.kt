package com.csstudios.resourcehub

import android.app.DownloadManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.URLUtil
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = filePathCallback
        filePathCallback = null
        if (result.resultCode == RESULT_OK) {
            callback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        } else {
            callback?.onReceiveValue(null)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(11, 17, 30)
        window.navigationBarColor = Color.rgb(11, 17, 30)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        webView = WebView(this).apply {
            setBackgroundColor(Color.rgb(11, 17, 30))
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val uri = request?.url ?: return false
                    if (uri.scheme == "file" || uri.host == "localhost") return false
                    val path = uri.path?.lowercase().orEmpty()
                    val resourceDownload = path.contains("/storage/v1/object/sign/resource-files/") ||
                        listOf(".zip", ".jar", ".mcpack", ".mcaddon", ".schem", ".schematic", ".yaml", ".yml", ".json", ".toml").any { path.endsWith(it) }
                    if (resourceDownload) return false // Let WebView's DownloadListener handle the binary file.
                    if (uri.scheme == "https" || uri.scheme == "http" || uri.scheme == "mailto") {
                        return try {
                            startActivity(Intent(Intent.ACTION_VIEW, uri))
                            true
                        } catch (_: Exception) { false }
                    }
                    return true
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    this@MainActivity.filePathCallback?.onReceiveValue(null)
                    this@MainActivity.filePathCallback = filePathCallback
                    val intent = try {
                        fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
                    } catch (_: Exception) {
                        Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
                    }
                    return try {
                        filePickerLauncher.launch(intent)
                        true
                    } catch (_: Exception) {
                        this@MainActivity.filePathCallback?.onReceiveValue(null)
                        this@MainActivity.filePathCallback = null
                        false
                    }
                }
            }
            setDownloadListener { url, _, contentDisposition, mimeType, _ ->
                try {
                    val uri = Uri.parse(url)
                    val filename = URLUtil.guessFileName(url, contentDisposition, mimeType)
                    val request = DownloadManager.Request(uri)
                        .setTitle(filename)
                        .setDescription("Descargando desde CS ResourceHub")
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                    if (!mimeType.isNullOrBlank()) request.setMimeType(mimeType)
                    (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                    Toast.makeText(this@MainActivity, "Descarga iniciada: $filename", Toast.LENGTH_LONG).show()
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, "No se pudo iniciar la descarga. Inténtalo de nuevo.", Toast.LENGTH_LONG).show()
                }
            }
        }
        setContentView(webView)
        if (savedInstanceState == null) webView.loadUrl("file:///android_asset/index.html")
        else webView.restoreState(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::webView.isInitialized) webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Android, retained for compatibility")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }
}
