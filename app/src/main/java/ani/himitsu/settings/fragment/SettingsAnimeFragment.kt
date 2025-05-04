package ani.himitsu.settings.fragment

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.core.math.MathUtils.clamp
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import ani.himitsu.R
import ani.himitsu.Refresh
import ani.himitsu.databinding.ActivitySettingsAnimeBinding
import ani.himitsu.download.DownloadsManager
import ani.himitsu.media.MediaType
import ani.himitsu.settings.PlayerSettingsActivity
import ani.himitsu.settings.Settings
import ani.himitsu.settings.SettingsActivity
import ani.himitsu.settings.SettingsAdapter
import ani.himitsu.settings.ViewType
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.snackString
import ani.himitsu.util.customAlertDialog
import bit.himitsu.TorrManager
import bit.himitsu.torrServerStart
import bit.himitsu.torrServerStop
import bit.himitsu.widget.onCompletedAction
import eu.kanade.tachiyomi.data.torrentServer.TorrentServerUtils
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SettingsAnimeFragment : Fragment() {
    private lateinit var binding: ActivitySettingsAnimeBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ActivitySettingsAnimeBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val settings = requireActivity() as SettingsActivity

        binding.apply {
            animeSettingsBack.setOnClickListener {
                settings.backToMenu()
            }

            val settingsAdapter = SettingsAdapter(
                arrayListOf(
                    Settings(
                        type = ViewType.SELECTOR,
                        nameRes = R.string.default_ep_view,
                        cardDrawable = arrayOf(
                            R.drawable.round_view_list_24,
                            R.drawable.round_grid_view_24,
                            R.drawable.round_view_comfy_24
                        ),
                        onCardClick = arrayOf(
                            { PrefManager.setVal(PrefName.AnimeDefaultView, 0) },
                            { PrefManager.setVal(PrefName.AnimeDefaultView, 1) },
                            { PrefManager.setVal(PrefName.AnimeDefaultView, 2) }
                        ),
                        selectedItem = PrefManager.getVal<Int>(PrefName.AnimeDefaultView)
                    ),
                    Settings(
                        type = ViewType.BUTTON,
                        nameRes = R.string.player_settings,
                        descRes = R.string.player_settings_desc,
                        icon = R.drawable.round_video_settings_24,
                        onClick = {
                            startActivity(Intent(settings, PlayerSettingsActivity::class.java))
                        },
                        isActivity = true
                    ),
                    Settings(
                        type = ViewType.BUTTON,
                        nameRes = R.string.purge_anime_downloads,
                        icon = R.drawable.round_delete_sweep_24,
                        onClick = {
                            settings.customAlertDialog().apply {
                                setMessage(R.string.purge_confirm, getString(R.string.anime))
                                setPositiveButton(R.string.yes, onClick = {
                                    val downloadsManager = Injekt.get<DownloadsManager>()
                                    downloadsManager.purgeDownloads(MediaType.ANIME)
                                })
                                setNegativeButton(R.string.no)
                                show()
                            }
                        },
                        isDialog = true
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.prefer_dub,
                        descRes = R.string.prefer_dub_desc,
                        icon = R.drawable.round_audiotrack_24,
                        pref = PrefName.SettingsPreferDub
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.show_yt,
                        descRes = R.string.show_yt_desc,
                        icon = R.drawable.round_play_circle_24,
                        pref = PrefName.ShowYtButton
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.include_list,
                        descRes = R.string.include_list_anime_desc,
                        icon = R.drawable.view_list_24,
                        isChecked = PrefManager.getVal(PrefName.IncludeAnimeList),
                        switch = { isChecked, _ ->
                            PrefManager.setVal(PrefName.IncludeAnimeList, isChecked)
                            Refresh.all()
                        }
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.local_timezone,
                        icon = R.drawable.round_access_time_24,
                        pref = PrefName.LocalTimeZone
                    ),
                    Settings(
                        type = ViewType.HEADER,
                        nameRes = R.string.torrserver
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.settings_torrent,
                        icon = R.drawable.ic_round_magnet_24,
                        isChecked = PrefManager.getVal(PrefName.TorrServerEnabled),
                        switch = { isChecked, _ ->
                            if (isChecked)
                                torrServerStart()
                            else
                                torrServerStop()
                            PrefManager.setVal(PrefName.TorrServerEnabled, isChecked)
                        }
                    ),
                    Settings(
                        type = ViewType.EDITTEXT,
                        nameRes = R.string.torrent_port,
                        descRes = R.string.port_range,
                        icon = R.drawable.ic_torrent_24,
                        defaultText = TorrentServerUtils.port,
                        onTextChange = { editText, value ->
                            val port = if (value.isNullOrBlank()) {
                                snackString(R.string.invalid_port)
                                "8090"
                            } else {
                                clamp(value.toInt(), 0, 65535).toString()
                            }
                            val wasRunning = TorrManager.isServiceRunning()
                            torrServerStop()
                            TorrentServerUtils.port = port
                            if (wasRunning) torrServerStart()
                            editText.setText(port)
                        }
                    ),
                    Settings(
                        type = ViewType.HEADER,
                        nameRes = R.string.advanced
                    ),
                    Settings(
                        type = ViewType.SLIDER,
                        name = getString(R.string.episode_timeout, getString(R.string.anify)),
                        desc = getString(R.string.episode_timeout_desc, getString(R.string.anify)),
                        icon = R.drawable.round_elderly_woman_24,
                        pref = PrefName.AnifyTimeout,
                        stepSize = 0.25f,
                        valueFrom = 0f,
                        valueTo = 1f
                    ),
                    Settings(
                        type = ViewType.SLIDER,
                        name = getString(R.string.episode_timeout, getString(R.string.kitsu)),
                        desc = getString(R.string.episode_timeout_desc, getString(R.string.kitsu)),
                        icon = R.drawable.round_elderly_woman_24,
                        pref = PrefName.KitsuTimeout,
                        stepSize = 0.25f,
                        valueFrom = 0f,
                        valueTo = 1.25f
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

    override fun onResume() {
        super.onResume()
        (requireActivity() as SettingsActivity).model.getQuery().value.let {
            binding.searchViewText.setText(it)
            (binding.settingsRecyclerView.adapter as SettingsAdapter)
                .getFilter()?.filter(it)
        }
    }
}