package com.larskroll.whloot.data

import anorm.SqlParser._
import anorm._

object EVEStatic {

}

object EVEStaticQueries {
	val itemNameForId: SqlQuery = 
		SQL("""SELECT typeName FROM invTypes WHERE typeID = {id}""");
}