function DimensionTreeMapView(dimensionTreeMapModel) {
  var template = $("#dimension-tree-map-template").html();
  this.template_compiled = Handlebars.compile(template);
  this.placeHolderId = "#dimension-tree-map-placeholder";
  this.dimensionTreeMapModel = dimensionTreeMapModel;
  this.currentTimeRangeConfig = {
    dateLimit : {
      days : 60
    },
    showDropdowns : true,
    showWeekNumbers : true,
    timePicker : true,
    timePickerIncrement : 5,
    timePicker12Hour : true,
    ranges : {
      'Last 24 Hours' : [ moment(), moment() ],
      'Yesterday' : [ moment().subtract(1, 'days'), moment().subtract(1, 'days') ],
      'Last 7 Days' : [ moment().subtract(6, 'days'), moment() ],
      'Last 30 Days' : [ moment().subtract(29, 'days'), moment() ],
      'This Month' : [ moment().startOf('month'), moment().endOf('month') ],
      'Last Month' : [ moment().subtract(1, 'month').startOf('month'), moment().subtract(1, 'month').endOf('month') ]
    }
  };
  this.baselineTimeRangeConfig = {
    dateLimit : {
      days : 60
    },
    showDropdowns : true,
    showWeekNumbers : true,
    timePicker : true,
    timePickerIncrement : 5,
    timePicker12Hour : true,
    ranges : {
      'Last 24 Hours' : [ moment(), moment() ],
      'Yesterday' : [ moment().subtract(1, 'days'), moment().subtract(1, 'days') ],
      'Last 7 Days' : [ moment().subtract(6, 'days'), moment() ],
      'Last 30 Days' : [ moment().subtract(29, 'days'), moment() ],
      'This Month' : [ moment().startOf('month'), moment().endOf('month') ],
      'Last Month' : [ moment().subtract(1, 'month').startOf('month'), moment().subtract(1, 'month').endOf('month') ]
    }
  };

}

DimensionTreeMapView.prototype = {
  render : function() {
    var result = this.template_compiled(this.dimensionTreeMapModel);
    $(this.placeHolderId).html(result);
    this.renderTreemapHeaderSection();
    this.renderTreemapSection();
  },

  renderTreemapHeaderSection : function() {

    function current_range_cb(start, end) {
      $('#heatmap-current-range span').addClass("time-range").html(start.format('MMM D, ') + start.format('hh:mm a') + '  &mdash;  ' + end.format('MMM D, ') + end.format('hh:mm a'));
    }
    function baseline_range_cb(start, end) {
      $('#heatmap-baseline-range span').addClass("time-range").html(start.format('MMM D, ') + start.format('hh:mm a') + '  &mdash;  ' + end.format('MMM D, ') + end.format('hh:mm a'));
    }

    $('#heatmap-current-range').daterangepicker(this.currentTimeRangeConfig, current_range_cb);

    $('#heatmap-baseline-range').daterangepicker(this.baselineTimeRangeConfig, baseline_range_cb);

    current_range_cb(this.dimensionTreeMapModel.currentStartTime, this.dimensionTreeMapModel.currentEndTime);
    baseline_range_cb(this.dimensionTreeMapModel.baselineStartTime, this.dimensionTreeMapModel.baselineEndTime);
  },

  renderTreemapSection : function() {
    // DRAW THE AXIS
    // Create the SVG Viewport
    var height = $('#axis-placeholder').height();
    var width = $('#axis-placeholder').width();
    var lineGraph = d3.select("#axis-placeholder").append("svg:svg").attr("width", width).attr("height", height);
    var myLine = lineGraph.append("svg:line").attr("x1", 0).attr("y1", height - 1).attr("x2", width).attr("y2", height - 1).style("stroke", "rgb(6,120,155)");
    startValue = 0;
    endValue = this.dimensionTreeMapModel.currentTotal;
    middleValue = this.dimensionTreeMapModel.currentTotal/2;
    var startLabel = lineGraph.append("svg:text").attr("x", 0).attr("y", height - 6).text(startValue).attr("font-family", "SourceSansPro").attr("font-size", "20px");
    var endLabel = lineGraph.append("svg:text").attr("x", width / 2).attr("y", height - 6).text(middleValue).attr("font-family", "SourceSansPro").attr("font-size", "20px").attr("text-anchor",
        "middle");
    var endLabel = lineGraph.append("svg:text").attr("x", width).attr("y", height - 6).text(endValue).attr("font-family", "SourceSansPro").attr("font-size", "20px").attr("text-anchor", "end");

    margin = 0;

    dimensions = this.dimensionTreeMapModel.dimensions;
    for (i = 0; i < dimensions.length; i++) {
      data = this.dimensionTreeMapModel.treeMapData[i];
      dimension = dimensions[i];
      var dimensionPlaceHolderId = '#' + dimension + '-heatmap-placeholder';
      var height = $(dimensionPlaceHolderId).height();
      var width = $(dimensionPlaceHolderId).width();
      var treeMap = d3.layout.treemap().mode("slice").size([ width, height ]).sort(function(a, b) {
        return a.value - b.value;
      });

      var div = d3.select(dimensionPlaceHolderId).attr("class", "heatmap")
      .append("svg:svg").attr("width", width).attr("height", height).append("svg:g").attr("transform", "translate(.5,.5)");

      var nodes = treeMap.nodes(data).filter(function(d) {
        return !d.children;
      });
      var cell = div.selectAll("g").data(nodes).enter().append("svg:g").attr("class", "cell").attr("transform", function(d) {
        return "translate(" + d.x + "," + d.y + ")";
      }).on("click", function(d) {
        return zoom(node == d.parent ? root : d.parent);
      });

      cell.append("svg:rect").attr("width", function(d) {
        return d.dx - 1
      }).attr("height", function(d) {
        return d.dy - 1
      }).style("fill", function(d) {
        return '#EC9F98'
      });

      cell.append("svg:text").attr("x", function(d) {
        return d.dx / 2;
      }).attr("y", function(d) {
        return d.dy / 2;
      }).attr("dy", ".35em").attr("text-anchor", "middle").text(function(d) {
        return d.t;
      }).style("opacity", function(d) {
        d.w = this.getComputedTextLength();
        return d.dx > d.w ? 1 : 0;
      });
    }
  }
}