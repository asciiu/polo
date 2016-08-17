socket = new WebSocket('ws://localhost:9001/socket')


socket.onopen = (event) ->
  console.log('open')
  return


socket.onmessage = (event) ->
  console.log(event.data)
  return