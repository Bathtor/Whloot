import 'package:polymer/polymer.dart';

/**
 * A Polymer click counter element.
 */
@CustomTag('click-counter')
class ClickCounter extends PolymerElement with ChangeNotifier  {
  @reflectable @published int get count => __$count; int __$count = 0; @reflectable set count(int value) { __$count = notifyPropertyChange(#count, __$count, value); }

  ClickCounter.created() : super.created() {
  }

  void increment() {
    count++;
  }
}

