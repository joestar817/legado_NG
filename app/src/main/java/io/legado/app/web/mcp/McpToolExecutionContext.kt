package io.legado.app.web.mcp

import com.google.gson.annotations.SerializedName

/**
 * 由内置 Agent 调用通道注入的可信执行上下文，不暴露为模型工具参数。
 */
data class McpToolExecutionContext(
    @SerializedName("conversation_id")
    val conversationId: String,
    @SerializedName("turn_id")
    val turnId: String,
    @SerializedName("skill_revision")
    val skillRevision: String,
    @SerializedName("skill_content_hash")
    val skillContentHash: String,
    @SerializedName("allowed_memory_ranges")
    val allowedMemoryRanges: List<McpMemoryAccessRange> = emptyList()
) {
    fun requireMemoryAccess(
        scopeType: String,
        scopeKey: String,
        domain: String,
        operation: McpMemoryOperation = McpMemoryOperation.READ
    ) {
        if (allowedMemoryRanges.isEmpty()) return
        val normalizedScopeType = scopeType.trim()
        val normalizedScopeKey = scopeKey.trim()
        val normalizedDomain = domain.trim()
        require(
            normalizedScopeType.isNotEmpty() &&
                normalizedScopeKey.isNotEmpty() &&
                normalizedDomain.isNotEmpty()
        ) {
            "active Agent Mode memory policy requires concrete scope_type, scope_key, and domain"
        }
        require(
            allowedMemoryRanges.any { range ->
                range.allows(
                    scopeType = normalizedScopeType,
                    scopeKey = normalizedScopeKey,
                    domain = normalizedDomain,
                    operation = operation
                )
            }
        ) {
            "active Agent Mode memory policy does not allow " +
                "scope_type=$normalizedScopeType, scope_key=$normalizedScopeKey, " +
                "domain=$normalizedDomain, operation=${operation.name.lowercase()}"
        }
    }
}

enum class McpMemoryOperation {
    READ,
    WRITE
}

enum class McpMemoryAccess {
    @SerializedName("read_only")
    READ_ONLY,

    @SerializedName("read_write")
    READ_WRITE
}

data class McpMemoryAccessRange(
    @SerializedName("scope_type")
    val scopeType: String,
    @SerializedName("domain")
    val domain: String,
    @SerializedName("scope_key")
    val scopeKey: String? = null,
    @SerializedName("access")
    val access: McpMemoryAccess = McpMemoryAccess.READ_WRITE
) {
    init {
        require(scopeType.isNotBlank() && scopeType == scopeType.trim()) {
            "memory access range scopeType must be a trimmed non-empty value"
        }
        require(domain.isNotBlank() && domain == domain.trim()) {
            "memory access range domain must be a trimmed non-empty value"
        }
        require(scopeKey == null || scopeKey.isNotBlank() && scopeKey == scopeKey.trim()) {
            "memory access range scopeKey must be null or a trimmed non-empty value"
        }
    }

    fun matches(
        scopeType: String,
        scopeKey: String,
        domain: String
    ): Boolean {
        return this.scopeType == scopeType &&
            this.domain == domain &&
            (this.scopeKey == null || this.scopeKey == scopeKey)
    }

    fun allows(
        scopeType: String,
        scopeKey: String,
        domain: String,
        operation: McpMemoryOperation
    ): Boolean {
        return matches(scopeType, scopeKey, domain) &&
            (operation == McpMemoryOperation.READ || access == McpMemoryAccess.READ_WRITE)
    }
}
