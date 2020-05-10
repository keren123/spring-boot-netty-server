package com.crazymaker.servlet.container.netty.core;

import com.crazymaker.servlet.container.netty.async.TracingThreadPoolExecutor;
import com.crazymaker.servlet.container.netty.filter.FilterDef;
import com.crazymaker.servlet.container.netty.filter.FilterMap;
import com.crazymaker.servlet.container.netty.filter.FilterRegistrationImpl;
import com.crazymaker.servlet.container.netty.registration.NettyFilterRegistration;
import com.crazymaker.servlet.container.netty.registration.NettyServletRegistration;
import com.crazymaker.servlet.container.netty.resource.WebResource;
import com.crazymaker.servlet.container.netty.resource.WebResourceRoot;
import com.crazymaker.servlet.container.netty.session.NettySessionManager;
import com.crazymaker.servlet.container.netty.utils.MimeTypeUtil;
import com.crazymaker.servlet.container.netty.utils.RequestUrlPatternMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ClassUtils;
import org.springframework.lang.Nullable;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterRegistration;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.SessionCookieConfig;
import javax.servlet.SessionTrackingMode;
import javax.servlet.descriptor.JspConfigDescriptor;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * ServletContext实现
 */
@Slf4j
public class NettyServletContext implements ServletContext
{

    private final String contextPath; //保证不以“/”结尾
    private final ClassLoader classLoader;
    private final String serverInfo;
    private volatile boolean initialized; //记录是否初始化完毕
    private RequestUrlPatternMapper servletUrlPatternMapper;
    private NettySessionManager sessionManager;

    private final Map<String, NettyServletRegistration> servlets = new HashMap<>(); //getServletRegistration()等方法要用，key是ServletName
    private final Map<String, NettyFilterRegistration> filters = new HashMap<>(); //getFilterRegistration()等方法要用，Key是FilterName
    private final Map<String, String> servletMappings = new HashMap<>(); //保存请求路径urlPattern与Servlet名的映射,urlPattern是不带contextPath的
    private final Hashtable<String, Object> attributes = new Hashtable<>();
    private static NettyServletContext instance;
    private final List<FilterMap> filterMapList = Lists.newArrayList();

    private final Map<String, Servlet> servletMap = Maps.newHashMap();


    private final Map<String, FilterDef> filterDefMap = Maps.newHashMap();
    /**
     * The Context instance with which we are associated.
     */
    WebResourceRoot resources;


    private TracingThreadPoolExecutor executor;
    /**
     * The document root for this web application.
     */
    private String docBase = null;

    /**
     * 服务端口
     */
    private int port;

    /**
     * 默认构造方法
     *
     * @param contextPath contextPath
     * @param classLoader classLoader
     * @param serverInfo  服务器信息，写在响应的server响应头字段
     */
    public NettyServletContext(String contextPath, ClassLoader classLoader, String serverInfo)
    {
        instance = this;
        if (contextPath.endsWith("/"))
        {
            contextPath = contextPath.substring(0, contextPath.length() - 1);
        }
        this.contextPath = contextPath;
        this.classLoader = classLoader;
        this.serverInfo = serverInfo;
        this.servletUrlPatternMapper = new RequestUrlPatternMapper(contextPath);
        this.sessionManager = new NettySessionManager(this);

        executor = new TracingThreadPoolExecutor(1, 1, new LinkedBlockingQueue<>(1));

    }

    public TracingThreadPoolExecutor getExecutor()
    {
        return executor;
    }

    public static NettyServletContext get()
    {
        return instance;
    }

    public NettySessionManager getSessionManager()
    {
        return sessionManager;
    }

    void setInitialised(boolean initialized)
    {
        this.initialized = initialized;
    }

    private boolean isInitialised()
    {
        return initialized;
    }

    private void checkNotInitialised()
    {
        checkState(!isInitialised(), "This method can not be called before the context has been initialised");
    }

    public void addServletMapping(String urlPattern, String name, Servlet servlet) throws ServletException
    {
        checkNotInitialised();
        servletMappings.put(urlPattern, checkNotNull(name));
        servletUrlPatternMapper.addServlet(urlPattern, servlet, name);
    }

