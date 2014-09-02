package com.larskroll.whloot.data

import anorm.SqlParser._
import anorm._
import scalikejdbc.NamedDB
import java.math.BigDecimal

case class ItemType(itemID: Long, itemName: String)

object EVEStatic {
	def station2systemId(stationId: Int): Option[Int] = {
	    NamedDB('mysql) localTxWithConnection {implicit conn =>
            EVEStaticQueries.solarSystemForStation.on("id" -> stationId).as(scalar[Int].singleOpt);            
        }
	}
	def searchMarketItem(pattern: String): List[ItemType] = {
	    NamedDB('mysql) localTxWithConnection {implicit conn =>
            EVEStaticQueries.searchMarketItem.on("pattern" -> s"%$pattern%").parse(ItemTypeParsers.parser *);          
        }
	}
	def rateMarketItem(itemID: Long) {
	    NamedDB('mysql) localTxWithConnection {implicit conn =>
	        EVEStaticQueries.incMarketPopularity.on("id" -> itemID).execute
	    }
	}
}

object EVEStaticQueries {
	val itemNameForId: SqlQuery = 
		SQL("""SELECT typeName FROM invTypes WHERE typeID = {id}""");
	val solarSystemForStation: SqlQuery = 
	    SQL("""SELECT solarSystemID FROM staStations WHERE stationID = {id}""");
	val searchMarketItem: SqlQuery = 
	    SQL("""SELECT t.typeName, t.typeID, COALESCE(p.`popularity`, 0) as popu 
	            FROM invTypes t LEFT JOIN whloot_item_popularity p 
	            ON t.typeID = p.itemID 
	            WHERE typeName LIKE {pattern} AND marketGroupID IS NOT NULL 
	            ORDER BY popu DESC""");
	val incMarketPopularity: SqlQuery =
	    SQL("""INSERT INTO whloot_item_popularity (itemID, popularity) 
	            VALUES ({id}, 1) 
	            ON DUPLICATE KEY UPDATE popularity=popularity+1""");
}

object ItemTypeParsers {
    val parser = long("typeID") ~
    (str("typeName") ?) ~
    get[BigDecimal]("popu") map {
        case id ~ name ~ popu => ItemType(id, name.getOrElse("?"));
    }
}