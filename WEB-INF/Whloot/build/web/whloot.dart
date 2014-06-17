library whloot;

import 'dart:html';
import 'dart:convert';
export 'dart:html';
export 'dart:convert';

final String apiUrl = "http://127.0.0.1:8012/";

typedef void DataLoadHandler(String responseText);

void apiRequest(String url, DataLoadHandler handler) {
  HttpRequest.getString(apiUrl+url).then(handler);
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