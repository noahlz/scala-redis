package com.redis.ds

trait Deque[A] {
  // inserts at the head
  def addFirst(a: A): Option[Long]

  // inserts at the tail 
  def addLast(a: A): Option[Long]

  // clears the deque
  def clear: Boolean

  // retrieves, but does not remove the head
  def peekFirst: Option[A]

  // retrieves, but does not remove the tail
  def peekLast: Option[A]

  // true, if empty
  def isEmpty: Boolean

  // retrieves and removes the head element of the deque
  def poll: Option[A]

  // retrieves and removes the head element of the deque
  def pollFirst: Option[A]

  // retrieves and removes the tail element of the deque
  def pollLast: Option[A]

  // size of the deque
  def size: Long
}

import com.redis.ListOperations
import com.redis.serialization.Parse.Implicits._
import com.redis.serialization._

trait RedisDeque[A] 
  extends Deque[A] { self: ListOperations =>

  val blocking: Boolean = false
  val timeoutInSecs: Int = 0
  implicit val f: Format
  implicit val pr: Parse[A]

  val key: String

  def addFirst(a: A): Option[Long] = lpush(key, a) 
  def addLast(a: A): Option[Long] = rpush(key, a)

  def peekFirst: Option[A] = lrange[A](key, 0, 0).map(_.head.get) 

  def peekLast: Option[A] = lrange[A](key, -1, -1).map(_.head.get) 

  def poll: Option[A] =
    if (blocking) {
      blpop[String, A](timeoutInSecs, key).map(_._2)
    } else lpop[A](key)

  def pollFirst: Option[A] = poll

  def pollLast: Option[A] =
    if (blocking) {
      brpop[String, A](timeoutInSecs, key).map(_._2)
    } else rpop[A](key) 

  def size: Long = llen(key) getOrElse(0L)

  def isEmpty: Boolean = size == 0

  def clear: Boolean = size match {
    case 0 => true
    case 1 => 
      val n = poll
      true
    case x => ltrim(key, -1, 0)
  }
}

import com.redis._

abstract class MyRedisDeque[A](bloking: Boolean, timoutInSecs: Int)(implicit format: Format, parse: Parse[A]) 
  extends RedisCommand(RedisClient.SINGLE) with RedisDeque[A] {
    override val blocking = bloking
    override val timeoutInSecs: Int = timoutInSecs
  }

class RedisDequeClient(val h: String, val p: Int, val d: Int = 0, val s: Option[Any] = None, val t : Int =0) {
  def getDeque[A](k: String, blocking: Boolean = false, timeoutInSecs: Int = 0)(implicit format: Format, parse: Parse[A]) = {

    new MyRedisDeque[A](blocking, timeoutInSecs)(format, parse) {
      implicit val f = format
      implicit val pr = parse
      val host = h
      val port = p
      val timeout = t
      val key = k
      override val database = d
      override val secret = s
      override def close(): Unit = disconnect
    }
  }
}
