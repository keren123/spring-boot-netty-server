/*
 * Copyright 2013 by Maxim Kalina
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.crazymaker.servlet.container.netty.request;

import io.netty.buffer.ByteBufInputStream;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.util.ReferenceCountUtil;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import java.io.IOException;

public class NettyServletInputStream extends ServletInputStream
{

    private FullHttpRequest fullHttpRequest;

    private ByteBufInputStream in;

    public NettyServletInputStream(FullHttpRequest fullHttpRequest)
    {
        this.fullHttpRequest = fullHttpRequest;

        this.in = new ByteBufInputStream(fullHttpRequest.content());

    }


    @Override
    public int read() throws IOException
    {
        return this.in.read();
    }

    @Override
    public int read(byte[] buf) throws IOException
    {
        return this.in.read(buf);
    }

    @Override
    public int read(byte[] buf, int offset, int len) throws IOException
    {
        return this.in.read(buf, offset, len);
    }

    @Override
    public boolean isFinished()
    {
        return false;
    }

    @Override
    public boolean isReady()
    {
        return true;
    }

    @Override
    public void setReadListener(ReadListener readListener)
    {

    }

    private boolean close = false;

    @Override
    public void close()
    {
        if (close)
        {
            return;
        }
        ReferenceCountUtil.release(fullHttpRequest);
        close = true;
    }

}
