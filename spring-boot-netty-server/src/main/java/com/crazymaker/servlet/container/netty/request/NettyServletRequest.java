package com.crazymaker.servlet.container.netty.request;

import com.crazymaker.servlet.container.netty.async.AsyncContextImpl;
import com.crazymaker.servlet.container.netty.core.NettyRequestDispatcher;
import com.crazymaker.servlet.container.netty.core.NettyServletContext;
import com.crazymaker.servlet.container.netty.core.NettyServletHandler;
import com.crazymaker.servlet.container.netty.request.parser.CookieParser;
import com.crazymaker.servlet.container.netty.request.parser.ProtocolParser;
import com.crazymaker.servlet.container.netty.request.parser.SessionParser;
import com.crazymaker.servlet.container.netty.request.parser.UriParser;
import com.crazymaker.servlet.container.netty.response.NettyServletResponse;
import com.crazymaker.servlet.container.netty.utils.DateUtils;
import com.google.common.collect.Maps;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpUpgradeHandler;
import javax.servlet.http.Part;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Leibniz
 */
public class NettyServletRequest implements HttpServletRequest
{
    public static final String DISPATCHER_TYPE = NettyRequestDispatcher.class.getName() + ".DISPATCHER_TYPE";
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final ChannelHandlerContext ctx;
    private final NettyServletContext servletContext;
    private final HttpRequest originalRequest;
    private final NettyServletInputStream inputStream;
    private final NettyServletResponse servletResponse;

    private boolean asyncSupported = true;

    private UriParser uriParser;
    private ProtocolParser protocolParser;
    private final CookieParser cookieParser;
    private final SessionParser sessionParser;


    public NettyServletRequest(ChannelHandlerContext ctx,
                               NettyServletHandler handler,
                               FullHttpRequest originalRequest,
                               NettyServletResponse servletResponse)
    {
        this.ctx = ctx;
        this.servletContext = handler.getNettyServletContext();


        this.originalRequest = originalRequest;
        this.inputStream = new NettyServletInputStream(originalRequest);

        this.attributes = new ConcurrentHashMap<>();
        this.headers = this.originalRequest.headers();
        this.uriParser = new UriParser(this.originalRequest, this.servletContext.getContextPath());
        this.protocolParser = new ProtocolParser(this.originalRequest);
        this.cookieParser = new CookieParser(this.originalRequest);
        this.sessionParser = new SessionParser(this.originalRequest, cookieParser, servletContext);
        this.servletResponse = servletResponse;
    }

    @SuppressWarnings("unused")
    HttpRequest getNettyRequest()
    {
        return originalRequest;
    }


    /*====== Cookie 相关方法 开始 ======*/
    @Override
    public Cookie[] getCookies()
    {
        return cookieParser.getCookies();
    }
    /*====== Cookie 相关方法 结束 ======*/


    /*====== Header 相关方法 开始 ======*/
    private HttpHeaders headers;

    @Override
    public long getDateHeader(String name)
    {
        String header = this.headers.get(name);

        if (header == null)
        {
            return -1L;
        } else
        {
            Date date = DateUtils.parseDate(header);
            if (date == null)
            {
                return -1L;
            } else
            {
                return date.getTime();
            }
        }
    }


