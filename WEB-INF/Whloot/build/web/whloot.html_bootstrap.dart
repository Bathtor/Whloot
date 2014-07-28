library app_bootstrap;

import 'package:polymer/polymer.dart';

import 'menubar.dart' as i0;
import 'whloot.html.0.dart' as i1;
import 'package:smoke/smoke.dart' show Declaration, PROPERTY, METHOD;
import 'package:smoke/static.dart' show useGeneratedCode, StaticConfiguration;
import 'menubar.dart' as smoke_0;
import 'package:polymer/polymer.dart' as smoke_1;
import 'package:observe/src/metadata.dart' as smoke_2;
abstract class _M0 {} // PolymerElement & ChangeNotifier

void main() {
  useGeneratedCode(new StaticConfiguration(
      checkedMode: false,
      getters: {
        #currentPage: (o) => o.currentPage,
        #keys: (o) => o.keys,
        #page: (o) => o.page,
        #pages: (o) => o.pages,
        #ticker: (o) => o.ticker,
      },
      setters: {
        #currentPage: (o, v) { o.currentPage = v; },
        #ticker: (o, v) { o.ticker = v; },
      },
      parents: {
        smoke_0.MenuBar: _M0,
        _M0: smoke_1.PolymerElement,
      },
      declarations: {
        smoke_0.MenuBar: {
          #currentPage: const Declaration(#currentPage, String, kind: PROPERTY, annotations: const [smoke_2.reflectable, smoke_2.observable]),
          #pages: const Declaration(#pages, Map, isFinal: true, annotations: const [smoke_2.observable]),
          #ticker: const Declaration(#ticker, String, kind: PROPERTY, annotations: const [smoke_2.reflectable, smoke_2.observable]),
        },
      },
      names: {
        #currentPage: r'currentPage',
        #keys: r'keys',
        #page: r'page',
        #pages: r'pages',
        #ticker: r'ticker',
      }));
  configureForDeployment([
      () => Polymer.register('menu-bar', i0.MenuBar),
    ]);
  i1.main();
}
