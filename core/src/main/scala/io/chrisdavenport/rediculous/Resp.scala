package io.chrisdavenport.rediculous

import scala.collection.mutable
import cats.data.NonEmptyList
import cats.implicits._
import scala.util.control.NonFatal
import java.nio.charset.StandardCharsets

sealed trait Resp

object Resp {

  import scala.{Array => SArray}
  import scodec.bits.ByteVector
  
  sealed trait RespParserResult[+A]{
    def extract: Option[A] = this match {
      case ParseComplete(out, _) => Some(out)
      case _ => None
    }
  }
  final case class ParseComplete[A](value: A, rest: ByteVector) extends RespParserResult[A]
  final case class ParseIncomplete(arr: ByteVector) extends RespParserResult[Nothing]
  final case class ParseError(message: String, cause: Option[Throwable]) extends RedisError with RespParserResult[Nothing]
  private[Resp] val CR = '\r'.toByte
  private[Resp] val LF = '\n'.toByte
  private[Resp] val Plus = '+'.toByte
  private[Resp] val Minus = '-'.toByte
  private[Resp] val Colon = ':'.toByte
  private[Resp] val Dollar = '$'.toByte
  private[Resp] val Star = '*'.toByte

  private[Resp] val MinusOne = "-1".getBytes()

  private[Resp] val CRLF = "\r\n".getBytes

  implicit class ByteVectorStringDecoder(s: ByteVector) {
    def prettyprint: String = {
      val str = s.decodeUtf8.getOrElse{throw new RuntimeException("Unable to parse as utf8 string")}
      str.replaceAll("\r", "\\\\r").replaceAll("\n", "\\\\n")
    }

    def decodeSubsetAsUtf8(offset: Int, length: Int): String = {
      s.slice(offset, offset + length).decodeUtf8.getOrElse{throw new RuntimeException("Unable to parse as utf8 string")}
    }
  }

  def renderRequest(nel: NonEmptyList[String]): Resp = {
    Resp.Array(Some(
      nel.toList.map(renderArg)
    ))
  }

  def renderArg(arg: String): Resp = {
    Resp.BulkString(Some(arg))
  }

  def encode(resp: Resp): SArray[Byte] = {
    resp match {
      case s@SimpleString(_) => SimpleString.encode(s)
      case e@Error(_) => Error.encode(e)
      case i@Integer(_) => Integer.encode(i)
      case b@BulkString(_) => BulkString.encode(b)
      case a@Array(_) => Array.encode(a)
    }
  }

  def parseAll(arr: ByteVector): RespParserResult[List[Resp]] = { // TODO Investigate Performance Benchmarks with Chain
    val listBuffer = new mutable.ListBuffer[Resp]
    def loop(arr: ByteVector): RespParserResult[List[Resp]] = {
      if (arr.isEmpty) ParseComplete(listBuffer.toList, arr)
      else parse(arr) match {
        case ParseIncomplete(out) => ParseIncomplete(out)
        // case ParseIncomplete(out) => ParseComplete(listBuffer.toList, out)
        case ParseComplete(value, rest) =>
          listBuffer.append(value)
          loop(rest)
        case e@ParseError(_,_) => e
      }
    }
    loop(arr)
  }

  def parse(arr: ByteVector): RespParserResult[Resp] = {
    if (arr.size > 0) {
      val switchVal = arr(0)
      switchVal match {
        case Plus => SimpleString.parse(arr)
        case Minus => Error.parse(arr)
        case Colon => Integer.parse(arr)
        case Dollar => BulkString.parse(arr)
        case Star => Array.parse(arr)
        case other   => 
          Thread.dumpStack()
          ParseError(s"Resp.parse provided array does not begin with any of the valid bytes +,-,:,$$,* : Got ${other.toChar} in ${arr.prettyprint} of length: ${arr.length}", None)
      }
    } else {
      ParseIncomplete(arr)
    }
  }

