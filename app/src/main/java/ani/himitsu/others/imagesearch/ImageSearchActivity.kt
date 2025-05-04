package ani.himitsu.others.imagesearch

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ani.himitsu.R
import ani.himitsu.connections.anilist.AniList
import ani.himitsu.currContext
import ani.himitsu.databinding.ActivityImageSearchBinding
import ani.himitsu.initActivity
import ani.himitsu.media.MediaDetailsActivity
import ani.himitsu.navBarHeight
import ani.himitsu.themes.ThemeManager
import ani.himitsu.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.util.lang.withUIContext

class ImageSearchActivity : AppCompatActivity() {
    private lateinit var binding: ActivityImageSearchBinding
    private val viewModel: ImageSearchViewModel by viewModels()

    private val imageSelectionLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { imageUri ->
                lifecycleScope.launch(Dispatchers.IO) {
                    withUIContext {
                        binding.progressBar.visibility = View.VISIBLE
                    }
                    val inputStream = contentResolver.openInputStream(imageUri)

                    if (inputStream != null) viewModel.analyzeImage(inputStream)
                    else toast(getString(R.string.error_loading_image))

                    withUIContext {
                        binding.progressBar.visibility = View.GONE
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initActivity(this)
        ThemeManager(this).applyTheme()
        binding = ActivityImageSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.uploadImage.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = navBarHeight
        }
        binding.uploadImage.setOnClickListener {
            viewModel.clearResults()
            imageSelectionLauncher.launch("image/*")
        }
        binding.imageSearchTitle.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        viewModel.searchResultLiveData.observe(this) { result ->
            result?.let { displayResult(it) }
        }

    }

    private fun displayResult(result: ImageSearchViewModel.SearchResult) {
        val recyclerView: RecyclerView = findViewById(R.id.recyclerView)
        val searchResults: List<ImageSearchViewModel.ImageResult> = result.result.orEmpty()
        val adapter = ImageSearchResultAdapter(searchResults)

        adapter.setOnItemClickListener(object : ImageSearchResultAdapter.OnItemClickListener {
            override fun onItemClick(searchResult: ImageSearchViewModel.ImageResult) {
                lifecycleScope.launch(Dispatchers.IO) {
                    val id = searchResult.anilist?.id?.toInt()
                    if (id == null) {
                        toast(getString(R.string.no_anilist_id_found))
                        return@launch
                    }
                    val media = AniList.query.getMedia(id, false)

                    withUIContext {
                        media?.let {
                            startActivity(
                                Intent(this@ImageSearchActivity, MediaDetailsActivity::class.java)
                                    .putExtra("media", it)
                            )
                        }
                    }
                }
            }
        })

        recyclerView.post {
            recyclerView.adapter = adapter
            recyclerView.layoutManager = LinearLayoutManager(currContext())
        }
    }
}