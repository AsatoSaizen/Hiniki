package ani.himitsu

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.drawable.Animatable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AnticipateInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.core.view.doOnAttach
import androidx.core.view.updateLayoutParams
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.viewpager2.adapter.FragmentStateAdapter
import ani.himitsu.connections.anilist.AniList
import ani.himitsu.connections.anilist.AniListHomeViewModel
import ani.himitsu.databinding.ActivityMainBinding
import ani.himitsu.databinding.DialogUserAgentBinding
import ani.himitsu.databinding.SplashScreenBinding
import ani.himitsu.home.AnimeFragment
import ani.himitsu.home.HomeFragment
import ani.himitsu.home.LoginFragment
import ani.himitsu.home.MangaFragment
import ani.himitsu.home.NoInternet
import ani.himitsu.media.MediaDetailsActivity
import ani.himitsu.media.SearchActivity
import ani.himitsu.notifications.NotificationActivity
import ani.himitsu.notifications.TaskScheduler
import ani.himitsu.profile.ProfileActivity
import ani.himitsu.profile.activity.FeedActivity
import ani.himitsu.settings.ExtensionsActivity
import ani.himitsu.settings.SettingsDialogFragment
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefManager.asLiveBool
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.settings.saving.SharedPreferenceBooleanLiveData
import ani.himitsu.settings.saving.internal.PreferenceKeystore
import ani.himitsu.settings.saving.internal.PreferencePackager
import ani.himitsu.themes.ThemeManager
import ani.himitsu.util.Logger
import ani.himitsu.util.customAlertDialog
import ani.himitsu.view.dialog.CustomBottomDialog
import bit.himitsu.content.toPx
import bit.himitsu.os.Version
import bit.himitsu.setNavigationTransparent
import bit.himitsu.setStatusTransparent
import bit.himitsu.torrServerStop
import bit.himitsu.update.MatagiUpdater
import bit.himitsu.withFlexibleMargin
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import eu.kanade.domain.source.service.SourcePreferences
import io.noties.markwon.Markwon
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.joery.animatedbottombar.AnimatedBottomBar
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.Serializable


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var incognitoLiveData: SharedPreferenceBooleanLiveData

    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo
    private var hasConfirmedSession = false
    private var hasCompletedLoading = -1

    @SuppressLint("InternalInsetResource", "DiscouragedApi")
    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager(this).applyTheme()
        super.onCreate(savedInstanceState)

        if (PrefManager.getVal(PrefName.SecureLock)) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }

        // get FRAGMENT_CLASS_NAME from intent
        val fragment = intent.getStringExtra("FRAGMENT_CLASS_NAME")

        if (Himitsu.isAndroidTV) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } // Forced landscape for Android TV

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val action = intent.action
        if (Intent.ACTION_VIEW == action && intent.type != null && intent.data != null) {
            val uri: Uri = intent.data!!
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            try {
                val jsonString = contentResolver.openInputStream(uri)?.readBytes()
                    ?: throw Exception("Error reading file")
                val name = DocumentFile.fromSingleUri(this, uri)?.name ?: "settings"
                //.sani is encrypted, .ani is not
                if (name.endsWith(".sani") && Version.isMarshmallow) {
                    passwordAlertDialog { password ->
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
                            if (PreferencePackager.unpack(decryptedJson)) {
                                val intent = Intent(this, this.javaClass)
                                this.finish()
                                startActivity(intent)
                            }
                        } else {
                            toast(getString(R.string.password_cannot_be_empty))
                        }
                    }
                } else if (name.endsWith(".ani")) {
                    val decryptedJson = jsonString.toString(Charsets.UTF_8)
                    if (PreferencePackager.unpack(decryptedJson)) {
                        val intent = Intent(this, this.javaClass)
                        this.finish()
                        startActivity(intent)
                    }
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
        } else if (PrefManager.getVal<String>(PrefName.LastBuildHash) != BuildConfig.COMMIT) {
            Himitsu.instance.clearObsoletePrefs()
            PrefManager.setVal(PrefName.LastBuildHash, BuildConfig.COMMIT)
        }

        TaskScheduler.scheduleSingleWork(this)

        initMainActivity()
        bottomBar = binding.includedNavbar.navbar.apply {
            background = if (Version.isNougat) {
                val backgroundDrawable = background as GradientDrawable
                val currentColor = backgroundDrawable.color?.defaultColor ?: 0
                val semiTransparentColor = (currentColor and 0x00FFFFFF) or 0xF9000000.toInt()
                backgroundDrawable.apply {
                    setColor(semiTransparentColor)
                }
            } else {
                ContextCompat.getDrawable(this@MainActivity, R.drawable.bottom_nav_gray)
            }
            updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
                    16.toPx
                else
                    24.toPx
            }
        }

        binding.incognito.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = statusBarHeight
        }
        incognitoLiveData = PrefManager.getLiveVal(PrefName.Incognito, false).asLiveBool()
        incognitoLiveData.observe(this) {
            if (it) {
                val slideDownAnim = ObjectAnimator.ofFloat(
                    binding.incognito,
                    View.TRANSLATION_Y,
                    -(binding.incognito.height.toFloat() + statusBarHeight),
                    0f
                )
                slideDownAnim.duration = 200
                slideDownAnim.start()
                binding.incognito.visibility = View.VISIBLE
            } else {
                val slideUpAnim = ObjectAnimator.ofFloat(
                    binding.incognito,
                    View.TRANSLATION_Y,
                    0f,
                    -(binding.incognito.height.toFloat() + statusBarHeight)
                )
                slideUpAnim.duration = 200
                slideUpAnim.start()
                // wait for animation to finish
                Handler(Looper.getMainLooper()).postDelayed(
                    { binding.incognito.visibility = View.GONE },
                    200
                )
            }
        }
        incognitoNotification(this)

        var doubleBackToExitPressedOnce = false
        onBackPressedDispatcher.addCallback(this) {
            if (bottomBar.selectedIndex != 1) {
                bottomBar.selectTabAt(1)
            } else {
                if (doubleBackToExitPressedOnce) {
                    finishAndRemoveTask()
                    return@addCallback
                }
                doubleBackToExitPressedOnce = true
                snackString(this@MainActivity.getString(R.string.back_to_exit)).apply {
                    this?.addCallback(object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                        override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                            super.onDismissed(transientBottomBar, event)
                            doubleBackToExitPressedOnce = false
                            SettingsDialogFragment.newInstance(
                                SettingsDialogFragment.Companion.PageType.HOME
                            ).show(
                                supportFragmentManager, "dialog"
                            )
                        }
                    })
                }
            }
        }

        binding.root.isMotionEventSplittingEnabled = false
        if (Version.isSnowCone) {
            splashScreen.setOnExitAnimationListener { splashScreenView ->
                ObjectAnimator.ofFloat(
                    splashScreenView,
                    View.TRANSLATION_Y,
                    0f,
                    -splashScreenView.height.toFloat()
                ).apply {
                    interpolator = AnticipateInterpolator()
                    duration = 200L
                    doOnEnd {
                        splashScreenView.remove()
                    }
                    start()
                }
            }
        } else {
            lifecycleScope.launch {
                val splash = SplashScreenBinding.inflate(layoutInflater)
                binding.root.addView(splash.root)
                (splash.splashImage.drawable as Animatable).start()

                delay(1200)

                ObjectAnimator.ofFloat(
                    splash.root,
                    View.TRANSLATION_Y,
                    0f,
                    -splash.root.height.toFloat()
                ).apply {
                    interpolator = AnticipateInterpolator()
                    duration = 200L
                    doOnEnd {
                        binding.root.removeView(splash.root)
                    }
                    start()
                }
            }
        }

        binding.root.doOnAttach {
            initMainActivity()
            val preferences: SourcePreferences = Injekt.get()
            if (preferences.animeExtensionUpdatesCount().get() > 0
                || preferences.mangaExtensionUpdatesCount().get() > 0) {
                Snackbar.make(
                    window.decorView.findViewById(android.R.id.content),
                    R.string.extension_updates_available,
                    Snackbar.LENGTH_LONG
                ).apply {
                    setAction(R.string.review) {
                        this.dismiss()
                        startActivity(Intent(this@MainActivity, ExtensionsActivity::class.java))
                    }
                    show()
                }
            }
        }

        selectedOption = if (fragment != null) {
            when (fragment) {
                AnimeFragment::class.java.name -> 0
                HomeFragment::class.java.name -> 1
                MangaFragment::class.java.name -> 2
                else -> 1
            }
        } else {
            PrefManager.getVal(PrefName.DefaultStartUpTab)
        }

        if (intent.hasExtra("FRAGMENT_TO_LOAD")) {
            intent.extras?.let { extras ->
                val fragmentToLoad = extras.getString("FRAGMENT_TO_LOAD")
                val mediaId = extras.getInt("mediaId", -1)
                val commentId = extras.getInt("commentId", -1)
                val activityId = extras.getInt("activityId", -1)

                when {
                    fragmentToLoad == "NOTIFICATIONS" && activityId != -1 -> {
                        Logger.log("MainActivity, onCreate: $activityId")
                        val notificationIntent =
                            Intent(this, NotificationActivity::class.java).apply {
                                putExtra("FRAGMENT_TO_LOAD", "NOTIFICATIONS")
                                putExtra("activityId", activityId)
                            }
                        startActivity(notificationIntent)
                    }

                    fragmentToLoad == "FEED" && activityId != -1 -> {
                        val feedIntent = Intent(this, FeedActivity::class.java).apply {
                            putExtra("FRAGMENT_TO_LOAD", "FEED")
                            putExtra("activityId", activityId)

                        }
                        startActivity(feedIntent)
                    }

                    fragmentToLoad == "MEDIA" && mediaId != -1 -> {
                        val mediaIntent = Intent(this, MediaDetailsActivity::class.java).apply {
                            putExtra("FRAGMENT_TO_LOAD", fragmentToLoad)
                            putExtra("mediaId", mediaId)
                            putExtra("continue", intent.getBooleanExtra("continue", false))
                        }
                        startActivity(mediaIntent)
                    }

                    mediaId != -1 && commentId != -1 -> {
                        val detailIntent = Intent(this, MediaDetailsActivity::class.java).apply {
                            putExtra("FRAGMENT_TO_LOAD", fragmentToLoad)
                            putExtra("mediaId", mediaId)
                            putExtra("commentId", commentId)
                        }
                        startActivity(detailIntent)
                    }
                }
            }
        }
        val offlineMode: Boolean = PrefManager.getVal(PrefName.OfflineMode)
        if (!isOnline) {
            toast(this@MainActivity.getString(R.string.no_internet))
            startActivity(Intent(this, NoInternet::class.java))
        } else {
            if (offlineMode) {
                toast(this@MainActivity.getString(R.string.offline_mode))
                startActivity(Intent(this, NoInternet::class.java))
            } else {
                val model: AniListHomeViewModel by viewModels()
                binding.mainProgressBar.visibility = View.GONE
                val mainViewPager = binding.viewpager .apply {
                    isUserInputEnabled = false
                    adapter = ViewPagerAdapter(supportFragmentManager, lifecycle)
                }
                // mainViewPager.setPageTransformer(ZoomOutPageTransformer())
                bottomBar.setupWithViewPager2(mainViewPager)
                bottomBar.setOnTabSelectListener(object : AnimatedBottomBar.OnTabSelectListener {
                    override fun onTabSelected(
                        lastIndex: Int,
                        lastTab: AnimatedBottomBar.Tab?,
                        newIndex: Int,
                        newTab: AnimatedBottomBar.Tab
                    ) {
                        // bottomBar.animate().translationZ(12f).setDuration(200).start()
                        bottomBar.visibility = View.VISIBLE
                        selectedOption = newIndex
                        hasCompletedLoading += 1
                    }
                    override fun onTabReselected(index: Int, tab: AnimatedBottomBar.Tab) {
                        if (hasCompletedLoading < 1) return
                        when (index) {
                            0 -> {
                                this@MainActivity.startActivity(
                                    Intent(this@MainActivity, SearchActivity::class.java)
                                        .putExtra("type", "ANIME")
                                )
                            }
                            1 -> {
                                SettingsDialogFragment.newInstance(
                                    SettingsDialogFragment.Companion.PageType.HOME
                                ).show(
                                    this@MainActivity.supportFragmentManager,
                                    "dialog"
                                )
                            }
                            2 -> {
                                this@MainActivity.startActivity(
                                    Intent(this@MainActivity, SearchActivity::class.java)
                                        .putExtra("type", "MANGA")
                                    )
                            }
                        }
                    }
                })
                if (bottomBar.selectedIndex != selectedOption) {
                    mainViewPager.setCurrentItem(selectedOption, false)
                    bottomBar.selectTabAt(selectedOption, false)
                }
                // Load Data
                lifecycleScope.launch(Dispatchers.IO) {
                    model.loadMain()
                    intent.extras?.let { extras ->
                        val type = extras.getString("type")
                        when (type) {
                            "user" -> {
                                val username = extras.getString("username")
                                username?.toIntOrNull()?.let {
                                    startActivity(
                                        Intent(this@MainActivity, ProfileActivity::class.java)
                                            .putExtra("userId", it)
                                    )
                                } ?: startActivity(
                                    Intent(this@MainActivity, ProfileActivity::class.java)
                                        .putExtra("username", username)
                                )
                            }
                            "anime", "manga" -> {
                                val id = extras.getInt("mediaId", 0)
                                val isMAL = extras.getBoolean("mal", false)
                                lifecycleScope.launch(Dispatchers.IO) {
                                    if (id != 0) {
                                        AniList.query.getMedia(id, isMAL, type)?.let {
                                            startActivity(Intent(
                                                this@MainActivity,
                                                MediaDetailsActivity::class.java
                                            ).putExtra("media", it.apply {
                                                cameFromContinue =
                                                    extras.getBoolean("continue", false)
                                            } as Serializable))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (Version.isSnowCone && !(PrefManager.getVal<Boolean>(PrefName.AllowOpeningLinks))) {
                    CustomBottomDialog.newInstance().apply {
                        title = this@MainActivity.getString(R.string.auto_open_links)
                        val md = this@MainActivity.getString(R.string.auto_open_links_desc)
                        addView(TextView(this@MainActivity).apply {
                            val markWon =
                                Markwon.builder(this@MainActivity)
                                    .usePlugin(SoftBreakAddsNewLinePlugin.create()).build()
                            markWon.setMarkdown(this, md)
                        })

                        setNegativeButton(this@MainActivity.getString(R.string.no)) {
                            PrefManager.setVal(PrefName.AllowOpeningLinks, true)
                            dismiss()
                        }

                        setPositiveButton(this@MainActivity.getString(R.string.yes)) {
                            PrefManager.setVal(PrefName.AllowOpeningLinks, true)
                            tryWith(true) {
                                startActivity(
                                    Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS)
                                        .setData(Uri.parse("package:$packageName"))
                                )
                            }
                            dismiss()
                        }
                    }.show(supportFragmentManager, "links")
                }
            }
        }
    }

    private fun initMainActivity() {
        initActivity(this)
        window.setStatusTransparent()
        window.setNavigationTransparent()
        binding.includedNavbar.navbarContainer
            .withFlexibleMargin(resources.configuration, toRight = false)
    }

    override fun onStart() {
        super.onStart()
        CoroutineScope(Dispatchers.IO).launch {
            if (PrefManager.getVal(PrefName.CheckUpdate)) MatagiUpdater.check(this@MainActivity)
        }

        if (PrefManager.getVal(PrefName.SecureLock)) {
            promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.biometric_title))
                .setSubtitle(getString(R.string.biometric_details))
                .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                .setConfirmationRequired(false)
                .build()

            biometricPrompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        Toast.makeText(
                            currContext(),
                            getString(R.string.biometric_error, errString),
                            Toast.LENGTH_SHORT
                        ).show()
                        when (errorCode) {
                            BiometricPrompt.ERROR_HW_NOT_PRESENT,
                            BiometricPrompt.ERROR_HW_UNAVAILABLE,
                            BiometricPrompt.ERROR_NO_BIOMETRICS,
                            BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
                            BiometricPrompt.ERROR_VENDOR -> {
                                binding.biometricShield.visibility = View.GONE
                            }

                            BiometricPrompt.ERROR_CANCELED,
                            BiometricPrompt.ERROR_LOCKOUT,
                            BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
                            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                            BiometricPrompt.ERROR_NO_SPACE,
                            BiometricPrompt.ERROR_TIMEOUT,
                            BiometricPrompt.ERROR_UNABLE_TO_PROCESS,
                            BiometricPrompt.ERROR_USER_CANCELED -> finishAndRemoveTask()

                            // Prevent corrupting bulk conditions
                            BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED-> finishAndRemoveTask()

                            else -> biometricPrompt.authenticate(promptInfo)
                        }
                    }

                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult
                    ) {
                        super.onAuthenticationSucceeded(result)
                        Toast.makeText(
                            currContext(),
                            getString(R.string.biometric_success),
                            Toast.LENGTH_SHORT
                        ).show()
                        hasConfirmedSession = true
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        binding.biometricShield.visibility = View.GONE
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        Toast.makeText(
                            currContext(),
                            getString(R.string.biometric_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                        finishAndRemoveTask()
                    }
                })

            if (!hasConfirmedSession) {
                binding.biometricShield.visibility = View.VISIBLE
                biometricPrompt.authenticate(promptInfo)
            }
        }
    }

    fun setActiveNotificationCount() {
        supportFragmentManager.findFragmentByTag(
            "android:switcher:" + R.id.viewpager + ":" + binding.viewpager.currentItem
        )?.let { page ->
            when (binding.viewpager.currentItem) {
                0 -> (page as AnimeFragment).setActiveNotificationCount()
                1 -> if (AniList.token != null) (page as HomeFragment).setActiveNotificationCount()
                2 -> (page as MangaFragment).setActiveNotificationCount()
                else -> { } // Do nothing
            }
        }
    }

//    override fun onWindowFocusChanged(hasFocus: Boolean) {
//        super.onWindowFocusChanged(hasFocus)
//        if (PrefManager.getVal(PrefName.SecureLock)) {
//            binding.biometricShield.isGone = hasFocus && hasConfirmedSession
//        }
//    }

    override fun onDestroy() {
        hasCompletedLoading = -1
        hasConfirmedSession = false
        torrServerStop()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasExtra(AniList.LOGIN_URL)) {
            binding.viewpager.adapter = ViewPagerAdapter(supportFragmentManager, lifecycle)
            binding.viewpager.setCurrentItem(1, false)
            bottomBar.selectTabAt(1, false)
            Refresh.activity[1]!!.postValue(true)
        }
    }

    public override fun onUserLeaveHint() {
        torrServerStop()
        super.onUserLeaveHint()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        binding.includedNavbar.navbarContainer.withFlexibleMargin(newConfig, toRight = false)
        bottomBar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
                16.toPx
            else
                24.toPx
        }
    }

    private fun passwordAlertDialog(callback: (CharArray?) -> Unit) {
        val password = CharArray(16).apply { fill('0') }

        // Inflate the dialog layout
        val dialogView = DialogUserAgentBinding.inflate(layoutInflater)
        dialogView.userAgentTextBox.hint = getString(R.string.password)
        dialogView.subtitle.visibility = View.VISIBLE
        dialogView.subtitle.text = getString(R.string.enter_password_to_decrypt_file)

        val dialog = customAlertDialog().apply {
            setCancelable(false)
            setTitle(R.string.enter_password)
            setCustomView(dialogView.root)
            setPositiveButton(R.string.ok) { }
            setNegativeButton(R.string.cancel) {
                password.fill('0')
                callback(null)
            }
        }.show()

        // Override the positive button here
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val editText = dialog.findViewById<TextInputEditText>(R.id.userAgentTextBox)
            if (editText?.text?.isNotBlank() == true) {
                editText.text?.toString()?.trim()?.toCharArray(password)
                dialog.dismiss()
                callback(password)
            } else {
                toast(R.string.password_cannot_be_empty)
            }
        }
    }

    // ViewPager
    private class ViewPagerAdapter(fragmentManager: FragmentManager, lifecycle: Lifecycle) :
        FragmentStateAdapter(fragmentManager, lifecycle) {

        override fun getItemCount(): Int = 3

        override fun createFragment(position: Int): Fragment {
            when (position) {
                0 -> return AnimeFragment()
                1 -> return if (AniList.token != null) HomeFragment() else LoginFragment()
                2 -> return MangaFragment()
            }
            return if (AniList.token != null) HomeFragment() else LoginFragment()
        }
    }
}
