package ani.himitsu.settings.fragment

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import ani.himitsu.R
import ani.himitsu.databinding.ActivitySettingsUserInterfaceBinding
import ani.himitsu.restart
import ani.himitsu.settings.Page
import ani.himitsu.settings.START_PAGE
import ani.himitsu.settings.Settings
import ani.himitsu.settings.SettingsActivity
import ani.himitsu.settings.SettingsAdapter
import ani.himitsu.settings.ViewType
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import bit.himitsu.widget.onCompletedAction
import kotlinx.coroutines.launch

class UserInterfaceFragment : Fragment() {
    private lateinit var binding: ActivitySettingsUserInterfaceBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ActivitySettingsUserInterfaceBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val settings = requireActivity() as SettingsActivity

        binding.apply {
            uiSettingsBack.setOnClickListener {
                settings.backToMenu()
            }

            val map = mapOf(
                2f to 0.5f,
                1.75f to 0.625f,
                1.5f to 0.75f,
                1.25f to 0.875f,
                1f to 1f,
                0.75f to 1.25f,
                0.5f to 1.5f,
                0.25f to 1.75f,
                0f to 0f
            )
            val mapReverse = map.map { it.value to it.key }.toMap()

            var hasFoldingFeature = false
            lifecycleScope.launch {
                WindowInfoTracker.getOrCreate(settings)
                    .windowLayoutInfo(settings)
                    .collect { newLayoutInfo ->
                        hasFoldingFeature = newLayoutInfo.displayFeatures.find {
                            it is FoldingFeature
                        } != null
                    }
            }

            val scaleHandler = Handler(Looper.getMainLooper())

            val settingsAdapter = SettingsAdapter(
                arrayListOf(
                    Settings(
                        type = ViewType.SELECTOR,
                        nameRes = R.string.startUpTab,
                        cardDrawable = arrayOf(
                            R.drawable.round_movie_filter_24,
                            R.drawable.round_home_24,
                            R.drawable.round_import_contacts_24
                        ),
                        onCardClick = arrayOf(
                            { PrefManager.setVal(PrefName.DefaultStartUpTab, 0) },
                            { PrefManager.setVal(PrefName.DefaultStartUpTab, 1) },
                            { PrefManager.setVal(PrefName.DefaultStartUpTab, 2) }
                        ),
                        selectedItem = PrefManager.getVal<Int>(PrefName.DefaultStartUpTab)
                    ),
                    Settings(
                        type = ViewType.HEADER,
                        nameRes = R.string.general
                    ),
                    Settings(
                        type = ViewType.BUTTON,
                        nameRes = R.string.home_layout_show,
                        icon = R.drawable.round_playlist_add_24,
                        onClick = {
                            val set = PrefManager.getVal<List<Boolean>>(PrefName.HomeLayout).toMutableList()
                            val views = resources.getStringArray(R.array.home_layouts)
                            val dialog = AlertDialog.Builder(settings, R.style.MyDialog)
                                .setTitle(getString(R.string.home_layout_show)).apply {
                                    setMultiChoiceItems(
                                        views,
                                        PrefManager.getVal<List<Boolean>>(PrefName.HomeLayout).toBooleanArray()
                                    ) { _, i, value ->
                                        set[i] = value
                                    }
                                    setPositiveButton(getString(R.string.done)) { _, _ ->
                                        PrefManager.setVal(PrefName.HomeLayout, set)
                                    }
                                }.show()
                            dialog.window?.setDimAmount(0.8f)
                        },
                        isDialog = true
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.immersive_mode,
                        icon = R.drawable.round_fullscreen_24,
                        pref = PrefName.ImmersiveMode
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.hide_home_main,
                        descRes = R.string.hide_home_main_desc,
                        icon = R.drawable.ic_clean_hands_24,
                        pref = PrefName.HomeMainHide,
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.random_recommended,
                        descRes = R.string.random_recommended_desc,
                        icon = R.drawable.ic_auto_fix_high_24,
                        pref = PrefName.HideRandoRec
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.show_forum_button,
                        icon = R.drawable.round_forum_24,
                        pref = PrefName.ShowForumButton,
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.show_anibrain_button,
                        icon = R.drawable.ic_anibrain,
                        pref = PrefName.ShowAnibrainButton,
                    ),
                    Settings(
                        type = ViewType.SLIDER,
                        nameRes = R.string.load_affinity,
                        icon = R.drawable.round_speed_24,
                        labelLeft = getString(R.string.less_loading),
                        labelRight = getString(R.string.faster_start),
                        stepSize = 1f,
                        valueFrom = -2f,
                        valueTo = 2f,
                        value = PrefManager.getVal<Int>(PrefName.LoadingAffinity).toFloat(),
                        slider = { value, _ ->
                            PrefManager.setVal(PrefName.LoadingAffinity, value.toInt())
                        }
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.trending_covers,
                        icon = R.drawable.round_art_track_24,
                        pref = PrefName.TrendingCovers
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.small_view,
                        icon = R.drawable.round_photo_size_select_large_24,
                        pref = PrefName.SmallVille,
                        switch = { isChecked, binding ->
                            PrefManager.setVal(PrefName.SmallVille, isChecked)
                            settings.restart(
                                ComponentName(
                                    settings.packageName,
                                    SettingsActivity::class.qualifiedName!!
                                ),
                                Bundle().apply { putString(START_PAGE, Page.UI.name) }
                            )
                        },
                        itemsEnabled = arrayOf(11)
                    ),
                    Settings(
                        type = ViewType.SLIDER,
                        nameRes = R.string.small_view_scale,
                        icon = R.drawable.round_photo_size_select_small_24,
                        labelLeft = getString(R.string.scale_small),
                        labelRight = getString(R.string.scale_large),
                        pref = PrefName.LoisLane,
                        stepSize = 0.5f,
                        valueFrom = 1.5f,
                        valueTo = 3f,
                        slider = { clamped, binding ->
                            PrefManager.setVal(PrefName.LoisLane, clamped)
                            scaleHandler.removeCallbacksAndMessages(null)
                            scaleHandler.postDelayed({
                                settings.restart(
                                    ComponentName(
                                        settings.packageName,
                                        SettingsActivity::class.qualifiedName!!
                                    ),
                                    Bundle().apply { putString(START_PAGE, Page.UI.name) }
                                )
                            }, 1000)
                        },
                        isEnabled = PrefManager.getVal(PrefName.SmallVille)
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.persist_search,
                        icon = R.drawable.round_saved_search_24,
                        pref = PrefName.PersistSearch,
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.use_foldable,
                        descRes = R.string.use_foldable_desc,
                        icon = R.drawable.ic_devices_fold_24,
                        pref = PrefName.UseFoldable,
                        isVisible = hasFoldingFeature
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.floating_avatar,
                        icon = R.drawable.round_attractions_24,
                        pref = PrefName.FloatingAvatar,
                    ),
                    Settings(
                        type = ViewType.HEADER,
                        nameRes = R.string.animations
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.trailer_banners,
                        icon = R.drawable.round_video_camera_back_24,
                        pref = PrefName.YouTubeBanners
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.banner_animations,
                        icon = R.drawable.round_photo_size_select_actual_24,
                        pref = PrefName.BannerAnimations
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.layout_animations,
                        icon = R.drawable.round_animation_24,
                        pref = PrefName.LayoutAnimations
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.trending_scroller,
                        icon = R.drawable.trail_length_short,
                        pref = PrefName.TrendingScroller
                    ),
                    Settings(
                        type = ViewType.SLIDER,
                        nameRes = R.string.animation_speed,
                        icon = R.drawable.adjust,
                        stepSize = 0.25f,
                        valueFrom = 0f,
                        valueTo = 2f,
                        value = mapReverse[PrefManager.getVal(PrefName.AnimationSpeed)] ?: 1f,
                        slider = { value, _ ->
                            PrefManager.setVal(PrefName.AnimationSpeed, map[value] ?: 1f)
                        }
                    ),
                    Settings(
                        type = ViewType.HEADER,
                        nameRes = R.string.blur
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.blur_banners,
                        icon = R.drawable.blur_on,
                        pref = PrefName.BlurBanners
                    ),
                    Settings(
                        type = ViewType.SLIDER,
                        nameRes = R.string.radius,
                        icon = R.drawable.adjust,
                        pref = PrefName.BlurRadius,
                        stepSize = 1f,
                        valueFrom = 1f,
                        valueTo = 10f
                    ),
                    Settings(
                        type = ViewType.SLIDER,
                        nameRes = R.string.sampling,
                        icon = R.drawable.stacks,
                        pref = PrefName.BlurSampling,
                        stepSize = 1f,
                        valueFrom = 1f,
                        valueTo = 10f
                    )
                )
            )
            settingsRecyclerView.apply {
                adapter = settingsAdapter
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
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
