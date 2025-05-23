package ani.himitsu.others.webview

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebViewClient
import ani.himitsu.FileUrl
import ani.himitsu.databinding.BottomSheetWebviewBinding
import ani.himitsu.defaultHeaders
import ani.himitsu.view.dialog.BottomSheetDialogFragment
import eu.kanade.tachiyomi.network.NetworkHelper
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

abstract class WebViewBottomDialog : BottomSheetDialogFragment() {

    abstract val location: FileUrl

    private var _binding: BottomSheetWebviewBinding? = null
    open val binding get() = _binding!!

    abstract val title: String
    abstract val webViewClient: WebViewClient

    var callback: ((Map<String, String>) -> Unit)? = null

    protected var privateCallback: ((Map<String, String>) -> Unit) = {
        callback?.invoke(it)
        _binding?.webView?.stopLoading()
        dismiss()
    }

    val cookies: CookieManager? = Injekt.get<NetworkHelper>().cookieJar.manager
    //CookieManager.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetWebviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.webViewTitle.text = title
        binding.webView.settings.apply {
            javaScriptEnabled = true
            userAgentString = defaultHeaders["User-Agent"]
        }
        cookies?.setAcceptThirdPartyCookies(binding.webView, true)
        binding.webView.webViewClient = webViewClient
        binding.webView.loadUrl(location.url, location.headers)
        this.dismiss()
    }

    override fun onDestroy() {
        _binding = null
        super.onDestroy()
    }
}