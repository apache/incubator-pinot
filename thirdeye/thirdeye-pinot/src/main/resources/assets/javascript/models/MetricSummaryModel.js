function MetricSummaryModel() {
  this.summaryDashboardId = null;
  this.dashboardName = null;
  this.previousDashboardName = null;

  this.timeRange = "24_HOURS";
  this.metricSummaryList = [];

  this.renderViewEvent = new Event();
}

MetricSummaryModel.prototype = {

  reset : function() {

  },
  setParams : function(params) {
    console.log("Set params of MetricSummaryController");
    var params = HASH_SERVICE.getParams();
    if (params != undefined) {
      console.log('params');
      if (params[HASH_PARAMS.DASHBOARD_DASHBOARD_NAME] != undefined) {
        console.log('dashboard');
        this.previousDashboardName = this.dashboardName;
        this.dashboardName = params[HASH_PARAMS.DASHBOARD_DASHBOARD_NAME];
        this.summaryDashboardId = params[HASH_PARAMS.DASHBOARD_SUMMARY_DASHBOARD_ID];
      }
    }
  },

  rebuild : function() {
    if (this.dashboardName != null) {

      if (this.previousDashboardName != this.dashboardName) {
        dataService.fetchMetricSummary(this.dashboardName, this.timeRange, this.updateModelAndNotifyView.bind(this));
      } else {
        this.updateModelAndNotifyView(this.metricSummaryList)
      }
    }
  },
  updateModelAndNotifyView : function(metricSummaryList) {
    console.log('Results');
    console.log(metricSummaryList);
    this.metricSummaryList = metricSummaryList;
    this.renderViewEvent.notify();
  }
};

