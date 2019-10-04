package canoe.models

/**
  * Represents Telegram voice note
  */
case class Voice(fileId: String, duration: Int, mimeType: Option[String], fileSize: Option[Int])
