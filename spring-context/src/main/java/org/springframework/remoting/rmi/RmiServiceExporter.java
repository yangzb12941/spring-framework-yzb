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

package org.springframework.remoting.rmi;

import java.rmi.AlreadyBoundException;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.lang.Nullable;

/**
 * RMI exporter that exposes the specified service as RMI object with the specified name.
 * Such services can be accessed via plain RMI or via {@link RmiProxyFactoryBean}.
 * Also supports exposing any non-RMI service via RMI invokers, to be accessed via
 * {@link RmiClientInterceptor} / {@link RmiProxyFactoryBean}'s automatic detection
 * of such invokers.
 *
 * <p>With an RMI invoker, RMI communication works on the {@link RmiInvocationHandler}
 * level, needing only one stub for any service. Service interfaces do not have to
 * extend {@code java.rmi.Remote} or throw {@code java.rmi.RemoteException}
 * on all methods, but in and out parameters have to be serializable.
 *
 * <p>The major advantage of RMI, compared to Hessian, is serialization.
 * Effectively, any serializable Java object can be transported without hassle.
 * Hessian has its own (de-)serialization mechanisms, but is HTTP-based and thus
 * much easier to setup than RMI. Alternatively, consider Spring's HTTP invoker
 * to combine Java serialization with HTTP-based transport.
 *
 * <p>Note: RMI makes a best-effort attempt to obtain the fully qualified host name.
 * If one cannot be determined, it will fall back and use the IP address. Depending
 * on your network configuration, in some cases it will resolve the IP to the loopback
 * address. To ensure that RMI will use the host name bound to the correct network
 * interface, you should pass the {@code java.rmi.server.hostname} property to the
 * JVM that will export the registry and/or the service using the "-D" JVM argument.
 * For example: {@code -Djava.rmi.server.hostname=myserver.com}
 *
 * 使用示例
 * 1、建立RMI对外接口
 * public interface Hello RMIService{
 *     public int getAdd(int a,int b);
 * }
 *
 * 2、建立接口实现类
 * public class HelloRMIServiceImpl implements HelloRMIService{
 *     public int getAdd(int a,int b){
 *         return a+b;
 *     }
 * }
 *
 * 3、建立服务段配置文件
 * <!-- 服务端-->
 * <bean id="helloRMIServiceImpl" class="test.remote.HelloRMIServiceImpl"/>
 * <!-- 将类为一个RMI服务 -->
 * <bean id="myRMI" class="org.Springframework.remoting.RMI.RMIServiceExporter">
 *   <!-- 服务类 -->
 *   <property name="service" ref="helloRMIServiceImpl"/>
 *   <!-- 服务名 -->
 *   <property name="serviceName" ref="helloRMI"/>
 *   <!-- 服务接口 -->
 *   <property name="serviceInterface" value="test.remote.HelloRMIService"/>
 *   <!-- 服务端口 -->
 *   <property name="registryPort" value="9999"/>
 * </bean>
 *
 * 4、建立服务端测试
 * public class ServerTest{
 *     public static void main(String[] args){
 *         new ClassPathXmlApplicationContext("test/remote/RMIServer.xml");
 *     }
 * }
 *
 * 5、建立测试端配置文件
 * <!-- 客户端 -->
 * <bean id="myClient" class="org.Springframework.remoting.RMI.RMIProxyFactoryBean">
 *  <property name=”serviceUrl” value=”RMI://127.0.0.1:9999/helloRMI"/>
 *  <property name=”serviceInterface" value="test.remote.HelloRMIService"/>
 * </bean>
 *
 * 6、编写测试代码
 * public class ClientTest{
 *     public static void main(String[] args){
 *        ApplicationContext context = new ClassPathXmlApplicationContext ("test/remote/IRMIClient.xml");
 *        HelloRMIService hms = context.getBean("myClient", HelloRMIService.class);
 *        System.out.println(hms.getAdd(1,2));
 *     }
 * }
 * RMIServiceExporter 实现了 Spring 中几个比较敏感的接口: BeanClassLoaderAware、DisposableBean、InitializingBean，
 * 其中，
 * DisposableBean 接口保证在实现该接口的 bean 销毁时调用其destroy 方法，
 * BeanClassLoaderAware 接口保证在实现该接口的 bean 的初始化时调用其setBeanClassLoader 方法，
 * 而InitializingBean 接口则是保证在实现该接口的 bean初始化时调用其 afterPropertiesSet 方法，
 * 所以我们推断 RMIServiceExporter 的初始化函数入口一定在其afterPropertiesSet 或者 setBeanClassLoader 方法中。
 * 经过查看代码，确认 afterPropertiesSet 为RMIServiceExporter功能的初始化入口。
 *
 * 果然，在 afterPropertiesSet 函数中将实现委托给了prepare，而在 prepare 方法中我们找到了RMI服务发布的功能实现，
 * 同时，我们也大致清楚了 RMI服务发布的流程。
 * 1.验证 service。
 * 此处的service对应的是配置中类型为RMIServiceExporter 的 service 属性，它是实现类并不是接口。
 * 尽管后期会对RMIServiceExporter 做一系列的封装，但是，无论怎么封装，最终还是会将逻辑引向至RMIServiceExporter 来处理。
 * 所以，在发布之前需要进行验证。
 * 2.处理用户自定义的 SocketFactory 属性。在RMIServiceExporter 中提供了4 个套接字工厂配置,分别是
 * clientSocketFactory、serverSocketFactory 和 registryClientSocketFactory、registryServerSocketFactory。
 * 那么这两对配置又有什么区别或者说分别是应用在什么样的不同场景呢?
 * registryClientSocketFactory 与 registryServerSocketFactory 用于主机与RMI服务器之间连接的创建，
 * 也就是当使用 LocateRegistry.createRegistry(registryPort, clientSocketFactory，serverSocketFactory)
 * 方法创建 Registry 实例时会在RMI主机使用serverSocketFactory创建套接字等待连接，
 * 而服务端与RMI主机通信时会使用clientSocketFactory 创建连接套接字。
 *
 * clientSocketFactory、serverSocketFactory 同样是创建套接字，但是使用的位置不同。
 * clientSocketFactory、serverSocketFactory 用于导出远程对象，
 * serverSocketFactory 用于在服务端建立套接字等待客户端连接，而clientSocketFactory 用于调用端建立套接字发起连接。
 *
 * 3.根据配置参数获取Registry。
 *
 * 4.构造对外发布的实例。
 * 构建对外发布的实例，当外界通过注册的服务名调用响应的方法时，RMI服务会将请求引入此类来处理。
 *
 * 5.发布实例。
 * 在发布 RMI服务的流程中，有几个步骤可能是我们比较关心的。
 *
 *
 * @author Juergen Hoeller
 * @since 13.05.2003
 * @see RmiClientInterceptor
 * @see RmiProxyFactoryBean
 * @see java.rmi.Remote
 * @see java.rmi.RemoteException
 * @see org.springframework.remoting.caucho.HessianServiceExporter
 * @see org.springframework.remoting.httpinvoker.HttpInvokerServiceExporter
 */
