import 'package:polymer/polymer.dart';
import 'whloot.dart';

/**
 * A Polymer click counter element.
 */
@CustomTag('menu-bar')
class MenuBar extends PolymerElement {
  @observable String ticker = "[AAASC]";
  
  @observable String currentPage;
  
  @observable final Map<String, String> pages = {
             'whloot' : 'Home',
             'ops'   : 'Ops'
  };
  
  MenuBar.created() : super.created() {
    String curUrl = window.location.pathname;
    List<String> splitUrl = curUrl.split("/");
    List<String> splitEnd = splitUrl[splitUrl.length-1].split(".");
    currentPage = splitEnd[0];
    print(currentPage);
  }
}