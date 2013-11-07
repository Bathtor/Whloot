package com.larskroll.whloot

import akka.actor.ActorLogging
import akka.actor.Actor
import scala.concurrent.duration.DurationInt
import com.beimin.eveapi.corporation.assetlist.AssetListParser
import com.beimin.eveapi.core.ApiAuthorization
import com.beimin.eveapi.shared.assetlist.EveAsset
import com.beimin.eveapi.character.wallet.transactions.WalletTransactionsParser
import com.beimin.eveapi.shared.wallet.transactions.WalletTransactionsResponse
import com.beimin.eveapi.shared.assetlist.AssetListResponse
import com.beimin.eveapi.shared.wallet.transactions.ApiWalletTransaction
import scalikejdbc.NamedDB
import akka.util.Timeout
import com.larskroll.whloot.data.TransactionQueries
import java.util.Date
import akka.actor.Status.Success
import com.larskroll.whloot.data.TransactionParsers
import com.beimin.eveapi.core.ApiError
import scala.language.existentials
import com.larskroll.whloot.data.Loot
import com.larskroll.whloot.data.Ops
import org.joda.time.DateTime
import com.larskroll.whloot.data.LootQueries
import anorm.SqlParser._
import anorm._
import com.larskroll.whloot.data.EVEStaticQueries
import com.larskroll.whloot.data.LootParsers

// Request
trait ApiRequest
case object GetAssets extends ApiRequest;
case object GetTransactions extends ApiRequest;
case object ClearAssets extends ApiRequest;
// Response
trait ApiResponse
case class PullFailed(error: ApiError) extends ApiResponse;
case class StillCached(until: Date) extends ApiResponse;
case class Pulled(txs: Int, sells: Int) extends ApiResponse;
case class Diff(added: Set[Loot], removed: Set[Loot]) extends ApiResponse;
case class DiffFailed(msg: String) extends ApiResponse;
case object Cleared extends ApiResponse;

class APIService extends Actor with ActorLogging {
	import context._
	import scala.collection.JavaConversions._

	implicit val timeout = Timeout(5 seconds);

	val conf = Main.system.settings.config;
	val marketAuth = new ApiAuthorization(conf.getInt("whloot.api.market.key"),
		conf.getString("whloot.api.market.vCode"));
	val corpAuth = new ApiAuthorization(conf.getInt("whloot.api.corp.key"),
		conf.getString("whloot.api.corp.vCode"));
	val corpHangar = conf.getLong("whloot.hangars.cha");
	val hangarDivision = conf.getInt("whloot.hangars.division");

	private var assetsCached: AssetListResponse = null;
	private var transactionsCached: WalletTransactionsResponse = null;

	def receive = {
		case ClearAssets => {
			NamedDB('mysql) localTxWithConnection { implicit conn =>
				LootQueries.clearOp.on("op" -> Ops.ZERO).executeUpdate();
				sender ! Cleared;
			}
		}
		case GetAssets => {
			if ((assetsCached != null) && assetsCached.getCachedUntil().after(new Date())) {
				sender ! StillCached(assetsCached.getCachedUntil());
			} else {
				val parser = AssetListParser.getInstance();
				assetsCached = parser.getResponse(corpAuth);
				if (assetsCached.hasError()) {
					sender ! PullFailed(assetsCached.getError());
				} else {
					val assets: scala.collection.mutable.Set[EveAsset[EveAsset[_]]] = assetsCached.getAll();
					//printAssets(assets.toSet);
					val container = assets.find(asset => asset.getItemID() == corpHangar);
					container match {
						case Some(cha) => {
							val chaAssets: Iterable[EveAsset[_]] = cha.getAssets();
							val chaDiv = chaAssets.filter(asset => asset.getFlag() == hangarDivision);
							val (added, removed) = calcDiff(chaDiv);
							sender ! Diff(added, removed);
						}
						case None => sender ! DiffFailed(s"Could not find CHA with id = ${corpHangar}");
					}
				}
			}
		}
		case GetTransactions => {
			if ((transactionsCached != null) && transactionsCached.getCachedUntil().after(new Date())) {
				sender ! StillCached(transactionsCached.getCachedUntil());
			} else {
				val parser = WalletTransactionsParser.getInstance();
				transactionsCached = parser.getTransactionsResponse(marketAuth);
				if (transactionsCached.hasError()) {
					sender ! PullFailed(transactionsCached.getError());
				} else {
					println(transactionsCached.getAll().size());
					val newTransactions: scala.collection.mutable.Set[ApiWalletTransaction] = transactionsCached.getAll();
					val newSells = newTransactions.filter(_.getTransactionType() == "sell");
					// drop in DB
					NamedDB('mysql) localTxWithConnection { implicit conn =>
						newSells.foreach(t => {
							TransactionQueries.insert.on(
								"id" -> t.getTransactionID(),
								"ts" -> t.getTransactionDateTime(),
								"item" -> t.getTypeID(),
								"quantity" -> t.getQuantity(),
								"price" -> t.getPrice()).executeUpdate();
						});
					}
					sender ! Pulled(newTransactions.size, newSells.size);
				}
			}
		}
	}
	//
	//	private def getLastID(): Long = {
	//		NamedDB('mysql) localTxWithConnection { implicit conn =>
	//			val res = TransactionQueries.selectLast.parse(TransactionParsers.full.singleOpt);
	//			res match {
	//				case None => 1000;
	//				case Some(t) => t.id;
	//			}
	//		}
	//	}