    public void addServletMapping(String urlPattern, String name)
    {
        servletMappings.put(urlPattern, checkNotNull(name));
    }

    public void addFilterMapping(EnumSet<DispatcherType> dispatcherTypes, boolean isMatchAfter, String urlPattern)
    {
        checkNotInitialised();
        //TODO 过滤器的urlPatter解析
    }

    /**
     * SpringBoot只有一个Context，我觉得直接返回this就可以了
     */
    @Override
    public ServletContext getContext(String uripath)
    {
        return this;
    }

    @Override
    public String getContextPath()
    {
        return contextPath;
    }


    @Override
    public int getMajorVersion()
    {
        return 3;
    }

    @Override
    public int getMinorVersion()
    {
        return 0;
    }

    @Override
    public int getEffectiveMajorVersion()
    {
        return 3;
    }

    @Override
    public int getEffectiveMinorVersion()
    {
        return 0;
    }

    @Override
    public String getMimeType(String file)
    {
        return MimeTypeUtil.getMimeTypeByFileName(file);
    }

    @Override
    public Set<String> getResourcePaths(String path)
    {
        Set<String> thePaths = new HashSet<>();
        if (!path.endsWith("/"))
        {
            path += "/";
        }
        String basePath = getRealPath(path);
        if (basePath == null)
        {
            return thePaths;
        }
        File theBaseDir = new File(basePath);
        if (!theBaseDir.exists() || !theBaseDir.isDirectory())
        {
            return thePaths;
        }
        String theFiles[] = theBaseDir.list();
        if (theFiles == null)
        {
            return thePaths;
        }
        for (String filename : theFiles)
        {
            File testFile = new File(basePath + File.separator + filename);
            if (testFile.isFile())
                thePaths.add(path + filename);
            else if (testFile.isDirectory())
                thePaths.add(path + filename + "/");
        }
        return thePaths;
    }

    @Override
    public URL getResource(String path) throws MalformedURLException
    {
        String validatedPath = validateResourcePath(path, false);

        if (validatedPath == null)
        {
            log.error("path is error: {}", path);
        }

        WebResourceRoot resources = getResources(path);
        if (resources != null)
        {
            return resources.getResource(validatedPath).getURL();
        }

        return null;
    }

    public WebResourceRoot getResources(String path)
    {
        return resources;
    }


    public WebResourceRoot getResources()
    {
        return resources;
    }


    public WebResourceRoot getResourceRoot()
    {
        return resources;
    }

    public void setResources(WebResourceRoot resourceRoot)
    {
        this.resources = resourceRoot;
        resourceRoot.setContext(this);

    }

    @Override
    public InputStream getResourceAsStream(String path)
    {

        String validatedPath = validateResourcePath(path, false);

        if (validatedPath == null)
        {
            return null;
        }
        WebResource webResource = null;
        if (resources != null)
        {
            webResource = resources.getResource(validatedPath);
        }
        if (webResource != null)
        {
            return webResource.getInputStream();
        }

        return null;
    }


    @Override
    public RequestDispatcher getRequestDispatcher(String path)
    {
        String servletName = servletMappings.get(path);
        if (servletName == null)
        {
            servletName = servletMappings.get("/");
        }
        if (null == servletName)
        {
            return null;
        }
        return getNamedDispatcher(servletName);
    }

    @Override
    public RequestDispatcher getNamedDispatcher(String name)
    {
        try
        {
            Servlet servlet = servletMap.get(name);
            String servletPath = null;
            for (Map.Entry<String, String> servletEntry : servletMappings.entrySet())
            {
                if (servletEntry.getValue().equals(name))
                {
                    servletPath = servletEntry.getKey();
                }
            }

            if (null == servletPath)
            {
                log.error("Throwing exception when getting Filter from NettyFilterRegistration of name " + name);
                return null;
            }
            //TODO 过滤器的urlPatter解析
            List<Filter> allNeedFilters = new ArrayList<>();
            for (NettyFilterRegistration registration : this.filters.values())
            {
                allNeedFilters.add(registration.getFilter());
            }

            FilterChain filterChain = new NettyFilterChain(servlet, allNeedFilters);
            return new NettyRequestDispatcher(this, name, servletPath, servlet, filterChain);
        } catch (ServletException e)
        {
            log.error("Throwing exception when getting Filter from NettyFilterRegistration of name " + name, e);
            return null;
        }
    }

