/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.remoting.support;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.framework.adapter.AdvisorAdapterRegistry;
import org.springframework.aop.framework.adapter.GlobalAdvisorAdapterRegistry;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * Abstract base class for classes that export a remote service.
 * Provides "service" and "serviceInterface" bean properties.
 *
 * <p>Note that the service interface being used will show some signs of
 * remotability, like the granularity of method calls that it offers.
 * Furthermore, it has to have serializable arguments etc.
 *
 * @author Juergen Hoeller
 * @since 26.12.2003
 */
public abstract class RemoteExporter extends RemotingSupport {

	private Object service;

	private Class<?> serviceInterface;

	private Boolean registerTraceInterceptor;

	private Object[] interceptors;


	/**
	 * Set the service to export.
	 * Typically populated via a bean reference.
	 */
	public void setService(Object service) {
		this.service = service;
	}

	/**
	 * Return the service to export.
	 */
	public Object getService() {
		return this.service;
	}

	/**
	 * Set the interface of the service to export.
	 * The interface must be suitable for the particular service and remoting strategy.
	 */
	public void setServiceInterface(Class<?> serviceInterface) {
		Assert.notNull(serviceInterface, "'serviceInterface' must not be null");
		Assert.isTrue(serviceInterface.isInterface(), "'serviceInterface' must be an interface");
		this.serviceInterface = serviceInterface;
	}

	/**
	 * Return the interface of the service to export.
	 */
	public Class<?> getServiceInterface() {
		return this.serviceInterface;
	}

	/**
	 * Set whether to register a RemoteInvocationTraceInterceptor for exported
	 * services. Only applied when a subclass uses {@code getProxyForService}
	 * for creating the proxy to expose.
	 * <p>Default is "true". RemoteInvocationTraceInterceptor's most important value
	 * is that it logs exception stacktraces on the server, before propagating an
	 * exception to the client. Note that RemoteInvocationTraceInterceptor will <i>not</i>
	 * be registered by default if the "interceptors" property has been specified.
	 * @see #setInterceptors
	 * @see #getProxyForService
	 * @see RemoteInvocationTraceInterceptor
	 */
	public void setRegisterTraceInterceptor(boolean registerTraceInterceptor) {
		this.registerTraceInterceptor = registerTraceInterceptor;
	}

	/**
	 * Set additional interceptors (or advisors) to be applied before the
	 * remote endpoint, e.g. a PerformanceMonitorInterceptor.
	 * <p>You may specify any AOP Alliance MethodInterceptors or other
	 * Spring AOP Advices, as well as Spring AOP Advisors.
	 * @see #getProxyForService
	 * @see org.springframework.aop.interceptor.PerformanceMonitorInterceptor
	 */
	public void setInterceptors(Object[] interceptors) {
		this.interceptors = interceptors;
	}


	/**
	 * Check whether the service reference has been set.
	 * @see #setService
	 */
	protected void checkService() throws IllegalArgumentException {
		Assert.notNull(getService(), "Property 'service' is required");
	}

	/**
	 * Check whether a service reference has been set,
	 * and whether it matches the specified service.
	 * @see #setServiceInterface
	 * @see #setService
	 */
	protected void checkServiceInterface() throws IllegalArgumentException {
		Class<?> serviceInterface = getServiceInterface();
		Assert.notNull(serviceInterface, "Property 'serviceInterface' is required");

		Object service = getService();
		if (service instanceof String) {
			throw new IllegalArgumentException("Service [" + service + "] is a String " +
					"rather than an actual service reference: Have you accidentally specified " +
					"the service bean name as value instead of as reference?");
		}
		if (!serviceInterface.isInstance(service)) {
			throw new IllegalArgumentException("Service interface [" + serviceInterface.getName() +
					"] needs to be implemented by service [" + service + "] of class [" +
					service.getClass().getName() + "]");
		}
	}

	/**
	 * Get a proxy for the given service object, implementing the specified
	 * service interface.
	 * <p>Used to export a proxy that does not expose any internals but just
	 * a specific interface intended for remote access. Furthermore, a
	 * {@link RemoteInvocationTraceInterceptor} will be registered (by default).
	 *
	 * 请求处理类的初始化主要处理规则为:如果配置的 service 属性对应的类实现了 Remote 接口且没有配置
	 * serviceInterface 属性，那么直接使用 service 作为处理类;否则，使用RMIInvocationWrapper对service
	 * 的代理类和当前类也就是 RMIServiceExporter 进行封装。经过这样的封装，客户端与服务端便可以达成一致协议，
	 * 当客户端检测到是 RMInvocationWrapper类型stub 的时候便会直接调用其 invoke 方法，
	 * 使得调用端与服务端很好地连接在了一起。而RMIInvocationWrapper 封装了用于处理请求的代理类，
	 * 在invoke 中便会使用代理类进行进一步处理。
	 *
	 * 之前的逻辑已经非常清楚了,当请求 RMI服务时会由注册表 Registry 实例将请求转向之前注册的处理类去处理。
	 * 也就是之前封装的RMIInvocationWrapper,然后由RMIInvocationWrapper中的invoke 方法进行处理，
	 * 那么为什么不是在 invoke 方法中直接使用 service，而是通过代理再次将service封装呢?
	 *
	 * 这其中的一个关键点是，在创建代理时添加了一个增强拦截器 RemoteInvocationTraceInterceptor,
	 * 目的是为了对方法调用进行打印跟踪，但是如果直接在 invoke 方法中硬编码这些日志，会使代码看起来很不优雅，
	 * 而且耦合度很高，使用代理的方式就会解决这样的问题，而且会有很高的可扩展性。
	 *
	 * @return the proxy
	 * @see #setServiceInterface
	 * @see #setRegisterTraceInterceptor
	 * @see RemoteInvocationTraceInterceptor
	 */
	protected Object getProxyForService() {
		// 验证service
		checkService();
		// 验证serviceInterface
		checkServiceInterface();
		// 使用 JDK 的方式创建代理
		ProxyFactory proxyFactory = new ProxyFactory();
		// 添加代理接口
		proxyFactory.addInterface(getServiceInterface());

		if (this.registerTraceInterceptor != null ? this.registerTraceInterceptor : this.interceptors == null) {
			// 加入代理的横切面 RemoteInvocationTraceInterceptor 记录 Exporter 名称
			proxyFactory.addAdvice(new RemoteInvocationTraceInterceptor(getExporterName()));
		}
		if (this.interceptors != null) {
			AdvisorAdapterRegistry adapterRegistry = GlobalAdvisorAdapterRegistry.getInstance();
			for (Object interceptor : this.interceptors) {
				proxyFactory.addAdvisor(adapterRegistry.wrap(interceptor));
			}
		}
		//设置要代理的目标类
		proxyFactory.setTarget(getService());
		proxyFactory.setOpaque(true);
		//创建代理
		return proxyFactory.getProxy(getBeanClassLoader());
	}

	/**
	 * Return a short name for this exporter.
	 * Used for tracing of remote invocations.
	 * <p>Default is the unqualified class name (without package).
	 * Can be overridden in subclasses.
	 * @see #getProxyForService
	 * @see RemoteInvocationTraceInterceptor
	 * @see org.springframework.util.ClassUtils#getShortName
	 */
	protected String getExporterName() {
		return ClassUtils.getShortName(getClass());
	}

}