public class RmiServiceExporter extends RmiBasedExporter implements InitializingBean, DisposableBean {

	private String serviceName;

	private int servicePort = 0;  // anonymous port

	private RMIClientSocketFactory clientSocketFactory;

	private RMIServerSocketFactory serverSocketFactory;

	private Registry registry;

	private String registryHost;

	private int registryPort = Registry.REGISTRY_PORT;

	private RMIClientSocketFactory registryClientSocketFactory;

	private RMIServerSocketFactory registryServerSocketFactory;

	private boolean alwaysCreateRegistry = false;

	private boolean replaceExistingBinding = true;

	private Remote exportedObject;

	private boolean createdRegistry = false;


	/**
	 * Set the name of the exported RMI service,
	 * i.e. {@code rmi://host:port/NAME}
	 */
	public void setServiceName(String serviceName) {
		this.serviceName = serviceName;
	}

	/**
	 * Set the port that the exported RMI service will use.
	 * <p>Default is 0 (anonymous port).
	 */
	public void setServicePort(int servicePort) {
		this.servicePort = servicePort;
	}

	/**
	 * Set a custom RMI client socket factory to use for exporting the service.
	 * <p>If the given object also implements {@code java.rmi.server.RMIServerSocketFactory},
	 * it will automatically be registered as server socket factory too.
	 * @see #setServerSocketFactory
	 * @see java.rmi.server.RMIClientSocketFactory
	 * @see java.rmi.server.RMIServerSocketFactory
	 * @see UnicastRemoteObject#exportObject(Remote, int, RMIClientSocketFactory, RMIServerSocketFactory)
	 */
	public void setClientSocketFactory(RMIClientSocketFactory clientSocketFactory) {
		this.clientSocketFactory = clientSocketFactory;
	}

