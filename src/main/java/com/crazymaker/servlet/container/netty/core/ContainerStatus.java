package com.crazymaker.servlet.container.netty.core;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 容器状态
 * created by 尼恩  @ 疯狂创客圈
 */

public class ContainerStatus
{
    /**
     * 单例
     */
    public final static ContainerStatus INSTANCE = new ContainerStatus();

    /**
     * 连接数
     */
    private AtomicInteger connections = new AtomicInteger(0);
    private AtomicInteger pendingRequests = new AtomicInteger(0);
    /**
     * 请求数
     */
    private AtomicLong totalRequests = new AtomicLong(0);

    /**
     * 请求处理数
     */
    private AtomicLong handledRequests = new AtomicLong(0);


    private ContainerStatus()
    {
    }


    public ContainerStatus totalRequestsIncrement()
    {
        totalRequests.incrementAndGet();
        return this;
    }

    public ContainerStatus handledRequestsIncrement()
    {
        handledRequests.incrementAndGet();
        return this;
    }

    public ContainerStatus connectionIncrement()
    {
        connections.incrementAndGet();
        return this;
    }

    public ContainerStatus connectionDecrement()
    {
        connections.decrementAndGet();
        return this;
    }

    public ContainerStatus pendingRequestsIncrement()
    {
        pendingRequests.incrementAndGet();
        return this;
    }

    public ContainerStatus pendingRequestsDecrement()
    {
        pendingRequests.decrementAndGet();
        return this;
    }

    public int getConnections()
    {
        return connections.get();
    }

    public int getPendingRequests()
    {
        return pendingRequests.get();
    }

    public long getTotalRequests()
    {
        return totalRequests.get();
    }

    public long getHandledRequests()
    {
        return handledRequests.get();
    }

}
