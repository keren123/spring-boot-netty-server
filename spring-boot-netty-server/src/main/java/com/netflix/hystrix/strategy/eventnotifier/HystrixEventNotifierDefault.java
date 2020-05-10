/**
 * Copyright 2012 Netflix, Inc.
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
package com.netflix.hystrix.strategy.eventnotifier;


import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixEventType;

/**
 * Default implementations of {@link HystrixEventNotifier} that does nothing.
 *
 * @ExcludeFromJavadoc
 */
public class HystrixEventNotifierDefault extends HystrixEventNotifier
{

    private static HystrixEventNotifierDefault INSTANCE = new HystrixEventNotifierDefault();

    private HystrixEventNotifierDefault()
    {

    }

    public static HystrixEventNotifier getInstance()
    {
        return INSTANCE;
    }

    /**
     * Called for every event fired.
     * <p>
     * <b>Default Implementation: </b> Does nothing
     *
     * @param eventType event type
     * @param key       event key
     */
    public void markEvent(HystrixEventType eventType, HystrixCommandKey key)
    {
        // do nothing
    }


}
