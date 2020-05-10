package com.crazymaker.servlet.container.netty.bootstrap;

import com.crazymaker.servlet.container.netty.core.NettyServletContext;
import com.crazymaker.servlet.container.netty.core.NettyWebServer;
import com.crazymaker.servlet.container.netty.lifecycle.LifecycleException;
import com.crazymaker.servlet.container.netty.resource.LoaderHidingWebResourceSet;
import com.crazymaker.servlet.container.netty.resource.StandardRoot;
import com.crazymaker.servlet.container.netty.resource.WebResourceRoot;
import com.crazymaker.servlet.container.netty.resource.WebResourceSet;
import io.netty.bootstrap.Bootstrap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.server.WebServerException;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.boot.web.servlet.server.AbstractServletWebServerFactory;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.ClassUtils;

import javax.servlet.ServletException;
import java.io.File;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

/**
 * Spring Boot会查找 AbstractServletWebServerFactory抽象类的实现类(工厂类)，调用其getWebServer(...)方法，来获取web应用的容器
 * Spring Boot2.0 之前的版本， 需要继承 AbstractEmbeddedServletContainerFactory 抽象类
 */
public class NettyWebServerFactory extends AbstractServletWebServerFactory implements ResourceLoaderAware
{
    private final Logger log = LoggerFactory.getLogger(getClass());
    private static final String SERVER_INFO = "spring-boot-netty-server";
    private NettyServletContext context;
    private ResourceLoader resourceLoader;

    @Override
    public WebServer getWebServer(ServletContextInitializer... initializers)
    {

        ClassLoader parentClassLoader = null;
        if (resourceLoader != null)
        {
            parentClassLoader = resourceLoader.getClassLoader();
        } else
        {
            parentClassLoader = ClassUtils.getDefaultClassLoader();
        }
        //Netty启动环境相关信息
        Package nettyPackage = Bootstrap.class.getPackage();
        String title = nettyPackage.getImplementationTitle();
        String version = nettyPackage.getImplementationVersion();
        log.info("Running with " + title + " " + version);
        //是否支持默认Servlet
        if (isRegisterDefaultServlet())
        {
            log.warn("This container does not support a default servlet");
        }
        URLClassLoader urlClassLoader =
                new URLClassLoader(new URL[]{}, parentClassLoader);
        String contextPath = getContextPath();
        //上下文
        context = new NettyServletContext(contextPath, urlClassLoader, SERVER_INFO);
        for (ServletContextInitializer initializer : initializers)
        {
            try
            {
                initializer.onStartup(context);
            } catch (ServletException e)
            {
                throw new RuntimeException(e);
            }
        }
        //从SpringBoot配置中获取端口，如果没有则随机生成
        int port = getPort();
        assert (port > 0);
        File documentRoot = getValidDocumentRoot();
        File docBase = null;
        if (documentRoot != null)
        {
            docBase = documentRoot;
        } else
        {
            docBase = createTempDir("netty-server-docbase");
        }
        context.setDocBase(docBase.getAbsolutePath());
        context.setName(SERVER_INFO);
        context.setPort(port);
        StandardRoot root = new LoaderHidingResourceRoot(context);

        context.setResources(root);
        try
        {
            //初始化根目录下的资源
            root.start();
        } catch (LifecycleException e)
        {
            e.printStackTrace();
        }
        //todo
        if (documentRoot != null)
        {
        }
        //加载 Jar 中的 web 资源
        List<URL> jarResouces = getUrlsOfJarsWithMetaInfResources();
        addResourceJars(jarResouces);


        InetSocketAddress address = new InetSocketAddress(port);
        log.info("Server initialized with port: " + port);

        /**
         * 初始化filter 过滤器
         */
        if (!context.filterStart())
        {
            throw new WebServerException("Could not start Netty server",
                    new Exception("standardContext.filterStart Fail"));
        }

        /**
         * 初始化 Servlet 容器
         */
        if (!context.servletStart())
        {
            throw new WebServerException("Could not start Netty server",
                    new Exception("standardContext.servletStart Fail"));
        }

        //初始化容器,并返回
        return new NettyWebServer(address, context);
    }

    private void addResourceJars(List<URL> resourceJarUrls)
    {
        for (URL url : resourceJarUrls)
        {
            String path = url.getPath();
            if (path.endsWith(".jar") || path.endsWith(".jar!/"))
            {
                String jar = url.toString();
                if (!jar.startsWith("jar:"))
                {
                    // A jar file in the file system. Convert to Jar URL.
                    jar = "jar:" + jar + "!/";
                }
                addResourceSet(jar);
            } else
            {
                addResourceSet(url.toString());
            }
        }
    }

    private void addResourceSet(String resource)
    {
        try
        {
            if (isInsideNestedJar(resource))
            {
                // It's a nested jar but we now don't want the suffix because Tomcat
                // is going to try and locate it as a root URL (not the resource
                // inside it)
                resource = resource.substring(0, resource.length() - 2);
            }
            URL url = new URL(resource);
            String path = "/META-INF/resources";
            this.context.getResources().createWebResourceSet(
                    WebResourceRoot.ResourceSetType.RESOURCE_JAR, "/", url, path);
        } catch (Exception ex)
        {
            // Ignore (probably not a directory)
        }
    }

    private boolean isInsideNestedJar(String dir)
    {
        return dir.indexOf("!/") < dir.lastIndexOf("!/");
    }

    private static final class LoaderHidingResourceRoot extends StandardRoot
    {

        private LoaderHidingResourceRoot(NettyServletContext context)
        {
            super(context);
        }

        @Override
        protected WebResourceSet createMainResourceSet()
        {
            return new LoaderHidingWebResourceSet(super.createMainResourceSet());
        }

    }

    /**
     * 设置资源加载器
     *
     * @param resourceLoader
     */
    @Override
    public void setResourceLoader(ResourceLoader resourceLoader)
    {
        this.resourceLoader = resourceLoader;
    }
}