    @Override
    public Servlet getServlet(String name) throws ServletException
    {
        return servlets.get(name).getServlet();
    }

    @Override
    public Enumeration<Servlet> getServlets()
    {
        return Collections.emptyEnumeration();
    }

    @Override
    public Enumeration<String> getServletNames()
    {
        return Collections.emptyEnumeration();
    }

    @Override
    public void log(String msg)
    {
        log.info(msg);
    }

    @Override
    public void log(Exception exception, String msg)
    {
        log.error(msg, exception);
    }

    @Override
    public void log(String message, Throwable throwable)
    {
        log.error(message, throwable);
    }

    @Override
    public String getRealPath(String path)
    {
        if (!path.startsWith("/"))
            return null;
        try
        {
            File f = new File(getResource(path).toURI());
            return f.getAbsolutePath();
        } catch (Throwable t)
        {
            log.error("Throwing exception when getting real path of " + path, t);
            return null;
        }
    }


    /*
     * Returns null if the input path is not valid or a path that will be
     * acceptable to resources.getResource().
     */
    private String validateResourcePath(String path, boolean allowEmptyPath)
    {
        if (path == null)
        {
            return null;
        }

        if (path.length() == 0 && allowEmptyPath)
        {
            return path;
        }

        if (!path.startsWith("/"))
        {

            return "/" + path;

        }

        return path;
    }

    @Override
    public String getServerInfo()
    {
        return serverInfo;
    }

    @Override
    public String getInitParameter(String name)
    {
        return null;
    }

    @Override
    public Enumeration<String> getInitParameterNames()
    {
        return Collections.emptyEnumeration();
    }

    @Override
    public boolean setInitParameter(String name, String value)
    {
        return false;
    }

    @Override
    public Object getAttribute(String name)
    {
        return attributes.get(name);
    }

    @Override
    public Enumeration<String> getAttributeNames()
    {
        return attributes.keys();
    }

    @Override
    public void setAttribute(String name, Object object)
    {
        attributes.put(name, object);
    }

    @Override
    public void removeAttribute(String name)
    {
        attributes.remove(name);
    }

    @Override
    public String getServletContextName()
    {
        return getContextPath().toUpperCase(Locale.ENGLISH);
    }

    @Override
    public ServletRegistration.Dynamic addServlet(String servletName, String className)
    {
        return addServlet(servletName, className, null);
    }


    @Override
    public ServletRegistration.Dynamic addServlet(String servletName, Servlet servlet)
    {

        return addServlet(servletName, servlet.getClass().getName(), servlet);
    }

    @Override
    public ServletRegistration.Dynamic addServlet(String servletName, Class<? extends Servlet> servletClass)
    {
        return addServlet(servletName, servletClass.getName());
    }

    private ServletRegistration.Dynamic addServlet(String servletName, String className, Servlet servlet)
    {
        NettyServletRegistration servletRegistration = new NettyServletRegistration(this, servletName, className, servlet);
        servletMap.put(servletName, servlet);
        return servletRegistration;
    }

    @Override
    public <T extends Servlet> T createServlet(Class<T> c) throws ServletException
    {
        try
        {
            return c.newInstance();
        } catch (InstantiationException | IllegalAccessException e)
        {
            log.error("Throwing exception when creating instance of " + c.getName(), e);
        }
        return null;
    }

    @Override
    public ServletRegistration getServletRegistration(String servletName)
    {
        return null;
    }

    @Override
    public Map<String, ? extends ServletRegistration> getServletRegistrations()
    {
        return null;
    }

    @Override
    public FilterRegistration.Dynamic addFilter(String filterName, String className)
    {
        return addFilter(filterName, className, null);
    }

    @Override
    public FilterRegistration.Dynamic addFilter(String filterName, Filter filter)
    {
        return addFilter(filterName, filter.getClass().getName(), filter);
    }

