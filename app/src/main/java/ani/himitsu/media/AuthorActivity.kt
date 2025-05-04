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
import androidx.recyclerview.widget.LinearLayoutManager
import ani.himitsu.R
import ani.himitsu.Refresh
import ani.himitsu.currActivity
import ani.himitsu.databinding.ActivityAuthorBinding
import ani.himitsu.initActivity
import ani.himitsu.media.cereal.Author
import ani.himitsu.navBarHeight
import ani.himitsu.others.EmptyAdapter
import ani.himitsu.others.SpoilerPlugin
import ani.himitsu.others.getSerialized
import ani.himitsu.statusBarHeight
import ani.himitsu.themes.ThemeManager
import bit.himitsu.content.dpToColumns
import bit.himitsu.content.toPx
import bit.himitsu.setStatusColor
import io.noties.markwon.Markwon
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.util.lang.withIOContext

class AuthorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAuthorBinding
    private val scope = lifecycleScope
    private val model: OtherDetailsViewModel by viewModels()
    private var author: Author? = null
    private var loaded = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeManager(this).applyTheme()
        binding = ActivityAuthorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initActivity(this)
        window.setStatusColor(R.color.nav_bg)

        binding.studioFrame.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin += statusBarHeight }
        binding.studioProgressBar.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin += statusBarHeight }
        // TODO: Investigate hardcoded values
        binding.studioRecycler.updatePadding(bottom = 64.toPx + navBarHeight)
        binding.studioTitle.isSelected = true

        author = intent.getSerialized("author")
        binding.studioTitle.text = author?.name

        binding.studioClose.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        model.getAuthor().observe(this) {
            if (it != null) {
                author = it
                loaded = true

                val desc =
                    (if (author!!.age.toString() != "null") "${currActivity()!!.getString(R.string.age)} ${author!!.age}" else "") +
                            (if (author!!.dateOfBirth.toString() != "null")
                                "${currActivity()!!.getString(R.string.birthday)} ${author!!.dateOfBirth.toString()}" else "") +
                            (if (author!!.gender != "null")
                                currActivity()!!.getString(R.string.gender) + " " + when (author!!.gender) {
                                    currActivity()!!.getString(R.string.male) -> currActivity()!!.getString(
                                        R.string.male
                                    )

                                    currActivity()!!.getString(R.string.female) -> currActivity()!!.getString(
                                        R.string.female
                                    )

                                    else -> author!!.gender
                                } else "") + "\n" + author!!.description

                binding.authorDesc.isTextSelectable
                val markWon = Markwon.builder(this).usePlugin(SoftBreakAddsNewLinePlugin.create())
                    .usePlugin(SpoilerPlugin()).build()
                markWon.setMarkdown(binding.authorDesc, desc.replace("~!", "||").replace("!~", "||"))

                binding.studioProgressBar.visibility = View.GONE
                binding.studioRecycler.visibility = View.VISIBLE
                if (author!!.yearMedia.isNullOrEmpty()) {
                    binding.studioRecycler.visibility = View.GONE
                }
                val titlePosition = arrayListOf<Int>()
                val concatAdapter = ConcatAdapter()
                val map = author!!.yearMedia ?: return@observe
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

                binding.charactersRecycler.visibility = View.VISIBLE
                binding.charactersText.visibility = View.VISIBLE
                binding.charactersRecycler.adapter =
                    CharacterAdapter(author!!.character ?: arrayListOf())
                binding.charactersRecycler.layoutManager =
                    LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
                if (author!!.character.isNullOrEmpty()) {
                    binding.charactersRecycler.visibility = View.GONE
                    binding.charactersText.visibility = View.GONE
                }
            }
        }
        val live = Refresh.activity.getOrPut(this.hashCode()) { MutableLiveData(true) }
        live.observe(this) {
            if (it) {
                scope.launch {
                    if (author != null)
                        withIOContext { model.loadAuthor(author!!) }
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