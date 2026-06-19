package io.legado.app.web.mcp

import fi.iki.elonen.NanoHTTPD
import io.legado.app.service.McpService

class McpHttpServer(port: Int) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        McpService.serve()
        val contentType = ContentType(session.headers["content-type"]).tryUTF8()
        session.headers["content-type"] = contentType.contentTypeHeader
        val files = HashMap<String, String>()
        val postData = if (session.method == Method.POST) {
            session.parseBody(files)
            files["postData"]
        } else {
            null
        }
        return McpServer.serve(session.method, session.uri, postData)
    }
}
