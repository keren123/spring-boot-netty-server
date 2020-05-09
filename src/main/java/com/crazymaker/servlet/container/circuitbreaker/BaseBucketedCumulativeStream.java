package com.crazymaker.servlet.container.circuitbreaker;

import rx.Observable;
import rx.functions.Action0;
import rx.functions.Func2;

import java.util.concurrent.atomic.AtomicBoolean;


public abstract class BaseBucketedCumulativeStream<Event extends RequestEvent, Bucket, Output> extends BaseBucketedStream<Event, Bucket, Output>
{
    private Observable<Output> sourceStream;
    private final AtomicBoolean isSourceCurrentlySubscribed = new AtomicBoolean(false);

    protected BaseBucketedCumulativeStream(EventStream<Event> stream, int numBuckets, int bucketSizeInMs,
                                           Func2<Bucket, Event, Bucket> reduceCommandCompletion,
                                           Func2<Output, Bucket, Output> reduceBucket)
    {
        super(stream, numBuckets, bucketSizeInMs, reduceCommandCompletion);

        this.sourceStream = bucketedStream
                .scan(getEmptyOutputValue(), reduceBucket)
                .skip(numBuckets)
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
                .share()                        //multiple subscribers should get same data
                .onBackpressureDrop();          //if there are slow consumers, data should not buffer
    }

    @Override
    public Observable<Output> observe()
    {
        return sourceStream;
    }
}
