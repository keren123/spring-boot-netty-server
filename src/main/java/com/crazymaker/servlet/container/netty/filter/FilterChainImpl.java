package com.crazymaker.servlet.container.netty.filter;

import com.google.common.collect.Lists;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;
import java.util.LinkedList;


public class FilterChainImpl implements FilterChain
{

//    private Servlet servlet;

    private LinkedList<Filter> filters = Lists.newLinkedList();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException
    {
        Filter filter = filters.poll();
        if (null != filter)
        {
            filter.doFilter(request, response, this);
        }
    }

    public void addFilter(Filter filter)
    {
        filters.add(filter);
    }

//    public void setServlet(Servlet servlet) {
//        this.servlet = servlet;
//    }
}
