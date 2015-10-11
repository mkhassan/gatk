package org.broadinstitute.hellbender.tools.spark;

import com.google.cloud.dataflow.sdk.transforms.SerializableFunction;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.broadinstitute.hellbender.cmdline.Argument;
import org.broadinstitute.hellbender.cmdline.ArgumentCollection;
import org.broadinstitute.hellbender.cmdline.CommandLineProgramProperties;
import org.broadinstitute.hellbender.cmdline.StandardArgumentDefinitions;
import org.broadinstitute.hellbender.cmdline.programgroups.SparkProgramGroup;
import org.broadinstitute.hellbender.engine.dataflow.datasources.ReadContextData;
import org.broadinstitute.hellbender.engine.spark.AddContextDataToReadSpark;
import org.broadinstitute.hellbender.engine.spark.GATKSparkTool;
import org.broadinstitute.hellbender.engine.spark.datasources.VariantsSparkSource;
import org.broadinstitute.hellbender.exceptions.GATKException;
import org.broadinstitute.hellbender.tools.spark.transforms.BaseRecalibratorSparkFn;
import org.broadinstitute.hellbender.utils.SimpleInterval;
import org.broadinstitute.hellbender.utils.dataflow.BucketUtils;
import org.broadinstitute.hellbender.utils.read.GATKRead;
import org.broadinstitute.hellbender.utils.recalibration.BaseRecalibrationEngine;
import org.broadinstitute.hellbender.utils.recalibration.RecalUtils;
import org.broadinstitute.hellbender.utils.recalibration.RecalibrationArgumentCollection;
import org.broadinstitute.hellbender.utils.recalibration.RecalibrationReport;
import org.broadinstitute.hellbender.utils.recalibration.covariates.StandardCovariateList;
import org.broadinstitute.hellbender.engine.spark.JoinStrategy;
import org.broadinstitute.hellbender.utils.variant.Variant;

import java.io.PrintStream;
import java.util.List;

@CommandLineProgramProperties(
        summary = "Base Quality Score Recalibration (BQSR) -- Generates recalibration table based on various user-specified covariates (such as read group, reported quality score, machine cycle, and nucleotide context).",
        oneLineSummary = "Generates recalibration table",
        programGroup = SparkProgramGroup.class
)
public class BaseRecalibratorSpark extends GATKSparkTool {
    private static final long serialVersionUID = 1L;

    @Override
    public boolean requiresReads() { return true; }

    @Override
    public boolean requiresReference() { return true; }

    @Override
    public SerializableFunction<GATKRead, SimpleInterval> getReferenceWindowFunction() {
        return BaseRecalibrationEngine.BQSR_REFERENCE_WINDOW_FUNCTION;
    }

    @Argument(doc = "the known variants", shortName = "knownSites", fullName = "knownSites", optional = false)
    private List<String> knownVariants;

    @Argument(doc = "the join strategy for reference bases", shortName = "joinStrategy", fullName = "joinStrategy", optional = true)
    private JoinStrategy joinStrategy = JoinStrategy.SHUFFLE;

    @Argument(doc = "Path to save the final recalibration tables to.",
            shortName = StandardArgumentDefinitions.OUTPUT_SHORT_NAME, fullName = StandardArgumentDefinitions.OUTPUT_LONG_NAME, optional = false)
    private String outputTablesPath = null;

    /**
     * all the command line arguments for BQSR and its covariates
     */
    @ArgumentCollection(doc = "all the command line arguments for BQSR and its covariates")
    private final RecalibrationArgumentCollection bqsrArgs = new RecalibrationArgumentCollection();

    @Override
    protected void runTool( JavaSparkContext ctx ) {
        JavaRDD<GATKRead> initialReads = getReads();

        VariantsSparkSource variantsSparkSource = new VariantsSparkSource(ctx);
        if ( knownVariants.size() > 1 ) {
            throw new GATKException("Cannot currently handle more than one known sites file, " +
                    "as getParallelVariants(List) is broken");
        }
        JavaRDD<Variant> bqsrKnownVariants = variantsSparkSource.getParallelVariants(knownVariants.get(0));

        // TODO: Look into broadcasting the reference to all of the workers. This would make AddContextDataToReadSpark
        // TODO: and ApplyBQSRStub simpler (#855).
        JavaPairRDD<GATKRead, ReadContextData> rddReadContext = AddContextDataToReadSpark.add(initialReads, getReference(), bqsrKnownVariants, joinStrategy);
        // TODO: broadcast the reads header?
        final RecalibrationReport bqsrReport = BaseRecalibratorSparkFn.apply(rddReadContext, getHeaderForReads(), getReferenceSequenceDictionary(), bqsrArgs);

        try ( final PrintStream reportStream = new PrintStream(BucketUtils.createFile(outputTablesPath, getAuthenticatedGCSOptions())) ) {
            RecalUtils.outputRecalibrationReport(reportStream, bqsrArgs, bqsrReport.getQuantizationInfo(), bqsrReport.getRecalibrationTables(), new StandardCovariateList(bqsrArgs, getHeaderForReads()), true);
        }
    }
}