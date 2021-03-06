package jbok.examples.hg

import jbok.codec._
import jbok.core.Transaction
import jbok.crypto.hashing.{Hash, Hashing}
import jbok.testkit.Cast
import org.scalacheck.{Arbitrary, Gen}
import scalax.collection.Graph
import scalax.collection.GraphEdge.DiEdge
import scalax.collection.GraphPredef._
import scalax.collection.mutable.{Graph => MGraph}
import scodec.bits.ByteVector
import tsec.hashing.jca.SHA256

import scala.util.Random

trait HgGen {
  def genBoundedBytes(minSize: Int, maxSize: Int): Gen[Array[Byte]] =
    Gen.choose(minSize, maxSize).flatMap { sz =>
      Gen.listOfN(sz, Arbitrary.arbitrary[Byte]).map(_.toArray)
    }

  def genBytes(size: Int): Gen[Array[Byte]] = genBoundedBytes(size, size)

  def genTransaction: Gen[Transaction] =
    for {
      b1 <- genBytes(10)
      length <- Gen.chooseNum(1, 10)
      b2 <- genBytes(length)
    } yield {
      Transaction(Hashing[SHA256].hash(ByteVector(b1)), length, ByteVector(b2))
    }

  def emptyEvent(sp: Event, op: Event, creator: Hash, timestamp: Long): Event = {
    val ts = math.max(sp.body.timestamp, op.body.timestamp) + 1L
    val body = EventBody(sp.hash, op.hash, creator, timestamp, sp.body.index + 1, Nil)
    val hash = Hashing.hash[SHA256](body.asBytes)
    Event(body, hash)(EventInfo())
  }

  def genGraph(
      size: Int,
      n: Int,
      g: MGraph[Event, DiEdge] = MGraph.empty,
      lastEvents: Map[Hash, Event] = Map.empty): Graph[Event, DiEdge] = {
    if (size <= 0) {
      g
    } else if (g.order == 0) {
      g += HG.genesis
      val creators = (1 to n).toList.map(i => Cast.name2hash(Cast.names(i)))
      val newEvents = creators.map(c => emptyEvent(HG.genesis, HG.genesis, c, g.nodes.length + 1L))
      newEvents.foreach(e => g += (HG.genesis ~> e))
      genGraph(size - (n + 1), n, g, newEvents.map(x => x.body.creator -> x).toMap)
    } else {
      val Vector(sender, receiver) = Random.shuffle(1 to n).toVector.take(2)
      val op = lastEvents(Cast.name2hash(Cast.names(sender)))
      val sp = lastEvents(Cast.name2hash(Cast.names(receiver)))
      val newEvent = emptyEvent(sp, op, sp.body.creator, g.nodes.length + 1L)
      g += (sp ~> newEvent, op ~> newEvent)
      genGraph(size - 1, n, g, lastEvents + (sp.body.creator -> newEvent))
    }
  }
}
