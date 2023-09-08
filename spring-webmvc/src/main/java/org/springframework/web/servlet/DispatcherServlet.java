/*
 * Copyright 2002-2019 the original author or authors.
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import javax.servlet.DispatcherType;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.core.log.LogFormatUtils;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.lang.Nullable;
import org.springframework.ui.context.ThemeSource;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.async.WebAsyncManager;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.util.NestedServletException;
import org.springframework.web.util.WebUtils;

/**
 * Central dispatcher for HTTP request handlers/controllers, e.g. for web UI controllers
 * or HTTP-based remote service exporters. Dispatches to registered handlers for processing
 * a web request, providing convenient mapping and exception handling facilities.
 *
 * <p>This servlet is very flexible: It can be used with just about any workflow, with the
 * installation of the appropriate adapter classes. It offers the following functionality
 * that distinguishes it from other request-driven web MVC frameworks:
 *
 * <ul>
 * <li>It is based around a JavaBeans configuration mechanism.
 *
 * <li>It can use any {@link HandlerMapping} implementation - pre-built or provided as part
 * of an application - to control the routing of requests to handler objects. Default is
 * {@link org.springframework.web.servlet.handler.BeanNameUrlHandlerMapping} and
 * {@link org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping}.
 * HandlerMapping objects can be defined as beans in the servlet's application context,
 * implementing the HandlerMapping interface, overriding the default HandlerMapping if
 * present. HandlerMappings can be given any bean name (they are tested by type).
 *
 * <li>It can use any {@link HandlerAdapter}; this allows for using any handler interface.
 * Default adapters are {@link org.springframework.web.servlet.mvc.HttpRequestHandlerAdapter},
 * {@link org.springframework.web.servlet.mvc.SimpleControllerHandlerAdapter}, for Spring's
 * {@link org.springframework.web.HttpRequestHandler} and
 * {@link org.springframework.web.servlet.mvc.Controller} interfaces, respectively. A default
 * {@link org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter}
 * will be registered as well. HandlerAdapter objects can be added as beans in the
 * application context, overriding the default HandlerAdapters. Like HandlerMappings,
 * HandlerAdapters can be given any bean name (they are tested by type).
 *
 * <li>The dispatcher's exception resolution strategy can be specified via a
 * {@link HandlerExceptionResolver}, for example mapping certain exceptions to error pages.
 * Default are
 * {@link org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver},
 * {@link org.springframework.web.servlet.mvc.annotation.ResponseStatusExceptionResolver}, and
 * {@link org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver}.
 * These HandlerExceptionResolvers can be overridden through the application context.
 * HandlerExceptionResolver can be given any bean name (they are tested by type).
 *
 * <li>Its view resolution strategy can be specified via a {@link ViewResolver}
 * implementation, resolving symbolic view names into View objects. Default is
 * {@link org.springframework.web.servlet.view.InternalResourceViewResolver}.
 * ViewResolver objects can be added as beans in the application context, overriding the
 * default ViewResolver. ViewResolvers can be given any bean name (they are tested by type).
 *
 * <li>If a {@link View} or view name is not supplied by the user, then the configured
 * {@link RequestToViewNameTranslator} will translate the current request into a view name.
 * The corresponding bean name is "viewNameTranslator"; the default is
 * {@link org.springframework.web.servlet.view.DefaultRequestToViewNameTranslator}.
 *
 * <li>The dispatcher's strategy for resolving multipart requests is determined by a
 * {@link org.springframework.web.multipart.MultipartResolver} implementation.
 * Implementations for Apache Commons FileUpload and Servlet 3 are included; the typical
 * choice is {@link org.springframework.web.multipart.commons.CommonsMultipartResolver}.
 * The MultipartResolver bean name is "multipartResolver"; default is none.
 *
 * <li>Its locale resolution strategy is determined by a {@link LocaleResolver}.
 * Out-of-the-box implementations work via HTTP accept header, cookie, or session.
 * The LocaleResolver bean name is "localeResolver"; default is
 * {@link org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver}.
 *
 * <li>Its theme resolution strategy is determined by a {@link ThemeResolver}.
 * Implementations for a fixed theme and for cookie and session storage are included.
 * The ThemeResolver bean name is "themeResolver"; default is
 * {@link org.springframework.web.servlet.theme.FixedThemeResolver}.
 * </ul>
 *
 * <p><b>NOTE: The {@code @RequestMapping} annotation will only be processed if a
 * corresponding {@code HandlerMapping} (for type-level annotations) and/or
 * {@code HandlerAdapter} (for method-level annotations) is present in the dispatcher.</b>
 * This is the case by default. However, if you are defining custom {@code HandlerMappings}
 * or {@code HandlerAdapters}, then you need to make sure that a corresponding custom
 * {@code RequestMappingHandlerMapping} and/or {@code RequestMappingHandlerAdapter}
 * is defined as well - provided that you intend to use {@code @RequestMapping}.
 *
 * <p><b>A web application can define any number of DispatcherServlets.</b>
 * Each servlet will operate in its own namespace, loading its own application context
 * with mappings, handlers, etc. Only the root application context as loaded by
 * {@link org.springframework.web.context.ContextLoaderListener}, if any, will be shared.
 *
 * <p>As of Spring 3.1, {@code DispatcherServlet} may now be injected with a web
 * application context, rather than creating its own internally. This is useful in Servlet
 * 3.0+ environments, which support programmatic registration of servlet instances.
 * See the {@link #DispatcherServlet(WebApplicationContext)} javadoc for details.
 *
 * 在 Spring 中， ContextLoaderListener 只是辅助功能，用于创建 WebApplicationContext 类型
 * 实例 ， 而真正的逻辑实现其实是在 DispatcherServlet 中进行的， DispatcherServlet 是实现 servlet
 * 接口的实现类。 servlet 是一个 Java 编写的程序，此程序是基于 HTTP 协议的，在服务器端运行的（如 Tomcat ),
 * 是按照 servlet 规范编写的一个 Java 类。 主要是处理客户端的请求并将其结果发送到客户端。 servlet 的生命周期是由
 * servlet 的容器来控制的，它可以分为 3 个阶段：初始化、运行和销毁。
 * 1. 初始化阶段
 * A、servlet 容器加载 servlet 类，把 servlet 类的 .class 文件中的数据读到内存中。
 * B、servlet 容器创建一个 ServletConfig 对象。 ServletConfig 对象包含了 servlet 的初始化配配置信息。
 * C、servlet 容器创建一个 servlet 对象。 servlet 容器调用 servlet 对象的 init 方法进行初始化。
 *
 * 2. 运行阶段
 * 当 servlet 容器接收到一个请求时， servlet 容器会针对这个请求创建 servletRequest 和servletResponse 对象，
 * 然后调用 service 方法。 并把这两个参数传递给 service 方法。 service 方法通过 servletRequest 对象获得请求的信息。
 * 并处理该请求。 再通过 servletResponse 对象生成这个请求的响应结果。 然后销毁 servletRequest 和 servletResponse 对象。
 * 我们不管这个请求是 post 提交的还是 get 提交的，最终这个请求都会由 service 方法来处理。
 *
 * 3 . 销毁阶段
 * 当 Web  应用被终止时 ， servlet 容器会先调用 servlet对象的 destroy 方法，然后再销毁 servlet 对象，同时也会销毁与 servlet
 * 对象相关联的 servletConfig 对象。我们可以在 destroy 方法的实现中，释放 servlet 所占用的资源，如关闭数据库连接，关闭文件输入输出流等。
 * servlet 的框架是由两个 Java 包组成： javax.servlet 和 javax.servlet.http 。 在 javax.servlet 包中定义了所有的 servlet
 * 类都必须实现或扩展的通用接口和类，在 javax.servlet.http 包中定义了采用 HTTP 通信协议的 HttpServlet 类。 servlet 被设计成请求驱动，
 * servlet 的请求可能包含多个数据项，当 Web 容器接收到某个 servlet 请求时， servlet 把请求封装成一个 HttpServletRequest 对象，
 * 然后把对象传给 servlet 的对应的服务方法。 HTTP 的请求方式包括 delete 、 get 、 options 、 post 、 put 和 trace，
 * 在 HttpServlet 类中分别提供了相应的服务方法，它们是 doDelete()、 doGet() 、 doOptions() 、 doPost() 、 doPut()和 doTrace() 。
 *
 *
 * 1、建立 servlet
 * HttpServlet 类则会帮助我们根据方法类型的不同而将逻辑引入不同的函数。在子类中我们只需要重写对应的函数逻辑便可，
 * 如以上代码重写了 doGet 和 doPost 方法井将逻辑处理部分引导至 handleLogic 函数中，最后，又将页面跳转至 index.jsp。
 *
 * public class MyServlet extends HttpServlet{
 *     public void init(){
 *         System.out.println("this is init method");
 *     }
 *
 *     public void doGet(HttpServletRequest request , HttpServletResponse response){
 *         handleLogic(request,response);
 *     }
 *     public void doPost(HttpServletRequest request , HttpServletResponse response){
 *         handleLogic(request,response);
 *     }
 *
 *     private void handleLogic(HttpServletRequest request , HttpServletResponse response){
 *         System.out.println("handleLogic method");
 *         ServletContext sc = getServletContext();
 *         RequestDispatcher rd = null ;
 *
 *         rd = sc.getRequestDispatcher("/index.jsp"); //定向的页面
 *         try{
 *             rd.forward(request,response);
 *         }catch (Servlet Exception | IOException e){
 *            e.printStackTrace();
 *         }
 *     }
 * }
 * 2. 添加配置
 * 为了使 servlet 能够正常使用， 需要在 web.xml 文件中添加以下配置 ：
 * 配置后便可以根据对应的配置访问相应的路径了。
 * <servlet>
 *   <servlet-name>myservlet</servlet-name>
 *   <servlet-class>test.servlet.MyServlet</servlet-class>
 *   <load-on-startup>1</ load-on-startup>
 * </servlet>
 * <servlet-mapping>
 *     <servlet-name>myservlet</servlet-name>
 *     <url-pattern>*.htm</url-pattern>
 * </servlet-mapping>
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Chris Beams
 * @author Rossen Stoyanchev
 * @see org.springframework.web.HttpRequestHandler
 * @see org.springframework.web.servlet.mvc.Controller
 * @see org.springframework.web.context.ContextLoaderListener
 */
@SuppressWarnings("serial")
public class DispatcherServlet extends FrameworkServlet {

	/** Well-known name for the MultipartResolver object in the bean factory for this namespace. */
	public static final String MULTIPART_RESOLVER_BEAN_NAME = "multipartResolver";

	/** Well-known name for the LocaleResolver object in the bean factory for this namespace. */
	public static final String LOCALE_RESOLVER_BEAN_NAME = "localeResolver";

	/** Well-known name for the ThemeResolver object in the bean factory for this namespace. */
	public static final String THEME_RESOLVER_BEAN_NAME = "themeResolver";

	/**
	 * Well-known name for the HandlerMapping object in the bean factory for this namespace.
	 * Only used when "detectAllHandlerMappings" is turned off.
	 * @see #setDetectAllHandlerMappings
	 */
	public static final String HANDLER_MAPPING_BEAN_NAME = "handlerMapping";