	/**
	 * Set a custom RMI server socket factory to use for exporting the service.
	 * <p>Only needs to be specified when the client socket factory does not
	 * implement {@code java.rmi.server.RMIServerSocketFactory} already.
	 * @see #setClientSocketFactory
	 * @see java.rmi.server.RMIClientSocketFactory
	 * @see java.rmi.server.RMIServerSocketFactory
	 * @see UnicastRemoteObject#exportObject(Remote, int, RMIClientSocketFactory, RMIServerSocketFactory)
	 */
	public void setServerSocketFactory(RMIServerSocketFactory serverSocketFactory) {
		this.serverSocketFactory = serverSocketFactory;
	}

	/**
	 * Specify the RMI registry to register the exported service with.
	 * Typically used in combination with RmiRegistryFactoryBean.
	 * <p>Alternatively, you can specify all registry properties locally.
	 * This exporter will then try to locate the specified registry,
	 * automatically creating a new local one if appropriate.
	 * <p>Default is a local registry at the default port (1099),
	 * created on the fly if necessary.
	 * @see RmiRegistryFactoryBean
	 * @see #setRegistryHost
	 * @see #setRegistryPort
	 * @see #setRegistryClientSocketFactory
	 * @see #setRegistryServerSocketFactory
	 */
	public void setRegistry(Registry registry) {
		this.registry = registry;
	}

	/**
	 * Set the host of the registry for the exported RMI service,
	 * i.e. {@code rmi://HOST:port/name}
	 * <p>Default is localhost.
	 */
	public void setRegistryHost(String registryHost) {
		this.registryHost = registryHost;
	}

	/**
	 * Set the port of the registry for the exported RMI service,
	 * i.e. {@code rmi://host:PORT/name}
	 * <p>Default is {@code Registry.REGISTRY_PORT} (1099).
	 * @see java.rmi.registry.Registry#REGISTRY_PORT
	 */
	public void setRegistryPort(int registryPort) {
		this.registryPort = registryPort;
	}

	/**
	 * Set a custom RMI client socket factory to use for the RMI registry.
	 * <p>If the given object also implements {@code java.rmi.server.RMIServerSocketFactory},
	 * it will automatically be registered as server socket factory too.
	 * @see #setRegistryServerSocketFactory
	 * @see java.rmi.server.RMIClientSocketFactory
	 * @see java.rmi.server.RMIServerSocketFactory
	 * @see LocateRegistry#getRegistry(String, int, RMIClientSocketFactory)
	 */
	public void setRegistryClientSocketFactory(RMIClientSocketFactory registryClientSocketFactory) {
		this.registryClientSocketFactory = registryClientSocketFactory;
	}

	/**
	 * Set a custom RMI server socket factory to use for the RMI registry.
	 * <p>Only needs to be specified when the client socket factory does not
	 * implement {@code java.rmi.server.RMIServerSocketFactory} already.
	 * @see #setRegistryClientSocketFactory
	 * @see java.rmi.server.RMIClientSocketFactory
	 * @see java.rmi.server.RMIServerSocketFactory
	 * @see LocateRegistry#createRegistry(int, RMIClientSocketFactory, RMIServerSocketFactory)
	 */
	public void setRegistryServerSocketFactory(RMIServerSocketFactory registryServerSocketFactory) {
		this.registryServerSocketFactory = registryServerSocketFactory;
	}

