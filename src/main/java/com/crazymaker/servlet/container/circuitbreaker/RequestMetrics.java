package com.crazymaker.servlet.container.circuitbreaker;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.functions.Func2;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


public class RequestMetrics
{

    @SuppressWarnings("unused")
    private static final Logger logger = LoggerFactory.getLogger(RequestMetrics.class);

    private static final EventType[] ALL_EVENT_TYPES = EventType.values();

    public static final Func2<long[], RequestEvent, long[]> appendEventToBucket = new Func2<long[], RequestEvent, long[]>()
    {
        @Override
        public long[] call(long[] initialCountArray, RequestEvent execution)
        {
            ExecutionResult.EventCounts eventCounts = execution.getEventCounts();
            for (EventType eventType : ALL_EVENT_TYPES)
            {

                initialCountArray[eventType.ordinal()] += eventCounts.getCount(eventType);


            }
            return initialCountArray;
        }
    };

    public static final Func2<long[], long[], long[]> bucketAggregator = new Func2<long[], long[], long[]>()
    {
        @Override
        public long[] call(long[] cumulativeEvents, long[] bucketEventCounts)
        {
            for (EventType eventType : ALL_EVENT_TYPES)
            {
                cumulativeEvents[eventType.ordinal()] += bucketEventCounts[eventType.ordinal()];
            }
            return cumulativeEvents;
        }
    };

    // String is HystrixCommandKey.name() (we can't use HystrixCommandKey directly as we can't guarantee it implements hashcode/equals correctly)
    private static final ConcurrentHashMap<String, RequestMetrics> metrics = new ConcurrentHashMap<String, RequestMetrics>();


    static RequestMetrics metricsInstance = null;

    /**
     * 单例
     */
    public static RequestMetrics getInstance()
    {
        if (null == metricsInstance)
        {
            metricsInstance = new RequestMetrics();
        }
        return metricsInstance;
    }


    static void reset()
    {

        metricsInstance.unsubscribeAll();

    }


    private final AtomicInteger concurrentExecutionCount = new AtomicInteger();

    private HealthComputeStream healthCountsStream;
    private final RollingRequestEventStream rollingRequestEventStream;
    private final CumulativeRequestStream cumulativeCommandEventCounterStream;

    RequestMetrics()
    {

        healthCountsStream = HealthComputeStream.getInstance();
        rollingRequestEventStream = RollingRequestEventStream.getInstance();
        cumulativeCommandEventCounterStream = CumulativeRequestStream.getInstance();

    }

    synchronized void resetStream()
    {
        healthCountsStream.unsubscribe();
        healthCountsStream = HealthComputeStream.getInstance();
    }

    public long getRollingCount(EventType eventType)
    {
        return rollingRequestEventStream.getLatest(eventType);
    }

    public long getCumulativeCount(EventType eventType)
    {
        return cumulativeCommandEventCounterStream.getLatest(eventType);
    }


    /**
     * Current number of concurrent executions of {@link HystrixCommand#run()};
     *
     * @return int
     */
    public int getCurrentConcurrentExecutionCount()
    {
        return concurrentExecutionCount.get();
    }


    void markCommandDone(ExecutionResult executionResult, boolean executionStarted)
    {
        ThreadEventStream.getInstance().executionDone(executionResult);
        if (executionStarted)
        {
            concurrentExecutionCount.decrementAndGet();
        }
    }


    public HealthCounts getHealthCounts()
    {
        return healthCountsStream.getLatest();
    }

    private void unsubscribeAll()
    {
        healthCountsStream.unsubscribe();
        rollingRequestEventStream.unsubscribe();
        cumulativeCommandEventCounterStream.unsubscribe();
    }

    /**
     * Number of requests during rolling window.
     * Number that failed (failure + success + timeout + threadPoolRejected + semaphoreRejected).
     * Error percentage;
     */
    public static class HealthCounts
    {
        private final long totalCount;
        private final long errorCount;
        private final int errorPercentage;

        HealthCounts(long total, long error)
        {
            this.totalCount = total;
            this.errorCount = error;
            if (totalCount > 0)
            {
                this.errorPercentage = (int) ((double) errorCount / totalCount * 100);
            } else
            {
                this.errorPercentage = 0;
            }
        }

        private static final HealthCounts EMPTY = new HealthCounts(0, 0);

        public long getTotalRequests()
        {
            return totalCount;
        }

        public long getErrorCount()
        {
            return errorCount;
        }

        public int getErrorPercentage()
        {
            return errorPercentage;
        }

        public HealthCounts plus(long[] eventTypeCounts)
        {
            long updatedTotalCount = totalCount;
            long updatedErrorCount = errorCount;

            long successCount = eventTypeCounts[EventType.SUCCESS.ordinal()];
            long failureCount = eventTypeCounts[EventType.FAILURE.ordinal()];

            updatedTotalCount += (successCount + failureCount);
            updatedErrorCount += (failureCount);
            return new HealthCounts(updatedTotalCount, updatedErrorCount);
        }

        public static HealthCounts empty()
        {
            return EMPTY;
        }

        public String toString()
        {
            return "HealthCounts[" + errorCount + " / " + totalCount + " : " + getErrorPercentage() + "%]";
        }
    }
}
