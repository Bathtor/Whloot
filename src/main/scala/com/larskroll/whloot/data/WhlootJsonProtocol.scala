package com.larskroll.whloot.data

import spray.json._
import com.larskroll.whloot.Payout

object WhlootJsonProtocol extends DefaultJsonProtocol {
	import DefaultJsonProtocol._
	
	//implicit val memberFormat = jsonFormat2(Member);
	implicit object MemberFormat extends JsonFormat[Member] {
		def write(m: Member) = JsString(m.name);
		def read(value: JsValue) = Member(-1, value.toString);
	}
	implicit val lootFormat = jsonFormat4(Loot);
	implicit val opFormat = jsonFormat2(OpHeader);
	implicit val payoutFormat = jsonFormat7(Payout);
}