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

  return

$ ->
  $('#container').highcharts 'StockChart',
        rangeSelector: selected: 1
        title: text: 'Test'
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
