import 'package:polymer/polymer.dart';
import 'whloot.dart';

/**
 * A Polymer click counter element.
 */
@CustomTag('loot-list')
class LootList extends PolymerElement with ChangeNotifier  {
  @reflectable @published int get opid => __$opid; int __$opid; @reflectable set opid(int value) { __$opid = notifyPropertyChange(#opid, __$opid, value); }
  
  List<Loot> loots = toObservable(new List<Loot>());
  
  LootList.created() : super.created() {
    if (opid == null) {
      apiRequest("assets", dataHandler);
    } else {
      apiRequest("assets/"+opid.toString(), dataHandler);
    }
  }
  
  void dataHandler(String responseText) {
    var data = JSON.decode(responseText) as List;
    var lootlist = data.map(Loot.fromJsonObj);
    loots.clear();
    loots.addAll(lootlist);
  }
  
}