	/**
	 * Set whether to always create the registry in-process,
	 * not attempting to locate an existing registry at the specified port.
	 * <p>Default is "false". Switch this flag to "true" in order to avoid
	 * the overhead of locating an existing registry when you always
	 * intend to create a new registry in any case.
	 */
	public void setAlwaysCreateRegistry(boolean alwaysCreateRegistry) {
		this.alwaysCreateRegistry = alwaysCreateRegistry;
	}

	/**
	 * Set whether to replace an existing binding in the RMI registry,
	 * that is, whether to simply override an existing binding with the
	 * specified service in case of a naming conflict in the registry.
	 * <p>Default is "true", assuming that an existing binding for this
	 * exporter's service name is an accidental leftover from a previous
	 * execution. Switch this to "false" to make the exporter fail in such
	 * a scenario, indicating that there was already an RMI object bound.
	 */
	public void setReplaceExistingBinding(boolean replaceExistingBinding) {
		this.replaceExistingBinding = replaceExistingBinding;
	}

	/**
	 * afterPropertiesSet 为RMIServiceExporter功能的初始化入口。
	 * @throws RemoteException
	 */
	@Override
	public void afterPropertiesSet() throws RemoteException {
		prepare();
	}

	/**
	 * Initialize this service exporter, registering the service as RMI object.
	 * <p>Creates an RMI registry on the specified port if none exists.
	 * @throws RemoteException if service registration failed
	 */
	public void prepare() throws RemoteException {
		//检查验证service
		checkService();

		if (this.serviceName == null) {
			throw new IllegalArgumentException("Property 'serviceName' is required");
		}
		/**
		 * 如果用户在配置文件中配置了 clientSocketFactory 或serverSocketFactory的处理
		 * 如果配置文件中的 clientSocketFactory 同时又实现了RMIServerSocketFactory接口
		 * 那么会忽略配置中的serverSocketFactory而使用clientSocketFactory代替
		 */
		// Check socket factories for exported object.
		if (this.clientSocketFactory instanceof RMIServerSocketFactory) {
			this.serverSocketFactory = (RMIServerSocketFactory) this.clientSocketFactory;
		}
		// clientSocketFactory 和 serverSocketFactory 要么同时出现要么都不出现
		if ((this.clientSocketFactory != null && this.serverSocketFactory == null) ||
				(this.clientSocketFactory == null && this.serverSocketFactory != null)) {
			throw new IllegalArgumentException(
					"Both RMIClientSocketFactory and RMIServerSocketFactory or none required");
		}

		// Check socket factories for RMI registry.
		// 如果配置中的 registryClientSocketFactory 同时实现了 RMIServerSocketFactory 接口那么
		// 会忽略配置中的 registryServerSocketFactory 而使用 registryClientSocketFactory代替
		if (this.registryClientSocketFactory instanceof RMIServerSocketFactory) {
			this.registryServerSocketFactory = (RMIServerSocketFactory) this.registryClientSocketFactory;
		}
		//不允许出现只配置 registryServerSocketFactory 却没有配置 registryClientSocketFactory情况出现
		if (this.registryClientSocketFactory == null && this.registryServerSocketFactory != null) {
			throw new IllegalArgumentException(
					"RMIServerSocketFactory without RMIClientSocketFactory for registry not supported");
		}

		this.createdRegistry = false;

		// Determine RMI registry to use.
		// 确定RMI registry
		if (this.registry == null) {
			this.registry = getRegistry(this.registryHost, this.registryPort,
				this.registryClientSocketFactory, this.registryServerSocketFactory);
			this.createdRegistry = true;
		}

		// Initialize and cache exported object.
		// 初始化以及缓存导出的 Object
		// 此时通常情况下是使 RMIInvocationWrapper 封装的 JDK 代理 ，切面为 remoteInvocationTraceInterceptor
		this.exportedObject = getObjectToExport();

		if (logger.isDebugEnabled()) {
			logger.debug("Binding service '" + this.serviceName + "' to RMI registry: " + this.registry);
		}

		// Export RMI object.
		if (this.clientSocketFactory != null) {
			// 使用由给定的套接字工厂指定的传送方式导Ill 远程对象，以便能够接收传入的调用
			// clientSocketFactory ：进行远程对象词用的客户端套接字工厂
			// serverSocketFactory : 接收远程调用的服务端套接字工厂
			UnicastRemoteObject.exportObject(
					this.exportedObject, this.servicePort, this.clientSocketFactory, this.serverSocketFactory);
		}
		else {
			// 导出remote object ，以使它能接收特定端口的调用
			UnicastRemoteObject.exportObject(this.exportedObject, this.servicePort);
		}

		// Bind RMI object to registry.
		try {
			if (this.replaceExistingBinding) {
				this.registry.rebind(this.serviceName, this.exportedObject);
			}
			else {
				// 绑定服务名称到 remote object 外界调用 serviceName 时候会被 exportedObject接收
				this.registry.bind(this.serviceName, this.exportedObject);
			}
		}
		catch (AlreadyBoundException ex) {
			// Already an RMI object bound for the specified service name...
			// 已经有一个绑定到指定服务名称的RMI对象。。。
			unexportObjectSilently();
			throw new IllegalStateException(
					"Already an RMI object bound for name '"  + this.serviceName + "': " + ex.toString());
		}
		catch (RemoteException ex) {
			// Registry binding failed: let's unexport the RMI object as well.
			unexportObjectSilently();
			throw ex;
		}
	}


