package com.databricks.labs.automl.model

import com.databricks.labs.automl.params.{EvolutionDefaults, RandomForestConfig}
import com.databricks.labs.automl.utils.{DataValidation, SeedConverters, SparkSessionWrapper}
import org.apache.spark.ml.evaluation.{BinaryClassificationEvaluator, MulticlassClassificationEvaluator, RegressionEvaluator}
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.functions.{count, _}
import org.apache.spark.sql.{DataFrame, Row}

import scala.collection.mutable.ArrayBuffer
import scala.reflect.runtime.universe._

trait Evolution extends DataValidation with EvolutionDefaults with SeedConverters with SparkSessionWrapper {

  var _labelCol: String = _defaultLabel
  var _featureCol: String = _defaultFeature
  var _trainPortion: Double = _defaultTrainPortion
  var _trainSplitMethod: String = _defaultTrainSplitMethod
  var _trainSplitChronologicalColumn: String = _defaultTrainSplitChronologicalColumn
  var _trainSplitChronologicalRandomPercentage: Double = _defaultTrainSplitChronologicalRandomPercentage
  var _parallelism: Int = _defaultParallelism
  var _kFold: Int = _defaultKFold
  var _seed: Long = _defaultSeed
  var _kFoldIteratorRange: scala.collection.parallel.immutable.ParRange = Range(0, _kFold).par

  var _optimizationStrategy: String = _defaultOptimizationStrategy
  var _firstGenerationGenePool: Int = _defaultFirstGenerationGenePool
  var _numberOfMutationGenerations: Int = _defaultNumberOfMutationGenerations
  var _numberOfParentsToRetain: Int = _defaultNumberOfParentsToRetain
  var _numberOfMutationsPerGeneration: Int = _defaultNumberOfMutationsPerGeneration
  var _geneticMixing: Double = _defaultGeneticMixing
  var _generationalMutationStrategy: String = _defaultGenerationalMutationStrategy
  var _mutationMagnitudeMode: String = _defaultMutationMagnitudeMode
  var _fixedMutationValue: Int = _defaultFixedMutationValue
  var _earlyStoppingScore: Double = _defaultEarlyStoppingScore
  var _earlyStoppingFlag: Boolean = _defaultEarlyStoppingFlag

  var _evolutionStrategy: String = _defaultEvolutionStrategy
  var _continuousEvolutionMaxIterations: Int = _defaultContinuousEvolutionMaxIterations
  var _continuousEvolutionStoppingScore: Double = _defaultContinuousEvolutionStoppingScore
  var _continuousEvolutionParallelism: Int = _defaultContinuousEvolutionParallelism
  var _continuousEvolutionMutationAggressiveness: Int = _defaultContinuousEvolutionMutationAggressiveness
  var _continuousEvolutionGeneticMixing: Double = _defaultContinuousEvolutionGeneticMixing
  var _continuousEvolutionRollingImprovementCount: Int = _defaultContinuousEvolutionRollingImprovementCount

  var _initialGenerationMode: String = _defaultFirstGenMode
  var _initialGenerationPermutationCount: Int = _defaultFirstGenPermutations
  var _initialGenerationIndexMixingMode: String = _defaultFirstGenIndexMixingMode
  var _initialGenerationArraySeed: Long = _defaultFirstGenArraySeed

  var _modelSeedSet: Boolean = false
  var _modelSeed: Map[String, Any] = Map.empty

  var _dataReduce: Double = _defaultDataReduce

  var _randomizer: scala.util.Random = scala.util.Random
  _randomizer.setSeed(_seed)

  def setLabelCol(value: String): this.type = {
    _labelCol = value
    this
  }

  def setFeaturesCol(value: String): this.type = {
    _featureCol = value
    this
  }

  def setTrainPortion(value: Double): this.type = {
    require(value < 1.0 & value > 0.0, "Training portion must be in the range > 0 and < 1")
    _trainPortion = value
    this
  }

  def setTrainSplitMethod(value: String): this.type = {
    require(allowableTrainSplitMethod.contains(value),
      s"TrainSplitMethod $value must be one of: ${allowableTrainSplitMethod.mkString(", ")}")
    _trainSplitMethod = value
    this
  }

  def setTrainSplitChronologicalColumn(value: String): this.type = {
    _trainSplitChronologicalColumn = value
    this
  }

