package com.larskroll.whloot.data

import java.util.Date
import anorm.SqlParser._
import anorm._

case class Loot(opId: Int, itemId: Long, name: String, quantity: Long)

object LootQueries {

	val tableName = "whloot_loot";

	val select: SqlQuery =
		SQL("""SELECT * FROM """ + tableName + """
				ORDER BY opID DESC""");

	val selectForOp: SqlQuery =
		SQL("""SELECT * FROM """ + tableName + """
				WHERE opID = {op}""");
	
	val checkId: SqlQuery = 
		SQL("""SELECT COUNT(*) FROM """ + tableName + """ WHERE opId = {id}""");
	
	val insert: SqlQuery = 
		SQL("""INSERT INTO """ + tableName + """
				VALUE ({itemId}, {opId}, {name}, {quantity})""");
	
	val clearOp: SqlQuery = SQL("""DELETE FROM """ + tableName + """
			WHERE opId = {op}""");
}

object LootParsers {
	val full = long("opID") ~
		long("itemID") ~
		(str("name") ?) ~
		long("quantity") map {
			case opid ~ iid ~ name ~ q => Loot(opid.toInt, iid, name.getOrElse("?"), q);
		}
}

object ItemTransformers {
	val rules = Map(34l -> List((15331l, 500), (30497l, 2500)));

	def transform(prices: Map[Long, (Double, Long)], loots: Map[Long, Long]): Map[Long, (Double, Long)] = {
		val newprices = scala.collection.mutable.Map.empty[Long, (Double, Long)];
		prices.foreach { p =>
			val item = p._1;
			val price = p._2._1;
			val quantity = p._2._2;
			val total = price*quantity;
			if (rules.contains(item)) {
				val trans = rules(item);
				val quantities = trans.map { pair =>
					val id = pair._1;
					val tritPerItem = pair._2;
					val quant = if (loots.contains(id)) { loots(id) } else { println("Loots doesn't contain " + id); 0 };
					val tritQuant = tritPerItem * quant;
					(id, tritQuant)
				}
				val sum = quantities.foldLeft(0l)(_ + _._2);
				val value = sum.toDouble*price;
				if (sum == 0) {
					//println("Sum is zero: " + quantities.toString);
					quantities.foreach { q =>
						newprices += (q._1 -> (0.0, 0));
					}
				} else {
					//println("Sum is " + sum + ": " + quantities.toString);
					quantities.foreach { q =>
						val id = q._1;
						val quant = q._2.toDouble;
						val s = sum.toDouble;
						val lootQuant = loots(id);
						val itemPrice = if (loots.contains(id)) { (total * (quant/s))/ lootQuant} else { 0.0 }; 
						newprices += (q._1 -> (itemPrice, lootQuant));
					}
				}
			} else {
				newprices += (p._1 -> p._2);
			}
		};
		return newprices.toMap;
	}
}