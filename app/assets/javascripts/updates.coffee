$ ->
  #########################################################
  # Web socket feed should update the table of tickers
  socket = new WebSocket('ws://localhost:9001' + jsRoutes.controllers.PoloniexController.socket().url)

  socket.onopen = (event) ->
    console.log('connected')
    return

  socket.onmessage = (event) ->
    # Should be of type MarketMessage in json
    market = JSON.parse(event.data)

    if (market.name == "USDT_BTC")
      h1 = $('#'+market.cryptoCurrency).html(market.last)
    else
      # update market.info
      tr = $('#'+market.cryptoCurrency).children('td')

      $(tr[1]).html((market.last).toFixed(8))
      $(tr[2]).html(market.baseVolume)
      $(tr[3]).html(market.percentChange)

    return


