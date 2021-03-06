package com.larskroll.whloot.data

import java.util.Date
import anorm.SqlParser._
import anorm._
import org.joda.time.DateTime

case class Member(id: Int, name: String)
case class Op(id: Int, participants: Set[Member], loots: Set[Loot]) {
	def idAsDate() = Ops.idAsDate(id);
	def header = OpHeader(id, participants);
}
case class OpHeader(id: Int, participants: Set[Member])

object Ops {
	val ZERO = 0;

	def idAsDate(id: Int): DateTime = {
		val idStr = id.toString;
		val year = idStr.subSequence(0, 4).toString();
		val month = idStr.subSequence(4, 6).toString();
		val day = idStr.subSequence(6, 8).toString();
		val disamb = idStr.subSequence(8, 9).toString();
		new DateTime(year.toInt, month.toInt, day.toInt, 0, 0);
	}
	def dateAsId(date: DateTime): Int = {
		return date.year().get() * 100000 + date.monthOfYear().get() * 1000 + date.dayOfMonth().get() * 10;
	}
}

object Members {
	import scalikejdbc.NamedDB

	private var _members = Map.empty[Int, Member];

	def members = {
		if (_members.isEmpty) {
			load();
		}
		_members;
	}

	def load() = {
		NamedDB('mysql) localTxWithConnection { implicit conn =>
			val res = OpQueries.selectMembers.parse(OpParsers.member *);
			_members = res map { m => (m.id, m) } toMap;
		}
	}
}

object OpQueries {

	val opTable = "whloot_participation";
	val memberTable = "whloot_members";

	val selectMembers: SqlQuery =
		SQL("""SELECT * FROM """ + memberTable);

	val selectOpsSince: SqlQuery =
		SQL("""SELECT * FROM """ + opTable + """
				WHERE opID >= {since}""");
	
	val selectOpsBetween: SqlQuery =
		SQL("""SELECT * FROM """ + opTable + """
				WHERE opID >= {start} AND opID <= {end}""");
	
	val selectOps: SqlQuery =
		SQL("""SELECT * FROM """ + opTable);
}

object OpParsers {
	val member = long("memberID") ~ str("name") map {
		case id ~ name => Member(id.toInt, name)
	}

	val op = long("opID") ~ long("memberID") map {
		case id ~ member => (id.toInt, Members.members(member.toInt));
	}

	def opGrouper(list: List[Pair[Int, Member]]): List[Op] = {
		val res = list.groupBy(_._1).map {
			case (id, members) => Op(id, members.map(_._2).toSet, Set.empty[Loot])
		}
		res.toList;
	}
}