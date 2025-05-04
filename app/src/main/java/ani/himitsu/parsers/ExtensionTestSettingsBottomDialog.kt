package ani.himitsu.parsers

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import ani.himitsu.R
import ani.himitsu.databinding.BottomSheetExtensionTestSettingsBinding
import ani.himitsu.media.MediaType
import ani.himitsu.parsers.novel.NovelExtensionManager
import ani.himitsu.toast
import ani.himitsu.view.dialog.BottomSheetDialogFragment
import com.xwray.groupie.GroupieAdapter
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ExtensionTestSettingsBottomDialog(val testActivity: ParserTestActivity) : BottomSheetDialogFragment() {
    private var _binding: BottomSheetExtensionTestSettingsBinding? = null
    private val binding get() = _binding!!
    private val adapter: GroupieAdapter = GroupieAdapter()
    private val animeExtension: AnimeExtensionManager = Injekt.get()
    private val mangaExtensions: MangaExtensionManager = Injekt.get()
    private val novelExtensions: NovelExtensionManager = Injekt.get()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = BottomSheetExtensionTestSettingsBinding.inflate(inflater, container, false)
        return _binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.extensionSelectionRecyclerView.adapter = adapter
        binding.extensionSelectionRecyclerView.layoutManager = LinearLayoutManager(
            context,
            LinearLayoutManager.VERTICAL,
            false
        )
        binding.searchViewText.setText(testActivity.searchQuery)
        binding.searchViewText.addTextChangedListener {
            testActivity.searchQuery = it.toString()
        }
        binding.extensionTypeRadioGroup.check(
            when (testActivity.extensionType) {
                MediaType.ANIME -> binding.animeRadioButton.id
                MediaType.MANGA -> binding.mangaRadioButton.id
                MediaType.NOVEL -> binding.novelsRadioButton.id
                else -> binding.animeRadioButton.id
            }
        )
        binding.testTypeRadioGroup.check(
            when (testActivity.testType) {
                "ping" -> binding.pingRadioButton.id
                "basic" -> binding.basicRadioButton.id
                "full" -> binding.fullRadioButton.id
                else -> binding.pingRadioButton.id
            }
        )
        binding.animeRadioButton.setOnCheckedChangeListener { _, b ->
            if (b) {
                testActivity.extensionType = MediaType.ANIME
                testActivity.extensionNames.clear()
                setupAdapter()
            }
        }
        binding.mangaRadioButton.setOnCheckedChangeListener { _, b ->
            if (b) {
                testActivity.extensionType = MediaType.MANGA
                testActivity.extensionNames.clear()
                setupAdapter()
            }
        }
        binding.novelsRadioButton.setOnCheckedChangeListener { _, b ->
            if (b) {
                testActivity.extensionType = MediaType.NOVEL
                testActivity.extensionNames.clear()
                setupAdapter()
            }
        }
        binding.pingRadioButton.setOnCheckedChangeListener { _, b ->
            if (b) testActivity.testType = "ping"
        }
        binding.basicRadioButton.setOnCheckedChangeListener { _, b ->
            if (b) testActivity.testType = "basic"
        }
        binding.fullRadioButton.setOnCheckedChangeListener { _, b ->
            if (b) testActivity.testType = "full"
        }
        setupAdapter()

        binding.startButton.setOnClickListener {
            if (testActivity.extensionNames.isEmpty()) {
                toast(R.string.no_extensions_selected)
                return@setOnClickListener
            }
            testActivity.runExtensionTest()
            dismiss()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun setupAdapter() {
        val namesAndUrls: Map<String,Drawable?> = when (testActivity.extensionType) {
            MediaType.ANIME -> animeExtension.installedExtensionsFlow.value.associate { it.name to it.icon }
            MediaType.MANGA -> mangaExtensions.installedExtensionsFlow.value.associate { it.name to it.icon }
            MediaType.NOVEL -> novelExtensions.installedExtensionsFlow.value.associate { it.name to it.icon }
            else -> emptyMap()
        }
        adapter.clear()
        namesAndUrls.forEach { (name, icon) ->
            val isSelected = testActivity.extensionNames.contains(name)
            adapter.add(ExtensionSelectItem(name, icon, isSelected, ::selectedCallback))
        }
    }

    private fun selectedCallback(name: String, isSelected: Boolean) {
        if (isSelected) {
            testActivity.extensionNames.add(name)
        } else {
            testActivity.extensionNames.remove(name)
        }
    }

    companion object {
        fun newInstance(testActivity: ParserTestActivity): ExtensionTestSettingsBottomDialog {
            return ExtensionTestSettingsBottomDialog(testActivity)
        }
    }
}