package ai.venice.bluepush

object Log {
    var sink: ((String) -> Unit)? = null

    fun add(msg: String) {
        sink?.invoke("${System.currentTimeMillis() % 100000}  $msg")
    }
}
