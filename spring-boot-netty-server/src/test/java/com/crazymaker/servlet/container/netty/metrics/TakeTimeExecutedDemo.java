package com.crazymaker.servlet.container.netty.metrics;

import com.netflix.hystrix.HystrixCommandMetrics;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import rx.schedulers.Schedulers;


@Slf4j
public class TakeTimeExecutedDemo
{

    public static final int COUNT = 50;

    @Test
    public void testToObservable() throws Exception
    {

        TakeTimeCommand command = null;
        for (int i = 0; i < COUNT; i++)
        {
            Thread.sleep(50);

            command = new TakeTimeCommand(1000);
            command.toObservable().subscribeOn(Schedulers.computation())
                    .subscribe(result -> result.length(),
                            error -> error.getCause()
                    );

//            command.toObservable()
//                    .subscribe(result -> log.info("result={}", result),
//                            error -> log.error("error={}", error)
//                    );
        }
        Thread.sleep(500);

        for (int i = 0; i < COUNT; i++)
        {
            Thread.sleep(20);

            command = new TakeTimeCommand(200);
            command.toObservable().subscribeOn(Schedulers.computation())
                    .subscribe(result -> result.length(),
                            error -> error.getCause()
                    );

//            command.toObservable()
//                    .subscribe(result -> log.info("result={}", result),
//                            error -> log.error("error={}", error)
//                    );
        }
        HystrixCommandMetrics.HealthCounts hc = command.getMetrics().getHealthCounts();
        log.info("window totalRequests：{},errorPercentage:{}",
                hc.getTotalRequests(),//滑动窗口总的请求数
                hc.getErrorPercentage());//滑动窗口出错比例

        Thread.sleep(Integer.MAX_VALUE);
    }

}