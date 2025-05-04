package ani.himitsu.media

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import ani.himitsu.R
import ani.himitsu.Refresh
import ani.himitsu.databinding.ActivityStudioBinding
import ani.himitsu.initActivity
import ani.himitsu.media.cereal.Studio
import ani.himitsu.navBarHeight
import ani.himitsu.others.EmptyAdapter
import ani.himitsu.others.getSerialized
import ani.himitsu.statusBarHeight
import ani.himitsu.themes.ThemeManager
import bit.himitsu.content.dpToColumns
import bit.himitsu.content.toPx
import bit.himitsu.setStatusColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.util.lang.withIOContext

class StudioActivity : AppCompatActivity() {
    private lateinit var binding: ActivityStudioBinding
    private val scope = lifecycleScope
    private val model: OtherDetailsViewModel by viewModels()
    private var studio: Studio? = null
    private var loaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeManager(this).applyTheme()
        binding = ActivityStudioBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initActivity(this)
        window.setStatusColor(R.color.nav_bg)

        binding.root.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin += statusBarHeight }
        // TODO: Investigate hardcoded values
        binding.studioRecycler.updatePadding(bottom = 64.toPx + navBarHeight)
        binding.studioTitle.isSelected = true

        studio = intent.getSerialized("studio")
        binding.studioTitle.text = studio?.name

        binding.studioClose.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        model.getStudio().observe(this) {
            if (it != null) {
                studio = it
                loaded = true
                binding.studioProgressBar.visibility = View.GONE
                binding.studioRecycler.visibility = View.VISIBLE

                val titlePosition = arrayListOf<Int>()
                val concatAdapter = ConcatAdapter()
                val map = studio!!.yearMedia ?: return@observe
                val keys = map.keys.toTypedArray()
                var pos = 0

                val gridSize = 124.dpToColumns
                val gridLayoutManager = GridLayoutManager(this, gridSize)
                gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        return when (position in titlePosition) {
                            true -> gridSize
                            else -> 1
                        }
                    }
                }
                for (i in keys.indices) {
                    val medias = map[keys[i]]!!
                    val empty = if (medias.size >= 4) medias.size % 4 else 4 - medias.size
                    titlePosition.add(pos)
                    pos += (empty + medias.size + 1)

                    concatAdapter.addAdapter(TitleAdapter("${keys[i]} (${medias.size})"))
                    concatAdapter.addAdapter(MediaAdaptor(ViewType.COMPACT, medias, this, true))
                    concatAdapter.addAdapter(EmptyAdapter(empty))
                }

                binding.studioRecycler.adapter = concatAdapter
                binding.studioRecycler.layoutManager = gridLayoutManager
            }
        }
        val live = Refresh.activity.getOrPut(this.hashCode()) { MutableLiveData(true) }
        live.observe(this) {
            if (it) {
                scope.launch {
                    if (studio != null)
                        withIOContext { model.loadStudio(studio!!) }
                    live.postValue(false)
                }
            }
        }
    }

    override fun onDestroy() {
        if (Refresh.activity.containsKey(this.hashCode())) {
            Refresh.activity.remove(this.hashCode())
        }
        super.onDestroy()
    }

    override fun onResume() {
        binding.studioProgressBar.isGone = loaded
        super.onResume()
    }
}