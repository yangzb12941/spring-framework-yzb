/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.servlet;

import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.EnvironmentCapable;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceEditor;
import org.springframework.core.io.ResourceLoader;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.support.ServletContextResourceLoader;
import org.springframework.web.context.support.StandardServletEnvironment;

/**
 * Simple extension of {@link javax.servlet.http.HttpServlet} which treats
 * its config parameters ({@code init-param} entries within the
 * {@code servlet} tag in {@code web.xml}) as bean properties.
 *
 * <p>A handy superclass for any type of servlet. Type conversion of config
 * parameters is automatic, with the corresponding setter method getting
 * invoked with the converted value. It is also possible for subclasses to
 * specify required properties. Parameters without matching bean property
 * setter will simply be ignored.
 *
 * <p>This servlet leaves request handling to subclasses, inheriting the default
 * behavior of HttpServlet ({@code doGet}, {@code doPost}, etc).
 *
 * <p>This generic servlet base class has no dependency on the Spring
 * {@link org.springframework.context.ApplicationContext} concept. Simple
 * servlets usually don't load their own context but rather access service
 * beans from the Spring root application context, accessible via the
 * filter's {@link #getServletContext() ServletContext} (see
 * {@link org.springframework.web.context.support.WebApplicationContextUtils}).
 *
 * <p>The {@link FrameworkServlet} class is a more specific servlet base
 * class which loads its own application context. FrameworkServlet serves
 * as direct base class of Spring's full-fledged {@link DispatcherServlet}.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @see #addRequiredProperty
 * @see #initServletBean
 * @see #doGet
 * @see #doPost
 */
@SuppressWarnings("serial")
public abstract class HttpServletBean extends HttpServlet implements EnvironmentCapable, EnvironmentAware {

	/** Logger available to subclasses. */
	protected final Log logger = LogFactory.getLog(getClass());

	@Nullable
	private ConfigurableEnvironment environment;

	private final Set<String> requiredProperties = new HashSet<>(4);


	/**
	 * Subclasses can invoke this method to specify that this property
	 * (which must match a JavaBean property they expose) is mandatory,
	 * and must be supplied as a config parameter. This should be called
	 * from the constructor of a subclass.
	 * <p>This method is only relevant in case of traditional initialization
	 * driven by a ServletConfig instance.
	 * @param property name of the required property
	 */
	protected final void addRequiredProperty(String property) {
		this.requiredProperties.add(property);
	}

	/**
	 * Set the {@code Environment} that this servlet runs in.
	 * <p>Any environment set here overrides the {@link StandardServletEnvironment}
	 * provided by default.
	 * @throws IllegalArgumentException if environment is not assignable to
	 * {@code ConfigurableEnvironment}
	 */
	@Override
	public void setEnvironment(Environment environment) {
		Assert.isInstanceOf(ConfigurableEnvironment.class, environment, "ConfigurableEnvironment required");
		this.environment = (ConfigurableEnvironment) environment;
	}

	/**
	 * Return the {@link Environment} associated with this servlet.
	 * <p>If none specified, a default environment will be initialized via
	 * {@link #createEnvironment()}.
	 */
	@Override
	public ConfigurableEnvironment getEnvironment() {
		if (this.environment == null) {
			this.environment = createEnvironment();
		}
		return this.environment;
	}

	/**
	 * Create and return a new {@link StandardServletEnvironment}.
	 * <p>Subclasses may override this in order to configure the environment or
	 * specialize the environment type returned.
	 */
	protected ConfigurableEnvironment createEnvironment() {
		return new StandardServletEnvironment();
	}

