package com.larskroll.whloot

import akka.actor.ActorLogging
import akka.actor.Actor
import scala.concurrent.duration.DurationInt
import scalikejdbc.NamedDB
import akka.util.Timeout
import java.util.Date
import com.larskroll.whloot.data._
import org.joda.time.Interval
import org.joda.time.Instant
import org.joda.time.DateTime
import org.joda.time.Days
import akka.actor.ActorContext

case class CalcPayout(sinceOp: Int);
case class CalcPayoutRange(startOp: Int, endOp: Int, tidBound: Long);
case class Payout(totalIncome: Double, fuelDays: Int, fuelPart: Double, masterPart: Double, srpPart: Double, distributableIncome: Double, perMember: Map[Member, Double])
case class ListAssets(id: Int);
case class AssetList(loots: List[Loot]);
case object ListOps;
case class OpList(ops: List[OpHeader]);

class OpService extends Actor with ActorLogging {
	import context._

	val fuelCostPerDay = 14.0 * 1000 * 1000; // in ISK
	val masterWallet = 5.0 / 100;
	val srpWallet = 20.0 / 100;

	implicit val timeout = Timeout(5 seconds);

	def receive = {
		case ListAssets(id) => {
			NamedDB('mysql) localTxWithConnection {implicit conn =>
				val loots = LootQueries.selectForOp.on("op" -> id).parse(LootParsers.full *);
				sender ! AssetList(loots);
			}
		}
		case ListOps => {
			NamedDB('mysql) localTxWithConnection {implicit conn =>
				val res = OpQueries.selectOps.parse(OpParsers.op *);
				val ops = OpParsers.opGrouper(res) map { o =>
					OpHeader(o.id, o.participants);
				};
				sender ! OpList(ops);
			}
		}
		case CalcPayoutRange(startOp, endOp, tidBound) => {
			println(s"### Pulling for ($startOp, $endOp) ###");
			NamedDB('mysql) localTxWithConnection { implicit conn =>
				val res = OpQueries.selectOpsBetween.on("start" -> startOp, "end" -> endOp).parse(OpParsers.op *);
				println("######## Temp Ops #######\n 	" + res.toString);
				val ops = OpParsers.opGrouper(res) map { o =>
					val loots = LootQueries.selectForOp.on("op" -> o.id).parse(LootParsers.full *);
					Op(o.id, o.participants, loots.toSet);
				};
				println("######## Ops #######\n 	" + ops.toString);
				val transDate = if (endOp == Int.MaxValue) startOp else endOp
				val trans = TransactionQueries.selectSince.on("date" -> Ops.idAsDate(transDate).toString(), "tid" -> tidBound).parse(TransactionParsers.full *);
				println("######## Transactions #######\n 	" + trans.toString);
				val sumTrans = trans.foldLeft(0.0) {
					case (sum, t) => sum + (t.price * t.quantity)
				}
				println(f"######## Transactions Sum: ${sumTrans}%-,10.2f ISK #######\n 	");
				val itemPrices = pricePerItem(trans);
				println("######## Prices #######\n 	" + itemPrices.toString);
				val sumPrices = itemPrices.foldLeft(0.0) {
					case (sum, pair) => sum + (pair._2._1 * pair._2._2);
				}
				println(f"######## Prices Sum: ${sumPrices}%-,10.2f ISK #######\n 	");
				val itemNumbers = sumOfLoots(ops);
				println("######## Loots #######\n 	" + itemNumbers.toString);
				val lootPrices = ItemTransformers.transform(itemPrices, itemNumbers);
				checkNums(itemNumbers, lootPrices);
				println("######## Loot Prices #######\n 	" + lootPrices.toString);
				val sumLootPrices = lootPrices.foldLeft(0.0) {
					case (sum, pair) => sum + (pair._2._1 * pair._2._2);
				}
				println(f"######## Loot Prices Sum: ${sumLootPrices}%-,10.2f ISK #######\n 	");
				val (totalIncome, lootPerPerson) = participationPercentage(ops, lootPrices);
				println(f"######## Loot Per Person: ${lootPerPerson} %% #######\n 	");
				val daysSincePayout = Days.daysBetween(new DateTime(Ops.idAsDate(startOp)), new Instant()).getDays();
				val fuelPart = daysSincePayout * fuelCostPerDay;
				val reducedIncome = totalIncome - fuelPart;
				val masterPart = reducedIncome * masterWallet;
				val srpPart = reducedIncome * srpWallet;
				val distributableIncome = reducedIncome - masterPart - srpPart;
				val incomePerMember = lootPerPerson.map {
					case (member, perc) =>
						(member -> (perc * distributableIncome))
				}
				sender ! Payout(totalIncome, daysSincePayout, fuelPart, masterPart, srpPart, distributableIncome, incomePerMember);
			}
		}
	}
	
