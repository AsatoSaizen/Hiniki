package ani.himitsu.widgets.statistics

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import ani.himitsu.databinding.StatisticsWidgetConfigureBinding
import ani.himitsu.themes.ThemeManager
import ani.himitsu.widgets.ColorDialog
import com.google.android.material.button.MaterialButton
import eltos.simpledialogfragment.SimpleDialog
import eltos.simpledialogfragment.color.SimpleColorDialog
import eu.kanade.tachiyomi.util.system.getThemeColor

/**
 * The configuration screen for the [ProfileStatsWidget] AppWidget.
 */
class ProfileStatsConfigure : AppCompatActivity(),
    SimpleDialog.OnDialogResultListener {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var isMonetEnabled = false
    private var onClickListener = View.OnClickListener {
        val context = this@ProfileStatsConfigure

        // It is the responsibility of the configuration activity to update the app widget
        val appWidgetManager = AppWidgetManager.getInstance(context)

        ProfileStatsWidget.updateAppWidget(
            context,
            appWidgetManager,
            appWidgetId
        )

        // Make sure we pass back the original appWidgetId
        val resultValue = Intent()
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, resultValue)
        finish()
    }
    private lateinit var binding: StatisticsWidgetConfigureBinding

    public override fun onCreate(savedInstanceState: Bundle?) {

        ThemeManager(this).applyTheme()
        super.onCreate(savedInstanceState)

        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)

        binding = StatisticsWidgetConfigureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        val prefs =
            getSharedPreferences(ProfileStatsWidget.getPrefsName(appWidgetId), Context.MODE_PRIVATE)
        val topBackground =
            prefs.getInt(ProfileStatsWidget.PREF_BACKGROUND_COLOR, Color.parseColor("#80000000"))
        (binding.topBackgroundButton as MaterialButton).iconTint =
            ColorStateList.valueOf(topBackground)
        binding.topBackgroundButton.setOnClickListener {
            ColorDialog.showColorDialog(
                this@ProfileStatsConfigure,
                topBackground,
                ProfileStatsWidget.PREF_BACKGROUND_COLOR
            )
        }
        val bottomBackground =
            prefs.getInt(ProfileStatsWidget.PREF_BACKGROUND_FADE, Color.parseColor("#00000000"))
        (binding.bottomBackgroundButton as MaterialButton).iconTint =
            ColorStateList.valueOf(bottomBackground)
        binding.bottomBackgroundButton.setOnClickListener {
            ColorDialog.showColorDialog(
                this@ProfileStatsConfigure,
                bottomBackground,
                ProfileStatsWidget.PREF_BACKGROUND_FADE
            )
        }
        val titleColor = prefs.getInt(ProfileStatsWidget.PREF_TITLE_TEXT_COLOR, Color.WHITE)
        (binding.titleColorButton as MaterialButton).iconTint = ColorStateList.valueOf(titleColor)
        binding.titleColorButton.setOnClickListener {
            ColorDialog.showColorDialog(
                this@ProfileStatsConfigure,
                titleColor,
                ProfileStatsWidget.PREF_TITLE_TEXT_COLOR
            )
        }
        val statsColor = prefs.getInt(ProfileStatsWidget.PREF_STATS_TEXT_COLOR, Color.WHITE)
        (binding.statsColorButton as MaterialButton).iconTint = ColorStateList.valueOf(statsColor)
        binding.statsColorButton.setOnClickListener {
            ColorDialog.showColorDialog(
                this@ProfileStatsConfigure,
                statsColor,
                ProfileStatsWidget.PREF_STATS_TEXT_COLOR
            )
        }
        binding.useAppTheme.setOnCheckedChangeListener { _, isChecked ->
            isMonetEnabled = isChecked
            binding.topBackgroundButton.isGone = isChecked
            binding.bottomBackgroundButton.isGone = isChecked
            binding.titleColorButton.isGone = isChecked
            binding.statsColorButton.isGone = isChecked
            if (isChecked) themeColors()
        }
        binding.addButton.setOnClickListener(onClickListener)

        // Find the widget id from the intent.
        appWidgetId = intent.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // If this activity was started with an intent without an app widget ID, finish with an error.
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
    }

    private fun themeColors() {
        val backgroundColor = getThemeColor(com.google.android.material.R.attr.colorSurface)
        val textColor = getThemeColor(com.google.android.material.R.attr.colorPrimary)
        val subTextColor = getThemeColor(com.google.android.material.R.attr.colorOnBackground)

        getSharedPreferences(
            ProfileStatsWidget.getPrefsName(appWidgetId),
            Context.MODE_PRIVATE
        ).edit().apply {
            putInt(ProfileStatsWidget.PREF_BACKGROUND_COLOR, backgroundColor)
            putInt(ProfileStatsWidget.PREF_BACKGROUND_FADE, backgroundColor)
            putInt(ProfileStatsWidget.PREF_TITLE_TEXT_COLOR, textColor)
            putInt(ProfileStatsWidget.PREF_STATS_TEXT_COLOR, subTextColor)
            apply()
        }
    }

    override fun onResult(dialogTag: String, which: Int, extras: Bundle): Boolean {
        if (which == SimpleDialog.OnDialogResultListener.BUTTON_POSITIVE) {
            if (!isMonetEnabled) {
                val prefs = getSharedPreferences(
                    ProfileStatsWidget.getPrefsName(appWidgetId),
                    Context.MODE_PRIVATE
                )
                when (dialogTag) {
                    ProfileStatsWidget.PREF_BACKGROUND_COLOR -> {
                        prefs.edit()
                            .putInt(
                                ProfileStatsWidget.PREF_BACKGROUND_COLOR,
                                extras.getInt(SimpleColorDialog.COLOR)
                            )
                            .apply()
                        (binding.topBackgroundButton as MaterialButton).iconTint =
                            ColorStateList.valueOf(extras.getInt(SimpleColorDialog.COLOR))
                    }

                    ProfileStatsWidget.PREF_BACKGROUND_FADE -> {
                        prefs.edit()
                            .putInt(
                                ProfileStatsWidget.PREF_BACKGROUND_FADE,
                                extras.getInt(SimpleColorDialog.COLOR)
                            )
                            .apply()
                        (binding.bottomBackgroundButton as MaterialButton).iconTint =
                            ColorStateList.valueOf(extras.getInt(SimpleColorDialog.COLOR))
                    }

                    ProfileStatsWidget.PREF_TITLE_TEXT_COLOR -> {
                        prefs.edit()
                            .putInt(
                                ProfileStatsWidget.PREF_TITLE_TEXT_COLOR,
                                extras.getInt(SimpleColorDialog.COLOR)
                            )
                            .apply()
                        (binding.titleColorButton as MaterialButton).iconTint =
                            ColorStateList.valueOf(extras.getInt(SimpleColorDialog.COLOR))
                    }

                    ProfileStatsWidget.PREF_STATS_TEXT_COLOR -> {
                        prefs.edit()
                            .putInt(
                                ProfileStatsWidget.PREF_STATS_TEXT_COLOR,
                                extras.getInt(SimpleColorDialog.COLOR)
                            )
                            .apply()
                        (binding.statsColorButton as MaterialButton).iconTint =
                            ColorStateList.valueOf(extras.getInt(SimpleColorDialog.COLOR))
                    }
                }
            }
        }
        return true
    }
}
