library whloot;

import 'dart:html';
import 'dart:convert';
import 'package:xml/xml.dart';
import 'package:intl/intl.dart';
export 'dart:html';
export 'dart:convert';


final String apiUrl = "http://127.0.0.1:8012/";
final String eveCentral = "http://api.eve-central.com/api/";
final oCcy = new NumberFormat("#,##0.00", "en_US");

typedef void DataLoadHandler(String responseText);

void apiRequest(String url, DataLoadHandler handler) {
  HttpRequest.getString(apiUrl+url).then(handler);
}
void marketRequest(String url, DataLoadHandler handler) {
  HttpRequest.getString(eveCentral+url).then(handler);
}

class Loot {
  final int opId;
  final int itemId;
  final String name;
  final int quantity;
  Loot(this.opId, this.itemId, this.name, this.quantity);
  Loot.fromJson(Map json) : this(json['opId'], json['itemId'], json['name'], json['quantity']);
  static fromJsonObj(Map json) => new Loot.fromJson(json);
  @override
  String toString() => "Loot($opId, $itemId, $name, $quantity)";
}

class Member {
  final String name;
  Member(this.name);
  Member.fromJson(String json) : this(json);
  static fromJsonObj(String json) => new Member.fromJson(json);
  @override
  String toString() => "Member($name)";
}

class Op {
  final int id;
  List<Member> participants = new List<Member>();
  Op(this.id, this.participants);
  Op.fromJson(Map json) : id = json['id'] {
    var members = json['participants'].map(Member.fromJsonObj);
    participants.addAll(members);
  }
  static fromJsonObj(Map json) => new Op.fromJson(json);
}

class Order {
  int id;
  int stationId;
  String stationName;
  String price;
  int volume;
  
  Order.fromXml(XmlElement xml) {
    id = int.parse(xml.attributes.first.value);
    stationName = xml.findElements('station_name').first.text;
    stationId = int.parse(xml.findElements('station').first.text);
    String priceStr = xml.findElements('price').first.text;
    double priceD = double.parse(priceStr);
    price = oCcy.format(priceD);
    volume =  int.parse(xml.findElements('vol_remain').first.text);
  }
  
  static fromXmlObj(XmlElement xml) => new Order.fromXml(xml);
}