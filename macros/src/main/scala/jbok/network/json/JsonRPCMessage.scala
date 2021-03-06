package jbok.network.json

import java.nio.charset.StandardCharsets

import cats.implicits._
import io.circe._
import io.circe.generic.JsonCodec
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.parser._
import jbok.network.json.JsonRPCMessage.RequestId
import scodec.Codec

@JsonCodec
case class JsonRPCId(id: String)

sealed trait JsonRPCMessage[+A]

object JsonRPCMessage {
  type RequestId = String

  implicit def encoder[A: Encoder]: Encoder[JsonRPCMessage[A]] = new Encoder[JsonRPCMessage[A]] {
    override def apply(a: JsonRPCMessage[A]): Json = {
      val json = a match {
        case r: JsonRPCRequest[A] => r.asJson
        case r: JsonRPCNotification[A] => r.asJson
        case r: JsonRPCResponse[A] => r.asJson
      }
      json.mapObject(_.add("jsonrpc", "2.0".asJson))
    }
  }

  implicit def decoder[A: Decoder]: Decoder[JsonRPCMessage[A]] = Decoder.decodeJsonObject.emap[JsonRPCMessage[A]] {
    obj =>
      val json = Json.fromJsonObject(obj)
      val result = if (obj.contains("id")) {
        if (obj.contains("error")) json.as[JsonRPCError]
        else if (obj.contains("result")) json.as[JsonRPCResult[A]]
        else json.as[JsonRPCRequest[A]]
      } else {
        json.as[JsonRPCNotification[A]]
      }
      result.leftMap(_.toString)
  }

  implicit def binaryCodec[A: Encoder: Decoder](): Codec[JsonRPCMessage[A]] =
    scodec.codecs
      .string(StandardCharsets.UTF_8)
      .xmap[JsonRPCMessage[A]](x => decode[JsonRPCMessage[A]](x).right.get, _.asJson.noSpaces)
}

@JsonCodec
case class JsonRPCRequest[A](id: RequestId, method: String, params: A) extends JsonRPCMessage[A]

@JsonCodec
case class JsonRPCNotification[A](method: String, params: A) extends JsonRPCMessage[A]

sealed trait JsonRPCResponse[+A] extends JsonRPCMessage[A]
object JsonRPCResponse {
  implicit def encoder[A: Encoder]: Encoder[JsonRPCResponse[A]] = new Encoder[JsonRPCResponse[A]] {
    override def apply(a: JsonRPCResponse[A]): Json = {
      val json = a match {
        case r: JsonRPCResult[A] => r.asJson
        case e: JsonRPCError => e.asJson
      }
      json.mapObject(_.add("jsonrpc", "2.0".asJson))
    }
  }

  implicit def decoder[A: Decoder]: Decoder[JsonRPCResponse[A]] = Decoder.decodeJsonObject.emap[JsonRPCResponse[A]] {
    obj =>
      val json = Json.fromJsonObject(obj)
      val result =
        if (obj.contains("error")) json.as[JsonRPCError]
        else json.as[JsonRPCResult[A]]

      result.leftMap(_.toString)
  }

  def ok[A](id: RequestId, result: A): JsonRPCResult[A] = JsonRPCResult(id, result)

  def error(id: RequestId, error: ErrorObject): JsonRPCError =
    JsonRPCError(id, error)

  def internalError(message: String): JsonRPCError =
    internalError(RequestId.Null, message)

  def internalError(id: RequestId, message: String): JsonRPCError =
    JsonRPCError(id, ErrorObject(ErrorCode.InternalError, message, None))

  def invalidParams(message: String): JsonRPCError =
    invalidParams(RequestId.Null, message)

  def invalidParams(id: RequestId, message: String): JsonRPCError =
    JsonRPCError(id, ErrorObject(ErrorCode.InvalidParams, message, None))

  def invalidRequest(message: String): JsonRPCError =
    JsonRPCError(RequestId.Null, ErrorObject(ErrorCode.InvalidRequest, message, None))

  def cancelled(id: RequestId): JsonRPCError =
    JsonRPCError(id, ErrorObject(ErrorCode.RequestCancelled, "", None))

  def parseError(message: String): JsonRPCError =
    JsonRPCError(RequestId.Null, ErrorObject(ErrorCode.ParseError, message, None))

  def methodNotFound(method: String, id: Option[RequestId] = None): JsonRPCError =
    JsonRPCError(
      id.getOrElse(RequestId.Null),
      ErrorObject(ErrorCode.MethodNotFound, s"method $method does not exist", None))
}

@JsonCodec
case class JsonRPCResult[A](id: RequestId, result: A) extends JsonRPCResponse[A]

@JsonCodec
case class JsonRPCError(id: RequestId, error: ErrorObject) extends JsonRPCResponse[Nothing]
