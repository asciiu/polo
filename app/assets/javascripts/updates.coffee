$ ->
  #########################################################
  # Web socket feed should update the table of tickers
  socket = new WebSocket('ws://localhost:9001' + jsRoutes.controllers.PoloniexController.socket().url)

  socket.onopen = (event) ->
    console.log('connected')
    return

  socket.onmessage = (event) ->
    chart = $('#candle-chart').highcharts()
    if (chart == undefined)
      console.log("no chart")
      return

    market = JSON.parse(event.data)

    if (market.name == "USDT_BTC")
      h1 = $('#'+market.name).html(market.status.last)
    else
      # update market status
      tr = $('#'+market.name).children('td')

      $(tr[1]).html((market.status.last).toFixed(8))
      $(tr[2]).html(market.status.baseVolume)
      $(tr[3]).html(market.status.percentChange)

    # update chart
    if (market.name == chart.title.textStr)
      # update the header stats
      $('#td-change').html(market.status.percentChange)
      $('#td-high').html(market.status.last.toFixed(8))
      $('#td-low').html(market.status.high24hr.toFixed(8))
      $('#td-last').html(market.status.low24hr.toFixed(8))

      # get latest candle from server
      route = jsRoutes.controllers.PoloniexController.latestCandle(market.name)
      $.ajax
        method: route.method
        url: route.url
        success: (result) ->
          # if latest candle time matches chart latest candle replace the last candle
          data = chart.series[0].data
          candles = chart.series[0]
          ema1 = chart.series[1]
          ema2 = chart.series[2]

          # last candle in chart data
          last = data[data.length-1]

          # if there is not candle then there's nothing to report
          if (result.length == 0)
            return

          # is the last.name (the candle period) the same as the result candle period?
          if (last != undefined && result[0] == last.name)
            last.update({high: result[2], low: result[3], close: result[4]})

            # ema1
            if (result[5] != 0)
              # update the very latest ema1
              ema1.data[ema1.data.length-1].update({y:result[5]})

            # ema2
            if (result[6] != 0)
              if (ema2.data.length == 0)
                ema2.addPoint({x: data.length-1, y:result[6]})
              else
                ema2.data[ema2.data.length-1].update({y:result[6]})

          else if (data.length == 0 || result[0] != last.name)
            candles.addPoint(result, true)

            if (result[5] != 0)
              ema1.addPoint({x: data.length-1, y:result[5]})

            if (result[6] != 0)
              ema2.addPoint({x: data.length-1, y:result[6]})

          return
    return