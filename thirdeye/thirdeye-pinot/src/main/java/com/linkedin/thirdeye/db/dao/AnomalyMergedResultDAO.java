package com.linkedin.thirdeye.db.dao;

import com.google.inject.persist.Transactional;
import com.linkedin.thirdeye.db.entity.AnomalyMergedResult;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.persistence.NoResultException;

public class AnomalyMergedResultDAO extends AbstractJpaDAO<AnomalyMergedResult> {

  private static final String FIND_BY_COLLECTION_METRIC_DIMENSIONS_ORDER_BY_END_TIME =
      "from AnomalyMergedResult amr where amr.collection=:collection and amr.metric=:metric "
          + "and amr.dimensions=:dimensions order by amr.endTime desc";

  private static final String FIND_BY_COLLECTION_METRIC_DIMENSIONS_TIME =
      "from AnomalyMergedResult r where r.collection=:collection and r.metric=:metric "
          + "and r.dimensions in :dimensions and ((r.startTime >= :startTime and r.startTime <= :endTime) "
          + "or (r.endTime >= :startTime and r.endTime <= :endTime)) order by r.endTime desc";

  private static final String FIND_BY_COLLECTION_METRIC_TIME =
      "from AnomalyMergedResult r where r.collection=:collection and r.metric=:metric "
          + "and ((r.startTime >= :startTime and r.startTime <= :endTime) "
          + "or (r.endTime >= :startTime and r.endTime <= :endTime)) order by r.endTime desc";

  private static final String FIND_BY_COLLECTION_TIME =
      "from AnomalyMergedResult r where r.collection=:collection "
          + "and ((r.startTime >= :startTime and r.startTime <= :endTime) "
          + "or (r.endTime >= :startTime and r.endTime <= :endTime)) order by r.endTime desc";

  private static final String FIND_BY_FUNCTION_AND_DIMENSIONS =
      "from AnomalyMergedResult amr where amr.function.id=:functionId "
          + "and amr.dimensions=:dimensions order by amr.endTime desc";

  private static final String FIND_BY_FUNCTION_AND_NULL_DIMENSION =
      "from AnomalyMergedResult amr where amr.function.id=:functionId "
          + "and amr.dimensions is null order by amr.endTime desc";

  private static final String FIND_BY_TIME =
      "from AnomalyMergedResult r WHERE ((r.startTime >= :startTime AND r.startTime <= :endTime) "
          + "OR (r.endTime >= :startTime AND r.endTime <= :endTime)) order by r.endTime desc ";

  private static final String FIND_BY_TIME_EMAIL =
      "SELECT r FROM EmailConfiguration d JOIN d.functions f, AnomalyMergedResult r "
          + "WHERE r.function.id=f.id AND d.id = :emailId "
          + "AND ((r.startTime >= :startTime AND r.startTime <= :endTime) "
          + "OR (r.endTime >= :startTime AND r.endTime <= :endTime)) order by r.endTime desc ";

  public AnomalyMergedResultDAO() {
    super(AnomalyMergedResult.class);
  }

  @Transactional
  public List<AnomalyMergedResult> getAllByTime(long startTime, long endTime) {
    return getEntityManager().createQuery(FIND_BY_TIME, entityClass)
        .setParameter("startTime", startTime).setParameter("endTime", endTime).getResultList();
  }

  @Transactional
  public List<AnomalyMergedResult> getAllByTimeEmailId(long startTime, long endTime, long emailId) {
    return getEntityManager().createQuery(FIND_BY_TIME_EMAIL, entityClass)
        .setParameter("emailId", emailId).setParameter("startTime", startTime)
        .setParameter("endTime", endTime).getResultList();
  }

  @Transactional
  public List<AnomalyMergedResult> findByCollectionMetricDimensionsTime(String collection,
      String metric, String [] dimensions, long startTime, long endTime) {
    List<String> dimList = Arrays.asList(dimensions);
    return getEntityManager()
        .createQuery(FIND_BY_COLLECTION_METRIC_DIMENSIONS_TIME, entityClass)
        .setParameter("collection", collection).setParameter("metric", metric)
        .setParameter("dimensions", dimList).setParameter("startTime", startTime)
        .setParameter("endTime", endTime).getResultList();
  }

  @Transactional
  public List<AnomalyMergedResult> findByCollectionMetricTime(String collection,
      String metric, long startTime, long endTime) {
    return getEntityManager().createQuery(FIND_BY_COLLECTION_METRIC_TIME, entityClass)
        .setParameter("collection", collection).setParameter("metric", metric)
        .setParameter("startTime", startTime).setParameter("endTime", endTime).getResultList();
  }

  @Transactional
  public List<AnomalyMergedResult> findByCollectionTime(String collection,
      long startTime, long endTime) {
    return getEntityManager().createQuery(FIND_BY_COLLECTION_TIME, entityClass)
        .setParameter("collection", collection).setParameter("startTime", startTime)
        .setParameter("endTime", endTime).getResultList();
  }

  @Transactional
  public AnomalyMergedResult findLatestByCollectionMetricDimensions(
      String collection, String metric, String dimensions) {
    try {
      return getEntityManager()
          .createQuery(FIND_BY_COLLECTION_METRIC_DIMENSIONS_ORDER_BY_END_TIME, entityClass)
          .setParameter("collection", collection).setParameter("metric", metric)
          .setParameter("dimensions", dimensions).setMaxResults(1).getSingleResult();
    } catch (NoResultException e) {
      return null;
    }
  }

  @Transactional
  public AnomalyMergedResult findLatestByFunctionIdDimensions(Long functionId, String dimensions) {
    try {
      return getEntityManager().createQuery(FIND_BY_FUNCTION_AND_DIMENSIONS, entityClass)
          .setParameter("functionId", functionId).setParameter("dimensions", dimensions)
          .setMaxResults(1).getSingleResult();
    } catch (NoResultException e) {
      return null;
    }
  }

  @Transactional
  public AnomalyMergedResult findLatestByFunctionIdOnly(Long functionId) {
    try {
      return getEntityManager().createQuery(FIND_BY_FUNCTION_AND_NULL_DIMENSION, entityClass)
          .setParameter("functionId", functionId).setMaxResults(1).getSingleResult();
    } catch (NoResultException e) {
      return null;
    }
  }
}
