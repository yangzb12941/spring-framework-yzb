/*
 * Copyright 2002-2012 the original author or authors.
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

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import org.springframework.aop.support.AopUtils;
import org.springframework.lang.Nullable;
import org.springframework.remoting.RemoteConnectFailureException;
import org.springframework.remoting.RemoteInvocationFailureException;
import org.springframework.remoting.RemoteLookupFailureException;
import org.springframework.remoting.support.RemoteInvocationBasedAccessor;
import org.springframework.remoting.support.RemoteInvocationUtils;

/**
 * {@link org.aopalliance.intercept.MethodInterceptor} for accessing conventional
 * RMI services or RMI invokers. The service URL must be a valid RMI URL
 * (e.g. "rmi://localhost:1099/myservice").
 *
 * <p>RMI invokers work at the RmiInvocationHandler level, needing only one stub for
 * any service. Service interfaces do not have to extend {@code java.rmi.Remote}
 * or throw {@code java.rmi.RemoteException}. Spring's unchecked
 * RemoteAccessException will be thrown on remote invocation failure.
 * Of course, in and out parameters have to be serializable.
 *
 * <p>With conventional RMI services, this invoker is typically used with the RMI
 * service interface. Alternatively, this invoker can also proxy a remote RMI service
 * with a matching non-RMI business interface, i.e. an interface that mirrors the RMI
 * service methods but does not declare RemoteExceptions. In the latter case,
 * RemoteExceptions thrown by the RMI stub will automatically get converted to
 * Spring's unchecked RemoteAccessException.
 *
 * @author Juergen Hoeller
 * @since 29.09.2003
 * @see RmiServiceExporter
 * @see RmiProxyFactoryBean
 * @see RmiInvocationHandler
 * @see org.springframework.remoting.RemoteAccessException
 * @see java.rmi.RemoteException
 * @see java.rmi.Remote
 */
