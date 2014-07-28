import 'package:polymer/polymer.dart';
import 'package:xml/xml.dart';
import 'whloot.dart';

@CustomTag('quick-look')
class QuickLook extends PolymerElement {

  @observable String system = "";
  @observable String item = "";

  @observable List<ObsOrder> sellOrders = toObservable(new List<ObsOrder>());
  @observable List<ObsOrder> buyOrders = toObservable(new List<ObsOrder>());

  @observable bool asc = true;

  QuickLook.created() : super.created() {
  }

  void submit(Event e, var detail, Node target) {
    marketRequest("quicklook?typeid=" + item, dataHandler);
  }

  void sortSell(Event e, var detail, Node target) {
    sortCollection(sellOrders, target.text);
  }

  void sortBuy(Event e, var detail, Node target) {
    sortCollection(buyOrders, target.text);
  }

  void sortCollection(List<ObsOrder> coll, String field) {
    asc = !asc; // flip on every call
    switch (field) {
      case "System":
        if (asc) {
          coll.sort((a, b) => a.stationName.compareTo(b.stationName));
        } else {
          coll.sort((a, b) => b.stationName.compareTo(a.stationName));
        }
        break;
      case "Price":
        if (asc) {
          coll.sort((a, b) => a.price.compareTo(b.price));
        } else {
          coll.sort((a, b) => b.price.compareTo(a.price));
        }
        break;
      case "Qty":
        if (asc) {
          coll.sort((a, b) => a.volume.compareTo(b.volume));
        } else {
          coll.sort((a, b) => b.volume.compareTo(a.volume));
        }
        break;
      case "Jumps":
        if (asc) {
          coll.sort((a, b) => a.jumps.compareTo(b.jumps));
        } else {
          coll.sort((a, b) => b.jumps.compareTo(a.jumps));
        }
        break;
    }
  }

  void dataHandler(String responseText) {
    print("Parsing...");
    var data = parse(responseText);
    print("done!");
    var sell = data.findAllElements('sell_orders').first;
    var buy = data.findAllElements('buy_orders').first;
    var sO = sell.findElements('order').map(ObsOrder.fromXmlObj);
    var bO = buy.findElements('order').map(ObsOrder.fromXmlObj);
    // add first so there is something to see
    sellOrders.clear();
    sellOrders.addAll(sO);
    buyOrders.clear();
    buyOrders.addAll(bO);
    // get all the jumps
    sellOrders.forEach(getJumps);
    buyOrders.forEach(getJumps);
  }

  void getJumps(ObsOrder o) {
    apiRequest("path/from/$system/toStation/${o.stationId}", (responseText) {
      var data = JSON.decode(responseText);
      if (data.containsKey('length')) {
        o.jumps = data['length'] - 1;
      }
    });
  }
}

class ObsOrder extends Order with Observable {

  @observable int jumps = -1;

  ObsOrder.fromXml(XmlElement xml) : super.fromXml(xml) {

  }

  static fromXmlObj(XmlElement xml) => new ObsOrder.fromXml(xml);
}
