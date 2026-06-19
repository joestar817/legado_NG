package io.legado.app.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import io.legado.app.R
import io.legado.app.base.BaseService
import io.legado.app.constant.AppConst
import io.legado.app.constant.EventBus
import io.legado.app.constant.IntentAction
import io.legado.app.constant.NotificationId
import io.legado.app.constant.PreferKey
import io.legado.app.receiver.NetworkChangedListener
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.postEvent
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.sendToClip
import io.legado.app.utils.servicePendingIntent
import io.legado.app.utils.startForegroundServiceCompat
import io.legado.app.utils.startService
import io.legado.app.utils.stopService
import io.legado.app.utils.toastOnUi
import io.legado.app.web.mcp.McpHttpServer
import splitties.init.appCtx
import java.io.IOException

class McpService : BaseService() {

    companion object {
        var isRun = false
        var hostAddress = ""

        fun start(context: Context) {
            context.startService<McpService>()
        }

        fun startForeground(context: Context) {
            val intent = Intent(context, McpService::class.java)
            context.startForegroundServiceCompat(intent)
        }

        fun stop(context: Context) {
            context.stopService<McpService>()
        }

        fun serve() {
            appCtx.startService<McpService> {
                action = "serve"
            }
        }
    }

    private var mcpHttpServer: McpHttpServer? = null
    private var notificationList = mutableListOf(appCtx.getString(R.string.service_starting))
    private val networkChangedListener by lazy {
        NetworkChangedListener(this)
    }

    override fun onCreate() {
        super.onCreate()
        isRun = true
        networkChangedListener.register()
        networkChangedListener.onNetworkChanged = {
            updateHostAddress()
            startForegroundNotification()
            postEvent(EventBus.MCP_SERVICE, hostAddress)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            IntentAction.stop -> stopSelf()
            "copyHostAddress" -> sendToClip(hostAddress)
            "serve" -> Unit
            else -> upMcpServer()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        networkChangedListener.unRegister()
        isRun = false
        if (mcpHttpServer?.isAlive == true) {
            mcpHttpServer?.stop()
        }
        postEvent(EventBus.MCP_SERVICE, "")
    }

    private fun upMcpServer() {
        if (mcpHttpServer?.isAlive == true) {
            mcpHttpServer?.stop()
        }
        val addressList = NetworkUtils.getLocalIPAddress()
        if (addressList.any()) {
            val port = getPort()
            mcpHttpServer = McpHttpServer(port)
            try {
                mcpHttpServer?.start()
                updateHostAddress()
                isRun = true
                postEvent(EventBus.MCP_SERVICE, hostAddress)
                startForegroundNotification()
            } catch (e: IOException) {
                toastOnUi(e.localizedMessage ?: "")
                e.printOnDebug()
                stopSelf()
            }
        } else {
            toastOnUi("mcp service cant start, no ip address")
            stopSelf()
        }
    }

    private fun updateHostAddress() {
        val addressList = NetworkUtils.getLocalIPAddress()
        notificationList.clear()
        if (addressList.any()) {
            notificationList.addAll(addressList.map { address ->
                getString(
                    R.string.mcp_service_url_format,
                    getString(R.string.http_ip, address.hostAddress, getPort())
                )
            })
            hostAddress = notificationList.first()
        } else {
            hostAddress = getString(R.string.network_connection_unavailable)
            notificationList.add(hostAddress)
        }
    }

    private fun getPort(): Int {
        var port = getPrefInt(PreferKey.mcpPort, 1124)
        if (port !in 1024..65530) {
            port = 1124
        }
        return port
    }

    @SuppressLint("ObsoleteSdkInt")
    override fun startForegroundNotification() {
        val builder = NotificationCompat.Builder(this, AppConst.channelIdWeb)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSmallIcon(R.drawable.ic_web_service_noti)
            .setOngoing(true)
            .setContentTitle(getString(R.string.mcp_service))
            .setContentText(notificationList.joinToString("\n"))
            .setContentIntent(
                servicePendingIntent<McpService>("copyHostAddress")
            )
        builder.addAction(
            R.drawable.ic_stop_black_24dp,
            getString(R.string.cancel),
            servicePendingIntent<McpService>(IntentAction.stop)
        )
        val notification = builder.build()
        startForeground(NotificationId.McpService, notification)
    }
}
