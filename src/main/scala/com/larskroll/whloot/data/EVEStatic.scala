package com.larskroll.whloot.data

import anorm.SqlParser._
import anorm._
import scalikejdbc.NamedDB

object EVEStatic {
	def station2systemId(stationId: Int): Option[Int] = {
	    NamedDB('mysql) localTxWithConnection {implicit conn =>
            EVEStaticQueries.solarSystemForStation.on("id" -> stationId).as(scalar[Int].singleOpt);            
        }
	}
}

object EVEStaticQueries {
	val itemNameForId: SqlQuery = 
		SQL("""SELECT typeName FROM invTypes WHERE typeID = {id}""");
	val solarSystemForStation: SqlQuery = 
	    SQL("""SELECT solarSystemID FROM staStations WHERE stationID = {id}""");
}