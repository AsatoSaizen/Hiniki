package ani.himitsu.settings.fragment

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import ani.himitsu.R
import ani.himitsu.copyToClipboard
import ani.himitsu.databinding.ActivitySettingsExtensionsBinding
import ani.himitsu.databinding.DialogUserAgentBinding
import ani.himitsu.databinding.ItemRepositoryBinding
import ani.himitsu.media.MediaType
import ani.himitsu.parsers.ParserTestActivity
import ani.himitsu.parsers.novel.NovelExtensionManager
import ani.himitsu.settings.Settings
import ani.himitsu.settings.SettingsActivity
import ani.himitsu.settings.SettingsAdapter
import ani.himitsu.settings.ViewType
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.util.customAlertDialog
import bit.himitsu.nio.repoLong
import bit.himitsu.nio.repoNoJson
import bit.himitsu.nio.repoNoRaw
import bit.himitsu.nio.repoParsed
import bit.himitsu.nio.repoShort
import bit.himitsu.os.Version
import bit.himitsu.widget.onCompletedAction
import bit.himitsu.widget.onCompletedActionText
import com.google.android.material.textfield.TextInputEditText
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import kotlin.getValue
import kotlin.text.startsWith

class SettingsExtensionsFragment : Fragment() {
    private lateinit var binding: ActivitySettingsExtensionsBinding
    private val extensionInstaller = Injekt.get<BasePreferences>().extensionInstaller()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ActivitySettingsExtensionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val settings = requireActivity() as SettingsActivity

