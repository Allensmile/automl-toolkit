package com.databricks.spark.automatedml.params

case class MainConfig(
                       modelFamily: String,
                       labelCol: String,
                       featuresCol: String,
                       naFillFlag: Boolean,
                       varianceFilterFlag: Boolean,
                       outlierFilterFlag: Boolean,
                       pearsonFilteringFlag: Boolean,
                       covarianceFilteringFlag: Boolean,
                       oneHotEncodeFlag: Boolean,
                       scalingFlag: Boolean,
                       autoStoppingFlag: Boolean,
                       autoStoppingScore: Double,
                       featureImportanceCutoffType: String,
                       featureImportanceCutoffValue: Double,
                       dateTimeConversionType: String,
                       fieldsToIgnoreInVector: Array[String],
                       numericBoundaries: Map[String, (Double, Double)],
                       stringBoundaries: Map[String, List[String]],
                       scoringMetric: String,
                       scoringOptimizationStrategy: String,
                       fillConfig: FillConfig,
                       outlierConfig: OutlierConfig,
                       pearsonConfig: PearsonConfig,
                       covarianceConfig: CovarianceConfig,
                       scalingConfig: ScalingConfig,
                       geneticConfig: GeneticConfig,
                       mlFlowLoggingFlag: Boolean,
                       mlFlowLogArtifactsFlag: Boolean,
                       mlFlowConfig: MLFlowConfig,
                       inferenceConfigSaveLocation: String
                     )

// TODO: Change MainConfig to use this case class definition.
case class DataPrepConfig(
                         naFillFlag: Boolean,
                         varianceFilterFlag: Boolean,
                         outlierFilterFlag: Boolean,
                         pearsonFilterFlag: Boolean,
                         covarianceFilterFlag: Boolean,
                         scalingFlag: Boolean
                         )

case class MLFlowConfig(
                        mlFlowTrackingURI: String,
                        mlFlowExperimentName: String,
                        mlFlowAPIToken: String,
                        mlFlowModelSaveDirectory: String
                       )

case class FillConfig(
                       numericFillStat: String,
                       characterFillStat: String,
                       modelSelectionDistinctThreshold: Int
                     )

case class OutlierConfig(
                          filterBounds: String,
                          lowerFilterNTile: Double,
                          upperFilterNTile: Double,
                          filterPrecision: Double,
                          continuousDataThreshold: Int,
                          fieldsToIgnore: Array[String]
                        )

case class PearsonConfig(
                          filterStatistic: String,
                          filterDirection: String,
                          filterManualValue: Double,
                          filterMode: String,
                          autoFilterNTile: Double
                        )

case class CovarianceConfig(
                             correlationCutoffLow: Double,
                             correlationCutoffHigh: Double
                           )

case class GeneticConfig(
                          parallelism: Int,
                          kFold: Int,
                          trainPortion: Double,
                          trainSplitMethod: String,
                          trainSplitChronologicalColumn: String,
                          trainSplitChronologicalRandomPercentage: Double,
                          seed: Long,
                          firstGenerationGenePool: Int,
                          numberOfGenerations: Int,
                          numberOfParentsToRetain: Int,
                          numberOfMutationsPerGeneration: Int,
                          geneticMixing: Double,
                          generationalMutationStrategy: String,
                          fixedMutationValue: Int,
                          mutationMagnitudeMode: String,
                          evolutionStrategy: String,
                          continuousEvolutionMaxIterations: Int,
                          continuousEvolutionStoppingScore: Double,
                          continuousEvolutionParallelism: Int,
                          continuousEvolutionMutationAggressiveness: Int,
                          continuousEvolutionGeneticMixing: Double,
                          continuousEvolutionRollingImprovementCount: Int,
                          modelSeed: Map[String, Any]
                        )

case class ScalingConfig(
                          scalerType: String,
                          scalerMin: Double,
                          scalerMax: Double,
                          standardScalerMeanFlag: Boolean,
                          standardScalerStdDevFlag: Boolean,
                          pNorm: Double
                        )