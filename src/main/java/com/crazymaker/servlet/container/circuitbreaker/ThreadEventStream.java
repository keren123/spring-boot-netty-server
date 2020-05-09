package com.crazymaker.servlet.container.circuitbreaker;


import rx.functions.Action1;
import rx.observers.Subscribers;
import rx.subjects.PublishSubject;
import rx.subjects.Subject;

public class ThreadEventStream
{

    private final Subject<RequestEvent, RequestEvent> writeOnlyRequestCompletionSubject;
    private final Subject<RequestEvent, RequestEvent> writeOnlyRequestFailureSubject;

    /**
     * 单例
     */
    private static ThreadEventStream existingStream = null;

    public static ThreadEventStream getInstance()
    {
        if (existingStream == null)
        {
            existingStream = new ThreadEventStream();

        }
        return existingStream;
    }

    private static final Action1<RequestEvent> writeCommandCompletionsToShardedStreams = new Action1<RequestEvent>()
    {
        @Override
        public void call(RequestEvent commandCompletion)
        {
            RequestFinishedStream commandStream = RequestFinishedStream.getInstance();
            commandStream.write(commandCompletion);
        }
    };

    private static final Action1<RequestEvent> writeCollapserExecutionsToShardedStreams = new Action1<RequestEvent>()
    {
        @Override
        public void call(RequestEvent collapserEvent)
        {
            RequestFinishedStream collapserStream = RequestFinishedStream.getInstance();
            collapserStream.write(collapserEvent);
        }
    };

    private ThreadEventStream()
    {

        writeOnlyRequestCompletionSubject = PublishSubject.create();

        writeOnlyRequestCompletionSubject
                .onBackpressureBuffer()
                .doOnNext(writeCommandCompletionsToShardedStreams)
                .unsafeSubscribe(Subscribers.empty());

        writeOnlyRequestFailureSubject = PublishSubject.create();
        writeOnlyRequestFailureSubject
                .onBackpressureBuffer()
                .doOnNext(writeCollapserExecutionsToShardedStreams)
                .unsafeSubscribe(Subscribers.empty());
    }


    public void shutdown()
    {
        writeOnlyRequestCompletionSubject.onCompleted();
        writeOnlyRequestFailureSubject.onCompleted();
    }

    public void executionDone(ExecutionResult executionResult)
    {
        RequestEvent event = RequestEvent.from(executionResult);
        writeOnlyRequestCompletionSubject.onNext(event);
    }

    public void executionFailure(ExecutionResult executionResult)
    {
        RequestEvent event = RequestEvent.from(executionResult);
        writeOnlyRequestFailureSubject.onNext(event);
    }


}
