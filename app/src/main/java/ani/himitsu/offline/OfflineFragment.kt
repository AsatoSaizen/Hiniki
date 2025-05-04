package ani.himitsu.offline

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import ani.himitsu.R
import ani.himitsu.Refresh
import ani.himitsu.databinding.FragmentOfflineBinding
import ani.himitsu.databinding.ItemTitleRecyclerBinding
import ani.himitsu.isOnline
import ani.himitsu.navBarHeight
import ani.himitsu.settings.SettingsDialogFragment
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.statusBarHeight
import bit.himitsu.content.reboot
import bit.himitsu.manami.OfflineAnimeDB
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.util.lang.withUIContext

class OfflineFragment : Fragment() {
    private var offline = false
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentOfflineBinding.inflate(inflater, container, false)
        binding.refreshContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = statusBarHeight
            bottomMargin = navBarHeight
        }
        offline = PrefManager.getVal(PrefName.OfflineMode)
        binding.noInternet.text =
            if (offline) getString(R.string.offline_mode) else getString(R.string.no_internet)
        binding.refreshButton.text = getString(if (offline) R.string.go_online else R.string.refresh)
        binding.refreshButton.setOnClickListener {
            if (offline) PrefManager.setVal(PrefName.OfflineMode, false)
            if (requireContext().isOnline) {
                requireContext().reboot()
            } else {
                requireActivity().recreate()
                Refresh.all()
            }
        }
        binding.settingsButton.setOnClickListener {
            SettingsDialogFragment.newInstance(SettingsDialogFragment.Companion.PageType.OfflineHOME).show(
                requireActivity().supportFragmentManager,
                "dialog"
            )
        }

        lifecycleScope.launch(Dispatchers.IO) {
            if (!PrefManager.getVal<Boolean>(PrefName.OfflineRes)) return@launch
            withUIContext {
                binding.noInternetSad.visibility = View.GONE
            }
            val animeAdapter = OfflineMediaAdapter(
                OfflineAnimeDB(requireActivity()).getSavedAnime()
            )
            if (animeAdapter.itemCount > 0) {
                withUIContext {
                    ItemTitleRecyclerBinding.inflate(
                        LayoutInflater.from(context),
                        binding.offlineContainer,
                        false
                    ).apply {
                        itemTitle.setText(R.string.continue_watching)
                        itemRecycler.adapter = animeAdapter
                        itemRecycler.layoutManager = LinearLayoutManager(
                            requireContext(),
                            LinearLayoutManager.HORIZONTAL,
                            false
                        )
                        binding.offlineContainer.addView(root)
                    }
                }
            } else {
                val offlineAdapter = OfflineAnimeAdapter(
                    OfflineAnimeDB(requireActivity()).getOffllineAnime()
                )
                if (offlineAdapter.itemCount > 0) {
                    withUIContext {
                        ItemTitleRecyclerBinding.inflate(
                            LayoutInflater.from(context),
                            binding.offlineContainer,
                            false
                        ).apply {
                            itemTitle.setText(R.string.continue_watching)
                            itemRecycler.adapter = offlineAdapter
                            itemRecycler.layoutManager = LinearLayoutManager(
                                requireContext(),
                                LinearLayoutManager.HORIZONTAL,
                                false
                            )
                            binding.offlineContainer.addView(root)
                        }
                    }
                }
            }
            val mangaAdapter = OfflineMediaAdapter(
                OfflineAnimeDB(requireActivity()).getSavedManga()
            )
            if (mangaAdapter.itemCount > 0) {
                withUIContext {
                    ItemTitleRecyclerBinding.inflate(
                        LayoutInflater.from(context),
                        binding.offlineContainer,
                        false
                    ).apply {
                        itemTitle.setText(R.string.continue_reading)
                        itemRecycler.adapter = mangaAdapter
                        itemRecycler.layoutManager = LinearLayoutManager(
                            requireContext(),
                            LinearLayoutManager.HORIZONTAL,
                            false
                        )
                        binding.offlineContainer.addView(root)
                    }
                }
            }
        }

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        offline = PrefManager.getVal(PrefName.OfflineMode)
    }
}