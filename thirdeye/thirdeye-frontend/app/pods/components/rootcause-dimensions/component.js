/**
 * Component for root cause dimensions table
 * @module components/rootcause-dimensions
 * @property {Array} entities - library of currently loaded RCA entities (contains metric properties we depend on)
 * @property {String} metricUrn - URN of currently loaded metric
 * @property {Object} context - a representation of the current cached state of the RCA page (we only care about its 'analysisRange' and 'compareMode' for now)
 * @property {Array} selectedUrns - the list of currently selected and graphed metrics. We sync this with the table's 'isSelecte' row property.
 * @property {Boolean} isLoading - loading state
 * @example
    {{rootcause-dimensions
      entities=entities
      metricUrn=metricUrn
      context=context
      selectedUrns=selectedUrns
      isLoading=(or isLoadingAggregates isLoadingScores)
      onSelection=(action "onSelection")
    }}
 * @exports rootcause-dimensions
 * @author smcclung
 */

import Component from '@ember/component';
import { once } from '@ember/runloop'
import { isPresent, isEmpty } from '@ember/utils';
import { task, timeout } from 'ember-concurrency';
import { inject as service } from '@ember/service';
import { computed, get, set, getProperties, setProperties } from '@ember/object';
import {
  toCurrentUrn,
  toBaselineUrn,
  hasPrefix,
  toWidthNumber,
  toBaselineRange
} from 'thirdeye-frontend/utils/rca-utils';
import {
  groupedHeaders,
  baseColumns
} from 'thirdeye-frontend/shared/dimensionAnalysisTableConfig';
import d3 from 'd3';

const EXTRA_WIDTH = 0;

