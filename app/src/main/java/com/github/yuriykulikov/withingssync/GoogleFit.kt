package com.github.yuriykulikov.withingssync

import com.google.android.gms.fitness.HistoryClient
import com.google.android.gms.fitness.data.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.tasks.await

class GoogleFit(
    private val historyClient: HistoryClient,
) {
  private val weightSource =
      DataSource.Builder()
          .setDataType(DataType.TYPE_WEIGHT)
          .setAppPackageName(BuildConfig.APPLICATION_ID)
          .setType(DataSource.TYPE_RAW)
          .build()

  private val fatSource =
      DataSource.Builder()
          .setDataType(DataType.TYPE_BODY_FAT_PERCENTAGE)
          .setAppPackageName(BuildConfig.APPLICATION_ID)
          .setType(DataSource.TYPE_RAW)
          .build()

  suspend fun post(measures: List<WithingsMeasure>) {
    val weightDataSet =
        buildDataSet(weightSource) {
          addAll(
              measures.mapNotNull { measure ->
                if (measure.weight == null) null
                else
                    buildDataPoint(weightSource) {
                      setField(Field.FIELD_WEIGHT, measure.weight.toFloat() / 1000f)
                      setTimestamp(measure.date.toInstant().epochSecond, TimeUnit.SECONDS)
                    }
              })
        }

    val fatDataSet =
        buildDataSet(fatSource) {
          addAll(
              measures.mapNotNull { measure ->
                if (measure.fatRatio == null) null
                else
                    buildDataPoint(fatSource) {
                      setField(Field.FIELD_PERCENTAGE, measure.fatRatio.toFloat() / 1000f)
                      setTimestamp(measure.date.toInstant().epochSecond, TimeUnit.SECONDS)
                    }
              })
        }

    if (weightDataSet.dataPoints.isNotEmpty()) {
      historyClient.insertData(weightDataSet).await()
    }

    if (fatDataSet.dataPoints.isNotEmpty()) {
      historyClient.insertData(fatDataSet).await()
    }
  }
}

fun buildDataSet(dataSource: DataSource, block: DataSet.Builder.() -> Unit): DataSet {
  return DataSet.builder(dataSource).apply(block).build()
}

fun buildDataPoint(dataSource: DataSource, block: DataPoint.Builder.() -> Unit): DataPoint {
  return DataPoint.builder(dataSource).apply(block).build()
}
