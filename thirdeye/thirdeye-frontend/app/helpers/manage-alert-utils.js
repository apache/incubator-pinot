import { helper } from '@ember/component/helper';
import moment from 'moment';

/**
 * Handles types and defaults returned from eval/projected endpoints
 * @param {Number|String} metric - number or string like 'NaN', 'Infinity'
 * @param {Boolean} isPercentage - shall we treat this as a % or a whole number?
 * @returns {Object}
 */
export function formatEvalMetric(metric, isPercentage = false) {
  const isWhole = Number(metric) % 1 === 0;
  let shown = (metric === 'Infinity') ? metric : 'N/A';
  if (isFinite(metric)) {
    if (isPercentage) {
      shown = isWhole ? metric * 100 : (metric * 100).toFixed(1);
    } else {
      shown = isWhole ? metric : metric.toFixed(1);
    }
  }
  return shown;
}

/**
 * Pluralizes and formats the anomaly range duration string
 * @param {Number} time
 * @param {String} unit
 * @returns {String}
 */
export function pluralizeTime(time, unit) {
  const unitStr = time > 1 ? unit + 's' : unit;
  return time ? time + ' ' + unitStr : '';
}

/**
 * Split array of all anomalyIds into buckets of bucketSize
 * @param {Array} anomalyIds - array of anomaly Ids
 * @param {Number} bucketSize - number of anomalies per group
 * @returns {Array}
 */
export function toIdGroups(anomalyIds, bucketSize = 10) {
  const idGroups = anomalyIds.map((item, index) => {
    return (index % bucketSize === 0) ? anomalyIds.slice(index, index + bucketSize) : null;
  }).filter(item => item);
  return idGroups;
}

/**
 * Derives and formats extra anomaly properties such as duration
 * @param {Array} anomalies - array of raw anomalies
 * @returns {Array}
 */
export function enhanceAnomalies(rawAnomalies) {
  const newAnomalies = [];
  const anomaliesPresent = rawAnomalies && rawAnomalies.length;
  // De-dupe raw anomalies, extract only the good stuff (anomalyDetailsList)
  const anomalies = anomaliesPresent ? [].concat(...rawAnomalies.map(data => data.anomalyDetailsList)) : [];

  // Loop over all anomalies to configure display settings
  anomalies.forEach((anomaly) => {
    let dimensionList = [];
    const startMoment = moment(anomaly.anomalyStart);
    const endMoment = moment(anomaly.anomalyEnd);
    const anomalyDuration = moment.duration(endMoment.diff(startMoment));
    const days = anomalyDuration.get("days");
    const hours = anomalyDuration.get("hours");
    const minutes = anomalyDuration.get("minutes");
    const durationArr = [pluralizeTime(days, 'day'), pluralizeTime(hours, 'hour'), pluralizeTime(minutes, 'minute')];

    // Placeholder: ChangeRate will not be calculated on front-end
    const changeRate = (anomaly.current && anomaly.baseline)
      ? ((anomaly.current - anomaly.baseline) / anomaly.baseline * 100).toFixed(2) : 0;

    // We want to display only non-zero duration values in our table
    const noZeroDurationArr = _.remove(durationArr, function(item) {
      return Ember.isPresent(item);
    });

    // Set 'not reviewed' label
    if (!anomaly.anomalyFeedback) {
      anomaly.anomalyFeedback = 'Not reviewed yet';
    }

    // Add missing properties
    Object.assign(anomaly, {
      changeRate,
      shownChangeRate: changeRate,
      startDateStr: moment(anomaly.anomalyStart).format('MMM D, hh:mm A'),
      durationStr: noZeroDurationArr.join(', '),
      severityScore: (anomaly.current/anomaly.baseline - 1).toFixed(2),
      shownCurrent: anomaly.current,
      shownBaseline: anomaly.baseline,
      showResponseSaved: false,
      shorResponseFailed: false
    });

    // Create a list of all available dimensions for toggling. Also massage dimension property.
    if (anomaly.anomalyFunctionDimension) {
      let dimensionObj = JSON.parse(anomaly.anomalyFunctionDimension);
      let dimensionStrArr = [];
      for (let dimension of Object.keys(dimensionObj)) {
        let dimensionKey = dimension.dasherize();
        let dimensionVal = dimensionObj[dimension].join(',');
        dimensionList.push({ dimensionKey, dimensionVal });
        dimensionStrArr.push(`${dimensionKey}:${dimensionVal}`);
      }
      let dimensionString = dimensionStrArr.join(' & ');
      Object.assign(anomaly, { dimensionList, dimensionString });
    }

    newAnomalies.push(anomaly);
  });

  return newAnomalies;
}

/**
 * Generates time range options for selection in the self-serve UI
 * @method setUpTimeRangeOptions
 * @param {Array} datesKeys - array of keys used to generate time ranges
 * @param {String} duration - the selected time span that is default
 * @return {Array}
 */
