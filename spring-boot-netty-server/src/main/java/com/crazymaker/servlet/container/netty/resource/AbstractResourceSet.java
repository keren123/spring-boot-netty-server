/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.crazymaker.servlet.container.netty.resource;

import com.crazymaker.servlet.container.netty.lifecycle.LifecycleBase;
import com.crazymaker.servlet.container.netty.lifecycle.LifecycleException;
import com.crazymaker.servlet.container.netty.lifecycle.LifecycleState;

import java.util.jar.Manifest;

public abstract class AbstractResourceSet extends LifecycleBase
        implements WebResourceSet
{

    private WebResourceRoot root;
    private String base;
    private String internalPath = "";
    private String webAppMount;
    private boolean classLoaderOnly;
    private boolean staticOnly;
    private Manifest manifest;


    protected final void checkPath(String path)
    {
        if (path == null || path.length() == 0 || path.charAt(0) != '/')
        {
            throw new IllegalArgumentException("abstractResourceSet.checkPath:" + path);
        }
    }

    @Override
    public final void setRoot(WebResourceRoot root)
    {
        this.root = root;
    }

    protected final WebResourceRoot getRoot()
    {
        return root;
    }


    protected final String getInternalPath()
    {
        return internalPath;
    }

    public final void setInternalPath(String internalPath)
    {
        checkPath(internalPath);
        // Optimise internal processing
        if (internalPath.equals("/"))
        {
            this.internalPath = "";
        } else
        {
            this.internalPath = internalPath;
        }
    }

    public final void setWebAppMount(String webAppMount)
    {
        checkPath(webAppMount);
        // Optimise internal processing
        if (webAppMount.equals("/"))
        {
            this.webAppMount = "";
        } else
        {
            this.webAppMount = webAppMount;
        }
    }

    protected final String getWebAppMount()
    {
        return webAppMount;
    }

    public final void setBase(String base)
    {
        this.base = base;
    }

    protected final String getBase()
    {
        return base;
    }

    @Override
    public boolean getClassLoaderOnly()
    {
        return classLoaderOnly;
    }

    @Override
    public void setClassLoaderOnly(boolean classLoaderOnly)
    {
        this.classLoaderOnly = classLoaderOnly;
    }

    @Override
    public boolean getStaticOnly()
    {
        return staticOnly;
    }

    @Override
    public void setStaticOnly(boolean staticOnly)
    {
        this.staticOnly = staticOnly;
    }

    protected final void setManifest(Manifest manifest)
    {
        this.manifest = manifest;
    }

    protected final Manifest getManifest()
    {
        return manifest;
    }


    //-------------------------------------------------------- Lifecycle methods
    @Override
    protected final void startInternal() throws LifecycleException
    {
        setState(LifecycleState.STARTING);
    }

    @Override
    protected final void stopInternal() throws LifecycleException
    {
        setState(LifecycleState.STOPPING);
    }

    @Override
    protected final void destroyInternal() throws LifecycleException
    {
        gc();
    }
}