	/**
	 * Well-known name for the HandlerAdapter object in the bean factory for this namespace.
	 * Only used when "detectAllHandlerAdapters" is turned off.
	 * @see #setDetectAllHandlerAdapters
	 */
	public static final String HANDLER_ADAPTER_BEAN_NAME = "handlerAdapter";

	/**
	 * Well-known name for the HandlerExceptionResolver object in the bean factory for this namespace.
	 * Only used when "detectAllHandlerExceptionResolvers" is turned off.
	 * @see #setDetectAllHandlerExceptionResolvers
	 */
	public static final String HANDLER_EXCEPTION_RESOLVER_BEAN_NAME = "handlerExceptionResolver";

	/**
	 * Well-known name for the RequestToViewNameTranslator object in the bean factory for this namespace.
	 */
	public static final String REQUEST_TO_VIEW_NAME_TRANSLATOR_BEAN_NAME = "viewNameTranslator";

	/**
	 * Well-known name for the ViewResolver object in the bean factory for this namespace.
	 * Only used when "detectAllViewResolvers" is turned off.
	 * @see #setDetectAllViewResolvers
	 */
	public static final String VIEW_RESOLVER_BEAN_NAME = "viewResolver";

	/**
	 * Well-known name for the FlashMapManager object in the bean factory for this namespace.
	 */
	public static final String FLASH_MAP_MANAGER_BEAN_NAME = "flashMapManager";

	/**
	 * Request attribute to hold the current web application context.
	 * Otherwise only the global web app context is obtainable by tags etc.
	 * @see org.springframework.web.servlet.support.RequestContextUtils#findWebApplicationContext
	 */
	public static final String WEB_APPLICATION_CONTEXT_ATTRIBUTE = DispatcherServlet.class.getName() + ".CONTEXT";

	/**
	 * Request attribute to hold the current LocaleResolver, retrievable by views.
	 * @see org.springframework.web.servlet.support.RequestContextUtils#getLocaleResolver
	 */
	public static final String LOCALE_RESOLVER_ATTRIBUTE = DispatcherServlet.class.getName() + ".LOCALE_RESOLVER";

	/**
	 * Request attribute to hold the current ThemeResolver, retrievable by views.
	 * @see org.springframework.web.servlet.support.RequestContextUtils#getThemeResolver
	 */
	public static final String THEME_RESOLVER_ATTRIBUTE = DispatcherServlet.class.getName() + ".THEME_RESOLVER";

	/**
	 * Request attribute to hold the current ThemeSource, retrievable by views.
	 * @see org.springframework.web.servlet.support.RequestContextUtils#getThemeSource
	 */
	public static final String THEME_SOURCE_ATTRIBUTE = DispatcherServlet.class.getName() + ".THEME_SOURCE";

	/**
	 * Name of request attribute that holds a read-only {@code Map<String,?>}
	 * with "input" flash attributes saved by a previous request, if any.
	 * @see org.springframework.web.servlet.support.RequestContextUtils#getInputFlashMap(HttpServletRequest)
	 */
	public static final String INPUT_FLASH_MAP_ATTRIBUTE = DispatcherServlet.class.getName() + ".INPUT_FLASH_MAP";

	/**
	 * Name of request attribute that holds the "output" {@link FlashMap} with
	 * attributes to save for a subsequent request.
	 * @see org.springframework.web.servlet.support.RequestContextUtils#getOutputFlashMap(HttpServletRequest)
	 */
	public static final String OUTPUT_FLASH_MAP_ATTRIBUTE = DispatcherServlet.class.getName() + ".OUTPUT_FLASH_MAP";

	/**
	 * Name of request attribute that holds the {@link FlashMapManager}.
	 * @see org.springframework.web.servlet.support.RequestContextUtils#getFlashMapManager(HttpServletRequest)
	 */
	public static final String FLASH_MAP_MANAGER_ATTRIBUTE = DispatcherServlet.class.getName() + ".FLASH_MAP_MANAGER";

	/**
	 * Name of request attribute that exposes an Exception resolved with a
	 * {@link HandlerExceptionResolver} but where no view was rendered
	 * (e.g. setting the status code).
	 */
	public static final String EXCEPTION_ATTRIBUTE = DispatcherServlet.class.getName() + ".EXCEPTION";

	/** Log category to use when no mapped handler is found for a request. */
	public static final String PAGE_NOT_FOUND_LOG_CATEGORY = "org.springframework.web.servlet.PageNotFound";

	/**
	 * Name of the class path resource (relative to the DispatcherServlet class)
	 * that defines DispatcherServlet's default strategy names.
	 */
	private static final String DEFAULT_STRATEGIES_PATH = "DispatcherServlet.properties";

	/**
	 * Common prefix that DispatcherServlet's default strategy attributes start with.
	 */
	private static final String DEFAULT_STRATEGIES_PREFIX = "org.springframework.web.servlet";

	/** Additional logger to use when no mapped handler is found for a request. */
	protected static final Log pageNotFoundLogger = LogFactory.getLog(PAGE_NOT_FOUND_LOG_CATEGORY);

	private static final Properties defaultStrategies;

	static {
		// Load default strategy implementations from properties file.
		// This is currently strictly internal and not meant to be customized
		// by application developers.
		// 从属性文件加载默认策略实现。这目前是严格的内部，不意味着应用程序开发人员可以自定义。
		// 在系统加载的时候， defaultStrategies 根据目前路径 DispatcherServlet.properties 来初始化本
		// 身，查看 DispatcherServlet.properties 中对应 HandlerAdapter 的属性：
		// org.Springframework.web.servlet.HandlerAdapter=org.Springframework.web.servlet .mvc.HttpRequestHandlerAdapter, \
		// org Springframework.web.servlet.mvc.SimpleControllerHandlerAdapter,\
		// org Springframework.web.servlet.mvc.annotation.AnnotationMethodHandlerAdapter

		// 作为总控制器的派遣器 servlet 通过处理器映射得到处理器后，会轮询处理器适配器模块，
		// 查找能够处理当前 HTTP 请求的处理器适配器的实现，处理器适配器模块根据处理器映射返回
		// 的处理器类型，例如简单的控制器类型 、注解控制器类型或者远程调用处理器类型，来选择某
		// 一个适当的处理器适配器的实现，从而适配当前的 HTTP请求。

		// 1、HTTP 请求处理器适配器(HttpRequestHandlerAdapter)
		//   HTTP 请求处理器适配器仅仅支持对 HTTP 请求处理的适配。它简单地将请求对象和响应对象
		//   传递给 HTTP 请求处理器的实现，它并不需要返回值，它主要应用在基于 HTTP的远程调用的实现上。

		// 2、简单控制器处理器适配器(SimpleControllerHandlerAdapter)
		//   这个实现类将 HTTP 请求适配到一个控制器的实现进行处理。这里控制器的现是一个简
		//   单的控制器接口的实现。简单控制器处理器适配器被设计成一个框架类的实现 ，不需要被改写，
		//   客户化的业务逻辑通常是在控制器接口的实现类中现的。

		// 3、注解方法处理器适配器(AnnotationMethodHandlerAdapter)
		//   这个类的现是注解的实现，它需要结合注解方法映射和注解方法处理器协同工作。
		//   它通过解析声明在注解控制器的请求映射信息来解析相应的处理器方法来处理当前的 HTTP请求。
		//   在处理的过程中，它通过反射来发现探测处理器方法的参数，调用处理器方法，并且映射
		//   返回值到模型和控制器对象，最后返回模型和控制器对象给作为主控制器的派遣器 Servlet。
		try {
			ClassPathResource resource = new ClassPathResource(DEFAULT_STRATEGIES_PATH, DispatcherServlet.class);
			defaultStrategies = PropertiesLoaderUtils.loadProperties(resource);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Could not load '" + DEFAULT_STRATEGIES_PATH + "': " + ex.getMessage());
		}
	}

	/** Detect all HandlerMappings or just expect "handlerMapping" bean?. */
	private boolean detectAllHandlerMappings = true;

	/** Detect all HandlerAdapters or just expect "handlerAdapter" bean?. */
	private boolean detectAllHandlerAdapters = true;

	/** Detect all HandlerExceptionResolvers or just expect "handlerExceptionResolver" bean?. */
	private boolean detectAllHandlerExceptionResolvers = true;

	/** Detect all ViewResolvers or just expect "viewResolver" bean?. */
	private boolean detectAllViewResolvers = true;

	/** Throw a NoHandlerFoundException if no Handler was found to process this request? *.*/
	private boolean throwExceptionIfNoHandlerFound = false;

	/** Perform cleanup of request attributes after include request?. */
	private boolean cleanupAfterInclude = true;

	/** MultipartResolver used by this servlet. */
	@Nullable
	private MultipartResolver multipartResolver;

	/** LocaleResolver used by this servlet. */
	@Nullable
	private LocaleResolver localeResolver;

	/** ThemeResolver used by this servlet. */
	@Nullable
	private ThemeResolver themeResolver;

	/** List of HandlerMappings used by this servlet. */
	@Nullable
	private List<HandlerMapping> handlerMappings;

	/** List of HandlerAdapters used by this servlet. */
	@Nullable
	private List<HandlerAdapter> handlerAdapters;

	/** List of HandlerExceptionResolvers used by this servlet. */
	@Nullable
	private List<HandlerExceptionResolver> handlerExceptionResolvers;

	/** RequestToViewNameTranslator used by this servlet. */
	@Nullable
	private RequestToViewNameTranslator viewNameTranslator;

	/** FlashMapManager used by this servlet. */
	@Nullable
	private FlashMapManager flashMapManager;

	/** List of ViewResolvers used by this servlet. */
	@Nullable
	private List<ViewResolver> viewResolvers;


	/**
	 * Create a new {@code DispatcherServlet} that will create its own internal web
	 * application context based on defaults and values provided through servlet
	 * init-params. Typically used in Servlet 2.5 or earlier environments, where the only
	 * option for servlet registration is through {@code web.xml} which requires the use
	 * of a no-arg constructor.
	 * <p>Calling {@link #setContextConfigLocation} (init-param 'contextConfigLocation')
	 * will dictate which XML files will be loaded by the
	 * {@linkplain #DEFAULT_CONTEXT_CLASS default XmlWebApplicationContext}
	 * <p>Calling {@link #setContextClass} (init-param 'contextClass') overrides the
	 * default {@code XmlWebApplicationContext} and allows for specifying an alternative class,
	 * such as {@code AnnotationConfigWebApplicationContext}.
	 * <p>Calling {@link #setContextInitializerClasses} (init-param 'contextInitializerClasses')
	 * indicates which {@code ApplicationContextInitializer} classes should be used to
	 * further configure the internal application context prior to refresh().
	 * @see #DispatcherServlet(WebApplicationContext)
	 */
	public DispatcherServlet() {
		super();
		setDispatchOptionsRequest(true);
	}

