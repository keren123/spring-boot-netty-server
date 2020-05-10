package com.crazymaker.servlet.container.circuitbreaker;

public enum EventType
{
    SUCCESS(1),
    FAILURE(0);

    private final int code;

    EventType(int code)
    {
        this.code = code;
    }
}