package io.legado.app.quickjs

internal object QuickJsSandboxProtocol {
    const val PROCESS_SUFFIX = ":quickjs_sandbox"

    const val MAX_INPUT_CHARS = 384_000
    const val MAX_INPUT_BYTES = MAX_INPUT_CHARS * 4
    const val MAX_OUTPUT_CHARS = 65_536
    const val MEMORY_LIMIT_BYTES = 64L * 1024L * 1024L
    const val MAX_STACK_SIZE_BYTES = 512L * 1024L
    const val EVALUATION_TIMEOUT_MILLIS = 15_000L
    const val BIND_TIMEOUT_MILLIS = 5_000L
    const val TIMEOUT_DETECTION_TOLERANCE_MILLIS = 500L

    const val KEY_SUCCESS = "success"
    const val KEY_VALUE = "value"
    const val KEY_ERROR = "error"

    const val ERROR_UNSUPPORTED_API = "UNSUPPORTED_API"
    const val ERROR_MAIN_THREAD = "MAIN_THREAD"
    const val ERROR_INPUT_TOO_LARGE = "INPUT_TOO_LARGE"
    const val ERROR_INPUT_PIPE_FAILED = "INPUT_PIPE_FAILED"
    const val ERROR_INPUT_READ_FAILED = "INPUT_READ_FAILED"
    const val ERROR_OUTPUT_TOO_LARGE = "OUTPUT_TOO_LARGE"
    const val ERROR_BIND_FAILED = "BIND_FAILED"
    const val ERROR_BIND_TIMEOUT = "BIND_TIMEOUT"
    const val ERROR_PROCESS_DIED = "PROCESS_DIED"
    const val ERROR_EVALUATION_TIMEOUT = "EVALUATION_TIMEOUT"
    const val ERROR_EVALUATION_FAILED = "EVALUATION_FAILED"
    const val ERROR_INVALID_RESPONSE = "INVALID_RESPONSE"
}
