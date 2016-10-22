$ ->
  setCandles = (candles) ->
    if (candles.length == 0)
      return

    chart = $('#candle-chart').highcharts()
    if (chart == undefined)
      console.log("no chart")
      return

    cdls = candles.map (obj) ->
      obj.slice(0, 5)

    # retrieve the candles and set chart data for candles series
    chart.series[0].setData(cdls, true)

    # obtain array of ema1 values from candles
    ema1 = candles.map (obj) ->
      {x: obj[0], y: obj[5]}

    # we don't care about non zero values
    ema1 = ema1.filter (obj) ->
      obj.y > 0

    # set data for ema1 series
    chart.series[1].setData( ema1, true )

    ema2 = candles.map (obj, index) ->
      {x: obj[0], y: obj[6]}

    ema2 = ema2.filter (obj) ->
      obj.y > 0

    chart.series[2].setData( ema2, true )

    vols = candles.map (obj, index) ->
      {x: obj[0], y: obj[7]}

    chart.series[3].setData( vols, true)

    return

  loadChartData = (socket, marketName) ->
    socket.send(marketName)

    chart = $('#candle-chart').highcharts()
    if (chart == undefined)
      console.log("no chart")
      return

    chart.setTitle({text: marketName})

  #########################################################
  # Web socket feed should update the table of tickers
  socket = new WebSocket('ws://localhost:9001' + jsRoutes.controllers.HistoryController.socket().url)

  socket.onopen = (event) ->
    name = $('tr')[1].id
    loadChartData(socket, name)

    # register a click on market name and send to to the server
    $('table > tbody > tr').click (event) ->
      marketName = $(this).attr('id')
      loadChartData(socket, marketName)

    console.log('connected')
    return

  socket.onmessage = (event) ->
    msg = JSON.parse(event.data);

    switch msg.type
      when 'MarketCandles'
        setCandles(msg.data)

      when 'MarketCandle'
        console.log('update the last market candle')

      when 'MarketMessage'
        # Should be of type MarketMessage in json
        console.log('message')

    return