package ani.himitsu.parsers

import android.content.res.Configuration
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import ani.himitsu.databinding.ActivityParserTestBinding
import ani.himitsu.initActivity
import ani.himitsu.media.MediaType
import ani.himitsu.statusBarHeight
import ani.himitsu.themes.ThemeManager
import bit.himitsu.withFlexibleMargin
import com.xwray.groupie.GroupieAdapter

class ParserTestActivity : AppCompatActivity() {
    private lateinit var binding: ActivityParserTestBinding
    val adapter = GroupieAdapter()
    val extensionNames: MutableList<String> = mutableListOf()
    val extensionsToTest: MutableList<ExtensionTestItem> = mutableListOf()

    var extensionType = MediaType.ANIME
    var testType = "ping"
    var searchQuery = "Chainsaw Man"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        initActivity(this)
        binding = ActivityParserTestBinding.inflate(layoutInflater)
        binding.settingsContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = statusBarHeight
        }
        binding.extensionResultsRecyclerView.withFlexibleMargin(resources.configuration)
        setContentView(binding.root)

        binding.extensionResultsRecyclerView.adapter = adapter
        binding.extensionResultsRecyclerView.layoutManager = LinearLayoutManager(
            this,
            LinearLayoutManager.VERTICAL,
            false
        )
        binding.backButton.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        binding.optionsLayout.setOnClickListener {
            ExtensionTestSettingsBottomDialog.newInstance(this)
                .show(supportFragmentManager, "extension_test_settings")
        }
    }

    fun runExtensionTest() {
        extensionsToTest.forEach { it.cancelJob() }
        extensionsToTest.clear()
        adapter.clear()
        extensionNames.forEach { name ->
            val extension = when (extensionType) {
                MediaType.ANIME -> {
                    AnimeSources.list.find { source -> source.name == name }?.get?.value
                }

                MediaType.MANGA -> {
                    MangaSources.list.find { source -> source.name == name }?.get?.value
                }

                MediaType.NOVEL -> {
                    NovelSources.list.find { source -> source.name == name }?.get?.value
                }
            }
            extension?.let {
                extensionsToTest.add(ExtensionTestItem(extensionType, testType, it, searchQuery))
            }
        }

        extensionsToTest.forEach {
            adapter.add(it)
            it.startTest()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        binding.extensionResultsRecyclerView.withFlexibleMargin(newConfig)
    }
}