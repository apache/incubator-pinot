<table border="0" cellpadding="0" cellspacing="0" style="padding:0px; width:100%; font-family: 'Proxima Nova','Arial', 'Helvetica Neue',Helvetica, sans-serif;font-size:12px;line-height:normal;margin:0 auto; padding:0px 0px 10px 0px; background-color: #fff;">
  <tr style="height:50px;background: #000">
    <td align="left" style="padding: 10px;height:50px;">
      <div style="height: 35px;width: 45px;display: inline-block; background-image: url(data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAACsAAAAiCAQAAABsW+iDAAAA3klEQVRIx83Wuw3CMBAG4BshaahZIBUjsAKNB8gopqaiTpsRGMEjsANI3ADI+CElcTgKuLuQsyIrUvTJ+n1WDL7yJ8+pLggwH8BEM0ywEqXEbpdfLfZYA2CNvSCLDoZCJ8faCWt12HbtISht2Z+OA97QpXH9kh2zLd8D9cR2knyNZwnWxszLmvXKLyxdRbcIsgcBNgQRt+uCuzFhNotH6tDwWafMvn/FYB93FbZo0cXZxps0Gkk2opkPsBxr0rPPszRr/EaDBenVfsqW/XegO2F9dzCC7XQuohUTJq/NL1/k/oovlOCIAAAAAElFTkSuQmCC); background-position: 0 0;
            background-repeat: no-repeat; " ></div>
      <span style="width:200px;color:#fff;font-size:20px;display:inline;position:relative;top:-3px;font-weight:200;font-family: 'Proxima Nova','Arial', 'Helvetica Neue',Helvetica, sans-serif;">ThirdEye</span>
    </td>
  </tr>
  <tr>
    <td style="min-height: 30px; padding:10px 15px; font-family: 'Proxima Nova','Arial', 'Helvetica Neue',Helvetica, sans-serif;font-size:16px;font-weight:300; width:100%;display:inline;">
      <p style="margin-left:15px">Hello,</p>
      <p style="margin-left: 15px">You are receiving this email because you have subscribed to ThirdEye Anomaly detection service for '${collection}:${metric}'.<br/></p>
      <p style="margin-left: 15px"> ThirdEye has analyzed your dataset for time range ${dateFormat(startTime)} to ${dateFormat(endTime)} (${timeZone}) and has detected <b>${anomalyCount} ${(anomalyCount == 1)?string("anomaly", "anomalies")}.</b> </p>
    <#if (anomalyCount > 0)>
      <p style="margin-left: 15px">Below is the full list of anomalies detected during this time period.</p>
    </#if>
    </td>
  </tr>
<#if (groupedAnomalyResults?has_content)>
  <tr>
    <td>
      <table align="center" border="1" style="width:100%;border-collapse: collapse; border-spacing: 0 margin-bottom: 15px; border-color:#ddd;">
        <tr>
          <th style="padding:5px 3px">Dimensions</th>
          <th>No.</th>
          <th>Time (${timeZone})</th>
        <#-- <th>End</th> -->
          <th>Reason</th>
          <th>Function</th>
        </tr>
        <#assign anomalySequenceIndex = 1>
        <#list groupedAnomalyResults?keys as dimensionMap>
          <#assign results = groupedAnomalyResults[dimensionMap]>
          <#assign dimensionStr = dimensionMap>
          <#list results as r>
            <tr>
              <#if r == results?first>
                <td rowspan="${results?size}" style="padding:5px 3px">${dimensionStr}</td>
              </#if>
              <td style="padding:5px 3px">
              <#-- <a href="${anomalyEndpoint}${r.id?c}" target="_blank"> -->
                                ${anomalySequenceIndex?c}
                                <#-- </a> -->
              </td>
              <td style="padding:5px 3px">
              <#-- Assuming start time is always present -->
                <#if r.endTime??>
                ${dateFormat(r.startTime)} to ${dateFormat(r.endTime)}
                <#else>
                ${dateFormat(r.startTime)}
                </#if>
              </td>
            <#-- <td style="text-align:center">${r.endDate!"N/A"}</td> -->
              <td style="padding:5px 3px">${r.message!"N/A"}
              </td>
              <td style="padding:5px 3px">
              <#-- <a href="${functionEndpoint}${r.functionId?c}" target="_blank"> -->
              ${r.function.functionName} (${r.function.type})
              <#-- </a> -->
              </td>
            </tr>
            <#assign anomalySequenceIndex = anomalySequenceIndex + 1>
          </#list>
        </#list>
      </table>
    </td>
  </tr>
</#if>
  <tr>
    <td>
      Go to <a href="${dashboardHost}/dashboard#view=anomalies&dataset=${collection}&compareMode=WoW&aggTimeGranularity=${windowUnit}&currentStart=${startTime?c}&currentEnd=${endTime?c}&metrics=${metric}" target="_top">ThirdEye Anomalies Dashboard</a>
    </td>
  </tr>
<#if (metricDimensionValueReports?has_content)>
   <tr>
     <td>
       <hr/>
       <br/> Report start time : ${reportStartDateTime}
    </td>
  </tr>
  <#assign reportCount = 1>
  <#list metricDimensionValueReports?keys as metric>
    <#list metricDimensionValueReports[metric]?keys as groupByDimension>
      <#assign dimensionsTimeBucketValueMap = metricDimensionValueReports[metric][groupByDimension]>
      <#assign dimensionsTimeBucketValueMap1 = metricDimensionValueReports[metric][groupByDimension]>
      <tr>
        <td><b>${reportCount} - ${metric} by ${groupByDimension}</b></td>
      </tr>
      <tr>
        <td>
          <table align="center" border="1" style="width:100%;border-collapse: collapse; border-spacing: 0 margin-bottom:15px;border-color:#ddd;" cellspacing="0px" cellpadding="2px">
            <tr>
              <td>${groupByDimension}</td>
              <#assign itrCount = 0 >
              <#list dimensionsTimeBucketValueMap?keys as dimensionKey>
                <#assign timeBucketValueMap = dimensionsTimeBucketValueMap[dimensionKey]>
                <#if itrCount == 0>
                    <#list timeBucketValueMap?keys as timeBucket>
                      <td>
                        ${timeBucket?number?number_to_time?string("HH:mm")}
                      </td>
                    </#list>
                </#if>
                <#assign itrCount = itrCount+1>
              </#list>
            </tr>
            <#list dimensionsTimeBucketValueMap1?keys as dimensionKey1>
              <tr>
                <td>
                    ${dimensionKey1}
                </td>
                <#assign timevalmap = dimensionsTimeBucketValueMap1[dimensionKey1] >
                <#list timevalmap?keys as timebucketkey>
                  <td> ${timevalmap[timebucketkey]}% </td>
                </#list>
              </tr>
            </#list>
          </table>
        </td>
      </tr>
      <#assign reportCount = reportCount + 1>
    </#list>
  </#list>
</#if>
  <tr>
    <td style="padding:10px 30px; font-family:font-family: 'Proxima Nova','Arial', 'Helvetica Neue',Helvetica, sans-serif;font-size:16px;font-weight:300; width:100%;display:inline;">
      <hr/>
      <br/>
      <p style="margin-left:15px">If you have any questions regarding this report, please email <a href="mailto:ask_thirdeye@linkedin.com" target="_top">ask_thirdeye@linkedin.com</a></p>
      <p style="margin-left:15px"> Report generated at: ${dateFormat(reportGenerationTimeMillis)}</p>
      <p style="margin-left:15px">
        Thanks,<br>
        ThirdEye Team
      </p>
    </td>
  </tr>
</table>
