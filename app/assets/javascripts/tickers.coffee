socket = new WebSocket('ws://localhost:9001/socket')


socket.onopen = (event) ->
  console.log('open')
  return

socket.onmessage = (event) ->
  market = JSON.parse(event.data)

  if (market.name == "USDT_BTC")
    h1 = $('#'+market.name).html(market.status.last)
  else
    tr = $('#'+market.name).children('td')

    $(tr[1]).html((market.status.last).toFixed(8))
    $(tr[2]).html(market.status.baseVolume)
    $(tr[3]).html((market.status.percentChange*100).toFixed(2))

  return