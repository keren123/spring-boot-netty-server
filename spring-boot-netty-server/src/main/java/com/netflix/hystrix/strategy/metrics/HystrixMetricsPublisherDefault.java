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
package com.netflix.hystrix.strategy.metrics;

/**
 * Default implementation of {@link HystrixMetricsPublisher}.
 * <p>
 * See <a href="https://github.com/Netflix/Hystrix/wiki/Plugins">Wiki docs</a> about plugins for more information.
 *
 * @ExcludeFromJavadoc
 */
public class HystrixMetricsPublisherDefault extends HystrixMetricsPublisher
{

    private static HystrixMetricsPublisherDefault INSTANCE = new HystrixMetricsPublisherDefault();

    public static HystrixMetricsPublisher getInstance()
    {
        return INSTANCE;
    }

    private HystrixMetricsPublisherDefault()
    {
    }

}
