import 'dart:html';

// print the raw json response text from the server
void onDataLoaded(String responseText) {
  var jsonString = responseText;
  print(jsonString);
}

void loadData() {
  var url = "http://127.0.0.1:8012/corstest";

  // call the web server asynchronously
  var request = HttpRequest.getString(url).then(onDataLoaded);
}

main() {
  loadData();
}