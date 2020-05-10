package com.crazymaker.servlet.container.circuitbreaker;


import rx.functions.Func2;

public class CumulativeRequestStream extends BaseBucketedCumulativeStream<RequestEvent, long[], long[]>
{


    private static final int NUM_EVENT_TYPES = EventType.values().length;

    private static CumulativeRequestStream existingStream = null;

    public static CumulativeRequestStream getInstance()
    {
        if (existingStream == null)
        {
            int counterMetricWindow = 1000;
            int numCounterBuckets = 10;
            int counterBucketSizeInMs = counterMetricWindow / numCounterBuckets;

            Func2<long[], RequestEvent, long[]> reduceCommandCompletion = RequestMetrics.appendEventToBucket;
            Func2<long[], long[], long[]> reduceBucket = RequestMetrics.bucketAggregator;
            existingStream = new CumulativeRequestStream(numCounterBuckets,
                    counterBucketSizeInMs,
                    reduceCommandCompletion, reduceBucket);

        }
        return existingStream;
    }

    private CumulativeRequestStream(int numCounterBuckets, int counterBucketSizeInMs,
                                    Func2<long[], RequestEvent, long[]> reduceCommandCompletion,
                                    Func2<long[], long[], long[]> reduceBucket)
    {
        super(RequestFinishedStream.getInstance(),
                numCounterBuckets,
                counterBucketSizeInMs,
                reduceCommandCompletion, reduceBucket);
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
        return getLatest()[eventType.ordinal()];
    }
}
