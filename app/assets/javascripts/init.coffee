$ ->
  name = $('#market-name').html()
  chart = $('#candle-chart').highcharts()

  # update chart
  route = jsRoutes.controllers.PoloniexController.candles(name)
  chart.setTitle({text: name})

  $.ajax
    method: route.method
    url: route.url
    success: (result) ->
      if (result.length == 0)
        return

      candles = result.map (obj) ->
        obj.slice(0, 5)

      chart.series[0].setData(candles, true, true)

      # obtain array of ema1 values from result
      ema1 = result.map (obj) ->
        {x: obj[0], y: obj[5]}

      # we don't care about non zero values
      ema1 = ema1.filter (obj) ->
        obj.y > 0

      chart.series[1].setData(ema1, true, true)

      ema2 = result.map (obj, index) ->
        {x: obj[0], y: obj[6]}

      ema2 = ema2.filter (obj) ->
        obj.y > 0

      chart.series[2].setData(ema2, true, true)

      vols = result.map (obj, index) ->
        {x: obj[0], y: obj[7]}

      chart.series[3].setData(vols, true, true)

      return
