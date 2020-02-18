package reqdata

final case class NoConfigureDbInRequest(private val message: String = "",
                                   private val cause: Throwable = None.orNull)
  extends Exception(message, cause)