	/**
	 * Create a new {@code DispatcherServlet} with the given web application context. This
	 * constructor is useful in Servlet 3.0+ environments where instance-based registration
	 * of servlets is possible through the {@link ServletContext#addServlet} API.
	 * <p>Using this constructor indicates that the following properties / init-params
	 * will be ignored:
	 * <ul>
	 * <li>{@link #setContextClass(Class)} / 'contextClass'</li>
	 * <li>{@link #setContextConfigLocation(String)} / 'contextConfigLocation'</li>
	 * <li>{@link #setContextAttribute(String)} / 'contextAttribute'</li>
	 * <li>{@link #setNamespace(String)} / 'namespace'</li>
	 * </ul>
	 * <p>The given web application context may or may not yet be {@linkplain
	 * ConfigurableApplicationContext#refresh() refreshed}. If it has <strong>not</strong>
	 * already been refreshed (the recommended approach), then the following will occur:
	 * <ul>
	 * <li>If the given context does not already have a {@linkplain
	 * ConfigurableApplicationContext#setParent parent}, the root application context
	 * will be set as the parent.</li>
	 * <li>If the given context has not already been assigned an {@linkplain
	 * ConfigurableApplicationContext#setId id}, one will be assigned to it</li>
	 * <li>{@code ServletContext} and {@code ServletConfig} objects will be delegated to
	 * the application context</li>
	 * <li>{@link #postProcessWebApplicationContext} will be called</li>
	 * <li>Any {@code ApplicationContextInitializer}s specified through the
	 * "contextInitializerClasses" init-param or through the {@link
	 * #setContextInitializers} property will be applied.</li>
	 * <li>{@link ConfigurableApplicationContext#refresh refresh()} will be called if the
	 * context implements {@link ConfigurableApplicationContext}</li>
	 * </ul>
	 * If the context has already been refreshed, none of the above will occur, under the
	 * assumption that the user has performed these actions (or not) per their specific
	 * needs.
	 * <p>See {@link org.springframework.web.WebApplicationInitializer} for usage examples.
	 * @param webApplicationContext the context to use
	 * @see #initWebApplicationContext
	 * @see #configureAndRefreshWebApplicationContext
	 * @see org.springframework.web.WebApplicationInitializer
	 */
	public DispatcherServlet(WebApplicationContext webApplicationContext) {
		super(webApplicationContext);
		setDispatchOptionsRequest(true);
	}


	/**
	 * Set whether to detect all HandlerMapping beans in this servlet's context. Otherwise,
	 * just a single bean with name "handlerMapping" will be expected.
	 * <p>Default is "true". Turn this off if you want this servlet to use a single
	 * HandlerMapping, despite multiple HandlerMapping beans being defined in the context.
	 */
	public void setDetectAllHandlerMappings(boolean detectAllHandlerMappings) {
		this.detectAllHandlerMappings = detectAllHandlerMappings;
	}

	/**
	 * Set whether to detect all HandlerAdapter beans in this servlet's context. Otherwise,
	 * just a single bean with name "handlerAdapter" will be expected.
	 * <p>Default is "true". Turn this off if you want this servlet to use a single
	 * HandlerAdapter, despite multiple HandlerAdapter beans being defined in the context.
	 */
	public void setDetectAllHandlerAdapters(boolean detectAllHandlerAdapters) {
		this.detectAllHandlerAdapters = detectAllHandlerAdapters;
	}

	/**
	 * Set whether to detect all HandlerExceptionResolver beans in this servlet's context. Otherwise,
	 * just a single bean with name "handlerExceptionResolver" will be expected.
	 * <p>Default is "true". Turn this off if you want this servlet to use a single
	 * HandlerExceptionResolver, despite multiple HandlerExceptionResolver beans being defined in the context.
	 */
	public void setDetectAllHandlerExceptionResolvers(boolean detectAllHandlerExceptionResolvers) {
		this.detectAllHandlerExceptionResolvers = detectAllHandlerExceptionResolvers;
	}

	/**
	 * Set whether to detect all ViewResolver beans in this servlet's context. Otherwise,
	 * just a single bean with name "viewResolver" will be expected.
	 * <p>Default is "true". Turn this off if you want this servlet to use a single
	 * ViewResolver, despite multiple ViewResolver beans being defined in the context.
	 */
	public void setDetectAllViewResolvers(boolean detectAllViewResolvers) {
		this.detectAllViewResolvers = detectAllViewResolvers;
	}

	/**
	 * Set whether to throw a NoHandlerFoundException when no Handler was found for this request.
	 * This exception can then be caught with a HandlerExceptionResolver or an
	 * {@code @ExceptionHandler} controller method.
	 * <p>Note that if {@link org.springframework.web.servlet.resource.DefaultServletHttpRequestHandler}
	 * is used, then requests will always be forwarded to the default servlet and a
	 * NoHandlerFoundException would never be thrown in that case.
	 * <p>Default is "false", meaning the DispatcherServlet sends a NOT_FOUND error through the
	 * Servlet response.
	 * @since 4.0
	 */
	public void setThrowExceptionIfNoHandlerFound(boolean throwExceptionIfNoHandlerFound) {
		this.throwExceptionIfNoHandlerFound = throwExceptionIfNoHandlerFound;
	}

	/**
	 * Set whether to perform cleanup of request attributes after an include request, that is,
	 * whether to reset the original state of all request attributes after the DispatcherServlet
	 * has processed within an include request. Otherwise, just the DispatcherServlet's own
	 * request attributes will be reset, but not model attributes for JSPs or special attributes
	 * set by views (for example, JSTL's).
	 * <p>Default is "true", which is strongly recommended. Views should not rely on request attributes
	 * having been set by (dynamic) includes. This allows JSP views rendered by an included controller
	 * to use any model attributes, even with the same names as in the main JSP, without causing side
	 * effects. Only turn this off for special needs, for example to deliberately allow main JSPs to
	 * access attributes from JSP views rendered by an included controller.
	 */
	public void setCleanupAfterInclude(boolean cleanupAfterInclude) {
		this.cleanupAfterInclude = cleanupAfterInclude;
	}


	/**
	 * This implementation calls {@link #initStrategies}.
	 * onRefresh 是 FrameworkServlet 类中提供的模板方法，在其子类 DispatcherServlet
	 * 中进行了重写，主要用于刷新 Spring 在 Web 功能实现中所必须使用的全局变量。
	 */
	@Override
	protected void onRefresh(ApplicationContext context) {
		initStrategies(context);
	}

	/**
	 * Initialize the strategy objects that this servlet uses.
	 * <p>May be overridden in subclasses in order to initialize further strategy objects.
	 */
	protected void initStrategies(ApplicationContext context) {
		// (1 ）初始化MultipartResolver
		initMultipartResolver(context);
		initLocaleResolver(context);
		initThemeResolver(context);
		initHandlerMappings(context);
		initHandlerAdapters(context);
		initHandlerExceptionResolvers(context);
		initRequestToViewNameTranslator(context);
		initViewResolvers(context);
		initFlashMapManager(context);
	}

	/**
	 * Initialize the MultipartResolver used by this class.
	 * <p>If no bean is defined with the given name in the BeanFactory for this namespace,
	 * no multipart handling is provided.
	 *
	 * 在 Spring 中， MultipartResolver 主要用来处理文件上传。 默认情况下， Spring 是没有 multipart
	 * 处理的，因为一些开发者想要自己处理它们。 如果想使用 Spring 的 multipart ， 则需要在 Web 应用的上下文中添加
	 * multipart 解析器。 这样，每个请求就会被检查是否包含 multipart。 然而， 如果请求中包含 multipart，那么上下文中定义的
	 * MultipartResolver 就会解析它，这样请求中的 multipart 属性就会像其他属性一样被处理。 常用配置如下 ：
	 * <bean id＝ ”multipartResolver ” class＝ ”org.Springframework.web.multipart.commons.CommonsMultipartResolver">
	 *     <! -- 该属性用来配置可上传文件的最大字节数-->
	 *     <property name= "maximumFileSize">
	 *       <value>1OOOOO</value>
	 *     </property>
	 * </bean>
	 * CommonsMultipartResolver 还提供了其他功能用于帮助用户完成上传功能，有兴趣
	 * 的读者可以进一步查看。 那么 MultipartResolver 就是在 initMultipartResolver
	 * 中被加入到 DispatcherServlet 中的。
	 *
	 * 因为之前的步骤已经完成了 Spring 中配置文件的解析，所以在这里只要在配置文件注册过
	 * 都可以通过 ApplicationContext 提供的 getBean 方法来直接获取对应 bean ，进而
	 * 初始化 MultipartResolver 中的 multipartResolver 变量。
	 */
	private void initMultipartResolver(ApplicationContext context) {
		try {
			this.multipartResolver = context.getBean(MULTIPART_RESOLVER_BEAN_NAME, MultipartResolver.class);
			if (logger.isTraceEnabled()) {
				logger.trace("Detected " + this.multipartResolver);
			}
			else if (logger.isDebugEnabled()) {
				logger.debug("Detected " + this.multipartResolver.getClass().getSimpleName());
			}
		}
		catch (NoSuchBeanDefinitionException ex) {
			// Default is no multipart resolver.
			this.multipartResolver = null;
			if (logger.isTraceEnabled()) {
				logger.trace("No MultipartResolver '" + MULTIPART_RESOLVER_BEAN_NAME + "' declared");
			}
		}
	}

	/**
	 * Initialize the LocaleResolver used by this class.
	 * <p>If no bean is defined with the given name in the BeanFactory for this namespace,
	 * we default to AcceptHeaderLocaleResolver.
	 * 在 Spring 的国际化配置中一共有 3 种使用方式。
	 * 1、基于 URL 参数的配置。
	 *    通过 URL 参数来控制国际化， 比如你在页面上加一句<a href=”?locale=zh_CN”></a>简体中文
	 * 来控制项目中使用的国际化参数。 而提供这个功能的就是 AcceptHeaderLocaleResolver，默认的参数名为 locale，
	 * 注意大小写。 里面放的就是你的提交参数，比如 en_US, zh_CN 之类的 ， 具体配置如下；
	 * <bean id=”localeResolver” class=”org.Springframework.web.servlet.il8n.AcceptHeaderLocaleResolver”/>
	 * 2、基于 session 的配置。
	 *  它通过检验用户会话中预置的属性来解析区域。 最常用的是根据用户本次会话过程中的语言设定决定语言种类
	 *  （例如，用户登录时选择语言种类，则此次登录周期内统一使用此语言设定），如果该会话属性不存在，它会根据 accept-language HTTP 头部确定默认区域。
	 *  <bean id=”localeResolver” class=”org.Springframework.web.servlet.il8n.SessionLocaleResolver”/>
	 * 3、基于 cookie 的国际化配置。
	 *  CookieLocaleResolver 用于通过浏览器的 cookie 设置取得 Locale 对象。 这种策略在应用程
	 *  序不支持会话或者状态必须保存在客户端时有用，配置如下 ：
	 *  <bean id=”localeResolver" class＝”org.Springframework.web.servlet.i18n.CookieLocaleResolver”/>
	 *
	 *  这 3 种方式都可以解决国际化的问题。但是，对于 LocalResolver 的使用基础是在 DispatcherServlet 中的初始化。
	 *  提取配置文件中设置的 LocaleResolver 来初始化 DispatcherServlet 中的 localeResolver属性。
	 */
	private void initLocaleResolver(ApplicationContext context) {
		try {
			this.localeResolver = context.getBean(LOCALE_RESOLVER_BEAN_NAME, LocaleResolver.class);
			if (logger.isTraceEnabled()) {
				logger.trace("Detected " + this.localeResolver);
			}
			else if (logger.isDebugEnabled()) {
				logger.debug("Detected " + this.localeResolver.getClass().getSimpleName());
			}
		}
		catch (NoSuchBeanDefinitionException ex) {
			// We need to use the default.
			this.localeResolver = getDefaultStrategy(context, LocaleResolver.class);
			if (logger.isTraceEnabled()) {
				logger.trace("No LocaleResolver '" + LOCALE_RESOLVER_BEAN_NAME +
						"': using default [" + this.localeResolver.getClass().getSimpleName() + "]");
			}
		}
	}

