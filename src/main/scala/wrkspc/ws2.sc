import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse, Uri}
import application.ReqResp
import application.ReqResp.{route404, routeDicts, routeGetDebug, routeGetFavicon}
import confs.DbConfig
import data.Cache
import envs.EnvContainer.ZEnvLog
import zio.{Ref, Runtime, ZIO}

import scala.concurrent.Future

def reqHandlerM(dbConfigList: DbConfig, actorSystem: ActorSystem, cache: Ref[Cache])(request: HttpRequest):
Future[HttpResponse] = {
  implicit val system: ActorSystem = actorSystem

  lazy val responseFuture: ZIO[ZEnvLog, Throwable, HttpResponse] = request
  match {
    case request@HttpRequest(HttpMethods.GET, _, _, _, _) =>
      request match {
        case request@HttpRequest(_, Uri.Path("/debug"), _, _, _) => routeGetDebug(request)
      }
  }

  Runtime.default.unsafeRunToFuture(
    responseFuture.provideLayer(zio.ZEnv.live >>> envs.EnvContainer.ZEnvLogLayer)
  )
}