    // First Byte is +
    // +foo/r/n
  case class SimpleString(value: String) extends Resp
  object SimpleString {
    def encode(s: SimpleString): SArray[Byte] = {
      val sA = s.value.getBytes(StandardCharsets.UTF_8)
      val buffer = new mutable.ArrayBuffer[Byte](sA.size + 3)
      buffer.append(Plus)
      buffer.++=(sA)
      buffer.++=(CRLF)
      buffer.toArray
    }
    def parse(arr: ByteVector): RespParserResult[SimpleString] = {
      var idx = 1
      try {
        if (arr(0) != Plus) throw ParseError("RespSimple String did not begin with +", None)
        while (idx < arr.size && arr(idx) != CR){
          idx += 1
        }
        if (idx < arr.size && (idx +1 < arr.size) && arr(idx +1) == LF){
          val out = arr.decodeSubsetAsUtf8(1, idx - 1)
          ParseComplete(SimpleString(out), arr.drop(idx + 2))
        } else {
          ParseIncomplete(arr)
        }
      } catch {
        case NonFatal(e) => 
          ParseError(s"Error in RespSimpleString Processing: ${e.getMessage}", Some(e))
      }
    }
  }
  // First Byte is -
  case class Error(value: String) extends RedisError with Resp{
    def message: String = s"Resp Error- $value"
    val cause: Option[Throwable] = None
  }
  object Error {
    def encode(error: Error): SArray[Byte] = 
      SArray(Minus) ++ error.value.getBytes(StandardCharsets.UTF_8) ++ CRLF
    def parse(arr: ByteVector): RespParserResult[Error] = {
      var idx = 1
      try {
        if (arr(0) != Minus) throw ParseError("RespError did not begin with -", None)
        while (idx < arr.size && arr(idx) != CR){
          idx += 1
        }
        if (idx < arr.size && (idx +1 < arr.size) && arr(idx +1) == LF){
          val out = arr.decodeSubsetAsUtf8(1, idx - 1)
          ParseComplete(Error(out), arr.drop(idx + 2))
        } else {
          ParseIncomplete(arr)
        }
      } catch {
        case NonFatal(e) => 
          ParseError(s"Error in Resp Error Processing: ${e.getMessage}", Some(e))
      }
    }
  }
  // First Byte is :
  case class Integer(long: Long) extends Resp
  object Integer {
    def encode(i: Integer): SArray[Byte] = {
      SArray(Colon) ++ i.long.toString().getBytes(StandardCharsets.UTF_8) ++ CRLF
    }
    def parse(arr: ByteVector): RespParserResult[Integer] = {
      var idx = 1
      try {
        if (arr(0) != Colon) throw ParseError("RespInteger String did not begin with :", None)
        while (idx < arr.size && arr(idx) != CR){
          idx += 1
        }
        if (idx < arr.size && (idx +1 < arr.size) && arr(idx +1) == LF){
          val out = arr.decodeSubsetAsUtf8(1, idx - 1).toLong
          ParseComplete(Integer(out), arr.drop(idx + 2))
        } else {
          ParseIncomplete(arr)
        }
      } catch {
        case NonFatal(e) => 
          ParseError(s"Error in  RespInteger Processing: ${e.getMessage}", Some(e))
      }
    }
  }
  // First Byte is $
  // $3/r/n/foo/r/n
  case class BulkString(value: Option[String]) extends Resp
  object BulkString {
    private val empty = SArray(Dollar) ++ MinusOne ++ CRLF
    def encode(b: BulkString): SArray[Byte] = {
      b.value match {
        case None => empty
        case Some(s) => {
          val bytes = s.getBytes(StandardCharsets.UTF_8)
          val size = bytes.size.toString.getBytes(StandardCharsets.UTF_8)
          val buffer = mutable.ArrayBuilder.make[Byte]
          buffer.+=(Dollar)
          buffer.++=(size)
          buffer.++=(CRLF)
          buffer.++=(bytes)
          buffer.++=(CRLF)
          buffer.result()
        }
      }
    }
    def parse(arr: ByteVector): RespParserResult[BulkString] = {
      var idx = 1
      var length = -1
      try {
        if (arr(0) != Dollar) throw ParseError("RespBulkString String did not begin with +", None)
        while (idx < arr.size && arr(idx) != CR){
          idx += 1
        }
        if (idx < arr.size && (idx +1 < arr.size) && arr(idx +1) == LF){
          val out = arr.decodeSubsetAsUtf8(1, idx - 1).toInt
          length = out
          idx += 2
        }
        if (length == -1) ParseComplete(BulkString(None), arr.drop(idx))
        else if (idx + length + 2 <= arr.size)  {
          val out = arr.decodeSubsetAsUtf8(idx, length)
          ParseComplete(BulkString(Some(out)), arr.drop(idx + length + 2))
        } else ParseIncomplete(arr)
      } catch {
        case NonFatal(e) => 
          ParseError(s"Error in BulkString Processing: ${e.getMessage}", Some(e))
      }
    }

  }
  // First Byte is *
  case class Array(a: Option[List[Resp]]) extends Resp
  object Array {
    def encode(a: Array): SArray[Byte] = {
      val buffer = mutable.ArrayBuilder.make[Byte]
      buffer += Star
      a.a match {
        case None => 
          buffer ++= MinusOne
          buffer ++= CRLF
        case Some(value) => 
          buffer ++= value.size.toString().getBytes(StandardCharsets.UTF_8)
          buffer ++= CRLF
          value.foreach(resp => 
            buffer ++= Resp.encode(resp)
          )
      }
      buffer.result()
    }
    def parse(arr: ByteVector): RespParserResult[Array] = {
      var idx = 1
      var length = -1
      try {
        if (arr(0) != Star) throw ParseError("RespArray String did not begin with *", None)
        // read length first
        while (idx < arr.size && arr(idx) != CR){
          idx += 1
        }
        if ((idx +1 < arr.size) && arr(idx +1) == LF){
          val out = arr.decodeSubsetAsUtf8(1, idx - 1).toInt
          length = out
          idx += 2
        }
        else {
          println(s"ArrIncomplete: ${arr.prettyprint}, LENGTH: ${arr.length} idx: ${idx}")
          Thread.dumpStack()
          ParseIncomplete(arr)
        }

        if (length == -1) ParseComplete(Array(None), arr.drop(idx))
        else {
          @scala.annotation.tailrec
          def repeatParse(arr: ByteVector, decrease: Int, accum: List[Resp]) : RespParserResult[Array] = {
            if (decrease == 0) {
              if (accum.length == length)
                ParseComplete(Array(Some(accum.reverse)), arr)
              else ParseIncomplete(ByteVector.empty)
            }

            else 
              Resp.parse(arr) match {
                case i@ParseIncomplete(_) => i
                case ParseComplete(value, rest) => repeatParse(rest, decrease - 1, value :: accum)
                case e@ParseError(_,_) => e
              }
          }
          val next = arr.drop(idx)
          repeatParse(next, length, List.empty)
        }
      } catch {
        case NonFatal(e) => 
          println(s"\r\nBAD: ${arr.prettyprint}, LENGTH: ${arr.length}")
          e.printStackTrace()
          ParseError(s"Error in RespArray Processing: ${e.getMessage}", Some(e))
      }
    }
  }
}