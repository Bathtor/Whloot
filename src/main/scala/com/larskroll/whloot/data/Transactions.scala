package com.larskroll.whloot.data

import java.util.Date
import anorm.SqlParser._
import anorm._
import java.math.BigDecimal

case class Transaction(id: Long, ts: Date, itemId: Long, quantity: Long, price: Double)

object TransactionQueries {
	val tableName = "whloot_transactions";
	
	val insert: SqlQuery = 
		SQL("""INSERT INTO """ + tableName + """
				(transactionID, transactionTS, itemID, quantity, price) VALUES
				({id}, {ts}, {item}, {quantity}, {price})""");
	val selectLast: SqlQuery = 
		SQL("""SELECT * FROM """ + tableName + """ 
				ORDER BY transactionID DESC LIMIT 1""");
	val selectSince: SqlQuery = 
		SQL("""SELECT * FROM """ + tableName + """ 
				WHERE transactionTS > {date}""");
}

object TransactionParsers {
	val full = long("transactionID") ~ 
	date("transactionTS") ~ 
	long("itemID") ~
	long("quantity") ~
	get[BigDecimal]("price") map {
		case tid ~ ts ~ iid ~ q ~ p => Transaction(tid, ts, iid, q, p.doubleValue());
	}
}