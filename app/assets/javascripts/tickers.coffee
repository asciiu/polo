$ ->
  $('#candle-chart').highcharts 'StockChart',
        rangeSelector: selected: 1
        title: text: 'Test'
        plotOptions: {
          candlestick: {
            color: 'rgba(255, 102, 102, 0.3)'
            upColor: 'rgba(112, 219, 112, 0.3)'
          }
        }
        series: [ {
          type: 'candlestick'
          name: 'CandleSticks'
          data: []
          dataGrouping: units: [
            [
              'minute'
              [
                5
              ]
            ]
          ]
        }, {
          name: 'EMA - 15'
          data: []
        }, {
          name: 'EMA - 7'
          data: []
        }]

  chart = $('#candle-chart').highcharts()

  $('table > tbody > tr').click (event) ->
    name = $(this).attr('id')
    # update header stats
    td = $('#'+name).children('td')

    # percent change
    $('#td-change').html($(td[3]).html())
    # 24 hour high
    $('#td-high').html($(td[4]).html())
    # 24 hour low
    $('#td-low').html($(td[5]).html())
    # last
    $('#td-last').html($(td[1]).html())

    chart.setTitle({text: name})
    route = jsRoutes.controllers.PoloniexController.candles(name)

    $.ajax
      method: route.method
      url: route.url
      success: (result) ->
        #chart.addSeries({type: "candlestick", data: result}, true)
        chart.series[0].setData(result, true)
        return
