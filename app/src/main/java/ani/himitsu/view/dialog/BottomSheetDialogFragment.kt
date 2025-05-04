package ani.himitsu.view.dialog

import android.content.res.Configuration
import android.view.View
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentManager
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import bit.himitsu.setNavigationTransparent
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

open class BottomSheetDialogFragment : BottomSheetDialogFragment() {
    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            if (PrefManager.getVal(PrefName.ImmersiveMode)) {
                WindowInsetsControllerCompat(
                    window, window.decorView
                ).hide(WindowInsetsCompat.Type.statusBars())
            }
            if (this.resources.configuration.orientation != Configuration.ORIENTATION_PORTRAIT) {
                BottomSheetBehavior.from(requireView().parent as View).state = BottomSheetBehavior.STATE_EXPANDED
            }
            WindowInsetsControllerCompat(
                window, window.decorView
            ).show(WindowInsetsCompat.Type.navigationBars())
            window.setNavigationTransparent()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        dialog?.window?.let { window ->
            if (newConfig.orientation != Configuration.ORIENTATION_PORTRAIT) {
                BottomSheetBehavior.from(requireView().parent as View).state = BottomSheetBehavior.STATE_EXPANDED
            }
            window.setNavigationTransparent()
        }
    }

    override fun show(manager: FragmentManager, tag: String?) {
        val ft = manager.beginTransaction()
        ft.add(this, tag)
        ft.commitAllowingStateLoss()
    }
}