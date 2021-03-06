//package jbok.crypto.authds.mpt
//
//import cats.effect.Effect
//import cats.implicits._
//import jbok.crypto._
//import jbok.crypto.authds.mpt.Node._
//import jbok.persistent.BatchKeyValueDB
//import jbok.persistent.leveldb.{LevelDB, LevelDBConfig}
//import scodec.bits.ByteVector
//import scodec.codecs.implicits.implicitByteVectorCodec
//
//object MerklePatriciaTrie {
//  def hash(bytes: ByteVector): ByteVector = bytes.kec256
//
//  val emptyRoot = BlankNode
//
//  val emptyRootHash = hash(NodeCodec.encode(emptyRoot).require)
//
//  val alphabet: Vector[String] = "0123456789abcdef".map(_.toString).toVector
//
//  def apply[F[_]: Effect](
//      db: BatchKeyValueDB[F],
//      root: Node = emptyRoot,
//      rootHash: ByteVector = emptyRootHash
//  ): MerklePatriciaTrie[F] = new MerklePatriciaTrie(db, root, rootHash)
//
////  def apply[F[_]: Effect](config: LevelDBConfig): F[MerklePatriciaTrie[F]] = {
////    for {
////      db <- LevelDB[F](config)
////      store = new Store[F, ByteVector, ByteVector](db)
////    } yield new MerklePatriciaTrie[F](store, emptyRoot, emptyRootHash)
////  }
//}
//
//import jbok.crypto.authds.mpt.MerklePatriciaTrie._
//
//class MerklePatriciaTrie[F[_]](db: BatchKeyValueDB[F], root: Node, rootHash: ByteVector)(implicit F: Effect[F]) {
//
//  def get(key: String): F[Option[ByteVector]] = {
//    def get0(node: Node, key: String): F[Option[ByteVector]] =
//      node match {
//        case BlankNode => F.pure(None)
//
//        case bn @ BranchNode(_, value) =>
//          if (key.isEmpty) {
//            F.pure(value)
//          } else {
//            for {
//              subNode <- bn.branchAt(key.head) match {
//                case Left(hash) => decode(Left(hash))
//                case Right(n)   => n.pure[F]
//              }
//              r <- get0(subNode, key.tail)
//            } yield r
//          }
//
//        case LeafNode(cur, value) =>
//          F.pure(if (cur == key) Some(value) else None)
//
//        case ExtensionNode(cur, child) =>
//          for {
//            subNode <- child match {
//              case Left(hash) => decode(Left(hash))
//              case Right(n)   => n.pure[F]
//            }
//            r <- if (key.startsWith(cur))
//              get0(subNode, key.slice(cur.length, key.length))
//            else
//              F.pure(None)
//          } yield r
//      }
//
//    get0(root, key)
//  }
//
//  def clear(): F[MerklePatriciaTrie[F]] =
//    for {
//      _ <- deleteChildStorage(root)
//      _ <- deleteNodeStorage(root)
//      _ <- db.commit()
//    } yield MerklePatriciaTrie(db, emptyRoot, emptyRootHash)
//
//  def update(key: String, value: ByteVector): F[MerklePatriciaTrie[F]] =
//    for {
//      root <- updateAndDeleteStorage(root, key, value)
//      rootHash <- updateRootHash(root)
//      _ <- db.commit()
//    } yield MerklePatriciaTrie[F](db, root, rootHash)
//
//  def size: F[Int] = {
//    def size0(node: Node): F[Int] = node match {
//      case BlankNode               => 0.pure[F]
//      case LeafNode(_, _)          => 1.pure[F]
//      case ExtensionNode(_, child) => decode(child) >>= size0
//      case BranchNode(branches, value) =>
//        for {
//          bn <- branches.traverse(x => decode(x) >>= size0).map(_.sum)
//        } yield bn + (if (value.isEmpty) 0 else 1)
//    }
//
//    size0(root)
//  }
//
//  def delete(key: String): F[MerklePatriciaTrie[F]] =
//    for {
//      root <- deleteAndDeleteStorage(root, key)
//      rootHash <- updateRootHash(root)
//      _ <- db.commit()
//    } yield MerklePatriciaTrie(db, root, rootHash)
//
//  def toMap: F[Map[String, ByteVector]] = {
//    def toMap0(node: Node): F[Map[String, ByteVector]] = node match {
//      case BlankNode => F.pure(Map.empty)
//      case LeafNode(key, value) =>
//        F.pure(Map(key -> value))
//      case ExtensionNode(key, entry) =>
//        for {
//          decoded <- decode(entry)
//          subMap <- toMap0(decoded)
//        } yield subMap.map { case (k, v) => key ++ k -> v }
//      case BranchNode(branches, value) =>
//        for {
//          xs <- branches.zipWithIndex.traverse { case (e, i) => (decode(e) >>= toMap0).map(_ -> i) }
//          m = xs.foldLeft(Map.empty[String, ByteVector]) {
//            case (acc, (cur, i)) => acc ++ cur.map { case (k, v) => alphabet(i) ++ k -> v }
//          }
//        } yield if (value.isDefined) m + ("" -> value.get) else m
//    }
//
//    toMap0(root)
//  }
//
//  // ------------------------------------------
//
//  private def encode(node: Node): F[NodeEntry] =
//    for {
//      encoded <- F.delay(NodeCodec.encode(node).require)
//      e <- if (encoded.size < 32) {
//        Left(node).pure[F]
//      } else {
//        val key = hash(encoded)
//        db.put(key, encoded) *> Left(key).pure[F]
//      }
//    } yield e
//
//  private def decode(entry: NodeEntry): F[Node] = entry match {
//    case Left(hash) if hash == emptyRootHash => F.pure(BlankNode)
//    case Left(hash) =>
//      for {
//        encoded <- db.get(hash)
//      } yield NodeCodec.decode(encoded).require
//    case Right(node) => node.pure[F]
//  }
//
//  private def updateRootHash(root: Node): F[ByteVector] = {
//    val encoded = NodeCodec.encode(root).require
//    val key = hash(encoded)
//    db.put(key, encoded)
//    key.pure[F]
//  }
//
//  private def updateAndDeleteStorage(node: Node, key: String, value: ByteVector): F[Node] =
//    for {
//      newNode <- updateNode(node, key, value)
//      _ <- if (newNode != node) deleteNodeStorage(node) else F.unit
//    } yield newNode
//
//  private def updateNode(node: Node, key: String, value: ByteVector): F[Node] = node match {
//    case BlankNode =>
//      F.pure(LeafNode(key, value)) // empty, insert a key-value anyway
//
//    case bn @ BranchNode(_, _) =>
//      if (key.isEmpty) {
//        // key matches, update the value directly
//        F.pure(bn.mapValue(value))
//      } else {
//        // recursively update the branch
//        for {
//          decoded <- decode(bn.branchAt(key.head))
//          newNode <- updateAndDeleteStorage(decoded, key.tail, value)
//          encoded <- encode(newNode)
//        } yield bn.updated(key.head, encoded)
//      }
//
//    case n: KVNode =>
//      updateKVNode(n, key, value)
//  }
//
//  private def updateKVNode(node: KVNode, key: String, value: ByteVector): F[Node] = {
//
//    val curKey = node.key
//    val prefix = Key.longestCommonPrefix(curKey, key)
//    val prefixLength = prefix.length
//    val remainCurKey = curKey.slice(prefixLength, curKey.length)
//    val remainKey = key.slice(prefixLength, key.length)
//
//    val newNodeF: F[Node] = if (remainKey.isEmpty && remainCurKey.isEmpty) {
//      node match {
//        case LeafNode(_, _) =>
//          F.pure(LeafNode(key, value))
//        case ExtensionNode(_, child) =>
//          decode(child).flatMap(x => updateAndDeleteStorage(x, remainKey, value))
//      }
//    } else if (remainCurKey.isEmpty) {
//      node match {
//        case LeafNode(_, v) =>
//          encode(LeafNode(remainKey.tail, value)).map(e => BranchNode(Some(v)).updated(remainKey.head, e))
//        case ExtensionNode(_, child) =>
//          decode(child).flatMap(x => updateAndDeleteStorage(x, remainKey, value))
//      }
//    } else {
//      val (k1, v1) = node match {
//        case LeafNode(_, v)                                      => (remainCurKey.head, encode(LeafNode(remainCurKey.tail, v)))
//        case ExtensionNode(_, child) if remainCurKey.length == 1 => (remainCurKey.head, child)
//        case ExtensionNode(_, child)                             => (remainCurKey.head, encode(ExtensionNode(remainCurKey.tail, child)))
//      }
//
//      val newNode = {
//        val n = BranchNode.empty
//          .updated(k1, v1)
//
//        if (remainKey.isEmpty) n.mapValue(value)
//        else n.updated(remainKey.head, encode(LeafNode(remainKey.tail, value)))
//      }
//
//      F.pure(newNode)
//    }
//
//    for {
//      newNode <- newNodeF
//    } yield {
//      if (prefixLength == 0 || remainKey.isEmpty && remainCurKey.isEmpty && node.isLeaf && newNode.isLeaf) {
//        newNode
//      } else {
//        ExtensionNode(prefix, encode(newNode))
//      }
//    }
//  }
//
//  private def deleteBranchNode(node: BranchNode, key: String): F[Node] =
//    if (key.isEmpty) {
//      // key matches
//      normalizeBranchNode(node.copy(value = None))
//    } else {
//      for {
//        decoded <- decode(node.branchAt(key.head))
//        newSubNode <- deleteAndDeleteStorage(decoded, key.tail)
//        encoded <- encode(newSubNode)
//      } yield {
//        if (encoded == node.branchAt(key.head)) {
//          node
//        } else {
//          node.updated(key.head, encoded)
//        }
//      }
//    }
//
//  private def normalizeBranchNode(node: BranchNode): F[Node] = {
//    val nonBlankBranch = node.branches.zipWithIndex.find {
//      case (x, _) => !(x.isRight && x.right.get.isInstanceOf[BlankNode.type])
//    }
//
//    if (nonBlankBranch.isDefined && node.value.isDefined) {
//      F.pure(node)
//    } else if (node.value.isDefined) {
//      // the value item is not blank
//      F.pure(LeafNode("", node.value.get))
//    } else {
//      // normal item is not blank
//      val prefix: String = alphabet(nonBlankBranch.get._2)
//      for {
//        subNode <- decode(node.branches.head)
//      } yield
//        subNode match {
//          case LeafNode(key, value)      => LeafNode(prefix ++ key, value)
//          case ExtensionNode(key, entry) => ExtensionNode(prefix ++ key, entry)
//          case bn @ BranchNode(_, _)     => encode(bn).map(e => ExtensionNode(prefix, e))
//          case BlankNode                 => ???
//        }
//    }
//  }
//
//  private def deleteKVNode(node: KVNode, key: String): F[Node] = node match {
//    case _ if !key.startsWith(node.key) => F.pure(node) // key not found
//    case LeafNode(curKey, _)            => F.pure(if (key == curKey) BlankNode else node)
//    case ExtensionNode(curKey, child) =>
//      for {
//        decoded <- decode(child)
//        newSubNode <- deleteAndDeleteStorage(decoded, key.slice(curKey.length, key.length))
//        encoded <- encode(newSubNode)
//      } yield {
//        if (encoded == child) node
//        else {
//          newSubNode match {
//            case BlankNode           => BlankNode
//            case bn: BranchNode      => ExtensionNode(curKey, encoded)
//            case LeafNode(k, v)      => LeafNode(curKey ++ k, v)
//            case ExtensionNode(k, v) => ExtensionNode(curKey ++ k, v)
//          }
//        }
//      }
//  }
//
//  private def delete0(node: Node, key: String): F[Node] = node match {
//    case BlankNode      => F.pure(BlankNode)
//    case bn: BranchNode => deleteBranchNode(bn, key)
//    case ln: KVNode     => deleteKVNode(ln, key)
//  }
//
//  private def deleteAndDeleteStorage(node: Node, key: String): F[Node] =
//    for {
//      newNode <- delete0(node, key)
//      _ <- if (node != newNode) {
//        deleteNodeStorage(node)
//      } else F.unit
//    } yield newNode
//
//  private def deleteChildStorage(node: Node): F[Unit] = node match {
//    case BranchNode(branches, _) =>
//      branches.traverse(n => decode(n) >>= deleteChildStorage).map(_ => ())
//    case ExtensionNode(_, child) =>
//      decode(child) >>= deleteChildStorage
//    case _ => F.unit
//  }
//
//  private def deleteNodeStorage(node: Node): F[Unit] = node match {
//    case BlankNode   => F.unit
//    case _: LeafNode => F.unit
//    case _ =>
//      for {
//        encoded <- encode(node)
//        _ <- encoded match {
//          case Left(hash) => db.del(hash)
//          case _          => F.unit
//        }
//      } yield ()
//  }
//}
