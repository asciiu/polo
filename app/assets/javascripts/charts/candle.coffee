$ ->
  ########################################################################
  # selectPointsByDrag - selects candle data via drag and draws a
  # rectangle around graph region. This should be invoked after
  # a drag event. Refer to the chart events.
  ########################################################################
  selectPointsByDrag = (e) ->
    points = @series[0].points.filter (point) ->
      point.x >= e.xAxis[0].min and point.x <= e.xAxis[0].max

    points.forEach (point) ->
      point.select(true, true)

    open = points[0].open
    close = points[points.length-1].close

    # percent change in this selected period
    percent = ((close - open) / open) * 100
    # lowest value
    low = Math.min.apply(null, (points.map (p) -> p.low))
    # highest value
    high = Math.max.apply(null, (points.map (p) -> p.high))

    str = 'C: ' + percent.toFixed(2) + ' %<br/>H: ' + high.toFixed(8) + '<br/>L: ' + low.toFixed(8)

    # series 0 will be the first series - candle - of our chart
    #Highcharts.each @series[0].points, (point) ->
    #  if point.x >= e.xAxis[0].min and point.x <= e.xAxis[0].max
    #    point.select true, true

    # min and max of selection area
    xMin = chart.xAxis[0].translate((e.xAxis[0]||chart.xAxis[0]).min)
    xMax = chart.xAxis[0].translate((e.xAxis[0]||chart.xAxis[0]).max)
    yMin = chart.yAxis[0].translate((e.yAxis[0]||chart.yAxis[0]).min)
    yMax = chart.yAxis[0].translate((e.yAxis[0]||chart.yAxis[0]).max)

    chart.lbl.attr({
      text: str,
      x: xMax + chart.plotLeft,
      y: chart.plotTop,
    })

    rectangle.attr({
      x: xMin + chart.plotLeft,
      y: chart.plotTop,
      width: xMax-xMin,
      height: chart.plotHeight
    })

    # Fire a custom event
    #Highcharts.fireEvent this, 'selectedpoints', points: @getSelectedPoints()

    # return false to cancel zoom
    return false


  ########################################################################
  # selectPointsByDrag - selects candle data via drag and draws a
  # rectangle around graph region
  ########################################################################
  selectedPoints = (e) ->
    console.log('toast')
    # Show a label
    #toast this, '<b>' + e.points.length + ' points selected.</b>' + '<br>Click on empty space to deselect.'
    return


  ########################################################################
  # selectPointsByDrag - selects candle data via drag and draws a
  # rectangle around graph region
  ########################################################################
  unselectByClick = (event) ->
    points = @getSelectedPoints()
    if points.length > 0
      Highcharts.each points, (point) ->
        point.select false

    rectangle.attr({x:0,y:0,width:0,heigth:0})
    chart.lbl.attr({
      text: "",
      x: 0,
      y: 0,
    })

    return

  ########################################################################
  # Create chart
  ########################################################################
  Highcharts.stockChart 'candle-chart', {
    title: {
      text: 'Hello'
      style: {
        font: 'bold 16px "Trebuchet MS", Verdana, sans-serif'
        color: '#FFF'
      }
    }

    exporting: enabled: false
    credits: enabled: false

    tooltip: {
      style: {
        color: '#FFF'
      }
      enabled: true,
      positioner: (labelWidth, labelHeight, point) ->
        return { x: chart.plotWidth - labelWidth + chart.plotLeft, y: 39 }
      shadow: false,
      borderWidth: 0,
      backgroundColor: 'rgba(30, 43, 52, 1.0)'
      formatter: () ->
        x = this.x
        point = this.points.find (p) -> x == p.x
        point = point.point

        open = point.open.toFixed(8)
        high = point.high.toFixed(8)
        low = point.low.toFixed(8)
        close = point.close.toFixed(8)
        date = Highcharts.dateFormat('%b %e %Y %H:%M', new Date(this.x))

        s = '<b>'+date + ' O:</b> '  + open + ' <b>H:</b> ' + high + '<b> L:</b> ' + low + '<b> C: </b>' + close

        return s
      shared: true
    }

    chart: {
      events: {
        click: unselectByClick
        selection: selectPointsByDrag
        selectedpoints: selectedPoints
      }
      zoomType: 'xy'
      backgroundColor: 'rgba(30, 43, 52, 1.0)'
      style: {
        fontFamily: 'monospace',
        color: "#FFF"
      }
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
            color: 'rgba(255, 255, 255, 0.0)'
          }
        }
        point: {
          events: {
            select: (e) ->
          }
        }
      },
      candlestick: {
        color: 'rgba(255, 102, 102, 0.6)'
        lineColor: 'rgba(255, 102, 102, 1)'
        upColor: 'rgba(112, 219, 112, 0.6)'
        upLineColor: 'rgba(112, 219, 112, 1.0)'
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
          x: 5,
          padding: 0,
          format: '{value:.8f}'
      },
      gridLineWidth: 0,
      minorGridLineWidth: 0,

      height: '80%',
      crosshair: {
        snap: false,
        label: {
          align: 'left',
          enabled: true,
          format: '{value:.8f}',
          padding: 4,
          backgroundColor: 'rgba(22, 122, 198, 0.7)'
        }
      }
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
      gridLineWidth: 0,
      minorGridLineWidth: 0,
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
      fillColor: 'rgba(179, 224, 255, 0.2)',
      data: [],
      yAxis: 1
    }, {
      name: 'center'
      color: 'rgba(255, 255, 255, 1)',
      lineWidth: 1,
      data: []
    }, {
      name: 'upper'
      color: 'rgba(255, 255, 255, 0.6)',
      lineWidth: 1,
      data: []
    }, {
      name: 'lower'
      color: 'rgba(255, 255, 255, 0.6)',
      lineWidth: 1,
      data: []
    }]
  }

  chart = $('#candle-chart').highcharts()
  rectangle = chart.renderer.rect(0,0,0,0,0).css({
      stroke: 'null',
      strokeWidth: '.5',
      fill: 'rgba(22, 122, 198, 0.4)'
  }).add();

  chart.lbl = chart.renderer.label('', 0, 0, null, null, null, true).css({
    textAlign: 'center'
    fontSize: '8pt'
    color: 'rgba(255, 255, 255,0.8)'
  }).add()

