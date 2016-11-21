package com.linkedin.thirdeye.datalayer.bao;

import java.util.List;

import com.linkedin.thirdeye.datalayer.dto.MergedAnomalyResultDTO;

public interface MergedAnomalyResultManager extends AbstractManager<MergedAnomalyResultDTO> {

  MergedAnomalyResultDTO findById(Long id, boolean loadRawAnomalies);

  List<MergedAnomalyResultDTO> getAllByTimeEmailIdAndNotifiedFalse(long startTime, long endTime,
      long emailId);

  List<MergedAnomalyResultDTO> findAllConflictByFunctionId(long functionId, long conflictWindowStart, long conflictWindowEnd);

  List<MergedAnomalyResultDTO> findAllConflictByFunctionIdDimensions(long functionId, long conflictWindowStart, long conflictWindowEnd, String dimensions);

  List<MergedAnomalyResultDTO> findByCollectionMetricDimensionsTime(String collection,
      String metric, String dimensions, long startTime, long endTime, boolean loadRawAnomalies);

  List<MergedAnomalyResultDTO> findByCollectionMetricTime(String collection, String metric,
      long startTime, long endTime, boolean loadRawAnomalies);

  List<MergedAnomalyResultDTO> findByCollectionTime(String collection, long startTime,
      long endTime, boolean loadRawAnomalies);

  MergedAnomalyResultDTO findLatestConflictByFunctionIdDimensions(Long functionId, String dimensions,
      long conflictWindowStart, long conflictWindowEnd);

  MergedAnomalyResultDTO findLatestByFunctionIdOnly(Long functionId);

  List<MergedAnomalyResultDTO> findByFunctionId(Long functionId);

  void updateAnomalyFeedback(MergedAnomalyResultDTO entity);
}
