package ani.himitsu.media

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.GridLayoutManager
import ani.himitsu.R
import ani.himitsu.databinding.ActivityMediaListViewBinding
import ani.himitsu.initActivity
import ani.himitsu.media.cereal.Media
import ani.himitsu.others.getSerialized
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.statusBarHeight
import ani.himitsu.themes.ThemeManager
import bit.himitsu.content.dpToColumns
import bit.himitsu.hideSystemBarsExtendView
import bit.himitsu.setNavigationTheme
import bit.himitsu.setStatusColor
import bit.himitsu.setStatusTheme
import bit.himitsu.showSystemBarsRetractView
import eu.kanade.tachiyomi.util.system.getThemeColor

class MediaListViewActivity: AppCompatActivity() {
    private lateinit var binding: ActivityMediaListViewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMediaListViewBinding.inflate(layoutInflater)
        ThemeManager(this).applyTheme()
        initActivity(this)

        if (PrefManager.getVal(PrefName.ImmersiveMode)) {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            hideSystemBarsExtendView()
            binding.settingsContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusBarHeight
            }
        } else {
            showSystemBarsRetractView()
            window.setStatusColor(R.color.nav_bg_inv)

        }
        setContentView(binding.root)

        binding.listBackButton.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        val surfaceColor = getThemeColor(com.google.android.material.R.attr.colorSurface)

        window.setStatusTheme()
        window.setNavigationTheme()
        binding.listAppBar.setBackgroundColor(surfaceColor)
        val mediaList = intent.getSerialized("media") as? ArrayList<Media> ?: ArrayList()
        val view = PrefManager.getCustomVal("mediaView", 0)
        var mediaView: View = when (view) {
            1 -> binding.mediaList
            0 -> binding.mediaGrid
            else -> binding.mediaGrid
        }
        mediaView.alpha = 1f
        fun changeView(mode: Int, current: View) {
            mediaView.alpha = 0.33f
            mediaView = current
            current.alpha = 1f
            PrefManager.setCustomVal("mediaView", mode)
            binding.mediaRecyclerView.adapter = MediaAdaptor(mode, mediaList, this)
            binding.mediaRecyclerView.layoutManager = GridLayoutManager(
                this, if (mode == 1) 1 else 120.dpToColumns
            )
        }
        binding.mediaList.setOnClickListener {
            changeView(1, binding.mediaList)
        }
        binding.mediaGrid.setOnClickListener {
            changeView(0, binding.mediaGrid)
        }
        val text = "${intent.getStringExtra("title")} (${mediaList.count()})"
        binding.listTitle.text = text
        binding.mediaRecyclerView.adapter = MediaAdaptor(view, mediaList, this)
        binding.mediaRecyclerView.layoutManager = GridLayoutManager(
            this, if (view == 1) 1 else 120.dpToColumns
        )
    }
}
