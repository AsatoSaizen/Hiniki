package ani.himitsu.settings.fragment

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import ani.dantotsu.addons.AddonDownloader
import ani.dantotsu.addons.download.DownloadAddonManager
import ani.himitsu.R
import ani.himitsu.databinding.ActivitySettingsAddonsBinding
import ani.himitsu.databinding.ItemSettingsBinding
import ani.himitsu.settings.Settings
import ani.himitsu.settings.SettingsActivity
import ani.himitsu.settings.SettingsAdapter
import ani.himitsu.settings.ViewType
import ani.himitsu.snackString
import ani.himitsu.util.Logger
import bit.himitsu.widget.onCompletedAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.util.lang.launchIO
import tachiyomi.core.util.lang.withUIContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SettingsAddonFragment : Fragment() {
    private lateinit var binding: ActivitySettingsAddonsBinding
    private val downloadAddonManager: DownloadAddonManager = Injekt.get()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ActivitySettingsAddonsBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val settings = requireActivity() as SettingsActivity

        binding.apply {
            binding.addonSettingsBack.setOnClickListener {
                settings.backToMenu()
            }

            val settingsAdapter = SettingsAdapter(
                arrayListOf(
                    Settings(
                        type = ViewType.BUTTON,
                        nameRes = R.string.anime_downloader_addon,
                        descRes = R.string.not_installed,
                        icon = R.drawable.ic_download_24,
                        hasTransition = true,
                        attach = {
                            setStatus(
                                view = it,
                                context = settings,
                                status = downloadAddonManager.hadError(settings),
                                hasUpdate = downloadAddonManager.hasUpdate
                            )
                            var job = Job()
                            downloadAddonManager.addListenerAction { _ ->
                                job.cancel()
                                it.settingsIconRight.animate().cancel()
                                it.settingsIconRight.rotation = 0f
                                setStatus(
                                    view = it,
                                    context = settings,
                                    status = downloadAddonManager.hadError(settings),
                                    hasUpdate = false
                                )
                            }
                            it.settingsIconRight.setOnClickListener { _ ->
                                if (it.settingsDesc.text == getString(R.string.installed)) {
                                    downloadAddonManager.uninstall()
                                    return@setOnClickListener
                                } else {
                                    job = Job()
                                    it.settingsIconRight.setImageResource(R.drawable.ic_sync)
                                    CoroutineScope(Dispatchers.Main + job).launch {
                                        while (isActive) {
                                            withUIContext {
                                                it.settingsIconRight.animate()
                                                    .rotationBy(360f)
                                                    .setDuration(1000)
                                                    .setInterpolator(LinearInterpolator())
                                                    .start()
                                            }
                                            delay(1000)
                                        }
                                    }
                                    snackString(getString(R.string.downloading))
                                    lifecycleScope.launchIO {
                                        AddonDownloader.update(
                                            activity = settings,
                                            downloadAddonManager,
                                            repo = DownloadAddonManager.REPO,
                                            currentVersion = downloadAddonManager.getVersion() ?: ""
                                        )
                                    }
                                }
                            }
                        }
                    )
                )
            )
            settingsRecyclerView.apply {
                adapter = settingsAdapter
                layoutManager = LinearLayoutManager(settings, LinearLayoutManager.VERTICAL, false)
                setHasFixedSize(true)
            }

            settings.model.getQuery().observe(viewLifecycleOwner) { query ->
                settingsAdapter.getFilter()?.filter(query)
            }
            binding.searchViewText.setText(settings.model.getQuery().value)
            binding.searchViewText.setOnEditorActionListener(onCompletedAction {
                with (requireContext().getSystemService(
                    Context.INPUT_METHOD_SERVICE
                ) as InputMethodManager) {
                    hideSoftInputFromWindow(binding.searchViewText.windowToken, 0)
                }
                settings.model.setQuery(binding.searchViewText.text?.toString())
            })
            binding.searchView.setEndIconOnClickListener {
                settings.model.setQuery(binding.searchViewText.text?.toString())
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        downloadAddonManager.removeListenerAction()
    }

    private fun setStatus(
        view: ItemSettingsBinding,
        context: Context,
        status: String?,
        hasUpdate: Boolean
    ) {
        try {
            when (status) {
                context.getString(R.string.loaded_successfully) -> {
                    view.settingsIconRight.setImageResource(R.drawable.round_delete_24)
                    view.settingsIconRight.rotation = 0f
                    view.settingsDesc.text = context.getString(R.string.installed)
                }

                null -> {
                    view.settingsIconRight.setImageResource(R.drawable.ic_download_24)
                    view.settingsIconRight.rotation = 0f
                    view.settingsDesc.text = context.getString(R.string.not_installed)
                }

                else -> {
                    view.settingsIconRight.setImageResource(R.drawable.round_new_releases_24)
                    view.settingsIconRight.rotation = 0f
                    view.settingsDesc.text = context.getString(R.string.error_msg, status)
                }
            }
            if (hasUpdate) {
                view.settingsIconRight.setImageResource(R.drawable.round_sync_24)
                view.settingsDesc.text = context.getString(R.string.update_addon)
            }
        } catch (e: Exception) {
            Logger.log(e)
        }
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as SettingsActivity).model.getQuery().value.let {
            binding.searchViewText.setText(it)
            (binding.settingsRecyclerView.adapter as SettingsAdapter)
                .getFilter()?.filter(it)
        }
    }
}