    private FilterRegistration.Dynamic addFilter(
            String filterName, String filterClass, Filter filter)
    {
//        NettyFilterRegistration filterRegistration = new NettyFilterRegistration(this, filterName, filterClass, filter);
//        filters.put(filterName, filterRegistration);
//        return filterRegistration;
        if (filterName == null || filterName.equals(""))
        {
            throw new IllegalArgumentException("applicationContext.invalidFilterName" + filterName);
        }

        FilterDef filterDef = this.findFilterDef(filterName);

        // Assume a 'complete' FilterRegistration is one that has a class and
        // a name
        if (filterDef == null)
        {
            filterDef = new FilterDef();
            filterDef.setFilterName(filterName);
            this.addFilterDef(filterDef);
        } else
        {
            if (filterDef.getFilterName() != null &&
                    filterDef.getFilterClass() != null)
            {
                return null;
            }
        }

        if (filter == null)
        {
            try
            {
                Class clazz = ClassUtils.getClass(filterClass);
                filter = createFilter(clazz);
            } catch (Exception e)
            {
                throw new IllegalArgumentException("applicationContext.invalidFilterClass" + filterClass);
            }
            filterDef.setFilter(filter);
            filterDef.setFilterClass(filterClass);
        } else
        {
            filterDef.setFilterClass(filter.getClass().getName());
            filterDef.setFilter(filter);
        }

        return new FilterRegistrationImpl(filterDef, this);


    }

    @Override
    public FilterRegistration.Dynamic addFilter(String filterName, Class<? extends Filter> filterClass)
    {
        return addFilter(filterName, filterClass.getName());
    }

