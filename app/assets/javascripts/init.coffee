$ ->
  $('#candle-chart').highcharts 'StockChart',
        title: text: 'Test'
        exporting: enabled: false

        rangeSelector : {
          buttons : [{
              type : 'hour',
              count : 1,
              text : '1h'
          }, {
              type : 'hour',
              count : 2,
              text : '2h'
          }, {
              type : 'hour',
              count : 4,
              text : '4h'
          }, {
              type : 'hour',
              count : 6,
              text : '6h'
          }, {
              type : 'all',
              count : 1,
              text : '24h'
          }],
          selected : 3,
          inputEnabled : false
        }

        plotOptions: {
          candlestick: {
            color: 'rgba(255, 102, 102, 0.5)'
            upColor: 'rgba(112, 219, 112, 0.5)'
          }
        }
        yAxis: [{
          labels: {
              align: 'right',
              x: -3
          },
          title: {
              text: '5 Minute Candles'
          },
          height: '90%',
          lineWidth: 2
        }, {
          labels: {
              align: 'right',
              x: -3
          },
          title: {
              text: '24 Hr Volume'
          },
          top: '90%',
          height: '20%',
          offset: 0,
          lineWidth: 2
        }],
        series: [ {
          type: 'candlestick'
          name: 'CandleSticks'
          data: []
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
          type: 'area',
          name: 'Volume',
          color: 'rgba(153, 214, 255, 0.7)',
          data: [],
          yAxis: 1
        }]

  chart = $('#candle-chart').highcharts()
  name = $('#market-name').html()

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
      ema1 = result.map (obj) ->
        {x: obj[0], y: obj[5]}

      # we don't care about non zero values
      ema1 = ema1.filter (obj) ->
        obj.y > 0

      # set data for ema1 series
      chart.series[1].setData( ema1, true )

      ema2 = result.map (obj, index) ->
        {x: obj[0], y: obj[6]}

      ema2 = ema2.filter (obj) ->
        obj.y > 0

      chart.series[2].setData( ema2, true )

      vols = result.map (obj, index) ->
        {x: obj[0], y: obj[7]}

      chart.series[3].setData( vols, true)

      return
