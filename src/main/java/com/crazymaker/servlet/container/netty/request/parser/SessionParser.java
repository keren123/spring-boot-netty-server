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

package com.crazymaker.servlet.container.netty.request.parser;


import com.crazymaker.servlet.container.netty.core.NettyServletContext;
import com.crazymaker.servlet.container.netty.session.NettyHttpSession;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpSession;

/**
 * session 会话 解析
 * create by 尼恩 @ 疯狂创客圈
 */
public class SessionParser
{

    private final CookieParser cookieParser;
    private NettyHttpSession session;
    private boolean isCookieSession;
    private boolean isURLSession;
    /**
     * 懒加载：是否已经解析过
     */
    boolean isParsed = false;

    /**
     * 请求实例
     */
    private final HttpRequest request;

    /**
     * 请求头
     */
    private final HttpHeaders headers;
    /**
     * 请求上下文
     */
    private final NettyServletContext servletContext;
    /**
     * cookies数组
     */
    private javax.servlet.http.Cookie[] cookies;

    public SessionParser(HttpRequest request, CookieParser cookieParser, NettyServletContext servletContext)
    {
        this.request = request;
        headers = request.headers();
        this.cookieParser = cookieParser;
        this.servletContext = servletContext;
    }

    /**
     * 先后看请求路径和Cookie中是否有sessionid
     * 有，则从SessionManager获取session对象放入session属性
     * 如果session对象过期，则创建一个新的并放入
     * 无，则创建一个新Session并放入
     */
    private void checkAndParse()
    {
        if (isParsed)
        {
            return;
        }

        String sessionId;
        NettyHttpSession curSession;

        //从Cookie解析SessionID
        sessionId = getSessionIdFromCookie();
        if (sessionId != null)
        {
            curSession = servletContext.getSessionManager().getSession(sessionId);
            if (null != curSession)
            {
                this.isCookieSession = true;
                recoverySession(curSession);
                return;
            }
        }

        if (!this.isCookieSession)
        {
            // 从请求路径解析SessionID
            sessionId = getSessionIdFromUrl();
            curSession = servletContext.getSessionManager().getSession(sessionId);
            if (null != curSession)
            {
                this.isURLSession = true;
                recoverySession(curSession);
                return;
            }
        }
        //Cookie和请求参数中都没拿到Session，则创建一个
        if (this.session == null)
        {
            this.session = createSession();
        }
        isParsed = true;
    }


    /**
     * @return 从URL解析到的SessionID
     */
    private String getSessionIdFromUrl()
    {
        StringBuilder u = new StringBuilder(request.uri());
        int sessionStart = u.toString().indexOf(";" + NettyHttpSession.SESSION_REQUEST_PARAMETER_NAME + "=");
        if (sessionStart == -1)
        {
            return null;
        }
        int sessionEnd = u.toString().indexOf(';', sessionStart + 1);
        if (sessionEnd == -1)
            sessionEnd = u.toString().indexOf('?', sessionStart + 1);
        if (sessionEnd == -1) // still
            sessionEnd = u.length();
        return u.substring(sessionStart + NettyHttpSession.SESSION_REQUEST_PARAMETER_NAME.length() + 2, sessionEnd);
    }

    /**
     * @return 从Cookie解析到的SessionID
     */
    private String getSessionIdFromCookie()
    {
        Cookie[] cookies = cookieParser.getCookies();
        if (cookies == null)
        {
            return null;
        }
        for (Cookie cookie : cookies)
        {
            if (cookie.getName().equals(NettyHttpSession.SESSION_COOKIE_NAME))
            {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * 恢复旧Session
     *
     * @param curSession 要恢复的Session对象
     */
    private void recoverySession(NettyHttpSession curSession)
    {
//        checkAndParse();
        this.session = curSession;
        this.session.setNew(false);
        this.servletContext.getSessionManager().updateAccessTime(this.session);
    }


    public HttpSession getSession(boolean create)
    {
        checkAndParse();
        boolean valid = isRequestedSessionIdValid(); //在管理器存在，且没到期
        //可用则直接返回
        if (valid)
        {
            return session.getSession();
        }
        //不可用则判断是否新建
        if (!create)
        {
            session = null; //如果过期了设为null
            return null;
        }
        //不可用且允许新建则新建之
        this.session = createSession();
        return this.session.getSession();
    }


    public HttpSession getSession()
    {
        return getSession(true);
    }


    public String changeSessionId()
    {
        checkAndParse();

        this.session = createSession();
        return this.session.getId();
    }

    private NettyHttpSession createSession()
    {

        return servletContext.getSessionManager().createSession();
    }


    public boolean isRequestedSessionIdValid()
    {
        checkAndParse();

        return servletContext.getSessionManager().checkValid(session);
    }


    public boolean isRequestedSessionIdFromCookie()
    {
        checkAndParse();

        return isCookieSession;
    }


    public boolean isRequestedSessionIdFromURL()
    {
        checkAndParse();

        return isURLSession;
    }


    public boolean isRequestedSessionIdFromUrl()
    {
        checkAndParse();

        return isRequestedSessionIdFromURL();
    }


    public String getRequestedSessionId()
    {
        checkAndParse();

        return session.getId();
    }

}
