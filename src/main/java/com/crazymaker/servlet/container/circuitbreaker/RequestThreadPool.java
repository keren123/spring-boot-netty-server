package com.crazymaker.servlet.container.circuitbreaker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Scheduler;
import rx.functions.Func0;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


public interface RequestThreadPool
{

    /**
     * Implementation of {@link ThreadPoolExecutor}.
     *
     * @return ThreadPoolExecutor
     */
    public ExecutorService getExecutor();

    public Scheduler getScheduler();

    public Scheduler getScheduler(Func0<Boolean> shouldInterruptThread);


    /**
     * Whether the queue will allow adding an item to it.
     * <p>
     * This allows dynamic control of the max queueSize versus whatever the actual max queueSize is so that dynamic changes can be done via property changes rather than needing an app
     * restart to adjust when commands should be rejected from queuing up.
     *
     * @return boolean whether there is space on the queue
     */
    boolean isQueueSpaceAvailable();

    /**
     * @ExcludeFromJavadoc
     */
    class Factory
    {

        private static RequestThreadPool instance = null;

        public static RequestThreadPool getInstance()
        {
            if (instance == null)
            {
                instance = new RequestThreadPoolDefault();


            }
            return instance;
        }


        static synchronized void shutdown()
        {

            instance.getExecutor().shutdown();

        }
    }

    /**
     * @ExcludeFromJavadoc
     * @ThreadSafe
     */
    static class RequestThreadPoolDefault implements RequestThreadPool
    {
        private static final Logger logger = LoggerFactory.getLogger(RequestThreadPoolDefault.class);

        private final NettyWebServerConfig properties = NettyWebServerConfig.getInstance();
        private final BlockingQueue<Runnable> queue;
        private final int queueSize;
        private static Scheduler scheduler;

        //todo
        private final ThreadPoolExecutor executor = null;

        public RequestThreadPoolDefault()
        {
            this.queueSize = properties.getMaxQueueSize();

            this.queue = this.executor.getQueue();

        }

        @Override
        public ThreadPoolExecutor getExecutor()
        {
            touchConfig();
            return executor;
        }

        @Override
        public Scheduler getScheduler()
        {
            if (null == scheduler)
            //interrupt underlying threads on timeout
            {
                scheduler = getScheduler(new Func0<Boolean>()
                {
                    @Override
                    public Boolean call()
                    {
                        return true;
                    }
                });
            }
            return scheduler;
        }

        @Override
        public Scheduler getScheduler(Func0<Boolean> shouldInterruptThread)
        {

            touchConfig();
            RequestScheduler.ThreadPoolScheduler scheduler =
                    new RequestScheduler.ThreadPoolScheduler(this, shouldInterruptThread);
            return new RequestScheduler(scheduler);
        }

        // allow us to change things via fast-properties by setting it each time
        private void touchConfig()
        {
            final int dynamicCoreSize = properties.getCoreSize();
            final int configuredMaximumSize = properties.getMaximumSize();
            int dynamicMaximumSize = properties.getActualMaximumSize();
            final boolean allowSizesToDiverge = properties.getAllowMaximumSizeToDivergeFromCoreSize();
            boolean maxTooLow = false;

            if (allowSizesToDiverge && configuredMaximumSize < dynamicCoreSize)
            {
                //if user sets maximum < core (or defaults get us there), we need to maintain invariant of core <= maximum
                dynamicMaximumSize = dynamicCoreSize;
                maxTooLow = true;
            }

            // In JDK 6, setCorePoolSize and setMaximumPoolSize will execute a lock operation. Avoid them if the pool size is not changed.
            if (executor.getCorePoolSize() != dynamicCoreSize || (allowSizesToDiverge && executor.getMaximumPoolSize() != dynamicMaximumSize))
            {
                if (maxTooLow)
                {
                    logger.error("Hystrix ThreadPool configuration  is trying to set coreSize = " +
                            dynamicCoreSize + " and maximumSize = " + configuredMaximumSize + ".  Maximum size will be set to " +
                            dynamicMaximumSize + ", the coreSize value, since it must be equal to or greater than the coreSize value");
                }
                executor.setCorePoolSize(dynamicCoreSize);
                executor.setMaximumPoolSize(dynamicMaximumSize);
            }

            executor.setKeepAliveTime(properties.getKeepAliveTimeMinutes(), TimeUnit.MINUTES);
        }

        /**
         * Whether the threadpool queue has space available according to the <code>queueSizeRejectionThreshold</code> settings.
         * <p>
         * Note that the <code>queueSize</code> is an final instance variable on HystrixThreadPoolDefault, and not looked up dynamically.
         * The data structure is static, so this does not make sense as a dynamic lookup.
         * The <code>queueSizeRejectionThreshold</code> can be dynamic (up to <code>queueSize</code>), so that should
         * still get checked on each invocation.
         * <p>
         * If a SynchronousQueue implementation is used (<code>maxQueueSize</code> <= 0), it always returns 0 as the size so this would always return true.
         */
        @Override
        public boolean isQueueSpaceAvailable()
        {
            if (queueSize <= 0)
            {
                // we don't have a queue so we won't look for space but instead
                // let the thread-pool reject or not
                return true;
            } else
            {
                return executor.getQueue().size() < properties.getQueueSizeRejectionThreshold();
            }
        }

    }


}
