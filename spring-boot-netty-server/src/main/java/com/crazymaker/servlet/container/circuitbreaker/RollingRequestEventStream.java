package com.crazymaker.servlet.container.circuitbreaker;

import rx.functions.Func2;

public class RollingRequestEventStream
        extends BaseRollingStream<RequestEvent, long[], long[]>
{

    private static final int NUM_EVENT_TYPES = EventType.values().length;


    private RollingRequestEventStream(int numCounterBuckets, int counterBucketSizeInMs,
                                      Func2<long[], RequestEvent, long[]> reduceCommandCompletion,
                                      Func2<long[], long[], long[]> reduceBucket)
    {
        super(RequestFinishedStream.getInstance(),
                numCounterBuckets,
                counterBucketSizeInMs,
                reduceCommandCompletion,
                reduceBucket);
    }

    @Override
    long[] getEmptyBucketSummary()
    {
        return new long[NUM_EVENT_TYPES];
    }

    @Override
    long[] getEmptyOutputValue()
    {
        return new long[NUM_EVENT_TYPES];
    }

    public long getLatest(EventType eventType)
    {
        long[] latest = getLatest();
        return latest[eventType.ordinal()];
    }


    private static RollingRequestEventStream existingStream = null;

    public static RollingRequestEventStream getInstance()
    {
        if (existingStream == null)
        {
            int counterMetricWindow = 1000;
            int numCounterBuckets = 10;
            int counterBucketSizeInMs = counterMetricWindow / numCounterBuckets;
            existingStream = new RollingRequestEventStream(
                    numCounterBuckets, counterBucketSizeInMs,
                    RequestMetrics.appendEventToBucket,
                    RequestMetrics.bucketAggregator);

        }
        return existingStream;
    }

}
