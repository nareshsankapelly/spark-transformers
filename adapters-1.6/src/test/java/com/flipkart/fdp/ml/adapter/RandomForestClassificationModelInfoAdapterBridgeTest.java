package com.flipkart.fdp.ml.adapter;

import com.flipkart.fdp.ml.export.ModelExporter;
import com.flipkart.fdp.ml.importer.ModelImporter;
import com.flipkart.fdp.ml.transformer.Transformer;
import org.apache.spark.ml.Pipeline;
import org.apache.spark.ml.PipelineModel;
import org.apache.spark.ml.PipelineStage;
import org.apache.spark.ml.classification.RandomForestClassificationModel;
import org.apache.spark.ml.classification.RandomForestClassifier;
import org.apache.spark.ml.feature.StringIndexer;
import org.apache.spark.ml.feature.StringIndexerModel;
import org.apache.spark.mllib.linalg.Vector;
import org.apache.spark.sql.DataFrame;
import org.apache.spark.sql.Row;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * Created by akshay.us on 8/29/16.
 */
public class RandomForestClassificationModelInfoAdapterBridgeTest extends SparkTestBase {


    @Test
    public void testRandomForestClassification() {
        // Load the data stored in LIBSVM format as a DataFrame.
        DataFrame data = sqlContext.read().format("libsvm").load("src/test/resources/classification_test.libsvm");

        StringIndexerModel stringIndexerModel = new StringIndexer()
                .setInputCol("label")
                .setOutputCol("labelIndex")
                .fit(data);

        data = stringIndexerModel.transform(data);

        // Split the data into training and test sets (30% held out for testing)
        DataFrame[] splits = data.randomSplit(new double[]{0.7, 0.3});
        DataFrame trainingData = splits[0];
        DataFrame testData = splits[1];

        // Train a RandomForest model.
        RandomForestClassificationModel classificationModel = new RandomForestClassifier()
                .setLabelCol("labelIndex")
                .setFeaturesCol("features")
                .setPredictionCol("prediction")
                .setRawPredictionCol("rawPrediction")
                .setProbabilityCol("probability")
                .fit(trainingData);


        byte[] exportedModel = ModelExporter.export(classificationModel, null);

        Transformer transformer = ModelImporter.importAndGetTransformer(exportedModel);

        Row[] sparkOutput = classificationModel.transform(testData).select("features", "prediction", "rawPrediction", "probability").collect();

        //compare predictions
        for (Row row : sparkOutput) {
            Vector v = (Vector) row.get(0);
            double actual = row.getDouble(1);
            double [] actualProbability = ((Vector) row.get(3)).toArray();
            double[] actualRaw = ((Vector) row.get(2)).toArray();

            Map<String, Object> inputData = new HashMap<String, Object>();
            inputData.put(transformer.getInputKeys().iterator().next(), v.toArray());
            transformer.transform(inputData);
            double predicted = (double) inputData.get("prediction");
            double[] probability = (double[]) inputData.get("probability");
            double[] rawPrediction = (double[]) inputData.get("rawPrediction");

            assertEquals(actual, predicted, EPSILON);
            assertArrayEquals(actualProbability, probability, EPSILON);
            assertArrayEquals(actualRaw, rawPrediction, EPSILON);


        }

    }


    @Test
    public void testRandomForestClassificationWithPipeline() {
        // Load the data stored in LIBSVM format as a DataFrame.
        DataFrame data = sqlContext.read().format("libsvm").load("src/test/resources/classification_test.libsvm");

        // Split the data into training and test sets (30% held out for testing)
        DataFrame[] splits = data.randomSplit(new double[]{0.7, 0.3});
        DataFrame trainingData = splits[0];
        DataFrame testData = splits[1];

        StringIndexer indexer = new StringIndexer()
                .setInputCol("label")
                .setOutputCol("labelIndex");

        // Train a DecisionTree model.
        RandomForestClassifier classifier = new RandomForestClassifier()
                .setLabelCol("labelIndex")
                .setFeaturesCol("features")
                .setPredictionCol("prediction")
                .setRawPredictionCol("rawPrediction")
                .setProbabilityCol("probability");


        Pipeline pipeline = new Pipeline()
                .setStages(new PipelineStage[]{indexer, classifier});

        // Train model.  This also runs the indexer.
        PipelineModel sparkPipeline = pipeline.fit(trainingData);

        //Export this model
        byte[] exportedModel = ModelExporter.export(sparkPipeline, null);

        //Import and get Transformer
        Transformer transformer = ModelImporter.importAndGetTransformer(exportedModel);

        Row[] sparkOutput = sparkPipeline.transform(testData).select("label", "features", "prediction", "rawPrediction", "probability").collect();

        //compare predictions
        for (Row row : sparkOutput) {
            Vector v = (Vector) row.get(1);
            double actual = row.getDouble(2);
            double [] actualProbability = ((Vector) row.get(4)).toArray();
            double[] actualRaw = ((Vector) row.get(3)).toArray();

            Map<String, Object> inputData = new HashMap<String, Object>();
            inputData.put("features", v.toArray());
            inputData.put("label", row.get(0).toString());
            transformer.transform(inputData);
            double predicted = (double) inputData.get("prediction");
            double[] probability = (double[]) inputData.get("probability");
            double[] rawPrediction = (double[]) inputData.get("rawPrediction");

            assertEquals(actual, predicted, EPSILON);
            assertArrayEquals(actualProbability, probability, EPSILON);
            assertArrayEquals(actualRaw, rawPrediction, EPSILON);
        }
    }

}

