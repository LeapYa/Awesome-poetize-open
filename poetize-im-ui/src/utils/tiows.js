import ReconnectingWebSocket from 'reconnecting-websocket';

/**
 * @param {*} ws_protocol wss or ws
 * @param {*} ip
 * @param {*} port
 * @param {*} paramStr 加在ws url后面的请求参数，形如：name=张三&id=12
 * @param {*} binaryType 'blob' or 'arraybuffer'
 */
export default function (ws_protocol, ip, port, paramStr, binaryType) {

  this.ws_protocol = ws_protocol;
  this.ip = ip;
  this.port = port;
  this.paramStr = paramStr;
  this.binaryType = binaryType;

  if (port === "") {
    this.url = ws_protocol + '://' + ip + '/socket';
  } else {
    this.url = ws_protocol + '://' + ip + ":" + port + '/socket';
  }
  if (paramStr) {
    this.url += '?' + paramStr;
  }

  this.connect = () => {
    let ws = new ReconnectingWebSocket(this.url);
    this.ws = ws;
    ws.binaryType = this.binaryType;

    ws.onopen = function (event) {
      //获取离线消息
    }

    ws.onclose = function (event) {

    }

    ws.onerror = function (event) {

    }
  }

  this.send = (data) => {
    this.ws.send(data);
  }
}
