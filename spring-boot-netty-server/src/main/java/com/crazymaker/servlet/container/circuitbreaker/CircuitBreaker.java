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
package com.crazymaker.servlet.container.circuitbreaker;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Circuit-breaker logic that is hooked into {@link RequestCommand } execution and will stop allowing executions if failures have gone past the defined threshold.
 * <p>
 * It will then allow single retries after a defined sleepWindow until the execution succeeds at which point it will again close the circuit and allow executions again.
 */
public interface CircuitBreaker
{

    /**
     * Every {@link RequestCommand } requests asks this if it is allowed to proceed or not.
     * <p>
     * This takes into account the half-open logic which allows some requests through when determining if it should be closed again.
     *
     * @return boolean whether a request should be permitted
     */
    public boolean allowRequest();

    /**
     * Whether the circuit is currently open (tripped).
     *
     * @return boolean state of circuit breaker
     */
    public boolean isOpen();

    /**
     * Invoked on successful executions from {@link RequestCommand } as part of feedback mechanism when in a half-open state.
     */
    void markSuccess();

    public static CircuitBreaker getInstance()
    {
        return CircuitBreakerImpl.getInstance();
    }


    /**
     * The default production implementation of {@link CircuitBreaker}.
     *
     * @ExcludeFromJavadoc
     * @ThreadSafe
     */
    class CircuitBreakerImpl implements CircuitBreaker
    {

        static CircuitBreaker existingInstance = null;

        public static CircuitBreaker getInstance()
        {
            if (existingInstance == null)
            {
                existingInstance = new CircuitBreakerImpl();
            }
            return existingInstance;
        }

        private final NettyWebServerConfig properties;
        private final RequestMetrics metrics;

        /* track whether this circuit is open/closed at any given point in time (default to false==closed) */
        private AtomicBoolean circuitOpen = new AtomicBoolean(false);

        /* when the circuit was marked open or was last allowed to try a 'singleTest' */
        private AtomicLong circuitOpenedOrLastTestedTime = new AtomicLong();

        protected CircuitBreakerImpl()
        {
            this.properties = NettyWebServerConfig.getInstance();
            this.metrics = RequestMetrics.getInstance();
        }

        public void markSuccess()
        {
            if (circuitOpen.get())
            {
                if (circuitOpen.compareAndSet(true, false))
                {
                    //win the thread race to reset metrics
                    //Unsubscribe from the current stream to reset the health counts stream.  This only affects the health counts view,
                    //and all other metric consumers are unaffected by the reset
                    metrics.resetStream();
                }
            }
        }

        @Override
        public boolean allowRequest()
        {

            return !isOpen() || allowSingleTest();
        }

        public boolean allowSingleTest()
        {
            long timeCircuitOpenedOrWasLastTested = circuitOpenedOrLastTestedTime.get();
            // 1) if the circuit is open
            // 2) and it's been longer than 'sleepWindow' since we opened the circuit
            if (circuitOpen.get() && System.currentTimeMillis() > timeCircuitOpenedOrWasLastTested + properties.getCircuitBreakerSleepWindowInMilliseconds())
            {
                // We push the 'circuitOpenedTime' ahead by 'sleepWindow' since we have allowed one request to try.
                // If it succeeds the circuit will be closed, otherwise another singleTest will be allowed at the end of the 'sleepWindow'.
                if (circuitOpenedOrLastTestedTime.compareAndSet(timeCircuitOpenedOrWasLastTested, System.currentTimeMillis()))
                {
                    // if this returns true that means we set the time so we'll return true to allow the singleTest
                    // if it returned false it means another thread raced us and allowed the singleTest before we did
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean isOpen()
        {
            if (circuitOpen.get())
            {
                // if we're open we immediately return true and don't bother attempting to 'close' ourself as that is left to allowSingleTest and a subsequent successful test to close
                return true;
            }

            // we're closed, so let's see if errors have made us so we should trip the circuit open
            RequestMetrics.HealthCounts health = metrics.getHealthCounts();

            // check if we are past the statisticalWindowVolumeThreshold
            if (health.getTotalRequests() < properties.getCircuitBreakerRequestVolumeThreshold())
            {
                // we are not past the minimum volume threshold for the statisticalWindow so we'll return false immediately and not calculate anything
                return false;
            }

            if (health.getErrorPercentage() < properties.getCircuitBreakerErrorThresholdPercentage())
            {
                return false;
            } else
            {
                // our failure rate is too high, trip the circuit
                if (circuitOpen.compareAndSet(false, true))
                {
                    // if the previousValue was false then we want to set the currentTime
                    circuitOpenedOrLastTestedTime.set(System.currentTimeMillis());
                    return true;
                } else
                {
                    // How could previousValue be true? If another thread was going through this code at the same time a race-condition could have
                    // caused another thread to set it to true already even though we were in the process of doing the same
                    // In this case, we know the circuit is open, so let the other thread set the currentTime and report back that the circuit is open
                    return true;
                }
            }
        }

    }
}



