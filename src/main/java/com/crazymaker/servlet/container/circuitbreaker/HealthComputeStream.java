package com.crazymaker.servlet.container.circuitbreaker;


import rx.functions.Func2;


public class HealthComputeStream extends BaseRollingStream<RequestEvent, long[], RequestMetrics.HealthCounts>
{

    private static final int NUM_EVENT_TYPES = EventType.values().length;

    private static final Func2<RequestMetrics.HealthCounts, long[], RequestMetrics.HealthCounts> healthCheckAccumulator = new Func2<RequestMetrics.HealthCounts, long[], RequestMetrics.HealthCounts>()
    {
        @Override
        public RequestMetrics.HealthCounts call(RequestMetrics.HealthCounts healthCounts, long[] bucketEventCounts)
        {
            return healthCounts.plus(bucketEventCounts);
        }
    };

    private static HealthComputeStream existingStream = null;

    public static HealthComputeStream getInstance()
    {
        if (null == existingStream)
        {
            int bucketSizeInMs = 100;

            int numBuckets = 1000 / bucketSizeInMs;
            Func2<long[], RequestEvent, long[]> reduceCommandCompletion = RequestMetrics.appendEventToBucket;

            existingStream = new HealthComputeStream(numBuckets, bucketSizeInMs, RequestMetrics.appendEventToBucket);
        }
        return existingStream;
    }


    private HealthComputeStream(final int numBuckets, final int bucketSizeInMs,
                                Func2<long[], RequestEvent, long[]> reduceCommandCompletion)
    {
        super(RequestFinishedStream.getInstance(), numBuckets, bucketSizeInMs, reduceCommandCompletion, healthCheckAccumulator);
    }

    @Override
    long[] getEmptyBucketSummary()
    {
        return new long[NUM_EVENT_TYPES];
    }

    @Override
    RequestMetrics.HealthCounts getEmptyOutputValue()
    {
        return RequestMetrics.HealthCounts.empty();
    }
}
