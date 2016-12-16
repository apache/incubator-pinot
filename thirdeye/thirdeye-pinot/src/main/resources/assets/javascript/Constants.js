function Constants() {

  // constants
  this.METRIC_AUTOCOMPLETE_QUERY_PARAM = 'name';
  this.DASHBOARD_AUTOCOMPLETE_QUERY_PARAM = 'name';
  this.ANOMALY_AUTOCOMPLETE_QUERY_PARAM = 'id';

  // endpoints
  this.METRIC_AUTOCOMPLETE_ENDPOINT = '/data/autocomplete/metric';
  this.DASHBOARD_AUTOCOMPLETE_ENDPOINT = '/data/autocomplete/dashboard';
  this.ANOMALY_AUTOCOMPLETE_ENDPOINT = '/anomalies/autocomplete/anomalyId';
  this.SEARCH_ANOMALIES_METRICIDS = '/anomalies/search/metricIds/';
  this.SEARCH_ANOMALIES_DASHBOARDID = '/anomalies/search/dashboardId/';
  this.SEARCH_ANOMALIES_ANOMALYIDS = '/anomalies/search/anomalyIds/';
  this.UPDATE_ANOMALY_FEEDBACK = '/anomalies/updateFeedback/';

  this.TIMESERIES_DATE_FORMAT = 'YYYY-MM-DD HH:mm';
  this.DETAILS_DATE_FORMAT = 'MMM DD YYYY HH:mm';

  this.MODE_METRIC = 'metric';
  this.MODE_DASHBOARD = 'dashboard';
  this.MODE_ID = 'id';

  this.FEEDBACK_STRING_CONFIRMED_ANOMALY = 'Confirmed Anomaly';
  this.FEEDBACK_STRING_FALSE_ALARM = 'False Alarm';
  this.FEEDBACK_STRING_CONFIRMED_NOT_ACTIONABLE = 'Confirmed - Not Actionable';
  this.FEEDBACK_TYPE_ANOMALY = 'ANOMALY';
  this.FEEDBACK_TYPE_NOT_ANOMALY = 'NOT_ANOMALY';
  this.FEEDBACK_TYPE_ANOMALY_NO_ACTION = 'ANOMALY_NO_ACTION';


}

Constants.prototype = {


};
