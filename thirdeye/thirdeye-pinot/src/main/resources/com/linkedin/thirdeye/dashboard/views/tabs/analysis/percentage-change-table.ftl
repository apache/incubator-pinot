<div class="col-md-12">
  <div class="table-responsive">
    <table class="table table-bordered analysis-table">
      <thead>
      <tr>
        <th></th>
        {{#each this.subDimensionContributionDetails.timeBucketsCurrent as |timestamp timeIndex|}}
        <th>{{displayMonthDayHour timestamp}}</th>
        {{/each}}
      </tr>
      </thead>
      <tbody>
      {{#if_eq this.showCumulativeChecked true}}
      {{#each this.subDimensionContributionDetails.cumulativePercentageChange as
      |cumulativePercentageChangeArr cKeyIndex|}}
      <tr>
        <td>{{cKeyIndex}}</td>
        {{#each cumulativePercentageChangeArr as |cPercentageChange cidx|}}
        <td style="background-color: {{computeColor cPercentageChange}};color: {{computeTextColor cPercentageChange}};"
            id="{{cKeyIndex}}-{{cidx}}">
          <div class="row">
            {{#if_eq this.showDetailsChecked true}}
            <div class="col-md-4">
              cumCurrent
            <#--{{this.subDimensionContributionDetails.cumulativeCurrentValues[cKeyIndex][cidx]}}-->
            </div>
            <div class="col-md-4">
              cumBaseline
            <#--{{this.subDimensionContributionDetails.cumulativeBaselineValues[cKeyIndex][cidx]}}-->
            </div>
            {{/if_eq}}
            <div class="col-md-4">
              {{cPercentageChange}}%
            </div>
          </div>
        </td>
        {{/each}}
      </tr>
      {{/each}}
      {{/if_eq}}


      {{#if_eq this.showCumulativeChecked false}}
      {{#each this.subDimensionContributionDetails.percentageChange as |percentageChangeArr
      keyIndex|}}
      <tr>
        <td>{{keyIndex}}</td>
        {{#each percentageChangeArr as |percentageChange idx|}}
        <td style="background-color: {{computeColor percentageChange}};color: {{computeTextColor percentageChange}};"
            id="{{keyIndex}}-{{idx}}">
          <div class="row">
            {{#if_eq this.showDetailsChecked true}}
            <div class="col-md-4">
              current
            <#--{{this.subDimensionContributionDetails.currentValues[keyIndex][cidx]}}-->
            </div>
            <div class="col-md-4">
              baseline
            <#--{{this.subDimensionContributionDetails.baselineValues[keyIndex][cidx]}}-->
            </div>
            {{/if_eq}}
            <div class="col-md-4">
              {{percentageChange}}%
            </div>
          </div>

        </td>
        {{/each}}
      </tr>
      {{/each}}
      {{/if_eq}}

      </tbody>
    </table>
  </div>
</div>
