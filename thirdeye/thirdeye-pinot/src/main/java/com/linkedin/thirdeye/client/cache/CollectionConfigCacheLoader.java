package com.linkedin.thirdeye.client.cache;

import com.linkedin.thirdeye.dashboard.Utils;
import java.util.List;

import com.google.common.cache.CacheLoader;
import com.linkedin.thirdeye.dashboard.configs.AbstractConfig;
import com.linkedin.thirdeye.dashboard.configs.CollectionConfig;
import com.linkedin.thirdeye.dashboard.configs.WebappConfigFactory.WebappConfigType;
import com.linkedin.thirdeye.datalayer.bao.WebappConfigManager;
import com.linkedin.thirdeye.datalayer.dto.WebappConfigDTO;

public class CollectionConfigCacheLoader extends CacheLoader<String, CollectionConfig> {

  private WebappConfigManager webappConfigDAO;

  public CollectionConfigCacheLoader(WebappConfigManager webappConfigDAO) {
    this.webappConfigDAO = webappConfigDAO;
  }

  @Override
  public CollectionConfig load(String collection) throws Exception {
    CollectionConfig collectionConfig = null;
    List<WebappConfigDTO> webappConfigs = webappConfigDAO
        .findByCollectionAndType(collection, WebappConfigType.COLLECTION_CONFIG);
    if (!webappConfigs.isEmpty()) {
      String configJson = Utils.getJsonFromObject(webappConfigs.get(0).getConfigMap());
      collectionConfig = AbstractConfig.fromJSON(configJson, CollectionConfig.class);
    }
    return collectionConfig;
  }

}
