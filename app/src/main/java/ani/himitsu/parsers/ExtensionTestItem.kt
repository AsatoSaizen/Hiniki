package ani.himitsu.parsers

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import androidx.core.view.isVisible
import ani.dantotsu.parsers.NovelParser
import ani.himitsu.R
import ani.himitsu.databinding.ItemExtensionTestBinding
import ani.himitsu.media.MediaType
import com.xwray.groupie.viewbinding.BindableItem
import eu.kanade.tachiyomi.util.system.getThemeColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.util.lang.withUIContext

class ExtensionTestItem(
    private var extensionType: MediaType,
    private var testType: String,
    private var extension: BaseParser,
    private var searchString: String
) : BindableItem<ItemExtensionTestBinding>() {
    private lateinit var binding: ItemExtensionTestBinding
    private lateinit var context: Context
    private var job: Job? = null
    private var isRunning = false
    private var pingResult: Triple<Int, Int?, String>? = null
    private var searchResultData: TestResult = TestResult()
    private var episodeResultData: TestResult = TestResult()
    private var serverResultData: TestResult = TestResult()

    override fun bind(viewBinding: ItemExtensionTestBinding, position: Int) {
        binding = viewBinding
        context = binding.root.context
        binding.extensionIconImageView.setImageDrawable(extension.icon)
        binding.extensionNameTextView.text = extension.name
        binding.extensionLoading.isVisible = isRunning
        hideAllResults()

        println(searchString)
        pingResult()
        searchResult()
        episodeResult()
        serverResult()
    }

    override fun getLayout(): Int {
        return R.layout.item_extension_test
    }

    override fun initializeViewBinding(view: View): ItemExtensionTestBinding {
        return ItemExtensionTestBinding.bind(view)
    }

    private fun hideAllResults() {
        if (::binding.isInitialized.not()) return
        binding.searchResultText.isVisible = false
        binding.episodeResultText.isVisible = false
        binding.serverResultText.isVisible = false
    }

    fun cancelJob() {
        job?.cancel()
        job = null
        binding.extensionLoading.isVisible = false
    }

    fun startTest() {
        pingResult = null
        searchResultData = TestResult()
        episodeResultData = TestResult()
        serverResultData = TestResult()
        isRunning = true
        hideAllResults()
        job?.cancel()
        job = Job()
        CoroutineScope(Dispatchers.IO + job!!).launch {
            when (extensionType) {
                MediaType.ANIME -> {
                    val extension = extension as AnimeParser
                    runAnimeTest(extension)
                }

                MediaType.MANGA -> {
                    val extension = extension as MangaParser
                    runMangaTest(extension)
                }

                MediaType.NOVEL -> {
                    val extension = extension as NovelParser
                    runNovelTest(extension)
                }
            }
        }
    }

    private suspend fun runAnimeTest(extension: AnimeParser) {
        if (testType == "ping") {
            pingResult = extension.ping()
            withUIContext { pingResult() }
            done()
            return
        }
        val searchStart = System.currentTimeMillis()
        val searchResult = extension.search(searchString)
        searchResultData.time = (System.currentTimeMillis() - searchStart).toInt()
        searchResultData.size = searchResult.size
        withUIContext { searchResult() }
        if (searchResultData.size == 0 || testType == "basic") {
            done()
            return
        }
        val episodeResultTime = System.currentTimeMillis()
        val episodeResult = extension.loadEpisodes("", null, searchResult.first().sAnime!!)
        episodeResultData.time = (System.currentTimeMillis() - episodeResultTime).toInt()
        episodeResultData.size = episodeResult.size
        withUIContext { episodeResult() }
        if (episodeResultData.size == 0) {
            done()
            return
        }
        val serverResultTime = System.currentTimeMillis()
        val serverResult = extension.loadVideoServers("", null, episodeResult.first().sEpisode!!)
        serverResultData.time = (System.currentTimeMillis() - serverResultTime).toInt()
        serverResultData.size = serverResult.size
        withUIContext { serverResult() }

        done()
    }

    private suspend fun runMangaTest(extension: MangaParser) {
        if (testType == "ping") {
            pingResult = extension.ping()
            withUIContext { pingResult() }
            done()
            return
        }
        val searchStart = System.currentTimeMillis()
        val searchResult = extension.search(searchString)
        searchResultData.time = (System.currentTimeMillis() - searchStart).toInt()
        searchResultData.size = searchResult.size
        withUIContext { searchResult() }
        if (searchResultData.size == 0 || testType == "basic") {
            done()
            return
        }
        val episodeResultStart = System.currentTimeMillis()
        val chapterResult = extension.loadChapters("", null, searchResult.first().sManga!!)
        episodeResultData.time = (System.currentTimeMillis() - episodeResultStart).toInt()
        episodeResultData.size = chapterResult.size
        withUIContext { episodeResult() }
        if (episodeResultData.size == 0) {
            done()
            return
        }
        val serverResultStart = System.currentTimeMillis()
        val serverResult = extension.loadImages("",  chapterResult.first().sChapter)
        serverResultData.time = (System.currentTimeMillis() - serverResultStart).toInt()
        serverResultData.size = serverResult.size
        withUIContext { serverResult() }

        done()
    }

    private suspend fun runNovelTest(extension: NovelParser) {
        if (testType == "ping") {
            withUIContext { pingResult() }
            done()
            return
        }
        val searchStart = System.currentTimeMillis()
        val searchResult = extension.search(searchString)
        searchResultData.time = (System.currentTimeMillis() - searchStart).toInt()
        searchResultData.size = searchResult.size
        withUIContext { searchResult() }
        if (searchResultData.size == 0 || testType == "basic") {
            done()
            return
        }
        val chapterResultTime = System.currentTimeMillis()
        val chapterResult = extension.loadBook(searchResult.first().link, null)
        episodeResultData.time = (System.currentTimeMillis() - chapterResultTime).toInt()
        episodeResultData.size = chapterResult.links.size
        withUIContext {
            episodeResult()
            serverResult()
        }

        done()
    }

    private suspend fun done() {
        if (::binding.isInitialized.not()) return
        isRunning = false
        withUIContext { binding.extensionLoading.isVisible = isRunning }
    }

    private fun pingResult() {
        if (::binding.isInitialized.not()) return
        if (extensionType == MediaType.NOVEL) {
            binding.pingResultText.isVisible = true
            binding.pingResultText.text = context.getString(R.string.test_not_supported)
            binding.pingResultText.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.round_info_outline_24, 0, 0, 0
            )
            return
        }
        if (pingResult == null) {
            binding.pingResultText.isVisible = false
            return
        } else {
            binding.pingResultText.isVisible = true
        }
        binding.pingResultText.setTextColor(
            context.getThemeColor(com.google.android.material.R.attr.colorPrimary)
        )
        val (code, time, message) = pingResult!!
        if (code == 200) {
            binding.pingResultText.text = context.getString(R.string.ping_success, time.toString())
            binding.pingResultText.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_circle_check, 0, 0, 0
            )
            return
        }
        binding.pingResultText.text =
            context.getString(R.string.ping_error, code.toString(), message)
        binding.pingResultText.setCompoundDrawablesWithIntrinsicBounds(
            R.drawable.ic_circle_cancel, 0, 0, 0
        )
        binding.pingResultText.setTextColor(
            context.getThemeColor(com.google.android.material.R.attr.colorError)
        )
    }

    private fun searchResult() {
        if (::binding.isInitialized.not()) return
        if (searchResultData.time == 0) {
            binding.searchResultText.isVisible = false
            return
        }
        binding.searchResultText.setTextColor(
            context.getThemeColor(com.google.android.material.R.attr.colorPrimary)
        )
        binding.searchResultText.isVisible = true
        if (searchResultData.size == 0) {
            val text = context.getString(R.string.title_search_test,
                context.getString(R.string.no_results_found))
            binding.searchResultText.text = text
            binding.searchResultText.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_circle_cancel, 0, 0, 0
            )
            binding.searchResultText.setTextColor(
                context.getThemeColor(com.google.android.material.R.attr.colorError)
            )
            return
        }
        val text = "${context.getString(
            R.string.title_search_test,
            context.getString(R.string.results_found, searchResultData.size.toString())
        )}\n${searchResultData.time}ms"
        binding.searchResultText.text = text
        binding.searchResultText.setCompoundDrawablesWithIntrinsicBounds(
            R.drawable.ic_circle_check, 0, 0, 0
        )
    }

    private fun episodeResult() {
        if (::binding.isInitialized.not()) return
        if (episodeResultData.time == 0) {
            binding.episodeResultText.isVisible = false
            return
        }
        binding.episodeResultText.setTextColor(
            context.getThemeColor(com.google.android.material.R.attr.colorPrimary)
        )
        binding.episodeResultText.isVisible = true
        if (episodeResultData.size == 0) {
            val text = when(extensionType) {
                MediaType.ANIME -> context.getString(R.string.episode_search_test,
                    context.getString(R.string.no_results_found))
                MediaType.MANGA -> context.getString(R.string.chapter_search_test,
                    context.getString(R.string.no_results_found))
                else -> context.getString(R.string.book_search_test,
                    context.getString(R.string.no_results_found))
            }
            binding.episodeResultText.text = text
            binding.episodeResultText.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_circle_cancel, 0, 0, 0
            )
            binding.episodeResultText.setTextColor(
                context.getThemeColor(com.google.android.material.R.attr.colorError)
            )
            return
        }
        val text = when(extensionType) {
            MediaType.ANIME -> context.getString(R.string.episode_search_test,
                context.getString(R.string.results_found, episodeResultData.size.toString()))
            MediaType.MANGA -> context.getString(R.string.chapter_search_test,
                context.getString(R.string.results_found, episodeResultData.size.toString()))
            else -> context.getString(R.string.book_search_test,
                context.getString(R.string.results_found, episodeResultData.size.toString()))
        }
        binding.episodeResultText.text = text + "\n${episodeResultData.time}ms"
        binding.episodeResultText.setCompoundDrawablesWithIntrinsicBounds(
            R.drawable.ic_circle_check, 0, 0, 0
        )
    }

    private fun serverResult() {
        if (::binding.isInitialized.not()) return
        if (extensionType == MediaType.NOVEL) {
            binding.pingResultText.isVisible = true
            binding.pingResultText.text = context.getString(R.string.test_not_supported)
            binding.pingResultText.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.round_info_outline_24, 0, 0, 0
            )
            return
        }
        if (serverResultData.time == 0) {
            binding.serverResultText.isVisible = false
            return
        }
        binding.serverResultText.setTextColor(
            context.getThemeColor(com.google.android.material.R.attr.colorPrimary)
        )
        binding.serverResultText.isVisible = true
        if (serverResultData.size == 0) {
            val text = "${when(extensionType) {
                MediaType.ANIME -> context.getString(R.string.video_search_test,
                    context.getString(R.string.no_results_found))
                MediaType.MANGA -> context.getString(R.string.image_search_test,
                    context.getString(R.string.no_results_found))
                else -> context.getString(R.string.book_search_test,
                    context.getString(R.string.no_results_found))
            }}\n${serverResultData.time}ms"
            binding.serverResultText.text = text
            binding.serverResultText.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_circle_cancel, 0, 0, 0
            )
            binding.serverResultText.setTextColor(
                context.getThemeColor(com.google.android.material.R.attr.colorError)
            )
            return
        }
        val text = when(extensionType) {
            MediaType.ANIME -> context.getString(R.string.video_search_test,
                context.getString(R.string.results_found, serverResultData.size.toString()))
            MediaType.MANGA -> context.getString(R.string.image_search_test,
                context.getString(R.string.results_found, serverResultData.size.toString()))
            else -> context.getString(R.string.book_search_test,
                context.getString(R.string.results_found, serverResultData.size.toString()))
        }
        binding.serverResultText.text = text
        binding.serverResultText.setCompoundDrawablesWithIntrinsicBounds(
            R.drawable.ic_circle_check, 0, 0, 0
        )
    }

    data class TestResult(
        var size: Int = 0,
        var time: Int = 0,
    )
}