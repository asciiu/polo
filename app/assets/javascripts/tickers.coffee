$ ->
  $('#container').highcharts 'StockChart',
        rangeSelector: selected: 1
        title: text: 'Test'
        plotOptions: {
          candlestick: {
            color: 'red'
            upColor: 'green'
          }
        }
        series: [ {
          type: 'candlestick'
          data: []
          dataGrouping: units: [
            [
              'minute'
              [
                5
              ]
            ]
          ]
        } ]

  chart = $('#container').highcharts()

  $('table > tbody > tr').click (event) ->
    name = $(this).attr('id')
    chart.setTitle({text: name})
    route = jsRoutes.controllers.PoloniexController.candles(name)

    $.ajax
      method: route.method
      url: route.url
      success: (result) ->
        #chart.addSeries({type: "candlestick", data: result}, true)
        chart.series[0].setData(result, true)
        return

  # Web socket feed should update the table ot tickers
  socket = new WebSocket('ws://localhost:9001' + jsRoutes.controllers.PoloniexController.socket().url)

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
      $(tr[3]).html(market.status.percentChange)

    # update chart
    if (market.name == chart.title.textStr)
      # get latest candle from server
      route = jsRoutes.controllers.PoloniexController.latestCandle(market.name)
      $.ajax
        method: route.method
        url: route.url
        success: (result) ->
          # if latest candle time matches chart latest candle replace the last candle
          data = chart.series[0].data
          last = data[data.length-1]
          if (result.length > 0 && result[0] == last.name)
            chart.series[0].data[data.length-1].update({high: result[2], low: result[3], close: result[4]})
          else
            chart.series[0].addPoint(result, true)

          #chart.addSeries({type: "candlestick", data: data}, true)
          #chart.series[0].setData(data, true)
          # else add the latest candle data
          #chart.addSeries({type: "candlestick", data: result}, true)
          #chart.series[0].setData(result, true)
          return
    return

# $ ->
#   $.getJSON 'https://www.highcharts.com/samples/data/jsonp.php?a=e&filename=aapl-ohlc.json&callback=?', (data) ->
#     console.log(data)
#     # create the chart
#     $('#container').highcharts 'StockChart',
#       rangeSelector: selected: 1
#       title: text: 'AAPL Stock Price'
#       series: [ {
#         type: 'candlestick'
#         name: 'AAPL Stock Price'
#         data: data
#         dataGrouping: units: [
#           [
#             'week'
#             [ 1 ]
#           ]
#           [
#             'month'
#             [
#               1
#               2
#               3
#               4
#               6
#             ]
#           ]
#         ]
#       } ]
#     return
#   return
