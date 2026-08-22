package io.legado.app.ui.book.character

/**
 * 书籍角色入口。角色资料与发音人配置共用 [BookCharacterTtsActivity] 的页面，
 * 仅根据入口选择不同的初始分页。
 */
class BookCharacterActivity : BookCharacterTtsActivity() {

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