	/**
	 * Initialize the ThemeResolver used by this class.
	 * <p>If no bean is defined with the given name in the BeanFactory for this namespace,
	 * we default to a FixedThemeResolver.
	 *
	 * 初始化 ThemeResolver。
	 * 在 Web 开发中经常会遇到通过主题 Theme 来控制网页风格，这将进一步改善用户体验。
	 * 简单地说， 一个主题就是一组静态资源（ 比如样式表和图片 ） ， 它们可以影响应用程序的视觉效果。
	 * Spring 中的主题功能和国际化功能非常类似。 Spring 主题功能的构成主要包括如下内容。
	 * 1、主题资源 。
	 * org.Springframework.ui.context.ThemeSource 是 Spring 中主题资源的接口 ， Spring 的主题需
	 * 要通过 ThemeSource 接口来实现存放主题信息的资源。
	 * org.Springframework.ui.context.support.ResourceBundleThemeSource 是 ThemeSource 接口默
	 * 认实现类 （也就是通过 ResourceBundle 资源的方式定义主题）， 在 Spring 中的配置如下 ：
	 * <bean id= ”themeSource” class＝ ”org.Springframework.ui.context.support.ResourceBundleThemeSource” >
	 *     <property name= ” basenamePrefix " value= ”com.test.”></property>
	 * </bean>
	 * 默认状态下是在类路径根目录下查找相应的资源文件，也可以通过 basenamePrefix 来制定。
	 * 这样， DispatcherServlet 就会在 com.test 包下查找资源文件。
	 *
	 * 2、主题解析器。
	 * ThemeSource 定义了一些主题资源，那么不同的用户使用什么主题资源由谁定义呢？
	 * org.Springframework.web.servlet.ThemeResolver 是主题解析器的接口 ，
	 * 主题解析的工作便由它的 子类来完成。 对于主题解析器的子类主要有 3 个比较常用的实现。
	 * 以主题文件 summer.properties 为例。
	 * ① FixedThemeResolver 用于选择一个固定的主题。
	 *  <bean id＝”themeResolver" class＝”org.Springframework.web.servlet.theme.FixedThemeResolver”>
	 *      <property name= ”defaultThemeName” value= "summer” />
	 *  </bean>
	 * 以上配置的作用是设置主题文件为 summer.properties ，在整个项目内固定不变。
	 *
	 * ② CookieThemeResolver 用于实现用户所选的主题， 以 cookie 的形式存放在客户端的机器上，配置如下：
	 * <bean id＝”themeResolver” class＝”org.Springframework.web.servlet.theme.CookieThemeResolver”>
	 *   <property name=”defaultThemeName” value= ”summer"/>
	 * </bean>
	 *
	 * ③ SessionThemeResolver 用于主题保存在用户的 HTTP Session 中。
	 * <bean id＝”themeResolver” class＝”org.Springframework.web.servlet.theme.SessionThemeResolver” >
	 *     <property name= ” defaultThemeName ” value=” summer ” />
	 * </bean>
	 * 以上配置用于设置主题名称，并且将该名称保存在用户的 HttpSession 中。
	 *
	 * ④ AbstractThemeResolver 是一个抽象类被 SessionThemeResolver 和 FixedThemeResolver 继承，用户也可以继承它来自定义主题解析器。
	 * A、拦截器。
	 *  如果需要根据用户请求来改变主题， 那么 Spring 提供了一个已经实现的拦截器 ThemeChangeInterceptor 拦截器了，配置如下：
	 *  <bean id=”themeChangeInterceptor” class=” org.Springframework.web.servlet.theme.ThemeChangeInterceptor">
	 *      <property name =”paramName” value=”themeName"></property>
	 *  </bean> 其中设置用户请求参数名为 themeName，即 URL 为？themeName＝具体的主题名称。
	 *  此外，还需要在 handlerMapping 中配置拦截器。 当然需要在 HandleMapping 中添加拦截器。
	 *  <property name=”interceptors” >
	 *    <list>
	 *      <ref local= ”themeChangeInterceptor"/>
	 *    </list>
	 *   </property>
	 * 了解了主题文件的简单使用方式后，再来查看解析器的初始化工作，与其他变量的初始化工作相同， 主题文件解析器的初始化工作并没有任何需要特别说明的地方。
	 */
	private void initThemeResolver(ApplicationContext context) {
		try {
			this.themeResolver = context.getBean(THEME_RESOLVER_BEAN_NAME, ThemeResolver.class);
			if (logger.isTraceEnabled()) {
				logger.trace("Detected " + this.themeResolver);
			}
			else if (logger.isDebugEnabled()) {
				logger.debug("Detected " + this.themeResolver.getClass().getSimpleName());
			}
		}
		catch (NoSuchBeanDefinitionException ex) {
			// We need to use the default.
			this.themeResolver = getDefaultStrategy(context, ThemeResolver.class);
			if (logger.isTraceEnabled()) {
				logger.trace("No ThemeResolver '" + THEME_RESOLVER_BEAN_NAME +
						"': using default [" + this.themeResolver.getClass().getSimpleName() + "]");
			}
		}
	}

	/**
	 * Initialize the HandlerMappings used by this class.
	 * <p>If no HandlerMapping beans are defined in the BeanFactory for this namespace,
	 * we default to BeanNameUrlHandlerMapping.
	 *
	 * 4 初始化 HandlerMappings。
	 * 当客户端发出 Request 时 DispatcherServlet 会将 Request 提交给 HandlerMapping ， 然后
	 * HandlerMapping 根据 WebApplicationContext 的配置来回传给 DispatcherServlet 相应的 Controller。
	 * 在基于 SpringMVC 的 Web 应用程序中，我们可以为 DispatcherServlet 提供多个 HandlerMapping 供其使用。
	 * DispatcherServlet 在选用 HandlerMapping 的过程中，将根据我们所指定的一系列 HandlerMapping 的优先级进行排序，
	 * 然后优先使用优先级在前的 HandlerMapping。 如果当前的 HandlerMapping能够返回可用的 Handler, DispatcherServlet则使用
	 * 当前返回的 Handler 进行 Web 请求的处理，而不再继续询问其他的 HandierMapping。
	 * 否则， DispatcherServlet 将继续按照各个 HandlerMapping 的优先级进行询问，直到获取一个可用的 Handler 为止。 初始化配 置如下：
	 *
	 * 默认情况下， SpringMVC 将加载当前系统中所有实现了 HandlerMapping 接口的 bean。 如果只期望 SpringMVC
	 * 加载指定的 handlerMapping 时，可以修改 web.xml 中的 DispatcherServlet 的初始参数 ， 将 detectAllHandlerMappings
	 * 的值设置为 false :
	 * <init-param>
	 *     <param-name>detectAllHandlerMappings</param-name>
	 *     <param-value>false</param-value>
	 * </init-param>
	 * 此时， SpringMVC 将查找名为“handlerMapping”的 bean ，并作为当前系统中唯一的handlerMapping。
	 * 如果没有定义 handlerMapping 的话，则 SpringMVC 将按照 org.Springframework.web.servlet.DispatcherServlet
	 * 所在目录下的DispatcherServlet.properties 中所定义的org.Springframework.web.servlet.HandlerMapping
	 * 的内容来加载默认的 handlerMapping （用户没有自定义 Strategies 的情况下）。
	 *
	 */
	private void initHandlerMappings(ApplicationContext context) {
		this.handlerMappings = null;

		if (this.detectAllHandlerMappings) {
			// Find all HandlerMappings in the ApplicationContext, including ancestor contexts.
			Map<String, HandlerMapping> matchingBeans =
					BeanFactoryUtils.beansOfTypeIncludingAncestors(context, HandlerMapping.class, true, false);
			if (!matchingBeans.isEmpty()) {
				this.handlerMappings = new ArrayList<>(matchingBeans.values());
				// We keep HandlerMappings in sorted order.
				AnnotationAwareOrderComparator.sort(this.handlerMappings);
			}
		}
		else {
			try {
				HandlerMapping hm = context.getBean(HANDLER_MAPPING_BEAN_NAME, HandlerMapping.class);
				this.handlerMappings = Collections.singletonList(hm);
			}
			catch (NoSuchBeanDefinitionException ex) {
				// Ignore, we'll add a default HandlerMapping later.
			}
		}

		// Ensure we have at least one HandlerMapping, by registering
		// a default HandlerMapping if no other mappings are found.
		if (this.handlerMappings == null) {
			this.handlerMappings = getDefaultStrategies(context, HandlerMapping.class);
			if (logger.isTraceEnabled()) {
				logger.trace("No HandlerMappings declared for servlet '" + getServletName() +
						"': using default strategies from DispatcherServlet.properties");
			}
		}
	}

	/**
	 * Initialize the HandlerAdapters used by this class.
	 * <p>If no HandlerAdapter beans are defined in the BeanFactory for this namespace,
	 * we default to SimpleControllerHandlerAdapter.
	 *
	 * 5.初始化 HandlerAdapters。
	 * 从名字也能联想到这是一个典型的适配器模式的使用，在计算机编程中，
	 * 适配器模式将一个类的接口适配成用户所期待的。 使用适配器，可以使接口不兼容而无法在一起工作的类协同工作，
	 * 做法是将类自己的接口包裹在一个己存在的类中。 那么在处理 handler 时为什么会使用适配器模式呢？
	 * 回答这个问题我们首先要分析它的初始化逻辑。
	 *
	 * 同样在初始化的过程中涉及了一个变量 detectAllHandlerAdapters ,
	 * detectAllHandlerAdapters 作用和 detectAllHandlerMappings 类似，只不过作用对象为 handlerAdapter。
	 * 亦可通过如下配置来强制系统只加载 bean name 为 "handlerAdapter" handlerAdapter。
	 * <init-param>
	 *     <param-name>detectAllHandlerAdapters</param-name>
	 *     <param-value>false</param-value>
	 * </init-param>
	 *
	 * 如果无法找到对应的 bean，那么系统会尝试加载默认的适配器。
	 */
	private void initHandlerAdapters(ApplicationContext context) {
		this.handlerAdapters = null;

		if (this.detectAllHandlerAdapters) {
			// Find all HandlerAdapters in the ApplicationContext, including ancestor contexts.
			// 在ApplicationContext中查找所有HandlerAdapter，包括祖先上下文。
			Map<String, HandlerAdapter> matchingBeans =
					BeanFactoryUtils.beansOfTypeIncludingAncestors(context, HandlerAdapter.class, true, false);
			if (!matchingBeans.isEmpty()) {
				this.handlerAdapters = new ArrayList<>(matchingBeans.values());
				// We keep HandlerAdapters in sorted order.
				AnnotationAwareOrderComparator.sort(this.handlerAdapters);
			}
		}
		else {
			try {
				HandlerAdapter ha = context.getBean(HANDLER_ADAPTER_BEAN_NAME, HandlerAdapter.class);
				this.handlerAdapters = Collections.singletonList(ha);
			}
			catch (NoSuchBeanDefinitionException ex) {
				// Ignore, we'll add a default HandlerAdapter later.
			}
		}

		// Ensure we have at least some HandlerAdapters, by registering
		// default HandlerAdapters if no other adapters are found.
		// 如果找不到其他适配器，请注册默认的HandlerAdapters，确保我们至少有一些HandlerAdapter。
		// 如果无法找到对应的 bean，那么系统会尝试加载默认的适配器。
		if (this.handlerAdapters == null) {
			// 在 getDefaultStrategies 函数中， Spring 会尝试从 defaultStrategies 中加载对应的
			// HandlerAdapter 的属性，那么 defaultStrategies 是如何初始化的呢？
			this.handlerAdapters = getDefaultStrategies(context, HandlerAdapter.class);
			if (logger.isTraceEnabled()) {
				logger.trace("No HandlerAdapters declared for servlet '" + getServletName() +
						"': using default strategies from DispatcherServlet.properties");
			}
		}
	}

