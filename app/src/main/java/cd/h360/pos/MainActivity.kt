package cd.h360.pos

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import cd.h360.pos.databinding.ActivityMainBinding
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val bgExecutor = Executors.newSingleThreadExecutor()

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = filePathCallback
        if (callback == null) return@registerForActivityResult

        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        callback.onReceiveValue(uris)
        filePathCallback = null
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op */ }

    // H360-CUSTOM-PATCH [H360_ANDROID_WEBVIEW_URL]
    private val homeUrl = BuildConfig.WEBVIEW_BASE_URL
    private val maintenanceCheckUrl = BuildConfig.MAINTENANCE_CHECK_URL
    private val kioskModeEnabled = BuildConfig.ENABLE_KIOSK_MODE
    private val appOrigin = Uri.parse(homeUrl).let { "${it.scheme}://${it.host}" }

    private val allowedHosts = BuildConfig.ALLOWED_INTERNAL_HOSTS
        .split(",")
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }
        .toSet()

    private val bridge by lazy {
        H360JsBridge(
            onRole = { role ->
                saveRole(role)
                AppShortcutsManager.updateForRole(this, role)
                H360WidgetUpdater.refreshAllWidgets(this)
            },
            onOfflinePending = { count ->
                saveOfflinePending(count)
                H360WidgetUpdater.refreshAllWidgets(this)
            },
            onLastSync = { lastSync ->
                saveLastSync(lastSync)
                H360WidgetUpdater.refreshAllWidgets(this)
            }
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupWebView()
        setupBackNavigation()
        setupMaintenanceActions()
        requestNotificationPermissionIfNeeded()
        enableKioskModeIfConfigured()
        AppShortcutsManager.updateForRole(this, readRole())
        H360WidgetUpdater.refreshAllWidgets(this)
        checkMaintenanceAndLoad()
    }

    private fun setupToolbar() {
        binding.toolbar.title = getString(R.string.app_name)
        binding.toolbar.setTitleTextColor(ContextCompat.getColor(this, android.R.color.white))
        binding.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.toolbar.navigationIcon?.setTint(ContextCompat.getColor(this, android.R.color.white))
        binding.toolbar.setNavigationOnClickListener {
            if (binding.webView.canGoBack()) {
                binding.webView.goBack()
            }
        }
        binding.toolbar.inflateMenu(R.menu.main_actions)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_print_invoice -> {
                    printCurrentPage()
                    true
                }
                R.id.action_copilot -> {
                    binding.webView.loadUrl("$appOrigin/h360copilot")
                    true
                }
                R.id.action_offline -> {
                    binding.webView.loadUrl("$appOrigin/h360offline")
                    true
                }
                R.id.action_new_sale -> {
                    binding.webView.loadUrl("$appOrigin/pos/create")
                    true
                }
                else -> false
            }
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.maintenanceContainer.visibility == View.VISIBLE) {
                    finish()
                    return
                }

                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    finish()
                }
            }
        })
    }

    private fun setupMaintenanceActions() {
        binding.maintenanceRetryButton.setOnClickListener {
            checkMaintenanceAndLoad(forceReload = true)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun enableKioskModeIfConfigured() {
        if (!kioskModeEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            runCatching { startLockTask() }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webView = binding.webView
        val settings = webView.settings

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.loadsImagesAutomatically = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportMultipleWindows(false)
        settings.mediaPlaybackRequiresUserGesture = false
        webView.setInitialScale(80)

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.addJavascriptInterface(bridge, "H360Native")

        binding.swipeRefresh.setOnRefreshListener {
            webView.reload()
        }

        webView.setDownloadListener { url, userAgent, _, mimeType, _ ->
            val request = DownloadManager.Request(Uri.parse(url))
            request.setMimeType(mimeType)
            request.addRequestHeader("User-Agent", userAgent)
            request.setDescription(getString(R.string.download_in_progress))
            request.setTitle(Uri.parse(url).lastPathSegment ?: "download")
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, Uri.parse(url).lastPathSegment)

            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback

                val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }

                return try {
                    fileChooserLauncher.launch(intent)
                    true
                } catch (_: Exception) {
                    this@MainActivity.filePathCallback = null
                    false
                }
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                val visible = newProgress in 1..99
                binding.progressBar.visibility = if (visible) View.VISIBLE else View.GONE
                binding.toolbar.subtitle = if (visible) "${newProgress}%" else view?.title
                super.onProgressChanged(view, newProgress)
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val targetUri = request.url
                val host = targetUri.host?.lowercase().orEmpty()

                if (host.isEmpty() || allowedHosts.contains(host)) {
                    return false
                }

                runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, targetUri))
                }
                return true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.swipeRefresh.isRefreshing = false
                binding.offlineBanner.visibility = if (isOnline()) View.GONE else View.VISIBLE
                if (!url.isNullOrBlank()) {
                    val inferredRole = inferRoleFromUrl(url)
                    if (inferredRole != null && inferredRole != readRole()) {
                        saveRole(inferredRole)
                        AppShortcutsManager.updateForRole(this@MainActivity, inferredRole)
                        H360WidgetUpdater.refreshAllWidgets(this@MainActivity)
                    }
                }
                super.onPageFinished(view, url)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    binding.offlineBanner.visibility = View.VISIBLE
                }
                super.onReceivedError(view, request, error)
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                if (request?.isForMainFrame == true && errorResponse?.statusCode == 503) {
                    showMaintenanceOverlay()
                }
                super.onReceivedHttpError(view, request, errorResponse)
            }
        }
    }

    private fun checkMaintenanceAndLoad(forceReload: Boolean = false) {
        if (!isOnline()) {
            binding.offlineBanner.visibility = View.VISIBLE
            hideMaintenanceOverlay()
            if (forceReload) {
                loadTargetUrl()
            }
            return
        }

        bgExecutor.execute {
            val statusCode = runCatching {
                val conn = (URL(maintenanceCheckUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                }
                conn.responseCode
            }.getOrDefault(200)

            runOnUiThread {
                if (statusCode == 503) {
                    showMaintenanceOverlay()
                } else {
                    hideMaintenanceOverlay()
                    if (forceReload || binding.webView.url.isNullOrBlank()) {
                        loadTargetUrl()
                    }
                }
            }
        }
    }

    private fun loadTargetUrl() {
        val target = DeepLinkResolver.resolve(intent, homeUrl, allowedHosts)
        binding.webView.loadUrl(target)
    }

    private fun showMaintenanceOverlay() {
        binding.maintenanceContainer.visibility = View.VISIBLE
        binding.swipeRefresh.isEnabled = false
    }

    private fun hideMaintenanceOverlay() {
        binding.maintenanceContainer.visibility = View.GONE
        binding.swipeRefresh.isEnabled = true
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
        @Suppress("DEPRECATION")
        val info = cm.activeNetworkInfo
        @Suppress("DEPRECATION")
        return info != null && info.isConnected
    }

    override fun onDestroy() {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        bgExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun printCurrentPage() {
        val printManager = getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val adapter = binding.webView.createPrintDocumentAdapter("h360-invoice")
        printManager.print(
            "H360 Invoice",
            adapter,
            PrintAttributes.Builder().build()
        )
    }

    private fun inferRoleFromUrl(url: String): String? {
        val normalized = url.lowercase()
        return when {
            normalized.contains("/superadmin") -> "admin"
            normalized.contains("/user-management") -> "admin"
            normalized.contains("/pos/") -> "cashier"
            normalized.contains("/stock") || normalized.contains("/product") -> "storekeeper"
            else -> null
        }
    }

    private fun readRole(): String {
        val prefs = getSharedPreferences(H360WidgetUpdater.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(H360WidgetUpdater.KEY_ROLE, "guest") ?: "guest"
    }

    private fun saveRole(role: String) {
        getSharedPreferences(H360WidgetUpdater.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(H360WidgetUpdater.KEY_ROLE, role)
            .apply()
    }

    private fun saveOfflinePending(count: Int) {
        getSharedPreferences(H360WidgetUpdater.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(H360WidgetUpdater.KEY_OFFLINE_PENDING, count)
            .apply()
    }

    private fun saveLastSync(lastSync: String) {
        getSharedPreferences(H360WidgetUpdater.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(H360WidgetUpdater.KEY_LAST_SYNC, lastSync)
            .apply()
    }
}

private class H360JsBridge(
    private val onRole: (String) -> Unit,
    private val onOfflinePending: (Int) -> Unit,
    private val onLastSync: (String) -> Unit
) {
    @JavascriptInterface
    fun setRole(role: String?) {
        val safeRole = role?.trim()?.lowercase().orEmpty()
        if (safeRole.isNotBlank()) {
            onRole(safeRole)
        }
    }

    @JavascriptInterface
    fun setOfflinePendingCount(count: Int) {
        if (count >= 0) {
            onOfflinePending(count)
        }
    }

    @JavascriptInterface
    fun setLastSync(lastSync: String?) {
        val safeLastSync = lastSync?.trim().orEmpty()
        if (safeLastSync.isNotBlank()) {
            onLastSync(safeLastSync)
        }
    }
}
