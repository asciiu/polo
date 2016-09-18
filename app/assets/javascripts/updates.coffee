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

    # Should be of type MarketUpdate in json
    market = JSON.parse(event.data)

    if (market.name == "USDT_BTC")
      h1 = $('#'+market.name).html(market.info.last)
    else
      # update market.info
      tr = $('#'+market.name).children('td')

      $(tr[1]).html((market.info.last).toFixed(8))
      $(tr[2]).html(market.info.baseVolume)
      $(tr[3]).html(market.info.percentChange)

    # update chart
    if (market.name == chart.title.textStr)
      # update the header stats
      $('#td-change').html(market.info.percentChange)
      $('#td-high').html(market.info.last.toFixed(8))
      $('#td-low').html(market.info.high24hr.toFixed(8))
      $('#td-last').html(market.info.low24hr.toFixed(8))

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
          vol  = chart.series[3]

          # last candle in chart data
          last = data[data.length-1]
          time = last.x
          
          if (last.x == undefined)
            console.log(last)

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

          # new candle period
          else if (data.length == 0 || result[0] != time)
            candles.addPoint(result.slice(0, 4), true, true, true)

            if (result[5] != 0)
              ema1.addPoint({x: result[0], y:result[5]}, true, true, true)

            if (result[6] != 0)
              ema2.addPoint({x: result[0], y:result[6]}, true, true, true)

            # limit data to 288 values
            if (vol.data.length < 288)
              vol.addPoint({x: result[0], y:result[7]}, true, false, true)
            else
              vol.addPoint({x: result[0], y:result[7]}, true, true, true)

          return
    return