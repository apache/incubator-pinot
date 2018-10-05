import Component from '@ember/component';
import fetch from 'fetch';
import { toBaselineUrn, toCurrentUrn, filterPrefix } from 'thirdeye-frontend/utils/rca-utils';
import { selfServeApiCommon } from 'thirdeye-frontend/utils/api/self-serve';
import { task, timeout } from 'ember-concurrency';
import _ from 'lodash';
import { checkStatus } from 'thirdeye-frontend/utils/utils';
import { get, computed } from '@ember/object';
export default Component.extend({
  classNames: ['rootcause-select-metric-dimension'],

  selectedUrn: null, // ""

  onSelection: null, // function (metricUrn)

  placeholder: 'Search for a Metric',

  //
  // internal
  //
  mostRecentSearch: null, // promise

  selectedUrnCache: null, // ""

  selectedMetric: null, // {}

  /**
   * @summary Concurrency task that triggers returning the selected metrics as recommended list
   * @return {Array} array of groupName and options list
   * @example
     [
       { groupName: 'Selected Metrics', options: [{alias:'one', id: '1'}, {alias:'two', id: '2'}, {alias:'three', id: '3'}] }
     ]
   */
  recommendedMetrics: computed(
    'entities', 'selectedUrns',
    function() {
      const { selectedUrns, entities } = this.getProperties('selectedUrns', 'entities');
      const result = filterPrefix(selectedUrns, 'thirdeye:metric:')
        .map((urn) => {
          const entity = entities[urn];
          const agg = entity ? { alias: entity.label, id: entity.urn.split(':')[2] } : {};
          return agg;
        });

      return [
        { groupName: 'Selected Metrics', options: _.sortBy(result, (row) => row.alias) || [] }
      ];
    }
  ),

  /**
   * Ember concurrency task that triggers the metric autocomplete
   */
  searchMetrics: task(function* (metric) {
    yield timeout(1000);
    return fetch(selfServeApiCommon.metricAutoComplete(metric))
      .then(checkStatus);
  }),

  didReceiveAttrs() {
    this._super(...arguments);

    const {
      selectedUrn,
      selectedUrnCache
    } = this.getProperties('selectedUrn', 'selectedUrnCache');

    if (!_.isEqual(selectedUrn, selectedUrnCache)) {
      this.set('selectedUrnCache', selectedUrn);

      if (selectedUrn) {
        const url = `/data/metric/${selectedUrn.split(':')[2]}`;
        fetch(url)
          .then(checkStatus)
          .then((res) => {
            this.set('selectedMetric', res);
          });
      } else {
        this.set('selectedMetric', null);
      }
    }
  },

  actions: {
    /**
     * Action handler for metric recomendations on currently selected metrics
     * @param {Object} metric
     */
    recommendedMetricsAction(metric) {
      this.send('onChange', metric);
    },
    /**
     * Action handler for metric search changes
     * @param {Object} metric
     */
    onChange(metric) {
      const { onSelection } = this.getProperties('onSelection');
      if (!onSelection) { return; }

      const { id } = metric;
      if (!id) { return; }

      const metricUrn = `thirdeye:metric:${id}`;
      const updates = { [metricUrn]: true, [toBaselineUrn(metricUrn)]: true, [toCurrentUrn(metricUrn)]: true };

      onSelection(updates);
    },

    /**
     * Performs a search task while cancelling the previous one
     * @param {Array} metrics
     */
    onSearch(metrics) {
      const lastSearch = this.get('mostRecentSearch');
      if (lastSearch) {
        lastSearch.cancel();
      }

      const task = this.get('searchMetrics');
      const taskInstance = task.perform(metrics);
      this.set('mostRecentSearch', taskInstance);

      return taskInstance;
    }
  }
});
