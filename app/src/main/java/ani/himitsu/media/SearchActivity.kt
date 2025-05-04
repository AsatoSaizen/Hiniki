package ani.himitsu.media

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePaddingRelative
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ani.himitsu.connections.anilist.AniList
import ani.himitsu.connections.anilist.AniListSearch
import ani.himitsu.databinding.ActivitySearchBinding
import ani.himitsu.initActivity
import ani.himitsu.media.cereal.SearchResults
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.statusBarHeight
import ani.himitsu.themes.ThemeManager
import bit.himitsu.content.dpToColumns
import bit.himitsu.search.PPTSearchAdapter
import bit.himitsu.setBaseline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.util.lang.withIOContext
import java.util.Timer
import java.util.TimerTask

class SearchActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySearchBinding
    private val scope = lifecycleScope
    val model: AniListSearch by viewModels()

    var style: Int = 0

    private lateinit var mediaAdaptor: MediaAdaptor
    private lateinit var pptAdapter: PPTSearchAdapter
    private lateinit var progressAdapter: ProgressAdapter
    private lateinit var concatAdapter: ConcatAdapter
    private lateinit var headerAdaptor: SearchAdapter

    lateinit var result: SearchResults
    lateinit var updateChips: (() -> Unit)

    private var isKeyboardVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeManager(this).applyTheme()
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initActivity(this)

        // TODO: Investigate hardcoded values
        binding.searchRecyclerView.updatePaddingRelative(
            top = statusBarHeight
        )
        binding.searchRecyclerView.setBaseline()

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            isKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            insets
        }

        onBackPressedDispatcher.addCallback(this) {
            if (isKeyboardVisible) {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
            } else {
                finish()
            }
        }

        style = PrefManager.getVal(PrefName.SearchStyle)
        var listOnly: Boolean? = intent.getBooleanExtra("listOnly", false)
        if (!listOnly!!) listOnly = null

        val notSet = model.notSet
        if (model.notSet) {
            model.notSet = false
            model.searchResults = SearchResults(
                type = intent.getStringExtra("type") ?: "ANIME",
                isAdult = if (AniList.adult) intent.getBooleanExtra("hentai", false) else false,
                onList = listOnly,
                search = intent.getStringExtra("query"),
                genres = intent.getStringExtra("genre")?.let { mutableListOf(it) },
                tags = intent.getStringExtra("tag")?.let { mutableListOf(it) },
                sort = intent.getStringExtra("sortBy"),
                status = intent.getStringExtra("status"),
                source = intent.getStringExtra("source"),
                countryOfOrigin = intent.getStringExtra("country"),
                season = intent.getStringExtra("season"),
                seasonYear = if (intent.getStringExtra("type") == "ANIME") intent.getStringExtra("seasonYear")
                    ?.toIntOrNull() else null,
                startYear = if (intent.getStringExtra("type") == "MANGA") intent.getStringExtra("seasonYear")
                    ?.toIntOrNull() else null,
                results = mutableListOf(),
                hasNextPage = false
            )
        }

        result = model.searchResults

        progressAdapter = ProgressAdapter(searched = model.searched)
        mediaAdaptor = MediaAdaptor(
            style, model.searchResults.results, this, matchParent = true
        ).apply {
            extension = intent.getStringExtra("extension")
        }
        pptAdapter = PPTSearchAdapter(model, scope)
        headerAdaptor = SearchAdapter(this, model.searchResults.type)

        val gridSize = 120.dpToColumns
        val gridLayoutManager = GridLayoutManager(this, gridSize)
        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when (position) {
                    0 -> gridSize
                    concatAdapter.itemCount - 1 -> gridSize
                    else -> when (style) {
                        0 -> 1
                        else -> gridSize
                    }
                }
            }
        }

        concatAdapter = ConcatAdapter(headerAdaptor, mediaAdaptor, pptAdapter, progressAdapter)

        binding.searchRecyclerView.layoutManager = gridLayoutManager
        binding.searchRecyclerView.adapter = concatAdapter

        binding.searchRecyclerView.addOnScrollListener(object :
            RecyclerView.OnScrollListener() {
            override fun onScrolled(v: RecyclerView, dx: Int, dy: Int) {
                if (!v.canScrollVertically(1)) {
                    if (model.searchResults.hasNextPage && model.searchResults.results.isNotEmpty() && !loading) {
                        scope.launch(Dispatchers.IO) {
                            model.loadNextPage(model.searchResults)
                        }
                    }
                }
                super.onScrolled(v, dx, dy)
            }
        })

        model.getSearch().observe(this) {
            if (it != null) {
                model.searchResults.apply {
                    onList = it.onList
                    isAdult = it.isAdult
                    perPage = it.perPage
                    search = it.search
                    sort = it.sort
                    genres = it.genres
                    excludedGenres = it.excludedGenres
                    excludedTags = it.excludedTags
                    tags = it.tags
                    season = it.season
                    startYear = it.startYear
                    seasonYear = it.seasonYear
                    status = it.status
                    source = it.source
                    format = it.format
                    countryOfOrigin = it.countryOfOrigin
                    page = it.page
                    hasNextPage = it.hasNextPage
                }

                val prev = model.searchResults.results.size
                model.searchResults.results.addAll(it.results)
                mediaAdaptor.notifyItemRangeInserted(prev, it.results.size)

                progressAdapter.bar?.isVisible = it.hasNextPage
            }
        }

        model.allResults.observe(this) { collections ->
            collections?.let { pptAdapter.setContent(it) } ?: clearPPT()
        }

        progressAdapter.ready.observe(this) {
            if (it == true) {
                if (!notSet) {
                    if (!model.searched) {
                        model.searched = true
                        headerAdaptor.search?.run()
                    }
                } else {
                    headerAdaptor.requestFocus?.run()
                    headerAdaptor.requestFocus?.let { focus ->
                        Handler(Looper.getMainLooper()).postDelayed(focus, 150)
                    }
                }

                if (intent.getBooleanExtra("hideKeyboard", false)) {
                    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED)
                    search()
                }
            }
        }
    }

    fun searchPPT(query: String?, type: String? = null) {
        if (query.isNullOrBlank()) return
        val allResults = ArrayList<Any>()
        scope.launch {
            withIOContext {
                if (type == null || type == "CHARACTER")
                    allResults.addAll(AniList.query.searchCharacter(query))
                if (type == null || type == "STUDIO")
                    allResults.addAll(AniList.query.searchStudio(query))
                if (type == null || type == "STAFF")
                    allResults.addAll(AniList.query.searchStaff(query))
                if (type == "USER")
                    allResults.addAll(AniList.query.searchUser(query))
                withContext(scope.coroutineContext) {
                    model.allResults.postValue(allResults)
                    progressAdapter.bar?.visibility = View.GONE
                }
            }
        }
    }

    fun clearPPT() {
        pptAdapter.clear()
    }

    fun emptyMediaAdapter() {
        searchTimer.cancel()
        searchTimer.purge()
        mediaAdaptor.notifyItemRangeRemoved(0, model.searchResults.results.size)
        model.searchResults.results.clear()
        clearPPT()
        progressAdapter.bar?.visibility = View.GONE
    }

    private var searchTimer = Timer()
    private var loading = false
    fun search() {
        headerAdaptor.setHistoryVisibility(false)
        val size = model.searchResults.results.size
        model.searchResults.results.clear()
        binding.searchRecyclerView.post {
            mediaAdaptor.notifyItemRangeRemoved(0, size)
        }
        clearPPT()

        progressAdapter.bar?.visibility = View.VISIBLE

        searchTimer.cancel()
        searchTimer.purge()

        if (result.type != "ANIME" && result.type != "MANGA") {
            searchPPT(result.search, result.type)
            return
        }
        val timerTask: TimerTask = object : TimerTask() {
            override fun run() {
                scope.launch(Dispatchers.IO) {
                    loading = true
                    model.loadSearch(result)
                    loading = false
                }
            }
        }
        searchTimer = Timer()
        searchTimer.schedule(timerTask, 500)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun recycler() {
        mediaAdaptor.viewType = ViewType.entries[style]
        mediaAdaptor.notifyDataSetChanged()
    }

    var state: Parcelable? = null
    override fun onPause() {
        if (this::headerAdaptor.isInitialized) {
            headerAdaptor.addHistory()
        }
        super.onPause()
        state = binding.searchRecyclerView.layoutManager?.onSaveInstanceState()
    }

    override fun onResume() {
        super.onResume()
        binding.searchRecyclerView.layoutManager?.onRestoreInstanceState(state)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        binding.searchRecyclerView.setBaseline(newConfig)
    }
}
