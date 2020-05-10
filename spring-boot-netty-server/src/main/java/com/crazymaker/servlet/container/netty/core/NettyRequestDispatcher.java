package com.crazymaker.servlet.container.netty.core;

import com.crazymaker.servlet.container.netty.filter.FilterChainFactory;
import com.crazymaker.servlet.container.netty.filter.FilterChainImpl;
import com.crazymaker.servlet.container.netty.request.NettyServletRequest;
import com.crazymaker.servlet.container.netty.response.NettyServletResponse;

import javax.servlet.FilterChain;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 分发器，除了传统的forward和include，把正常的Servlet调用也放在这里 dispatch()方法
 */
public class NettyRequestDispatcher implements RequestDispatcher
{

    /**
     * The servlet name for a named dispatcher.
     */
    private String servletName = null;

    /**
     * The servlet path for this RequestDispatcher.
     */
    private String servletPath = null;

    private final ServletContext context;

    private Servlet httpServlet;

    private FilterChain filterChain;

    NettyRequestDispatcher(ServletContext context,
                           String servletName, String servletPath,
                           Servlet servlet, FilterChain filterChain)
    {
        this.context = context;
        this.servletName = servletName;
        this.servletPath = servletPath;
        this.httpServlet = servlet;
        this.filterChain = filterChain;

    }

    @Override
    public void forward(ServletRequest servletRequest, ServletResponse servletResponse) throws ServletException, IOException
    {
        if (httpServlet != null)
        {
            //TODO Wrap
            httpServlet.service(servletRequest, servletResponse);
        } else
        {
            ((HttpServletResponse) servletResponse).sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    @Override
    public void include(ServletRequest servletRequest, ServletResponse servletResponse) throws ServletException, IOException
    {
        if (httpServlet != null)
        {
            //TODO Wrap
            httpServlet.service(servletRequest, servletResponse);
        } else
        {
            ((HttpServletResponse) servletResponse).sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    public void dispatch(NettyServletRequest request, NettyServletResponse response) throws ServletException, IOException
    {
        FilterChainImpl chain = FilterChainFactory.createFilterChain(request, httpServlet);
        chain.doFilter(request, response);
        if (response.getStatus() == HttpServletResponse.SC_OK)
        {
            httpServlet.service(request, response);
        }
    }
}
