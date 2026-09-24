package io.legado.app.ui.book.read.epub

/** ES5 on purpose: report an unsupported engine before parsing the EPUB runtime. */
internal object EpubWebViewCapabilities {
    // Do not use eval/Function here: the document CSP denies dynamic compilation even
    // when this trusted probe is injected by native evaluateJavascript.
    const val CHECK = """(function(){try{
        return typeof Promise==='function' && typeof Map==='function' && typeof Set==='function' &&
            typeof fetch==='function' && typeof FontFace==='function' && !!document.fonts &&
            !!document.fonts.ready && typeof Element.prototype.replaceChildren==='function' &&
            !!window.CSS && CSS.supports('selector(:where(html))');
    }catch(e){return false;}})()"""
}
