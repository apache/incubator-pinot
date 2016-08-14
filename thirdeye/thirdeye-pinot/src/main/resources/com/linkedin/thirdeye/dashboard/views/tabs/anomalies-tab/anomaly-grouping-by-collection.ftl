<section id="anomaly-grouping-by-collection-template-section">
  <script id="anomaly-grouping-by-collection-template" type="text/x-handlebars-template">
    <div id="anomaly-grouping-by-collection">
      <table id="anomaly-grouping-table" class="anomaly2">
        <thead>
        <tr>
          <td>collection</td>
          <td>#anomalies</td>
        </tr>
        </thead>
        <tbody>
        {{#each this as |groupbyrow rowIndex|}}
        <tr>
          <td>{{groupbyrow/groupBy/collection}}</td>
          <td>{{groupbyrow/value}}</td>
          <td><span class="show-anomaly-grouping uk-button"
                    onclick="renderAnomalySummaryByCollection('{{groupbyrow/groupBy/collection}}')"
                    data-uk-tooltip
                    title="Show Anomalies"><i class="uk-icon-list"></i></span></td>
        </tr>
        {{/each}}
        </tbody>
      </table>
    </div>
  </script>
</section>
