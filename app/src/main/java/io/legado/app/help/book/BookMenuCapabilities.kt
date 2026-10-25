package io.legado.app.help.book

/** 菜单只表达已有能力；隐藏或禁用由各入口的布局决定，不改变书籍类型。 */
internal class BookMenuCapabilities(
    local: Boolean,
    epub: Boolean,
    audio: Boolean,
    video: Boolean,
    image: Boolean,
    webFile: Boolean,
    pdf: Boolean,
) {
    val textContent = !audio && !video && !image && !webFile && !pdf
    val listen = audio || textContent
    val changeSource = !local
    val update = !local
    val download = !local && !video && !webFile
    val export = !audio && !video && !webFile && !pdf
    val simulatedReading = !epub && !video && !webFile && !pdf
    val showCache = !video && !webFile && !pdf
    val chapterList = !webFile
}