	/**
	 * Initialize the HandlerExceptionResolver used by this class.
	 * <p>If no bean is defined with the given name in the BeanFactory for this namespace,
	 * we default to no exception resolver.
	 *
	 * 基于 HandlerExceptionResolver 接口的异常处理，使用这种方式只需要实现 resolveException 方法，
	 * 该方法返回一个 ModelAndView 对象，在方法内部对异常的类型进行判断，然后尝试生成对应的 ModelAndView 对象，
	 * 如果该方法返回了 null ，则 Spring 会继续寻找其他的实现了 HandlerExceptionResolver 接口的 bean。
	 * 换句话说，Spring 会搜索所有注册在其环境中的实现了 HandlerExceptionResolver 接口的 bean，逐个执行，
	 * 直到返回了一个 ModelAndView 对象。
	 *
	 * public class ExceptionHandler implements HandlerExceptionResolvers{
	 *     private static final Log logs = LogFactory.getLog(ExceptionHandler.class);
	 *
	 *     @Override
	 *     public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response , Object obj, Exception exception){
	 *        request.setAttribute("exception",exception.toString());
	 *        request.setAttribute("exceptionStack",exception);
	 *        logs.error(exception.toString(),exception);
	 *        return new ModelAndView("error/exception");
	 *     }
	 * }
	 *
	 * 这个类必须声明到 Spring 中去，让 Spring 管理它，在 Spring 的配置文件 applicationContext.xml
	 * 中增加以下内容 ：
	 * <bean id= ”exceptionHandler” class=”com.test.exception.MyExceptionHandler”/>
	 */
	private void initHandlerExceptionResolvers(ApplicationContext context) {
		this.handlerExceptionResolvers = null;

		if (this.detectAllHandlerExceptionResolvers) {
			// Find all HandlerExceptionResolvers in the ApplicationContext, including ancestor contexts.
			// 在ApplicationContext中查找所有HandlerExceptionResolver，包括祖先上下文。
			Map<String, HandlerExceptionResolver> matchingBeans = BeanFactoryUtils
					.beansOfTypeIncludingAncestors(context, HandlerExceptionResolver.class, true, false);
			if (!matchingBeans.isEmpty()) {
				this.handlerExceptionResolvers = new ArrayList<>(matchingBeans.values());
				// We keep HandlerExceptionResolvers in sorted order.
				// 我们将 HandlerExceptionResolver 按排序。
				AnnotationAwareOrderComparator.sort(this.handlerExceptionResolvers);
			}
		}
		else {
			try {
				HandlerExceptionResolver her =
						context.getBean(HANDLER_EXCEPTION_RESOLVER_BEAN_NAME, HandlerExceptionResolver.class);
				this.handlerExceptionResolvers = Collections.singletonList(her);
			}
			catch (NoSuchBeanDefinitionException ex) {
				// Ignore, no HandlerExceptionResolver is fine too.
				// 忽略，没有HandlerExceptionResolver也可以。
			}
		}

		// Ensure we have at least some HandlerExceptionResolvers, by registering
		// default HandlerExceptionResolvers if no other resolvers are found.
		// 如果找不到其他解析程序，请注册默认的HandlerExceptionResolver，确保我们至少有一些HandlerException解析程序。
		if (this.handlerExceptionResolvers == null) {
			this.handlerExceptionResolvers = getDefaultStrategies(context, HandlerExceptionResolver.class);
			if (logger.isTraceEnabled()) {
				logger.trace("No HandlerExceptionResolvers declared in servlet '" + getServletName() +
						"': using default strategies from DispatcherServlet.properties");
			}
		}
	}

	/**
	 * Initialize the RequestToViewNameTranslator used by this servlet instance.
	 * <p>If no implementation is configured then we default to DefaultRequestToViewNameTranslator.
	 *
	 * 当 Controller 处理器方法没有返回一个 View 对象或逻辑视图名称，并且在该方法中没有直
	 * 接往 response 的输出流里面写数据的时候， Spring 就会采用约定好的方式提供一个逻辑视图名称。
	 * 这个逻辑视图名称是通过 Spring 定义的 org.Springframework.web.servlet.RequestToViewNameTranslator
	 * 接口的 getViewName 方法来实现的，我们可以实现自己的 RequestToViewNameTranslator 接口来约定好没有返回视
	 * 图名称的时候如何确定视图名称。 Spring 已经给我们提供了一个它自己的实现，那就是
	 * org.Springframework.web.servlet.view.DefaultRequestToViewNameTranslator。
	 *
	 * 在介绍 DefaultRequestToViewNameTranslator 是如何约定视图名称之前，先来看一下它支持用户定义的属性。
	 * 1、prefix：前缀 ， 表示约定好的视图名称需要加上的前缀，默认是空串 。
	 * 2、suffix ：后缀，表示约定好的视图名称需要加上的后缀， 默认是空串 。
	 * 3、separator：分隔符，默认是斜杠“／” 。
	 * 4、stripLeadingSlash：如果首字符是分隔符，是否要去除， 默认是 true 。
	 * 5、stripTrailingSlash：如果最后一个字符是分隔符，是否要去除，默认是 true。
	 * 6、stripExtension：如果请求路径包含扩展名是否要去除 ， 默认是 true。
	 * 7、urlDecode：是否需要对 URL 解码，默认是 true 。 它会采用 request 指定的编码或者 IS0-8859-1 编码对 URL 进行解码。
	 *
	 * 当我们没有在 SpringMVC 的配置文件中手动的定义一个名为 viewNameTranslator 的 Bean
	 * 的时候， Spring就会为我们提供一个默认的 viewNameTranslator ，即 DefaultRequestToViewNameTranslator。
	 * 接下来看一下， 当 Controller 处理器方法没有返回逻辑视图名称时， DefaultRequestToViewNameTranslator
	 * 是如何约定视图名称的。
	 * DefaultRequestToViewNameTranslator 会获取到请求的 URL，然后根据提供的属性做一些改造，把改造之后的结果作为视图名称返回。
	 * 这里以请求路径 http://localhost/app/test/index.html 为例，来说明一下 DefaultRequestToViewNameTranslator 是如何工作的。
	 * 该请求路径对应的请求 URL为/test/index.html，我们来看以下几种情况， 它分别对应的逻辑视图名称是什么。
	 * A、prefix 和 suffix 如果都存在，其他为默认值，那么对应返回的逻辑视图名称应该是 prefixtest/indexsuffix。
	 * B、stripLeadingSlash 和 stripExtension 都为 false ，其他默认， 这时候对应的逻辑视图名称是/product/index.html。
	 * C、都采用默认配置时，返回的逻辑视图名称应该是 product/index。
	 *
	 * 如果逻辑视图名称跟请求路径相同或者相关关系都是一样的 ， 那么我们就可以采用 Spring
	 * 为我们事先约定好的逻辑视图名称返回，这可以大大简化我们的开发工作，而以上功能实现的关键属性 viewNameTranslator，
	 * 则是在 initRequestToViewNameTranslator 中完成。
	 */
	private void initRequestToViewNameTranslator(ApplicationContext context) {
		try {
			this.viewNameTranslator =
					context.getBean(REQUEST_TO_VIEW_NAME_TRANSLATOR_BEAN_NAME, RequestToViewNameTranslator.class);
			if (logger.isTraceEnabled()) {
				logger.trace("Detected " + this.viewNameTranslator.getClass().getSimpleName());
			}
			else if (logger.isDebugEnabled()) {
				logger.debug("Detected " + this.viewNameTranslator);
			}
		}
		catch (NoSuchBeanDefinitionException ex) {
			// We need to use the default.
			this.viewNameTranslator = getDefaultStrategy(context, RequestToViewNameTranslator.class);
			if (logger.isTraceEnabled()) {
				logger.trace("No RequestToViewNameTranslator '" + REQUEST_TO_VIEW_NAME_TRANSLATOR_BEAN_NAME +
						"': using default [" + this.viewNameTranslator.getClass().getSimpleName() + "]");
			}
		}
	}

	/**
	 * Initialize the ViewResolvers used by this class.
	 * <p>If no ViewResolver beans are defined in the BeanFactory for this
	 * namespace, we default to InternalResourceViewResolver.
	 *
	 * 在 SpringMVC 中，当 Controller 将请求处理结果放入到 ModelAndView 中以后 ， DispatcherServlet
	 * 会根据 ModelAndView 选择合适的视图进行渲染。 那么在 SpringMVC 中是如 何选择合适的 View 呢？
	 *
	 * View 对象是是如何创建的呢？
	 *
	 * 答案就在 ViewResolver 中。 ViewResolver接口定义了 resolverViewName 方法，
	 * 根据 viewName 创建合适类型的 View 实现。 那么如何配置 ViewResolver 呢？
	 * 在 Spring 中， ViewResolver 作为 Spring Bean存在，可以在 Spring 配置文件中进行配置，例如下面的代码，配置了 JSP 相关的 viewResolver。
	 * <bean class= ”org.Sprinqframework.web.servlet.view.InternalResourceViewResolver">
	 *     <property name=”prefix” value=”/WEB-INF/views/”/>
	 *     <property name=”suffix" value=”.jsp” />
	 * </bean>
	 */
	private void initViewResolvers(ApplicationContext context) {
		this.viewResolvers = null;

		if (this.detectAllViewResolvers) {
			// Find all ViewResolvers in the ApplicationContext, including ancestor contexts.
			Map<String, ViewResolver> matchingBeans =
					BeanFactoryUtils.beansOfTypeIncludingAncestors(context, ViewResolver.class, true, false);
			if (!matchingBeans.isEmpty()) {
				this.viewResolvers = new ArrayList<>(matchingBeans.values());
				// We keep ViewResolvers in sorted order.
				AnnotationAwareOrderComparator.sort(this.viewResolvers);
			}
		}
		else {
			try {
				ViewResolver vr = context.getBean(VIEW_RESOLVER_BEAN_NAME, ViewResolver.class);
				this.viewResolvers = Collections.singletonList(vr);
			}
			catch (NoSuchBeanDefinitionException ex) {
				// Ignore, we'll add a default ViewResolver later.
			}
		}

		// Ensure we have at least one ViewResolver, by registering
		// a default ViewResolver if no other resolvers are found.
		// 如果找不到其他解析器，请注册默认的ViewResolver，确保我们至少有一个ViewResolver。
		if (this.viewResolvers == null) {
			this.viewResolvers = getDefaultStrategies(context, ViewResolver.class);
			if (logger.isTraceEnabled()) {
				logger.trace("No ViewResolvers declared for servlet '" + getServletName() +
						"': using default strategies from DispatcherServlet.properties");
			}
		}
	}