  def setTrainSplitChronologicalRandomPercentage(value: Double): this.type = {
    _trainSplitChronologicalRandomPercentage = value
    if (value > 10) println("[WARNING] setTrainSplitChronologicalRandomPercentage() setting this value above 10 " +
      "percent will cause significant per-run train/test skew and variability in row counts during training.  " +
      "Use higher values only if this is desired.")
    this
  }

  def setParallelism(value: Int): this.type = {
    require(_parallelism < 10000, s"Parallelism above 10000 will result in cluster instability.")
    _parallelism = value
    this
  }

  def setKFold(value: Int): this.type = {
    _kFold = value
    _kFoldIteratorRange = Range(0, _kFold).par
    this
  }

  def setSeed(value: Long): this.type = {
    _seed = value
    this
  }

  def setOptimizationStrategy(value: String): this.type = {
    val valueLC = value.toLowerCase
    require(allowableOptimizationStrategies.contains(valueLC),
      s"Optimization Strategy '$valueLC' is not a member of ${
        invalidateSelection(valueLC, allowableOptimizationStrategies)
      }")
    _optimizationStrategy = valueLC
    this
  }

  def setFirstGenerationGenePool(value: Int): this.type = {
    require(value >= 5,
      s"Values less than 5 for firstGenerationGenePool will require excessive generational mutation to converge")
    _firstGenerationGenePool = value
    this
  }

  def setNumberOfMutationGenerations(value: Int): this.type = {
    require(value > 0, s"Number of Generations must be greater than 0")
    _numberOfMutationGenerations = value
    this
  }

  def setNumberOfParentsToRetain(value: Int): this.type = {
    require(value > 0, s"Number of Parents must be greater than 0. '$value' is not a valid number.")
    _numberOfParentsToRetain = value
    this
  }

  def setNumberOfMutationsPerGeneration(value: Int): this.type = {
    require(value > 0, s"Number of Mutations per generation must be greater than 0. '$value' is not a valid number.")
    _numberOfMutationsPerGeneration = value
    this
  }

  def setGeneticMixing(value: Double): this.type = {
    require(value < 1.0 & value > 0.0,
      s"Mutation Aggressiveness must be in range (0,1). Current Setting of $value is not permitted.")
    _geneticMixing = value
    this
  }

  def setGenerationalMutationStrategy(value: String): this.type = {
    val valueLC = value.toLowerCase
    require(allowableMutationStrategies.contains(valueLC),
      s"Generational Mutation Strategy '$valueLC' is not a member of ${
        invalidateSelection(valueLC, allowableMutationStrategies)
      }")
    _generationalMutationStrategy = valueLC
    this
  }

  def setMutationMagnitudeMode(value: String): this.type = {
    val valueLC = value.toLowerCase
    require(allowableMutationMagnitudeMode.contains(valueLC),
      s"Mutation Magnitude Mode '$valueLC' is not a member of ${
        invalidateSelection(valueLC, allowableMutationMagnitudeMode)
      }")
    _mutationMagnitudeMode = valueLC
    this
  }

  def setFixedMutationValue(value: Int): this.type = {
    val maxMutationCount = modelConfigLength[RandomForestConfig]
    require(value <= maxMutationCount,
      s"Mutation count '$value' cannot exceed number of hyperparameters ($maxMutationCount)")
    require(value > 0, s"Mutation count '$value' must be greater than 0")
    _fixedMutationValue = value
    this
  }

  def setEarlyStoppingScore(value: Double): this.type = {
    _earlyStoppingScore = value
    this
  }

  def setEarlyStoppingFlag(value: Boolean): this.type = {
    _earlyStoppingFlag = value
    this
  }

  def setEvolutionStrategy(value: String): this.type = {
    require(allowableEvolutionStrategies.contains(value),
      s"Evolution Strategy '$value' is not a supported mode.  Must be one of: ${
        invalidateSelection(value, allowableEvolutionStrategies)
      }")
    _evolutionStrategy = value
    this
  }

  def setContinuousEvolutionMaxIterations(value: Int): this.type = {
    if (value > 500) println(s"[WARNING] Total Modeling count $value is higher than recommended limit of 500.  " +
      s"This tuning will take a long time to run.")
    _continuousEvolutionMaxIterations = value
    this
  }

  def setContinuousEvolutionStoppingScore(value: Double): this.type = {
    _continuousEvolutionStoppingScore = value
    this
  }

