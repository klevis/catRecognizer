package ramo.klevis;

import org.datavec.api.io.filters.BalancedPathFilter;
import org.datavec.api.io.labels.ParentPathLabelGenerator;
import org.datavec.api.split.FileSplit;
import org.datavec.api.split.InputSplit;
import org.datavec.image.loader.BaseImageLoader;
import org.datavec.image.loader.NativeImageLoader;
import org.datavec.image.recordreader.ImageRecordReader;
import org.deeplearning4j.datasets.datavec.RecordReaderDataSetIterator;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.modelimport.keras.trainedmodels.TrainedModels;
import org.deeplearning4j.nn.transferlearning.FineTuneConfiguration;
import org.deeplearning4j.nn.transferlearning.TransferLearning;
import org.deeplearning4j.nn.transferlearning.TransferLearningHelper;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.deeplearning4j.ui.standalone.ClassPathResource;
import org.deeplearning4j.zoo.PretrainedType;
import org.deeplearning4j.zoo.ZooModel;
import org.deeplearning4j.zoo.model.VGG16;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.dataset.api.DataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.dataset.api.preprocessor.VGG16ImagePreProcessor;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.io.File;
import java.io.IOException;
import java.util.Random;

/**
 * Created by klevis.ramo on 12/26/2017.
 */
public class Run {
    private static final long seed = 12345;
    private static final Random randNumGen = new Random(seed);
    private static final String[] allowedExtensions = BaseImageLoader.ALLOWED_FORMATS;
    private static String DATA_PATH = "resources";

    public static void main(String[] args) throws IOException {
        ZooModel zooModel = new VGG16();
        ComputationGraph pretrainedNet = (ComputationGraph) zooModel.initPretrained(PretrainedType.IMAGENET);


        // Define the File Paths
        File trainData = new File(DATA_PATH + "/train");
        File testData = new File(DATA_PATH + "/test");


        ParentPathLabelGenerator labelGeneratorMaker = new ParentPathLabelGenerator();
        // Define the FileSplit(PATH, ALLOWED FORMATS,random)
        BalancedPathFilter pathFilter = new BalancedPathFilter(randNumGen, allowedExtensions, labelGeneratorMaker);
        FileSplit train = new FileSplit(trainData, NativeImageLoader.ALLOWED_FORMATS, randNumGen);
//        FileSplit test = new FileSplit(testData, NativeImageLoader.ALLOWED_FORMATS, randNumGen);

        // Extract the parent path as the image label

        ImageRecordReader imageRecordReader = new ImageRecordReader(224, 224, 3, labelGeneratorMaker);
        InputSplit[] sample = train.sample(pathFilter, 20, 80);
        imageRecordReader.initialize(sample[0]);

        DataSetIterator trainIterator = new RecordReaderDataSetIterator(imageRecordReader, 32, 1, 1);
        trainIterator.setPreProcessor(new VGG16ImagePreProcessor());


        FineTuneConfiguration fineTuneConf = new FineTuneConfiguration.Builder()
                .learningRate(5e-5)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .updater(Updater.NESTEROVS)
                .seed(seed)
                .build();

        ComputationGraph vgg16Transfer = new TransferLearning.GraphBuilder(pretrainedNet)
                .fineTuneConfiguration(fineTuneConf)
                .setFeatureExtractor("fc2")
                .removeVertexKeepConnections("predictions")
                .addLayer("predictions",
                        new OutputLayer.Builder(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD)
                                .nIn(4096).nOut(1)
                                .weightInit(WeightInit.XAVIER)
                                .activation(Activation.SOFTMAX).build(), "fc2")
                .build();
        vgg16Transfer.setListeners(new ScoreIterationListener(5));

        vgg16Transfer.fit(trainIterator);
    }
}