	/**
	 * Initialize the {@link FlashMapManager} used by this servlet instance.
	 * <p>If no implementation is configured then we default to
	 * {@code org.springframework.web.servlet.support.DefaultFlashMapManager}.
	 *
	 * SpringMVC Flash attributes 提供了一个请求存储属性，可供其他请求使用。 在使用重定向
	 * 时候非常必要，例如 Post/Redirect/Get 模式。 Flash attributes 在重定向之前暂存（就像存在 session 中）
	 * 以便重定向之后还能使用，并立即删除。
	 * SpringMVC 有两个主要的抽象来支持 flash attributes。 FlashMap 用于保持 flash attributes,
	 * 而 FlashMapManager 用于存储、检索、管理 FlashMap 实例。 flash attributes 支持默认开启（ ”on” ）并不
	 * 需要显式启用，它永远不会导致 HTTP Session 的创建。
	 *
	 * 这两个 FlashMap 实例都可以通过静态方法 RequestContextUtils 从 SpringMVC 的任何位置访问。
	 * flashMapManager 的初始化在 initFlashMapManager 中完成。
	 */
	private void initFlashMapManager(ApplicationContext context) {
		try {
			this.flashMapManager = context.getBean(FLASH_MAP_MANAGER_BEAN_NAME, FlashMapManager.class);
			if (logger.isTraceEnabled()) {
				logger.trace("Detected " + this.flashMapManager.getClass().getSimpleName());
			}
			else if (logger.isDebugEnabled()) {
				logger.debug("Detected " + this.flashMapManager);
			}
		}
		catch (NoSuchBeanDefinitionException ex) {
			// We need to use the default.
			this.flashMapManager = getDefaultStrategy(context, FlashMapManager.class);
			if (logger.isTraceEnabled()) {
				logger.trace("No FlashMapManager '" + FLASH_MAP_MANAGER_BEAN_NAME +
						"': using default [" + this.flashMapManager.getClass().getSimpleName() + "]");
			}
		}
	}

	/**
	 * Return this servlet's ThemeSource, if any; else return {@code null}.
	 * <p>Default is to return the WebApplicationContext as ThemeSource,
	 * provided that it implements the ThemeSource interface.
	 * @return the ThemeSource, if any
	 * @see #getWebApplicationContext()
	 */
	@Nullable
	public final ThemeSource getThemeSource() {
		return (getWebApplicationContext() instanceof ThemeSource ? (ThemeSource) getWebApplicationContext() : null);
	}

	/**
	 * Obtain this servlet's MultipartResolver, if any.
	 * @return the MultipartResolver used by this servlet, or {@code null} if none
	 * (indicating that no multipart support is available)
	 */
	@Nullable
	public final MultipartResolver getMultipartResolver() {
		return this.multipartResolver;
	}

	/**
	 * Return the configured {@link HandlerMapping} beans that were detected by
	 * type in the {@link WebApplicationContext} or initialized based on the
	 * default set of strategies from {@literal DispatcherServlet.properties}.
	 * <p><strong>Note:</strong> This method may return {@code null} if invoked
	 * prior to {@link #onRefresh(ApplicationContext)}.
	 * @return an immutable list with the configured mappings, or {@code null}
	 * if not initialized yet
	 * @since 5.0
	 */
	@Nullable
	public final List<HandlerMapping> getHandlerMappings() {
		return (this.handlerMappings != null ? Collections.unmodifiableList(this.handlerMappings) : null);
	}

	/**
	 * Return the default strategy object for the given strategy interface.
	 * <p>The default implementation delegates to {@link #getDefaultStrategies},
	 * expecting a single object in the list.
	 * @param context the current WebApplicationContext
	 * @param strategyInterface the strategy interface
	 * @return the corresponding strategy object
	 * @see #getDefaultStrategies
	 */
	protected <T> T getDefaultStrategy(ApplicationContext context, Class<T> strategyInterface) {
		List<T> strategies = getDefaultStrategies(context, strategyInterface);
		if (strategies.size() != 1) {
			throw new BeanInitializationException(
					"DispatcherServlet needs exactly 1 strategy for interface [" + strategyInterface.getName() + "]");
		}
		return strategies.get(0);
	}

	/**
	 * Create a List of default strategy objects for the given strategy interface.
	 * <p>The default implementation uses the "DispatcherServlet.properties" file (in the same
	 * package as the DispatcherServlet class) to determine the class names. It instantiates
	 * the strategy objects through the context's BeanFactory.
	 *
	 * 在系统加载的时候， defaultStrategies 根据当前路径 DispatcherServlet.properties 来初始化本
	 * 身，查看 DispatcherServlet.properties 中对应于 HandlerAdapter 的属性：
	 * org.Springframework.web.servlet.HandlerAdapter=org.Springframework.web.servlet.mvc.HttpRequestHandlerAdapter , \
	 * org.Springframework.web.servlet.mvc.SimpleControllerHandlerAdapter , \
	 * org.Springframework.web.servlet.mvc.annotation.AnnotationMethodHandlerAdapter
	 * 由此得知，如果程序开发人员没有在配置文件中定义自己的适配器，那么 Spring 会默认加
	 * 载配置文件中的 3 个适配器。
	 * 作为总控制器的派遣器 servlet 通过处理器映射得到处理器后，会轮询处理器适配器模块，查找能够处理当前 HTTP 请求的处理器适配器的实现，
	 * 处理器适配器模块根据处理器映射返回的处理器类型，例如简单的控制器类型、注解控制器类型或者远程调用处理器类型，来选择某一个适当的处理
	 * 器适配器的实现，从而适配当前的 HTTP 请求。
	 * 1、HTTP 请求处理器适配器（ HttpRequestHandlerAdapter ）。
	 *   HTTP 请求处理器适配器仅仅支持对 HTTP 请求处理器的适配。 它简单地将 HTTP 请求对象和响应对象传递给 HTTP
	 *   请求处理器的实现，它并不需要返回值。 它主要应用在基于 HTTP 的远程调用的实现上。
	 *
	 * 2、简单控制器处理器适配器（ SimpleControllerHandlerAdapter ）。
	 *   这个实现类将 HTTP 请求适配到一个控制器的实现进行处理。 这里控制器的实现是一个简单的控制器接口的实现。
	 *   简单控制器处理器适配器被设计成一个框架类的实现，不需要被改写，客户化的业务逻辑通常是在控制器接口的实现类中实现的。
	 *
	 * 3、注解方法处理器适配器（ AnnotationMethodHandlerAdapter ）。
	 *   这个类的实现是基于注解的实现，它需要结合注解方法映射和注解方法处理器协同工作。 它通过解析声明在注解控制器的请求
	 *   映射信息来解析相应的处理器方法来处理当前的 HTTP 请求。在处理的过程中，它通过反射来发现探测处理器方法的参数，
	 *   调用处理器方法，并且映射返回值到模型和控制器对象，最后返回模型和控制器对象给作为主控制器的派遣器 Servlet。
	 *   所以我们现在基本上可以回答之前的问题了，Spring中所使用的 Handler 并没有任何特殊的联系，但是为了统一处理，
	 *   Spring提供了不同情况下的适配器。
	 *
	 * @param context the current WebApplicationContext
	 * @param strategyInterface the strategy interface
	 * @return the List of corresponding strategy objects
	 */
	@SuppressWarnings("unchecked")
	protected <T> List<T> getDefaultStrategies(ApplicationContext context, Class<T> strategyInterface) {
		String key = strategyInterface.getName();
		String value = defaultStrategies.getProperty(key);
		if (value != null) {
			String[] classNames = StringUtils.commaDelimitedListToStringArray(value);
			List<T> strategies = new ArrayList<>(classNames.length);
			for (String className : classNames) {
				try {
					Class<?> clazz = ClassUtils.forName(className, DispatcherServlet.class.getClassLoader());
					Object strategy = createDefaultStrategy(context, clazz);
					strategies.add((T) strategy);
				}
				catch (ClassNotFoundException ex) {
					throw new BeanInitializationException(
							"Could not find DispatcherServlet's default strategy class [" + className +
							"] for interface [" + key + "]", ex);
				}
				catch (LinkageError err) {
					throw new BeanInitializationException(
							"Unresolvable class definition for DispatcherServlet's default strategy class [" +
							className + "] for interface [" + key + "]", err);
				}
			}
			return strategies;
		}
		else {
			return new LinkedList<>();
		}
	}

	/**
	 * Create a default strategy.
	 * <p>The default implementation uses
	 * {@link org.springframework.beans.factory.config.AutowireCapableBeanFactory#createBean}.
	 * @param context the current WebApplicationContext
	 * @param clazz the strategy implementation class to instantiate
	 * @return the fully configured strategy instance
	 * @see org.springframework.context.ApplicationContext#getAutowireCapableBeanFactory()
	 * @see org.springframework.beans.factory.config.AutowireCapableBeanFactory#createBean
	 */
	protected Object createDefaultStrategy(ApplicationContext context, Class<?> clazz) {
		return context.getAutowireCapableBeanFactory().createBean(clazz);
	}