  def setContinuousEvolutionParallelism(value: Int): this.type = {
    if (value > 10) println(s"[WARNING] ContinuousEvolutionParallelism -> $value is higher than recommended " +
      s"concurrency for efficient optimization for convergence." +
      s"\n  Setting this value below 11 will converge faster in most cases.")
    _continuousEvolutionParallelism = value
    this
  }

  def setContinuousEvolutionMutationAggressiveness(value: Int): this.type = {
    if (value > 4) println(s"[WARNING] ContinuousEvolutionMutationAggressiveness -> $value. " +
      s"\n  Setting this higher than 4 will result in extensive random search and will take longer to converge " +
      s"to optimal hyperparameters.")
    _continuousEvolutionMutationAggressiveness = value
    this
  }

  def setContinuousEvolutionGeneticMixing(value: Double): this.type = {
    require(value < 1.0 & value > 0.0,
      s"Mutation Aggressiveness must be in range (0,1). Current Setting of $value is not permitted.")
    _continuousEvolutionGeneticMixing = value
    this
  }

  def setContinuousEvolutionRollingImporvementCount(value: Int): this.type = {
    require(value > 0, s"ContinuousEvolutionRollingImprovementCount must be > 0. $value is invalid.")
    if (value < 10) println(s"[WARNING] ContinuousEvolutionRollingImprovementCount -> $value setting is low.  " +
      s"Optimal Convergence may not occur due to early stopping.")
    _continuousEvolutionRollingImprovementCount = value
    this
  }

  def setModelSeed(value: Map[String, Any]): this.type = {
    _modelSeed = value
    _modelSeedSet = true
    this
  }

  def setDataReductionFactor(value: Double): this.type = {
    require(value > 0, s"Data Reduction Factor must be between 0 and 1")
    require(value < 1, s"Data Reduction Factor must be between 0 and 1")
    _dataReduce = value
    this
  }

  def setFirstGenMode(value: String): this.type = {
    require(allowableInitialGenerationModes.contains(value), s"First Generation Mode '$value' is not a supported mode." +
      s"  Must be one of: ${
        invalidateSelection(value, allowableInitialGenerationModes)
      }")
    _initialGenerationMode = value
    this
  }

  def setFirstGenPermutations(value: Int): this.type = {
    _initialGenerationPermutationCount = value
    this
  }

  def setFirstGenIndexMixingMode(value: String): this.type = {
    require(allowableInitialGenerationIndexMixingModes.contains(value), s"First Generation Mode '$value' is not a" +
      s"supported mode.  Must be one of ${
        invalidateSelection(value, allowableInitialGenerationIndexMixingModes)
      }")
    _initialGenerationIndexMixingMode = value
    this
  }

  def setFirstGenArraySeed(value: Long): this.type = {
    _initialGenerationArraySeed = value
    this
  }

  def getFirstGenArraySeed: Long = _initialGenerationArraySeed

  def getFirstGenIndexMixingMode: String = _initialGenerationIndexMixingMode

  def getFirstGenPermutations: Int = _initialGenerationPermutationCount

  def getFirstGenMode: String = _initialGenerationMode

  def getLabelCol: String = _labelCol

  def getFeaturesCol: String = _featureCol

  def getTrainPortion: Double = _trainPortion

  def getTrainSplitMethod: String = _trainSplitMethod

  def getTrainSplitChronologicalColumn: String = _trainSplitChronologicalColumn

  def getTrainSplitChronologicalRandomPercentage: Double = _trainSplitChronologicalRandomPercentage

  def getParallelism: Int = _parallelism

  def getKFold: Int = _kFold

  def getSeed: Long = _seed

  def getOptimizationStrategy: String = _optimizationStrategy

  def getFirstGenerationGenePool: Int = _firstGenerationGenePool

  def getNumberOfMutationGenerations: Int = _numberOfMutationGenerations

  def getNumberOfParentsToRetain: Int = _numberOfParentsToRetain

  def getNumberOfMutationsPerGeneration: Int = _numberOfMutationsPerGeneration

  def getGeneticMixing: Double = _geneticMixing

  def getGenerationalMutationStrategy: String = _generationalMutationStrategy

  def getMutationMagnitudeMode: String = _mutationMagnitudeMode

  def getFixedMutationValue: Int = _fixedMutationValue

  def getEarlyStoppingScore: Double = _earlyStoppingScore

  def getEarlyStoppingFlag: Boolean = _earlyStoppingFlag