        binding.apply {
            extensionSettingsBack.setOnClickListener {
                settings.backToMenu()
            }

            val settingsAdapter = SettingsAdapter(
                arrayListOf(
                    Settings(
                        type = ViewType.BUTTON,
                        nameRes = R.string.anime_add_repository,
                        desc = getString(R.string.add_repository_desc, MediaType.ANIME.text),
                        icon = R.drawable.ic_github_mark,
                        onClick = {
                            val dialogView = DialogUserAgentBinding.inflate(layoutInflater)
                            val editText = dialogView.userAgentTextBox.apply {
                                hint = getString(R.string.anime_add_repository)
                            }
                            settings.customAlertDialog().apply {
                                setTitle(R.string.anime_add_repository)
                                setCustomView(dialogView.root)
                                setPositiveButton(getString(R.string.ok)) {
                                    if (!editText.text.isNullOrBlank()) {
                                        it.attachView.processUserInput(
                                            editText.text.toString(),
                                            MediaType.ANIME
                                        )
                                    }
                                }
                                setNegativeButton(getString(R.string.cancel))
                                attach { dialog ->
                                    it.attachView.processEditorAction(
                                        dialog,
                                        editText,
                                        MediaType.ANIME
                                    )
                                }
                                show()
                            }
                        },
                        attach = {
                            it.attachView.setExtensionOutput(MediaType.ANIME)
                        }
                    ),
                    Settings(
                        type = ViewType.BUTTON,
                        nameRes = R.string.manga_add_repository,
                        desc = getString(R.string.add_repository_desc, MediaType.MANGA.text),
                        icon = R.drawable.ic_github_mark,
                        onClick = {
                            val dialogView = DialogUserAgentBinding.inflate(layoutInflater)
                            val editText = dialogView.userAgentTextBox.apply {
                                hint = getString(R.string.manga_add_repository)
                            }
                            settings.customAlertDialog().apply {
                                setTitle(R.string.manga_add_repository)
                                setCustomView(dialogView.root)
                                setPositiveButton(getString(R.string.ok)) {
                                    if (!editText.text.isNullOrBlank()) {
                                        it.attachView.processUserInput(
                                            editText.text.toString(),
                                            MediaType.MANGA
                                        )
                                    }
                                }
                                setNegativeButton(getString(R.string.cancel))
                                attach { dialog ->
                                    it.attachView.processEditorAction(
                                        dialog,
                                        editText,
                                        MediaType.MANGA
                                    )
                                }
                                show()
                            }
                        },
                        attach = {
                            it.attachView.setExtensionOutput(MediaType.MANGA)
                        }
                    ),
                    Settings(
                        type = ViewType.BUTTON,
                        nameRes = R.string.novel_add_repository,
                        desc = getString(R.string.add_repository_desc, MediaType.NOVEL.text),
                        icon = R.drawable.ic_github_mark,
                        onClick = {
                            val dialogView = DialogUserAgentBinding.inflate(layoutInflater)
                            val editText = dialogView.userAgentTextBox.apply {
                                hint = getString(R.string.novel_add_repository)
                            }
                            settings.customAlertDialog().apply {
                                setTitle(R.string.novel_add_repository)
                                setCustomView(dialogView.root)
                                setPositiveButton(getString(R.string.ok)) {
                                    if (!editText.text.isNullOrBlank()) {
                                        it.attachView.processUserInput(
                                            editText.text.toString(),
                                            MediaType.NOVEL
                                        )
                                    }
                                }
                                setNegativeButton(getString(R.string.cancel))
                                attach { dialog ->
                                    it.attachView.processEditorAction(
                                        dialog,
                                        editText,
                                        MediaType.NOVEL
                                    )
                                }
                                show()
                            }
                        },
                        attach = {
                            it.attachView.setExtensionOutput(MediaType.NOVEL)
                        }
                    ),
                    Settings(
                        type = ViewType.BUTTON,
                        nameRes = R.string.extension_test,
                        descRes = R.string.extension_test_desc,
                        icon = R.drawable.round_network_check_24,
                        isActivity = true,
                        onClick = {
                            settings.startActivity(
                                Intent(settings, ParserTestActivity::class.java)
                            )
                        }
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.force_legacy_installer,
                        descRes = R.string.force_legacy_installer_desc,
                        icon = R.drawable.round_new_releases_24,
                        isChecked = extensionInstaller.get() == BasePreferences.ExtensionInstaller.LEGACY,
                        switch = { isChecked, _ ->
                            if (isChecked) {
                                extensionInstaller.set(BasePreferences.ExtensionInstaller.LEGACY)
                            } else {
                                extensionInstaller.set(BasePreferences.ExtensionInstaller.PACKAGEINSTALLER)
                            }
                        },
                        isVisible = Version.isLowerThan(Build.VERSION_CODES.Q)
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.skip_loading_extension_icons,
                        descRes = R.string.skip_loading_extension_icons_desc,
                        icon = R.drawable.round_hide_image_24,
                        pref = PrefName.SkipExtensionIcons
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.NSFWExtention,
                        descRes = R.string.NSFWExtention_desc,
                        icon = R.drawable.round_no_adult_content_24,
                        pref = PrefName.NSFWExtension

                    )
                )
            )
            settingsRecyclerView.apply {
                adapter = settingsAdapter
                layoutManager = LinearLayoutManager(settings, LinearLayoutManager.VERTICAL, false)
                setHasFixedSize(true)
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

    fun ViewGroup.setExtensionOutput(type: MediaType) {
        val settings = requireActivity() as SettingsActivity
        removeAllViews()
        when (type) {
            MediaType.ANIME -> { PrefName.AnimeExtensionRepos }
            MediaType.MANGA -> { PrefName.MangaExtensionRepos }
            MediaType.NOVEL -> { PrefName.NovelExtensionRepos }
        }.let { repoList ->
            PrefManager.getVal<Set<String>>(repoList).forEach { item ->
                val repoView = ItemRepositoryBinding.inflate(
                    LayoutInflater.from(context), this, true
                )
                repoView.repositoryItem.text = item.repoShort
                repoView.deleteRepoItem.setOnClickListener {
                    AlertDialog.Builder(settings, R.style.MyDialog)
                        .setTitle(R.string.rem_repository)
                        .setMessage(item)
                        .setPositiveButton(getString(R.string.ok)) { dialog, _ ->
                            val repos = PrefManager.getVal<Set<String>>(repoList).minus(item)
                            PrefManager.setVal(repoList, repos)
                            setExtensionOutput(type)
                            dialog.dismiss()
                        }
                        .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                            dialog.dismiss()
                        }
                        .create()
                        .show()
                }
                repoView.repositoryItem.setOnClickListener {
                    modifyEditorAction(item, type)
                    true
                }
                repoView.repositoryItem.setOnLongClickListener  {
                    it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    copyToClipboard(item.repoLong)
                    true
                }
            }
            isVisible = childCount > 0
        }
    }

    fun ViewGroup.processUserInput(input: String, type: MediaType? = null) {
        val mediaType: MediaType
        var entry = input.repoNoJson
        when  {
            input.startsWith("aniyomi:") -> {
                mediaType = MediaType.ANIME
                entry = entry.removePrefix("aniyomi:").repoParsed
            }
            input.startsWith("tachiyomi:") -> {
                mediaType = MediaType.MANGA
                entry = entry.removePrefix("tachiyomi:").repoParsed
            }
            input.startsWith("novels:") -> {
                mediaType = MediaType.NOVEL
                entry = entry.removePrefix("novels:").repoParsed
            }
            else -> {
                mediaType = type ?: return
                entry = entry.repoNoRaw
            }
        }
        var prefName: PrefName = when (mediaType) {
            MediaType.ANIME -> { PrefName.AnimeExtensionRepos }
            MediaType.MANGA -> { PrefName.MangaExtensionRepos }
            MediaType.NOVEL -> { PrefName.NovelExtensionRepos }
        }

        val media = PrefManager.getVal<Set<String>>(prefName).plus(entry)
        PrefManager.setVal(prefName, media)
        setExtensionOutput(mediaType)
    }

    fun ViewGroup.processEditorAction(
        dialog: androidx.appcompat.app.AlertDialog,
        editText: TextInputEditText,
        mediaType: MediaType
    ) {
        editText.setOnEditorActionListener(onCompletedActionText {
            processUserInput(editText.text.toString(), mediaType)
            dialog.dismiss()
        })
    }

    fun ViewGroup.modifyEditorAction(item: String, mediaType: MediaType) {
        val settings = requireActivity() as SettingsActivity
        val prefName: PrefName = when (mediaType) {
            MediaType.ANIME -> { PrefName.AnimeExtensionRepos }
            MediaType.MANGA -> { PrefName.MangaExtensionRepos }
            MediaType.NOVEL -> { PrefName.NovelExtensionRepos }
        }
        val title = when (mediaType) {
            MediaType.ANIME -> { R.string.anime_edit_repository }
            MediaType.MANGA -> { R.string.manga_edit_repository }
            MediaType.NOVEL -> { R.string.novel_edit_repository }
        }
        val dialogView = DialogUserAgentBinding.inflate(layoutInflater)
        val editText = dialogView.userAgentTextBox.apply {
            hint = getString(title)
            setText(item.repoLong)
        }
        settings.customAlertDialog().apply {
            setTitle(getString(title))
            setCustomView(dialogView.root)
            setPositiveButton(getString(R.string.ok)) {
                if (!editText.text.isNullOrBlank()) {
                    val repos = PrefManager.getVal<Set<String>>(prefName).minus(item)
                    PrefManager.setVal(prefName, repos)
                    processUserInput(
                        editText.text.toString(),
                        mediaType
                    )
                }
            }
            setNegativeButton(getString(R.string.cancel))
            attach { dialog ->
                processEditorAction(
                    dialog,
                    editText,
                    mediaType
                )
            }
            show()
        }
    }

    override fun onDestroyView() {
        CoroutineScope(Dispatchers.IO).launch {
            val animeExtensionManager: AnimeExtensionManager by injectLazy()
            animeExtensionManager.findAvailableExtensions()
        }
        CoroutineScope(Dispatchers.IO).launch {
            val mangaExtensionManager: MangaExtensionManager by injectLazy()
            mangaExtensionManager.findAvailableExtensions()
        }
        CoroutineScope(Dispatchers.IO).launch {
            val novelExtensionManager: NovelExtensionManager by injectLazy()
            novelExtensionManager.findAvailableExtensions()
            novelExtensionManager.findAvailablePlugins()
        }
        super.onDestroyView()
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