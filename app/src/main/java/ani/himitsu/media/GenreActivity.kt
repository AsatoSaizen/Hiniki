package ani.himitsu.media

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import ani.himitsu.connections.anilist.AniList
import ani.himitsu.connections.anilist.GenresViewModel
import ani.himitsu.databinding.ActivityGenreBinding
import ani.himitsu.initActivity
import ani.himitsu.navBarHeight
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.statusBarHeight
import ani.himitsu.themes.ThemeManager
import bit.himitsu.content.dpToColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class GenreActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGenreBinding
    val model: GenresViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeManager(this).applyTheme()
        binding = ActivityGenreBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initActivity(this)

        binding.listBackButton.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.genreContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin += statusBarHeight
            bottomMargin += navBarHeight
        }
        val type = intent.getStringExtra("type")
        if (type != null) {
            val adapter = GenreAdapter(type, true)
            model.doneListener = {
                MainScope().launch {
                    binding.mediaInfoGenresProgressBar.visibility = View.GONE
                }
            }
            if (model.genres != null) {
                adapter.genres = model.genres!!
                adapter.pos = ArrayList(model.genres!!.keys)
                if (model.done) model.doneListener?.invoke()
            }
            binding.mediaInfoGenresRecyclerView.adapter = adapter

            binding.mediaInfoGenresRecyclerView.layoutManager = GridLayoutManager(
                this, 156.dpToColumns
            )

            lifecycleScope.launch(Dispatchers.IO) {
                model.loadGenres(
                    AniList.genres ?: loadLocalGenres() ?: arrayListOf()
                ) {
                    MainScope().launch {
                        adapter.addGenre(it)
                    }
                }
            }
        }
    }

    private fun loadLocalGenres(): ArrayList<String>? {
        val genres = PrefManager.getVal<Set<String>>(PrefName.GenresList)
            .toMutableList()
        return if (genres.isEmpty()) {
            null
        } else {
            //sort alphabetically
            genres.sort().let { genres as ArrayList<String> }
        }
    }
}