	/**
	 * Map config parameters onto bean properties of this servlet, and
	 * invoke subclass initialization.
	 *
	 * DispatcherServlet 的初始化过程主要是通过将当前的 servlet 类型实例转换为 BeanWrapper 类型实例，
	 * 以便使用 Spring 中提供的注入功能进行对应属性的注入。 这些属性如 contextAttribute、contextClass、
	 * nameSpace、contextConfigLocation 等，都可以在 web.xml 文件中以初始化参数的方式配置在 servlet 的声明中。
	 * DispatcherServlet 继承自 FrameworkServlet, FrameworkServlet 类上包含对应的同名属性，
	 * Spring 会保证这些参数被注入到对应的值中。 属性注入主要包含以下几个步骤。
	 * 1 .封装及验证初始化参数
	 * 2. 将当前 Servlet 实例转化成 BeanWrapper 实例
	 *  PropertyAccessorFactory.forBeanPropertyAccess 是 Spring 中提供的工具方法，主要用于将指
	 * 定实例转化为 Spring 中可以处理的 BeanWrapper 类型的实例。
	 * 3. 注册相对于 Resource 的属性编辑器
	 *  属性编辑器，我们在上文中已经介绍并且分析过其原理，这里使用属性编辑器的目的是在
	 *  对当前实例（ DispatcherServlet ）属性注入过程中一旦遇到 Resource 类型的属性就会使用 ResourceEditor 去解析。
	 * 4. 属性注入
	 *   BeanWrapper 为 Spring 中的方法 ， 支持 Spring 的自动注入。其实我们最常用的属性注入无
	 *  非是 contextAttribute、 contextClass 、 nameSpace 、 contextConfigLocation 等。
	 * 5.servletBean 的初始化
	 *  在 ContextLoaderListener 加载的时候已经创建了 WebApplicationContext 实例，而在这个函
	 *  数中最重要的就是对这个实例进行进一步的补充初始化。
	 *
	 * @throws ServletException if bean properties are invalid (or required
	 * properties are missing), or if subclass initialization fails.
	 */
	@Override
	public final void init() throws ServletException {

		// Set bean properties from init parameters.
		// 解析init-param 并封装至pvs中
		// 1 .封装及验证初始化参数
		// ServletConfigPropertyValues 除了封装属性外还有对属性验证的功能。
		PropertyValues pvs = new ServletConfigPropertyValues(getServletConfig(), this.requiredProperties);
		if (!pvs.isEmpty()) {
			try {
				// 将当前的这个 servlet 类转化为一个 BeanWrapper，从而能够以 Spring 的方式来对 init-param
				// 的值进行注入
				BeanWrapper bw = PropertyAccessorFactory.forBeanPropertyAccess(this);
				ResourceLoader resourceLoader = new ServletContextResourceLoader(getServletContext());
				// 注册自定义属性编辑器 ，一旦遇到 Resource 类型的属性将会使用 ResourceEditor 进行解析
				bw.registerCustomEditor(Resource.class, new ResourceEditor(resourceLoader, getEnvironment()));
				//空实现，留给子类覆盖
				initBeanWrapper(bw);
				//属性注入
				bw.setPropertyValues(pvs, true);
			}
			catch (BeansException ex) {
				if (logger.isErrorEnabled()) {
					logger.error("Failed to set bean properties on servlet '" + getServletName() + "'", ex);
				}
				throw ex;
			}
		}

		// Let subclasses do whatever initialization they like.
		// 留给子类扩展
		// 父类 FrameworkServlet 覆盖了 H即ServletBean 中的 initServletBean
		initServletBean();
	}

	/**
	 * Initialize the BeanWrapper for this HttpServletBean,
	 * possibly with custom editors.
	 * <p>This default implementation is empty.
	 * @param bw the BeanWrapper to initialize
	 * @throws BeansException if thrown by BeanWrapper methods
	 * @see org.springframework.beans.BeanWrapper#registerCustomEditor
	 */
	protected void initBeanWrapper(BeanWrapper bw) throws BeansException {
	}

	/**
	 * Subclasses may override this to perform custom initialization.
	 * All bean properties of this servlet will have been set before this
	 * method is invoked.
	 * <p>This default implementation is empty.
	 * @throws ServletException if subclass initialization fails
	 */
	protected void initServletBean() throws ServletException {
	}

	/**
	 * Overridden method that simply returns {@code null} when no
	 * ServletConfig set yet.
	 * @see #getServletConfig()
	 */
	@Override
	@Nullable
	public String getServletName() {
		return (getServletConfig() != null ? getServletConfig().getServletName() : null);
	}


	/**
	 * PropertyValues implementation created from ServletConfig init parameters.
	 */
	private static class ServletConfigPropertyValues extends MutablePropertyValues {

		/**
		 * Create new ServletConfigPropertyValues.
		 *
		 * 从代码中得知，封装属性主要是对初始化的参数进行封装，也就是 servlet 中配置的
		 * <init-param></init-param>中配置的封装。 当然，用户可以通过对 requiredProperties
		 * 参数的初始化来强制验证某些属性的必要性， 这样，在属性封装的过程中，一旦检测到 requiredProperties
		 * 中的属性没有 指定初始值，就会抛出异常。
		 *
		 * @param config the ServletConfig we'll use to take PropertyValues from
		 * @param requiredProperties set of property names we need, where
		 * we can't accept default values
		 * @throws ServletException if any required properties are missing
		 */
		public ServletConfigPropertyValues(ServletConfig config, Set<String> requiredProperties)
				throws ServletException {

			Set<String> missingProps = (!CollectionUtils.isEmpty(requiredProperties) ?
					new HashSet<>(requiredProperties) : null);

			Enumeration<String> paramNames = config.getInitParameterNames();
			while (paramNames.hasMoreElements()) {
				String property = paramNames.nextElement();
				Object value = config.getInitParameter(property);
				addPropertyValue(new PropertyValue(property, value));
				if (missingProps != null) {
					missingProps.remove(property);
				}
			}

			// Fail if we are still missing properties.
			if (!CollectionUtils.isEmpty(missingProps)) {
				throw new ServletException(
						"Initialization from ServletConfig for servlet '" + config.getServletName() +
						"' failed; the following required properties were missing: " +
						StringUtils.collectionToDelimitedString(missingProps, ", "));
			}
		}
	}

}
