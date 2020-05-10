package com.crazymaker.servlet.container.circuitbreaker;

import com.crazymaker.servlet.container.circuitbreaker.RequestTimer.TimerListener;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import lombok.extern.slf4j.Slf4j;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func0;
import rx.functions.Func1;
import rx.subscriptions.CompositeSubscription;

import java.lang.ref.Reference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class RequestCommand<R>
{
    protected enum CommandState
    {
        NOT_STARTED, OBSERVABLE_CHAIN_CREATED, USER_CODE_EXECUTED, UNSUBSCRIBED, TERMINAL
    }

    public static enum FailureType
    {
        BAD_REQUEST_EXCEPTION, COMMAND_EXCEPTION, TIMEOUT, SHORTCIRCUIT, REJECTED_THREAD_EXECUTION, REJECTED_SEMAPHORE_EXECUTION, REJECTED_SEMAPHORE_FALLBACK
    }

    protected enum TimedOutStatus
    {
        NOT_EXECUTED, COMPLETED, TIMED_OUT
    }

    protected enum ThreadState
    {
        NOT_USING_THREAD, STARTED, UNSUBSCRIBED, TERMINAL
    }

    protected final RequestMetrics metrics = RequestMetrics.getInstance();

    protected final CircuitBreaker circuitBreaker = CircuitBreaker.getInstance();
    protected final AtomicReference<Reference<TimerListener>> timeoutTimer = new AtomicReference<Reference<TimerListener>>();

    protected AtomicReference<CommandState> commandState = new AtomicReference<CommandState>(CommandState.NOT_STARTED);
    protected final AtomicReference<TimedOutStatus> isCommandTimedOut = new AtomicReference<TimedOutStatus>(TimedOutStatus.NOT_EXECUTED);
    protected AtomicReference<ThreadState> threadState = new AtomicReference<ThreadState>(ThreadState.NOT_USING_THREAD);

    protected volatile ExecutionResult executionResult = ExecutionResult.EMPTY;
    //state on shared execution
    protected volatile long commandStartTimestamp = -1L;
    protected volatile ExecutionResult executionResultAtTimeOfCancellation;
    protected final RequestThreadPool threadPool = RequestThreadPool.Factory.getInstance();
    private static final NettyWebServerConfig properties = NettyWebServerConfig.getInstance();

    private void handleCommandEnd(boolean commandExecutionStarted)
    {
        Reference<TimerListener> tl = timeoutTimer.get();
        if (tl != null)
        {
            tl.clear();
        }

        long userThreadLatency = System.currentTimeMillis() - commandStartTimestamp;
        executionResult = executionResult.markUserThreadCompletion((int) userThreadLatency);
        if (executionResultAtTimeOfCancellation == null)
        {
            metrics.markCommandDone(executionResult, commandExecutionStarted);
        } else
        {
            metrics.markCommandDone(executionResultAtTimeOfCancellation, commandExecutionStarted);
        }


    }


    public Observable<R> toObservable()
    {
        final RequestCommand<R> _cmd = this;

        //命令停止回调
        final Action0 terminateCommandCleanup = new Action0()
        {

            @Override
            public void call()
            {
                if (_cmd.commandState.compareAndSet(CommandState.OBSERVABLE_CHAIN_CREATED, CommandState.TERMINAL))
                {
                    //user code never ran
                    handleCommandEnd(false);
                    return;

                } else if (_cmd.commandState.compareAndSet(CommandState.USER_CODE_EXECUTED, CommandState.TERMINAL))
                {
                    //user code did run
                    handleCommandEnd(true);
                    return;
                }
                handleCommandEnd(true); //user code did run
            }
        };


        final Func0<Observable<R>> applyHystrixSemantics = new Func0<Observable<R>>()
        {
            @Override
            public Observable<R> call()
            {
                if (commandState.get().equals(CommandState.UNSUBSCRIBED))
                {
                    return Observable.never();
                }
                return applyHystrixSemantics(_cmd);
            }
        };


        return Observable.defer(new Func0<Observable<R>>()
        {
            @Override
            public Observable<R> call()
            {
                /* this is a stateful object so can only be used once */
                if (!commandState.compareAndSet(CommandState.NOT_STARTED, CommandState.OBSERVABLE_CHAIN_CREATED))
                {
                    IllegalStateException ex = new IllegalStateException("This instance can only be executed once. Please instantiate a new instance.");
                    //TODO make a new error type for this
                    throw new RuntimeException(" command executed multiple times - this is not permitted.");
                }

                commandStartTimestamp = System.currentTimeMillis();


                Observable<R> hystrixObservable =
                        Observable.defer(applyHystrixSemantics);


                return hystrixObservable
                        .doOnTerminate(terminateCommandCleanup);
            }
        });
    }

    private Observable<R> applyHystrixSemantics(final RequestCommand<R> _cmd)
    {

        /* determine if we're allowed to execute */
        if (circuitBreaker.allowRequest())
        {
            final TryableSemaphore executionSemaphore = getExecutionSemaphore();
            final AtomicBoolean semaphoreHasBeenReleased = new AtomicBoolean(false);
            final Action0 singleSemaphoreRelease = new Action0()
            {
                @Override
                public void call()
                {
                    if (semaphoreHasBeenReleased.compareAndSet(false, true))
                    {
                        executionSemaphore.release();
                    }
                }
            };

            final Action1<Throwable> markExceptionThrown = new Action1<Throwable>()
            {
                @Override
                public void call(Throwable t)
                {
                    log.info("markExceptionThrown ");
                }
            };

            if (executionSemaphore.tryAcquire())
            {
                try
                {
                    /* used to track userThreadExecutionTime */
                    executionResult = executionResult.setInvocationStartTime(System.currentTimeMillis());
                    return executeCommandAndObserve(_cmd)
                            .doOnError(markExceptionThrown)
                            .doOnTerminate(singleSemaphoreRelease)
                            .doOnUnsubscribe(singleSemaphoreRelease);
                } catch (RuntimeException e)
                {
                    return Observable.error(e);
                }
            } else
            {
                return handleSemaphoreRejectionViaFallback();
            }
        } else
        {
            return handleShortCircuitViaFallback();
        }
    }

    private Observable<R> handleSemaphoreRejectionViaFallback()
    {
        Exception semaphoreRejectionException = new RuntimeException("could not acquire a semaphore for execution");
        executionResult = executionResult.setExecutionException(semaphoreRejectionException);
        log.debug("RequestCommand  Execution Rejection by Semaphore."); // debug only since we're throwing the exception and someone higher will do something with it
        // retrieve a fallback or throw an exception if no fallback available
        return getFallbackOrThrowException(this, EventType.FAILURE, FailureType.REJECTED_SEMAPHORE_EXECUTION,
                "could not acquire a semaphore for execution", semaphoreRejectionException);
    }

    private Observable<R> getFallbackOrThrowException(final RequestCommand<R> _cmd, final EventType eventType, final FailureType failureType, final String message, final Exception originalException)
    {
        long latency = System.currentTimeMillis() - executionResult.getStartTimestamp();
        // record the executionResult
        // do this before executing fallback so it can be queried from within getFallback (see See https://github.com/Netflix/Hystrix/pull/144)
        executionResult = executionResult.addEvent((int) latency, eventType);

        /* fallback is disabled so throw Netty request RuntimeException  */
        log.debug("Fallback disabled for RequestCommand  so will throw Netty request RuntimeException . "); // debug only since we're throwing the exception and someone higher will do something with it

        /* executionHook for all errors */
        return Observable.error(new RuntimeException(" executing error "));

    }

    private TryableSemaphore getExecutionSemaphore()
    {
        return TryableSemaphore.DEFAULT;
    }


    private Observable<R> handleShortCircuitViaFallback()
    {
        // short-circuit and go directly to fallback (or throw an exception if no fallback implemented)
        Exception shortCircuitException = new RuntimeException("Hystrix circuit short-circuited and is OPEN");
        executionResult = executionResult.setExecutionException(shortCircuitException);
        try
        {
            return getFallbackOrThrowException(this, EventType.FAILURE, FailureType.SHORTCIRCUIT,
                    "short-circuited", shortCircuitException);
        } catch (Exception e)
        {
            return Observable.error(e);
        }
    }


    /**
     * This decorates "Hystrix" functionality around the run() Observable.
     *
     * @return R
     */
    private Observable<R> executeCommandAndObserve(final RequestCommand<R> _cmd)
    {
        final HystrixRequestContext currentRequestContext = HystrixRequestContext.getContextForCurrentThread();

        final Action1<R> markEmits = new Action1<R>()
        {
            @Override
            public void call(R r)
            {

                long latency = System.currentTimeMillis() - executionResult.getStartTimestamp();
                executionResult = executionResult.addEvent((int) latency, EventType.SUCCESS);
                circuitBreaker.markSuccess();

            }
        };

        final Action0 markOnCompleted = new Action0()
        {
            @Override
            public void call()
            {

                long latency = System.currentTimeMillis() - executionResult.getStartTimestamp();
                executionResult = executionResult.addEvent((int) latency, EventType.SUCCESS);
                circuitBreaker.markSuccess();

            }
        };

        final Func1<Throwable, Observable<R>> handleFallback = new Func1<Throwable, Observable<R>>()
        {
            @Override
            public Observable<R> call(Throwable t)
            {
                Exception e = getExceptionFromThrowable(t);
                executionResult = executionResult.setExecutionException(e);


                return handleFailureViaFallback(e);
            }
        };


        Observable<R> execution = executeCommandWithSpecifiedIsolation(_cmd)
                .lift(new RequestCommand.HystrixObservableTimeoutOperator<R>(_cmd));


        return execution.doOnNext(markEmits)
                .doOnCompleted(markOnCompleted)
                .onErrorResumeNext(handleFallback);
    }

    protected Exception getExceptionFromThrowable(Throwable t)
    {
        Exception e;
        if (t instanceof Exception)
        {
            e = (Exception) t;
        } else
        {
            // Hystrix 1.x uses Exception, not Throwable so to prevent a breaking change Throwable will be wrapped in Exception
            e = new Exception("Throwable caught while executing.", t);
        }
        return e;
    }

    private Observable<R> handleFailureViaFallback(Exception underlying)
    {
        /**
         * All other error handling
         */
        log.debug("Error executing HystrixCommand.run(). Proceeding to fallback logic ...", underlying);

        // record the exception
        executionResult = executionResult.setException(underlying);
        return getFallbackOrThrowException(this, EventType.FAILURE, FailureType.COMMAND_EXCEPTION, "failed", underlying);
    }

    private Observable<R> executeCommandWithSpecifiedIsolation(final RequestCommand<R> _cmd)
    {

        // mark that we are executing in a thread (even if we end up being rejected we still were a THREAD execution and not SEMAPHORE)
        return Observable.defer(new Func0<Observable<R>>()
        {
            @Override
            public Observable<R> call()
            {
                executionResult = executionResult.setExecutionOccurred();
                if (!commandState.compareAndSet(CommandState.OBSERVABLE_CHAIN_CREATED, CommandState.USER_CODE_EXECUTED))
                {
                    return Observable.error(new IllegalStateException("execution attempted while in state : " + commandState.get().name()));
                }

                if (isCommandTimedOut.get() == TimedOutStatus.TIMED_OUT)
                {
                    // the command timed out in the wrapping thread so we will return immediately
                    // and not increment any of the counters below or other such logic
                    return Observable.error(new RuntimeException("timed out before executing run()"));
                }
                if (threadState.compareAndSet(ThreadState.NOT_USING_THREAD, ThreadState.STARTED))
                {

                    executionResult = executionResult.setExecutedInThread();
                    /**
                     * If any of these hooks throw an exception, then it appears as if the actual execution threw an error
                     */
                    try
                    {
                        return getExecutionObservable();
                    } catch (Throwable ex)
                    {
                        return Observable.error(ex);
                    }
                } else
                {
                    //command has already been unsubscribed, so return immediately
                    return Observable.error(new RuntimeException("unsubscribed before executing run()"));
                }
            }
        }).doOnTerminate(new Action0()
        {
            @Override
            public void call()
            {
                if (threadState.compareAndSet(ThreadState.STARTED, ThreadState.TERMINAL))
                {
                    //
                }
                if (threadState.compareAndSet(ThreadState.NOT_USING_THREAD, ThreadState.TERMINAL))
                {
                    //if it was never started and received terminal, then no need to clean up (I don't think this is possible)
                }
                //if it was unsubscribed, then other cleanup handled it
            }
        }).doOnUnsubscribe(new Action0()
        {
            @Override
            public void call()
            {
                if (threadState.compareAndSet(ThreadState.STARTED, ThreadState.UNSUBSCRIBED))
                {
                    //
                }
                if (threadState.compareAndSet(ThreadState.NOT_USING_THREAD, ThreadState.UNSUBSCRIBED))
                {
                    //if it was never started and was cancelled, then no need to clean up
                }
                //if it was terminal, then other cleanup handled it
            }
        }).subscribeOn(threadPool.getScheduler(new Func0<Boolean>()
        {
            @Override
            public Boolean call()
            {
                return properties.getExecutionIsolationThreadInterruptOnTimeout() && _cmd.isCommandTimedOut.get() == TimedOutStatus.TIMED_OUT;
            }
        }));

    }

    final protected Observable<R> getExecutionObservable()
    {
        return Observable.defer(new Func0<Observable<R>>()
        {
            @Override
            public Observable<R> call()
            {
                try
                {
                    return Observable.just(run());
                } catch (Throwable ex)
                {
                    return Observable.error(ex);
                }
            }
        }).doOnSubscribe(new Action0()
        {
            @Override
            public void call()
            {
                // Save thread on which we get subscribed so that we can interrupt it later if needed
                //     executionThread.set(Thread.currentThread());
            }
        });
    }

    private static class HystrixObservableTimeoutOperator<R> implements Observable.Operator<R, R>
    {

        final RequestCommand<R> originalCommand;

        public HystrixObservableTimeoutOperator(final RequestCommand<R> originalCommand)
        {
            this.originalCommand = originalCommand;
        }

        @Override
        public Subscriber<? super R> call(final Subscriber<? super R> child)
        {
            final CompositeSubscription s = new CompositeSubscription();
            // if the child unsubscribes we unsubscribe our parent as well
            child.add(s);

            /*
             * Define the action to perform on timeout outside of the TimerListener to it can capture the HystrixRequestContext
             * of the calling thread which doesn't exist on the Timer thread.
             */
            final Runnable timeoutRunnable = new Runnable()
            {

                @Override
                public void run()
                {
                    child.onError(new RuntimeException("time out"));
                }
            };

            TimerListener listener = new TimerListener()
            {

                boolean timeOut = false;

                @Override
                public boolean isTimeOut()
                {
                    return timeOut;
                }

                @Override
                public void tick()
                {
                    timeOut = true;

                    // if we can go from NOT_EXECUTED to TIMED_OUT then we do the timeout codepath
                    // otherwise it means we lost a race and the run() execution completed or did not start
                    if (originalCommand.isCommandTimedOut.compareAndSet(TimedOutStatus.NOT_EXECUTED, TimedOutStatus.TIMED_OUT))
                    {

                        // shut down the original request
                        s.unsubscribe();

                        timeoutRunnable.run();
                        //if it did not start, then we need to mark a command start for concurrency metrics, and then issue the timeout
                    }


                }

                @Override
                public int getIntervalTimeInMilliseconds()
                {
                    return properties.getExecutionTimeoutInMilliseconds();
                }
            };

            final Reference<TimerListener> tl = RequestTimer.getInstance().addTimerListener(listener);

            // set externally so execute/queue can see this
            originalCommand.timeoutTimer.set(tl);

            /**
             * If this subscriber receives values it means the parent succeeded/completed
             */
            Subscriber<R> parent = new Subscriber<R>()
            {

                @Override
                public void onCompleted()
                {
                    if (isNotTimedOut())
                    {
                        // stop timer and pass notification through
                        tl.clear();
                        child.onCompleted();
                    }
                }

                @Override
                public void onError(Throwable e)
                {
                    if (isNotTimedOut())
                    {
                        // stop timer and pass notification through
                        tl.clear();
                        child.onError(e);
                    }
                }

                @Override
                public void onNext(R v)
                {
                    if (isNotTimedOut())
                    {
                        child.onNext(v);
                    }
                }

                private boolean isNotTimedOut()
                {
                    // if already marked COMPLETED (by onNext) or succeeds in setting to COMPLETED
                    return originalCommand.isCommandTimedOut.get() == TimedOutStatus.COMPLETED ||
                            originalCommand.isCommandTimedOut.compareAndSet(TimedOutStatus.NOT_EXECUTED, TimedOutStatus.COMPLETED);
                }

            };

            // if s is unsubscribed we want to unsubscribe the parent
            s.add(parent);

            return parent;
        }

    }

    protected R run()
    {
        return null;
    }
}
