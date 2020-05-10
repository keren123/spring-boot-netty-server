package com.crazymaker.servlet.container.netty.core;

import com.crazymaker.servlet.container.netty.request.NettyServletRequest;
import com.crazymaker.servlet.container.netty.response.NettyServletResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * channel激活时， 开启一个新的输入流
 * 有信息/请求进入时，封装请求和响应对象，执行读操作
 * channel恢复时，关闭输入流，等待下一次连接到来
 */

public class NettyServletHandler extends ChannelInboundHandlerAdapter
{
    private static final HttpResponse DEFAULT_FULLHTTP_RESPONSE =
            new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE);

    private final Logger log = LoggerFactory.getLogger(getClass());

    private NettyServletContext nettyServletContext;


    NettyServletHandler(NettyServletContext nettyServletContext)
    {
        this.nettyServletContext = nettyServletContext;
    }

    public NettyServletContext getNettyServletContext()
    {
        return nettyServletContext;
    }


    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception
    {
        /**
         * 增加连接数
         */
        ContainerStatus.INSTANCE.connectionIncrement();
//        log.info("new connection,ctx.channel is {} ", ctx.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception
    {
        /**
         * 减少连接数
         */
        ContainerStatus.INSTANCE.connectionDecrement();
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx)
    {
        ctx.flush();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception
    {
        try
        {


            if (msg instanceof FullHttpRequest)
            {
                FullHttpRequest fullHttpRequest = (FullHttpRequest) msg;


                NettyServletResponse servletResponse = new NettyServletResponse(ctx, nettyServletContext);

                NettyServletRequest nettyServletRequest =
                        new NettyServletRequest(ctx, this, fullHttpRequest, servletResponse);

                servletResponse.setRequestFacade(nettyServletRequest);
                /**
                 * 请求头包含Expect: 100-continue
                 */
                if (HttpUtil.is100ContinueExpected(fullHttpRequest))
                {
                    ctx.write(DEFAULT_FULLHTTP_RESPONSE, ctx.voidPromise());
                }
//                ReferenceCountUtil.retain(msg);
                ctx.fireChannelRead(nettyServletRequest);
//                log.info("msg {} is instanceof FullHttpRequest", msg);
//                log.info("channel is {}", ctx.channel());
            } else
            {
                ReferenceCountUtil.release(msg);
            }
        } finally
        {
//            ReferenceCountUtil.release(msg);
            ContainerStatus.INSTANCE.handledRequestsIncrement();
            ContainerStatus.INSTANCE.totalRequestsIncrement();
        }

    }

}