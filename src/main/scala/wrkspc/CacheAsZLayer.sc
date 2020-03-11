import zio._
import zio.ZLayer
import zio.console._

object foo {
  //#1
  type CacheManager[K, V] = Has[CacheManager.Service[K, V]]
  //#2
  object CacheManager {

    //#3
    trait Service[K, V] {
      def get(key: K): UIO[Option[V]]
      def set(key: K, value: V): UIO[Unit]
    }

    //#4
    /*
    final class RefCache[K, V] extends CacheManager.Service[K, V] {
      val cache: UIO[Ref[Map[K, V]]] = Ref.make(Map.empty[K, V])
      override def get(key: K): UIO[Option[V]] = cache.flatMap(c => c.get.map(_.get(key)))
      override def set(key: K, value: V): zio.UIO[Unit] = cache.flatMap(c => c.update(_.updated(key, value)).unit)
    }
    */
    final class RefCache[K, V](ref: Ref[Map[K, V]]) extends CacheManager.Service[K, V] {
      override def get(key: K): UIO[Option[V]] = ref.get.map(_.get(key))
      override def set(key: K, value: V): UIO[Unit] = ref.update(_.updated(key, value))
    }

    //#5

    def RefCache[K, V](implicit tag: Tagged[CacheManager.Service[K, V]]
                      ): ZLayer.NoDeps[Nothing, CacheManager[K, V]] = {
      ZLayer.fromEffect[
        Any,
        Nothing,
        CacheManager.Service[K, V]
      ] {
        //UIO(new RefCache[K, V])
        Ref.make(Map.empty[K, V]).map(refEmpty => new RefCache[K, V](refEmpty))
      }
    }


  }
}

import foo._
object MyApp extends App {

  lazy val myenv: ZLayer[Any, Nothing, ZEnv with CacheManager[Int,String]] =
    ZEnv.live ++ CacheManager.RefCache[Int,String]

  val WsApp: List[String] => ZIO[ZEnv with CacheManager[Int,String], Throwable, Unit] = args =>
    for {
      c <- ZIO.access[CacheManager[Int,String]](_.get)
      _ <- c.set(1,"String 1")
      _ <- c.set(2,"String 2")
      _ <- c.set(3,"String 3")
      v <- c.get(2)
      _ <- putStrLn(s" for key = 2 value = [$v]")
      res <- Task.unit
    } yield res

  def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    WsApp(args).provideCustomLayer(myenv).fold(_ => 1, _ => 0)

}

val runtime = Runtime.default
runtime.unsafeRun(MyApp.run(List()).provideLayer(MyApp.myenv))
