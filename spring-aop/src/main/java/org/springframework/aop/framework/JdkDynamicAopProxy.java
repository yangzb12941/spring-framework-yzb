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

package org.springframework.aop.framework;

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.AopInvocationException;
import org.springframework.aop.RawTargetAccess;
import org.springframework.aop.TargetSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.DecoratingProxy;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * JDK-based {@link AopProxy} implementation for the Spring AOP framework,
 * based on JDK {@link java.lang.reflect.Proxy dynamic proxies}.
 *
 * <p>Creates a dynamic proxy, implementing the interfaces exposed by
 * the AopProxy. Dynamic proxies <i>cannot</i> be used to proxy methods
 * defined in classes, rather than interfaces.
 *
 * <p>Objects of this type should be obtained through proxy factories,
 * configured by an {@link AdvisedSupport} class. This class is internal
 * to Spring's AOP framework and need not be used directly by client code.
 *
 * <p>Proxies created using this class will be thread-safe if the
 * underlying (target) class is thread-safe.
 *
 * <p>Proxies are serializable so long as all Advisors (including Advices
 * and Pointcuts) and the TargetSource are serializable.
 *
 * 首先来看一下基于 java 原生代理生成增强代理对象的过程，位于 JdkDynamicAopProxy 类中。
 * Java 原生代理要求代理类实现 InvocationHandler 接口，并在 InvocationHandler#invoke
 * 方法中实现代理增强逻辑。JdkDynamicAopProxy 正好实现了该接口，对应的 JdkDynamicAopProxy#invoke
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Dave Syer
 * @see java.lang.reflect.Proxy
 * @see AdvisedSupport
 * @see ProxyFactory
 */
final class JdkDynamicAopProxy implements AopProxy, InvocationHandler, Serializable {

	/** use serialVersionUID from Spring 1.2 for interoperability. */
	private static final long serialVersionUID = 5531744639992436476L;


	/*
	 * NOTE: We could avoid the code duplication between this class and the CGLIB
	 * proxies by refactoring "invoke" into a template method. However, this approach
	 * adds at least 10% performance overhead versus a copy-paste solution, so we sacrifice
	 * elegance for performance. (We have a good test suite to ensure that the different
	 * proxies behave the same :-)
	 * This way, we can also more easily take advantage of minor optimizations in each class.
	 */

	/** We use a static Log to avoid serialization issues. */
	private static final Log logger = LogFactory.getLog(JdkDynamicAopProxy.class);

	/** Config used to configure this proxy. */
	private final AdvisedSupport advised;

	/**
	 * Is the {@link #equals} method defined on the proxied interfaces?
	 */
	private boolean equalsDefined;

	/**
	 * Is the {@link #hashCode} method defined on the proxied interfaces?
	 */
	private boolean hashCodeDefined;


	/**
	 * Construct a new JdkDynamicAopProxy for the given AOP configuration.
	 * @param config the AOP configuration as AdvisedSupport object
	 * @throws AopConfigException if the config is invalid. We try to throw an informative
	 * exception in this case, rather than let a mysterious failure happen later.
	 */
	public JdkDynamicAopProxy(AdvisedSupport config) throws AopConfigException {
		Assert.notNull(config, "AdvisedSupport must not be null");
		if (config.getAdvisors().length == 0 && config.getTargetSource() == AdvisedSupport.EMPTY_TARGET_SOURCE) {
			throw new AopConfigException("No advisors and no TargetSource specified");
		}
		this.advised = config;
	}


	@Override
	public Object getProxy() {
		return getProxy(ClassUtils.getDefaultClassLoader());
	}

	@Override
	public Object getProxy(@Nullable ClassLoader classLoader) {
		if (logger.isTraceEnabled()) {
			logger.trace("Creating JDK dynamic proxy: " + this.advised.getTargetSource());
		}
		// 获取需要被代理的接口集合
		Class<?>[] proxiedInterfaces = AopProxyUtils.completeProxiedInterfaces(this.advised, true);
		// 检测是否在被代理接口中声明了 equals 和 hashCode 方法
		findDefinedEqualsAndHashCodeMethods(proxiedInterfaces);
		// 基于 java 原生代理生成代理对象
		return Proxy.newProxyInstance(classLoader, proxiedInterfaces, this);
	}

