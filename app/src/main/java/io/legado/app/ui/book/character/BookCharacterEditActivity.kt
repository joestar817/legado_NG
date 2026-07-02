package io.legado.app.ui.book.character

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookCharacter
import io.legado.app.databinding.ActivityBookCharacterEditBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding

class BookCharacterEditActivity : BaseActivity<ActivityBookCharacterEditBinding>() {

    override val binding by viewBinding(ActivityBookCharacterEditBinding::inflate)
    private lateinit var workKey: String
    private var characterId: Long = 0L
    private var character: BookCharacter? = null

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        workKey = intent.getStringExtra(BookCharacterActivity.EXTRA_WORK_KEY).orEmpty()
        characterId = intent.getLongExtra(BookCharacterActivity.EXTRA_CHARACTER_ID, 0L)
        character = appDb.bookCharacterDao.getCharacter(characterId)
        initSpinners()
        bindCharacter()
        binding.tvSave.setOnClickListener { saveCharacter() }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        if (characterId > 0L) {
            menuInflater.inflate(R.menu.book_character_edit, menu)
        }
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_delete) {
            deleteCharacter()
            return true
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun initSpinners() {
        binding.spinnerGender.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            BookCharacterLabels.genderValues.map { BookCharacterLabels.genderLabel(this, it) }
        )
        binding.spinnerRole.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            BookCharacterLabels.roleValues.map { BookCharacterLabels.roleLabel(this, it) }
        )
    }

    private fun bindCharacter() {
        val item = character ?: return
        binding.editName.setText(item.name)
        binding.spinnerGender.setSelection(BookCharacterLabels.genderValues.indexOf(item.gender).coerceAtLeast(0))
        binding.spinnerRole.setSelection(BookCharacterLabels.roleValues.indexOf(item.roleTag).coerceAtLeast(0))
        binding.editAliases.setText(readAliases(item).joinToString(", "))
        binding.editIntro.setText(item.displayIntro().orEmpty())
        binding.editAvatarUri.setText(item.avatarUri.orEmpty())
        binding.editPortraitUri.setText(item.portraitUri.orEmpty())
        binding.editImagePrompt.setText(item.imagePrompt.orEmpty())
    }

    private fun saveCharacter() {
        val name = binding.editName.text?.toString()?.trim().orEmpty()
        if (name.isEmpty()) {
            toastOnUi(R.string.character_name_empty)
            return
        }
        val now = System.currentTimeMillis()
        val item = (character ?: BookCharacter(
            workKey = workKey,
            createdAt = now
        )).apply {
            this.name = name
            gender = BookCharacterLabels.genderValues[binding.spinnerGender.selectedItemPosition]
            roleTag = BookCharacterLabels.roleValues[binding.spinnerRole.selectedItemPosition]
            identity = null
            aliasesJson = aliasesFromInput().takeIf { it.isNotEmpty() }?.let { GSON.toJson(it) }
            intro = binding.editIntro.text?.toString()?.trim()?.ifBlank { null }
            shortIntro = null
            avatarUri = binding.editAvatarUri.text?.toString()?.trim()?.ifBlank { null }
            portraitUri = binding.editPortraitUri.text?.toString()?.trim()?.ifBlank { null }
            imagePrompt = binding.editImagePrompt.text?.toString()?.trim()?.ifBlank { null }
            updatedAt = now
        }
        if (item.id == 0L) {
            appDb.bookCharacterDao.insertCharacter(item)
        } else {
            appDb.bookCharacterDao.updateCharacter(item)
        }
        appDb.bookCharacterDao.updateCharacterCount(workKey, now)
        setResult(RESULT_OK)
        finish()
    }

    private fun deleteCharacter() {
        val item = character ?: return
        alert(titleResource = R.string.draw, messageResource = R.string.sure_del) {
            yesButton {
                appDb.bookCharacterDao.deleteCharacter(item)
                appDb.bookCharacterDao.updateCharacterCount(workKey)
                setResult(RESULT_OK)
                finish()
            }
            noButton()
        }
    }

    private fun aliasesFromInput(): List<String> {
        return binding.editAliases.text?.toString()
            ?.split(",", "，", "/", "、")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            .orEmpty()
    }

    private fun readAliases(item: BookCharacter): List<String> {
        return item.aliasesJson?.let {
            GSON.fromJsonObject<List<String>>(it).getOrNull()
        }.orEmpty()
    }
}
