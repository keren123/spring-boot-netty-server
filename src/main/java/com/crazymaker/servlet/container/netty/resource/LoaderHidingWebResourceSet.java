package com.crazymaker.servlet.container.netty.resource;

import com.crazymaker.servlet.container.netty.lifecycle.LifecycleBase;
import com.crazymaker.servlet.container.netty.lifecycle.LifecycleException;
import org.springframework.util.ReflectionUtils;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Set;

public final class LoaderHidingWebResourceSet extends AbstractResourceSet
{

    private final WebResourceSet delegate;

    private final Method initInternal;

    public LoaderHidingWebResourceSet(WebResourceSet delegate)
    {
        this.delegate = delegate;
        try
        {
            this.initInternal = LifecycleBase.class.getDeclaredMethod("initInternal");
            this.initInternal.setAccessible(true);
        } catch (Exception ex)
        {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public WebResource getResource(String path)
    {
        if (path.startsWith("/org/springframework/boot"))
        {
            return new EmptyResource(getRoot(), path);
        }
        return this.delegate.getResource(path);
    }

    @Override
    public String[] list(String path)
    {
        return this.delegate.list(path);
    }

    @Override
    public Set<String> listWebAppPaths(String path)
    {
        return this.delegate.listWebAppPaths(path);
    }

    @Override
    public boolean mkdir(String path)
    {
        return this.delegate.mkdir(path);
    }

    @Override
    public boolean write(String path, InputStream is, boolean overwrite)
    {
        return this.delegate.write(path, is, overwrite);
    }

    @Override
    public URL getBaseUrl()
    {
        return this.delegate.getBaseUrl();
    }

    @Override
    public void setReadOnly(boolean readOnly)
    {
        this.delegate.setReadOnly(readOnly);
    }

    @Override
    public boolean isReadOnly()
    {
        return this.delegate.isReadOnly();
    }

    @Override
    public void gc()
    {
        this.delegate.gc();
    }

    @Override
    protected void initInternal() throws LifecycleException
    {
        if (this.delegate instanceof LifecycleBase)
        {
            try
            {
                ReflectionUtils.invokeMethod(this.initInternal, this.delegate);
            } catch (Exception ex)
            {
                throw new LifecycleException(ex);
            }
        }
    }

}