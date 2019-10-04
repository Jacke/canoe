package canoe.methods.messages

import canoe.marshalling.codecs._
import canoe.methods.Method
import canoe.models.messages.TelegramMessage
import canoe.models.{ChatId, InlineKeyboardMarkup, InputFile}
import io.circe.generic.semiauto.deriveEncoder
import io.circe.{Decoder, Encoder}

/**
  * Use this method to edit only the reply markup of messages sent by the bot
  * or via the bot (for inline bots).
  *
  * On success, if edited message is sent by the bot, the edited Message is returned,
  * otherwise True is returned.
  *
  * Use methods in companion object in order to construct the value of this class.
  *
  * @param chatId          Unique identifier for the target chat or username of the target channel
  *                        (in the format @channelusername).
  *                        Required if 'inlineMessageId' is not specified.
  * @param messageId       Unique identifier of the sent message.
  *                        Required if 'inlineMessageId' is not specified.
  * @param inlineMessageId Identifier of the inline message.
  *                        Required if 'chatId' and 'messageId' are not specified.
  * @param replyMarkup     New inline keyboard.
  */
case class EditMessageReplyMarkup private (chatId: Option[ChatId],
                                           messageId: Option[Int],
                                           inlineMessageId: Option[String],
                                           replyMarkup: Option[InlineKeyboardMarkup] = None)

object EditMessageReplyMarkup {
  import io.circe.generic.auto._

  /**
    * For the messages sent directed by the bot
    */
  def direct(chatId: ChatId,
             messageId: Int,
             replyMarkup: Option[InlineKeyboardMarkup] = None): EditMessageReplyMarkup =
    EditMessageReplyMarkup(Some(chatId), Some(messageId), None, replyMarkup)

  /**
    * For the inlined messages sent via the bot
    */
  def inlined(inlineMessageId: String,
              replyMarkup: Option[InlineKeyboardMarkup] = None): EditMessageReplyMarkup =
    EditMessageReplyMarkup(None, None, Some(inlineMessageId), replyMarkup)

  implicit val method: Method[EditMessageReplyMarkup, Either[Boolean, TelegramMessage]] =
    new Method[EditMessageReplyMarkup, Either[Boolean, TelegramMessage]] {

      def name: String = "editMessageReplyMarkup"

      def encoder: Encoder[EditMessageReplyMarkup] = deriveEncoder[EditMessageReplyMarkup].snakeCase

      def decoder: Decoder[Either[Boolean, TelegramMessage]] =
        eitherDecoder(
          Decoder.decodeBoolean,
          TelegramMessage.telegramMessageDecoder
        )

      def uploads(request: EditMessageReplyMarkup): List[(String, InputFile)] = Nil
    }
}
