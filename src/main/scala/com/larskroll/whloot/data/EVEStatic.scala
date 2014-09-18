package com.larskroll.whloot.data

import anorm.SqlParser._
import anorm._
import scalikejdbc.NamedDB
import java.math.BigDecimal
import com.twitter.util.LruMap

case class ItemType(itemID: Long, itemName: String)

object EVEStatic {

    val recentItems = new LruMap[Long, ItemType](50);
    val topItems = new LruMap[Long, ItemType](5);

    def station2systemId(stationId: Int): Option[Int] = {
        NamedDB('mysql) localTxWithConnection { implicit conn =>
            EVEStaticQueries.solarSystemForStation.on("id" -> stationId).as(scalar[Int].singleOpt);
        }
    }
    def searchMarketItem(pattern: String): List[ItemType] = {
        NamedDB('mysql) localTxWithConnection { implicit conn =>
            val items = EVEStaticQueries.searchMarketItem.on("pattern" -> s"%$pattern%").parse(ItemTypeParsers.parser *);
            items.foreach(item => recentItems += (item.itemID -> item));
            return items;
        }
    }
    def rateMarketItem(itemID: Long) {
        NamedDB('mysql) localTxWithConnection { implicit conn =>
            EVEStaticQueries.incMarketPopularity.on("id" -> itemID).execute
            val it = recentItems.get(itemID);
            it.foreach((item) => {
                topItems += (itemID -> item);
            });

            // TODO look up itemID and add anyway
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