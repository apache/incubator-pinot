import Ember from 'ember';
import { checkStatus, toBaselineRange, toFilters, toFilterMap } from 'thirdeye-frontend/helpers/utils';
import fetch from 'fetch';
import _ from 'lodash';

export default Ember.Service.extend({
  breakdowns: null, // {}

  context: null, // {}

  pending: null, // Set

  errors: null, // Set({ urn, error })

  init() {
    this.setProperties({breakdowns: {}, context: {}, pending: new Set(), errors: new Set() });
  },

  clearErrors() {
    this.setProperties({ errors: new Set() });
  },

  request(requestContext, urns) {
    const { context, breakdowns, pending } = this.getProperties('context', 'breakdowns', 'pending');

    const metrics = [...urns].filter(urn => urn.startsWith('frontend:metric:'));

    // TODO eviction on cache size limit

    let missing;
    let newPending;
    let newBreakdowns;
    if(!_.isEqual(context, requestContext)) {
      // new analysis range: evict all, reload, keep stale copy of incoming
      missing = metrics;
      newPending = new Set(metrics);
      newBreakdowns = metrics.filter(urn => urn in breakdowns).reduce((agg, urn) => { agg[urn] = breakdowns[urn]; return agg; }, {});

    } else {
      // same context: load missing
      missing = metrics.filter(urn => !(urn in breakdowns) && !pending.has(urn));
      newPending = new Set([...pending].concat(missing));
      newBreakdowns = breakdowns;
    }

    this.setProperties({ context: _.cloneDeep(requestContext), breakdowns: newBreakdowns, pending: newPending });

    if (_.isEmpty(missing)) {
      // console.log('rootcauseBreakdownsService: request: all metrics up-to-date. ignoring.');
      return;
    }

    // metrics
    const metricUrns = missing.filter(urn => urn.startsWith('frontend:metric:current:'));
    metricUrns.forEach(urn => this._fetchSlice(urn, requestContext.anomalyRange, requestContext));

    // baselines
    const baselineRange = toBaselineRange(requestContext.anomalyRange, requestContext.compareMode);
    const baselineUrns = missing.filter(urn => urn.startsWith('frontend:metric:baseline:'));
    baselineUrns.forEach(urn => this._fetchSlice(urn, baselineRange, requestContext));
  },

  _complete(requestContext, incoming) {
    const { context, pending, breakdowns } = this.getProperties('context', 'pending', 'breakdowns');

    // only accept latest result
    if (!_.isEqual(context, requestContext)) {
      // console.log('rootcauseBreakdownsService: _complete: received stale result. ignoring.');
      return;
    }

    const newPending = new Set([...pending].filter(urn => !(urn in incoming)));
    const newBreakdowns = Object.assign({}, breakdowns, incoming);

    this.setProperties({ breakdowns: newBreakdowns, pending: newPending });
  },

  _extractBreakdowns(incoming, urn) {
    // NOTE: only supports single time range
    const breakdowns = {};
    Object.keys(incoming).forEach(range => {
      Object.keys(incoming[range]).forEach(mid => {
        breakdowns[urn] = incoming[range][mid];
      });
    });
    return breakdowns;
  },

  _fetchSlice(urn, range, context) {
    const metricId = urn.split(':')[3];
    const metricFilters = toFilters([urn]);
    const filters = toFilterMap(metricFilters);

    const filterString = encodeURIComponent(JSON.stringify(filters));

    const url = `/aggregation/query?metricIds=${metricId}&ranges=${range[0]}:${range[1]}&filters=${filterString}&rollup=20`;
    return fetch(url)
      .then(checkStatus)
      .then(res => this._extractBreakdowns(res, urn))
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