  def getEvolutionStrategy: String = _evolutionStrategy

  def getContinuousEvolutionMaxIterations: Int = _continuousEvolutionMaxIterations

  def getContinuousEvolutionStoppingScore: Double = _continuousEvolutionStoppingScore

  def getContinuousEvolutionParallelism: Int = _continuousEvolutionParallelism

  def getContinuousEvolutionMutationAggressiveness: Int = _continuousEvolutionMutationAggressiveness

  def getContinuousEvolutionGeneticMixing: Double = _continuousEvolutionGeneticMixing

  def getContinuousEvolutionRollingImporvementCount: Int = _continuousEvolutionRollingImprovementCount

  def getModelSeed: Map[String, Any] = _modelSeed

  def getDataReductionFactor: Double = _dataReduce

  def totalModels: Int = _evolutionStrategy match {
    case "batch" => (_numberOfMutationsPerGeneration * _numberOfMutationGenerations) + _firstGenerationGenePool
    case "continuous" => _continuousEvolutionMaxIterations - _continuousEvolutionParallelism + _firstGenerationGenePool
    case _ => throw new MatchError(s"EvolutionStrategy mode ${_evolutionStrategy} is not supported." +
      s"\n  Choose one of: ${allowableEvolutionStrategies.mkString(", ")}")
  }

  def modelConfigLength[T: TypeTag]: Int = {
    typeOf[T].members.collect {
      case m: MethodSymbol if m.isCaseAccessor => m
    }.toList.length
  }

  private def toDoubleType(x: Any): Option[Double] = x match {
    case i: Int => Some(i)
    case d: Double => Some(d)
    case _ => None
  }

  private def generateEmptyTrainTest(data: DataFrame): (DataFrame, DataFrame) = {

    val schema = data.schema

    var trainData = spark.createDataFrame(sc.emptyRDD[Row], schema)
    var testData = spark.createDataFrame(sc.emptyRDD[Row], schema)
    (trainData, testData)
  }

  /**
    * Method for stratification of the test/train around the unique values of the label column
    * This mode is recommended for label value distributions in classification that have relatively balanced
    * and uniformly distributed instances of the classes.
    * If there is significant skew, it is highly recommended to use under or over sampling.
    *
    * @param data Dataframe that is the input to the train/test split
    * @param seed random seed for splitting the data into train/test.
    * @return An Array of Dataframes: Array[<trainData>, <testData>]
    */
  def stratifiedSplit(data: DataFrame, seed: Long): Array[DataFrame] = {

    var (trainData, testData) = generateEmptyTrainTest(data)

    val uniqueLabels = data.select(_labelCol).distinct().collect()

    uniqueLabels.foreach{ x =>

      val conversionValue = toDoubleType(x(0)).get

      val Array(trainSplit, testSplit) = data.filter(
        col(_labelCol) === conversionValue).randomSplit(Array(_trainPortion, 1 - _trainPortion), seed)

      trainData = trainData.union(trainSplit)
      testData = testData.union(testSplit)

    }

    Array(trainData, testData)
  }

  def underSampleSplit(data: DataFrame, seed: Long): Array[DataFrame] = {

    var (trainData, testData) = generateEmptyTrainTest(data)

    val totalDataSetCount = data.count()

    val groupedLabelCount = data.select(_labelCol)
      .groupBy(_labelCol)
      .agg(count("*").as("counts"))
      .withColumn("skew", col("counts") / lit(totalDataSetCount))
      .select(_labelCol, "skew")

    val uniqueGroups = groupedLabelCount.collect()

    val smallestSkew = groupedLabelCount
      .sort(col("skew").asc)
      .select(col("skew"))
      .first()
      .getDouble(0)

    uniqueGroups.foreach{ x =>

      val groupData = toDoubleType(x(0)).get

      val groupRatio = x.getDouble(1)

      val groupDataFrame = data.filter(col(_labelCol) === groupData)

      val Array(train, test) = if (groupRatio == smallestSkew) {
        groupDataFrame.randomSplit(Array(_trainPortion, 1 - _trainPortion), seed)
      } else {
        groupDataFrame.sample(false, smallestSkew / groupRatio)
          .randomSplit(Array(_trainPortion, 1 - _trainPortion), seed)
      }

      trainData = trainData.union(train)
      testData = testData.union(test)

    }

    Array(trainData, testData)

  }

