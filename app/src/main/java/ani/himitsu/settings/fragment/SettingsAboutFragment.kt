package ani.himitsu.settings.fragment

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import ani.himitsu.BuildConfig
import ani.himitsu.R
import ani.himitsu.copyToClipboard
import ani.himitsu.databinding.ActivitySettingsAboutBinding
import ani.himitsu.openLinkInBrowser
import ani.himitsu.pop
import ani.himitsu.settings.FAQActivity
import ani.himitsu.settings.Settings
import ani.himitsu.settings.SettingsActivity
import ani.himitsu.settings.SettingsAdapter
import ani.himitsu.settings.ViewType
import ani.himitsu.settings.extension.DevelopersDialogFragment
import ani.himitsu.settings.extension.ForksDialogFragment
import ani.himitsu.toast
import ani.himitsu.view.dialog.CustomBottomDialog
import bit.himitsu.net.Bandwidth
import bit.himitsu.widget.onCompletedAction
import io.noties.markwon.Markwon
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import kotlinx.coroutines.launch

class SettingsAboutFragment : Fragment() {
    private lateinit var binding: ActivitySettingsAboutBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ActivitySettingsAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val settings = requireActivity() as SettingsActivity

        binding.apply {
            aboutSettingsBack.setOnClickListener {
                settings.backToMenu()
            }

            val settingsAdapter = SettingsAdapter(
                arrayListOf(
                    Settings(
                        type = ViewType.BUTTON,
                        nameRes = R.string.account_help,
                        icon = R.drawable.round_help_24,
                        onClick = {
                            val title = getString(R.string.account_help)
                            val full = getString(R.string.full_account_help)
                            CustomBottomDialog.newInstance().apply {
                                setTitleText(title)
                                addView(
                                    TextView(settings).apply {
                                        val markWon = Markwon.builder(settings)
                                            .usePlugin(SoftBreakAddsNewLinePlugin.create()).build()
                                        markWon.setMarkdown(this, full)
                                    }
                                )
                            }.show(settings.supportFragmentManager, "dialog")
                        },
                        isActivity = true
                    ),
                    Settings(
                        type = ViewType.BUTTON,
                        nameRes = R.string.faq,
                        descRes = R.string.faq_desc,
                        icon = R.drawable.round_help_24,
                        onClick = {
                            startActivity(Intent(settings, FAQActivity::class.java))
                        },
                        isActivity = true
                    ),
                    Settings(
                        type = ViewType.BUTTON,
                        nameRes = R.string.devs,
                        descRes = R.string.devs_desc,
                        icon = R.drawable.round_wheelchair_pickup_24,
                        onClick = {
                            DevelopersDialogFragment().show(settings.supportFragmentManager, "dialog")
                        },
                        isActivity = true
                    ),
                    Settings(
                        type = ViewType.BUTTON,
                        nameRes = R.string.forks,
                        descRes = R.string.forks_desc,
                        icon = R.drawable.round_fork_right_24,
                        onClick = {
                            ForksDialogFragment().show(settings.supportFragmentManager, "dialog")
                        },
                        isActivity = true
                    ),
                    Settings(
                        type = ViewType.BUTTON,
                        nameRes = R.string.disclaimer,
                        descRes = R.string.disclaimer_desc,
                        icon = R.drawable.round_info_outline_24,
                        onClick = {
                            CustomBottomDialog.newInstance().apply {
                                setTitleText(settings.getString(R.string.disclaimer))
                                addView(TextView(settings).apply {
                                    setText(R.string.full_disclaimer)
                                })
                                setNegativeButton(settings.getString(R.string.close)) {
                                    dismiss()
                                }
                                show(settings.supportFragmentManager, "dialog")
                            }
                        },
                        isActivity = true
                    ),
                    Settings(
                        type = ViewType.BUTTON,
                        nameRes = R.string.license,
                        descRes = R.string.license_desc,
                        icon = R.drawable.round_info_outline_24,
                        onClick = {
                            CustomBottomDialog.newInstance().apply {
                                setTitleText(settings.getString(R.string.license))
                                addView(TextView(settings).apply {
                                    text = settings.assets.open("license.txt").use {
                                        it.bufferedReader().use { it.readText() }
                                    }
                                })
                                setNegativeButton(settings.getString(R.string.close)) {
                                    dismiss()
                                }
                                show(settings.supportFragmentManager, "dialog")
                            }
                        },
                        isActivity = true
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

            settingBuyMeCoffee.setOnClickListener {
                lifecycleScope.launch { it.pop() }
                openLinkInBrowser(getString(R.string.coffee))
            }

            settingKoFi.setOnClickListener {
                lifecycleScope.launch { it.pop() }
                openLinkInBrowser(getString(R.string.kofi))
            }

            settingPayPal.setOnClickListener {
                lifecycleScope.launch { it.pop() }
                openLinkInBrowser(getString(R.string.paypal))
            }

            loginDiscord.setOnClickListener {
                openLinkInBrowser(getString(R.string.discord_url))
            }

            loginGitlab.setOnClickListener {
                openLinkInBrowser(getString(R.string.gitlab, getString(R.string.repo_gl)))
            }
            binding.loginHimitsu.setOnClickListener {
                openLinkInBrowser(getString(R.string.himitsu_url))
            }

            settingsVersion.apply {
                text = getString(R.string.version_current, BuildConfig.VERSION_NAME)

                setOnLongClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    copyToClipboard(SettingsActivity.getDeviceInfo(), false)
                    toast(getString(R.string.copied_device_info))
                    return@setOnLongClickListener true
                }
            }

            settingsBandwidth.text = Bandwidth.getNetworkSpeed()
        }
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as SettingsActivity).model.getQuery().value.let {
            binding.searchViewText.setText(it)
            (binding.settingsRecyclerView.adapter as SettingsAdapter)
                .getFilter()?.filter(it)
        }
        if (isAdded) {
            val popOff = Handler(Looper.getMainLooper())

            popOff.postDelayed({
                lifecycleScope.launch { binding.settingBuyMeCoffee.pop() }
            }, 250)

            popOff.postDelayed({
                lifecycleScope.launch { binding.settingKoFi.pop() }
            }, 500)

            popOff.postDelayed({
                lifecycleScope.launch { binding.settingPayPal.pop() }
            }, 750)

            popOff.postDelayed({
                lifecycleScope.launch { binding.settingsLinks.pop() }
            }, 1000)

            popOff.postDelayed({
                lifecycleScope.launch { binding.settingPayPal.pop() }
            }, 1250)

            popOff.postDelayed({
                lifecycleScope.launch { binding.settingKoFi.pop() }
            }, 1500)

            popOff.postDelayed({
                lifecycleScope.launch { binding.settingBuyMeCoffee.pop() }
            }, 1750)
        }
    }
}
