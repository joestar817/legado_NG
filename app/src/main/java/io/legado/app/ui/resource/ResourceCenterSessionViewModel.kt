package io.legado.app.ui.resource

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.help.resource.ResourceRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 仅持有浏览租约和优先级；后台安装继续由原Repository拥有。 */
internal class ResourceCenterSessionViewModel : ViewModel() {
    private var token: Long? = null
    private var foreground = false
    private var priority: Set<String>? = null
    private var versionChecks: Job? = null

    fun start() {
        if (token == null) token = ResourceRepository.openSession()
    }

    fun prioritize(types: Set<String>?) {
        priority = types
        if (foreground) ResourceRepository.prioritize(types)
    }

    fun onForeground() {
        start()
        foreground = true
        ResourceRepository.prioritize(priority)
        if (versionChecks?.isActive == true) return
        versionChecks = viewModelScope.launch {
            while (isActive) {
                ResourceRepository.revalidate()
                delay(120_000L)
            }
        }
    }

    fun onBackground() {
        foreground = false
        versionChecks?.cancel()
        versionChecks = null
    }

    override fun onCleared() {
        onBackground()
        token?.let(ResourceRepository::closeSession)
        super.onCleared()
    }
}
