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
import ani.himitsu.settings.paging.AnimeExtensionAdapter
import ani.himitsu.settings.paging.AnimeExtensionsViewModel
import ani.himitsu.settings.paging.AnimeExtensionsViewModelFactory
import ani.himitsu.settings.paging.OnAnimeInstallClickListener
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import rx.android.schedulers.AndroidSchedulers
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeExtensionsFragment : Fragment(),
    SearchQueryHandler, OnAnimeInstallClickListener {
    private var _binding: FragmentExtensionsBinding? = null
    private val binding by lazy { _binding!! }

    private val animeExtensionManager: AnimeExtensionManager = Injekt.get()
    private val viewModel: AnimeExtensionsViewModel by viewModels {
        AnimeExtensionsViewModelFactory(animeExtensionManager)
    }

    private val animeAdapter by lazy {
        AnimeExtensionAdapter(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExtensionsBinding.inflate(inflater, container, false)

        binding.allExtensionsRecyclerView.apply {
            isNestedScrollingEnabled = false
            adapter = animeAdapter
            layoutManager = LinearLayoutManager(context)
            FastScrollerBuilder(this).useMd2Style().build().setPadding(0, 0, 0, 0)
        }

        lifecycleScope.launch {
            viewModel.pagerFlow.collectLatest {
                animeAdapter.submitData(it)
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

    override fun onInstallClick(pkg: AnimeExtension.Available) {
        val context = requireContext()
        if (isAdded) {
            val notificationManager =
                requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val installerSteps = InstallerSteps(notificationManager, context)
            // Start the installation process
            animeExtensionManager.installExtension(pkg)
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