	private def printAssets(list: Set[EveAsset[EveAsset[_]]]) {
		list foreach (a => {
			print(s"Asset(Item: ${a.getItemID()}, Type: ${a.getTypeID()}, Location: ${a.getLocationID()}, Flag: ${a.getFlag()}, Unpackaged? ${a.getSingleton()}, Quantity: ${a.getQuantity()}");
			if (!a.getAssets().isEmpty()) {
				val assets: Iterable[EveAsset[_]] = a.getAssets();
				val assetset: Set[EveAsset[EveAsset[_]]] = assets.map(ea => ea.asInstanceOf[EveAsset[EveAsset[_]]]).toSet;
				print("Assets: \n");
				printAssets(assetset);
			}
			println(")");
		});
	}

	private def calcDiff(assets: Iterable[EveAsset[_]]): Pair[Set[Loot], Set[Loot]] = {
		val opId = findOpId();
		val added = scala.collection.mutable.Set.empty[Loot];
		val removed = scala.collection.mutable.Set.empty[Loot];
		NamedDB('mysql) localTxWithConnection { implicit conn =>
			val newAssets = assets.flatMap(asset => {
				val name = EVEStaticQueries.itemNameForId.on("id" -> asset.getTypeID()).as(scalar[String].singleOpt);
				name match {
					case Some(s) => Some(Loot(Ops.ZERO, asset.getTypeID(), s, asset.getQuantity()))
					case None => println(s"Could not find item with id=${asset.getTypeID()}"); None
				}
			});
			val newAssetMap = newAssets.map(asset => (asset.itemId -> asset)).toMap;
			val oldAssets = LootQueries.selectForOp.on("op" -> Ops.ZERO).as(LootParsers.full *);
			val oldAssetMap = oldAssets.map(asset => (asset.itemId -> asset)).toMap;
			val diffAssets = newAssetMap map {
				case (id, asset) => {
					if (oldAssetMap contains id) {
						val oldAsset = oldAssetMap(id);
						val diffAsset = Loot(opId, id, asset.name, asset.quantity - oldAsset.quantity);
						if (diffAsset.quantity > 0) {
							added += diffAsset;
						} else if (diffAsset.quantity < 0) {
							removed += diffAsset;
						}
						diffAsset
					} else {
						val diffAsset = Loot(opId, id, asset.name, asset.quantity);
						added += diffAsset;
						diffAsset
					}
				}
			};
			oldAssetMap foreach {
				case (id, asset) => {
					if (!(newAssetMap contains id)) {
						removed += asset;
					}
				}
			};
			// clear old state
			LootQueries.clearOp.on("op" -> Ops.ZERO).executeUpdate();
			// add new state
			newAssets foreach (asset => {
				LootQueries.insert.on("itemId" -> asset.itemId,
					"opId" -> asset.opId,
					"name" -> asset.name,
					"quantity" -> asset.quantity).executeUpdate();
			});
			// add new op
			diffAssets foreach (asset => {
				LootQueries.insert.on("itemId" -> asset.itemId,
					"opId" -> asset.opId,
					"name" -> asset.name,
					"quantity" -> asset.quantity).executeUpdate();
			});
		}
		return (added.toSet, removed.toSet);
	}

	private def findOpId(): Int = {
		var id = Ops.dateAsId(new DateTime());
		NamedDB('mysql) localTxWithConnection { implicit conn =>
			var numEntries = -1l;
			while (numEntries != 0) {
				numEntries = LootQueries.checkId.on("id" -> id).as(scalar[Long].single);
				id += 1;
			}
			id -= 1;
		}
		return id;
	}
}