	/**
	 * Locate or create the RMI registry for this exporter.
	 *
	 * 对RMI稍有了解就会知道，由于底层的封装，获取 Registry 实例是非常简单的，只需要使个函数 LocateRegistry.createRegistry(..)创建
	 * Registry 实例就可以了。但是，Spring 中并没有这么做，而是考虑得更多，比如 RMI 注册主机与发布的服务并不在一台机器上，
	 * 那么需要使用LocateRegistry.getRegistry(registryHost,registryPort,clientSocketFactory)去远程获取 Registry 实例。
	 *
	 * @param registryHost the registry host to use (if this is specified,
	 * no implicit creation of a RMI registry will happen)
	 * @param registryPort the registry port to use
	 * @param clientSocketFactory the RMI client socket factory for the registry (if any)
	 * @param serverSocketFactory the RMI server socket factory for the registry (if any)
	 * @return the RMI registry
	 * @throws RemoteException if the registry couldn't be located or created
	 */
	protected Registry getRegistry(String registryHost, int registryPort,
			@Nullable RMIClientSocketFactory clientSocketFactory, @Nullable RMIServerSocketFactory serverSocketFactory)
			throws RemoteException {

		if (registryHost != null) {
			// Host explicitly specified: only lookup possible.
			// 远程连接测试
			if (logger.isDebugEnabled()) {
				logger.debug("Looking for RMI registry at port '" + registryPort + "' of host [" + registryHost + "]");
			}
			// 如果 registryHost 尝试获取对应主机的 Registry
			Registry reg = LocateRegistry.getRegistry(registryHost, registryPort, clientSocketFactory);
			testRegistry(reg);
			return reg;
		}
		else {
			// 获取本机的 Registry
			return getRegistry(registryPort, clientSocketFactory, serverSocketFactory);
		}
	}