    @Override
    public String getHeader(String name)
    {
        return this.headers.get(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name)
    {
        return Collections.enumeration(this.headers.getAll(name));
    }

    @Override
    public Enumeration<String> getHeaderNames()
    {
        return Collections.enumeration(this.headers.names());
    }

    @Override
    public int getIntHeader(String name)
    {
        String headerStringValue = this.headers.get(name);
        if (headerStringValue == null)
        {
            return -1;
        }
        return Integer.parseInt(headerStringValue);
    }

    @Override
    public String getMethod()
    {
        return originalRequest.method().name();
    }

    /*====== Header 相关方法 结束 ======*/


    /*====== 各种路径 相关方法 开始 ======*/

    @Override
    public String getPathInfo()
    {
        return uriParser.getPathInfo();
    }

    @Override
    public String getQueryString()
    {

        return uriParser.getQueryString();
    }

    @Override
    public String getRequestURI()
    {

        return uriParser.getRequestURI();
    }

    @Override
    public StringBuffer getRequestURL()
    {
        StringBuffer url = new StringBuffer();
        String scheme = this.getScheme();
        int port = this.getServerPort();
        String urlPath = this.getRequestURI();

        url.append(scheme); // http, https
        url.append("://");
        url.append(this.getServerName());
        if ((scheme.equals("http") && port != 80)
                || (scheme.equals("https") && port != 443))
        {
            url.append(':');
            url.append(this.getServerPort());
        }
        url.append(urlPath);
        return url;
    }

    @Override
    public String getServletPath()
    {

        return uriParser.getServletPath();
    }

    @Override
    public String getContextPath()
    {
        return servletContext.getContextPath();
    }


    @Override
    public String getRealPath(String path)
    {
        return servletContext.getRealPath(path);
    }
    /*====== 各种路径 相关方法 结束 ======*/


    /*====== Session 相关方法 开始 ======*/

    @Override
    public HttpSession getSession(boolean create)
    {
        return sessionParser.getSession(create);

    }

    @Override
    public HttpSession getSession()
    {
        return sessionParser.getSession();
    }

    @Override
    public String changeSessionId()
    {
        return sessionParser.changeSessionId();

    }


    @Override
    public boolean isRequestedSessionIdValid()
    {
        return sessionParser.isRequestedSessionIdValid();
    }

    @Override
    public boolean isRequestedSessionIdFromCookie()
    {
        return sessionParser.isRequestedSessionIdFromCookie();
    }

    @Override
    public boolean isRequestedSessionIdFromURL()
    {
        return sessionParser.isRequestedSessionIdFromURL();
    }

    @Override
    @Deprecated
    public boolean isRequestedSessionIdFromUrl()
    {
        return sessionParser.isRequestedSessionIdFromURL();
    }

    @Override
    public String getRequestedSessionId()
    {
        return sessionParser.getRequestedSessionId();
    }
    /*====== Session 相关方法 结束 ======*/



    /*====== Request Parameters 相关方法 开始 ======*/

    private transient boolean isParameterParsed = false; //请求参数是否已经解析

    private final Map<String, List<String>> parameters = new HashMap<>();

    /**
     * 解析请求参数
     */
    private void parseParameter()
    {
        if (isParameterParsed)
        {
            return;
        }

        HttpMethod method = originalRequest.method();

        QueryStringDecoder decoder = new QueryStringDecoder(originalRequest.uri());
        if (HttpMethod.GET == method)
        {
            // 是GET请求
            parameters.putAll(decoder.parameters());
        } else if (HttpMethod.POST == method)
        {
            // 是POST请求
            HttpPostRequestDecoder httpPostRequestDecoder = new HttpPostRequestDecoder(originalRequest);
            try
            {
                List<InterfaceHttpData> parmList = httpPostRequestDecoder.getBodyHttpDatas();
                for (InterfaceHttpData parm : parmList)
                {
                    Attribute data = (Attribute) parm;
                    try
                    {
                        parseRequestBody(data);
                    } catch (Exception e)
                    {
                        log.error("HttpPostRequestDecoder error", e);
                    }
                }
            } finally
            {
                if (httpPostRequestDecoder != null)
                {
                    httpPostRequestDecoder.destroy();
                }
            }

        }

        this.isParameterParsed = true;
    }

    private void parseRequestBody(Attribute attribute) throws Exception
    {
        if (this.parameters.containsKey(attribute.getName()))
        {
            this.parameters.get(attribute.getName()).add(attribute.getValue());
        } else
        {
            List<String> values = new ArrayList<>();
            values.add(attribute.getValue());
            this.parameters.put(attribute.getName(), values);
            this.attributes.put(attribute.getValue(), values);
        }
    }


    /**
     * 检查请求参数是否已经解析。
     * 如果还没有则解析之。
     */
    private void checkParameterParsed()
    {
        if (!isParameterParsed)
        {
            parseParameter();
        }
    }

    @Override
    public String getParameter(String key)
    {
        checkParameterParsed();
        List<String> parameterValues = parameters.get(key);
        return (parameterValues == null || parameterValues.isEmpty()) ? null : parameterValues.get(0);

    }

    @Override
    public Enumeration<String> getParameterNames()
    {
        checkParameterParsed();
        return Collections.enumeration(parameters.keySet());
    }

    @Override
    public String[] getParameterValues(String key)
    {
        checkParameterParsed();
        List<String> values = this.parameters.get(key);
        if (values == null || values.isEmpty())
            return null;
        return values.toArray(new String[values.size()]);
    }

    @Override
    public Map<String, String[]> getParameterMap()
    {
        checkParameterParsed();
        Map<String, String[]> parameterMap = Maps.newHashMap();
        for (Map.Entry<String, List<String>> parameter : parameters.entrySet())
        {
            parameterMap.put(parameter.getKey(), parameter.getValue().toArray(new String[0]));
        }
        return parameterMap;
    }

    /*====== Request Parameters 相关方法 结束 ======*/


    /*====== 请求协议、地址、端口 相关方法 开始 ======*/
    @Override
    public String getProtocol()
    {
        String protocol = protocolParser.getProtocol();

        return protocol;
    }

    @Override
    public String getScheme()
    {
        String scheme = protocolParser.getScheme();
        if (null != scheme)
        {
            return scheme.toLowerCase();
        }
        return null;
    }


    @Override
    public String getServerName()
    {
        return protocolParser.getHostName();
    }

    @Override
    public int getServerPort()
    {
        return servletContext.getPort();

    }

    @Override
    public String getLocalName()
    {
        return getServerName();
    }

    @Override
    public String getLocalAddr()
    {
        InetSocketAddress address =
                (InetSocketAddress) ctx.channel().localAddress();
        return address.getAddress().getHostAddress();
    }

    @Override
    public int getLocalPort()
    {
        return getServerPort();
    }

    @Override
    public String getRemoteAddr()
    {
        return ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress().getHostAddress();
    }

    @Override
    public String getRemoteHost()
    {
        return ((InetSocketAddress) ctx.channel().remoteAddress()).getHostName();
    }

    @Override
    public int getRemotePort()
    {
        return ((InetSocketAddress) ctx.channel().remoteAddress()).getPort();
    }

    /*====== 请求协议、地址、端口 相关方法 结束 ======*/


    /*====== Request Attributes 相关方法 开始 ======*/
    private final Map<String, Object> attributes;

    @Override
    public Object getAttribute(String name)
    {
        return attributes.get(name);
    }

    @Override
    public Enumeration<String> getAttributeNames()
    {
        return Collections.enumeration(attributes.keySet());
    }

    @Override
    public void setAttribute(String name, Object o)
    {
        attributes.put(name, o);
    }

    @Override
    public void removeAttribute(String name)
    {
        attributes.remove(name);
    }



    /*====== Request Attributes 相关方法 结束 ======*/


    /*====== multipart/form-data 相关方法 开始 ======*/
    @Override
    public Collection<Part> getParts() throws IOException, IllegalStateException, ServletException
    {
        //TODO
        throw new IllegalStateException(
                "Method 'getParts' not yet implemented!");
    }

    @Override
    public Part getPart(String name) throws IOException, IllegalStateException, ServletException
    {
        //TODO
        throw new IllegalStateException(
                "Method 'getParts' not yet implemented!");
    }

    /*====== multipart/form-data 相关方法 结束 ======*/

    @Override
    public boolean isSecure()
    {
        return getScheme().equalsIgnoreCase("HTTPS");
    }

    @Override
    public ServletContext getServletContext()
    {
        return servletContext;
    }

    public ServletResponse getServletResponse()
    {
        return servletResponse;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException
    {
        return inputStream;
    }

    @Override
    public BufferedReader getReader() throws IOException
    {
        return new BufferedReader(new InputStreamReader(inputStream, getCharacterEncoding()));
    }

    @Override
    public int getContentLength()
    {
        return (int) HttpUtil.getContentLength(this.originalRequest, -1);
    }

    @Override
    public long getContentLengthLong()
    {
        return getContentLength();
    }

    private String characterEncoding;

    @Override
    public String getCharacterEncoding()
    {
        if (characterEncoding == null)
        {
            characterEncoding = parseCharacterEncoding();
        }
        return characterEncoding;
    }

    @Override
    public void setCharacterEncoding(String env) throws UnsupportedEncodingException
    {
        characterEncoding = env;
    }

    @Override
    public String getContentType()
    {
        return headers.get("content-type");
    }

    private static final String DEFAULT_CHARSET = "UTF-8";

    private String parseCharacterEncoding()
    {
        String contentType = getContentType();
        if (contentType == null)
        {
            return DEFAULT_CHARSET;
        }
        int start = contentType.indexOf("charset=");
        if (start < 0)
        {
            return DEFAULT_CHARSET;
        }
        String encoding = contentType.substring(start + 8);
        int end = encoding.indexOf(';');
        if (end >= 0)
        {
            encoding = encoding.substring(0, end);
        }
        encoding = encoding.trim();
        if ((encoding.length() > 2) && (encoding.startsWith("\""))
                && (encoding.endsWith("\"")))
        {
            encoding = encoding.substring(1, encoding.length() - 1);
        }
        return encoding.trim();
    }


    /*====== 以下是暂不处理的接口方法 ======*/

    @Override
    public Locale getLocale()
    {
        return null;
    }

    @Override
    public Enumeration<Locale> getLocales()
    {
        return null;
    }

    @Override
    public RequestDispatcher getRequestDispatcher(String path)
    {
        return servletContext.getRequestDispatcher(path);
    }

    @Override
    public String getAuthType()
    {
        throw new IllegalStateException(
                "Method 'getAuthType' not yet implemented!");
    }

    @Override
    public boolean authenticate(HttpServletResponse response) throws IOException, ServletException
    {
        throw new IllegalStateException(
                "Method 'authenticate' not yet implemented!");
    }

    @Override
    public void login(String username, String password) throws ServletException
    {
        throw new IllegalStateException(
                "Method 'login' not yet implemented!");
    }

    @Override
    public void logout() throws ServletException
    {
        throw new IllegalStateException(
                "Method 'logout' not yet implemented!");
    }

    @Override
    public <T extends HttpUpgradeHandler> T upgrade(Class<T> handlerClass) throws IOException, ServletException
    {
        throw new IllegalStateException(
                "Method 'upgrade' not yet implemented!");
    }

    @Override
    public String getPathTranslated()
    {
        throw new IllegalStateException(
                "Method 'getPathTranslated' not yet implemented!");
    }

    @Override
    public boolean isUserInRole(String role)
    {
        throw new IllegalStateException(
                "Method 'isUserInRole' not yet implemented!");
    }

    @Override
    public Principal getUserPrincipal()
    {
        return null;
    }

    @Override
    public String getRemoteUser()
    {
        throw new IllegalStateException(
                "Method 'getRemoteUser' not yet implemented!");
    }

    public HttpRequest getOriginalRequest()
    {
        return originalRequest;
    }


    /*====== 异步 相关方法 开始 ======*/
    private boolean asyncStarted = false;
    private AsyncContextImpl asyncContext;

    private DispatcherType dispatcherType = DispatcherType.REQUEST;

    public void setDispatcherType(DispatcherType dispatcherType)
    {
        this.dispatcherType = dispatcherType;
    }

    @Override
    public DispatcherType getDispatcherType()
    {
        return dispatcherType;
    }


    @Override
    public AsyncContext startAsync() throws IllegalStateException
    {
        this.asyncStarted = true;
        this.setDispatcherType(DispatcherType.ASYNC);
        this.asyncContext = new AsyncContextImpl(this, null);
        return this.asyncContext;
    }

    @Override
    public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) throws IllegalStateException
    {
        this.asyncStarted = true;
        this.setDispatcherType(DispatcherType.ASYNC);
        this.asyncContext = new AsyncContextImpl(servletRequest, servletResponse);
        return this.asyncContext;
    }

    @Override
    public boolean isAsyncStarted()
    {
        return asyncStarted;
    }

    public void setAsyncStarted(boolean asyncStarted)
    {
        this.asyncStarted = asyncStarted;
    }

    @SuppressWarnings("unused")
    void setAsyncSupported(boolean asyncSupported)
    {
        this.asyncSupported = asyncSupported;
    }

    @Override
    public boolean isAsyncSupported()
    {
        return asyncSupported;
    }

    @Override
    public AsyncContext getAsyncContext()
    {
        return asyncContext;
    }


    /*====== 异步 相关方法 结束 ======*/
}
