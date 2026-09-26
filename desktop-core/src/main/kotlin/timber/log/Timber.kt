package timber.log

import java.util.logging.Level
import java.util.logging.Logger

object Timber {
    private val logger = Logger.getLogger("MetroList")

    fun d(message: String) {
        logger.fine(message)
    }

    fun w(message: String) {
        logger.warning(message)
    }

    fun e(throwable: Throwable, message: String) {
        logger.log(Level.SEVERE, message, throwable)
    }
}
