package com.crazymaker.servlet.container.netty.async;


import com.crazymaker.servlet.container.netty.response.NettyServletResponse;

import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import java.io.IOException;

/**
 * @author: create by daichangya
 * @version: v1.0
 * @description: com.daicy.panda.netty.servlet
 * @date:19-11-21
 */
public class AsyncListenerImpl implements AsyncListener
{
    @Override
    public void onComplete(AsyncEvent event) throws IOException
    {
        NettyServletResponse servletResponse = (NettyServletResponse) event.getSuppliedResponse();
        servletResponse.close();
    }

    @Override
    public void onTimeout(AsyncEvent event) throws IOException
    {

    }

    @Override
    public void onError(AsyncEvent event) throws IOException
    {

    }

    @Override
    public void onStartAsync(AsyncEvent event) throws IOException
    {

    }
}
