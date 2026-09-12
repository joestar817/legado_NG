package io.legado.app.ui.design.theme

import android.app.Application
import splitties.init.injectAsAppCtx

/** Supplies the app context without starting production services in font regression tests. */
class InterfaceFontTestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        injectAsAppCtx()
    }
}
