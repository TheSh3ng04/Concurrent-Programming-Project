package cp.serverSim

import cats.effect.IOApp
import cats.effect.IO
import com.comcast.ip4s._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.Logger
import org.slf4j.LoggerFactory

object Main extends IOApp.Simple {

  private val logger = LoggerFactory.getLogger(getClass)

  def run: IO[Unit] = {
    logger.info("Starting server...")

    Routes.routes.flatMap { httpRoutes =>
      val httpApp =
        Logger.httpApp(logHeaders = true, logBody = false)(httpRoutes.orNotFound)

      EmberServerBuilder
        .default[IO]
        .withHost(ipv4"127.0.0.1")
        .withPort(port"8888")
        .withHttpApp(httpApp)
        .build
        .useForever
        .onError { e =>
          IO(logger.error("Error: server couldn't start.", e))
        }
    }
  }
}