export default Component.extend({
  classNames: ['rootcause-dimensions'],
  dimensionsApiService: service('services/api/dimensions'),

  /**
   * Incoming start and end dates (context.analysisRange) for current metric or entity being analyzed
   * @type {Array}
   */
  range: [],

  /**
   * The type of change used by the current metric (context.compareMode)
   * @type {String}
   */
  mode: '',

  /**
   * Incoming collection of loaded entities cached in RCA services
   * @type {Object}
   */
  entities: {},

  /**
   * Incoming metric URN
   * @type {String}
   */
  metricUrn: '',

  /**
   * Incoming metric URN
   * @type {String}
   */
  selectedUrns: '',

    /**
   * Existing metric URN
   * @type {String}
   */
  cachedUrn: '',

  /**
   * Callback on metric selection
   * @type {function}
   */
  onSelection: null, // function (Set, state)

  /**
   * Cached value to be inserted into table header
   * @type {String}
   */
  overallChange: 'NA',

  /**
   * Dimension data for models-table
   * @type {Array}
   */
  dimensionsRawData: [],

  /**
   * Single record cache for previous row's dimension names array
   * @type {Array}
   */
  previousDimensionNames: [],

  /**
   * Override for table classes
   * @type {Object}
   */
  dimensionTableClasses: {
    table: 'rootcause-dimensions-table table-condensed',
    noDataCell: 'rootcause-dimensions-table__column--blank-cell'
  },

  /**
   * Boolean to prevent render pre-fetch
   * @type {Boolean}
   */
  isDimensionDataPresent: false,

  /**
   * Template for custom header row
   * @type {Boolean}
   */
  headerFilteringRowTemplate: 'custom/dimensions-table/header-row-filtering',

  /**
   * loading status for component
   * @type {boolean}
   */
  isLoading: false,

  didReceiveAttrs() {
    this._super(...arguments);

    // We're pulling in the entities list here because its the only way to extract the metric name and dataset
    const { entities, metricUrn, range, mode } = this.getProperties('entities', 'metricUrn', 'range', 'mode');
    // Baseline start/end is dependent on 'compareMode' (WoW, Wo2W, etc)
    const baselineRange = toBaselineRange(range, mode);
    // If metric URN is found in entity list, proceed. Otherwise, we have no metadata to construct the call.
    const metricEntity = entities[metricUrn];

    if (metricEntity) {
      const parsedMetric = metricEntity.label.split('::');
      once(() => {
        get(this, 'fetchDimensionAnalysisData').perform({
          metric: parsedMetric[1],
          dataset: parsedMetric[0],
          currentStart: range[0],
          currentEnd: range[1],
          baselineStart: baselineRange[0],
          baselineEnd: baselineRange[1],
          summarySize: 20,
          oneSideError: false,
          depth: 3
        });
      });
    }
  },

  /**
   * Data for each column of dimensions table
   * @type Array - array of objects, each corresponding to a row in the table
   */
  dimensionTableData: computed(
    'dimensionsRawData.length',
    'selectedUrns',
    'metricUrn',
    function () {
      const { dimensionsRawData, selectedUrns, metricUrn } = this.getProperties('dimensionsRawData', 'selectedUrns', 'metricUrn');
      const toFixedIfDecimal = (number) => (number % 1 !== 0) ? number.toFixed(2) : number;
      const dimensionNames = dimensionsRawData.dimensions || [];
      const dimensionRows = dimensionsRawData.responseRows || [];
      let newDimensionRows = [];

      // Build new dimension array for display as table rows
      if (dimensionRows.length) {
        dimensionRows.forEach((record, index) => {
          let {
            dimensionArr, // Generate array of cell-specific objects for each dimension
            dimensionUrn // Generate URN for each record from dimension names/values
          } = this._generateDimensionUrn(dimensionNames, record);
          let overallContribution = record.contributionToOverallChange;

          // New records of template-ready data
          newDimensionRows.push({
            id: index + 1,
            dimensionUrn,
            dimensionArr,
            names: record.names,
            dimensions: dimensionNames,
            isSelected: selectedUrns.has(dimensionUrn),
            percentageChange: record.percentageChange,
            contributionChange: record.contributionChange,
            contributionToOverallChange: record.contributionToOverallChange,
            cob: `${toFixedIfDecimal(record.currentValue) || 0} / ${toFixedIfDecimal(record.baselineValue) || 0}`,
            elementWidth: this._calculateContributionBarWidth(dimensionRows, record)
          });
        });
      }

      return newDimensionRows;
    }
  ),

  /**
   * Builds the columns array, pushing incoming dimensions into the base columns
   * @type {Array} Array of column objects
   */
  dimensionTableColumns: computed(
    'dimensionsRawData.length',
    'selectedUrns',
    function () {
      const { dimensionsRawData, selectedUrns } = this.getProperties('dimensionsRawData', 'selectedUrns');
      const dimensionNamesArr = dimensionsRawData.dimensions || [];
      const tableBaseClass = 'rootcause-dimensions-table__column';
      let dimensionColumns = [];

      if (dimensionNamesArr.length) {
        dimensionNamesArr.forEach((dimension, index) => {
          let isLastDimension = index === dimensionNamesArr.length - 1;
          dimensionColumns.push({
            disableSorting: true,
            isFirstColumn: index === 0,
            disableFiltering: isLastDimension, // currently overridden by headerFilteringRowTemplate
            propertyName: dimension.camelize(),
            title: dimension.capitalize(),
            isGrouped: !isLastDimension, // no label grouping logic on last dimension
            component: 'custom/dimensions-table/dimension',
            className: `${tableBaseClass} ${tableBaseClass}--med-width ${tableBaseClass}--custom`,
          });
        });
      }
      // Merge the dynamic columns with the preset ones for the complete table
      return dimensionNamesArr.length ? [ ...dimensionColumns, ...baseColumns ] : [];
    }
  ),

  /**
   * Builds the headers array dynamically, based on availability of dimension records
   * @type {Array} Array of grouped headers
   */
  dimensionTableHeaders: computed(
    'dimensionsRawData.length',
    'selectedUrns',
    'overallChange',
    function () {
      const { overallChange, dimensionsRawData }  = getProperties(this, 'overallChange', 'dimensionsRawData');
      const dimensionNames = dimensionsRawData.dimensions || [];
      const tableHeaders = dimensionNames ? groupedHeaders(dimensionNames.length, overallChange) : [];
      return tableHeaders;
    }
  ),

  /**
   * Calculates offsets to use in positioning contribution bars based on aggregated widths
   * @method  _calculateContributionBarWidth
   * @param {Array} dimensionRows - array of dimension records
   * @param {Array} record - single current record
   * @returns {Object} positive and negative offset widths
   * @private
   */
  _calculateContributionBarWidth(dimensionRows, record) {
    const overallChangeValues = dimensionRows.map(row => toWidthNumber(row.contributionToOverallChange));
    const allValuesPositive = overallChangeValues.every(val => val > 0);
    const allValuesNegative = overallChangeValues.every(val => val < 0);
    const widthAdditivePositive = allValuesPositive ? EXTRA_WIDTH : 0;
    const widthAdditiveNegative = allValuesNegative ? EXTRA_WIDTH : 0;

    // Find the largest change value across all rows
    const maxChange = d3.max(dimensionRows.map((row) => {
      return Math.abs(toWidthNumber(row.contributionToOverallChange));
    }));

    // Generate a scale mapping the change value span to a specific range
    const widthScale = d3.scale.linear()
      .domain([0, maxChange])
      .range([0, 100]);

    // Convert contribution value to a width based on our scale
    const contributionValue = toWidthNumber(record.contributionToOverallChange);
    const widthPercent = Math.round(widthScale(Math.abs(contributionValue)));

    // These will be used to set our bar widths/classes in dimensions-table/change-bars component
    return {
      positive: (contributionValue > 0) ? `${widthPercent + widthAdditivePositive}%` : '0%',
      negative: (contributionValue > 0) ? '0%' : `${widthPercent + widthAdditiveNegative}%`
    }
  },

  /**
   * Builds an array of objects with enough data for the dynamic dimension table columns to
   * know how to render each cell. Based on this object 'dimensionArr', we also build a  rich URN
   * containing all the dimensions present in a record in a format that the RCA page understands.
   * @method  _generateDimensionUrn
   * @param {Array} dimensionNames - array of dimension names from root of response object
   * @param {Array} record - single current record
   * @returns {Object} name/value object for dimensions, plus the new URN
   * @private
   */
  _generateDimensionUrn(dimensionNames, record) {
    // We cache the value of the previous row's dimension values for row grouping
    const previousDimensionNames = get(this, 'previousDimensionNames');
    // We want to display excluded dimensions with value '(ALL)-' and having 'otherDimensionValues' prop
    const otherValues = isPresent(record, 'otherDimensionValues') ? record.otherDimensionValues : null;

    // Array to help dimension column component decide what to render in each cell
    const dimensionArr = dimensionNames.map((name, index) => {
      let dimensionValue = record.names[index];
      return {
        label: name.camelize(),
        value: dimensionValue || '-',
        isHidden: dimensionValue === previousDimensionNames[index], // if its a repeated value, hide it
        otherValues: dimensionValue === '(ALL)-' ? otherValues : null
      };
    });

    // Create a string version of dimension name/value pairs
    const encodedDimensions = isPresent(dimensionArr) ? dimensionArr.map((dObj) => {
      return encodeURIComponent(`${dObj.label}=${dObj.value}`);
    }).join(':') : '';
    // Append dimensions string to metricUrn. This will be sent to the graph legend for display
    const dimensionUrn = `${get(this, 'metricUrn')}:${encodedDimensions}`;
    // Now save the current record names as 'previous'
    set(this, 'previousDimensionNames', record.names);

    return { dimensionArr, dimensionUrn };
  },

  actions: {
    /**
     * Triggered on row selection
     * Updates the currently selected urns based on user selection on the table
     * @param {Object} eventObj
     */
    displayDataChanged (eventObj) {
      if (isEmpty(eventObj.selectedItems)) { return; }
      const { selectedUrns, onSelection } = this.getProperties('selectedUrns', 'onSelection');
      const selectedRows = eventObj.selectedItems;
      if (!onSelection) { return; }
      const selectedRecord = selectedRows[0];
      const urn = selectedRecord.dimensionUrn;
      const state = !selectedRecord.isSelected;
      const updates = {[urn]: state};
      if (hasPrefix(urn, 'thirdeye:metric:')) {
        updates[toCurrentUrn(urn)] = state;
        updates[toBaselineUrn(urn)] = state;
      }
      onSelection(updates);
    }
  },

  /**
   * Concurrency task to call for either cached or new dimension data from store
   * @method fetchDimensionAnalysisData
   * @param {Object} dimensionObj - required params for query
   * @returns {Generator object}
   * @private
   */
  fetchDimensionAnalysisData: task(function * (dimensionObj) {
    const dimensionsPayload = yield this.get('dimensionsApiService').queryDimensionsByMetric(dimensionObj);
    const dimensionNames = dimensionsPayload.dimensions || [];
    const ratio = dimensionsPayload.globalRatio;
    const convertedChangeRate = (ratio) => `${Math.abs((ratio -1) * 100).toFixed(2)}%`;

    this.setProperties({
      isLoading: false,
      dimensionsRawData: dimensionsPayload,
      cachedUrn: get(this, 'metricUrn'),
      isDimensionDataPresent: true,
      overallChange: ratio ? convertedChangeRate(ratio) : 'N/A'
    });

  }).cancelOn('deactivate').drop()
});
