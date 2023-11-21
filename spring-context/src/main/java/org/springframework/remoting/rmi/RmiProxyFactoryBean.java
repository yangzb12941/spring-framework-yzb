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

package org.springframework.remoting.rmi;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.util.Assert;

/**
 * {@link FactoryBean} for RMI proxies, supporting both conventional RMI services
 * and RMI invokers. Exposes the proxied service for use as a bean reference,
 * using the specified service interface. Proxies will throw Spring's unchecked
 * RemoteAccessException on remote invocation failure instead of RMI's RemoteException.
 *
 * <p>The service URL must be a valid RMI URL like "rmi://localhost:1099/myservice".
 * RMI invokers work at the RmiInvocationHandler level, using the same invoker stub
 * for any service. Service interfaces do not have to extend {@code java.rmi.Remote}
 * or throw {@code java.rmi.RemoteException}. Of course, in and out parameters
 * have to be serializable.
 *
 * <p>With conventional RMI services, this proxy factory is typically used with the
 * RMI service interface. Alternatively, this factory can also proxy a remote RMI
 * service with a matching non-RMI business interface, i.e. an interface that mirrors
 * the RMI service methods but does not declare RemoteExceptions. In the latter case,
 * RemoteExceptions thrown by the RMI stub will automatically get converted to
 * Spring's unchecked RemoteAccessException.
 *
 * <p>The major advantage of RMI, compared to Hessian, is serialization.
 * Effectively, any serializable Java object can be transported without hassle.
 * Hessian has its own (de-)serialization mechanisms, but is HTTP-based and thus
 * much easier to setup than RMI. Alternatively, consider Spring's HTTP invoker
 * to combine Java serialization with HTTP-based transport.
 *
 * ｛@link FactoryBean｝用于RMI代理，同时支持传统的RMI服务和RMI调用程序。
 * 使用指定的服务接口公开代理服务以用作bean引用。代理将在远程调用失败时抛出Spring未检查的RemoteAccessException，
 * 而不是RMI的RemoteException服务URL必须是有效的RMI URL，如"RMI:localhost:1009/myService"。
 * RMI调用程序在RmiInvocationHandler级别工作，对任何服务使用相同的调用程序存根。服务接口不必扩展{@codejava.rmi.Remote}
 * 或抛出{@codejava.rmi.RemoteException}。
 *
 * 当然，输入和输出参数必须是可序列化的对于传统的RMI服务，这个代理工厂通常与RMI服务接口一起使用。或者，该工厂还可以使用匹配的
 * 非RMI业务接口代理远程RMI服务，即镜像RMI服务方法。但不声明RemoteExceptions的接口。
 * 在后一种情况下，RMI存根抛出的RemoteException将自动转换为Spring未检查的RemoteAccessException
 * 与Hessian相比，RMI的主要优势是序列化。实际上，任何可串行化的Java对象都可以顺利传输。Hessian有自己的（反）序列化机制，
 * 但它是基于HTTP的，因此比RMI更容易设置。或者，考虑Spring的HTTP调用程序，将Java序列化与基于HTTP的传输相结合。
 *
 * 提取出该类实现的比较重要的接口 InitializingBean、BeanClassLoaderAware 以及 MethodInterceptor。
 *
 * @author Juergen Hoeller
 * @since 13.05.2003
 * @see #setServiceInterface
 * @see #setServiceUrl
 * @see RmiClientInterceptor
 * @see RmiServiceExporter
 * @see java.rmi.Remote
 * @see java.rmi.RemoteException
 * @see org.springframework.remoting.RemoteAccessException
 * @see org.springframework.remoting.caucho.HessianProxyFactoryBean
 * @see org.springframework.remoting.httpinvoker.HttpInvokerProxyFactoryBean
 */
public class RmiProxyFactoryBean extends RmiClientInterceptor implements FactoryBean<Object>, BeanClassLoaderAware {

	private Object serviceProxy;

	// 这样，我们似乎已近形成了一个大致的轮廓，当获取该 bean 时，首先通过afterPropertiesSet创建代理类，
	// 并使用当前类作为增强方法，而在调用该 bean 时其实返回的是代理类，既然调用的是代理类，那么又会使用当前 bean 作为增强器进行增强，
	// 也就是说会调用 RMIProxyFactoryBean的父类RMIClientInterceptor的invoke方法。
	@Override
	public void afterPropertiesSet() {
		super.afterPropertiesSet();
		Class<?> ifc = getServiceInterface();
		Assert.notNull(ifc, "Property 'serviceInterface' is required");
		// 根据设置的接口创建代理，并使用当前类 this 作为增强器
		this.serviceProxy = new ProxyFactory(ifc, this).getProxy(getBeanClassLoader());
	}

	//同时， RMIProxyFactoryBean 又实现 FactoryBean 接口，那么当获取 bean 并不是直接
	//获取 bean ，而是获取该 bean 的 getObject 方法
	@Override
	public Object getObject() {
		return this.serviceProxy;
	}

	@Override
	public Class<?> getObjectType() {
		return getServiceInterface();
	}

	@Override
	public boolean isSingleton() {
		return true;
	}

}
