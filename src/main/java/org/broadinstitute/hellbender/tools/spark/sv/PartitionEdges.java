package org.broadinstitute.hellbender.tools.spark.sv;

public class PartitionEdges {
    private final int beginningContigID;
    private final int beginningPosition;
    private final int endingContigID;
    private final int endingPosition;

    /** A constructor that will always report onEdge to be false. */
    public PartitionEdges() {
        beginningContigID = -1;
        beginningPosition = 0;
        endingContigID = -1;
        endingPosition = 0;
    }

    /** A constructor from the metadata with a specified edge width. */
    public PartitionEdges( final int partitionIdx,
                           final ReadMetadata readMetadata,
                           final int edgeWidth ) {
        if ( partitionIdx == 0 ) {
            beginningContigID = ReadMetadata.PartitionBounds.UNMAPPED;
            beginningPosition = -1;
        } else {
            final ReadMetadata.PartitionBounds bounds = readMetadata.getPartitionBounds(partitionIdx - 1);
            beginningContigID = bounds.getLastContigID();
            if ( beginningContigID == ReadMetadata.PartitionBounds.UNMAPPED ) {
                beginningPosition = -1;
            } else {
                beginningPosition = bounds.getLastStart() + edgeWidth;
            }
        }
        if ( partitionIdx == readMetadata.getNPartitions() - 1 ) {
            endingContigID = ReadMetadata.PartitionBounds.UNMAPPED;
            endingPosition = Integer.MAX_VALUE;
        } else {
            final ReadMetadata.PartitionBounds bounds = readMetadata.getPartitionBounds(partitionIdx + 1);
            endingContigID = bounds.getFirstContigID();
            if ( endingContigID == ReadMetadata.PartitionBounds.UNMAPPED ) {
                endingPosition = Integer.MAX_VALUE;
            } else {
                endingPosition = bounds.getFirstStart() - edgeWidth;
            }
        }
    }

    public boolean onEdge( final SVInterval interval ) {
        return onLeadingEdge(interval) || onTrailingEdge(interval);
    }

    public boolean onLeadingEdge( final SVInterval interval ) {
        return interval.getContig() == beginningContigID && interval.getStart() < beginningPosition;
    }

    public boolean onTrailingEdge( final SVInterval interval ) {
        return interval.getContig() == endingContigID && interval.getStart() >= endingPosition;
    }
}
