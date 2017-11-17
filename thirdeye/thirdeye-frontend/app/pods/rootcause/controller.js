import Ember from 'ember';
import { makeIterable, filterObject, filterPrefix, toBaselineUrn, toCurrentUrn } from 'thirdeye-frontend/helpers/utils';
import EVENT_TABLE_COLUMNS from 'thirdeye-frontend/mocks/eventTableColumns';
import config from 'thirdeye-frontend/mocks/filterBarConfig';
import _ from 'lodash';

const ROOTCAUSE_TAB_DIMENSIONS = "dimensions";
const ROOTCAUSE_TAB_METRICS = "metrics";
const ROOTCAUSE_TAB_EVENTS = "events";

export default Ember.Controller.extend({

  /**
   * QueryParams that needs to update the url
   */
  queryParams: [
    'granularity',
    'filters',
    'compareMode',
    'anomalyRangeStart',
    'anomalyRangeEnd',
    'analysisRangeStart',
    'analysisRangeEnd'
  ],
  entitiesService: Ember.inject.service('rootcause-entities-cache'), // service

  timeseriesService: Ember.inject.service('rootcause-timeseries-cache'), // service

  aggregatesService: Ember.inject.service('rootcause-aggregates-cache'), // service

  breakdownsService: Ember.inject.service('rootcause-breakdowns-cache'), // service


  selectedUrns: null, // Set

  invisibleUrns: null, // Set

  hoverUrns: null, // Set

  context: null, // { urns: Set, anomalyRange: [2], baselineRange: [2], analysisRange: [2] }

  filterConfig: config, // {}

  settingsConfig: null, // {}

  activeTab: null, // ""

  init() {
    this._super(...arguments);
    this.setProperties({ activeTab: ROOTCAUSE_TAB_DIMENSIONS });
  },

  filteredUrns: null,

  _contextObserver: Ember.observer(
    'context',
    'entities',
    'selectedUrns',
    'entitiesService',
    'timeseriesService',
    'aggregatesService',
    'breakdownsService',
    function () {
      console.log('_contextObserver()');
      const { context, entities, selectedUrns, entitiesService, timeseriesService, aggregatesService, breakdownsService } =
        this.getProperties('context', 'entities', 'selectedUrns', 'entitiesService', 'timeseriesService', 'aggregatesService', 'breakdownsService');

      if (!context || !selectedUrns) {
        return;
      }

      entitiesService.request(context, selectedUrns);
      timeseriesService.request(context, selectedUrns);
      breakdownsService.request(context, selectedUrns);

      const metricUrns = new Set(filterPrefix(Object.keys(entities), 'thirdeye:metric:'));
      const currentUrns = [...metricUrns].map(toCurrentUrn);
      const baselineUrns = [...metricUrns].map(toBaselineUrn);
      aggregatesService.request(context, new Set(currentUrns.concat(baselineUrns)));
    }
  ),

  //
  // Public properties (computed)
  //

  entities: Ember.computed(
    'entitiesService.entities',
    function () {
      console.log('entities()');
      return this.get('entitiesService.entities');
    }
  ),

  timeseries: Ember.computed(
    'timeseriesService.timeseries',
    function () {
      console.log('timeseries()');
      return this.get('timeseriesService.timeseries');
    }
  ),

  aggregates: Ember.computed(
    'aggregatesService.aggregates',
    function () {
      console.log('aggregates()');
      return this.get('aggregatesService.aggregates');
    }
  ),

  breakdowns: Ember.computed(
    'breakdownsService.breakdowns',
    function () {
      console.log('breakdowns()');
      return this.get('breakdownsService.breakdowns');
    }
  ),

  chartSelectedUrns: Ember.computed(
    'entities',
    'selectedUrns',
    'invisibleUrns',
    function () {
      console.log('chartSelectedUrns()');
      const { selectedUrns, invisibleUrns } =
        this.getProperties('selectedUrns', 'invisibleUrns');

      const urns = new Set(selectedUrns);
      [...invisibleUrns].forEach(urn => urns.delete(urn));

      console.log('chartSelectedUrns: urns', urns);
      return urns;
    }
  ),

  eventTableEntities: Ember.computed(
    'entities',
    'filteredUrns',
    function () {
      console.log('eventTableEntities()');
      const { entities, filteredUrns } = this.getProperties('entities', 'filteredUrns');
      console.log("EVENT TABLE ENTITIES: ", filterObject(entities, (e) => filteredUrns.has(e.urn)));
      return filterObject(entities, (e) => filteredUrns.has(e.urn));
    }
  ),

  eventTableColumns: EVENT_TABLE_COLUMNS,

  eventFilterEntities: Ember.computed(
    'entities',
    function () {
      console.log('eventFilterEntities()');
      const { entities } = this.getProperties('entities');
      console.log("printing entities: ", entities);
      console.log("EVENT FILTER ENTITIES: ", filterObject(entities, (e) => e.type == 'event'));
      return filterObject(entities, (e) => e.type == 'event');
    }
  ),

  tooltipEntities: Ember.computed(
    'entities',
    'invisibleUrns',
    'hoverUrns',
    function () {
      const { entities, invisibleUrns, hoverUrns } = this.getProperties('entities', 'invisibleUrns', 'hoverUrns');
      const visibleUrns = [...hoverUrns].filter(urn => !invisibleUrns.has(urn));
      return filterObject(entities, (e) => visibleUrns.has(e.urn));
    }
  ),

  isLoadingEntities: Ember.computed(
    'entitiesService.entities',
    function () {
      console.log('isLoadingEntities()');
      return this.get('entitiesService.pending').size > 0;
    }
  ),

  isLoadingTimeseries: Ember.computed(
    'timeseriesService.timeseries',
    function () {
      console.log('isLoadingTimeseries()');
      return this.get('timeseriesService.pending').size > 0;
    }
  ),

  isLoadingAggregates: Ember.computed(
    'aggregatesService.aggregates',
    function () {
      console.log('isLoadingAggregates()');
      return this.get('aggregatesService.aggregates').size > 0;
    }
  ),

  /**
   * Cache for urns, filtered by event type
   * This is to cache results when toggling between events on filter bar
   * @type {Object}
   * @example
   * {
   *  Holiday: {urns1, urns2},
   *  Deployment: {urns3, urns4}
   * }
   */
  urnsCache: {},

  /**
   * Cache for filters by event type
   * @type {Object}
   * @example
   * {
   *  holiday: {
   *    subFilters: {
   *      country: ['US', 'CA'],
   *      region: ['North America']
   *    }
   *  },
   *  GCN: {...}
   * }
   */
  filtersCache: {},

  //
  // Actions
  //

  actions: {
    onSelection(updates) {
      console.log('onSelection()');
      console.log('onSelection: updates', updates);
      const { selectedUrns } = this.getProperties('selectedUrns');
      Object.keys(updates).filter(urn => updates[urn]).forEach(urn => selectedUrns.add(urn));
      Object.keys(updates).filter(urn => !updates[urn]).forEach(urn => selectedUrns.delete(urn));
      this.set('selectedUrns', new Set(selectedUrns));
    },

    onVisibility(updates) {
      console.log('onVisibility()');
      console.log('onVisibility: updates', updates);
      const { invisibleUrns } = this.getProperties('invisibleUrns');
      Object.keys(updates).filter(urn => updates[urn]).forEach(urn => invisibleUrns.delete(urn));
      Object.keys(updates).filter(urn => !updates[urn]).forEach(urn => invisibleUrns.add(urn));
      this.set('invisibleUrns', new Set(invisibleUrns));
    },

    /**
     * Handles the rootcause_setting change event
     * and updates query params and context
     * @param {Object} newParams new parameters to update
     */
    onContext(context) {
      console.log('settingsOnChange()');
      this.set('context', context);
    },

    onFilter(urns) {
      console.log('filterOnSelect()');
      this.set('filteredUrns', new Set(urns));
    },

    chartOnHover(urns) {
      console.log('chartOnHover()');
      this.set('hoverUrns', new Set(urns));
    },

    loadtestSelectedUrns() {
      console.log('loadtestSelected()');
      const { entities } = this.getProperties('entities');
      this.set('selectedUrns', new Set(Object.keys(entities)));
    },

    addSelectedUrns(urns) {
      console.log('addSelectedUrns()');
      const { selectedUrns } = this.getProperties('selectedUrns');
      makeIterable(urns).forEach(urn => selectedUrns.add(urn));
      this.set('selectedUrns', new Set(selectedUrns));
    },

    removeSelectedUrns(urns) {
      console.log('removeSelectedUrns()');
      const { selectedUrns } = this.getProperties('selectedUrns');
      makeIterable(urns).forEach(urn => selectedUrns.delete(urn));
      this.set('selectedUrns', new Set(selectedUrns));
    },

    addFilteredUrns(urns) {
      console.log('addFilteredUrns()');
      const { filteredUrns } = this.getProperties('filteredUrns');
      makeIterable(urns).forEach(urn => filteredUrns.add(urn));
      this.set('filteredUrns', new Set(filteredUrns));
    },

    removeFilteredUrns(urns) {
      console.log('removeFilteredUrns()');
      const { filteredUrns } = this.getProperties('filteredUrns');
      makeIterable(urns).forEach(urn => filteredUrns.delete(urn));
      this.set('filteredUrns', new Set(filteredUrns));
    },

    addInvisibleUrns(urns) {
      console.log('addInvisibleUrns()');
      const { invisibleUrns } = this.getProperties('invisibleUrns');
      makeIterable(urns).forEach(urn => invisibleUrns.add(urn));
      this.set('invisibleUrns', new Set(invisibleUrns));
    },

    removeInvisibleUrns(urns) {
      console.log('removeInvisibleUrns()');
      const { invisibleUrns } = this.getProperties('invisibleUrns');
      makeIterable(urns).forEach(urn => invisibleUrns.delete(urn));
      this.set('invisibleUrns', new Set(invisibleUrns));
    },

    onHeatmapClick([dimension, subdimension]) {
      // TODO: do something with the call back
      console.log('heatmap click registerd for: ', dimension, subdimension);
    },

    /**
     * Updates the filters cache with newly selected filters
     * @param {String} eventType - event type (i.e. Holiday, GCN)
     * @param {Object} subFilter - representation of subfilter and its values (i.e. {key: "country", value: ["US"]})
     */
    updateFiltersCache(eventType, subFilter) {
      // Initialize object assignments in the cache
      if (!this.filtersCache[eventType]) {
        this.filtersCache[eventType] = {};
      }
      if (!this.filtersCache[eventType].subFilters) {
        this.filtersCache[eventType].subFilters = {};
      }

      if (subFilter && !this.filtersCache[eventType].subFilters[subFilter.key]) {
        this.filtersCache[eventType].subFilters[subFilter.key] = [];
      }

      // Set the subFilters value based on event type
      if (subFilter) {
        this.filtersCache[eventType].subFilters[subFilter.key] = subFilter.value;
      }
    },

    /**
     * Updates the urns cache by event type
     * @param {String} eventType
     * @param {Array} urns - array of urns
     */
    updateUrnsCache(eventType, urns) {
      this.urnsCache[eventType] = urns;
    },

    /**
     * Filters urns based on event type, subfilters selected, and user text from filter search bar
     * @param {String} eventType - type of event
     * @param {Object} subFilter - filter within event (i.e. country, region for Holiday)
     * @param {String} text - user's input from filter search bar
     */
    filterUrns(eventType, subFilter, text) {
      let urns;
      const cachedEvent = this.urnsCache[eventType];
      const filtersCache = this.filtersCache;
      const entities = this.get('eventFilterEntities');

      this.send('updateFiltersCache', eventType, subFilter);

      // If the event is cached and there are no new subfilters selected
      if (!_.isEmpty(cachedEvent) && !subFilter) {
        urns = cachedEvent;
        if (text) {
          urns = urns.filter(urn => entities[urn].label.includes(text ? text.toLowerCase() : ''));
        }
      } else {
        const subFilters = filtersCache[eventType].subFilters;

        // Filter by event type and text
        urns = Object.keys(entities).filter(urn => entities[urn].eventType == eventType
                                                  && entities[urn].label.includes(text ? text.toLowerCase() : ''));

        // Filter by subfilters
        if (!_.isEmpty(subFilters)) {
          urns = urns.filter(urn => {
            let labels = Object.keys(subFilters);
            return labels.some(label => {
              let attributeValues = entities[urn].attributes[label];
              if (attributeValues) {
                if (!subFilters[label].length) {
                  return true;
                } else {
                  return _.intersection(attributeValues, subFilters[label]).length;
                }
              }
            });
          });
        }

        // Update urnsCache
        this.send('updateUrnsCache', eventType, urns);
      }

      // Reset filtered urns
      this.send('filterOnSelect', urns);

    }
  }
});

