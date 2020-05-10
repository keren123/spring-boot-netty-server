package com.crazymaker.servlet.container.netty.async;

import com.crazymaker.servlet.container.netty.core.ContainerStatus;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


public class TracingThreadPoolExecutor extends ThreadPoolExecutor
{
    private final AtomicInteger pendingTasks = new AtomicInteger();

    public TracingThreadPoolExecutor(int corePoolSize, int maximumPoolSize,
                                     BlockingQueue<Runnable> workQueue)
    {
        super(corePoolSize, maximumPoolSize,
                0L, TimeUnit.MILLISECONDS, workQueue);

//        serverStatus.workerPool(this);
    }

    @Override
    public void execute(Runnable command)
    {
        super.execute(command);
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r)
    {
        ContainerStatus.INSTANCE.pendingRequestsIncrement();
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t)
    {
        ContainerStatus.INSTANCE.pendingRequestsDecrement();
    }

    public int getPendingTasks()
    {
        return pendingTasks.get();
    }
}