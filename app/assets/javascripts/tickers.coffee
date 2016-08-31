socket = new WebSocket('ws://localhost:9001/socket')

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
          name: 'Title'
          data: [
              [0, 7, 2, 0, 4]
              [1, 1, 4, 2, 8]
              [2, 3, 3, 9, 3]
              [3, 7, 2, 0, 4]
              [4, 1, 4, 2, 8]
              [5, 3, 3, 9, 3]
              [6, 7, 2, 0, 4]
              [7, 1, 4, 2, 8]
              [8, 3, 3, 9, 3]
              [8, 7, 2, 0, 4]
              [9, 1, 4, 2, 8]
              [10, 3, 3, 9, 3]
          ]
          dataGrouping: units: [
            [
              'minute'
              [ 5 ]
            ]
            [
              'minute'
              [
                1
                2
                3
                4
                6
              ]
            ]
          ]
        } ]

  chart = $('#container').highcharts()

  $('table > tbody > tr').click (event) ->
    name = $(this).attr('id')
    chart.setTitle({text: name})

# $ ->
   $.getJSON 'https://www.highcharts.com/samples/data/jsonp.php?a=e&filename=aapl-ohlc.json&callback=?', (data) ->
     console.log(data)
     # create the chart
     $('#container').highcharts 'StockChart',
       rangeSelector: selected: 1
       title: text: 'AAPL Stock Price'
       series: [ {
         type: 'candlestick'
         name: 'AAPL Stock Price'
         data: data
         dataGrouping: units: [
           [
             'week'
             [ 1 ]
           ]
           [
             'month'
             [
               1
               2
               3
               4
               6
             ]
           ]
         ]
       } ]
     return
   return
