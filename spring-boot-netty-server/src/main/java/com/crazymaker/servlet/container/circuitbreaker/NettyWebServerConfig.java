package com.crazymaker.servlet.container.circuitbreaker;

public class NettyWebServerConfig
{


    private static NettyWebServerConfig instance;

    public static NettyWebServerConfig getInstance()
    {
        if (null == instance)
        {
            instance = new NettyWebServerConfig();
        }
        return instance;
    }

    public long getCircuitBreakerRequestVolumeThreshold()
    {
        return 0;
    }

    public int getCircuitBreakerErrorThresholdPercentage()
    {
        return 0;
    }

    public long getCircuitBreakerSleepWindowInMilliseconds()
    {
        return 0;
    }

    public int getQueueSizeRejectionThreshold()
    {
        return 0;
    }

    public long getKeepAliveTimeMinutes()
    {
        return 0;
    }

    public int getCoreSize()
    {
        return 0;
    }

    public int getMaximumSize()
    {
        return 0;
    }

    public int getActualMaximumSize()
    {
        return 0;
    }

    public boolean getAllowMaximumSizeToDivergeFromCoreSize()
    {
        return false;
    }

    public int getMaxQueueSize()
    {
        return 0;
    }

    public boolean getExecutionIsolationThreadInterruptOnTimeout()
    {
        return false;
    }

    public int getExecutionTimeoutInMilliseconds()
    {
        return 0;
    }
}
