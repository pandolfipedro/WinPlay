package diplay.desktop

object PlatformLog {
    @Volatile var listener: ((String) -> Unit)? = null
    private fun log(tag: String, message: String, error: Throwable? = null): Int {
        listener?.invoke("$tag: $message${error?.message?.let { " ($it)" } ?: ""}")
        return 0
    }
    fun d(tag: String, message: String) = log(tag, message)
    fun i(tag: String, message: String) = log(tag, message)
    fun v(tag: String, message: String) = log(tag, message)
    fun w(tag: String, message: String) = log(tag, message)
    fun w(tag: String, message: String, error: Throwable) = log(tag, message, error)
    fun e(tag: String, message: String) = log(tag, message)
    fun e(tag: String, message: String, error: Throwable) = log(tag, message, error)
}
