package com.crazymaker.servlet.container.netty.utils;

import javax.servlet.Servlet;

/**
 * Created on 2017-08-25 12:28.
 */
public class MappingData
{

    Servlet servlet = null;
    String servletName;
    String redirectPath;

    public void recycle()
    {
        servlet = null;
        servletName = null;
        redirectPath = null;
    }

}
