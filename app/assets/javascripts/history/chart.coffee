$ ->
  selectPointsByDrag = (e) ->
    # Select points
    series = @series[0]
    Highcharts.each series.points, (point) ->
      if point.x >= e.xAxis[0].min and point.x <= e.xAxis[0].max
        point.select true, true

    xMin = chart.xAxis[0].translate((e.xAxis[0]||chart.xAxis[0]).min)
    xMax = chart.xAxis[0].translate((e.xAxis[0]||chart.xAxis[0]).max)
    yMin = chart.yAxis[0].translate((e.yAxis[0]||chart.yAxis[0]).min)
    yMax = chart.yAxis[0].translate((e.yAxis[0]||chart.yAxis[0]).max)

    console.log(chart.plotHeight)
    rectangle.attr({
      x: xMin + chart.plotLeft,
      y: chart.plotTop,
      width: xMax-xMin,
      height: chart.plotHeight
    })

    # Fire a custom event
    #Highcharts.fireEvent this, 'selectedpoints', points: @getSelectedPoints()
    return false
    # Don't zoom

  ###*
  # The handler for a custom event, fired from selection event
  ###
  selectedPoints = (e) ->
    console.log('toast')
    # Show a label
    #toast this, '<b>' + e.points.length + ' points selected.</b>' + '<br>Click on empty space to deselect.'
    return

  unselectByClick = (event) ->
    points = @getSelectedPoints()
    if points.length > 0
      Highcharts.each points, (point) ->
        point.select false

    rectangle.attr({x:0,y:0,width:0,heigth:0})

    return

  Highcharts.stockChart 'candle-chart', {
    title: text: 'Captured Data'
    exporting: enabled: false
    tooltip: enabled: false
    chart: {
      events: {
        click: unselectByClick
        selection: selectPointsByDrag
        selectedpoints: selectedPoints
      }
      zoomType: 'xy'
    }
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
      selected : 4,
      inputEnabled : false
    }

    plotOptions: {
      series: {
        allowPointSelect: true,
        states: {
          select: {
            color: 'rgba(255,255,255, 1.0)'
          }
        }
      },
      candlestick: {
        color: 'rgba(255, 102, 102, .7)'
        lineColor: 'rgba(255, 102, 102, 1)'
        upColor: 'rgba(112, 219, 112, .7)'
        upLineColor: 'rgba(112, 219, 112, 1)'
      },
      scatter: {
        marker: {
          radius: 4,
          symbol: 'circle',
          states: {
            hover: {
              enabled: true,
              lineColor: 'rgb(100,100,100)'
           }
          }
        },
        states: {
          hover: {
            marker: {
              enabled: false
            }
          }
        },
        tooltip: {
          headerFormat: '<b>{series.name}</b><br>',
          pointFormat: '{point.x} time, {point.y} price'
        }
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
      allowPointSelect: true
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
      color: 'rgba(179, 224, 255, 0.2)',
      data: [],
      yAxis: 1
    }, {
      type: 'scatter',
      name: 'Buy',
      color: 'rgba(0, 119, 181, 1)',
      data: []
    }, {
      type: 'scatter',
      name: 'Sell',
      color: 'rgba(221, 81, 67, 1)',
      data: []
    }]
  }


  chart = $('#candle-chart').highcharts()
  rectangle = chart.renderer.rect(0,0,0,0,0).css({
      stroke: 'null',
      strokeWidth: '.5',
      fill: 'rgba(179, 224, 255, 0.5)'
  }).add();

