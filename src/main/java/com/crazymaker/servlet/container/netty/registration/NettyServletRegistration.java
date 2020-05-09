package com.crazymaker.servlet.container.netty.registration;

import com.crazymaker.servlet.container.netty.core.NettyServletContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.MultipartConfigElement;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.ServletSecurityElement;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

/**
 * Servlet的注册器，一个Servlet对应一个注册器
 */
public class NettyServletRegistration extends AbstractNettyRegistration implements ServletRegistration.Dynamic
{
    private final Logger log = LoggerFactory.getLogger(getClass());
    private volatile boolean initialised;
    private Servlet servlet;
    private Collection<String> urlPatternMappings = new LinkedList<>();

    public NettyServletRegistration(NettyServletContext context, String servletName, String className, Servlet servlet)
    {
        super(servletName, className, context);
        this.servlet = servlet;
    }

    public Servlet getServlet() throws ServletException
    {
        if (!initialised)
        {
            synchronized (this)
            {
                if (!initialised)
                {
                    if (null == servlet)
                    {
                        try
                        {
                            servlet = (Servlet) Class.forName(getClassName()).newInstance(); //反射获取实例
                        } catch (Exception e)
                        {
                            throw new ServletException(e);
                        }
                    }
                    servlet.init(this); //初始化Servlet
                    initialised = true;
                }
            }
        }
        return servlet;
    }

    @Override
    public void setLoadOnStartup(int loadOnStartup)
    {

    }

    @Override
    public Set<String> setServletSecurity(ServletSecurityElement constraint)
    {
        return null;
    }

    @Override
    public void setMultipartConfig(MultipartConfigElement multipartConfig)
    {

    }

    @Override
    public void setRunAsRole(String roleName)
    {

    }

    @Override
    public String getRunAsRole()
    {
        return null;
    }

    @Override
    public Set<String> addMapping(String... urlPatterns)
    {

        NettyServletContext context = NettyServletContext.get();
        for (String urlPattern : urlPatterns)
        {
            context.addServletMapping(urlPattern, getName());
        }
        urlPatternMappings.addAll(Arrays.asList(urlPatterns));
        return new HashSet<>(urlPatternMappings);
        /*
        //在RequestUrlPatternMapper中会检查url Pattern是否冲突
        NettyServletContext context = getNettyContext();
        for (String urlPattern : urlPatterns)
        {
            try
            {
                context.addServletMapping(urlPattern, getName(), getServlet());
            } catch (ServletException e)
            {
                log.error("Throwing exception when getting Servlet in NettyServletRegistration.", e);
            }
        }
        urlPatternMappings.addAll(Arrays.asList(urlPatterns));
        return new HashSet<>(urlPatternMappings);*/
    }

    @Override
    public Collection<String> getMappings()
    {
        return urlPatternMappings;
    }
}
