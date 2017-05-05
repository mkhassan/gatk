package org.broadinstitute.hellbender.tools.spark.sv;

import org.apache.commons.collections4.iterators.SingletonIterator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.Iterator;

/**
 * A class to examine a stream of BreakpointEvidence, and group it into Intervals.
 */
public final class BreakpointEvidenceClusterer implements Function<BreakpointEvidence, Iterator<BreakpointEvidence>> {
    private final int gapSize;
    private final PartitionEdges partitionEdges;
    private SVInterval curInterval;
    private int curWeight;
    private static final Iterator<BreakpointEvidence> EMPTY_ITERATOR = Collections.emptyIterator();

    public BreakpointEvidenceClusterer( final int gapSize, final PartitionEdges partitionEdges ) {
        this.gapSize = gapSize;
        this.partitionEdges = partitionEdges;
        this.curWeight = 0;
    }

    @Override
    public Iterator<BreakpointEvidence> apply( final BreakpointEvidence evidence ) {
        if ( partitionEdges.onEdge(evidence.getLocation()) ) {
            if ( curInterval == null ) {
                return new SingletonIterator<>(evidence);
            }
            List<BreakpointEvidence> evList = new ArrayList<>(2);
            evList.add(new BreakpointEvidence(curInterval,curWeight,true));
            evList.add(evidence);
            curInterval = null;
            return evList.iterator();
        }
        Iterator<BreakpointEvidence> result = EMPTY_ITERATOR;
        final SVInterval interval = evidence.getLocation();
        final int weight = evidence.getWeight();
        if ( curInterval == null ) {
            curInterval = interval;
            curWeight = weight;
        } else if ( curInterval.gapLen(interval) < gapSize ) {
            curInterval = curInterval.join(interval);
            curWeight += weight;
        } else {
            result = new SingletonIterator<>(new BreakpointEvidence(curInterval,curWeight,true));
            curInterval = interval;
            curWeight = weight;
        }
        return result;
    }
}
