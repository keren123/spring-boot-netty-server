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
package com.crazymaker.servlet.container.circuitbreaker;

import rx.Observable;
import rx.functions.Action0;
import rx.functions.Func1;
import rx.functions.Func2;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class BaseRollingStream<Event extends RequestEvent, Bucket, Output>
        extends BaseBucketedStream<Event, Bucket, Output>
{
    private Observable<Output> sourceStream;
    private final AtomicBoolean isSourceCurrentlySubscribed = new AtomicBoolean(false);

    protected BaseRollingStream(EventStream<Event> stream,
                                final int numBuckets,
                                int bucketSizeInMs,
                                final Func2<Bucket, Event, Bucket> appendRawEventToBucket,
                                final Func2<Output, Bucket, Output> reduceBucket)
    {
        super(stream, numBuckets, bucketSizeInMs, appendRawEventToBucket);
        Func1<Observable<Bucket>, Observable<Output>> reduceWindowToSummary = new Func1<Observable<Bucket>, Observable<Output>>()
        {
            @Override
            public Observable<Output> call(Observable<Bucket> window)
            {
                return window.scan(getEmptyOutputValue(), reduceBucket).skip(numBuckets);
            }
        };
        this.sourceStream = bucketedStream      //stream broken up into buckets
                .window(numBuckets, 1)          //emit overlapping windows of buckets
                .flatMap(reduceWindowToSummary) //convert a window of bucket-summaries into a single summary
                .doOnSubscribe(new Action0()
                {
                    @Override
                    public void call()
                    {
                        isSourceCurrentlySubscribed.set(true);
                    }
                })
                .doOnUnsubscribe(new Action0()
                {
                    @Override
                    public void call()
                    {
                        isSourceCurrentlySubscribed.set(false);
                    }
                })
                .share()    //multiple subscribers should get same data
                .onBackpressureDrop(); //if there are slow consumers, data should not buffer
    }

    @Override
    public Observable<Output> observe()
    {
        return sourceStream;
    }

    protected boolean isSourceCurrentlySubscribed()
    {
        return isSourceCurrentlySubscribed.get();
    }
}
