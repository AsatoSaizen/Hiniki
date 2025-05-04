package ani.himitsu.settings.fragment

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import ani.himitsu.Himitsu
import ani.himitsu.R
import ani.himitsu.Refresh
import ani.himitsu.databinding.ActivitySettingsSystemBinding
import ani.himitsu.databinding.DialogUserAgentBinding
import ani.himitsu.media.cereal.AniProgress
import ani.himitsu.restart
import ani.himitsu.savePrefsToDownloads
import ani.himitsu.settings.Page
import ani.himitsu.settings.START_PAGE
import ani.himitsu.settings.Settings
import ani.himitsu.settings.SettingsActivity
import ani.himitsu.settings.SettingsAdapter
import ani.himitsu.settings.ViewType
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.settings.saving.internal.Location
import ani.himitsu.settings.saving.internal.PreferenceKeystore
import ani.himitsu.settings.saving.internal.PreferencePackager
import ani.himitsu.snackString
import ani.himitsu.toast
import ani.himitsu.util.Logger
import ani.himitsu.util.StoragePermissions
import ani.himitsu.util.customAlertDialog
import bit.himitsu.manami.OfflineAnimeDB
import bit.himitsu.os.Version
import bit.himitsu.update.MatagiUpdater
import bit.himitsu.widget.onCompletedAction
import bit.himitsu.widget.onCompletedActionText
import com.bumptech.glide.Glide
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Firebase
import com.google.firebase.crashlytics.crashlytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import tachiyomi.core.util.lang.withUIContext

class SettingsSystemFragment : Fragment() {
    private lateinit var binding: ActivitySettingsSystemBinding

    private val exDns by lazy { resources.getStringArray(R.array.dnsProvider) }