	/**
	 * Finds any {@link #equals} or {@link #hashCode} method that may be defined
	 * on the supplied set of interfaces.
	 * @param proxiedInterfaces the interfaces to introspect
	 */
	private void findDefinedEqualsAndHashCodeMethods(Class<?>[] proxiedInterfaces) {
		for (Class<?> proxiedInterface : proxiedInterfaces) {
			Method[] methods = proxiedInterface.getDeclaredMethods();
			for (Method method : methods) {
				if (AopUtils.isEqualsMethod(method)) {
					this.equalsDefined = true;
				}
				if (AopUtils.isHashCodeMethod(method)) {
					this.hashCodeDefined = true;
				}
				if (this.equalsDefined && this.hashCodeDefined) {
					return;
				}
			}
		}
	}


	/**
	 * Implementation of {@code InvocationHandler.invoke}.
	 * <p>Callers will see exactly the exception thrown by the target,
	 * unless a hook method throws an exception.
	 *
	 * 1、特殊处理 Object#equals、Object#hashCode、DecoratingProxy#getDecoratedClass，
	 *    以及 Advised 类及其父类中定义的方法；
	 * 2、如果配置了 expose-proxy 属性，则记录当前代理对象，以备在内部间调用时实施增强；
	 * 3、获取当前方法的拦截器链；
	 * 4、如果没有拦截器定义，则直接反射调用增强方法，否则先逐一执行拦截器方法，最后再应用增强方法；
	 * 5、处理返回值。
	 *
	 * 上面的函数中最主要的工作就是创建了一个拦截器链，并使用 ReflectiveMethodInvocation 类进行了链的封装，
	 * 而在 ReflectiveMethodInvocation 类的 proceed 方法中实现了拦截器的逐一调用，
	 * 那么我们继续来探究，在 proceed 方法中是怎么实现前置增强在目标方法前调用后置增 强在目标方法后调用的逻辑呢？
	 */
	@Override
	@Nullable
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		Object oldProxy = null;
		boolean setProxyContext = false;

		TargetSource targetSource = this.advised.targetSource;
		Object target = null;