  def overSampleSplit(data: DataFrame, seed: Long): Array[DataFrame] = {

    var (trainData, testData) = generateEmptyTrainTest(data)

    val groupedLabelCount = data
      .select(_labelCol)
      .groupBy(_labelCol)
      .agg(count("*").as("counts"))

    val uniqueGroups = groupedLabelCount.collect()

    val largestGroupCount = groupedLabelCount
      .sort(col("counts").desc)
      .select(col("counts"))
      .first()
      .getLong(0)

    uniqueGroups.foreach{ x =>
      val groupData = toDoubleType(x(0)).get

      val groupRatio = math.ceil(largestGroupCount / x.getLong(1)).toInt

      for(i <- 1 to groupRatio) {

        val Array(train, test): Array[DataFrame] = data
          .filter(col(_labelCol) === groupData)
          .randomSplit(Array(_trainPortion, 1 - _trainPortion), seed)

        trainData = trainData.union(train)
        testData = testData.union(test)

      }
    }

    Array(trainData, testData)

  }

  def stratifyReduce(data: DataFrame, reductionFactor: Double, seed:Long): Array[DataFrame] = {

    var (trainData, testData) = generateEmptyTrainTest(data)

    val uniqueLabels = data.select(_labelCol).distinct().collect()

    uniqueLabels.foreach{ x =>

      val conversionValue = toDoubleType(x(0)).get

      val Array(trainSplit, testSplit) = data.filter(
        col(_labelCol) === conversionValue).randomSplit(Array(_trainPortion, 1 - _trainPortion), seed)

      trainData = trainData.union(trainSplit.sample(reductionFactor))
      testData = testData.union(testSplit.sample(reductionFactor))

    }

    Array(trainData, testData)

  }

  def chronologicalSplit(data: DataFrame, seed: Long): Array[DataFrame] = {

    require(data.schema.fieldNames.contains(_trainSplitChronologicalColumn),
      s"Chronological Split Field ${_trainSplitChronologicalColumn} is not in schema: " +
        s"${data.schema.fieldNames.mkString(", ")}")

    // Validation check for the random 'wiggle value' if it's set that it won't risk creating zero rows in train set.
    if (_trainSplitChronologicalRandomPercentage > 0.0)
      require((1 - _trainPortion) * _trainSplitChronologicalRandomPercentage / 100 < 0.5,
        s"With trainSplitChronologicalRandomPercentage set at '${_trainSplitChronologicalRandomPercentage}' " +
          s"and a train test ratio of ${_trainPortion} there is a high probability of train data set being empty." +
          s"  \n\tAdjust lower to prevent non-deterministic split levels that could break training.")

    // Get the row count
    val rawDataCount = data.count.toDouble

    val splitValue = scala.math.round(rawDataCount * _trainPortion).toInt

    // Get the row number estimation for conduction the split at
    val splitRow: Int = if (_trainSplitChronologicalRandomPercentage <= 0.0) {
      splitValue
    }
    else {
      // randomly mutate the size of the test validation set
      val splitWiggle = scala.math.round(rawDataCount * (1 - _trainPortion) *
        _trainSplitChronologicalRandomPercentage / 100).toInt
      splitValue - _randomizer.nextInt(splitWiggle)
    }

    // Define the window partition
    val uniqueCol = "chron_grp_autoML_" + java.util.UUID.randomUUID().toString

    // Define temporary non-colliding columns for the window partition
    val uniqueRow = "row_" + java.util.UUID.randomUUID().toString
    val windowDefintion = Window.partitionBy(uniqueCol).orderBy(_trainSplitChronologicalColumn)

    // Generate a new Dataframe that has the row number partition, sorted by the chronological field
    val preSplitData = data.withColumn(uniqueCol, lit("grp"))
      .withColumn(uniqueRow, row_number() over windowDefintion)
      .drop(uniqueCol)

    // Generate the test/train split data based on sorted chronological column
    Array(preSplitData.filter(col(uniqueRow) <= splitRow).drop(uniqueRow),
      preSplitData.filter(col(uniqueRow) > splitRow).drop(uniqueRow))

  }

  def genTestTrain(data: DataFrame, seed: Long): Array[DataFrame] = {

    _trainSplitMethod match {
      case "random" => data.randomSplit(Array(_trainPortion, 1 - _trainPortion), seed)
      case "chronological" => chronologicalSplit(data, seed)
      case "stratified" => stratifiedSplit(data, seed)
      case "overSample" => overSampleSplit(data, seed)
      case "underSample" => underSampleSplit(data, seed)
      case "stratifyReduce" => stratifyReduce(data, _dataReduce, seed)
      case _ => throw new IllegalArgumentException(s"Cannot conduct train test split in mode: '${_trainSplitMethod}'")
    }

  }

