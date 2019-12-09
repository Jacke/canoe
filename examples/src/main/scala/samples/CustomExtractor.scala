package samples

import canoe.api._
import canoe.models.messages.UserMessage
import canoe.syntax._
import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.functor._
import fs2.Stream

object CustomExtractor extends IOApp {
  val token: String = "<your telegram token>"
  val userIdToGreet: Int = -1

  def run(args: List[String]): IO[ExitCode] =
    Stream
      .resource(TelegramClient.global[IO](token))
      .flatMap { implicit client =>
        Bot.polling[IO].follow(greetParticularUser(userIdToGreet))
      }
      .compile
      .drain
      .as(ExitCode.Success)

  def greetParticularUser[F[_]: TelegramClient](userId: Int): Scenario[F, Unit] =
    for {
      msg <- Scenario.start(expectParticularUsersMessages(userId))
      _   <- Scenario.eval(msg.chat.send(s"I was waiting for you ${name(msg)}"))
    } yield ()

  def expectParticularUsersMessages(userId: Int): Expect[UserMessage] = {
    case m: UserMessage if m.from.map(_.id).contains(userId) => m
  }

  def name(msg: UserMessage): String = msg.from.map(_.firstName).getOrElse("unknown")
}
