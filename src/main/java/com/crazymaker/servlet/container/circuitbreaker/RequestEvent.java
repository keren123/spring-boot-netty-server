package com.crazymaker.servlet.container.circuitbreaker;

import lombok.Data;

@Data
public class RequestEvent
{

    /**
     * 失败
     */
    private boolean failed;

    /**
     * 成功
     */
    private boolean succeed;

    public static RequestEvent from(ExecutionResult executionResult)
    {
        return new RequestEvent(executionResult);
    }


    public boolean isCommandCompletion()
    {
        return true;
    }

    protected final ExecutionResult executionResult;

    private RequestEvent(ExecutionResult executionResult)
    {
        this.executionResult = executionResult;
    }

    public ExecutionResult.EventCounts getEventCounts()
    {
        return executionResult.getEventCounts();
    }
}