  def extractBoundaryDouble(param: String, boundaryMap: Map[String, (AnyVal, AnyVal)]): (Double, Double) = {
    val minimum = boundaryMap(param)._1.asInstanceOf[Double]
    val maximum = boundaryMap(param)._2.asInstanceOf[Double]
    (minimum, maximum)
  }

  def extractBoundaryInteger(param: String, boundaryMap: Map[String, (AnyVal, AnyVal)]): (Int, Int) = {
    val minimum = boundaryMap(param)._1.asInstanceOf[Double].toInt
    val maximum = boundaryMap(param)._2.asInstanceOf[Double].toInt
    (minimum, maximum)
  }

  def generateRandomDouble(param: String, boundaryMap: Map[String, (AnyVal, AnyVal)]): Double = {
    val (minimumValue, maximumValue) = extractBoundaryDouble(param, boundaryMap)
    minimumValue + _randomizer.nextDouble() * (maximumValue - minimumValue)
  }

  def generateRandomInteger(param: String, boundaryMap: Map[String, (AnyVal, AnyVal)]): Int = {
    val (minimumValue, maximumValue) = extractBoundaryInteger(param, boundaryMap)
    _randomizer.nextInt(maximumValue - minimumValue) + minimumValue
  }

  def generateRandomString(param: String, boundaryMap: Map[String, List[String]]): String = {
    _randomizer.shuffle(boundaryMap(param)).head
  }

  def coinFlip(): Boolean = {
    math.random < 0.5
  }

  def coinFlip(parent: Boolean, child: Boolean, p: Double): Boolean = {
    if (math.random < p) parent else child
  }

  def buildLayerArray(inputFeatureSize: Int, distinctClasses: Int, nLayers: Int,
                      hiddenLayerSizeAdjust: Int): Array[Int] = {

    val layerConstruct = new ArrayBuffer[Int]

    layerConstruct += inputFeatureSize

    (1 to nLayers).foreach { x =>
      layerConstruct += inputFeatureSize + nLayers - x + hiddenLayerSizeAdjust
    }
    layerConstruct += distinctClasses
    layerConstruct.result.toArray
  }

  def generateLayerArray(layerParam: String, layerSizeParam: String, boundaryMap: Map[String, (AnyVal, AnyVal)],
                         inputFeatureSize: Int, distinctClasses: Int): Array[Int] = {

    val layersToGenerate = generateRandomInteger(layerParam, boundaryMap)
    val hiddenLayerSizeAdjust = generateRandomInteger(layerSizeParam, boundaryMap)

    buildLayerArray(inputFeatureSize, distinctClasses, layersToGenerate, hiddenLayerSizeAdjust)

  }

  def getRandomIndeces(minimum: Int, maximum: Int, parameterCount: Int): List[Int] = {
    val fullIndexArray = List.range(0, maximum)
    val randomSeed = new scala.util.Random
    val count = minimum + randomSeed.nextInt((parameterCount - minimum) + 1)
    val adjCount = if (count < 1) 1 else count
    val shuffledArray = scala.util.Random.shuffle(fullIndexArray).take(adjCount)
    shuffledArray.sortWith(_ < _)
  }

  def getFixedIndeces(minimum: Int, maximum: Int, parameterCount: Int): List[Int] = {
    val fullIndexArray = List.range(0, maximum)
    val randomSeed = new scala.util.Random
    randomSeed.shuffle(fullIndexArray).take(parameterCount).sortWith(_ < _)
  }

  def generateMutationIndeces(minimum: Int, maximum: Int, parameterCount: Int,
                              mutationCount: Int): Array[List[Int]] = {
    val mutations = new ArrayBuffer[List[Int]]
    for (_ <- 0 to mutationCount) {
      _mutationMagnitudeMode match {
        case "random" => mutations += getRandomIndeces(minimum, maximum, parameterCount)
        case "fixed" => mutations += getFixedIndeces(minimum, maximum, parameterCount)
        case _ => new UnsupportedOperationException(
          s"Unsupported mutationMagnitudeMode ${_mutationMagnitudeMode}")
      }
    }
    mutations.result.toArray
  }

