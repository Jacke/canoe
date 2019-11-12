canoe
=============

[![Build Status](https://travis-ci.org/augustjune/canoe.svg?branch=master)](https://travis-ci.org/augustjune/canoe)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.augustjune/canoe_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.augustjune/canoe_2.12)
[![Gitter](https://badges.gitter.im/augustjune-canoe/community.svg)](https://gitter.im/augustjune-canoe/community?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)

[![Telegram](https://img.shields.io/badge/Bot%20API-4.4%20(July%2029%2C%202019)-00aced.svg)](https://core.telegram.org/bots/api#recent-changes)
    
### Overview
**canoe** is a purely functional, compositional library for building interactive Telegram bots.
It provides functional streaming interface over [Telegram Bot API](https://core.telegram.org/bots/api) with built-in abstractions for describing your chatbot behavior.

### Getting started
sbt dependency:
```scala
libraryDependencies += "org.augustjune" %% "canoe" % "<version>"
```
You can find the latest version in [releases](https://github.com/augustjune/canoe/releases) tab 
or by clicking on the maven-central badge. The library is available for Scala 2.12, 2.13, and Scala.js.

Imports:
```scala
import canoe.api._
import canoe.syntax._
```

### The problem
Building interactive chatbots requires maintaining the state of each conversation, 
with possible interaction across them and/or using shared resources.
The complexity of this task grows rapidly with the advancement of the bot.
**canoe** solves this problem by decomposing behavior of the bot into the set of scenarios 
which the chatbot will follow.

### Example
Here's a quick example of how the definition of simple bot behavior looks like in **canoe**. 
More samples can be found [here](https://github.com/augustjune/canoe/tree/master/examples/src/main/scala/samples). 

```scala
import canoe.api._
import canoe.syntax._
import cats.effect.ConcurrentEffect
import fs2.Stream

def app[F[_]: ConcurrentEffect]: F[Unit] = 
  Stream
    .resource(TelegramClient.global[F](token))
    .flatMap { implicit client => Bot.polling[F].follow(greetings) }
    .compile.drain

def greetings[F[_]: TelegramClient]: Scenario[F, Unit] =
    for {
      chat  <- Scenario.start(command("hi").chat)
      _     <- Scenario.eval(chat.send("Hello. What's your name?"))
      name  <- Scenario.next(text)
      _     <- Scenario.eval(chat.send(s"Nice to meet you, $name"))
    } yield ()
```

Regardless of whether you prefer to use scenarios for building a bot, 
you are still able to use all functionality of Telegram Bot API in a streaming context, 
as it is demonstrated [here](https://github.com/augustjune/canoe/blob/master/examples/src/main/scala/samples/NoScenario.scala).

### Telegram Bot API methods
Low level abstractions are available through standalone Telegram Bot API methods from `canoe.methods` package.
Having instance of `TelegramClient` in implicit scope, 
you can use `call` method on constructed action in order to execute it in effect `F`.

```scala
def sendText[F[_]: TelegramClient](chatId: Long, text: String): F[TextMessage] = 
  SendMessage(chatId, text).call
```

As an alternative, all the methods from Telegram Bot API are available from corresponding models,
e.g.`chat.kickUser(user.id)`, `message.editText("edited")`.

### Webhook support
**canoe** also provides support for obtaining messages from Telegram by setting a webhook.
The same app described above would look this way using webhook version. 
Full example may be found [here](https://github.com/augustjune/canoe/blob/master/examples/src/main/scala/samples/WebhookGreetings.scala).

```scala
import canoe.api._
import canoe.syntax._
import cats.effect.{ConcurrentEffect, Timer}
import fs2.Stream

val url: String = "<your server url>"

def app[F[_]: ConcurrentEffect: Timer]: F[Unit] =
    Stream
      .resource(TelegramClient.global[F](token))
      .flatMap { implicit client =>
        Stream.resource(Bot.hook[F](url)).flatMap(_.follow(greetings))
      }
      .compile.drain

def greetings[F[_]: TelegramClient]: Scenario[F, Unit] = ???  // Scenario stays unchanged
```
Setting a webhook you have to specify the `url` to which Telegram updates will be sent. 
This address must be reachable for the Telegram, 
so in case you're using local environment you'd have to expose your local host to the Internet.
It can be achieved using **ngrok** simply following this [comprehensive guide](https://developer.github.com/webhooks/configuring/#using-ngrok).

### Handling errors
There's a lot of things that may go wrong during your scenarios executions, 
from user input to the network issues.
For this reason, `Scenario` has `MonadError` instance for any `F`.  
It means that you can use built-in `handleErrorWith` and `attempt` methods,
in order to react to the raised error or ensure that bot workflow won't break.
Full example may be found [here](https://github.com/augustjune/canoe/blob/master/examples/src/main/scala/samples/ErrorHandling.scala).

### Contribution
If you're interested in the project PRs are very welcomed.
In case it's a feature you'd like to introduce, it is recommended to discuss it first by raising an issue 
or simply using [gitter](https://gitter.im/augustjune-canoe/community).