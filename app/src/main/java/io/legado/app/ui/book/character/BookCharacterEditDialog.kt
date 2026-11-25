package io.legado.app.ui.book.character

import android.app.Dialog
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.view.updateLayoutParams
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.legado.app.R
import io.legado.app.base.BaseComposeDialogFragment
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookCharacter
import io.legado.app.data.entities.BookTtsCastRole
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookCharacterEditDialog() : BaseComposeDialogFragment() {

    constructor(workKey: String, characterId: Long = 0L, castRoleId: Long = 0L) : this() {
        arguments = Bundle().apply {
            putString(ARG_WORK_KEY, workKey)
            putLong(ARG_CHARACTER_ID, characterId)
            putLong(ARG_CAST_ROLE_ID, castRoleId)
        }
    }

    private val callback get() = activity as? Callback
    private val styleOnly get() = arguments?.getBoolean(ARG_STYLE_ONLY) == true
    private lateinit var workKey: String
    private var characterId = 0L
    private var castRoleId = 0L
    private var character: BookCharacter? = null
    private var castRole: BookTtsCastRole? = null
    private var saving by mutableStateOf(false)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        BottomSheetDialog(requireContext(), theme)

    @Suppress("DEPRECATION")
    override fun onStart() {
        super.onStart()
        val bottomSheet = dialog as? BottomSheetDialog ?: return
        bottomSheet.window?.apply {
            setBackgroundDrawableResource(R.color.transparent)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = 0.22f }
            decorView.setPadding(0, 0, 0, 0)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        bottomSheet.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            updateLayoutParams { height = ViewGroup.LayoutParams.WRAP_CONTENT }
        }
        bottomSheet.behavior.apply {
            isFitToContents = true
            skipCollapsed = true
            isDraggable = !saving
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        val composeView = (view as ComposeView).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
        if (styleOnly) {
            composeView.setContent {
                NgAppTheme(updateSystemBars = false) {
                    BookCharacterSilhouetteStyleDrawer { style ->
                        style?.let { BookCharacterSilhouetteSettings.set(requireContext(), it) }
                        dismissAllowingStateLoss()
                    }
                }
            }
            return
        }
        workKey = arguments?.getString(ARG_WORK_KEY).orEmpty()
        characterId = arguments?.getLong(ARG_CHARACTER_ID) ?: 0L
        castRoleId = arguments?.getLong(ARG_CAST_ROLE_ID) ?: 0L
        character = appDb.bookCharacterDao.getCharacter(characterId)
        castRole = appDb.bookCharacterDao.getTtsCastRole(castRoleId)
        if (
            workKey.isBlank() ||
            characterId > 0L && character?.workKey != workKey ||
            castRoleId > 0L && castRole?.workKey != workKey
        ) {
            dismissAllowingStateLoss()
            return
        }
        val isPromote = castRole != null
        val initialValue = initialCharacterFormValue(character, castRole)
        val title = getString(
            when {
                isPromote -> R.string.character_promote_title
                character != null -> R.string.edit_character
                else -> R.string.add_character
            }
        )
        val confirmText = getString(
            if (isPromote) R.string.character_promote else R.string.save
        )
        val automaticSilhouetteStyle = requireActivity().intent.automaticCharacterSilhouetteStyle()
        composeView.setContent {
            NgAppTheme(updateSystemBars = false) {
                BookCharacterEditDialogContent(
                    title = title,
                    confirmText = confirmText,
                    initialValue = initialValue,
                    saving = saving,
                    showPortraitOptions = !isPromote,
                    automaticSilhouetteStyle = automaticSilhouetteStyle,
                    onConfirm = ::save,
                )
            }
        }
    }

    private fun save(value: BookCharacterFormValue, style: BookCharacterSilhouetteStyle?) {
        if (value.name.isEmpty()) {
            toastOnUi(R.string.character_name_empty)
            return
        }
        if (saving) return
        saving = true
        isCancelable = false
        (dialog as? BottomSheetDialog)?.behavior?.isDraggable = false
        val appContext = requireContext().applicationContext
        lifecycleScope.launch {
            try {
                val savedCharacterId = withContext(Dispatchers.IO) {
                    BookCharacterEditor.save(appContext, workKey, character, castRole, value).also {
                        style?.let { BookCharacterSilhouetteSettings.set(appContext, it) }
                    }
                }
                callback?.onCharacterSaved(savedCharacterId, castRoleId)
                dismissAllowingStateLoss()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLog.put("保存角色资料失败", error)
                toastOnUi(R.string.character_save_error)
            } finally {
                saving = false
                isCancelable = true
                (dialog as? BottomSheetDialog)?.behavior?.isDraggable = true
            }
        }
    }

    interface Callback {
        fun onCharacterSaved(characterId: Long, castRoleId: Long)
    }

    companion object {
        internal fun forSilhouetteStyle() = BookCharacterEditDialog().apply {
            arguments = Bundle().apply { putBoolean(ARG_STYLE_ONLY, true) }
        }

        private const val ARG_WORK_KEY = "workKey"
        private const val ARG_CHARACTER_ID = "characterId"
        private const val ARG_CAST_ROLE_ID = "castRoleId"
        private const val ARG_STYLE_ONLY = "styleOnly"
    }
}
