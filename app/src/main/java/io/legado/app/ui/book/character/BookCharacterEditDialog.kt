package io.legado.app.ui.book.character

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseComposeDialogFragment
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookCharacter
import io.legado.app.data.entities.BookTtsCastRole
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.widget.dialog.applyNgDialogWindow
import io.legado.app.ui.widget.dialog.ngDialogMaxHeight
import io.legado.app.utils.toastOnUi
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
    private lateinit var workKey: String
    private var characterId = 0L
    private var castRoleId = 0L
    private var character: BookCharacter? = null
    private var castRole: BookTtsCastRole? = null
    private var saving by mutableStateOf(false)

    override fun onStart() {
        super.onStart()
        applyNgDialogWindow(height = ngDialogMaxHeight(0.86f))
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        workKey = arguments?.getString(ARG_WORK_KEY).orEmpty()
        characterId = arguments?.getLong(ARG_CHARACTER_ID) ?: 0L
        castRoleId = arguments?.getLong(ARG_CAST_ROLE_ID) ?: 0L
        character = appDb.bookCharacterDao.getCharacter(characterId)
        castRole = appDb.bookCharacterDao.getTtsCastRole(castRoleId)
        if (
            workKey.isBlank() ||
            characterId > 0L && character == null ||
            castRoleId > 0L && castRole == null
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
        (view as ComposeView).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                NgAppTheme(updateSystemBars = false) {
                    BookCharacterEditDialogContent(
                        title = title,
                        confirmText = confirmText,
                        initialValue = initialValue,
                        saving = saving,
                        onCancel = { dismissAllowingStateLoss() },
                        onConfirm = ::save,
                    )
                }
            }
        }
    }

    private fun save(value: BookCharacterFormValue) {
        if (value.name.isEmpty()) {
            toastOnUi(R.string.character_name_empty)
            return
        }
        saving = true
        lifecycleScope.launch(Dispatchers.IO) {
            val savedCharacterId = BookCharacterEditor.save(
                workKey = workKey,
                current = character,
                castRole = castRole,
                value = value,
            )
            withContext(Dispatchers.Main) {
                callback?.onCharacterSaved(savedCharacterId, castRoleId)
                dismissAllowingStateLoss()
            }
        }
    }

    interface Callback {
        fun onCharacterSaved(characterId: Long, castRoleId: Long)
    }

    companion object {
        private const val ARG_WORK_KEY = "workKey"
        private const val ARG_CHARACTER_ID = "characterId"
        private const val ARG_CAST_ROLE_ID = "castRoleId"
    }
}
