package io.legado.app.ui.book.read.aloud

import androidx.compose.ui.unit.IntRect
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.legado.app.ui.book.bgm.BgmListeningDialog
import io.legado.app.ui.design.components.compose.NgPaneWindowBinding

/** Only this listening Activity opts its existing drawer windows into pane-local presentation. */
internal class ReadAloudPaneDialogHost(
    private val manager: FragmentManager,
    private val bounds: (music: Boolean) -> IntRect?,
) : FragmentManager.FragmentLifecycleCallbacks() {
    private val bindings = mutableMapOf<DialogFragment, NgPaneWindowBinding>()

    init {
        manager.registerFragmentLifecycleCallbacks(this, true)
    }

    override fun onFragmentStarted(fm: FragmentManager, f: Fragment) {
        val fragment = f as? DialogFragment ?: return
        val dialog = fragment.dialog as? BottomSheetDialog ?: return
        val window = dialog.window ?: return
        bindings.remove(fragment)?.dispose()
        bindings[fragment] = NgPaneWindowBinding(
            window,
            dialog.findViewById(com.google.android.material.R.id.container),
        ).also { it.update(bounds(fragment is BgmListeningDialog)) }
    }

    override fun onFragmentStopped(fm: FragmentManager, f: Fragment) {
        (f as? DialogFragment)?.let { bindings.remove(it)?.dispose() }
    }

    fun refresh() {
        bindings.forEach { (fragment, binding) -> binding.update(bounds(fragment is BgmListeningDialog)) }
    }

    fun dispose() {
        manager.unregisterFragmentLifecycleCallbacks(this)
        bindings.values.forEach(NgPaneWindowBinding::dispose)
        bindings.clear()
    }
}
