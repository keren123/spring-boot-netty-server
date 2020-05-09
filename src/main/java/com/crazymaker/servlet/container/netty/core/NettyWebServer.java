package com.crazymaker.servlet.container.netty.core;

import com.google.common.base.StandardSystemProperty;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.server.WebServerException;

import java.net.InetSocketAddress;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 *定制的 Netty Server
 */
public class NettyWebServer implements WebServer
{
    private final Log log = LogFactory.getLog(getClass());
    //监听端口地址
    private final InetSocketAddress address;
    //和请求处理交互的 ServletContext 上下文
    private final NettyServletContext nettyServletContext;

    //Netty 所需的线程池
    private EventLoopGroup bossGroup;  //接收/监听请求
    private EventLoopGroup workerGroup; //IO处理线程池
    private DefaultEventExecutorGroup servletExecutor;  //业务处理线程池

    public NettyWebServer(InetSocketAddress address, NettyServletContext nettyServletContext)
    {
        this.address = address;
        this.nettyServletContext = nettyServletContext;
    }

    /**
     * servlet 容器启动，启动内部的 Netty 监听服务
     */
    @Override
    public void start() throws WebServerException
    {
        nettyServletContext.setInitialised(false);

        ServerBootstrap sb = new ServerBootstrap();
        //根据不同系统初始化对应的EventLoopGroup
        if ("Linux".equals(StandardSystemProperty.OS_NAME.value()))
        {
            bossGroup = new EpollEventLoopGroup(1);
            //不带参数，线程数传入0,实际解析为CPU核数*2
            workerGroup = new EpollEventLoopGroup();
            sb.channel(EpollServerSocketChannel.class)
                    .group(bossGroup, workerGroup)
                    .option(EpollChannelOption.TCP_CORK, true);
        } else
        {
            bossGroup = new NioEventLoopGroup(1);
            workerGroup = new NioEventLoopGroup();
            sb.channel(NioServerSocketChannel.class)
                    .group(bossGroup, workerGroup);
        }
        sb.option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_REUSEADDR, true)
                .option(ChannelOption.SO_BACKLOG, 100);
        log.info("Bootstrap configuration: " + sb.toString());

        servletExecutor = new DefaultEventExecutorGroup(50);
        sb.childHandler(new ChannelInitializer<SocketChannel>()
        {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception
            {
                ChannelPipeline pipeline = ch.pipeline();
                //HTTP编码解码Handler
                pipeline.addLast("codec", new HttpServerCodec(4096, 8192, 8192, false));
                //处理请求，读入数据，生成Request和Response对象
                pipeline.addLast(new HttpObjectAggregator(100 * 1024));
                pipeline.addLast(new ChunkedWriteHandler());
                pipeline.addLast("servletInput", new NettyServletHandler(nettyServletContext));
                //获取请求分发器，让对应的Servlet处理请求，同时处理404情况
                pipeline.addLast(checkNotNull(servletExecutor), "filterChain", new NettyRequestDispatcherHandler(nettyServletContext));
            }
        });


        nettyServletContext.setInitialised(true);

        ChannelFuture future = sb.bind(address).awaitUninterruptibly();
        Throwable cause = future.cause();
        if (null != cause)
        {
            throw new WebServerException("Could not start Netty server", cause);
        }
        log.info(nettyServletContext.getServerInfo() + " started on port: " + getPort());
    }

    /**
     * servlet 容器停止，优雅地关闭各种资源
     */
    @Override
    public void stop() throws WebServerException
    {
        log.info("Spring Boot Netty Server is now shuting down.");
        try
        {
            if (null != bossGroup)
            {
                bossGroup.shutdownGracefully().await();
            }
            if (null != workerGroup)
            {
                workerGroup.shutdownGracefully().await();
            }
            if (null != servletExecutor)
            {
                servletExecutor.shutdownGracefully().await();
            }
        } catch (InterruptedException e)
        {
            throw new WebServerException("Container stop interrupted", e);
        }
    }


    @Override
    public int getPort()
    {
        return address.getPort();
    }
}
