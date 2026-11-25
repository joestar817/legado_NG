package io.legado.app.ui.book.character

/**
 * 书籍角色入口，默认打开正式角色并提供全局剪影设置。
 */
class BookCharacterActivity : BookCharacterTtsActivity() {

    override val showSilhouetteSettings: Boolean = true

    override fun initialPage(): BookCharacterTtsPage = BookCharacterTtsPage.FORMAL

    companion object {
        const val EXTRA_WORK_KEY = "workKey"
        const val EXTRA_BOOK_NAME = "bookName"
        const val EXTRA_BOOK_AUTHOR = "bookAuthor"
        const val EXTRA_BOOK_URL = "bookUrl"
        const val EXTRA_CHARACTER_ID = "characterId"
        const val EXTRA_CAST_ROLE_ID = "castRoleId"
    }
}
