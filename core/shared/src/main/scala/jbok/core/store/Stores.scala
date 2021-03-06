package jbok.core.store

import cats.data.OptionT
import cats.effect.Sync
import cats.implicits._
import jbok.core.models.{BlockBody, BlockHeader, Receipt, _}
import jbok.crypto.authds.mpt.{MPTrie, Node}
import jbok.persistent.{KeyValueDB, KeyValueStore}
import scodec.bits.ByteVector
import jbok.codec.codecs._

class BlockHeaderStore[F[_]: Sync](db: KeyValueDB[F])
    extends KeyValueStore[F, ByteVector, BlockHeader](Namespaces.BlockHeader, db)

class BlockBodyStore[F[_]: Sync](db: KeyValueDB[F])
    extends KeyValueStore[F, ByteVector, BlockBody](Namespaces.BlockBody, db)

class ReceiptStore[F[_]: Sync](db: KeyValueDB[F])
    extends KeyValueStore[F, ByteVector, List[Receipt]](Namespaces.Receipts, db)

class BlockNumberHashStore[F[_]: Sync](db: KeyValueDB[F])
    extends KeyValueStore[F, BigInt, ByteVector](Namespaces.Heights, db)

class TransactionLocationStore[F[_]: Sync](db: KeyValueDB[F])
    extends KeyValueStore[F, ByteVector, TransactionLocation](Namespaces.TransactionLocation, db)

class AppStateStore[F[_]: Sync](db: KeyValueDB[F])
    extends KeyValueStore[F, String, ByteVector](Namespaces.AppStateNamespace, db) {
  private val BestBlockNumber = "BestBlockNumber"
  private val FastSyncDone = "FastSyncDone"
  private val EstimatedHighestBlock = "EstimatedHighestBlock"
  private val SyncStartingBlock = "SyncStartingBlock"

  def getBestBlockNumber: F[BigInt] =
    for {
      opt <- getOpt(BestBlockNumber)
      bn <- opt match {
        case Some(n) => decode[BigInt](n)
        case None    => BigInt(0).pure[F]
      }
    } yield bn

  def putBestBlockNumber(bestBlockNumber: BigInt): F[Unit] =
    encode(bestBlockNumber).flatMap(bn => put(BestBlockNumber, bn))

  def getFastSyncDone: F[Boolean] = get(FastSyncDone).flatMap(v => decode[Boolean](v))

  def putFastSyncDone(b: Boolean = true): F[Unit] = encode(b).flatMap(v => put(FastSyncDone, v))

  def getEstimatedHighestBlock: F[BigInt] = {
    val h = for {
      v <- OptionT(getOpt(EstimatedHighestBlock))
      h <- OptionT.liftF(decode[BigInt](v))
    } yield h

    h.value.map(_.getOrElse(0))
  }

  def putEstimatedHighestBlock(n: BigInt): F[Unit] =
    encode(n).flatMap(v => put(EstimatedHighestBlock, v))

  def getSyncStartingBlock: F[BigInt] = {
    val h = for {
      v <- OptionT(getOpt(SyncStartingBlock))
      h <- OptionT.liftF(decode[BigInt](v))
    } yield h

    h.value.map(_.getOrElse(0))
  }

  def putSyncStartingBlock(n: BigInt): F[Unit] =
    encode(n).flatMap(v => put(SyncStartingBlock, v))
}

class MPTNodeStore[F[_]: Sync](val mpt: MPTrie[F])
    extends KeyValueStore[F, Address, Account](Namespaces.NodeNamespace, mpt) {

  def getRootHash: F[ByteVector] = mpt.getRootHash

  def getRoot: F[Node] = mpt.getRoot

  def getNodeByHash(hash: ByteVector): F[Node] = mpt.getNodeByHash(hash)

  def size: F[Int] = mpt.size

  def clear(): F[Unit] = mpt.clear()
}

object MPTNodeStore {
  def apply[F[_]: Sync](db: KeyValueDB[F]): F[MPTNodeStore[F]] =
    for {
      trie <- MPTrie[F](db)
    } yield new MPTNodeStore[F](trie)
}
