$ ->
  light = (marketName, isSetup) ->
    tr = $('#'+marketName)

    if (isSetup)
      tr.addClass('callout success')
    else
      tr.removeClass('callout success')

  #########################################################
  # Web socket feed should update the table of tickers
  socket = new WebSocket('ws://localhost:9001' + jsRoutes.controllers.PoloniexController.setup().url)

  socket.onopen = (event) ->
    console.log('connected to setups')
    return

  socket.onmessage = (event) ->
    msg = JSON.parse(event.data);
    switch msg.type
      when 'MarketSetups'

        count = msg.data.length
        i = 0
        while i < count
          marketName = msg.data[i]
          light marketName, true
          ++i

      when 'MarketSetup'
        marketName = msg.data.marketName
        light marketName, msg.data.isSetup


    return
