package com.crazymaker.servlet.container.netty.filter;

import javax.servlet.Filter;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;


/**
 * Representation of a filter definition for a web application, as represented
 * in a <code>&lt;filter&gt;</code> element in the deployment descriptor.
 *
 * @author Craig R. McClanahan
 */
public class FilterDef implements Serializable
{

    private static final long serialVersionUID = 1L;


    // ------------------------------------------------------------- Properties


    /**
     * The description of this filter.
     */
    private String description = null;

    public String getDescription()
    {
        return this.description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }


    /**
     * The display name of this filter.
     */
    private String displayName = null;

    public String getDisplayName()
    {
        return this.displayName;
    }

    public void setDisplayName(String displayName)
    {
        this.displayName = displayName;
    }


    /**
     * The filter instance associated with this definition
     */
    private transient Filter filter = null;

    public Filter getFilter()
    {
        return filter;
    }

    public void setFilter(Filter filter)
    {
        this.filter = filter;
    }


    /**
     * The fully qualified name of the Java class that implements this filter.
     */
    private String filterClass = null;

    public String getFilterClass()
    {
        return this.filterClass;
    }

    public void setFilterClass(String filterClass)
    {
        this.filterClass = filterClass;
    }


    /**
     * The name of this filter, which must be unique among the filters
     * defined for a particular web application.
     */
    private String filterName = null;

    public String getFilterName()
    {
        return this.filterName;
    }

    public void setFilterName(String filterName)
    {
        if (filterName == null || filterName.equals(""))
        {
            throw new IllegalArgumentException("filterDef.invalidFilterName" + filterName);
        }
        this.filterName = filterName;
    }


    /**
     * The large icon associated with this filter.
     */
    private String largeIcon = null;

    public String getLargeIcon()
    {
        return this.largeIcon;
    }

    public void setLargeIcon(String largeIcon)
    {
        this.largeIcon = largeIcon;
    }


    /**
     * The set of initialization parameters for this filter, keyed by
     * parameter name.
     */
    private final Map<String, String> parameters = new HashMap<>();

    public Map<String, String> getParameterMap()
    {
        return this.parameters;
    }


    /**
     * The small icon associated with this filter.
     */
    private String smallIcon = null;

    public String getSmallIcon()
    {
        return this.smallIcon;
    }

    public void setSmallIcon(String smallIcon)
    {
        this.smallIcon = smallIcon;
    }

    private String asyncSupported = null;

    public String getAsyncSupported()
    {
        return asyncSupported;
    }

    public void setAsyncSupported(String asyncSupported)
    {
        this.asyncSupported = asyncSupported;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Add an initialization parameter to the set of parameters associated
     * with this filter.
     *
     * @param name  The initialization parameter name
     * @param value The initialization parameter value
     */
    public void addInitParameter(String name, String value)
    {

        if (parameters.containsKey(name))
        {
            // The spec does not define this but the TCK expects the first
            // definition to take precedence
            return;
        }
        parameters.put(name, value);

    }


    /**
     * Render a String representation of this object.
     */
    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder("FilterDef[");
        sb.append("filterName=");
        sb.append(this.filterName);
        sb.append(", filterClass=");
        sb.append(this.filterClass);
        sb.append("]");
        return sb.toString();
    }


}
