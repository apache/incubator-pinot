function AnomalyResultController(parentController) {
  this.parentController = parentController;
  this.anomalyResultModel = new AnomalyResultModel();
  this.anomalyResultView = new AnomalyResultView(this.anomalyResultModel);

//  this.anomalyResultView.metricChangeEvent.attach(this.metricChangeEventHandler.bind(this));
//  this.anomalyResultView.hideDataRangePickerEvent.attach(this.hideDataRangePickerEventHandler.bind(this));
  this.anomalyResultView.applyButtonEvent.attach(this.applyButtonEventHandler.bind(this));
  this.anomalyResultView.rootCauseAnalysisButtonClickEvent.attach(this.rootCauseAnalysisButtonClickEventHandler.bind(this));
  this.anomalyResultView.showDetailsLinkClickEvent.attach(this.showDetailsLinkClickEventHandler.bind(this));
  this.anomalyResultView.anomalyFeedbackSelectEvent.attach(this.anomalyFeedbackSelectEventHandler.bind(this));

  this.anomalyResultView.init();
}

AnomalyResultController.prototype = {
  handleAppEvent: function (params) {
    console.log("Inside handle app event of AnomalyResultController");
    this.anomalyResultModel.reset();
    this.anomalyResultModel.setParams(params);
    this.anomalyResultModel.rebuild();

    this.anomalyResultView.render();
  },
  handleAnomalyFeedbackChangeEvent: function(params) {
    this.anomalyResultModel.setParams(params);
    this.anomalyResultModel.updateAnomalyFeedback();
  },
  applyButtonEventHandler: function(sender, args) {
    console.log("Apply button Event");
    this.handleAppEvent(args);

  },
  rootCauseAnalysisButtonClickEventHandler: function (sender, args) {
    console.log("received root cause analysis button click event at AnomalyResultController");
    console.log(args);

    // Send this event and the args to parent controller, to route to AnalysisController
  },
  showDetailsLinkClickEventHandler: function (sender, args) {
    console.log("received show details link click event at AnomalyResultController");
    console.log(args);

    // Send this event and the args to parent controller, to route to details page
  },
  anomalyFeedbackSelectEventHandler: function(sender, args) {
    console.log("received anomaly feedback select event at AnomalyResultController");
    this.handleAnomalyFeedbackChangeEvent(args);
  }
};