		try {
			//equals 方法的处理
			if (!this.equalsDefined && AopUtils.isEqualsMethod(method)) {
				// The target does not implement the equals(Object) method itself.
				return equals(args[0]);
			}
			//hash 方法的处理
			else if (!this.hashCodeDefined && AopUtils.isHashCodeMethod(method)) {
				// The target does not implement the hashCode() method itself.
				return hashCode();
			}
			// 如果是 DecoratingProxy 中定义的方法（即 DecoratingProxy#getDecoratedClass），直接返回目标类对象
			else if (method.getDeclaringClass() == DecoratingProxy.class) {
				// There is only getDecoratedClass() declared -> dispatch to proxy config.
				return AopProxyUtils.ultimateTargetClass(this.advised);
			}
			// Class 类的 isAssignableFrom(Class cls)方法 ：
			// 如果调用这个方法的 class 或接口与参数 cls 表示的类或接口相同；
			// 或者是参数 cls 表示的类或接口的父类，则返回 true 。
			// 形象地：自身类 class isAssignableFrom(自身类或子类.class) 返回 true 例 ：
			//例：
			// System.out.println(ArrayList.class.isAssignableFrom(Object.class));
			// false
			// System.out.println(Object.class.isAssignableFrom(ArrayList.class));
			// true
			else if (!this.advised.opaque  // 允许被转换成 Advised 类型
					&& method.getDeclaringClass().isInterface() // 接口类型
					&& method.getDeclaringClass().isAssignableFrom(Advised.class) // 方法所在类是 Advised 类及其父类
			) {
				// Service invocations on ProxyConfig with the proxy config...
				return AopUtils.invokeJoinpointUsingReflection(this.advised, method, args);
			}
			// 结果值
			Object retVal;
			// 有时候目标对象内部的自我调用将无法实施切面中的增强则需要通过此属性暴露代理
			// 指定内部间调用也需要代理
			if (this.advised.exposeProxy) {
				// Make invocation available if necessary.
				oldProxy = AopContext.setCurrentProxy(proxy);
				setProxyContext = true;
			}

			// Get as late as possible to minimize the time we "own" the target,
			// in case it comes from a pool.
			// 尽可能晚一点，以尽量减少我们“拥有”目标的时间，以防它来自对象池。
			target = targetSource.getTarget();
			Class<?> targetClass = (target != null ? target.getClass() : null);

			// Get the interception chain for this method.
			// 获取当前方法的拦截器链
			List<Object> chain = this.advised.getInterceptorsAndDynamicInterceptionAdvice(method, targetClass);

			// Check whether we have any advice. If we don't, we can fallback on direct
			// reflective invocation of the target, and avoid creating a MethodInvocation.
			// 看看我们是否有什么拦截器。如果我们不这样做，我们可以回退到对目标的直接反射调用，并避免创建MethodInvocation。
			if (chain.isEmpty()) {
				// We can skip creating a MethodInvocation: just invoke the target directly
				// Note that the final invoker must be an InvokerInterceptor so we know it does
				// nothing but a reflective operation on the target, and no hot swapping or fancy proxying.
				// 我们可以跳过创建MethodInvocation：直接调用目标。
				// 注意，最终调用程序必须是InvokerInterceptor，这样我们就知道它只对目标执行反射操作，而不进行热交换或花式代理。
				// 如果没有发现任何拦截器，那么直接调用切点方法
				Object[] argsToUse = AopProxyUtils.adaptArgumentsIfNecessary(method, args);
				retVal = AopUtils.invokeJoinpointUsingReflection(target, method, argsToUse);
			}
			else {
				// We need to create a method invocation...
				// 将拦截器封装在 ReflectiveMethodInvocation ，
				// 以便于使用其 proceed 进行链接表用拦截器
				MethodInvocation invocation =
						new ReflectiveMethodInvocation(proxy, target, method, args, targetClass, chain);
				// Proceed to the joinpoint through the interceptor chain.
				// 执行拦截器链
				retVal = invocation.proceed();
			}

			// Massage return value if necessary.
			Class<?> returnType = method.getReturnType();
			//返回结果
			if (retVal != null && retVal == target &&
					returnType != Object.class && returnType.isInstance(proxy) &&
					!RawTargetAccess.class.isAssignableFrom(method.getDeclaringClass())) {
				// Special case: it returned "this" and the return type of the method
				// is type-compatible. Note that we can't help if the target sets
				// a reference to itself in another returned object.
				// 特殊情况：它返回“this”，并且该方法的返回类型与类型兼容。请注意，如果目标在另一个返回的对象中设置了对自身的引用，我们也无能为力。
				retVal = proxy;
			}
			else if (retVal == null && returnType != Void.TYPE && returnType.isPrimitive()) {
				throw new AopInvocationException(
						"Null return value from advice does not match primitive return type for: " + method);
			}
			return retVal;
		}
		finally {
			if (target != null && !targetSource.isStatic()) {
				// Must have come from TargetSource.
				targetSource.releaseTarget(target);
			}
			if (setProxyContext) {
				// Restore old proxy.
				AopContext.setCurrentProxy(oldProxy);
			}
		}
	}


	/**
	 * Equality means interfaces, advisors and TargetSource are equal.
	 * <p>The compared object may be a JdkDynamicAopProxy instance itself
	 * or a dynamic proxy wrapping a JdkDynamicAopProxy instance.
	 */
	@Override
	public boolean equals(@Nullable Object other) {
		if (other == this) {
			return true;
		}
		if (other == null) {
			return false;
		}

		JdkDynamicAopProxy otherProxy;
		if (other instanceof JdkDynamicAopProxy) {
			otherProxy = (JdkDynamicAopProxy) other;
		}
		else if (Proxy.isProxyClass(other.getClass())) {
			InvocationHandler ih = Proxy.getInvocationHandler(other);
			if (!(ih instanceof JdkDynamicAopProxy)) {
				return false;
			}
			otherProxy = (JdkDynamicAopProxy) ih;
		}
		else {
			// Not a valid comparison...
			return false;
		}

		// If we get here, otherProxy is the other AopProxy.
		return AopProxyUtils.equalsInProxy(this.advised, otherProxy.advised);
	}

	/**
	 * Proxy uses the hash code of the TargetSource.
	 */
	@Override
	public int hashCode() {
		return JdkDynamicAopProxy.class.hashCode() * 13 + this.advised.getTargetSource().hashCode();
	}

}
