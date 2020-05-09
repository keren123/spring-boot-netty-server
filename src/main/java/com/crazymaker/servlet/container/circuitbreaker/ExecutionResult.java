package com.crazymaker.servlet.container.circuitbreaker;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;


public class ExecutionResult
{
    private final EventCounts eventCounts;
    private final Exception failedExecutionException;
    private final Exception executionException;
    private final long startTimestamp;
    private final int executionLatency; //time spent in run() method
    private final int userThreadLatency; //time elapsed between caller thread submitting request and response being visible to it
    private final boolean executionOccurred;
    private final boolean isExecutedInThread;

    private static final EventType[] ALL_EVENT_TYPES = EventType.values();
    private static final int NUM_EVENT_TYPES = ALL_EVENT_TYPES.length;

    public static class EventCounts
    {
        private final BitSet events;
        private final int numFailure;
        private final int numSuccess;

        EventCounts()
        {
            this.events = new BitSet(NUM_EVENT_TYPES);
            this.numFailure = 0;
            this.numSuccess = 0;
        }

        EventCounts(BitSet events, int numSuccess, int numFailure)
        {
            this.events = events;
            this.numFailure = numFailure;
            this.numSuccess = numSuccess;
        }

        EventCounts(EventType... eventTypes)
        {
            BitSet newBitSet = new BitSet(NUM_EVENT_TYPES);
            int localNumFailure = 0;
            int localNumSuccess = 0;
            for (EventType eventType : eventTypes)
            {
                switch (eventType)
                {
                    case SUCCESS:
                        newBitSet.set(EventType.SUCCESS.ordinal());
                        localNumSuccess++;
                        break;
                    case FAILURE:
                        newBitSet.set(EventType.FAILURE.ordinal());
                        localNumFailure++;
                        break;
                    default:
                        newBitSet.set(eventType.ordinal());
                        break;
                }
            }
            this.events = newBitSet;
            this.numFailure = localNumFailure;
            this.numSuccess = localNumSuccess;
        }

        EventCounts plus(EventType eventType)
        {
            return plus(eventType, 1);
        }

        EventCounts plus(EventType eventType, int count)
        {
            BitSet newBitSet = (BitSet) events.clone();
            int localNumFailure = numFailure;
            int localNumSuccess = numSuccess;
            switch (eventType)
            {
                case SUCCESS:
                    newBitSet.set(EventType.SUCCESS.ordinal());
                    localNumSuccess += count;
                    break;
                case FAILURE:
                    newBitSet.set(EventType.FAILURE.ordinal());
                    localNumFailure += count;
                    break;
                default:
                    newBitSet.set(eventType.ordinal());
                    break;
            }
            return new EventCounts(newBitSet, localNumSuccess, localNumFailure);
        }

        public boolean contains(EventType eventType)
        {
            return events.get(eventType.ordinal());
        }

        public boolean containsAnyOf(BitSet other)
        {
            return events.intersects(other);
        }

        public int getCount(EventType eventType)
        {
            switch (eventType)
            {
                case SUCCESS:
                    return numSuccess;
                case FAILURE:
                    return numFailure;
                default:
                    return contains(eventType) ? 1 : 0;
            }
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            EventCounts that = (EventCounts) o;
            if (numFailure != that.numFailure) return false;
            if (numSuccess != that.numSuccess) return false;
            return events.equals(that.events);

        }

        @Override
        public int hashCode()
        {
            int result = events.hashCode();
            result = 31 * result + numFailure;
            result = 31 * result + numSuccess;
            return result;
        }

        @Override
        public String toString()
        {
            return "EventCounts{" +
                    "events=" + events +
                    ", numFailure=" + numFailure +
                    ", numSuccess=" + numSuccess +
                    '}';
        }
    }

    private ExecutionResult(EventCounts eventCounts, long startTimestamp,
                            int executionLatency,
                            int userThreadLatency,
                            Exception failedExecutionException,
                            Exception executionException,
                            boolean executionOccurred,
                            boolean isExecutedInThread)
    {
        this.eventCounts = eventCounts;
        this.startTimestamp = startTimestamp;
        this.executionLatency = executionLatency;
        this.userThreadLatency = userThreadLatency;
        this.failedExecutionException = failedExecutionException;
        this.executionException = executionException;
        this.executionOccurred = executionOccurred;
        this.isExecutedInThread = isExecutedInThread;
    }

    // we can return a static version since it's immutable
    public static ExecutionResult EMPTY = ExecutionResult.from();

    public static ExecutionResult from(
            EventType... eventTypes)
    {
        boolean didExecutionOccur = false;
        for (EventType eventType : eventTypes)
        {
            if (didExecutionOccur(eventType))
            {
                didExecutionOccur = true;
            }
        }
        return new ExecutionResult(new EventCounts(eventTypes), -1L,
                -1, -1, null,
                null, didExecutionOccur, false);
    }

