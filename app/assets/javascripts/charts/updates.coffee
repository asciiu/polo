$ ->
  updateChartData = (result) ->
    chart = $('#candle-chart').highcharts()
    candles = chart.series[0]
    ema1 = chart.series[1]
    ema2 = chart.series[2]
    vol  = chart.series[3]
    avg  = chart.series[4]
    upper = chart.series[5]
    lower = chart.series[6]

    # if there is not candle then there's nothing to report
    if (result.length == 0)
      return

    # last candle in chart data
    last = candles.data[candles.data.length-1]
    time = last.x

    # is the last.name (the candle period) the same as the result candle period?
    if (result[0] == time)
      last.update({x: result[0], high: result[2], low: result[3], close: result[4]})

      # ema1
      if (result[5] != 0)
        ema1.data[ema1.data.length-1].update({y: result[5]})

      # ema2
      if (result[6] != 0)
        ema2.data[ema2.data.length-1].update({y: result[6]})

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
    else if (result[0] != time)
      candle = result.slice(0, 4)
      candles.addPoint({x: candle[0], open: candle[1], high: candle[2], low: candle[3], close: candle[4]}, true, true, true)

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

  setChartStats = (high, low, last, percent, usd) ->
    chart = $('#candle-chart').highcharts()
    line1 = "24hr H: " + high + "<br>"
    line2 = "24hr L: " + low + "<br>"
    line3 = "Last: " + last + " BTC (" + percent + "%)" + "<br>"
    line4 = "USD: $" + usd

    str = line1 + line2 + line3
    if (usd != "")
      str += line4

    chart.upperRight.attr({
      text: str
    })


  #########################################################
  name = $('#market-name').html()
  last = $('#market-last').html()
  percent = $('#market-percent').html()
  low = $('#market-low').html()
  high = $('#market-high').html()

  chart = $('#candle-chart').highcharts()
  chart.setTitle({text: name})
  setChartStats(high, low, last, percent, "")

  # Web socket feed should update the table of tickers
  socket = new WebSocket('ws://localhost:9001' + jsRoutes.controllers.PoloniexController.updates(name).url)

  socket.onopen = (event) ->
    console.log('connected')
    return

  socket.onmessage = (event) ->
   msg = JSON.parse(event.data);

   switch msg.type
     when 'MarketMessage'
       # Should be of type MarketMessage in json
       market = msg.data

       # update chart
       chart = $('#candle-chart').highcharts()
       candles = chart.series[0]
       ema1 = chart.series[1]
       ema2 = chart.series[2]
       vol  = chart.series[3]
       avg  = chart.series[4]
       upper = chart.series[5]
       lower = chart.series[6]

       last = market.last.toFixed(8)
       high = market.high24hr.toFixed(8)
       low = market.low24hr.toFixed(8)
       percent = market.percentChange
       usd = msg.candle[10]

       setChartStats(high, low, last, percent, usd.toFixed(4))

       updateChartData(msg.candle)

    return