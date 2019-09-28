package canoe.api.clients

import canoe.api.{FailedMethod, ResponseDecodingError, TelegramClient}
import canoe.methods.Method
import canoe.models.{InputFile, Response => TelegramResponse}
import cats.effect.Sync
import cats.syntax.all._
import fs2.Stream
import io.chrisdavenport.log4cats.Logger
import org.http4s._
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl._
import org.http4s.multipart.{Multipart, Part}

private[api] class Http4sTelegramClient[F[_]: Sync: Logger](token: String, client: Client[F])
    extends TelegramClient[F] {

  private val botApiUri: Uri = Uri.unsafeFromString("https://api.telegram.org") / s"bot$token"

  def execute[Req, Res](request: Req)(implicit M: Method[Req, Res]): F[Res] = {

    val req = prepareRequest(botApiUri / M.name, M, request)

    implicit val decoder: EntityDecoder[F, TelegramResponse[Res]] =
      jsonOf(Sync[F], TelegramResponse.decoder(M.decoder))

    Logger[F].debug(s"Executing '${M.name}' Telegram method.") *>
      client
        .expect[TelegramResponse[Res]](req)
        .recoverWith { case error: InvalidMessageBodyFailure => handleUnknownEntity(M.name, request, error) }
        .flatMap(handleTelegramResponse(M, request))
  }

  private def handleUnknownEntity[I, A](method: String, input: I, error: InvalidMessageBodyFailure): F[A] =
    Logger[F].error(
      s"Received unknown Telegram entity during execution of '$method' method. \nInput data: $input. \n${error.details}"
    ) *>
      Sync[F].raiseError(ResponseDecodingError(error.details.dropWhile(_ != '{')))

  private def prepareRequest[Req, Res](url: Uri, method: Method[Req, Res], action: Req): F[Request[F]] = {
    val uploads = method.uploads(action).collect {
      case (name, InputFile.Upload(filename, contents)) =>
        Part.fileData(name, filename, Stream.emits(contents).covary[F])
    }

    if (uploads.isEmpty) jsonRequest(url, method, action)
    else multipartRequest(url, method, action, uploads)
  }

  private def jsonRequest[Req, Res](url: Uri, method: Method[Req, Res], action: Req): F[Request[F]] =
    Method.POST(action, url)(Sync[F], jsonEncoderOf(Sync[F], method.encoder))

  private def multipartRequest[Req, Res](url: Uri,
                                         method: Method[Req, Res],
                                         action: Req,
                                         parts: List[Part[F]]): F[Request[F]] = {
    val multipart = Multipart[F](parts.toVector)

    val params =
      method
        .encoder(action)
        .asObject
        .map(
          _.toIterable
            .filterNot(kv => kv._2.isNull || kv._2.isObject)
            .map { case (k, j) => k -> j.toString }
            .toMap
        )
        .getOrElse(Map.empty)

    val urlWithQueryParams = params.foldLeft(url) {
      case (url, (key, value)) => url.withQueryParam(key, value)
    }

    Method.POST(multipart, urlWithQueryParams).map(_.withHeaders(multipart.headers))
  }

  private def handleTelegramResponse[A, I, C](m: Method[I, A], input: I)(response: TelegramResponse[A]): F[A] =
    response match {
      case TelegramResponse(true, Some(result), _, _, _) => result.pure[F]

      case failed =>
        Logger[F].error(s"Received failed response from Telegram: $failed. Method name: ${m.name}, input data: $input") *>
          Sync[F].raiseError[A](FailedMethod(m, input, failed))
    }
}
