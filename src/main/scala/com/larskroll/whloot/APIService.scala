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

// Request
trait ApiRequest
case object GetAssets extends ApiRequest;
case object GetTransactions extends ApiRequest;
// Response
trait ApiResponse
case class PullFailed(error: ApiError) extends ApiResponse;
case class StillCached(until: Date) extends ApiResponse;
case class Pulled(txs: Int, sells: Int) extends ApiResponse

class APIService extends Actor with ActorLogging {
	import context._
	import scala.collection.JavaConversions._

	implicit val timeout = Timeout(5 seconds);

	val conf = Main.system.settings.config;
	val marketAuth = new ApiAuthorization(conf.getInt("whloot.api.market.key"),
		conf.getString("whloot.api.market.vCode"));
	val corpAuth = new ApiAuthorization(conf.getInt("whloot.api.corp.key"),
		conf.getString("whloot.api.corp.vCode"));

	private var assetsCached: AssetListResponse = null;
	private var transactionsCached: WalletTransactionsResponse = null;

	def receive = {
		case GetAssets => {
			val parser = AssetListParser.getInstance();
			assetsCached = parser.getResponse(corpAuth);
			val assets: scala.collection.mutable.Set[EveAsset[EveAsset[_]]] = assetsCached.getAll();
			//TODO keep doing stuff
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
}