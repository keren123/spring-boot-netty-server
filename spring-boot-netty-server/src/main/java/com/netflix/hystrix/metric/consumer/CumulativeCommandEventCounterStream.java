/**
 * Copyright 2015 Netflix, Inc.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.metric.consumer;

import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandMetrics;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.metric.HystrixCommandCompletion;
import com.netflix.hystrix.metric.HystrixCommandCompletionStream;
import rx.functions.Func2;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Maintains a stream of event counters for a given Command.
 * There is no rolling window abstraction on this stream - every event since the start of the JVM is kept track of.
 * The event counters object is calculated on the same schedule as the rolling abstract {@link RollingCommandEventCounterStream},
 * so bucket rolls correspond to new data in this stream, though data never goes out of window in this stream.
 * <p>
 * Therefore, a new set of counters is produced every t2 (=t1/b) milliseconds
 * t1 = {@link HystrixCommandProperties#metricsRollingStatisticalWindowInMilliseconds()}
 * b = {@link HystrixCommandProperties#metricsRollingStatisticalWindowBuckets()}
 * <p>
 * These values are stable - there's no peeking into a bucket until it is emitted
 * <p>
 * These values get produced and cached in this class.  This value (the latest observed value) may be queried using {@link #getLatest(HystrixEventType)}.
 */
public class CumulativeCommandEventCounterStream extends BucketedCumulativeCounterStream<HystrixCommandCompletion, long[], long[]>
{

    private static final ConcurrentMap<String, CumulativeCommandEventCounterStream> streams = new ConcurrentHashMap<String, CumulativeCommandEventCounterStream>();

    private static final int NUM_EVENT_TYPES = HystrixEventType.values().length;

    public static CumulativeCommandEventCounterStream getInstance(HystrixCommandKey commandKey, HystrixCommandProperties properties)
    {
        final int counterMetricWindow = properties.metricsRollingStatisticalWindowInMilliseconds().get();
        final int numCounterBuckets = properties.metricsRollingStatisticalWindowBuckets().get();
        final int counterBucketSizeInMs = counterMetricWindow / numCounterBuckets;

        return getInstance(commandKey, numCounterBuckets, counterBucketSizeInMs);
    }

    public static CumulativeCommandEventCounterStream getInstance(HystrixCommandKey commandKey, int numBuckets, int bucketSizeInMs)
    {
        CumulativeCommandEventCounterStream initialStream = streams.get(commandKey.name());
        if (initialStream != null)
        {
            return initialStream;
        } else
        {
            synchronized (CumulativeCommandEventCounterStream.class)
            {
                CumulativeCommandEventCounterStream existingStream = streams.get(commandKey.name());
                if (existingStream == null)
                {
                    CumulativeCommandEventCounterStream newStream = new CumulativeCommandEventCounterStream(commandKey, numBuckets, bucketSizeInMs,
                            HystrixCommandMetrics.appendEventToBucket, HystrixCommandMetrics.bucketAggregator);
                    streams.putIfAbsent(commandKey.name(), newStream);
                    return newStream;
                } else
                {
                    return existingStream;
                }
            }
        }
    }

    public static void reset()
    {
        streams.clear();
    }

    private CumulativeCommandEventCounterStream(HystrixCommandKey commandKey, int numCounterBuckets, int counterBucketSizeInMs,
                                                Func2<long[], HystrixCommandCompletion, long[]> reduceCommandCompletion,
                                                Func2<long[], long[], long[]> reduceBucket)
    {
        super(HystrixCommandCompletionStream.getInstance(commandKey), numCounterBuckets, counterBucketSizeInMs, reduceCommandCompletion, reduceBucket);
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

    public long getLatest(HystrixEventType eventType)
    {
        return getLatest()[eventType.ordinal()];
    }
}
