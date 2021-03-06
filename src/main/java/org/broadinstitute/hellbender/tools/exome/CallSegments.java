package org.broadinstitute.hellbender.tools.exome;

import org.broadinstitute.barclay.argparser.*;
import org.broadinstitute.barclay.argparser.Argument;
import org.broadinstitute.barclay.argparser.CommandLineProgramProperties;
import org.broadinstitute.barclay.help.DocumentedFeature;
import org.broadinstitute.hellbender.cmdline.*;
import org.broadinstitute.hellbender.cmdline.programgroups.CopyNumberProgramGroup;
import org.broadinstitute.hellbender.exceptions.UserException;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Calls segments as amplified, deleted or copy number neutral given files containing tangent-normalized
 * read counts by target and a list of segments.
 *
 * @author David Benjamin
 *
 * <h3>Examples</h3>
 *
 * <pre>
 * java -Xmx4g -jar $gatk_jar CallSegments \
 *   --tangentNormalized tn_coverage.tn.tsv \
 *   --segments segments.seg \
 *   --output entity_id.called
 * </pre>
 *
 * <p>To call on ReCapSeg legacy format data, set --legacy option to true. </p>
 */
@CommandLineProgramProperties(
        summary = "Call segments as amplified, deleted, or copy number neutral given files containing tangent-normalized" +
                " read counts by target and a list of segments",
        oneLineSummary = "Call segments as amplified, deleted, or copy number neutral",
        programGroup = CopyNumberProgramGroup.class
)
@DocumentedFeature
public final class CallSegments extends CommandLineProgram{

    @Argument(
            doc = "Input tangent-normalized read counts file.",
            fullName = ExomeStandardArgumentDefinitions.TANGENT_NORMALIZED_COUNTS_FILE_LONG_NAME,
            shortName = ExomeStandardArgumentDefinitions.TANGENT_NORMALIZED_COUNTS_FILE_SHORT_NAME,
            optional = false
    )
    protected File tangentNormalizedCoverageFile;

    @Argument(
            doc = "Input segment file.",
            fullName = ExomeStandardArgumentDefinitions.SEGMENT_FILE_LONG_NAME,
            shortName = ExomeStandardArgumentDefinitions.SEGMENT_FILE_SHORT_NAME,
            optional = false
    )
    protected File segmentsFile;

    @Argument(
            doc = "Output file for called segments.",
            fullName = StandardArgumentDefinitions.OUTPUT_LONG_NAME,
            shortName = StandardArgumentDefinitions.OUTPUT_SHORT_NAME,
            optional = false
    )
    protected File outFile;

    @Argument(
            doc = "(Advanced) Assume that the input seg file is using the legacy format (e.g. generated by python ReCapSeg)."
            + "  NOTE:  The output will be in the format used by this program -- i.e. no preservation of legacy field names, etc.",
            fullName = ExomeStandardArgumentDefinitions.LEGACY_SEG_FILE_LONG_NAME,
            shortName = ExomeStandardArgumentDefinitions.LEGACY_SEG_FILE_SHORT_NAME,
            optional = true
    )
    protected boolean isLegacyFormatSegFile = false;

    @Override
    protected Object doWork() {
        final ReadCountCollection tangentNormalizedCoverage;
        try {
            tangentNormalizedCoverage = ReadCountCollectionUtils.parse(tangentNormalizedCoverageFile);
        } catch (final IOException e) {
            throw new UserException.CouldNotReadInputFile(tangentNormalizedCoverageFile, e);
        }
        List<ModeledSegment> segments = isLegacyFormatSegFile ? SegmentUtils.readModeledSegmentsFromLegacySegmentFile(segmentsFile) :
               SegmentUtils.readModeledSegmentsFromSegmentFile(segmentsFile);

        ReCapSegCaller.makeCalls(tangentNormalizedCoverage, segments);

        final String sample = ReadCountCollectionUtils.getSampleNameForCLIsFromReadCountsFile(tangentNormalizedCoverageFile);
        SegmentUtils.writeModeledSegmentFile(outFile, segments, sample);
        return "SUCCESS";
    }
}
