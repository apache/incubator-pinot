import Ember from 'ember';
import { checkStatus, toBaselineUrn, toBaselineRange, toFilters, toFilterMap } from 'thirdeye-frontend/helpers/utils';
import fetch from 'fetch';
import _ from 'lodash';

export default Ember.Service.extend({
  timeseries: null, // {}

  context: null, // {}

  pending: null, // Set

  errors: null, // Set({ urn, error })

  init() {
    this.setProperties({ timeseries: {}, context: {}, pending: new Set(), errors: new Set() });
  },

  clearErrors() {
    this.setProperties({ errors: new Set() });
  },

  request(requestContext, urns) {
    const { context, timeseries, pending } = this.getProperties('context', 'timeseries', 'pending');

    const metrics = [...urns].filter(urn => urn.startsWith('frontend:metric:'));

    // TODO eviction on cache size limit

    let missing;
    let newPending;
    let newTimeseries;
    if(!_.isEqual(context, requestContext)) {
      // new analysis range: evict all, reload, keep stale copy of incoming
      missing = metrics;
      newPending = new Set(metrics);
      newTimeseries = metrics.filter(urn => urn in timeseries).reduce((agg, urn) => { agg[urn] = timeseries[urn]; return agg; }, {});

    } else {
      // same context: load missing
      missing = metrics.filter(urn => !(urn in timeseries) && !pending.has(urn));
      newPending = new Set([...pending].concat(missing));
      newTimeseries = timeseries;
    }

    this.setProperties({ context: _.cloneDeep(requestContext), timeseries: newTimeseries, pending: newPending });

    if (_.isEmpty(missing)) {
      // console.log('rootcauseTimeseriesService: request: all metrics up-to-date. ignoring.');
      return;
    }

    // metrics
    const metricUrns = missing.filter(urn => urn.startsWith('frontend:metric:current:'));
    metricUrns.forEach(urn => this._fetchSlice(urn, requestContext.analysisRange, requestContext, (t) => t));

    // baselines
    const baselineRange = toBaselineRange(requestContext.analysisRange, requestContext.compareMode);
    const baselineOffset = requestContext.analysisRange[0] - baselineRange[0];
    const offsetFunc = (timeseries) => this._convertToBaseline(timeseries, baselineOffset);
    const baselineUrns = missing.filter(urn => urn.startsWith('frontend:metric:baseline:'));
    baselineUrns.forEach(urn => this._fetchSlice(urn, baselineRange, requestContext, offsetFunc));
  },

  _complete(requestContext, incoming) {
    const { context, pending, timeseries } = this.getProperties('context', 'pending', 'timeseries');

    // only accept latest result
    if (!_.isEqual(context, requestContext)) {
      // console.log('rootcauseTimeseriesService: _complete: received stale result. ignoring.');
      return;
    }

    const newPending = new Set([...pending].filter(urn => !(urn in incoming)));
    const newTimeseries = Object.assign({}, timeseries, incoming);

    this.setProperties({ timeseries: newTimeseries, pending: newPending });
  },

  _extractTimeseries(json, urn) {
    const timeseries = {};
    Object.keys(json).forEach(range =>
      Object.keys(json[range]).filter(sid => sid != 'timestamp').forEach(sid => {
        const jrng = json[range];

        timeseries[urn] = {
          timestamps: jrng.timestamp,
          values: jrng[sid]
        };
      })
    );
    return timeseries;
  },

  _convertToBaseline(timeseries, offset) {
    const baseline = {};
    Object.keys(timeseries).forEach(urn => {
      const baselineUrn = toBaselineUrn(urn);
      baseline[baselineUrn] = {
        values: timeseries[urn].values,
        timestamps: timeseries[urn].timestamps.map(t => t + offset)
      };
    });
    return baseline;
  },

  _fetchSlice(urn, range, context, offsetFunc) {
    const metricId = urn.split(':')[3];
    const metricFilters = toFilters([urn]);
    const filters = toFilterMap(metricFilters);

    const filterString = encodeURIComponent(JSON.stringify(filters));

    const url = `/timeseries/query?metricIds=${metricId}&ranges=${range[0]}:${range[1]}&filters=${filterString}&granularity=${context.granularity}`;
    return fetch(url)
      .then(checkStatus)
      .then(res => this._extractTimeseries(res, urn))
      .then(res => offsetFunc(res))
      .then(res => this._complete(context, res))
      .catch(error => this._handleError(urn, error));
  },

  _handleError(urn, error) {
    const { errors, pending } = this.getProperties('errors', 'pending');

    const newError = urn;
    const newErrors = new Set([...errors, newError]);

    const newPending = new Set(pending);
    newPending.delete(urn);

    this.setProperties({ errors: newErrors, pending: newPending });
  }
});
