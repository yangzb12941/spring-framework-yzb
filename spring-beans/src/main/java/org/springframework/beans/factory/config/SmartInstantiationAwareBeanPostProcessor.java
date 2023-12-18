/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.beans.factory.config;

import java.lang.reflect.Constructor;

import org.springframework.beans.BeansException;
import org.springframework.lang.Nullable;

/**
 * Extension of the {@link InstantiationAwareBeanPostProcessor} interface,
 * adding a callback for predicting the eventual type of a processed bean.
 *
 * <p><b>NOTE:</b> This interface is a special purpose interface, mainly for
 * internal use within the framework. In general, application-provided
 * post-processors should simply implement the plain {@link BeanPostProcessor}
 * interface or derive from the {@link InstantiationAwareBeanPostProcessorAdapter}
 * class. New methods might be added to this interface even in point releases.
 *
 * 扩展了InstantiationAwareBeanPostProcessor接口，添加了一个用于预测已处理bean的最终类型的回调。
 * 注意：此接口是一个专用接口，主要用于框架内的内部使用。通常，应用程序提供的后处理器应该简单地实现纯
 * BeanPostProcessor接口或从InstantationAware BeanPost ProcessorAdapter类派生。
 * 即使在点发布中，也可能会向该界面添加新方法。
 *
 * 该扩展接口有3个触发点方法：
 *
 * predictBeanType：
 *   该触发点发生在 postProcessBeforeInstantiation 之前(因为一般不太需要扩展这个点)，
 *   这个方法用于预测Bean的类型，返回第一个预测成功的Class类型，如果不能预测返回null；
 *   当你调用BeanFactory.getType(name)时当通过bean的名字无法得到bean类型信息时就
 *   调用该回调方法来决定类型信息。
 *
 * determineCandidateConstructors：
 *   该触发点发生在 postProcessBeforeInstantiation 之后，用于确定该bean的构造函数之用，
 *   返回的是该 bean 的所有构造函数列表。用户可以扩展这个点，来自定义选择相应的构造器来实例化这个bean。
 *
 * getEarlyBeanReference：
 *   该触发点发生在 postProcessAfterInstantiation 之后，当有循环依赖的场景，当bean实例化好之后，
 *   为了防止有循环依赖，会提前暴露回调方法，用于bean实例化的后置处理。这个方法就是在提前暴露的回调方法中触发。
 *
 * 扩展方式为：
 *
 * public class TestSmartInstantiationAwareBeanPostProcessor implements SmartInstantiationAwareBeanPostProcessor {
 *
 *     @Override
 *     public Class<?> predictBeanType(Class<?> beanClass, String beanName) throws BeansException {
 *         System.out.println("[TestSmartInstantiationAwareBeanPostProcessor] predictBeanType " + beanName);
 *         return beanClass;
 *     }
 *
 *     @Override
 *     public Constructor<?>[] determineCandidateConstructors(Class<?> beanClass, String beanName) throws BeansException {
 *         System.out.println("[TestSmartInstantiationAwareBeanPostProcessor] determineCandidateConstructors " + beanName);
 *         return null;
 *     }
 *
 *     @Override
 *     public Object getEarlyBeanReference(Object bean, String beanName) throws BeansException {
 *         System.out.println("[TestSmartInstantiationAwareBeanPostProcessor] getEarlyBeanReference " + beanName);
 *         return bean;
 *     }
 * }
 * @author Juergen Hoeller
 * @since 2.0.3
 * @see InstantiationAwareBeanPostProcessorAdapter
 */
public interface SmartInstantiationAwareBeanPostProcessor extends InstantiationAwareBeanPostProcessor {

	/**
	 * Predict the type of the bean to be eventually returned from this
	 * processor's {@link #postProcessBeforeInstantiation} callback.
	 * <p>The default implementation returns {@code null}.
	 * @param beanClass the raw class of the bean
	 * @param beanName the name of the bean
	 * @return the type of the bean, or {@code null} if not predictable
	 * @throws org.springframework.beans.BeansException in case of errors
	 */
	@Nullable
	default Class<?> predictBeanType(Class<?> beanClass, String beanName) throws BeansException {
		return null;
	}

	/**
	 * Determine the candidate constructors to use for the given bean.
	 * <p>The default implementation returns {@code null}.
	 * @param beanClass the raw class of the bean (never {@code null})
	 * @param beanName the name of the bean
	 * @return the candidate constructors, or {@code null} if none specified
	 * @throws org.springframework.beans.BeansException in case of errors
	 */
	@Nullable
	default Constructor<?>[] determineCandidateConstructors(Class<?> beanClass, String beanName)
			throws BeansException {

		return null;
	}

	/**
	 * Obtain a reference for early access to the specified bean,
	 * typically for the purpose of resolving a circular reference.
	 * <p>This callback gives post-processors a chance to expose a wrapper
	 * early - that is, before the target bean instance is fully initialized.
	 * The exposed object should be equivalent to the what
	 * {@link #postProcessBeforeInitialization} / {@link #postProcessAfterInitialization}
	 * would expose otherwise. Note that the object returned by this method will
	 * be used as bean reference unless the post-processor returns a different
	 * wrapper from said post-process callbacks. In other words: Those post-process
	 * callbacks may either eventually expose the same reference or alternatively
	 * return the raw bean instance from those subsequent callbacks (if the wrapper
	 * for the affected bean has been built for a call to this method already,
	 * it will be exposes as final bean reference by default).
	 * <p>The default implementation returns the given {@code bean} as-is.
	 * @param bean the raw bean instance
	 * @param beanName the name of the bean
	 * @return the object to expose as bean reference
	 * (typically with the passed-in bean instance as default)
	 * @throws org.springframework.beans.BeansException in case of errors
	 */
	default Object getEarlyBeanReference(Object bean, String beanName) throws BeansException {
		return bean;
	}

}
