package ani.himitsu.settings.extension

import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import ani.himitsu.databinding.FragmentExtensionsBinding
import ani.himitsu.settings.InstallerSteps
import ani.himitsu.settings.SearchQueryHandler
import ani.himitsu.settings.paging.MangaExtensionAdapter
import ani.himitsu.settings.paging.MangaExtensionsViewModel
import ani.himitsu.settings.paging.MangaExtensionsViewModelFactory
import ani.himitsu.settings.paging.OnMangaInstallClickListener
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import rx.android.schedulers.AndroidSchedulers
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaExtensionsFragment : Fragment(),
    SearchQueryHandler, OnMangaInstallClickListener {
    private var _binding: FragmentExtensionsBinding? = null
    private val binding by lazy { _binding!! }

    private val mangaExtensionManager: MangaExtensionManager = Injekt.get()
    private val viewModel: MangaExtensionsViewModel by viewModels {
        MangaExtensionsViewModelFactory(mangaExtensionManager)
    }

    private val mangaAdapter by lazy {
        MangaExtensionAdapter(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExtensionsBinding.inflate(inflater, container, false)

        binding.allExtensionsRecyclerView.apply {
            isNestedScrollingEnabled = false
            adapter = mangaAdapter
            layoutManager = LinearLayoutManager(context)
            FastScrollerBuilder(this).useMd2Style().build().setPadding(0, 0, 0, 0)
        }

        lifecycleScope.launch {
            viewModel.pagerFlow.collectLatest { pagingData ->
                mangaAdapter.submitData(pagingData)
            }
        }

        viewModel.invalidatePager() // Force a refresh of the pager
        return binding.root
    }

    override fun updateContentBasedOnQuery(query: String?) {
        viewModel.setSearchQuery(query ?: "")
    }

    override fun notifyDataChanged() {
        viewModel.invalidatePager()
    }

    override fun onInstallClick(pkg: MangaExtension.Available) {
        if (isAdded) {  // Check if the fragment is currently added to its activity
            val context = requireContext()
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val installerSteps = InstallerSteps(notificationManager, context)
            // Start the installation process
            mangaExtensionManager.installExtension(pkg)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { installStep -> installerSteps.onInstallStep(installStep) {} },
                    { error -> installerSteps.onError(error) {} },
                    { installerSteps.onComplete { viewModel.invalidatePager() } }
                )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView();_binding = null
    }


}