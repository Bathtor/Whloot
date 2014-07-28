import 'package:polymer/polymer.dart';
import 'whloot.dart';

@CustomTag('menu-bar')
class MenuBar extends PolymerElement with ChangeNotifier  {
  @reflectable @observable
  String get ticker => __$ticker; String __$ticker = "[AAASC]"; @reflectable set ticker(String value) { __$ticker = notifyPropertyChange(#ticker, __$ticker, value); }

  @reflectable @observable
  String get currentPage => __$currentPage; String __$currentPage; @reflectable set currentPage(String value) { __$currentPage = notifyPropertyChange(#currentPage, __$currentPage, value); }

  @observable
  final Map<String, String> pages = {
    'whloot': 'Home',
    'ops': 'Ops',
    'market': 'Market'
  };

  MenuBar.created(): super.created() {
    String curUrl = window.location.pathname;
    List<String> splitUrl = curUrl.split("/");
    List<String> splitEnd = splitUrl[splitUrl.length - 1].split(".");
    currentPage = splitEnd[0];
    print(currentPage);
  }
}
