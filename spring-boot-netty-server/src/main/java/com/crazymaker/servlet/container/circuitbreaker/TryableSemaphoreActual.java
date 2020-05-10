package com.crazymaker.servlet.container.circuitbreaker;

import java.util.concurrent.atomic.AtomicInteger;

interface TryableSemaphore
{

    /**
     * Use like this:
     * <p>
     *
     * <pre>
     * if (s.tryAcquire()) {
     * try {
     * // do work that is protected by 's'
     * } finally {
     * s.release();
     * }
     * }
     * </pre>
     *
     * @return boolean
     */
    public abstract boolean tryAcquire();

    /**
     * ONLY call release if tryAcquire returned true.
     * <p>
     *
     * <pre>
     * if (s.tryAcquire()) {
     * try {
     * // do work that is protected by 's'
     * } finally {
     * s.release();
     * }
     * }
     * </pre>
     */
    public abstract void release();

    public abstract int getNumberOfPermitsUsed();

    public static final TryableSemaphore DEFAULT = new TryableSemaphoreActual(10);


    class TryableSemaphoreActual implements TryableSemaphore
    {
        private final Integer numberOfPermits;
        private final AtomicInteger count = new AtomicInteger(0);

        public TryableSemaphoreActual(Integer numberOfPermits)
        {
            this.numberOfPermits = numberOfPermits;
        }

        @Override
        public boolean tryAcquire()
        {
            int currentCount = count.incrementAndGet();
            if (currentCount > numberOfPermits.intValue())
            {
                count.decrementAndGet();

                return false;
            } else
            {

                return true;
            }
        }

        @Override
        public void release()
        {
            count.incrementAndGet();
        }

        @Override
        public int getNumberOfPermitsUsed()
        {
            return count.get();
        }

    }


}