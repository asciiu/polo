$ ->
  #route = jsRoutes.controllers.PoloniexController.isRecording()
  #$.ajax
  #  method: route.method
  #  url: route.url
  #  success: (result) ->
  #    # result will be boolean
  #    recording = JSON.parse(result)
  #    if (recording)
  #      $('div#run-btn').html('Stop')
  #      $('div#run-btn').addClass('alert')

  $('div#run-btn').click (event) ->
    self = $(this)
    if (self.html() == 'Record')
      route = jsRoutes.controllers.PoloniexController.startCapture()
      $.ajax
        method: route.method
        url: route.url
        success: (result) ->
          self.html('Stop')
          self.addClass('alert')
    else
      route = jsRoutes.controllers.PoloniexController.endCapture()
      $.ajax
        method: route.method
        url: route.url
        success: (result) ->
          self.html('Record')
          self.removeClass('alert')

    return

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