function DashboardModel() {
  this.dashboardName = "100 Most Recent Anomalies";
  this.startTime = moment().subtract(7, "days");
  this.endTime = moment();
  this.mode = "AnomalySummary"
}

DashboardModel.prototype = {

  init : function(params) {
    if (params.dashboardName) {
      this.dashboardName = params.dashboardName;
    }
    if (params.startTime) {
      this.startTime = params.startTime;
    }
    if (params.dashboardName) {
      this.endTime = params.endTime;
    }
    if (params.dashboardName) {
      this.dashboardViewMode = params.dashboardViewMode;
    }
    console.log("Changed dashboardName to " + params);
  },
  rebuild : function(params) {
    console.log("Changed dashboardName to " + this.dashboardName);
  }

}