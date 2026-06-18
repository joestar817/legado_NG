@file:Suppress("unused")

package io.legado.app.ui.widget.code

import android.content.Context
import android.widget.ArrayAdapter
import io.legado.app.R
import splitties.init.appCtx
import splitties.resources.color
import java.util.regex.Pattern

val legadoPattern: Pattern = Pattern.compile("\\|\\||&&|%%|@js:|@Json:|@css:|@@|@XPath:|@webjs:")
val jsonPattern: Pattern = Pattern.compile("\"[A-Za-z0-9]*?\"\\:|\"|\\{|\\}|\\[|\\]")
val wrapPattern: Pattern = Pattern.compile("\\\\n")
val jsCommentPattern: Pattern = Pattern.compile("//.*$|/\\*[\\s\\S]*?\\*/", Pattern.MULTILINE)
val jsStringPattern: Pattern = Pattern.compile("'(?:\\\\.|[^'\\\\])*'|\"(?:\\\\.|[^\"\\\\])*\"|`(?:\\\\.|[^`\\\\])*`")
val jsNumberPattern: Pattern = Pattern.compile("(?<![\\w.])-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?(?![\\w.])")
val jsKeywordPattern: Pattern = Pattern.compile(
    "\\b(?:async|await|break|case|catch|class|const|continue|default|delete|do|else|export|extends|finally|for|function|if|import|in|instanceof|let|new|return|switch|this|throw|try|typeof|var|void|while|yield)\\b"
)
val jsLiteralPattern: Pattern = Pattern.compile("\\b(?:false|null|true|undefined|NaN|Infinity)\\b")
val cssPropertyPattern: Pattern = Pattern.compile("\\b[-A-Za-z]+(?=\\s*:)")
val cssStringPattern: Pattern = Pattern.compile("'(?:\\\\.|[^'\\\\])*'|\"(?:\\\\.|[^\"\\\\])*\"")
val cssNumberPattern: Pattern = Pattern.compile("(?<![\\w.])-?\\d+(?:\\.\\d+)?(?:px|em|rem|vh|vw|%|s|ms)?(?![\\w.])")
val debugSectionPattern: Pattern = Pattern.compile("^// ===== .+ =====$", Pattern.MULTILINE)
val debugTimestampPattern: Pattern = Pattern.compile("\\[\\d{2}:\\d{2}\\.\\d{3}]")
val debugUrlPattern: Pattern = Pattern.compile("https?://[^\\s\"'<>]+|(?<=获取成功:)/[^\\s\"'<>]+")
val debugHeaderPattern: Pattern = Pattern.compile("^[A-Za-z0-9_-]+(?=:)", Pattern.MULTILINE)
val codeStringPattern: Pattern = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"")
val jsonKeyPattern: Pattern = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"(?=\\s*:)")
val jsonLiteralPattern: Pattern = Pattern.compile("\\b(?:true|false|null)\\b")
val jsonNumberPattern: Pattern = Pattern.compile("(?<![\\w.])-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?(?![\\w.])")
val htmlStartTagNamePattern: Pattern = Pattern.compile("(?<=<)[A-Za-z][\\w:-]*")
val htmlEndTagNamePattern: Pattern = Pattern.compile("(?<=</)[A-Za-z][\\w:-]*")
val htmlAttributePattern: Pattern = Pattern.compile("\\b[A-Za-z_:][\\w:.-]*(?=\\s*=\\s*\")")

fun CodeView.addLegadoPattern() {
    addSyntaxPattern(legadoPattern, appCtx.color(R.color.md_orange_900))
}

fun CodeView.addJsonPattern() {
    addSyntaxPattern(codeStringPattern, appCtx.color(R.color.md_green_800))
    addSyntaxPattern(jsonNumberPattern, appCtx.color(R.color.md_purple_700))
    addSyntaxPattern(jsonLiteralPattern, appCtx.color(R.color.md_purple_700))
    addSyntaxPattern(jsonKeyPattern, appCtx.color(R.color.md_blue_800))
}

fun CodeView.addJsPattern() {
    addSyntaxPattern(wrapPattern, appCtx.color(R.color.md_blue_grey_500))
    addSyntaxPattern(jsCommentPattern, appCtx.color(R.color.md_blue_grey_500))
    addSyntaxPattern(jsStringPattern, appCtx.color(R.color.md_green_800))
    addSyntaxPattern(jsNumberPattern, appCtx.color(R.color.md_purple_700))
    addSyntaxPattern(jsLiteralPattern, appCtx.color(R.color.md_purple_700))
    addSyntaxPattern(jsKeywordPattern, appCtx.color(R.color.md_light_blue_600))
}

fun CodeView.addHtmlPattern() {
    addSyntaxPattern(htmlStartTagNamePattern, appCtx.color(R.color.md_blue_800))
    addSyntaxPattern(htmlEndTagNamePattern, appCtx.color(R.color.md_blue_800))
    addSyntaxPattern(htmlAttributePattern, appCtx.color(R.color.md_purple_700))
    addSyntaxPattern(codeStringPattern, appCtx.color(R.color.md_green_800))
}

fun CodeView.addCssPattern() {
    addSyntaxPattern(cssPropertyPattern, appCtx.color(R.color.md_blue_800))
    addSyntaxPattern(cssStringPattern, appCtx.color(R.color.md_green_800))
    addSyntaxPattern(cssNumberPattern, appCtx.color(R.color.md_purple_700))
}

fun CodeView.addDebugLogPattern() {
    addSyntaxPattern(debugSectionPattern, appCtx.color(R.color.md_blue_grey_500))
    addSyntaxPattern(debugTimestampPattern, appCtx.color(R.color.md_blue_700))
    addSyntaxPattern(debugUrlPattern, appCtx.color(R.color.md_purple_700))
    addSyntaxPattern(debugHeaderPattern, appCtx.color(R.color.md_blue_800))
    addSyntaxPattern(codeStringPattern, appCtx.color(R.color.md_green_800))
    addSyntaxPattern(jsonNumberPattern, appCtx.color(R.color.md_purple_700))
    addSyntaxPattern(jsonLiteralPattern, appCtx.color(R.color.md_purple_700))
    addSyntaxPattern(jsonKeyPattern, appCtx.color(R.color.md_blue_800))
    addSyntaxPattern(htmlStartTagNamePattern, appCtx.color(R.color.md_blue_800))
    addSyntaxPattern(htmlEndTagNamePattern, appCtx.color(R.color.md_blue_800))
    addSyntaxPattern(htmlAttributePattern, appCtx.color(R.color.md_purple_700))
}

fun Context.arrayAdapter(keywords: Array<String>): ArrayAdapter<String> {
    return ArrayAdapter(this, R.layout.item_1line_text_and_del, R.id.text_view, keywords)
}