    private val openDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val settings = requireActivity() as SettingsActivity
            val component = ComponentName(settings.packageName, settings::class.qualifiedName!!)
            if (uri != null) {
                settings.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                try {
                    val jsonString = settings.contentResolver.openInputStream(uri)?.readBytes()
                        ?: throw Exception("Error reading file")
                    val name = DocumentFile.fromSingleUri(settings, uri)?.name ?: "settings"
                    //.sani is encrypted, .ani is not
                    if (name.endsWith(".sani") && Version.isMarshmallow) {
                        passwordAlertDialog(false) { password ->
                            if (password != null) {
                                val salt = jsonString.copyOfRange(0, 16)
                                val encrypted = jsonString.copyOfRange(16, jsonString.size)
                                val decryptedJson = try {
                                    PreferenceKeystore.decryptWithPassword(
                                        password,
                                        encrypted,
                                        salt
                                    )
                                } catch (_: Exception) {
                                    toast(getString(R.string.incorrect_password))
                                    return@passwordAlertDialog
                                }
                                if (PreferencePackager.unpack(decryptedJson))
                                    settings.restart(component)
                            } else {
                                toast(getString(R.string.password_cannot_be_empty))
                            }
                        }
                    } else if (name.endsWith(".ani")) {
                        val decryptedJson = jsonString.toString(Charsets.UTF_8)
                        if (PreferencePackager.unpack(decryptedJson))
                            settings.restart(component)
                    } else {
                        toast(getString(R.string.unknown_file_type))
                    }
                } catch (e: Exception) {
                    Logger.log(e)
                    if (e is SecurityException) {
                        toast(R.string.security_exception)
                    } else {
                        snackString(getString(R.string.error_importing_settings))
                    }
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ActivitySettingsSystemBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val settings = requireActivity() as SettingsActivity
        val component = ComponentName(settings.packageName, settings::class.qualifiedName!!)

        binding.apply {
            systemSettingsBack.setOnClickListener {
                settings.backToMenu()
            }

            val settingsAdapter = SettingsAdapter(
                arrayListOf(
                    Settings(
                        type = ViewType.DROPDOWN,
                        nameRes = R.string.selected_dns,
                        icon = R.drawable.round_dns_24,
                        adapter = ArrayAdapter(
                            requireContext(),
                            R.layout.item_dropdown,
                            exDns
                        ),
                        defaultText = exDns[PrefManager.getVal(PrefName.DohProvider)],
                        onItemClick = { item ->
                            PrefManager.setVal(PrefName.DohProvider, item)
                            settings.restart(
                                ComponentName(settings.packageName, settings::class.qualifiedName!!)
                            )
                        }
                    ),
                    Settings(
                        type = ViewType.BUTTON,
                        nameRes = R.string.user_agent,
                        descRes = R.string.user_agent_desc,
                        icon = R.drawable.round_video_settings_24,
                        onClick = {
                            val dialogView = DialogUserAgentBinding.inflate(layoutInflater)
                            val editText = dialogView.userAgentTextBox
                            editText.setText(PrefManager.getVal<String>(PrefName.DefaultUserAgent))
                            settings.customAlertDialog().apply {
                                setTitle(R.string.user_agent)
                                setCustomView(dialogView.root)
                                setPositiveButton(getString(R.string.ok)) {
                                    PrefManager.setVal(
                                        PrefName.DefaultUserAgent,
                                        editText.text.toString()
                                    )
                                }
                                setNeutralButton(getString(R.string.reset)) {
                                    PrefManager.removeVal(PrefName.DefaultUserAgent)
                                    editText.setText("")
                                }
                                setNegativeButton(getString(R.string.cancel)) { }
                                show()
                            }
                        },
                        isDialog = true
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.biometric_title,
                        descRes = R.string.biometric_summary,
                        icon = R.drawable.ic_fingerprint_24,
                        pref = PrefName.SecureLock,
                        isVisible = canUseBiometrics()
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.check_app_updates,
                        descRes = R.string.check_app_updates_desc,
                        icon = R.drawable.round_new_releases_24,
                        isChecked = PrefManager.getVal(PrefName.CheckUpdate),
                        switch = { isChecked, _ ->
                            PrefManager.setVal(PrefName.CheckUpdate, isChecked)
                            if (!isChecked) {
                                snackString(getString(R.string.long_click_to_check_update))
                            }
                        },
                        onLongClick = {
                            lifecycleScope.launch(Dispatchers.IO) {
                                MatagiUpdater.check(settings, true)
                            }
                        }
                    ),
                    Settings(
                        type = ViewType.BUTTON,
                        nameRes = R.string.backup_restore,
                        descRes = R.string.backup_restore_desc,
                        icon = R.drawable.backup_restore,
                        onClick = {
                            StoragePermissions.downloadsPermission(settings as AppCompatActivity)
                            val selectedArray = mutableListOf(false)
                            var filteredLocations = Location.entries.filter { it.exportable }
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                                filteredLocations = filteredLocations.filter {
                                    it.location != Location.Protected.location
                                }
                            }
                            selectedArray.addAll(List(filteredLocations.size - 1) { false })
                            settings.customAlertDialog().apply {
                                setTitle(R.string.backup_restore)
                                setMultiChoiceItems(
                                    filteredLocations.map { it.name }.toTypedArray(),
                                    selectedArray.toBooleanArray(),
                                    { which, isChecked -> selectedArray[which] = isChecked },
                                    null
                                )
                                setPositiveButton(R.string.button_restore) {
                                    openDocumentLauncher.launch(arrayOf("*/*"))
                                }
                                setNegativeButton(R.string.button_backup) {
                                    if (!selectedArray.contains(true)) {
                                        toast(R.string.no_location_selected)
                                        return@setNegativeButton
                                    }
                                    val selected = filteredLocations
                                        .filterIndexed { index, _ -> selectedArray[index] }
                                    if (selected.contains(Location.Protected)) {
                                        passwordAlertDialog(true) { password ->
                                            if (password != null) {
                                                savePrefsToDownloads(
                                                    BACKUP_FILENAME,
                                                    PrefManager.exportAllPrefs(selected),
                                                    settings,
                                                    password
                                                )
                                            } else {
                                                toast(R.string.password_cannot_be_empty)
                                            }
                                        }
                                    } else {
                                        savePrefsToDownloads(
                                            BACKUP_FILENAME,
                                            PrefManager.exportAllPrefs(selected),
                                            settings,
                                            null
                                        )
                                    }
                                }
                                setNeutralButton(R.string.cancel) { }
                                show()
                            }
                        },
                        isDialog = true
                    ),
                    Settings(
                        type = ViewType.HEADER,
                        nameRes = R.string.offline_mode
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.client_mode,
                        descRes = R.string.client_mode_desc,
                        icon = R.drawable.round_security_24,
                        isChecked = PrefManager.getVal(PrefName.ClientMode),
                        switch = { isChecked, _ ->
                            PrefManager.setVal(PrefName.ClientMode, isChecked)
                            settings.restart(component)
                        }
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.offline_ani,
                        icon = R.drawable.round_track_changes_24,
                        isChecked = PrefManager.getVal(PrefName.OfflineAni),
                        switch = { isChecked, _ ->
                            PrefManager.setVal(PrefName.OfflineAni, isChecked)
                            if (!isChecked)
                                PrefManager.setVal<List<AniProgress>>(PrefName.PendingItems, listOf())
                        }
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.offline_dbs,
                        icon = R.drawable.round_storage_24,
                        pref = PrefName.OfflineDbs
                    ),
                    Settings(
                        type = ViewType.BUTTON,
                        nameRes = R.string.update_offline_db,
                        icon = R.drawable.round_data_object_24,
                        onClick = {
                            (binding.settingsRecyclerView.adapter as SettingsAdapter)
                                .setItemEnabled(9, false)
                            (binding.settingsRecyclerView.adapter as SettingsAdapter)
                                .setItemVisibility(10, true)
                            CoroutineScope(Dispatchers.IO).launch {
                                OfflineAnimeDB(settings).writeDatabase()
                                withUIContext {
                                    (binding.settingsRecyclerView.adapter as SettingsAdapter)
                                        .setItemEnabled(9, true)
                                    (binding.settingsRecyclerView.adapter as SettingsAdapter)
                                        .setItemVisibility(10, false)
                                    toast(R.string.offline_db_updated)
                                }
                            }
                        },
                        isDialog = true
                    ),
                    Settings(
                        type = ViewType.PROGRESS,
                        isVisible = false
                    ),
                    Settings(
                        type = ViewType.BUTTON,
                        nameRes = R.string.remove_offline_db,
                        icon = R.drawable.round_data_object_24,
                        onClick = {
                            runBlocking(Dispatchers.IO) { OfflineAnimeDB(settings).removeDatabase() }
                            PrefManager.setVal(PrefName.OfflineDbs, false)
                            (binding.settingsRecyclerView.adapter as SettingsAdapter)
                                .notifyItemChanged(8)
                            toast(R.string.offline_db_removed)

                        },
                        isDialog = true
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.offline_ext,
                        icon = R.drawable.ic_extension,
                        pref = PrefName.OfflineExt,
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.offline_res,
                        icon = R.drawable.round_mediation_24,
                        pref = PrefName.OfflineRes,
                    ),
                    Settings(
                        type = ViewType.HEADER,
                        nameRes = R.string.debugging
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.share_username_in_logs,
                        descRes = R.string.share_username_in_logs_desc,
                        icon = R.drawable.round_search_24,
                        pref = PrefName.SharedUserID
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.crashlytics,
                        icon = R.drawable.round_edit_note_24,
                        isChecked = PrefManager.getVal(PrefName.Crashlytics),
                        switch = { isChecked, _ ->
                            PrefManager.setVal(PrefName.Crashlytics, isChecked)
                            Firebase.crashlytics.isCrashlyticsCollectionEnabled = isChecked
                        },
                        isVisible = !PrefManager.getVal<Boolean>(PrefName.Lightspeed)
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.log_to_file,
                        descRes = R.string.logging_warning,
                        icon = R.drawable.round_edit_note_24,
                        isChecked = PrefManager.getVal(PrefName.LogToFile),
                        switch = { isChecked, _ ->
                            PrefManager.setVal(PrefName.LogToFile, isChecked)
                            Logger.clearLog()
                            if (isChecked) Logger.init(settings)
                        },
                        itemsEnabled = arrayOf(18),
                        isVisible = !PrefManager.getVal<Boolean>(PrefName.Lightspeed)
                    ),
                    Settings(
                        type = ViewType.BUTTON,
                        descRes = R.string.share_log_file,
                        icon = R.drawable.round_share_24,
                        onClick = { Logger.shareLog(settings) },
                        isEnabled = PrefManager.getVal<Boolean>(PrefName.LogToFile),
                    ),
                    Settings(
                        type = ViewType.SWITCH,
                        nameRes = R.string.disable_debug,
                        descRes = R.string.rogue_warning,
                        icon = R.drawable.round_bug_report_24,
                        isChecked = PrefManager.getVal(PrefName.Lightspeed),
                        switch = { isChecked, _ ->
                            PrefManager.setVal(PrefName.Lightspeed, isChecked)
                            if (isChecked) {
                                Firebase.crashlytics.isCrashlyticsCollectionEnabled = false
                            }
                            settings.restart(
                                component,
                                Bundle().apply { putString(START_PAGE, Page.SYSTEM.name) }
                            )
                        },
                        itemsShown = arrayOf(16, 17)
                    ),
                    Settings(
                        type = ViewType.BUTTON,
                        nameRes = R.string.clear_glide,
                        descRes = R.string.clear_glide_desc,
                        icon = R.drawable.round_coronavirus_24,
                        onClick = {
                            lifecycleScope.launch {
                                Glide.get(Himitsu.instance).clearDiskCache()
                            }
                            Glide.get(Himitsu.instance).clearMemory()
                            Refresh.all()
                        },
                        hasTransition = true
                    )
                )
            )
            settingsRecyclerView.apply {
                adapter = settingsAdapter
                layoutManager = LinearLayoutManager(settings, LinearLayoutManager.VERTICAL, false)
                // setHasFixedSize(true)
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

    private fun canUseBiometrics() : Boolean {
        val biometricManager = BiometricManager.from(requireActivity())
        return when (biometricManager.canAuthenticate(BIOMETRIC_STRONG
                or BiometricManager.Authenticators.DEVICE_CREDENTIAL)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> false
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> false
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> true
            else -> { false }
        }
    }

    private fun passwordAlertDialog(isExporting: Boolean, callback: (CharArray?) -> Unit) {
        val password = CharArray(16).apply { fill('0') }

        // Inflate the dialog layout
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_user_agent, null)
        val editText = dialogView.findViewById<TextInputEditText>(R.id.userAgentTextBox).apply {
            hint = getString(R.string.password)
            setSingleLine()
        }

        val dialog = requireContext().customAlertDialog().apply {
            setCancelable(false)
            setTitle(getString(R.string.enter_password))
            setCustomView(dialogView)
            setPositiveButton(R.string.ok) {  }
            setNegativeButton(R.string.cancel) {
                password.fill('0')
                callback(null)
            }
        }.show()

        fun handleOkAction(dialog: androidx.appcompat.app.AlertDialog) {
            if (editText?.text?.isNotBlank() == true) {
                editText.text?.toString()?.trim()?.toCharArray(password)
                dialog.dismiss()
                callback(password)
            } else {
                toast(getString(R.string.password_cannot_be_empty))
            }
        }
        editText?.setOnEditorActionListener(onCompletedActionText { handleOkAction(dialog) })
        val subtitleTextView = dialogView.findViewById<TextView>(R.id.subtitle)
        subtitleTextView?.visibility = View.VISIBLE
        if (!isExporting) subtitleTextView?.text = getString(R.string.enter_password_to_decrypt_file)

        // Override the positive button here
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            handleOkAction(dialog)
        }
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as SettingsActivity).model.getQuery().value.let {
            binding.searchViewText.setText(it)
            (binding.settingsRecyclerView.adapter as SettingsAdapter).getFilter()?.filter(it)
        }
    }

    companion object {
        private const val BACKUP_FILENAME = "HimitsuSettings"
    }
}