	/**
	 * Exposes the DispatcherServlet-specific request attributes and delegates to {@link #doDispatch}
	 * for the actual dispatching.
	 *
	 * 我们猜想对请求处理至少应该包括一些诸如寻找 Handler 并页面跳转之类的逻辑处理，
	 * 但是，在 doService 中我们并没有看到想看到的逻辑，相反却同样是一些准备工作，但是这些准备工作却是必不可少的。
	 *
	 * Spring 将已经初始化的功能辅助工具变量，比如 localeResolver , themeResolver 等设置在 request 属性中，
	 * 而这些属性会在接下来的处理中派上用场。 经过层层的准备工作， 终于在 doDispatch 函数中看到了完整的请求处理过程。
	 *
	 */
	@Override
	protected void doService(HttpServletRequest request, HttpServletResponse response) throws Exception {
		logRequest(request);

		// Keep a snapshot of the request attributes in case of an include,
		// to be able to restore the original attributes after the include.
		Map<String, Object> attributesSnapshot = null;
		if (WebUtils.isIncludeRequest(request)) {
			attributesSnapshot = new HashMap<>();
			Enumeration<?> attrNames = request.getAttributeNames();
			while (attrNames.hasMoreElements()) {
				String attrName = (String) attrNames.nextElement();
				if (this.cleanupAfterInclude || attrName.startsWith(DEFAULT_STRATEGIES_PREFIX)) {
					attributesSnapshot.put(attrName, request.getAttribute(attrName));
				}
			}
		}

		// Make framework objects available to handlers and view objects.
		request.setAttribute(WEB_APPLICATION_CONTEXT_ATTRIBUTE, getWebApplicationContext());
		request.setAttribute(LOCALE_RESOLVER_ATTRIBUTE, this.localeResolver);
		request.setAttribute(THEME_RESOLVER_ATTRIBUTE, this.themeResolver);
		request.setAttribute(THEME_SOURCE_ATTRIBUTE, getThemeSource());

		if (this.flashMapManager != null) {
			FlashMap inputFlashMap = this.flashMapManager.retrieveAndUpdate(request, response);
			if (inputFlashMap != null) {
				request.setAttribute(INPUT_FLASH_MAP_ATTRIBUTE, Collections.unmodifiableMap(inputFlashMap));
			}
			request.setAttribute(OUTPUT_FLASH_MAP_ATTRIBUTE, new FlashMap());
			request.setAttribute(FLASH_MAP_MANAGER_ATTRIBUTE, this.flashMapManager);
		}

		try {
			doDispatch(request, response);
		}
		finally {
			if (!WebAsyncUtils.getAsyncManager(request).isConcurrentHandlingStarted()) {
				// Restore the original attribute snapshot, in case of an include.
				if (attributesSnapshot != null) {
					restoreAttributesAfterInclude(request, attributesSnapshot);
				}
			}
		}
	}

	private void logRequest(HttpServletRequest request) {
		LogFormatUtils.traceDebug(logger, traceOn -> {
			String params;
			if (isEnableLoggingRequestDetails()) {
				params = request.getParameterMap().entrySet().stream()
						.map(entry -> entry.getKey() + ":" + Arrays.toString(entry.getValue()))
						.collect(Collectors.joining(", "));
			}
			else {
				params = (request.getParameterMap().isEmpty() ? "" : "masked");
			}

			String queryString = request.getQueryString();
			String queryClause = (StringUtils.hasLength(queryString) ? "?" + queryString : "");
			String dispatchType = (!request.getDispatcherType().equals(DispatcherType.REQUEST) ?
					"\"" + request.getDispatcherType().name() + "\" dispatch for " : "");
			String message = (dispatchType + request.getMethod() + " \"" + getRequestUri(request) +
					queryClause + "\", parameters={" + params + "}");

			if (traceOn) {
				List<String> values = Collections.list(request.getHeaderNames());
				String headers = values.size() > 0 ? "masked" : "";
				if (isEnableLoggingRequestDetails()) {
					headers = values.stream().map(name -> name + ":" + Collections.list(request.getHeaders(name)))
							.collect(Collectors.joining(", "));
				}
				return message + ", headers={" + headers + "} in DispatcherServlet '" + getServletName() + "'";
			}
			else {
				return message;
			}
		});
	}

	/**
	 * Process the actual dispatching to the handler.
	 * <p>The handler will be obtained by applying the servlet's HandlerMappings in order.
	 * The HandlerAdapter will be obtained by querying the servlet's installed HandlerAdapters
	 * to find the first that supports the handler class.
	 * <p>All HTTP methods are handled by this method. It's up to HandlerAdapters or handlers
	 * themselves to decide which methods are acceptable.
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @throws Exception in case of any kind of processing failure
	 */
	protected void doDispatch(HttpServletRequest request, HttpServletResponse response) throws Exception {
		HttpServletRequest processedRequest = request;
		HandlerExecutionChain mappedHandler = null;
		boolean multipartRequestParsed = false;

		WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(request);

		try {
			ModelAndView mv = null;
			Exception dispatchException = null;

			try {
				//如果是 MultipartContent 类型的 request 则转换 request 为 MultipartHttpServletRequest 类型的 request
				processedRequest = checkMultipart(request);
				multipartRequestParsed = (processedRequest != request);

				// Determine handler for the current request.
				// 根据 request 信息寻找对应的 Handler
				mappedHandler = getHandler(processedRequest);
				if (mappedHandler == null) {
					//如果没有找到对应的 handler 则通过 response 反馈错误信息
					noHandlerFound(processedRequest, response);
					return;
				}

				// Determine handler adapter for the current request.
				// 根据当前的 handler 寻找对应的 HandlerAdapter
				HandlerAdapter ha = getHandlerAdapter(mappedHandler.getHandler());

				// Process last-modified header, if supported by the handler.
				// 如果当前 handler 支持 last-modified 头处理
				String method = request.getMethod();
				boolean isGet = "GET".equals(method);
				if (isGet || "HEAD".equals(method)) {
					long lastModified = ha.getLastModified(request, mappedHandler.getHandler());
					if (new ServletWebRequest(request, response).checkNotModified(lastModified) && isGet) {
						return;
					}
				}
				//拦截器的 preHandler 方法的调用
				if (!mappedHandler.applyPreHandle(processedRequest, response)) {
					return;
				}

				// Actually invoke the handler.
				// 真正的激活 handler 并返回视图
				mv = ha.handle(processedRequest, response, mappedHandler.getHandler());

				if (asyncManager.isConcurrentHandlingStarted()) {
					return;
				}
                // 视图名称转换应用于需要添加前缀后缀的情况
				applyDefaultViewName(processedRequest, mv);
				// 应用所有拦截器的 postHandle 方法
				mappedHandler.applyPostHandle(processedRequest, response, mv);
			}
			catch (Exception ex) {
				dispatchException = ex;
			}
			catch (Throwable err) {
				// As of 4.3, we're processing Errors thrown from handler methods as well,
				// making them available for @ExceptionHandler methods and other scenarios.
				dispatchException = new NestedServletException("Handler dispatch failed", err);
			}
			// 如果在 Handler 实例的处理中返回了 view，那么需要做页面的处理
			processDispatchResult(processedRequest, response, mappedHandler, mv, dispatchException);
		}
		catch (Exception ex) {
			// 完成处理激活触发器
			triggerAfterCompletion(processedRequest, response, mappedHandler, ex);
		}
		catch (Throwable err) {
			//完成处理激活触发器
			triggerAfterCompletion(processedRequest, response, mappedHandler,
					new NestedServletException("Handler processing failed", err));
		}
		finally {
			if (asyncManager.isConcurrentHandlingStarted()) {
				// Instead of postHandle and afterCompletion
				if (mappedHandler != null) {
					mappedHandler.applyAfterConcurrentHandlingStarted(processedRequest, response);
				}
			}
			else {
				// Clean up any resources used by a multipart request.
				if (multipartRequestParsed) {
					cleanupMultipart(processedRequest);
				}
			}
		}
	}

	/**
	 * Do we need view name translation?
	 */
	private void applyDefaultViewName(HttpServletRequest request, @Nullable ModelAndView mv) throws Exception {
		if (mv != null && !mv.hasView()) {
			String defaultViewName = getDefaultViewName(request);
			if (defaultViewName != null) {
				mv.setViewName(defaultViewName);
			}
		}
	}

	/**
	 * Handle the result of handler selection and handler invocation, which is
	 * either a ModelAndView or an Exception to be resolved to a ModelAndView.
	 */
	private void processDispatchResult(HttpServletRequest request, HttpServletResponse response,
			@Nullable HandlerExecutionChain mappedHandler, @Nullable ModelAndView mv,
			@Nullable Exception exception) throws Exception {

		boolean errorView = false;

		if (exception != null) {
			if (exception instanceof ModelAndViewDefiningException) {
				logger.debug("ModelAndViewDefiningException encountered", exception);
				mv = ((ModelAndViewDefiningException) exception).getModelAndView();
			}
			else {
				Object handler = (mappedHandler != null ? mappedHandler.getHandler() : null);
				mv = processHandlerException(request, response, handler, exception);
				errorView = (mv != null);
			}
		}

		// Did the handler return a view to render?
		if (mv != null && !mv.wasCleared()) {
			// 处理页面跳转
			render(mv, request, response);
			if (errorView) {
				WebUtils.clearErrorRequestAttributes(request);
			}
		}
		else {
			if (logger.isTraceEnabled()) {
				logger.trace("No view rendering, null ModelAndView returned.");
			}
		}

		if (WebAsyncUtils.getAsyncManager(request).isConcurrentHandlingStarted()) {
			// Concurrent handling started during a forward
			return;
		}

		if (mappedHandler != null) {
			// Exception (if any) is already handled..
			mappedHandler.triggerAfterCompletion(request, response, null);
		}
	}

	/**
	 * Build a LocaleContext for the given request, exposing the request's primary locale as current locale.
	 * <p>The default implementation uses the dispatcher's LocaleResolver to obtain the current locale,
	 * which might change during a request.
	 * @param request current HTTP request
	 * @return the corresponding LocaleContext
	 */
	@Override
	protected LocaleContext buildLocaleContext(final HttpServletRequest request) {
		LocaleResolver lr = this.localeResolver;
		if (lr instanceof LocaleContextResolver) {
			return ((LocaleContextResolver) lr).resolveLocaleContext(request);
		}
		else {
			return () -> (lr != null ? lr.resolveLocale(request) : request.getLocale());
		}
	}