    private static boolean didExecutionOccur(EventType eventType)
    {
        switch (eventType)
        {
            case SUCCESS:
            case FAILURE:
                return true;
            default:
                return false;
        }
    }

    public ExecutionResult setExecutionOccurred()
    {
        return new ExecutionResult(eventCounts, startTimestamp, executionLatency, userThreadLatency,
                failedExecutionException, executionException, true, isExecutedInThread);
    }

    public ExecutionResult setExecutionLatency(int executionLatency)
    {
        return new ExecutionResult(eventCounts, startTimestamp, executionLatency, userThreadLatency,
                failedExecutionException, executionException, executionOccurred, isExecutedInThread);
    }

    public ExecutionResult setException(Exception e)
    {
        return new ExecutionResult(eventCounts, startTimestamp, executionLatency, userThreadLatency, e,
                executionException, executionOccurred, isExecutedInThread);
    }

    public ExecutionResult setExecutionException(Exception executionException)
    {
        return new ExecutionResult(eventCounts, startTimestamp, executionLatency, userThreadLatency,
                failedExecutionException, executionException, executionOccurred, isExecutedInThread);
    }

    public ExecutionResult setInvocationStartTime(long startTimestamp)
    {
        return new ExecutionResult(eventCounts, startTimestamp, executionLatency, userThreadLatency,
                failedExecutionException, executionException, executionOccurred, isExecutedInThread);
    }

    public ExecutionResult setExecutedInThread()
    {
        return new ExecutionResult(eventCounts, startTimestamp, executionLatency, userThreadLatency,
                failedExecutionException, executionException, executionOccurred, true);
    }

    public ExecutionResult setNotExecutedInThread()
    {
        return new ExecutionResult(eventCounts, startTimestamp, executionLatency, userThreadLatency,
                failedExecutionException, executionException, executionOccurred, false);
    }


    public ExecutionResult markUserThreadCompletion(long userThreadLatency)
    {
        if (startTimestamp > 0)
        {
            /* execution time (must occur before terminal state otherwise a race condition can occur if requested by client) */
            return new ExecutionResult(eventCounts, startTimestamp, executionLatency, (int) userThreadLatency,
                    failedExecutionException, executionException, executionOccurred, isExecutedInThread);
        } else
        {
            return this;
        }
    }

    /**
     * Creates a new ExecutionResult by adding the defined 'event' to the ones on the current instance.
     *
     * @param eventType event to add
     * @return new {@link ExecutionResult} with event added
     */
    public ExecutionResult addEvent(EventType eventType)
    {
        return new ExecutionResult(eventCounts.plus(eventType), startTimestamp, executionLatency,
                userThreadLatency, failedExecutionException, executionException,
                executionOccurred, isExecutedInThread);
    }

    public ExecutionResult addEvent(int executionLatency, EventType eventType)
    {
        if (startTimestamp >= 0)
        {
            return new ExecutionResult(eventCounts.plus(eventType), startTimestamp, executionLatency,
                    userThreadLatency, failedExecutionException, executionException,
                    executionOccurred, isExecutedInThread);
        } else
        {
            return addEvent(eventType);
        }
    }

    public EventCounts getEventCounts()
    {
        return eventCounts;
    }

    public long getStartTimestamp()
    {
        return startTimestamp;
    }

    public int getExecutionLatency()
    {
        return executionLatency;
    }

    public int getUserThreadLatency()
    {
        return userThreadLatency;
    }

    public long getCommandRunStartTimeInNanos()
    {
        return startTimestamp * 1000 * 1000;
    }

    public Exception getException()
    {
        return failedExecutionException;
    }

    public Exception getExecutionException()
    {
        return executionException;
    }


    public List<EventType> getOrderedList()
    {
        List<EventType> eventList = new ArrayList<EventType>();
        for (EventType eventType : ALL_EVENT_TYPES)
        {
            if (eventCounts.contains(eventType))
            {
                eventList.add(eventType);
            }
        }
        return eventList;
    }

    public boolean isExecutedInThread()
    {
        return isExecutedInThread;
    }

    public boolean executionOccurred()
    {
        return executionOccurred;
    }


    @Override
    public String toString()
    {
        return "ExecutionResult{" +
                "eventCounts=" + eventCounts +
                ", failedExecutionException=" + failedExecutionException +
                ", executionException=" + executionException +
                ", startTimestamp=" + startTimestamp +
                ", executionLatency=" + executionLatency +
                ", userThreadLatency=" + userThreadLatency +
                ", executionOccurred=" + executionOccurred +
                ", isExecutedInThread=" + isExecutedInThread +
                '}';
    }
}
