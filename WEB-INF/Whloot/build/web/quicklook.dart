import 'package:polymer/polymer.dart';
import 'whloot.dart';

@CustomTag('quick-look')
class QuickLook extends PolymerElement with ChangeNotifier  {

  @reflectable @observable String get system => __$system; String __$system = "somesys"; @reflectable set system(String value) { __$system = notifyPropertyChange(#system, __$system, value); }
  @reflectable @observable String get item => __$item; String __$item = "someitem"; @reflectable set item(String value) { __$item = notifyPropertyChange(#item, __$item, value); }

  QuickLook.created(): super.created() {
  }
}