public class RmiClientInterceptor extends RemoteInvocationBasedAccessor
		implements MethodInterceptor {

	private boolean lookupStubOnStartup = true;

	private boolean cacheStub = true;

	private boolean refreshStubOnConnectFailure = false;

	private RMIClientSocketFactory registryClientSocketFactory;

	private Remote cachedStub;

	private final Object stubMonitor = new Object();


	/**
	 * Set whether to look up the RMI stub on startup. Default is "true".
	 * <p>Can be turned off to allow for late start of the RMI server.
	 * In this case, the RMI stub will be fetched on first access.
	 * @see #setCacheStub
	 */
	public void setLookupStubOnStartup(boolean lookupStubOnStartup) {
		this.lookupStubOnStartup = lookupStubOnStartup;
	}

	/**
	 * Set whether to cache the RMI stub once it has been located.
	 * Default is "true".
	 * <p>Can be turned off to allow for hot restart of the RMI server.
	 * In this case, the RMI stub will be fetched for each invocation.
	 * @see #setLookupStubOnStartup
	 */
	public void setCacheStub(boolean cacheStub) {
		this.cacheStub = cacheStub;
	}

	/**
	 * Set whether to refresh the RMI stub on connect failure.
	 * Default is "false".
	 * <p>Can be turned on to allow for hot restart of the RMI server.
	 * If a cached RMI stub throws an RMI exception that indicates a
	 * remote connect failure, a fresh proxy will be fetched and the
	 * invocation will be retried.
	 * @see java.rmi.ConnectException
	 * @see java.rmi.ConnectIOException
	 * @see java.rmi.NoSuchObjectException
	 */
	public void setRefreshStubOnConnectFailure(boolean refreshStubOnConnectFailure) {
		this.refreshStubOnConnectFailure = refreshStubOnConnectFailure;
	}

	/**
	 * Set a custom RMI client socket factory to use for accessing the RMI registry.
	 * @see java.rmi.server.RMIClientSocketFactory
	 * @see java.rmi.registry.LocateRegistry#getRegistry(String, int, RMIClientSocketFactory)
	 */
	public void setRegistryClientSocketFactory(RMIClientSocketFactory registryClientSocketFactory) {
		this.registryClientSocketFactory = registryClientSocketFactory;
	}

	//继续追踪代码，发现父类的父类，也就是 UrlBasedRemoteAccessor 中的 afterPropertiesSet
	//方法只完成了对 serviceUri 性的验证。
	//所以推断所有的客户端都应该在 prepare 方法中实现，继续查看 prepare（）
	@Override
	public void afterPropertiesSet() {
		super.afterPropertiesSet();
		prepare();
	}

	/**
	 * Fetches RMI stub on startup, if necessary.
	 *
	 * 从上面的代码中，我们了解到了一个很重要的属性 lookupStubOnStartup，如果将此属性设置为 true，
	 * 那么获取 stub 的工作就会在系统启动时被执行并缓存，从而提高使用时候的响应时间。
	 * 获取stub是RMI应用中的关键步骤，当然你可以使用两种方式进行使用自定义的套接字工厂。
	 * 如果使用这种方式，你需要在构造 Registry 实例时将自定义套接字工厂传入，并使用Registry 中提供的lookup
	 * 方法来获取对应的 stub。直接使用RMI提供的标准方法:Naming.lookup(getServiceUrl())。
	 *
	 * @throws RemoteLookupFailureException if RMI stub creation failed
	 * @see #setLookupStubOnStartup
	 * @see #lookupStub
	 */
	public void prepare() throws RemoteLookupFailureException {
		// Cache RMI stub on initialization?
		// 如果配置了 lookupStubOnStartup 属性便会在启动时寻找 stub
		if (this.lookupStubOnStartup) {
			Remote remoteObj = lookupStub();
			if (logger.isDebugEnabled()) {
				if (remoteObj instanceof RmiInvocationHandler) {
					logger.debug("RMI stub [" + getServiceUrl() + "] is an RMI invoker");
				}
				else if (getServiceInterface() != null) {
					boolean isImpl = getServiceInterface().isInstance(remoteObj);
					logger.debug("Using service interface [" + getServiceInterface().getName() +
						"] for RMI stub [" + getServiceUrl() + "] - " +
						(!isImpl ? "not " : "") + "directly implemented");
				}
			}
			if (this.cacheStub) {
				//将获取的stub缓存
				this.cachedStub = remoteObj;
			}
		}
	}

	/**
	 * Create the RMI stub, typically by looking it up.
	 * <p>Called on interceptor initialization if "cacheStub" is "true";
	 * else called for each invocation by {@link #getStub()}.
	 * <p>The default implementation looks up the service URL via
	 * {@code java.rmi.Naming}. This can be overridden in subclasses.
	 *
	 * 为了使用registryClientSocketFactory，代码量比使用RMI标准获取stub 方法多出了很多那么registryClientSocketFactory 到底是做什么用的呢?
	 * 与之前服务端的套接字工厂类似，这里的 registryClientSocketFactory 用来连接
	 * RMI服务器用户通过实现RMIClientSocketFactory 接口来控制用于连接的socket 的各种参数。
	 *
	 * @return the RMI stub to store in this interceptor
	 * @throws RemoteLookupFailureException if RMI stub creation failed
	 * @see #setCacheStub
	 * @see java.rmi.Naming#lookup
	 */
	protected Remote lookupStub() throws RemoteLookupFailureException {
		try {
			Remote stub = null;
			if (this.registryClientSocketFactory != null) {
				// RMIClientSocketFactory specified for registry access.
				// Unfortunately, due to RMI API limitations, this means
				// that we need to parse the RMI URL ourselves and perform
				// straight LocateRegistry.getRegistry/Registry.lookup calls.
				// 为注册表访问指定了RMIClientSocketFactory。不幸的是，由于RMI API的限制，
				// 这意味着我们需要自己解析RMI URL，并直接执行LocateRegistry.getRegistry.lookup调用。
				URL url = new URL(null, getServiceUrl(), new DummyURLStreamHandler());
				String protocol = url.getProtocol();
				// 验证传输协议
				if (protocol != null && !"rmi".equals(protocol)) {
					throw new MalformedURLException("Invalid URL scheme '" + protocol + "'");
				}
				//主机
				String host = url.getHost();
				//端口
				int port = url.getPort();
				//服务名
				String name = url.getPath();
				if (name != null && name.startsWith("/")) {
					name = name.substring(1);
				}
				Registry registry = LocateRegistry.getRegistry(host, port, this.registryClientSocketFactory);
				stub = registry.lookup(name);
			}
			else {
				// Can proceed with standard RMI lookup API...
				stub = Naming.lookup(getServiceUrl());
			}
			if (logger.isDebugEnabled()) {
				logger.debug("Located RMI stub with URL [" + getServiceUrl() + "]");
			}
			return stub;
		}
		catch (MalformedURLException ex) {
			throw new RemoteLookupFailureException("Service URL [" + getServiceUrl() + "] is invalid", ex);
		}
		catch (NotBoundException ex) {
			throw new RemoteLookupFailureException(
					"Could not find RMI service [" + getServiceUrl() + "] in RMI registry", ex);
		}
		catch (RemoteException ex) {
			throw new RemoteLookupFailureException("Lookup of RMI stub failed", ex);
		}
	}

	/**
	 * Return the RMI stub to use. Called for each invocation.
	 * <p>The default implementation returns the stub created on initialization,
	 * if any. Else, it invokes {@link #lookupStub} to get a new stub for
	 * each invocation. This can be overridden in subclasses, for example in
	 * order to cache a stub for a given amount of time before recreating it,
	 * or to test the stub whether it is still alive.
	 *
	 * 返回要使用的RMI存根。为每次调用调用默认实现返回初始化时创建的存根（如果有的话）。
	 * 否则，它会调用{@link lookupStub}来为每次调用获取一个新的存根。这可以在子类中重写，
	 * 例如，为了在重新创建存根之前将其缓存一段给定的时间，或者测试存根是否仍然有效。
	 *
	 * 当客户端使用接口进行方法调用时是通过 RMI获取 stub 的，然后再通过 stub中封装的信息进行
	 * 服务器的调用，这个 stu 就是在构建服务器时发布的对象，那么，客户端调用时最关键的一步也是进行 stub 的获取了。
	 *
	 * @return the RMI stub to use for an invocation
	 * @throws RemoteLookupFailureException if RMI stub creation failed
	 * @see #lookupStub
	 */
	protected Remote getStub() throws RemoteLookupFailureException {
		//如果有缓存直接使用缓存
		if (!this.cacheStub || (this.lookupStubOnStartup && !this.refreshStubOnConnectFailure)) {
			return (this.cachedStub != null ? this.cachedStub : lookupStub());
		}
		else {
			synchronized (this.stubMonitor) {
				if (this.cachedStub == null) {
					this.cachedStub = lookupStub();
				}
				return this.cachedStub;
			}
		}
	}


	/**
	 * Fetches an RMI stub and delegates to {@code doInvoke}.
	 * If configured to refresh on connect failure, it will call
	 * {@link #refreshAndRetry} on corresponding RMI exceptions.
	 *
	 * 之前分析了类型为RMIProxyFactoryBean的bean的初始化中完成的逻辑操作。
	 * 在初始化时创建了代理并将本身作为增强器加入了代理中(RMIProxyFactoryBean
	 * 间接实现了MethodInterceptor）。
	 * 那么这样一来，当在客户端调用代理的接口中的某个方法时，就会首先执行RMIProxyFactoryBean中的invoke方法进行增强。
	 *
	 * 当获取到stub 后便可以进行远程方法的调用了。Spring中对于远程方法的调用其实是分两种情况考虑的。
	 *
	 * 1、获取的 stub是RMIInvocationHandler 类型的，从服务端获取的stub是RMIInvocationHandler，就意味着服务端也同样使用了
	 * Spring 去构建，那么自然会使用 Spring 中作的约定，进行客户端调用处理。Spring 中的处理方式被委托给了doInvoke 方法。
	 *
	 * 2、当获取的stub不是RMIInvocationHandler 类型,
	 * 那么服务端构建RMI服务可能是通过普通的方法或者借助于 Spring 外的第三方插件，那么处理方式自然会按照 RMI 中普通的处理方式进行，
	 * 而这种普通的处理方式无非是反射。因为在 invocation 中包含了所需要调用的方法的各种信息，包括方法名称以及参数等，
	 * 而调用的实体正是 stub那么通过反射方法完全可以激活 stub 中的远程调用。
	 *
	 * @see #getStub
	 * @see #doInvoke(MethodInvocation, Remote)
	 * @see #refreshAndRetry
	 * @see java.rmi.ConnectException
	 * @see java.rmi.ConnectIOException
	 * @see java.rmi.NoSuchObjectException
	 */
	@Override
	public Object invoke(MethodInvocation invocation) throws Throwable {
		// 获取的服务器中对应的注册的 remote 对象，通过序列化传输
		Remote stub = getStub();
		try {
			return doInvoke(invocation, stub);
		}
		catch (RemoteConnectFailureException ex) {
			return handleRemoteConnectFailure(invocation, ex);
		}
		catch (RemoteException ex) {
			if (isConnectFailure(ex)) {
				return handleRemoteConnectFailure(invocation, ex);
			}
			else {
				throw ex;
			}
		}
	}

	/**
	 * Determine whether the given RMI exception indicates a connect failure.
	 * <p>The default implementation delegates to
	 * {@link RmiClientInterceptorUtils#isConnectFailure}.
	 * @param ex the RMI exception to check
	 * @return whether the exception should be treated as connect failure
	 */
	protected boolean isConnectFailure(RemoteException ex) {
		return RmiClientInterceptorUtils.isConnectFailure(ex);
	}

	/**
	 * Refresh the stub and retry the remote invocation if necessary.
	 * <p>If not configured to refresh on connect failure, this method
	 * simply rethrows the original exception.
	 * @param invocation the invocation that failed
	 * @param ex the exception raised on remote invocation
	 * @return the result value of the new invocation, if succeeded
	 * @throws Throwable an exception raised by the new invocation,
	 * if it failed as well
	 * @see #setRefreshStubOnConnectFailure
	 * @see #doInvoke
	 */
	@Nullable
	private Object handleRemoteConnectFailure(MethodInvocation invocation, Exception ex) throws Throwable {
		if (this.refreshStubOnConnectFailure) {
			String msg = "Could not connect to RMI service [" + getServiceUrl() + "] - retrying";
			if (logger.isDebugEnabled()) {
				logger.warn(msg, ex);
			}
			else if (logger.isWarnEnabled()) {
				logger.warn(msg);
			}
			return refreshAndRetry(invocation);
		}
		else {
			throw ex;
		}
	}

	/**
	 * Refresh the RMI stub and retry the given invocation.
	 * Called by invoke on connect failure.
	 * @param invocation the AOP method invocation
	 * @return the invocation result, if any
	 * @throws Throwable in case of invocation failure
	 * @see #invoke
	 */
	@Nullable
	protected Object refreshAndRetry(MethodInvocation invocation) throws Throwable {
		Remote freshStub = null;
		synchronized (this.stubMonitor) {
			this.cachedStub = null;
			freshStub = lookupStub();
			if (this.cacheStub) {
				this.cachedStub = freshStub;
			}
		}
		return doInvoke(invocation, freshStub);
	}

	/**
	 * Perform the given invocation on the given RMI stub.
	 * @param invocation the AOP method invocation
	 * @param stub the RMI stub to invoke
	 * @return the invocation result, if any
	 * @throws Throwable in case of invocation failure
	 */
	@Nullable
	protected Object doInvoke(MethodInvocation invocation, Remote stub) throws Throwable {
		if (stub instanceof RmiInvocationHandler) {
			// RMI invoker
			try {
				return doInvoke(invocation, (RmiInvocationHandler) stub);
			}
			catch (RemoteException ex) {
				throw RmiClientInterceptorUtils.convertRmiAccessException(
					invocation.getMethod(), ex, isConnectFailure(ex), getServiceUrl());
			}
			catch (InvocationTargetException ex) {
				Throwable exToThrow = ex.getTargetException();
				RemoteInvocationUtils.fillInClientStackTraceIfPossible(exToThrow);
				throw exToThrow;
			}
			catch (Throwable ex) {
				throw new RemoteInvocationFailureException("Invocation of method [" + invocation.getMethod() +
						"] failed in RMI service [" + getServiceUrl() + "]", ex);
			}
		}
		else {
			// traditional RMI stub
			try {
				return RmiClientInterceptorUtils.invokeRemoteMethod(invocation, stub);
			}
			catch (InvocationTargetException ex) {
				Throwable targetEx = ex.getTargetException();
				if (targetEx instanceof RemoteException) {
					RemoteException rex = (RemoteException) targetEx;
					throw RmiClientInterceptorUtils.convertRmiAccessException(
							invocation.getMethod(), rex, isConnectFailure(rex), getServiceUrl());
				}
				else {
					throw targetEx;
				}
			}
		}
	}

	/**
	 * Apply the given AOP method invocation to the given {@link RmiInvocationHandler}.
	 * <p>The default implementation delegates to {@link #createRemoteInvocation}.
	 *
	 * 之前反复提到了 Spring 中的客户端处理 RMI的方式。其实，在分析服务端发布 RMI的方式时，
	 * 我们已经了解到，Spring 将 RMI的导出Object 封装成了 RMIInvocationHandler 类型进行发布,
	 * 那么当客户端获取stub的时候是含了远程连接信息代理类的RMIInvocationHandler.
	 *
	 * 也就是说当调用RMIInvocationHandler中的方法时会使用RMI中提供的代理进行远程连接而此时
	 * Spring中要做的就是将代码引向RMIInvocationHandler 接口的invoke方法的调用。
	 *
	 * @param methodInvocation the current AOP method invocation
	 * @param invocationHandler the RmiInvocationHandler to apply the invocation to
	 * @return the invocation result
	 * @throws RemoteException in case of communication errors
	 * @throws NoSuchMethodException if the method name could not be resolved
	 * @throws IllegalAccessException if the method could not be accessed
	 * @throws InvocationTargetException if the method invocation resulted in an exception
	 * @see org.springframework.remoting.support.RemoteInvocation
	 */
	@Nullable
	protected Object doInvoke(MethodInvocation methodInvocation, RmiInvocationHandler invocationHandler)
		throws RemoteException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {

		if (AopUtils.isToStringMethod(methodInvocation.getMethod())) {
			return "RMI invoker proxy for service URL [" + getServiceUrl() + "]";
		}
		// 将 methodInvocation 中的方法名及参数等信息重新封装到 RemoteInvocation，并通过远程代理
		// 方法直接调用
		return invocationHandler.invoke(createRemoteInvocation(methodInvocation));
	}


	/**
	 * Dummy URLStreamHandler that's just specified to suppress the standard
	 * {@code java.net.URL} URLStreamHandler lookup, to be able to
	 * use the standard URL class for parsing "rmi:..." URLs.
	 */
	private static class DummyURLStreamHandler extends URLStreamHandler {

		@Override
		protected URLConnection openConnection(URL url) throws IOException {
			throw new UnsupportedOperationException();
		}
	}

}
