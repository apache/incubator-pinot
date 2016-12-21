function MetricSummaryController(parentController){
  this.parentController = parentController;
  this.metricSummaryModel = new MetricSummaryModel();
  this.metricSummaryView = new MetricSummaryView(this.metricSummaryModel);
}


MetricSummaryController.prototype ={

    handleAppEvent: function(params){
      console.log("handleAppEvent of metricSummary");
      console.log(params);
      this.metricSummaryModel.reset();
      this.metricSummaryModel.setParams(params);
      this.metricSummaryModel.rebuild();
    },
    onDashboardInputChange: function(params){
      console.log("dashboard input change");
      this.handleAppEvent(params);
    }


}
