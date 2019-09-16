package canoe.api

import canoe.api.sources.Polling
import canoe.models.Update
import canoe.models.messages.TelegramMessage
import cats.effect.Concurrent
import cats.effect.concurrent.Ref
import cats.implicits._
import fs2.concurrent.{Queue, Topic}
import fs2.{Pipe, Stream}

/**
  * An instance which can communicate with Telegram service and
  * interact with other Telegram users in a certain predefined way
  */
class Bot[F[_]] private[api] (source: UpdateSource[F])(implicit F: Concurrent[F]) {

  /**
    * Stream of all updates which your bot receives from Telegram service
    */
  def updates: Stream[F, Update] = source.updates

  /**
    * Defines the behavior of the bot.
    *
    * Bot is reacting to the incoming messages following provided scenarios.
    * When the user input is not matching/stops matching particular scenario
    * it means that current interaction is not described with this scenario
    * and bot will not continue acting it out.
    *
    * @example {{{
    *   val scenario = for {
    *     chat <- Scenario.start(command("first").chat)
    *     _    <- Scenario.eval(chat.send("first message received"))
    *     _    <- Scenario.next(command("second"))
    *     _    <- Scenario.eval(chat.send("second message received"))
    *   }
    *
    *  user > /first
    *  bot > first message received
    *  user > something else
    *  *end of the scenario*
    *
    *  user > /first
    *  bot > first message received
    *  user > /second
    *  bot > second message received
    *  *end of the scenario*
    * }}}
    *
    * Each scenario is handled concurrently across all chats,
    * which means that scenario is only blocked if it is already in progress within the same chat.
    *
    * All the behavior is suspended as an effect of resulting stream, without changing its elements.
    * Also, result stream is not halted by the execution of any particular scenario.
    *
    * @return Stream of all updates which your bot receives from Telegram service
    */
  def follow(scenarios: Scenario[F, Unit]*): Stream[F, Update] = {

    def filterById(id: Long): Pipe[F, TelegramMessage, TelegramMessage] =
      _.filter(_.chat.id == id)

    def register(idsRef: Ref[F, Set[Long]], id: Long): F[Boolean] =
      idsRef.modify { ids =>
        val was = ids.contains(id)
        ids + id -> was
      }

    def runSingle(scenario: Scenario[F, Unit],
                  idsRef: Ref[F, Set[Long]],
                  topic: Topic[F, Option[Update]]): Stream[F, Nothing] =
      topic
        .subscribe(1)
        .unNone
        .through(pipes.messages)
        .map { m =>
          Stream
            .eval(register(idsRef, m.chat.id))
            .flatMap {
              case true  => Stream.empty
              case false =>
                //  Using queue in order to avoid blocking topic publisher
                Stream.eval(Queue.unbounded[F, TelegramMessage]).flatMap { queue =>
                  val enq = topic
                    .subscribe(1)
                    .unNone
                    .through(pipes.messages andThen filterById(m.chat.id))
                    .through(queue.enqueue)

                  val deq = queue.dequeue.through(scenario.pipe).drain

                  deq.concurrently(enq)
                }
            }
        }
        .parJoinUnbounded

    def runAll(scenarios: List[Scenario[F, Unit]],
               updates: Stream[F, Update],
               topic: Topic[F, Option[Update]]): Stream[F, Update] = {

      val run = Stream
        .emits(scenarios)
        .zipWith(Stream.repeatEval(Ref[F].of(Set.empty[Long]))) {
          case (scenario, ids) => runSingle(scenario, ids, topic)
        }
        .parJoinUnbounded

      val populate = updates.evalTap(u => topic.publish1(Some(u)))

      populate.concurrently(run)
    }

    Stream.eval(Topic[F, Option[Update]](None)).flatMap { topic =>
      runAll(scenarios.toList, updates, topic)
    }
  }
}

object Bot {

  /**
    * Creates a bot which receives incoming updates using long polling mechanism
    *
    * See [[https://en.wikipedia.org/wiki/Push_technology#Long_polling wiki]].
    */
  def polling[F[_]](implicit F: Concurrent[F], client: TelegramClient[F]): Bot[F] =
    new Bot[F](new Polling[F](client))
}
