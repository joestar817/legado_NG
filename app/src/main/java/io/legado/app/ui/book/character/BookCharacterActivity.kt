package io.legado.app.ui.book.character

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookCharacter
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.databinding.ActivityBookCharacterBinding
import io.legado.app.databinding.ItemBookCharacterBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.widget.recycler.ItemTouchCallback
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.gone
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

class BookCharacterActivity : BaseActivity<ActivityBookCharacterBinding>(),
    ItemTouchCallback.Callback {

    override val binding by viewBinding(ActivityBookCharacterBinding::inflate)
    private val adapter by lazy { Adapter() }
    private lateinit var itemTouchCallback: ItemTouchCallback
    private val editResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            setResult(RESULT_OK)
        }
    }
    private lateinit var workKey: String
    private var bookName: String = ""
    private var bookAuthor: String = ""
    private var bookUrl: String? = null

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        bookName = intent.getStringExtra(EXTRA_BOOK_NAME).orEmpty()
        bookAuthor = intent.getStringExtra(EXTRA_BOOK_AUTHOR).orEmpty()
        bookUrl = intent.getStringExtra(EXTRA_BOOK_URL)
        workKey = intent.getStringExtra(EXTRA_WORK_KEY)
            ?: BookCharacterProfile.workKey(bookName, bookAuthor)
        appDb.bookCharacterDao.getOrCreateProfile(bookName, bookAuthor, bookUrl)
        binding.recyclerView.setEdgeEffectColor(primaryColor)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        itemTouchCallback = ItemTouchCallback(this).apply {
            isCanDrag = true
            isCanSwipe = true
        }
        ItemTouchHelper(itemTouchCallback).attachToRecyclerView(binding.recyclerView)
        adapter.setOnItemClickListener { _, item -> openEdit(item.id) }
        observeCharacters()
    }

    override fun swap(srcPosition: Int, targetPosition: Int): Boolean {
        adapter.swapItem(srcPosition, targetPosition)
        return true
    }

    override fun getSwipeFlags(adapterPosition: Int, defaultFlags: Int): Int {
        return ItemTouchHelper.RIGHT
    }

    override fun onSwiped(adapterPosition: Int, direction: Int) {
        val character = adapter.getItems().getOrNull(adapterPosition)
        if (character == null || direction != ItemTouchHelper.RIGHT) {
            if (adapterPosition >= 0) {
                adapter.notifyItemChanged(adapterPosition)
            }
            return
        }
        confirmDeleteCharacter(character, adapterPosition)
    }

    override fun onClearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        val now = System.currentTimeMillis()
        val sorted = adapter.getItems().mapIndexed { index, character ->
            character.apply {
                sortOrder = index
                updatedAt = now
            }
        }
        appDb.bookCharacterDao.updateCharacters(*sorted.toTypedArray())
        appDb.bookCharacterDao.updateCharacterCount(workKey, now)
        setResult(RESULT_OK)
    }

    private fun confirmDeleteCharacter(character: BookCharacter, adapterPosition: Int) {
        alert(titleResource = R.string.draw) {
            setMessage(getString(R.string.sure_del_any, character.name))
            yesButton {
                appDb.bookCharacterDao.deleteCharacter(character)
                appDb.bookCharacterDao.updateCharacterCount(workKey)
                setResult(RESULT_OK)
            }
            noButton {
                adapter.notifyItemChanged(adapterPosition)
            }
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.book_character, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_tts) {
            openTtsBindings()
            return true
        }
        if (item.itemId == R.id.menu_add) {
            openEdit(0L)
            return true
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun observeCharacters() {
        lifecycleScope.launch {
            appDb.bookCharacterDao.flowCharacters(workKey)
                .catch { toastOnUi(it.localizedMessage) }
                .flowOn(IO)
                .collect { characters ->
                    adapter.setItems(characters)
                    if (characters.isEmpty()) {
                        binding.tvEmpty.visible()
                    } else {
                        binding.tvEmpty.gone()
                    }
                }
        }
    }

    private fun openEdit(characterId: Long) {
        editResult.launch(
            Intent(this, BookCharacterEditActivity::class.java)
                .putExtra(EXTRA_WORK_KEY, workKey)
                .putExtra(EXTRA_BOOK_NAME, bookName)
                .putExtra(EXTRA_BOOK_AUTHOR, bookAuthor)
                .putExtra(EXTRA_BOOK_URL, bookUrl)
                .putExtra(EXTRA_CHARACTER_ID, characterId)
        )
    }

    private fun openTtsBindings() {
        startActivity(
            Intent(this, BookCharacterTtsActivity::class.java)
                .putExtra(EXTRA_WORK_KEY, workKey)
                .putExtra(EXTRA_BOOK_NAME, bookName)
                .putExtra(EXTRA_BOOK_AUTHOR, bookAuthor)
                .putExtra(EXTRA_BOOK_URL, bookUrl)
        )
    }

    private inner class Adapter : RecyclerAdapter<BookCharacter, ItemBookCharacterBinding>(this@BookCharacterActivity) {

        override fun getViewBinding(parent: ViewGroup): ItemBookCharacterBinding {
            return ItemBookCharacterBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemBookCharacterBinding,
            item: BookCharacter,
            payloads: MutableList<Any>
        ) = binding.run {
            tvAvatar.text = item.name.firstOrNull()?.toString().orEmpty()
            tvAvatar.setBackgroundResource(item.avatarBackground())
            tvName.text = item.name
            tvRole.text = BookCharacterLabels.roleLabel(context, item.roleTag)
            tvIdentity.text = buildList {
                aliases(item).takeIf { it.isNotEmpty() }?.let { add(it.joinToString(" / ")) }
            }.joinToString(" / ").ifBlank {
                BookCharacterLabels.genderLabel(context, item.gender)
            }
            tvIntro.text = item.displayIntro()
                ?: ""
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemBookCharacterBinding) {
        }

        private fun BookCharacter.avatarBackground(): Int {
            return when (gender) {
                BookCharacter.Gender.MALE -> R.drawable.bg_character_avatar_male
                BookCharacter.Gender.FEMALE -> R.drawable.bg_character_avatar_female
                else -> R.drawable.bg_character_avatar_unknown
            }
        }

        private fun aliases(item: BookCharacter): List<String> {
            return item.aliasesJson?.let {
                GSON.fromJsonObject<List<String>>(it).getOrNull()
            }.orEmpty()
        }
    }

    companion object {
        const val EXTRA_WORK_KEY = "workKey"
        const val EXTRA_BOOK_NAME = "bookName"
        const val EXTRA_BOOK_AUTHOR = "bookAuthor"
        const val EXTRA_BOOK_URL = "bookUrl"
        const val EXTRA_CHARACTER_ID = "characterId"
    }
}