  def geneMixing(parent: Double, child: Double, parentMutationPercentage: Double): Double = {
    (parent * parentMutationPercentage) + (child * (1 - parentMutationPercentage))
  }

  def geneMixing(parent: Int, child: Int, parentMutationPercentage: Double): Int = {
    ((parent * parentMutationPercentage) + (child * (1 - parentMutationPercentage))).toInt
  }

  def geneMixing(parent: String, child: String): String = {
    val mixed = new ArrayBuffer[String]
    mixed += parent += child
    scala.util.Random.shuffle(mixed.toList).head
  }

  def geneMixing(parent: Array[Int], child: Array[Int], parentMutationPercentage: Double): Array[Int] = {

    val staticStart = parent.head
    val staticEnd = parent.last

    val parentHiddenLayers = parent.length - 2
    val childHiddenLayers = child.length - 2

    val parentMagnitude = parent(1) - staticStart
    val childMagnidue = child(1) - staticStart

    val hiddenLayerMix = geneMixing(parentHiddenLayers, childHiddenLayers, parentMutationPercentage)
    val sizeAdjustMix = geneMixing(parentMagnitude, childMagnidue, parentMutationPercentage)

    buildLayerArray(staticStart, staticEnd, hiddenLayerMix, sizeAdjustMix)

  }

  def calculateModelingFamilyRemainingTime(currentGen: Int, currentModel: Int): Double = {

    val modelsComplete = _evolutionStrategy match {
      case "batch" =>
        if (currentGen == 1) {
          currentModel
        } else {
          _firstGenerationGenePool + (_numberOfMutationsPerGeneration * (currentGen - 2) + currentModel)
        }
      case _ => currentGen + _firstGenerationGenePool
    }

    (modelsComplete.toDouble / totalModels.toDouble) * 100

  }

  /**
    * Method for validating the distinct class count for a classification type model (for use in determining which
    * evaluator to employ for scoring and optimization of each model)
    * @param df source Dataframe (prior to splitting for train/test)
    * @return Boolean true for Binary Classification problem, false for multi-class problem
    */
  def classificationAdjudicator(df: DataFrame): Boolean = {

    // Calculate the distinct entries of the label value for a classification problem
    val uniqueLabelCounts = df.select(_labelCol).distinct().count()

    if(uniqueLabelCounts <= 2) true else false

  }

  /**
    * Method for restricting the available metrics used or are available for optimizing for classification problems
    * @param binaryValidation boolean check from classificationAdjudicator() method
    * @param metricPayload the hard-coded allowable List[String] of allowable classification metrics
    *                      from com.databricks.labs.automl.params.EvolutionDefaults
    * @return a copy of the the allowable params list with the Binary metrics removed if this is a multiclass problem.
    */
  def classificationMetricValidator(binaryValidation: Boolean, metricPayload: List[String]): List[String] = {

    if(binaryValidation) {
      metricPayload
    } else {
      metricPayload.diff(List("areaUnderROC", "areaUnderPR"))
    }

  }

  /**
    * Method for scoring and evaluating classification models (supporting both multi-class and binary classification
    * problems)
    * @param metricName the metric to be tested against (both for binary and multi-class)
    * @param labelColumn the column name in the data set that is the 'source of truth' to compare against
    * @param data the DataFrame that has been transformed
    * @return the score, as a Double value.
    */
  def classificationScoring(metricName: String, labelColumn: String, data: DataFrame): Double = {

    metricName match {
      case "areaUnderPR" | "areaUnderROC" =>
        new BinaryClassificationEvaluator()
          .setLabelCol(labelColumn)
          .setRawPredictionCol("probability")
          .setMetricName(metricName)
          .evaluate(data)
      case _ =>
        new MulticlassClassificationEvaluator()
          .setLabelCol(labelColumn)
          .setPredictionCol("prediction")
          .setMetricName(metricName)
          .evaluate(data)
    }

  }

  /**
    * Method for scoring Regression models.
    * @param metricName The metric desired to be tested
    * @param labelColumn The name of the label column
    * @param data the DataFrame that has been transformed by a model.
    * @return the score for the metric, as a Double value.
    */
  def regressionScoring(metricName: String, labelColumn: String, data: DataFrame): Double = {

    new RegressionEvaluator()
      .setLabelCol(labelColumn)
      .setMetricName(metricName)
      .evaluate(data)

  }



}