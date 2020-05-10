package com.crazymaker.servlet.container.netty.response;

import com.crazymaker.servlet.container.netty.core.NettyServletContext;
import com.crazymaker.servlet.container.netty.request.NettyServletRequest;
import com.crazymaker.servlet.container.netty.session.NettyHttpSession;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AsciiString;
import io.netty.util.concurrent.FastThreadLocal;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Http响应对象
 */
public class NettyServletResponse implements HttpServletResponse
{

    private static final String DEFAULT_CHARACTER_ENCODING = Charsets.UTF_8.name();

    /**
     * 封装的 servlet 响应
     */
    private final DefaultHttpResponse originalResponse;

    /**
     * 上下文环境
     */
    private final NettyServletContext nettyServletContext;

    private NettyServletOutputStream servletOutputStream;

    private ChannelHandlerContext ctx;

    private PrintWriter printWriter;

    /**
     * 提交状态
     */
    private boolean responseCommitted = false;

    /**
     * 字符编码
     */
    private String characterEncoding;

    /**
     * 内容类型
     */
    private String contentType;

    /**
     * 封装的请求
     */
    NettyServletRequest requestFacade;

    public NettyServletResponse(ChannelHandlerContext ctx, NettyServletContext nettyServletContext)
    {
        this.ctx = ctx;

        this.nettyServletContext = nettyServletContext;
        this.servletOutputStream = new NettyServletOutputStream(this);
        this.printWriter = new PrintWriter(servletOutputStream);
        this.originalResponse = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, false);

    }

    private List<Cookie> cookies = new ArrayList<>();

    @Override
    public void addCookie(Cookie cookie)
    {
        cookies.add(cookie);
/*

        String result = io.netty.handler.codec.http.cookie.ServerCookieEncoder.LAX.encode(new DefaultCookie(cookie.getName(), cookie.getValue()));
        this.originalResponse.headers().add(HttpHeaderNames.SET_COOKIE, result);
*/
    }

    @Override
    public boolean containsHeader(String name)
    {
        return this.originalResponse.headers().contains(name);
    }

    @Override
    public String encodeURL(String url)
    {
        try
        {
            String characterEncoding = getCharacterEncoding();
            if (StringUtils.isEmpty(characterEncoding))
            {
                return URLEncoder.encode(url);
            }
            return URLEncoder.encode(url, getCharacterEncoding());
        } catch (UnsupportedEncodingException e)
        {
            throw new RuntimeException("Error encoding url!", e);
        }
    }


    @Override
    public String encodeRedirectURL(String url)
    {
        return encodeURL(url);
    }

    @Override
    public String encodeUrl(String url)
    {
        return encodeURL(url);
    }

    @Override
    public String encodeRedirectUrl(String url)
    {
        return encodeURL(url);
    }

    @Override
    public void sendError(int sc, String msg) throws IOException
    {
        this.originalResponse.setStatus(new HttpResponseStatus(sc, msg));
    }

    @Override
    public void sendError(int sc) throws IOException
    {
        this.originalResponse.setStatus(HttpResponseStatus.valueOf(sc));
    }

    @Override
    public void sendRedirect(String location) throws IOException
    {
        setStatus(SC_FOUND);
        setHeader(HttpHeaderNames.LOCATION.toString(), location);
    }

    @Override
    public void setDateHeader(String name, long date)
    {
        this.originalResponse.headers().set(name, date);
    }

    @Override
    public void addDateHeader(String name, long date)
    {
        this.originalResponse.headers().add(name, date);
    }

    @Override
    public void setHeader(String name, String value)
    {
        this.originalResponse.headers().set(name, value);
    }

    @Override
    public void addHeader(String name, String value)
    {
        this.originalResponse.headers().add(name, value);
    }

    @Override
    public void setIntHeader(String name, int value)
    {
        this.originalResponse.headers().setInt(name, value);
    }

    @Override
    public void addIntHeader(String name, int value)
    {
        this.originalResponse.headers().addInt(name, value);
    }

    @Override
    public int getStatus()
    {
        return this.originalResponse.getStatus().code();
    }


    @Override
    public void setStatus(int sc)
    {
        this.originalResponse.setStatus(HttpResponseStatus.valueOf(sc));
    }

    @Override
    public void setStatus(int sc, String sm)
    {
        this.originalResponse.setStatus(new HttpResponseStatus(sc, sm));
    }


    @Override
    public String getHeader(String name)
    {
        return this.originalResponse.headers().get(name);
    }

    @Override
    public Collection<String> getHeaders(String name)
    {
        String value = this.getHeader(name);
        return null == value ? null : Lists.newArrayList(value);
    }

    @Override
    public Collection<String> getHeaderNames()
    {
        return this.originalResponse.headers().names();
    }

    @Override
    public String getCharacterEncoding()
    {
        return this.originalResponse.headers().get(HttpHeaderNames.CONTENT_ENCODING);
    }

    @Override
    public void setCharacterEncoding(String charset)
    {
        this.originalResponse.headers().set(HttpHeaderNames.CONTENT_ENCODING, charset);
        characterEncoding = charset;
    }


    @Override
    public String getContentType()
    {
        return this.originalResponse.headers().get(HttpHeaderNames.CONTENT_TYPE);
    }

    @Override
    public void setContentType(String contentType)
    {
        this.originalResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        this.contentType = contentType;
    }

    @Override
    public void setContentLength(int len)
    {
        HttpUtil.setContentLength(this.originalResponse, len);
    }

    @Override
    public NettyServletOutputStream getOutputStream() throws IOException
    {
        return servletOutputStream;
    }

    @Override
    public PrintWriter getWriter() throws IOException
    {
        return printWriter;
    }

    @Override
    public void setContentLengthLong(long len)
    {
        HttpUtil.setContentLength(this.originalResponse, len);
    }

    @Override
    public void setBufferSize(int size)
    {
    }

    private boolean flush = false;

    @Override
    public void flushBuffer()
    {
//        setResponseBasicHeader();
        this.servletOutputStream.flush();
//        boolean isKeepAlive = HttpUtil.isKeepAlive(originalResponse);
//        if (isKeepAlive) {
//            setContentLength(originalResponse.content().readableBytes());
//        }
    }

    @Override
    public int getBufferSize()
    {
        return this.servletOutputStream.getBufferSize();
    }

    @Override
    public void resetBuffer()
    {
        if (isCommitted())
            throw new IllegalStateException("Response already commited!");

        this.servletOutputStream.resetBuffer();
    }

    @Override
    public boolean isCommitted()
    {
        return this.responseCommitted;
    }

    @Override
    public void reset()
    {
        if (isCommitted())
            throw new IllegalStateException("Response already commited!");

        this.originalResponse.headers().clear();
        this.resetBuffer();
    }

    @Override
    public void setLocale(Locale loc)
    {

    }

    @Override
    public Locale getLocale()
    {
        return null;
    }


    public void close()
    {
        this.responseCommitted = true;
        servletOutputStream.close();
        boolean isKeepAlive = HttpUtil.isKeepAlive(originalResponse);
        if (ctx.channel().isActive())
        {
            ChannelFuture channelFuture = ctx.write(DefaultLastHttpContent.EMPTY_LAST_CONTENT);
            if (!isKeepAlive && channelFuture != null)
            {
                channelFuture.addListener(ChannelFutureListener.CLOSE);
            }
        }
    }

    public DefaultHttpResponse getOriginalResponse()
    {
        return originalResponse;
    }

    public ChannelHandlerContext getCtx()
    {
        return ctx;
    }

    /**
     * 设置基本的请求头
     */
    public void setResponseBasicHeader()
    {
//        if (responseCommitted)
//        {
//            return;
//        }
        HttpRequest request = requestFacade.getOriginalRequest();
        HttpUtil.setKeepAlive(originalResponse, HttpUtil.isKeepAlive(request));


        HttpHeaders headers = originalResponse.headers();
        String contentType = headers.get(HttpHeaderNames.CONTENT_TYPE);
        if (null != contentType && contentType.toLowerCase().indexOf("charset") < 0)
        {
            //Content Type 响应头的内容
            String value = null == characterEncoding ? contentType : contentType + "; charset=" + characterEncoding;
            headers.set(HttpHeaderNames.CONTENT_TYPE, value);
        }
        CharSequence date = getFormattedDate();
        headers.set(HttpHeaderNames.DATE, date); // 时间日期响应头
        headers.set(HttpHeaderNames.SERVER, nettyServletContext.getServerInfo()); //服务器信息响应头

        // cookies处理
//        long curTime = System.currentTimeMillis(); //用于根据maxAge计算Cookie的Expires
        //先处理Session ，如果是新Session需要通过Cookie写入
//        if (requestFacade.getSession().isNew())
//        {
//
//        }


        /*

        String result = io.netty.handler.codec.http.cookie.ServerCookieEncoder.LAX.encode(new DefaultCookie(cookie.getName(), cookie.getValue()));
        this.originalResponse.headers().add(HttpHeaderNames.SET_COOKIE, result);
        */
        boolean isSessionIdSet = false;

        //其他业务或框架设置的cookie，逐条写入到响应头去
        for (Cookie cookie : cookies)
        {
            StringBuilder sb = new StringBuilder();
            sb.append(cookie.getName()).append("=").append(cookie.getValue())
                    .append("; max-Age=").append(cookie.getMaxAge());
            if (cookie.getPath() != null) sb.append("; path=").append(cookie.getPath());
            if (cookie.getDomain() != null) sb.append("; domain=").append(cookie.getDomain());
            if (cookie.getName().equalsIgnoreCase(NettyHttpSession.SESSION_COOKIE_NAME))
            {
                isSessionIdSet = true;
            }
            headers.add(HttpHeaderNames.SET_COOKIE, sb.toString());
        }

        if (!isSessionIdSet)
        {
            String sessionCookieStr = NettyHttpSession.SESSION_COOKIE_NAME + "=" + requestFacade.getRequestedSessionId() + "; path=/; domain=" + requestFacade.getServerName();
            headers.add(HttpHeaderNames.SET_COOKIE, sessionCookieStr);
        }

//        responseCommitted=true;
    }

    /**
     * SimpleDateFormat非线程安全，为了节省内存提高效率，把他放在ThreadLocal里
     * 用于设置HTTP响应头的时间信息
     */
    private static final FastThreadLocal<DateFormat> FORMAT = new FastThreadLocal<DateFormat>()
    {
        @Override
        protected DateFormat initialValue()
        {
            DateFormat df = new SimpleDateFormat("EEE, dd-MMM-yyyy HH:mm:ss z", Locale.ENGLISH);
            df.setTimeZone(TimeZone.getTimeZone("GMT"));
            return df;
        }
    };

    /**
     * @return 线程安全的获取当前时间格式化后的字符串
     */
    @VisibleForTesting
    private CharSequence getFormattedDate()
    {
        return new AsciiString(FORMAT.get().format(new Date()));
    }


    public void setRequestFacade(NettyServletRequest requestFacade)
    {
        this.requestFacade = requestFacade;

    }
}
