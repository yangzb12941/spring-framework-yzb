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

package org.springframework.web.context;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

/**
 * Bootstrap listener to start up and shut down Spring's root {@link WebApplicationContext}.
 * Simply delegates to {@link ContextLoader} as well as to {@link ContextCleanupListener}.
 *
 * <p>As of Spring 3.1, {@code ContextLoaderListener} supports injecting the root web
 * application context via the {@link #ContextLoaderListener(WebApplicationContext)}
 * constructor, allowing for programmatic configuration in Servlet 3.0+ environments.
 * See {@link org.springframework.web.WebApplicationInitializer} for usage examples.
 *
 * 但是在 Web下，我们需要更多的是与 Web 环境相互结合，通常的办法是将路径以
 * context-param 的方式注册并使用 ContextLoaderListener 进行监听读取。
 * ContextLoaderListener 的作用就是启动 Web 容器时，自动装配 ApplicationContext 的配置信
 * 息。 因为它实现了 ServletContextListener 这个接口，在 web.xml 配置这个监昕器，启动容器时，
 * 就会默认执行它实现的方法，使用 ServletContextListener 接口，开发者能够在为客户端请求提供服
 * 务之前向 ServletContext 中添加任意的对象。 这个对象在 ServletContext 启动的时候被初始化，
 * 然后在 ServletContext 整个运行期间都是可见的。 每一个 Web 应用者附一个 ServletContext 与之相关联。
 * ServletContext对象在应用启动时被创建， 在应用关闭的时候被销毁。 ServletContext 在全局范围内有效，
 * 类似于应用中的一个全局变量。 在 ServletContextListener 中的核心逻辑便是初始化 WebApplicationContext
 * 实例并存放至ServletContext 中。
 *
 * 正式分析代码前我们同样还是首先了解 ServletContextListener 的使用。
 * 1. 创建自定义 ServletContextListener
 *    首先我们创建 ServletContextListener，目标是在系统启动时添加自定义的属性，以便于在全局范围内可以随时调用 。
 *    系统启动的时候会调用 ServletContextListener 实现类的 contextInitialized 方法，所以需要在这个方法中实现我们的初始化逻辑。
 *  public class MyDataContextListener implements ServletContextListener{
 *      private ServletContext context = null;
 *
 *      public MyDataContextListener(){
 *
 *      }
 *
 *      //该方法在ServletContext启动之后被调用，并准备好处理客户端请求
 *      public void contextInitialized(ServletContextEvent event){
 *          this.context = event.getServletContext();
 *          //通过你可以实现自己的逻辑并将结果记录在属性中
 *          context = setAttribute("myData","this is myData");
 *      }
 *
 *      //这个方法在ServletContext 将要关闭的时候调用
 *      public void contextDestroyed(ServletContextEvent event){
 *          this.context = null;
 *      }
 *  }
 *
 *  2、注册监听器
 *  在web.xml文件中需要注册自定义的监听器
 *  <listener>
 *      com.test.MyDataContextListener
 *  </listener>
 *
 *  3、测试
 *  一旦Web 应用启动的时候，我们就能在任意的Servlet或者JSP中通过如下方式获取数据
 *  String myData = (String) getServletContext().getAttribute("myData");
 *
 * @author Juergen Hoeller
 * @author Chris Beams
 * @since 17.02.2003
 * @see #setContextInitializers
 * @see org.springframework.web.WebApplicationInitializer
 */
public class ContextLoaderListener extends ContextLoader implements ServletContextListener {

	/**
	 * Create a new {@code ContextLoaderListener} that will create a web application
	 * context based on the "contextClass" and "contextConfigLocation" servlet
	 * context-params. See {@link ContextLoader} superclass documentation for details on
	 * default values for each.
	 * <p>This constructor is typically used when declaring {@code ContextLoaderListener}
	 * as a {@code <listener>} within {@code web.xml}, where a no-arg constructor is
	 * required.
	 * <p>The created application context will be registered into the ServletContext under
	 * the attribute name {@link WebApplicationContext#ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE}
	 * and the Spring application context will be closed when the {@link #contextDestroyed}
	 * lifecycle method is invoked on this listener.
	 * @see ContextLoader
	 * @see #ContextLoaderListener(WebApplicationContext)
	 * @see #contextInitialized(ServletContextEvent)
	 * @see #contextDestroyed(ServletContextEvent)
	 */
	public ContextLoaderListener() {
	}

	/**
	 * Create a new {@code ContextLoaderListener} with the given application context. This
	 * constructor is useful in Servlet 3.0+ environments where instance-based
	 * registration of listeners is possible through the {@link javax.servlet.ServletContext#addListener}
	 * API.
	 * <p>The context may or may not yet be {@linkplain
	 * org.springframework.context.ConfigurableApplicationContext#refresh() refreshed}. If it
	 * (a) is an implementation of {@link ConfigurableWebApplicationContext} and
	 * (b) has <strong>not</strong> already been refreshed (the recommended approach),
	 * then the following will occur:
	 * <ul>
	 * <li>If the given context has not already been assigned an {@linkplain
	 * org.springframework.context.ConfigurableApplicationContext#setId id}, one will be assigned to it</li>
	 * <li>{@code ServletContext} and {@code ServletConfig} objects will be delegated to
	 * the application context</li>
	 * <li>{@link #customizeContext} will be called</li>
	 * <li>Any {@link org.springframework.context.ApplicationContextInitializer ApplicationContextInitializer org.springframework.context.ApplicationContextInitializer ApplicationContextInitializers}
	 * specified through the "contextInitializerClasses" init-param will be applied.</li>
	 * <li>{@link org.springframework.context.ConfigurableApplicationContext#refresh refresh()} will be called</li>
	 * </ul>
	 * If the context has already been refreshed or does not implement
	 * {@code ConfigurableWebApplicationContext}, none of the above will occur under the
	 * assumption that the user has performed these actions (or not) per his or her
	 * specific needs.
	 * <p>See {@link org.springframework.web.WebApplicationInitializer} for usage examples.
	 * <p>In any case, the given application context will be registered into the
	 * ServletContext under the attribute name {@link
	 * WebApplicationContext#ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE} and the Spring
	 * application context will be closed when the {@link #contextDestroyed} lifecycle
	 * method is invoked on this listener.
	 * @param context the application context to manage
	 * @see #contextInitialized(ServletContextEvent)
	 * @see #contextDestroyed(ServletContextEvent)
	 */
	public ContextLoaderListener(WebApplicationContext context) {
		super(context);
	}


	/**
	 * Initialize the root web application context.
	 * 初始化根web应用程序上下文。
	 * ServletContext 启动之后会调用 ServletContextListener 的 contextInitialized 方法，那么，我
	 * 们就从这个函数开始进行分析。
	 */
	@Override
	public void contextInitialized(ServletContextEvent event) {
		//初始化WebApplicationContext
		initWebApplicationContext(event.getServletContext());
	}


	/**
	 * Close the root web application context.
	 */
	@Override
	public void contextDestroyed(ServletContextEvent event) {
		closeWebApplicationContext(event.getServletContext());
		ContextCleanupListener.cleanupAttributes(event.getServletContext());
	}

}
