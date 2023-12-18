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

package org.springframework.context.support;

import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.EmbeddedValueResolver;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.MessageSourceAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.lang.Nullable;
import org.springframework.util.StringValueResolver;

/**
 * {@link BeanPostProcessor} implementation that supplies the {@code ApplicationContext},
 * {@link org.springframework.core.env.Environment Environment}, or
 * {@link StringValueResolver} for the {@code ApplicationContext} to beans that
 * implement the {@link EnvironmentAware}, {@link EmbeddedValueResolverAware},
 * {@link ResourceLoaderAware}, {@link ApplicationEventPublisherAware},
 * {@link MessageSourceAware}, and/or {@link ApplicationContextAware} interfaces.
 *
 * <p>Implemented interfaces are satisfied in the order in which they are
 * mentioned above.
 *
 * <p>Application contexts will automatically register this with their
 * underlying bean factory. Applications do not use this directly.
 *
 * 该类本身并没有扩展点，但是该类内部却有6个扩展点可供实现 ，这些类触发的时机在 bean 实例化之后，初始化之前
 *
 * EnvironmentAware：
 *   用于获取 EnvironmentAware 的一个扩展类，这个变量非常有用， 可以获得系统内的所有参数。
 *   当然个人认为这个Aware没必要去扩展，因为spring内部都可以通过注入的方式来直接获得。
 *
 * EmbeddedValueResolverAware：
 *   用于获取 StringValueResolver 的一个扩展类， StringValueResolver 用于获取基于
 *   String 类型的 properties 的变量，一般我们都用 @Value 的方式去获取，如果实现了这个Aware接口，
 *   把StringValueResolver 缓存起来，通过这个类去获取 String 类型的变量，效果是一样的。
 *
 * ResourceLoaderAware：
 *   用于获取 ResourceLoader 的一个扩展类，ResourceLoader 可以用于获取 classpath
 *   内所有的资源对象，可以扩展此类来拿到ResourceLoader对象。
 *
 * ApplicationEventPublisherAware：
 *   用于获取 ApplicationEventPublisher 的一个扩展类，ApplicationEventPublisher
 *   可以用来发布事件，结合 ApplicationListener 来共同使用，下文在介绍ApplicationListener
 *   时会详细提到。这个对象也可以通过spring注入的方式来获得。
 *
 * MessageSourceAware：
 *   用于获取MessageSource的一个扩展类，MessageSource主要用来做国际化。
 *
 * ApplicationContextAware：
 *   用来获取ApplicationContext的一个扩展类，ApplicationContext应该是很多人非常熟悉的一个类了，
 *   就是spring上下文管理器，可以手动的获取任何在spring上下文注册的bean，
 *   我们经常扩展这个接口来缓存spring上下文，包装成静态方法。
 *   同时ApplicationContext也实现了BeanFactory，MessageSource，ApplicationEventPublisher等接口，
 *   也可以用来做相关接口的事情。
 *
 * @author Juergen Hoeller
 * @author Costin Leau
 * @author Chris Beams
 * @since 10.10.2003
 * @see org.springframework.context.EnvironmentAware
 * @see org.springframework.context.EmbeddedValueResolverAware
 * @see org.springframework.context.ResourceLoaderAware
 * @see org.springframework.context.ApplicationEventPublisherAware
 * @see org.springframework.context.MessageSourceAware
 * @see org.springframework.context.ApplicationContextAware
 * @see org.springframework.context.support.AbstractApplicationContext#refresh()
 */
class ApplicationContextAwareProcessor implements BeanPostProcessor {

	private final ConfigurableApplicationContext applicationContext;

	private final StringValueResolver embeddedValueResolver;


	/**
	 * Create a new ApplicationContextAwareProcessor for the given context.
	 */
	public ApplicationContextAwareProcessor(ConfigurableApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
		this.embeddedValueResolver = new EmbeddedValueResolver(applicationContext.getBeanFactory());
	}


	@Override
	@Nullable
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		if (!(bean instanceof EnvironmentAware || bean instanceof EmbeddedValueResolverAware ||
				bean instanceof ResourceLoaderAware || bean instanceof ApplicationEventPublisherAware ||
				bean instanceof MessageSourceAware || bean instanceof ApplicationContextAware)){
			return bean;
		}

		AccessControlContext acc = null;

		if (System.getSecurityManager() != null) {
			acc = this.applicationContext.getBeanFactory().getAccessControlContext();
		}

		if (acc != null) {
			AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
				invokeAwareInterfaces(bean);
				return null;
			}, acc);
		}
		else {
			invokeAwareInterfaces(bean);
		}

		return bean;
	}

	/**
	 * 实现这些 Aware 接口的 bean 在被初始化之后，可以取得一些对应的资源
	 * @param bean
	 */
	private void invokeAwareInterfaces(Object bean) {
		// 用于获取 EnvironmentAware 的一个扩展类，这个变量非常有用， 可以获得系统内的所有参数。
		// 当然个人认为这个Aware没必要去扩展，因为spring内部都可以通过注入的方式来直接获得。
		if (bean instanceof EnvironmentAware) {
			((EnvironmentAware) bean).setEnvironment(this.applicationContext.getEnvironment());
		}
		// 嵌入式值解析程序感知
		// 用于获取 StringValueResolver 的一个扩展类， StringValueResolver 用于获取基于
		// String 类型的 properties 的变量，一般我们都用 @Value 的方式去获取，如果实现了这个Aware接口，
		// 把StringValueResolver 缓存起来，通过这个类去获取 String 类型的变量，效果是一样的。
		if (bean instanceof EmbeddedValueResolverAware) {
			((EmbeddedValueResolverAware) bean).setEmbeddedValueResolver(this.embeddedValueResolver);
		}
		// 用于获取 ResourceLoader 的一个扩展类，ResourceLoader 可以用于获取 classpath
		// 内所有的资源对象，可以扩展此类来拿到ResourceLoader对象。
		if (bean instanceof ResourceLoaderAware) {
			((ResourceLoaderAware) bean).setResourceLoader(this.applicationContext);
		}
		// 用于获取 ApplicationEventPublisher 的一个扩展类，ApplicationEventPublisher
		// 可以用来发布事件，结合 ApplicationListener 来共同使用，下文在介绍ApplicationListener
		// 时会详细提到。这个对象也可以通过spring注入的方式来获得。
		if (bean instanceof ApplicationEventPublisherAware) {
			((ApplicationEventPublisherAware) bean).setApplicationEventPublisher(this.applicationContext);
		}
		// 用于获取MessageSource的一个扩展类，MessageSource主要用来做国际化。
		if (bean instanceof MessageSourceAware) {
			((MessageSourceAware) bean).setMessageSource(this.applicationContext);
		}
		// 用来获取ApplicationContext的一个扩展类，ApplicationContext应该是很多人非常熟悉的一个类了，
		// 就是spring上下文管理器，可以手动的获取任何在spring上下文注册的bean，
		// 我们经常扩展这个接口来缓存spring上下文，包装成静态方法。
		// 同时ApplicationContext也实现了BeanFactory，MessageSource，ApplicationEventPublisher等接口，
		// 也可以用来做相关接口的事情。
		if (bean instanceof ApplicationContextAware) {
			((ApplicationContextAware) bean).setApplicationContext(this.applicationContext);
		}
	}

}
