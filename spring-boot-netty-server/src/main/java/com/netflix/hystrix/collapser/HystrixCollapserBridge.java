/**
 * Copyright 2015 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.collapser;

import com.netflix.hystrix.HystrixCollapser.CollapsedRequest;
import com.netflix.hystrix.HystrixCollapserKey;
import rx.Observable;

import java.util.Collection;

/**
 * Bridge between HystrixCollapser and RequestCollapser to expose 'protected' and 'private' functionality across packages.
 *
 * @param <BatchReturnType>
 * @param <ResponseType>
 * @param <RequestArgumentType>
 */
public interface HystrixCollapserBridge<BatchReturnType, ResponseType, RequestArgumentType>
{

    Collection<Collection<CollapsedRequest<ResponseType, RequestArgumentType>>> shardRequests(Collection<CollapsedRequest<ResponseType, RequestArgumentType>> requests);

    Observable<BatchReturnType> createObservableCommand(Collection<CollapsedRequest<ResponseType, RequestArgumentType>> requests);

    Observable<Void> mapResponseToRequests(Observable<BatchReturnType> batchResponse, Collection<CollapsedRequest<ResponseType, RequestArgumentType>> requests);

    HystrixCollapserKey getCollapserKey();

}
