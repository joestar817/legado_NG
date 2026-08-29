package io.legado.app.model.login

import io.legado.app.data.entities.BaseSource
import io.legado.app.exception.NoStackTraceException
import io.legado.app.model.jsSource.JsSourceEngine

fun BaseSource.isLoginUiV2(): Boolean = LoginUiV2.isV2(loginUi)

fun BaseSource.evalLoginUiV2(stateJson: String): String? {
    val script = getLoginJs()
        ?: throw NoStackTraceException("登录 UI v2 缺少 loginUi/loginAction 脚本")
    val result = evalJS(
        "$script\nloginUi(JSON.parse(String(__loginState)))"
    ) {
        put("__loginState", stateJson)
    }
    return JsSourceEngine.normalizeJsResult(result)
}

fun BaseSource.evalLoginActionV2(
    action: String,
    stateJson: String,
    formJson: String,
): String? {
    val script = getLoginJs()
        ?: throw NoStackTraceException("登录 UI v2 缺少 loginUi/loginAction 脚本")
    val result = evalJS(
        "$script\n" +
            "loginAction(String(__loginAction), JSON.parse(String(__loginState)), " +
            "JSON.parse(String(__loginForm)))"
    ) {
        put("__loginAction", action)
        put("__loginState", stateJson)
        put("__loginForm", formJson)
    }
    return JsSourceEngine.normalizeJsResult(result)
}
