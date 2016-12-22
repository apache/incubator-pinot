<html>

<head>



<!-- CSS -->

<link href="../../assets/bootstrap/css/bootstrap.min.css" rel="stylesheet" type="text/css" />
<link href="../../assets/bootstrap/css/bootstrap-theme.min.css" rel="stylesheet" type="text/css" />
<link href="../../assets/jtable/themes/metro/blue/jtable.min.css" rel="stylesheet" type="text/css" />
<link href="../../assets/chosen/chosen.min.css" rel="stylesheet" type="text/css" />
<link rel="stylesheet" href="../../../assets/css/d3.css" />
<link rel="stylesheet" href="../../../assets/css/c3.css" />

<link rel="stylesheet" type="text/css" href="//cdn.jsdelivr.net/bootstrap.daterangepicker/2/daterangepicker.css" />
<link href="../../assets/css/styles.css" rel="stylesheet" type="text/css" />
<link href="https://cdnjs.cloudflare.com/ajax/libs/select2/4.0.3/css/select2.min.css" rel="stylesheet" />
<link href="../../assets/select2/select2-bootstrap.min.css" rel="stylesheet" type="text/css" />
<link href="../../assets/css/thirdeye.css" rel="stylesheet" type="text/css" />

<!-- javascripts -->
<script src="../../assets/js/vendor/jquery.js" type="text/javascript"></script>
<script src="../../assets/bootstrap/js/bootstrap.min.js" type="text/javascript"></script>
<script src="../../assets/jquery-ui/jquery-ui.min.js" type="text/javascript"></script>
<script src="../../assets/lib/handlebars.min.js" type="text/javascript"></script>
<script src="../../assets/js/vendor/vendorplugins.compiled.js" type="text/javascript"></script>
<script src="../../assets/chosen/chosen.jquery.min.js" type="text/javascript"></script>
<script src="../../assets/jtable/jquery.jtable.min.js" type="text/javascript"></script>
<script src="../../assets/js/d3/d3.v3.min.js" charset="utf-8" defer></script>
<script src="../../assets/js/c3/c3.js" defer></script>
<script src="../../assets/spin/spin.js" defer></script>
<script type="text/javascript" src="//cdn.jsdelivr.net/bootstrap.daterangepicker/2/daterangepicker.js"></script>
<script type="text/javascript" src="https://cdnjs.cloudflare.com/ajax/libs/select2/4.0.3/js/select2.min.js" defer></script>

<!-- custom scripts -->
<script src="../../../assets/javascript/libs/page.js" defer></script>
<script src="../../../assets/javascript/libs/handlebarsHelpers.js" defer></script>


<script src="../../assets/js/thirdeye/ingraph-metric-config.js"></script>
<script src="../../assets/js/thirdeye/ingraph-dashboard-config.js"></script>
<script src="../../assets/js/thirdeye/metric-config.js"></script>
<script src="../../assets/js/thirdeye/dataset-config.js"></script>
<script src="../../assets/js/thirdeye/job-info.js"></script>

<script src="../../../assets/js/lib/common/utility.js" defer></script>

<!-- JSON Editor comes here-->
<link rel="stylesheet" href="../../../assets/jsonedit/jsoneditor.min.css" />
<script src="../../../assets/jsonedit/jsoneditor.min.js" defer></script>
<script src="../../../assets/js/lib/entity-editor.js"></script>

<script id="anomalies-template" type="text/x-handlebars-template">
  <#include "tabs/anomalies.ftl"/>
</script>
<script id="anomaly-results-template" type="text/x-handlebars-template">
  <#include "tabs/anomaly-results.ftl"/>
</script>
<script id="dashboard-template" type="text/x-handlebars-template">
  <#include "tabs/dashboard.ftl"/>
</script>
<script id="analysis-template" type="text/x-handlebars-template">
  <#include "tabs/analysis.ftl"/>
</script>
<script id="metric-summary-template" type="text/x-handlebars-template">
	<#include "tabs/dashboard/metric-summary-dashboard.ftl">
</script>
<script id="anomaly-summary-template" type="text/x-handlebars-template">
	<#include "tabs/dashboard/anomaly-summary-dashboard.ftl">
</script>
<script id="wow-summary-template" type="text/x-handlebars-template">
	<#include "tabs/dashboard/wow-summary-dashboard.ftl">
</script>
<script id="timeseries-contributor-template" type="text/x-handlebars-template">
	<#include "tabs/analysis/timeseries-contributor.ftl"/>
</script>
<script id="timeseries-subdimension-legend-template" type="text/x-handlebars-template">
  <#include "tabs/analysis/timeseries-subdimension-legend.ftl"/>
</script>
<script id="contributor-table-details-template" type="text/x-handlebars-template">
  <#include "tabs/analysis/percentage-change-table.ftl"/>
</script>
<script id="dimension-tree-map-template" type="text/x-handlebars-template">
  <#include "tabs/analysis/dimension-tree-map.ftl"/>
</script>
<#include "admin/job-info.ftl"/>
<#include "admin/ingraph-metric-config.ftl"/>
<#include "admin/ingraph-dashboard-config.ftl"/>
<#include "admin/dataset-config.ftl"/>
<#include "admin/metric-config.ftl"/>
<#include "admin/job-info.ftl"/>

<!-- MVC classes -->
<script src="../../../assets/javascript/models/DashboardModel.js"></script>
<script src="../../../assets/javascript/views/DashboardView.js"></script>
<script src="../../../assets/javascript/controllers/DashboardController.js"></script>

