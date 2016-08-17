socket = new WebSocket('ws://localhost:9001/socket')


socket.onopen = (event) ->
  console.log('open')
  return

socket.onmessage = (event) ->
  market = JSON.parse(event.data)
  tr = $('#'+market.ticker).children('td')

  $(tr[1]).html(market.status.last)
  $(tr[2]).html(market.status.baseVolume)
  $(tr[3]).html((market.status.percentChange*100).toFixed(2))

  return