	/**
	 * Convert the request into a multipart request, and make multipart resolver available.
	 * <p>If no multipart resolver is set, simply use the existing request.
	 *
	 * 对于请求的处理， Spring 首先考虑的是对于 Multipart 的处理如果是 MultipartContentRequest ，
	 * 则转换 request 为 MultipartHttpServletRequest 类型的 request
	 *
	 * @param request current HTTP request
	 * @return the processed request (multipart wrapper if necessary)
	 * @see MultipartResolver#resolveMultipart
	 */
	protected HttpServletRequest checkMultipart(HttpServletRequest request) throws MultipartException {
		if (this.multipartResolver != null && this.multipartResolver.isMultipart(request)) {
			if (WebUtils.getNativeRequest(request, MultipartHttpServletRequest.class) != null) {
				if (request.getDispatcherType().equals(DispatcherType.REQUEST)) {
					logger.trace("Request already resolved to MultipartHttpServletRequest, e.g. by MultipartFilter");
				}
			}
			else if (hasMultipartException(request)) {
				logger.debug("Multipart resolution previously failed for current request - " +
						"skipping re-resolution for undisturbed error rendering");
			}
			else {
				try {
					return this.multipartResolver.resolveMultipart(request);
				}
				catch (MultipartException ex) {
					if (request.getAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE) != null) {
						logger.debug("Multipart resolution failed for error dispatch", ex);
						// Keep processing error dispatch with regular request handle below
					}
					else {
						throw ex;
					}
				}
			}
		}
		// If not returned before: return original request.
		return request;
	}

	/**
	 * Check "javax.servlet.error.exception" attribute for a multipart exception.
	 */
	private boolean hasMultipartException(HttpServletRequest request) {
		Throwable error = (Throwable) request.getAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE);
		while (error != null) {
			if (error instanceof MultipartException) {
				return true;
			}
			error = error.getCause();
		}
		return false;
	}

	/**
	 * Clean up any resources used by the given multipart request (if any).
	 * @param request current HTTP request
	 * @see MultipartResolver#cleanupMultipart
	 */
	protected void cleanupMultipart(HttpServletRequest request) {
		if (this.multipartResolver != null) {
			MultipartHttpServletRequest multipartRequest =
					WebUtils.getNativeRequest(request, MultipartHttpServletRequest.class);
			if (multipartRequest != null) {
				this.multipartResolver.cleanupMultipart(multipartRequest);
			}
		}
	}

	/**
	 * Return the HandlerExecutionChain for this request.
	 * <p>Tries all handler mappings in order.
	 *
	 * <bean id= simpleUrlMapping class ="org.Springframework.web.servlet.handler.SimpleUrlHandlerMapping”>
	 *    <property name=” mappings” >
	 *      <props>
	 *        <prop key=”/userlist.htm” >userController</prop>
	 *      </props>
	 *    </property>
	 * </bean>
	 *
	 * Spring 加载的过程中， Spring 会将类型为 SimpleUrlHandlerMapping 的实例加载到
	 * this.handlerMappings中，按照常理推断 ，根据 request 提取对应的 Handler ，无非就是提取当
	 * 实例中 userController ，但是 userController 为继承自 AbstractController 类型实例，
	 * 与 HandlerExecutionChain 并无任何 联，那么这 步是如何封装的呢
	 * @param request current HTTP request
	 * @return the HandlerExecutionChain, or {@code null} if no handler could be found
	 */
	@Nullable
	protected HandlerExecutionChain getHandler(HttpServletRequest request) throws Exception {
		if (this.handlerMappings != null) {
			for (HandlerMapping mapping : this.handlerMappings) {
				HandlerExecutionChain handler = mapping.getHandler(request);
				if (handler != null) {
					return handler;
				}
			}
		}
		return null;
	}

	/**
	 * No handler found -> set appropriate HTTP response status.
	 *
	 * 每个请求都应该对应着－个 Handler ，因为每个请求都会在后台有相应的逻辑对应，而逻辑的
	 * 现就是在 Handler 中，所以一旦遇到没有找到 Handler 情况（正常情况下如果没有 URL
	 * 匹配的 Handler ，开发人员可以设置默认的 Handler 处理请求，但是如果默认请求也未设置就
	 * 会出现 Handler为空的情况），就只能通过 response 向用户返回错误信息
	 *
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @throws Exception if preparing the response failed
	 */
	protected void noHandlerFound(HttpServletRequest request, HttpServletResponse response) throws Exception {
		if (pageNotFoundLogger.isWarnEnabled()) {
			pageNotFoundLogger.warn("No mapping for " + request.getMethod() + " " + getRequestUri(request));
		}
		if (this.throwExceptionIfNoHandlerFound) {
			throw new NoHandlerFoundException(request.getMethod(), getRequestUri(request),
					new ServletServerHttpRequest(request).getHeaders());
		}
		else {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
		}
	}

	/**
	 * Return the HandlerAdapter for this handler object.
	 *
	 * WebApplicationContext 的初始 过程中我们讨论了 HandlerAdapters 的初始化，了解了
	 * 在默认情况下普通的 Web 请求会交给 SimpleControllerHandlerAdapter 去处理。下面我们以
	 * SimpleControllerHandlerAdapter 为例来分析获取适配器的逻辑
	 *
	 * @param handler the handler object to find an adapter for
	 * @throws ServletException if no HandlerAdapter can be found for the handler. This is a fatal error.
	 */
	protected HandlerAdapter getHandlerAdapter(Object handler) throws ServletException {
		if (this.handlerAdapters != null) {
			for (HandlerAdapter adapter : this.handlerAdapters) {
				// 通过上面的函数我们了解到，对于获取适配器的逻辑无非就是遍历所有适配器来选择合适
				// 的适配器并返回它，而某个适配器是否适用于当前的 Handler 逻辑被封装在具体的适配器中
				// 进一步查看 SimpleControllerHandlerAdapter 中的 supports 方法。
				if (adapter.supports(handler)) {
					return adapter;
				}
			}
		}
		throw new ServletException("No adapter for handler [" + handler +
				"]: The DispatcherServlet configuration needs to include a HandlerAdapter that supports this handler");
	}

	/**
	 * Determine an error ModelAndView via the registered HandlerExceptionResolvers.
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @param handler the executed handler, or {@code null} if none chosen at the time of the exception
	 * (for example, if multipart resolution failed)
	 * @param ex the exception that got thrown during handler execution
	 * @return a corresponding ModelAndView to forward to
	 * @throws Exception if no error ModelAndView found
	 */
	@Nullable
	protected ModelAndView processHandlerException(HttpServletRequest request, HttpServletResponse response,
			@Nullable Object handler, Exception ex) throws Exception {

		// Success and error responses may use different content types
		request.removeAttribute(HandlerMapping.PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE);

		// Check registered HandlerExceptionResolvers...
		ModelAndView exMv = null;
		if (this.handlerExceptionResolvers != null) {
			for (HandlerExceptionResolver resolver : this.handlerExceptionResolvers) {
				exMv = resolver.resolveException(request, response, handler, ex);
				if (exMv != null) {
					break;
				}
			}
		}
		if (exMv != null) {
			if (exMv.isEmpty()) {
				request.setAttribute(EXCEPTION_ATTRIBUTE, ex);
				return null;
			}
			// We might still need view name translation for a plain error model...
			if (!exMv.hasView()) {
				String defaultViewName = getDefaultViewName(request);
				if (defaultViewName != null) {
					exMv.setViewName(defaultViewName);
				}
			}
			if (logger.isTraceEnabled()) {
				logger.trace("Using resolved error view: " + exMv, ex);
			}
			else if (logger.isDebugEnabled()) {
				logger.debug("Using resolved error view: " + exMv);
			}
			WebUtils.exposeErrorRequestAttributes(request, ex, getServletName());
			return exMv;
		}

		throw ex;
	}

	/**
	 * Render the given ModelAndView.
	 * <p>This is the last stage in handling a request. It may involve resolving the view by name.
	 * @param mv the ModelAndView to render
	 * @param request current HTTP servlet request
	 * @param response current HTTP servlet response
	 * @throws ServletException if view is missing or cannot be resolved
	 * @throws Exception if there's a problem rendering the view
	 */
	protected void render(ModelAndView mv, HttpServletRequest request, HttpServletResponse response) throws Exception {
		// Determine locale for request and apply it to the response.
		Locale locale =
				(this.localeResolver != null ? this.localeResolver.resolveLocale(request) : request.getLocale());
		response.setLocale(locale);

		View view;
		String viewName = mv.getViewName();
		if (viewName != null) {
			// We need to resolve the view name.
			view = resolveViewName(viewName, mv.getModelInternal(), locale, request);
			if (view == null) {
				throw new ServletException("Could not resolve view with name '" + mv.getViewName() +
						"' in servlet with name '" + getServletName() + "'");
			}
		}
		else {
			// No need to lookup: the ModelAndView object contains the actual View object.
			view = mv.getView();
			if (view == null) {
				throw new ServletException("ModelAndView [" + mv + "] neither contains a view name nor a " +
						"View object in servlet with name '" + getServletName() + "'");
			}
		}

		// Delegate to the View object for rendering.
		if (logger.isTraceEnabled()) {
			logger.trace("Rendering view [" + view + "] ");
		}
		try {
			if (mv.getStatus() != null) {
				response.setStatus(mv.getStatus().value());
			}
			view.render(mv.getModelInternal(), request, response);
		}
		catch (Exception ex) {
			if (logger.isDebugEnabled()) {
				logger.debug("Error rendering view [" + view + "]", ex);
			}
			throw ex;
		}
	}

	/**
	 * Translate the supplied request into a default view name.
	 * @param request current HTTP servlet request
	 * @return the view name (or {@code null} if no default found)
	 * @throws Exception if view name translation failed
	 */
	@Nullable
	protected String getDefaultViewName(HttpServletRequest request) throws Exception {
		return (this.viewNameTranslator != null ? this.viewNameTranslator.getViewName(request) : null);
	}

	/**
	 * Resolve the given view name into a View object (to be rendered).
	 * <p>The default implementations asks all ViewResolvers of this dispatcher.
	 * Can be overridden for custom resolution strategies, potentially based on
	 * specific model attributes or request parameters.
	 * @param viewName the name of the view to resolve
	 * @param model the model to be passed to the view
	 * @param locale the current locale
	 * @param request current HTTP servlet request
	 * @return the View object, or {@code null} if none found
	 * @throws Exception if the view cannot be resolved
	 * (typically in case of problems creating an actual View object)
	 * @see ViewResolver#resolveViewName
	 */
	@Nullable
	protected View resolveViewName(String viewName, @Nullable Map<String, Object> model,
			Locale locale, HttpServletRequest request) throws Exception {

		if (this.viewResolvers != null) {
			for (ViewResolver viewResolver : this.viewResolvers) {
				View view = viewResolver.resolveViewName(viewName, locale);
				if (view != null) {
					return view;
				}
			}
		}
		return null;
	}

	private void triggerAfterCompletion(HttpServletRequest request, HttpServletResponse response,
			@Nullable HandlerExecutionChain mappedHandler, Exception ex) throws Exception {

		if (mappedHandler != null) {
			mappedHandler.triggerAfterCompletion(request, response, ex);
		}
		throw ex;
	}

	/**
	 * Restore the request attributes after an include.
	 * @param request current HTTP request
	 * @param attributesSnapshot the snapshot of the request attributes before the include
	 */
	@SuppressWarnings("unchecked")
	private void restoreAttributesAfterInclude(HttpServletRequest request, Map<?, ?> attributesSnapshot) {
		// Need to copy into separate Collection here, to avoid side effects
		// on the Enumeration when removing attributes.
		Set<String> attrsToCheck = new HashSet<>();
		Enumeration<?> attrNames = request.getAttributeNames();
		while (attrNames.hasMoreElements()) {
			String attrName = (String) attrNames.nextElement();
			if (this.cleanupAfterInclude || attrName.startsWith(DEFAULT_STRATEGIES_PREFIX)) {
				attrsToCheck.add(attrName);
			}
		}

		// Add attributes that may have been removed
		attrsToCheck.addAll((Set<String>) attributesSnapshot.keySet());

		// Iterate over the attributes to check, restoring the original value
		// or removing the attribute, respectively, if appropriate.
		for (String attrName : attrsToCheck) {
			Object attrValue = attributesSnapshot.get(attrName);
			if (attrValue == null) {
				request.removeAttribute(attrName);
			}
			else if (attrValue != request.getAttribute(attrName)) {
				request.setAttribute(attrName, attrValue);
			}
		}
	}

	private static String getRequestUri(HttpServletRequest request) {
		String uri = (String) request.getAttribute(WebUtils.INCLUDE_REQUEST_URI_ATTRIBUTE);
		if (uri == null) {
			uri = request.getRequestURI();
		}
		return uri;
	}

}
