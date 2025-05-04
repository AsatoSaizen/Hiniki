package ani.himitsu.home

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import ani.himitsu.R
import ani.himitsu.connections.anilist.AniList
import ani.himitsu.databinding.FragmentLoginBinding
import ani.himitsu.openLinkInBrowser
import ani.himitsu.pop
import ani.himitsu.restart
import ani.himitsu.settings.saving.internal.PreferenceKeystore
import ani.himitsu.settings.saving.internal.PreferencePackager
import ani.himitsu.snackString
import ani.himitsu.toast
import ani.himitsu.util.Logger
import ani.himitsu.util.customAlertDialog
import bit.himitsu.os.Version
import bit.himitsu.webkit.setWebClickListeners
import bit.himitsu.widget.onCompletedActionText
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding by lazy { _binding!! }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.loginButton.setWebClickListeners(AniList.LOGIN_URL) {
            lifecycleScope.launch { binding.loginButton.pop() }
        }
        binding.loginDiscord.setOnClickListener {
            openLinkInBrowser(getString(R.string.discord_url))
        }
        binding.loginGitlab.setOnClickListener {
            openLinkInBrowser(getString(R.string.gitlab, getString(R.string.repo_gl)))
        }
        binding.loginHimitsu.setOnClickListener {
            openLinkInBrowser(getString(R.string.himitsu_url))
        }

        val openDocumentLauncher =
            registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->

                if (uri != null) {
                    requireActivity().contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    try {
                        val jsonString =
                            requireActivity().contentResolver.openInputStream(uri)?.readBytes()
                                ?: throw Exception("Error reading file")
                        val name =
                            DocumentFile.fromSingleUri(requireActivity(), uri)?.name ?: "settings"
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
                                        activity?.restart()
                                } else {
                                    toast(getString(R.string.password_cannot_be_empty))
                                }
                            }
                        } else if (name.endsWith(".ani")) {
                            val decryptedJson = jsonString.toString(Charsets.UTF_8)
                            if (PreferencePackager.unpack(decryptedJson))
                                activity?.restart()
                        } else {
                            toast(getString(R.string.invalid_file_type))
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

        binding.importSettingsButton.setOnClickListener {
            openDocumentLauncher.launch(arrayOf("*/*"))
        }

        if (!Version.isHimitsu) return
        try {
            with(requireActivity().packageManager) {
                getPackageInfo("ani.dantotsu.matagi", PackageManager.GET_META_DATA)
            }
            requireContext().customAlertDialog().apply {
                setCancelable(false)
                setTitle(R.string.conversion_title)
                setMessage(R.string.conversion_message)
                setPositiveButton(R.string.backup) {
                    startActivity(
                        Intent().setComponent(
                            ComponentName("ani.dantotsu.matagi", "ani.dantotsu.MainActivity")
                        )
                    )
                    requireActivity().finish()
                }
                setNeutralButton(R.string.uninstall) {
                    startActivity(
                        Intent(Intent.ACTION_DELETE)
                            .setData(Uri.parse("package:ani.dantotsu.matagi"))
                    )
                }
                setNegativeButton(R.string.close) { }
                show()
            }
        } catch (_: PackageManager.NameNotFoundException) { }
    }

    @SuppressLint("InflateParams")
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
            setPositiveButton(R.string.ok) { }
            setNegativeButton(R.string.cancel) {
                password.fill('0')
                callback(null)
            }
        }.show()

        fun handleOkAction(dialog: AlertDialog) {
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
        if (isAdded) {
            Handler(Looper.getMainLooper()).postDelayed({
                lifecycleScope.launch { binding.loginButton.pop() }
            }, 250)
        }
    }
}