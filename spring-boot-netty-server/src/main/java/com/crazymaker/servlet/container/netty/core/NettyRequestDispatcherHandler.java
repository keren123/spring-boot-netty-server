package com.crazymaker.servlet.container.netty.core;

import com.crazymaker.servlet.container.netty.request.NettyServletRequest;
import com.crazymaker.servlet.container.netty.response.NettyServletResponse;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * 读入请求数据时，对请求URI获取分发器，找不到返回404错误.
 * 找到则调用FilterChain进行业务逻辑，最后关闭输出流
 */
@ChannelHandler.Sharable
public class NettyRequestDispatcherHandler extends SimpleChannelInboundHandler<NettyServletRequest>
{
    private static final Log log = LogFactory.getLog(NettyRequestDispatcherHandler.class);
    private final NettyServletContext context;

    NettyRequestDispatcherHandler(NettyServletContext context)
    {
        this.context = checkNotNull(context);
    }


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, NettyServletRequest nettyServletRequest) throws Exception
    {
        NettyServletResponse nettyServletResponse = (NettyServletResponse) nettyServletRequest.getServletResponse();
        try
        {

            handleRequest0(nettyServletRequest, nettyServletResponse);

        } finally
        {

            try
            {
                nettyServletRequest.getInputStream().close();
            } catch (IOException e)
            {
                log.error("handleRequest error", e);
            }

            if (!nettyServletRequest.isAsyncStarted())
            {
                nettyServletResponse.close();
            }


        }
    }

    public static void handleRequest0(NettyServletRequest nettyServletRequest, NettyServletResponse nettyServletResponse)
    {
        try
        {
            NettyRequestDispatcher dispatcher =
                    (NettyRequestDispatcher) NettyServletContext.get().getRequestDispatcher(nettyServletRequest.getRequestURI());
            if (dispatcher == null)
            {
                nettyServletResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            dispatcher.dispatch(nettyServletRequest, nettyServletResponse);
//            Servlet servlet = ServletContextImpl.get().getServlet(DispatcherServletAutoConfiguration.DEFAULT_DISPATCHER_SERVLET_BEAN_NAME);
//            FilterChainImpl chain = FilterChainFactory.createFilterChain(nettyServletRequest, servlet);
//            chain.doFilter(servletRequest, servletResponse);
//            if (nettyServletResponse.getStatus() == HttpServletResponse.SC_OK) {
//                servlet.service(servletRequest, servletResponse);
//            }
        } catch (Exception e)
        {
            log.error("controller invoke uri:" + nettyServletRequest.getRequestURI(), e);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception
    {
        log.error("Unexpected exception caught during request", cause);
        ctx.close();
    }
}
