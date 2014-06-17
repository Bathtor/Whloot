import 'package:polymer/polymer.dart';
import 'whloot.dart';

/**
 * A Polymer click counter element.
 */
@CustomTag('op-list')
class OpList extends PolymerElement {
  
  Map<int, bool> opOpen = toObservable(new Map<int, bool>());
  List<Op> ops = toObservable(new List<Op>());
  
  OpList.created() : super.created() {
      apiRequest("ops", dataHandler);
  }
  
  void dataHandler(String responseText) {
    var data = JSON.decode(responseText) as List;
    var oplist = data.map(Op.fromJsonObj);
    ops.clear();
    ops.addAll(oplist);
    //opOpen.addAll(ops.map((o) => (o, true));
  }
  
}