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


package com.crazymaker.servlet.container.netty.core;


import com.crazymaker.servlet.container.netty.filter.FilterDef;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ClassUtils;

import javax.management.ObjectName;
import javax.naming.NamingException;
import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;


/**
 * Implementation of a <code>javax.servlet.FilterConfig</code> useful in
 * managing the filter instances instantiated when a web application
 * is first started.
 *
 * @author Craig R. McClanahan
 */
@Slf4j
public final class ApplicationFilterConfig implements FilterConfig, Serializable
{

    private static final long serialVersionUID = 1L;


    /**
     * Empty String collection to serve as the basis for empty enumerations.
     */
    private static final List<String> emptyString = Collections.emptyList();

    // ----------------------------------------------------------- Constructors


    /**
     * Construct a new ApplicationFilterConfig for the specified filter
     * definition.
     *
     * @param context   The context with which we are associated
     * @param filterDef Filter definition for which a FilterConfig is to be
     *                  constructed
     * @throws ClassCastException        if the specified class does not implement
     *                                   the <code>javax.servlet.Filter</code> interface
     * @throws ClassNotFoundException    if the filter class cannot be found
     * @throws IllegalAccessException    if the filter class cannot be
     *                                   publicly instantiated
     * @throws InstantiationException    if an exception occurs while
     *                                   instantiating the filter object
     * @throws ServletException          if thrown by the filter's init() method
     * @throws NamingException
     * @throws InvocationTargetException
     * @throws SecurityException
     * @throws NoSuchMethodException
     * @throws IllegalArgumentException
     */
    ApplicationFilterConfig(NettyServletContext context, FilterDef filterDef)
            throws ClassCastException, ServletException, IllegalArgumentException, SecurityException
    {

        super();

        this.context = context;
        this.filterDef = filterDef;
        // Allocate a new filter instance if necessary
        if (filterDef.getFilter() == null)
        {
            getFilter();
        } else
        {
            this.filter = filterDef.getFilter();
            initFilter();
        }
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * The Context with which we are associated.
     */
    private final transient NettyServletContext context;


    /**
     * The application Filter we are configured for.
     */
    private transient Filter filter = null;


    /**
     * The <code>FilterDef</code> that defines our associated Filter.
     */
    private final FilterDef filterDef;


    /**
     * JMX registration name
     */
    private ObjectName oname;

    // --------------------------------------------------- FilterConfig Methods


    /**
     * Return the name of the filter we are configuring.
     */
    @Override
    public String getFilterName()
    {
        return (filterDef.getFilterName());
    }

    /**
     * @return The class of the filter we are configuring.
     */
    public String getFilterClass()
    {
        return filterDef.getFilterClass();
    }

    /**
     * Return a <code>String</code> containing the value of the named
     * initialization parameter, or <code>null</code> if the parameter
     * does not exist.
     *
     * @param name Name of the requested initialization parameter
     */
    @Override
    public String getInitParameter(String name)
    {

        Map<String, String> map = filterDef.getParameterMap();
        if (map == null)
        {
            return (null);
        }

        return map.get(name);

    }


    /**
     * Return an <code>Enumeration</code> of the names of the initialization
     * parameters for this Filter.
     */
    @Override
    public Enumeration<String> getInitParameterNames()
    {
        Map<String, String> map = filterDef.getParameterMap();

        if (map == null)
        {
            return Collections.enumeration(emptyString);
        }

        return Collections.enumeration(map.keySet());
    }


    /**
     * Return the ServletContext of our associated web application.
     */
    @Override
    public ServletContext getServletContext()
    {

        return this.context;

    }


    /**
     * Return a String representation of this object.
     */
    @Override
    public String toString()
    {

        StringBuilder sb = new StringBuilder("ApplicationFilterConfig[");
        sb.append("name=");
        sb.append(filterDef.getFilterName());
        sb.append(", filterClass=");
        sb.append(filterDef.getFilterClass());
        sb.append("]");
        return (sb.toString());

    }

    // --------------------------------------------------------- Public Methods

    public Map<String, String> getFilterInitParameterMap()
    {
        return Collections.unmodifiableMap(filterDef.getParameterMap());
    }

    // -------------------------------------------------------- Package Methods


    /**
     * Return the application Filter we are configured for.
     *
     * @throws ClassCastException        if the specified class does not implement
     *                                   the <code>javax.servlet.Filter</code> interface
     * @throws ClassNotFoundException    if the filter class cannot be found
     * @throws IllegalAccessException    if the filter class cannot be
     *                                   publicly instantiated
     * @throws InstantiationException    if an exception occurs while
     *                                   instantiating the filter object
     * @throws ServletException          if thrown by the filter's init() method
     * @throws NamingException
     * @throws InvocationTargetException
     * @throws SecurityException
     * @throws NoSuchMethodException
     * @throws IllegalArgumentException
     */
    Filter getFilter() throws ClassCastException, ServletException,
            IllegalArgumentException, SecurityException
    {

        // Return the existing filter instance, if any
        if (this.filter != null)
            return (this.filter);

        // Identify the class loader we will be using
        String filterClass = filterDef.getFilterClass();

        ////
        try
        {
            Class clazz = ClassUtils.getClass(filterClass);
            this.filter = context.createFilter(clazz);
        } catch (Exception e)
        {
            throw new IllegalArgumentException("applicationContext.invalidFilterClass" + filterClass);
        }
        ///

        initFilter();

        return (this.filter);

    }

    private void initFilter() throws ServletException
    {

        filter.init(this);

    }

    /**
     * Return the filter definition we are configured for.
     */
    FilterDef getFilterDef()
    {

        return (this.filterDef);

    }


}
