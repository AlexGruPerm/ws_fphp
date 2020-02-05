override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] = {
  WsApp(args).foldM(
    throwable => putStrLn(s"Error: ${throwable.getMessage}") *> URIO.foreach(throwable.getStackTrace) {
      sTraceRow => putStrLn(s"$sTraceRow")
    }.map(_ => UIO.succeed(1)).flatten,
    _ => UIO.succeed(0)
  )
}

object WsServObj {
  private val WsApp: List[String] => ZIO[ZEnv, Throwable, Unit] = args =>
    for {
      cfg <- Configuration.config.load("C:\\ws_fphp\\src\\main\\resources\\application.conf")
      res <- WsServObj.WsServer(cfg)
    } yield res


  val wsRes = Managed.make(Task(ActorSystem("WsDb")))(sys => Task.fromFuture(_ => sys.terminate()).ignore).use(actorSystem =>
    for {
      _ <- putStrLn("start WsServer")
      reqHandlerResult <- startRequestHandler(conf, actorSystem).flatMap(_ => ZIO.never)
    } yield reqHandlerResult
  )

  def startRequestHandler(conf: Config, actorSystem: ActorSystem): ZIO[Any, Throwable, Unit] = {
    val reqHandler1: HttpRequest => Future[HttpResponse] = {
      case request@HttpRequest(HttpMethods.POST, Uri.Path("/test"), httpHeader, requestEntity, requestProtocol)
      => logRequest(log, request)
        Future.successful {
          val resJson: Json = s"SimpleTestString ${request.uri}".asJson
          HttpResponse(
            StatusCodes.OK,
            entity = HttpEntity(`application/json`, Printer.noSpaces.print(resJson))
          )
        }
    }
    serverSource.runForeach { connection =>
      connection.handleWithAsyncHandler(reqHandler1)
    }
    UIO.succeed(())
  }
}