	/**
	 * Locate or create the RMI registry for this exporter.
	 * @param registryPort the registry port to use
	 * @param clientSocketFactory the RMI client socket factory for the registry (if any)
	 * @param serverSocketFactory the RMI server socket factory for the registry (if any)
	 * @return the RMI registry
	 * @throws RemoteException if the registry couldn't be located or created
	 */
	protected Registry getRegistry(int registryPort,
			@Nullable RMIClientSocketFactory clientSocketFactory, @Nullable RMIServerSocketFactory serverSocketFactory)
			throws RemoteException {

		if (clientSocketFactory != null) {
			if (this.alwaysCreateRegistry) {
				logger.debug("Creating new RMI registry");
				// 使用 clientSocketFactory 创建 Registry
				return LocateRegistry.createRegistry(registryPort, clientSocketFactory, serverSocketFactory);
			}
			if (logger.isDebugEnabled()) {
				logger.debug("Looking for RMI registry at port '" + registryPort + "', using custom socket factory");
			}
			synchronized (LocateRegistry.class) {
				try {
					// Retrieve existing registry. 检索现有注册表。
					// 复用测试
					// 使用给定的主机、端口和客户端套接字工厂为注册表创建一个代理。如果提供的客户端套接字工厂为null，
					// 则ref类型为UnicastRef，否则ref类型为UnicastRef2。如果属性java.rmi.server.ignoreStubClasses为true，
					// 则返回的代理是实现Registry接口的动态代理类的实例；否则，返回的代理是RegistryImpl的预生成存根类的实例。
					Registry reg = LocateRegistry.getRegistry(null, registryPort, clientSocketFactory);
					testRegistry(reg);
					return reg;
				}
				catch (RemoteException ex) {
					logger.trace("RMI registry access threw exception", ex);
					logger.debug("Could not detect RMI registry - creating new one");
					// Assume no registry found -> create new one.
					// 假设没有找到注册表->创建新的注册表。
					return LocateRegistry.createRegistry(registryPort, clientSocketFactory, serverSocketFactory);
				}
			}
		}

		else {
			return getRegistry(registryPort);
		}
	}

	/**
	 * Locate or create the RMI registry for this exporter.
	 * @param registryPort the registry port to use
	 * @return the RMI registry
	 * @throws RemoteException if the registry couldn't be located or created
	 */
	protected Registry getRegistry(int registryPort) throws RemoteException {
		if (this.alwaysCreateRegistry) {
			logger.debug("Creating new RMI registry");
			return LocateRegistry.createRegistry(registryPort);
		}
		if (logger.isDebugEnabled()) {
			logger.debug("Looking for RMI registry at port '" + registryPort + "'");
		}
		synchronized (LocateRegistry.class) {
			try {
				// Retrieve existing registry.
				// 查看对应当前 registryPort的Registry 是否已经创建，如果创建直接使用
				Registry reg = LocateRegistry.getRegistry(registryPort);
				// 测试是否可用，如果不可用则抛出异常
				testRegistry(reg);
				return reg;
			}
			catch (RemoteException ex) {
				logger.trace("RMI registry access threw exception", ex);
				logger.debug("Could not detect RMI registry - creating new one");
				// Assume no registry found -> create new one.
				// 根据端口创建 Registry
				return LocateRegistry.createRegistry(registryPort);
			}
		}
	}

	/**
	 * Test the given RMI registry, calling some operation on it to
	 * check whether it is still active.
	 * <p>Default implementation calls {@code Registry.list()}.
	 * @param registry the RMI registry to test
	 * @throws RemoteException if thrown by registry methods
	 * @see java.rmi.registry.Registry#list()
	 */
	protected void testRegistry(Registry registry) throws RemoteException {
		registry.list();
	}


	/**
	 * Unbind the RMI service from the registry on bean factory shutdown.
	 */
	@Override
	public void destroy() throws RemoteException {
		if (logger.isDebugEnabled()) {
			logger.debug("Unbinding RMI service '" + this.serviceName +
					"' from registry" + (this.createdRegistry ? (" at port '" + this.registryPort + "'") : ""));
		}
		try {
			this.registry.unbind(this.serviceName);
		}
		catch (NotBoundException ex) {
			if (logger.isInfoEnabled()) {
				logger.info("RMI service '" + this.serviceName + "' is not bound to registry" +
						(this.createdRegistry ? (" at port '" + this.registryPort + "' anymore") : ""), ex);
			}
		}
		finally {
			unexportObjectSilently();
		}
	}

	/**
	 * Unexport the registered RMI object, logging any exception that arises.
	 */
	private void unexportObjectSilently() {
		try {
			UnicastRemoteObject.unexportObject(this.exportedObject, true);
		}
		catch (NoSuchObjectException ex) {
			if (logger.isInfoEnabled()) {
				logger.info("RMI object for service '" + this.serviceName + "' is not exported anymore", ex);
			}
		}
	}

}
