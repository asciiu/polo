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
    chart.series[0].setData(cdls, true, true)

    # obtain array of ema1 values from candles
    ema1 = candles.map (obj) ->
      {x: obj[0], y: obj[5]}

    # we don't care about non zero values
    ema1 = ema1.filter (obj) ->
      obj.y > 0

    # set data for ema1 series
    chart.series[1].setData( ema1, true, true )

    ema2 = candles.map (obj, index) ->
      {x: obj[0], y: obj[6]}

    ema2 = ema2.filter (obj) ->
      obj.y > 0

    chart.series[2].setData( ema2, true, true )

    vols = candles.map (obj, index) ->
      {x: obj[0], y: obj[7]}

    chart.series[3].setData( vols, true, true)

    centers = candles.map (obj, index) ->
      {x: obj[0], y: obj[8]}

    centers = centers.filter (obj) ->
      obj.y > 0
    chart.series[4].setData(centers, true, true)

    upper = candles.map (obj, index) ->
      {x: obj[0], y: obj[9]}
    upper = upper.filter (obj) ->
      obj.y > 0
    chart.series[5].setData(upper, true, true)

    lower = candles.map (obj, index) ->
      {x: obj[0], y: obj[10]}
    lower = lower.filter (obj) ->
      obj.y > 0
    chart.series[6].setData(lower, true, true)

    buys = candles.map (obj) ->
     {x: obj[0], y: obj[11]}

    buys = buys.filter (obj) ->
      obj.y > 0

    chart.series[7].setData(buys, true)

    # SELLS
    sells = candles.map (obj) ->
      {x: obj[0], y: obj[12]}

    sells = sells.filter (arr) ->
      arr.y > 0

    chart.series[8].setData(sells, true)

    return

  loadChartData = (socket, marketName) ->
    socket.send(marketName)

    chart = $('#candle-chart').highcharts()
    if (chart == undefined)
      console.log("no chart")
      return

    chart.setTitle({text: marketName})

  # if latest candle time matches chart latest candle replace the last candle
  setCandle = (result) ->
    chart = $('#candle-chart').highcharts()
    if (chart == undefined)
      console.log("no chart")
      return

    data = chart.series[0].data
    candles = chart.series[0]
    ema1 = chart.series[1]
    ema2 = chart.series[2]
    vol  = chart.series[3]
    avg  = chart.series[4]
    upper = chart.series[5]
    lower = chart.series[6]

    # last candle in chart data
    last = data[data.length-1]

    time = last.x

    # if there is not candle then there's nothing to report
    if (result.length == 0)
      return

    # is the last.name (the candle period) the same as the result candle period?
    if (last != undefined && result[0] == time)
      last.update({x: result[0], high: result[2], low: result[3], close: result[4]})

      # ema1
      if (result[5] != 0)
        # update the very latest ema1
        ema1.data[ema1.data.length-1].update({y:result[5]})

      # ema2
      if (result[6] != 0)
        ema2.data[ema2.data.length-1].update({y:result[6]})

      # 24 hour volume
      if (vol.data.length == 0)
        vol.addPoint({x: time, y: result[7]})
      else
        vol.data[vol.data.length-1].update({y: result[7]})

      # avg
      if (result[8] != 0)
        avg.data[avg.data.length-1].update({y: result[8]})

      # ema2
      if (result[9] != 0)
        upper.data[upper.data.length-1].update({y: result[9]})

      # ema1
      if (result[10] != 0)
        lower.data[lower.data.length-1].update({y: result[10]})

    # new candle period
    else if (data.length == 0 || result[0] != time)
      candles.addPoint(result.slice(0, 4), true, true, true)

      if (result[5] != 0)
        ema1.addPoint({x: result[0], y:result[5]}, true, true, true)

      if (result[6] != 0)
        ema2.addPoint({x: result[0], y:result[6]}, true, true, true)

      if (result[8] != 0)
        avg.addPoint({x: result[0], y:result[8]}, true, true, true)
      if (result[9] != 0)
        upper.addPoint({x: result[0], y:result[9]}, true, true, true)
      if (result[10] != 0)
        lower.addPoint({x: result[0], y:result[10]}, true, true, true)

      # limit data to 288 values
      if (vol.data.length < 288)
        vol.addPoint({x: result[0], y:result[7]}, true, false, true)
      else
        vol.addPoint({x: result[0], y:result[7]}, true, true, true)



    return

  #########################################################
  sessionId = $('div#session-id').html()
  # Web socket feed should update the table of tickers
  socket = new WebSocket('ws://localhost:9001' + jsRoutes.controllers.HistoryController.socket(sessionId).url)

  socket.onopen = (event) ->
    name = $('tr')[1].id
    loadChartData(socket, name)

    # register a click on market name and send to to the server
    $('table > tbody > tr').click (event) ->
      marketName = $(this).attr('id')
      loadChartData(socket, marketName)

    $('div#run-btn').click (event) ->
      socket.send('play')

    console.log('connected')
    return

  socket.onmessage = (event) ->
    msg = JSON.parse(event.data);

    switch msg.type
      when 'MarketCandles'
        setCandles(msg.data)

      when 'MarketCandle'
        setCandle(msg.data)

      when 'MarketMessage'
        # Should be of type MarketMessage in json
        console.log('message')

    return