export function setUpTimeRangeOptions(datesKeys, duration) {
  const newRangeArr = [];

  const defaultCustomRange = {
    name: 'Custom',
    value: 'custom',
    start: null,
    isActive: !datesKeys.includes(duration)
  }

  const dateKeyMap = new Map([
    [ '1m', ['Last 30 Days', 1, 'month'] ],
    [ '3m', ['3 Months', 3, 'month'] ],
    [ '2w', ['Last 2 Weeks', 2, 'week'] ],
    [ '1w', ['Last Week', 1, 'week'] ]
  ]);

  datesKeys.forEach((value) => {
    let currVal = dateKeyMap.get(value);
    let name = currVal[0];
    let start = moment().subtract(currVal[1], currVal[2]).endOf('day').utc()
    let isActive = duration === value;
    newRangeArr.push({ name, value, start, isActive });
  });

  newRangeArr.push(defaultCustomRange);

  return newRangeArr;
}

/**
 * Returns a sample JSON anomaly eval object
 * @returns {Object}
 */
export function evalObj() {
  return {
    userReportAnomaly: 0,
    totalResponses: 0,
    trueAnomalies: 0,
    recall: NaN,
    totalAlerts: 6,
    responseRate: 0.0,
    precision: 0.0,
    falseAlarm: 0,
    newTrend: 0,
    weightedPrecision: 0.0
  };
}

/**
 * If a dimension has been selected, the metric data object will contain subdimensions.
 * This method calls for dimension ranking by metric, filters for the selected dimension,
 * and returns a sorted list of graph-ready dimension objects.
 * @method getTopDimensions
 * @param {Object} dimensionObj - the object containing available subdimension for current metric
 * @param {Array} scoredDimensions - array of dimensions scored by relevance
 * @param {String} selectedDimension - the user-selected dimension to graph
 * @return {RSVP Promise}
 */
export function getTopDimensions(dimensionObj = {}, scoredDimensions, selectedDimension) {
  const maxSize = 5;
  const colors = ['orange', 'teal', 'purple', 'red', 'green', 'pink'];
  let dimensionList = [];
  let colorIndex = 0;

  if (selectedDimension) {
    const filteredDimensions =  _.filter(scoredDimensions, (dimension) => {
      return dimension.label.split('=')[0] === selectedDimension;
    });
    const topDimensions = filteredDimensions.sortBy('score').reverse().slice(0, maxSize);
    const topDimensionLabels = [...new Set(topDimensions.map(key => key.label.split('=')[1]))];

    // Build the array of subdimension objects for the selected dimension
    topDimensionLabels.forEach((subDimension) => {
      if (dimensionObj[subDimension] && subDimension !== '') {
        dimensionList.push({
          name: subDimension,
          metricName: subDimension,
          color: colors[colorIndex],
          baselineValues: dimensionObj[subDimension].baselineValues,
          currentValues: dimensionObj[subDimension].currentValues
        });
        colorIndex++;
      }
    });
  }

  // Return sorted list of dimension objects
  return dimensionList;
}

/**
 * Data needed to render the stats 'cards' above the anomaly graph for a given alert
 * @param {Object} alertEvalMetrics - contains the alert's performance data
 * @param {String} mode - the originating route
 * @param {String} severity - the severity threshold entered
 * @param {Boolean} isPercent - suffix associated with the selected severity mode
 * @returns {Array}
 */
export function buildAnomalyStats(alertEvalMetrics, mode, severity = '30', isPercent = true) {
  const tooltip = false;
  const severityUnit = isPercent ? '%' : '';

  const responseRateObj = {
    title: 'Response Rate',
    key: 'responseRate',
    units: '%',
    tooltip,
    hideProjected: true,
    text: '% of anomalies that are reviewed.'
  };

  const anomalyStats = [
    {
      title: 'Number of anomalies',
      key: 'totalAlerts',
      text: 'Estimated average number of anomalies',
      tooltip,
    },
    {
      title: 'Precision',
      key: 'precision',
      units: '%',
      tooltip,
      text: 'Among all anomalies detected, the % of them that are true.'
    },
    {
      title: 'Recall',
      key: 'recall',
      units: '%',
      tooltip,
      text: 'Among all anomalies that happened, the % of them detected by the system.'
    },
    {
      title: `MTTD for > ${severity}${severityUnit} change`,
      key: 'mttd',
      units: 'hrs',
      tooltip,
      text: `Minimum time to detect for anomalies with > ${severity}${severityUnit} change`
    }
  ];

  if (mode === 'explore') {
    anomalyStats.splice(1, 0, responseRateObj);
  }

  anomalyStats.forEach((stat) => {
    let origData = alertEvalMetrics.current[stat.key];
    let newData = alertEvalMetrics.projected[stat.key];
    let isPercentageMetric = stat.units === '%';
    let isTotal = stat.key === 'totalAlerts';
    stat.showProjected = mode === 'explore';
    stat.value = isTotal ? origData : formatEvalMetric(origData, isPercentageMetric);
    stat.projected = isTotal ? newData : formatEvalMetric(newData, isPercentageMetric);
    stat.valueUnits = isFinite(origData) ? stat.units : null;
    stat.projectedUnits = isFinite(newData) ? stat.units : null;
    stat.showDirectionIcon = isFinite(origData) && isFinite(newData) && origData !== newData;
    stat.direction = stat.showDirectionIcon && origData > newData ? 'bottom' : 'top';
  });

  return anomalyStats;
}

export default helper(
  formatEvalMetric,
  toIdGroups,
  pluralizeTime,
  enhanceAnomalies,
  getTopDimensions,
  setUpTimeRangeOptions,
  buildAnomalyStats,
  evalObj
);
