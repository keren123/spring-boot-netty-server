package com.crazymaker.servlet.container.circuitbreaker;

import rx.Observable;
import rx.subjects.PublishSubject;
import rx.subjects.SerializedSubject;
import rx.subjects.Subject;

public class RequestFinishedStream implements EventStream<RequestEvent>
{

    private static RequestFinishedStream existingStream = null;

    public static RequestFinishedStream getInstance()
    {
        if (existingStream == null)
        {
            existingStream = new RequestFinishedStream();

        }
        return existingStream;
    }


    private final Subject<RequestEvent, RequestEvent> writeOnlySubject;
    private final Observable<RequestEvent> readOnlyStream;

    private RequestFinishedStream()
    {
        this.writeOnlySubject = new SerializedSubject<RequestEvent, RequestEvent>(PublishSubject.<RequestEvent>create());
        this.readOnlyStream = writeOnlySubject.share();
    }


    public void write(RequestEvent event)
    {
        writeOnlySubject.onNext(event);
    }


    public Observable<RequestEvent> observe()
    {
        return readOnlyStream;
    }


}
