package com.crazymaker.servlet.container.circuitbreaker;

import rx.Observable;


public interface EventStream<E extends RequestEvent>
{

    Observable<E> observe();
}
