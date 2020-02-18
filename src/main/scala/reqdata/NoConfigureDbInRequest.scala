package reqdata

import java.util.NoSuchElementException

final case class NoConfigureDbInRequest(private val message: String = "",
                                   private val cause: Throwable = None.orNull)
  extends Throwable(message, cause)