    @Override
    public <T extends Filter> T createFilter(Class<T> c) throws ServletException
    {
        try
        {
            return c.newInstance();
        } catch (InstantiationException | IllegalAccessException e)
        {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public FilterRegistration getFilterRegistration(String filterName)
    {
        return filters.get(filterName);
    }

    @Override
    public Map<String, ? extends FilterRegistration> getFilterRegistrations()
    {
        return ImmutableMap.copyOf(filters);
    }

    @Override
    public SessionCookieConfig getSessionCookieConfig()
    {
        return null;
    }

    @Override
    public void setSessionTrackingModes(Set<SessionTrackingMode> sessionTrackingModes) throws IllegalStateException, IllegalArgumentException
    {

    }

    @Override
    public Set<SessionTrackingMode> getDefaultSessionTrackingModes()
    {
        return null;
    }

    @Override
    public Set<SessionTrackingMode> getEffectiveSessionTrackingModes()
    {
        return null;
    }


    //TODO 暂不支持Listener，现在很少用了吧
    @Override
    public void addListener(String className)
    {

    }

    @Override
    public <T extends EventListener> void addListener(T t)
    {

    }

    @Override
    public void addListener(Class<? extends EventListener> listenerClass)
    {

    }

    @Override
    public <T extends EventListener> T createListener(Class<T> c) throws ServletException
    {
        return null;
    }

    @Override
    public void declareRoles(String... roleNames)
    {

    }

    @Override
    public String getVirtualServerName()
    {
        return null;
    }

    @Override
    public ClassLoader getClassLoader()
    {
        return classLoader;
    }

    @Override
    public JspConfigDescriptor getJspConfigDescriptor()
    {
        return null;
    }

    public void addFilterMap(FilterMap filterMap)
    {
        filterMapList.add(filterMap);
    }

    public List<FilterMap> getFilterMapList()
    {
        return filterMapList;
    }


    public FilterDef findFilterDef(String name)
    {
        return filterDefMap.get(name);
    }


    public void addFilterDef(FilterDef filterDef)
    {
        filterDefMap.put(filterDef.getFilterName(), filterDef);
    }

    public Map<String, FilterDef> getFilterDefMap()
    {
        return filterDefMap;
    }

    /**
     * The set of filter configurations (and associated filter instances) we
     * have initialized, keyed by filter name.
     */
    private HashMap<String, ApplicationFilterConfig> filterConfigs = new HashMap<>();


    /**
     * Configure and initialize the set of filters for this Context.
     *
     * @return <code>true</code> if all filter initialization completed
     * successfully, or <code>false</code> otherwise.
     */
    public boolean filterStart()
    {

        // Instantiate and record a FilterConfig for each defined filter
        boolean ok = true;
        synchronized (filterConfigs)
        {
            filterConfigs.clear();
            for (Map.Entry<String, FilterDef> entry : filterDefMap.entrySet())
            {
                String name = entry.getKey();
                log.debug(" Starting filter:{}", name);
                try
                {
                    ApplicationFilterConfig filterConfig =
                            new ApplicationFilterConfig(this, entry.getValue());
                    filterConfigs.put(name, filterConfig);
                } catch (Throwable t)
                {
                    t.printStackTrace();
                    ok = false;
                }
            }
        }

        return ok;

    }


    /**
     * Configure and initialize the set of filters for this Context.
     *
     * @return <code>true</code> if all filter initialization completed
     * successfully, or <code>false</code> otherwise.
     */
    public boolean servletStart()
    {

        // Instantiate and record a FilterConfig for each defined filter
        boolean ok = true;
        synchronized (servletMap)
        {
            for (Map.Entry<String, Servlet> entry : servletMap.entrySet())
            {
                String name = entry.getKey();
                log.debug(" Starting Servlet:{}", name);
                try
                {
                    Servlet servlet = entry.getValue();
                    ServletConfig config = new DelegatingServletConfig(name);

                    servlet.init(config);
                } catch (Throwable t)
                {
                    t.printStackTrace();
                    ok = false;
                }
            }
        }

        return ok;

    }

    /**
     * @return the document root for this Context.  This can be an absolute
     * pathname, a relative pathname, or a URL.
     */
    public String getDocBase()
    {

        return this.docBase;
    }

    /**
     * Set the document root for this Context.  This can be an absolute
     * pathname, a relative pathname, or a URL.
     *
     * @param docBase The new document root
     */
    public void setDocBase(String docBase)
    {

        this.docBase = docBase;

    }

    /**
     * The application root for this Host.
     */
    private String appBase = "webapps";
    private volatile File appBaseFile = null;

    /**
     * ({@inheritDoc}
     */
    public File getAppBaseFile()
    {

        if (appBaseFile != null)
        {
            return appBaseFile;
        }

        File file = new File("webapps");


        // Make it canonical if possible
        try
        {
            file = file.getCanonicalFile();
        } catch (IOException ioe)
        {
            // Ignore
        }

        this.appBaseFile = file;
        return file;
    }

    /**
     * The human-readable name of this Container.
     */
    protected String name = null;

    public String getName()
    {
        return (name);

    }

    /**
     * Set a name string (suitable for use by humans) that describes this
     * Container.  Within the set of child containers belonging to a particular
     * parent, Container names must be unique.
     *
     * @param name New name of this container
     * @throws IllegalStateException if this Container has already been
     *                               added to the children of a parent Container (after which the name
     *                               may not be changed)
     */
    public void setName(String name)
    {

        this.name = name;
    }


    private boolean addWebinfClassesResources = false;

    public void setAddWebinfClassesResources(boolean addWebinfClassesResources)
    {
        this.addWebinfClassesResources = addWebinfClassesResources;
    }


    public boolean getAddWebinfClassesResources()
    {
        return addWebinfClassesResources;
    }

    public void setPort(int port)
    {
        this.port = port;
    }

    public int getPort()
    {
        return port;
    }

    /**
     * Internal implementation of the ServletConfig interface, to be passed
     * to the wrapped servlet. Delegates to ServletWrappingController fields
     * and methods to provide init parameters and other environment info.
     */
    @AllArgsConstructor
    private class DelegatingServletConfig implements ServletConfig
    {


        private String servletName;

        @Override
        @Nullable
        public String getServletName()
        {
            return servletName;
        }

        @Override
        @Nullable
        public ServletContext getServletContext()
        {
            return NettyServletContext.this;
        }

        @Override
        public String getInitParameter(String paramName)
        {
            return null;
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public Enumeration<String> getInitParameterNames()
        {
            return Collections.emptyEnumeration();
        }
    }
}
