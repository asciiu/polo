$ ->
  name = $('#market-name').html()

  # update chart
  route = jsRoutes.controllers.PoloniexController.candles(name)

  $.ajax
    method: route.method
    url: route.url
    success: (result) ->
      if (result.length == 0)
        return

      candles = result.map (obj) ->
        obj.slice(0, 5)

      # obtain array of ema1 values from result
      ema1 = result.map (obj) ->
        {x: obj[0], y: obj[5]}

      # we don't care about non zero values
      ema1 = ema1.filter (obj) ->
        obj.y > 0

      ema2 = result.map (obj, index) ->
        {x: obj[0], y: obj[6]}

      ema2 = ema2.filter (obj) ->
        obj.y > 0

      vols = result.map (obj, index) ->
        {x: obj[0], y: obj[7]}

      $('#candle-chart').highcharts 'StockChart',
             title: text: name
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
               selected : 2,
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
                   align: 'left',
                   x: 10
               },
               title: {
                   text: '5 Minute Candles'
               },
               height: '80%',
               lineWidth: 1
             }, {
               labels: {
                   align: 'left',
                   x: 10
               },
               title: {
                   text: '24 Hr Vol'
               },
               top: '90%',
               height: '10%',
               offset: 0,
               lineWidth: 1
             }],
             series: [ {
               type: 'candlestick'
               name: 'CandleSticks'
               data: candles
             }, {
               name: 'EMA - 7'
               color: 'rgba(36, 143, 36, 1)',
               lineWidth: 1,
               data: ema1
             }, {
               name: 'EMA - 15'
               color: 'rgba(255, 102, 102, 1)',
               lineWidth: 1,
               data: ema2
             }, {
               type: 'area',
               name: 'Volume',
               color: 'rgba(153, 214, 255, 0.7)',
               data: vols,
               yAxis: 1
             }]

      return