	private def checkNums(itemNumbers: Map[Long, Long], lootPrices: Map[Long, (Double, Long)]): Unit = {
		lootPrices.foreach {
			case (item, (price, num)) => {
				if (itemNumbers.contains(item)) {
					val iNum = itemNumbers(item);
					if (iNum != num) {
						println("Something doesn't fit: " + item + " iN: " + iNum + " lP: " + num);
					}
				} else {
					println("Something doesn't fit: " + item + " does not exists in loot");
				}
			}
		}
		itemNumbers.foreach {
			case (item, iNum) => {
				if (lootPrices.contains(item)) {
					val num = lootPrices(item)._2;
					if (iNum != num) {
						println("Something doesn't fit: " + item + " iN: " + iNum + " lP: " + num);
					}
				} else {
					println("Something doesn't fit: " + item + " does not exists in transactions");
				}
			}
		}
	}

	private def participationPercentage(ops: List[Op], prices: Map[Long, (Double, Long)]): (Double, Map[Member, Double]) = {
		val totalIncome = prices.foldLeft(0.0) {
			case (sum, pair) => sum + (pair._2._1 * pair._2._2);
		}
		val perMember = scala.collection.mutable.Map.empty[Member, Double];
		// Initialise with zero
		Members.members.values.foreach { m =>
			perMember += (m -> 0.0)
		}
		ops.foreach { op =>
			val opIncome = sumLootNumbers(sumOfLoots(List(op)), prices);
			val perParticipant = opIncome / op.participants.size;
			op.participants.foreach { part =>
				perMember += (part -> (perMember(part) + perParticipant));
			}
		}
		val perMemberPerc = perMember.toMap.map {
			case (member, money) => (member, money / totalIncome)
		}
		(totalIncome, perMemberPerc)
	}

	private def sumLootNumbers(numbers: Map[Long, Long], prices: Map[Long, (Double, Long)]): Double = numbers.foldLeft(0.0) {
		case (sum, itemNum) => sum + (itemNum._2 * prices(itemNum._1)._1);
	}

	private def sumOfLoots(ops: List[Op]): Map[Long, Long] = {
		val loots = ops.foldLeft(List.empty[Loot])(_ ++ _.loots);
		val res = loots.groupBy(_.itemId).map {
			case (itemId, lootItems) => {
				val sum = lootItems.foldLeft(0l)(_ + _.quantity);
				(itemId, sum)
			}
		};
		return res;
	}

	private def pricePerItem(trans: List[Transaction]): Map[Long, (Double, Long)] = {
		val byItemID = trans map { t => (t.itemId, t) };
		val res = byItemID.groupBy(_._1).map {
			case (itemID, pairs) => {
				val transactions = pairs.map(_._2);
				val quantity = transactions.foldLeft(0l)(_ + _.quantity);
				val price = transactions.foldLeft(0.0)((sum, t) => sum + (t.price * t.quantity.toDouble));
				val avgPrice = price / quantity.toDouble;
				(itemID, (avgPrice, quantity))
			}
		};
		return res;
	}
}