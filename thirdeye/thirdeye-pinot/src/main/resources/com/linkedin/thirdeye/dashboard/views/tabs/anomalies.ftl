
<div class="container-fluid">
	<div class="row bg-white row-bordered ">
		<div class="container top-buffer bottom-buffer">
			<div class="col-md-12">
				<div class=row>
					<div class="col-md-12">
						<div style="float: left; width: auto">
							<label for="anomalies-search-input" class="label-large-light">Search By: </label>
						</div>
						<div style="float:left; width: 150px">
							<select id="anomalies-search-mode" style="width:100%">
								<option value="metric">Metric(s)</option>
								<option value="dashboard">Dashboard</option>
								<option value="id">Anomaly ID</option>
							</select>
						</div>
						<div id="anomalies-search-metrics-container" style="overflow:hidden; display: none;">
							<select style="width: 100%" id="anomalies-search-metrics-input" class="label-large-light" multiple="multiple"></select>
						</div>
						<div id="anomalies-search-dashboard-container"  style="overflow:hidden; display: none;">
							<select style="width: 100%;" id="anomalies-search-dashboard-input" class="label-large-light"></select>
						</div>
						<div id="anomalies-search-anomaly-container"  style="overflow:hidden; display: none;">
							<select style="width: 100%;" id="anomalies-search-anomaly-input" class="label-large-light" multiple="multiple"></select>
						</div>
					</div>
				</div>
			</div>
		</div>
	</div>
</div>
<div class="container-fluid bg-white ">
	<div class="row bg-white row-bordered">
		<div class="container top-buffer bottom-buffer">
			<div class="col-md-4">
				<div>
					<label style="font-size: 15px; font-weight: 500">Select time range: </label>
				</div>
				<div>
					<label style="font-size: 11px; font-weight: 500">DATE RANGE(CURRENT) </label>
				</div>
				<div id="anomalies-time-range">
					<i class="glyphicon glyphicon-calendar fa fa-calendar"></i>&nbsp; <span></span> <b class="caret"></b>
				</div>
			</div>
			<div class="col-md-4">
				<div>
					<label style="font-size: 15px; font-weight: 500">Filter by Function: </label>
				</div>
				<div>
					<select class="form-control" id="anomaly-function-dropdown">
					</select>
				</div>
			</div>
			<div class="col-md-2">
				<div>
					<label style="font-size: 15px; font-weight: 500">Anomaly Status: </label>
				</div>
				<div>
					<label class="checkbox-inline"><input type="checkbox" id="status-resolved-checkbox"><span class="label anomaly-status-label">Resolved</span></label>
				</div>
				<div>
					<label class="checkbox-inline"><input type="checkbox" id="status-unresolved-checkbox"><span class="label anomaly-status-label">Unresolved</span></label>
				</div>
			</div>
			<div class="col-md-2" id="apply-button">
				<input type="button" class="btn btn-info" value="Apply" />
			</div>
		</div>
	</div>
</div>


<div>
  <div id='anomaly-spin-area'></div>
	<div id="anomaly-results-place-holder"></div>
</div>