<script src="../../../assets/javascript/models/MetricSummaryModel.js"></script>
<script src="../../../assets/javascript/views/MetricSummaryView.js"></script>
<script src="../../../assets/javascript/controllers/MetricSummaryController.js"></script>


<script src="../../../assets/javascript/models/AnomalySummaryModel.js"></script>
<script src="../../../assets/javascript/views/AnomalySummaryView.js"></script>
<script src="../../../assets/javascript/controllers/AnomalySummaryController.js"></script>

<script src="../../../assets/javascript/models/WoWSummaryModel.js"></script>
<script src="../../../assets/javascript/views/WoWSummaryView.js"></script>
<script src="../../../assets/javascript/controllers/WoWSummaryController.js"></script>

<script src="../../../assets/javascript/models/AnomalyResultModel.js"></script>
<script src="../../../assets/javascript/views/AnomalyResultView.js"></script>
<script src="../../../assets/javascript/controllers/AnomalyResultController.js"></script>

<script src="../../../assets/javascript/models/AnalysisModel.js"></script>
<script src="../../../assets/javascript/views/AnalysisView.js"></script>
<script src="../../../assets/javascript/controllers/AnalysisController.js"></script>

<script src="../../../assets/javascript/models/TimeSeriesCompareModel.js"></script>
<script src="../../../assets/javascript/views/TimeSeriesCompareView.js"></script>
<script src="../../../assets/javascript/controllers/TimeSeriesCompareController.js"></script>

<script src="../../../assets/javascript/models/DimensionTreeMapModel.js"></script>
<script src="../../../assets/javascript/views/DimensionTreeMapView.js"></script>
<script src="../../../assets/javascript/controllers/DimensionTreeMapController.js"></script>

<script src="../../../assets/javascript/AppController.js"></script>
<script src="../../../assets/javascript/AppView.js"></script>
<script src="../../../assets/javascript/AppModel.js"></script>

<script src="../../../assets/javascript/Event.js"></script>
<script src="../../../assets/javascript/HashParams.js"></script>
<script src="../../../assets/javascript/HashService.js"></script>
<script src="../../../assets/javascript/DataService.js"></script>
<script src="../../../assets/javascript/Constants.js"></script>

<script type="text/javascript">

var dataService = undefined;
var constants = undefined;
var HASH_SERVICE = undefined;
  $(document).ready(function() {
    constants = new Constants();
    dataService = new DataService();
    HASH_SERVICE = new HashService();
    HASH_SERVICE.init();
    var app = new AppController();
    app.init();
  });
</script>

</head>
<body>

	<div class="container-fullwidth bg-black">
		<div class="row">
			<div class="col-md-12">
				<div class="container">
					<nav class="navbar navbar-inverse" role="navigation">
						<div id="global-navbar" class="navbar-header">
							<button type="button" class="navbar-toggle" data-toggle="collapse" data-target="#bs-example-navbar-collapse-1">
								<span class="sr-only">Toggle navigation</span><span class="icon-bar"></span><span class="icon-bar"></span><span class="icon-bar"></span>
							</button>
							<a class="navbar-brand" href="#dashboard" data-toggle="tab">ThirdEye</a>
						</div>
						<div id="main-tabs" class="collapse navbar-collapse">
							<ul class="nav navbar-nav">
								<li class=""><a href="#dashboard">Dashboard</a></li>
								<li class=""><a href="#anomalies">Anomalies</a></li>
								<li class=""><a href="#analysis">Root Cause Analysis</a></li>
							</ul>

							<ul class="nav navbar-nav navbar-right">
								<li><a href="#">Manage Anomalies</a></li>
								<li class="dropdown"><a href="#" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-haspopup="true" aria-expanded="false">Admin <span class="caret"></span></a>
									<ul class="dropdown-menu">
										<li class=""><a href="#ingraph-metric-config" data-toggle="tab">Ingraph Metric</a></li>
										<li class=""><a href="#ingraph-dashboard-config" data-toggle="tab">Ingraph Dashboard</a></li>
										<li class=""><a href="#dataset-config" data-toggle="tab">Dataset </a></li>
										<li class=""><a href="#metric-config" data-toggle="tab">Metric</a></li>
										<li class=""><a href="#job-info" data-toggle="tab">JobInfo</a></li>
										<li class=""><a href="#entity-editor" data-toggle="tab">Entity Editor</a></li>
									</ul></li>
								<li><a href="#">Sign In</a></li>
							</ul>
						</div>
					</nav>
				</div>
			</div>
		</div>
	</div>

	<div class="tab-content">
		<div class="tab-pane" id="dashboard">
			<div id="dashboard-place-holder"></div>
		</div>
		<div class="tab-pane" id="anomalies">
			<div id="anomalies-place-holder"></div>
		</div>
		<div class="tab-pane" id="analysis">
			<div id="analysis-place-holder"></div>
		</div>
		<div class="tab-pane" id="ingraph-metric-config">
			<div id="ingraph-metric-config-place-holder"></div>
		</div>
		<div class="tab-pane" id="ingraph-dashboard-config">
			<div id="ingraph-dashboard-config-place-holder"></div>
		</div>
		<div class="tab-pane" id="dataset-config">
			<div id="dataset-config-place-holder"></div>
		</div>
		<div class="tab-pane" id="metric-config">
			<div id="metric-config-place-holder"></div>
		</div>
		<div class="tab-pane" id="job-info">
			<div id="job-info-place-holder"></div>
		</div>
		<div class="tab-pane" id="entity-editor">
			<div id="entity-editor-place-holder"></div>
		</div>
	</div>
</body>
</html>
