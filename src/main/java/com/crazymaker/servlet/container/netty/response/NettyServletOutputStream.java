package com.crazymaker.servlet.container.netty.response;

import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpUtil;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import java.io.IOException;

/**
 * 响应输出流
 */
public class NettyServletOutputStream extends ServletOutputStream
{

    private NettyServletResponse servletResponse;

    private ByteBufOutputStream out;

    private boolean flushed = false;

    public NettyServletOutputStream(NettyServletResponse response)
    {
        this.servletResponse = response;
        this.out = new ByteBufOutputStream(Unpooled.buffer(0));
    }

    @Override
    public void write(int b) throws IOException
    {
        this.out.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException
    {
        this.out.write(b);
    }

    @Override
    public void write(byte[] b, int offset, int len) throws IOException
    {
        this.out.write(b, offset, len);
    }

    @Override
    public void flush()
    {
//        this.response.setContent(out.buffer());
//        servletResponse.getCtx().writeAndFlush(out.buffer().copy());
//        resetBuffer();

        if (!flushed)
        {
            servletResponse.setResponseBasicHeader();
        }
        boolean chunked = HttpUtil.isTransferEncodingChunked(servletResponse.getOriginalResponse());
        ChannelHandlerContext ctx = servletResponse.getCtx();
        if (chunked && ctx.channel().isActive())
        {
            if (!flushed)
            {
                ctx.writeAndFlush(servletResponse.getOriginalResponse());
            }
            if (out.buffer().writerIndex() > out.buffer().readerIndex())
            {
                ctx.writeAndFlush((new DefaultHttpContent(out.buffer().copy())));
                resetBuffer();
            }
            this.flushed = true;
        }
    }

    private boolean close = false;

    @Override
    public void close()
    {
        if (close)
        {
            return;
        }
        close = true;
        boolean chunked = HttpUtil.isTransferEncodingChunked(servletResponse.getOriginalResponse());
        ChannelHandlerContext ctx = servletResponse.getCtx();
        if (!chunked)
        {
            // 设置content-length头
            if (!HttpUtil.isContentLengthSet(servletResponse.getOriginalResponse()))
            {
                HttpUtil.setContentLength(servletResponse.getOriginalResponse(), this.out.buffer().readableBytes());
            }
            if (ctx.channel().isActive())
            {
                ctx.writeAndFlush(servletResponse.getOriginalResponse());
            }
        }
        if (out.buffer().writerIndex() > out.buffer().readerIndex() && ctx.channel().isActive())
        {
            ctx.writeAndFlush((new DefaultHttpContent(out.buffer())));
        }
    }

    public void resetBuffer()
    {
        this.out.buffer().clear();
    }

    public boolean isFlushed()
    {
        return flushed;
    }

    public int getBufferSize()
    {
        return this.out.buffer().capacity();
    }

    @Override
    public boolean isReady()
    {
        return false;
    }

    @Override
    public void setWriteListener(WriteListener writeListener)
    {

    }
}
