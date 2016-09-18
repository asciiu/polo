$ ->

  groupingUnits = [[
    'week',
    [1]
  ], [
    'month',
    [1, 2, 3, 4, 6]
  ]]

  $('#candle-chart').highcharts 'StockChart',
        title: text: 'Test'
        plotOptions: {
          candlestick: {
            color: 'rgba(255, 102, 102, 0.7)'
            upColor: 'rgba(112, 219, 112, 0.7)'
          }
        }
        yAxis: [{
          labels: {
              align: 'right',
              x: -3
          },
          title: {
              text: '5 min'
          },
          height: '60%',
          lineWidth: 2
        }, {
          labels: {
              align: 'right',
              x: -3
          },
          title: {
              text: '24 Hr Volume'
          },
          top: '65%',
          height: '35%',
          offset: 0,
          lineWidth: 2
        }],
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
          name: 'EMA - 7'
          color: 'rgba(36, 143, 36, 1)',
          lineWidth: 1,
          data: []
        }, {
          name: 'EMA - 15'
          color: 'rgba(255, 102, 102, 1)',
          lineWidth: 1,
          data: []
        }, {
          type: 'column',
          name: 'Volume',
          data: [],
          yAxis: 1,
          dataGrouping: {
              units: groupingUnits
          }
        }]

  chart = $('#candle-chart').highcharts()

  # market selection from table
  $('table > tbody > tr').click (event) ->
    # market name
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

    # update chart
    chart.setTitle({text: name})
    route = jsRoutes.controllers.PoloniexController.candles(name)
    $.ajax
      method: route.method
      url: route.url
      success: (result) ->
        if (result.length == 0)
          return

        candles = result.map (obj) ->
          obj.slice(0, 5)

        # retrieve the candles and set chart data for candles series
        chart.series[0].setData(candles, true)

        # obtain array of ema1 values from result
        ema1 = result.map (obj, index) ->
          {x: index, y: obj[5]}
        # we don't care about non zero values
        ema1 = ema1.filter (obj) ->
          obj.y > 0
        # set data for ema1 series
        chart.series[1].setData( ema1, true )

        ema2 = result.map (obj, index) ->
          {x: index, y: obj[6]}
        ema2 = ema2.filter (obj) ->
          obj.y > 0
        chart.series[2].setData( ema2, true )

        vols = result.map (obj, index) ->
          {x: index, y: obj[7]}

        chart.series[3].setData( vols, true)

        return
