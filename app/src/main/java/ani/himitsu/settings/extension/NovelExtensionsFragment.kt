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
import ani.himitsu.parsers.novel.NovelExtension
import ani.himitsu.parsers.novel.NovelExtensionManager
import ani.himitsu.settings.InstallerSteps
import ani.himitsu.settings.SearchQueryHandler
import ani.himitsu.settings.paging.NovelExtensionAdapter
import ani.himitsu.settings.paging.NovelExtensionsViewModel
import ani.himitsu.settings.paging.NovelExtensionsViewModelFactory
import ani.himitsu.settings.paging.OnNovelInstallClickListener
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import rx.android.schedulers.AndroidSchedulers
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelExtensionsFragment : Fragment(),
    SearchQueryHandler, OnNovelInstallClickListener {
    private var _binding: FragmentExtensionsBinding? = null
    private val binding by lazy { _binding!! }

    private val novelExtensionManager: NovelExtensionManager = Injekt.get()
    private val viewModel: NovelExtensionsViewModel by viewModels {
        NovelExtensionsViewModelFactory(novelExtensionManager)
    }

    private val adapter by lazy {
        NovelExtensionAdapter(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExtensionsBinding.inflate(inflater, container, false)

        binding.allExtensionsRecyclerView.isNestedScrollingEnabled = false
        binding.allExtensionsRecyclerView.adapter = adapter
        binding.allExtensionsRecyclerView.layoutManager = LinearLayoutManager(context)

        lifecycleScope.launch {
            viewModel.pagerFlow.collectLatest { pagingData ->
                adapter.submitData(pagingData)
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

    override fun onInstallClick(pkg: NovelExtension.Available) {
        if (isAdded) {  // Check if the fragment is currently added to its activity
            val context = requireContext()
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val installerSteps = InstallerSteps(notificationManager, context)

            // Start the installation process
            novelExtensionManager.installExtension(pkg)
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