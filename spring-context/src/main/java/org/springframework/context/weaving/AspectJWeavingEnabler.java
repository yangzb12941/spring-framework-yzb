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

package org.springframework.context.weaving;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

import org.aspectj.weaver.loadtime.ClassPreProcessorAgentAdapter;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.Ordered;
import org.springframework.instrument.classloading.InstrumentationLoadTimeWeaver;
import org.springframework.instrument.classloading.LoadTimeWeaver;
import org.springframework.lang.Nullable;

/**
 * Post-processor that registers AspectJ's
 * {@link org.aspectj.weaver.loadtime.ClassPreProcessorAgentAdapter}
 * with the Spring application context's default
 * {@link org.springframework.instrument.classloading.LoadTimeWeaver}.
 *
 * @author Juergen Hoeller
 * @author Ramnivas Laddad
 * @since 2.5
 */
public class AspectJWeavingEnabler
		implements BeanFactoryPostProcessor, BeanClassLoaderAware, LoadTimeWeaverAware, Ordered {

	/**
	 * The {@code aop.xml} resource location.
	 */
	public static final String ASPECTJ_AOP_XML_RESOURCE = "META-INF/aop.xml";


	@Nullable
	private ClassLoader beanClassLoader;

	@Nullable
	private LoadTimeWeaver loadTimeWeaver;


	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		this.beanClassLoader = classLoader;
	}

	@Override
	public void setLoadTimeWeaver(LoadTimeWeaver loadTimeWeaver) {
		this.loadTimeWeaver = loadTimeWeaver;
	}

	@Override
	public int getOrder() {
		return HIGHEST_PRECEDENCE;
	}

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		enableAspectJWeaving(this.loadTimeWeaver, this.beanClassLoader);
	}


	/**
	 * Enable AspectJ weaving with the given {@link LoadTimeWeaver}.
	 * @param weaverToUse the LoadTimeWeaver to apply to (or {@code null} for a default weaver)
	 * @param beanClassLoader the class loader to create a default weaver for (if necessary)
	 */
	public static void enableAspectJWeaving(
			@Nullable LoadTimeWeaver weaverToUse, @Nullable ClassLoader beanClassLoader) {
		//此时已经被初始化为 DefaultContextLoadTimeWeaver
		if (weaverToUse == null) {
			if (InstrumentationLoadTimeWeaver.isInstrumentationAvailable()) {
				weaverToUse = new InstrumentationLoadTimeWeaver(beanClassLoader);
			}
			else {
				throw new IllegalStateException("No LoadTimeWeaver available");
			}
		}
		//使用 DefaultContextLoadTimeWeaver 类型的 bean 巾的 loadTimeWeaver 属性注册转换器
		weaverToUse.addTransformer(
				new AspectJClassBypassingClassFileTransformer(new ClassPreProcessorAgentAdapter()));
	}


	/**
	 * ClassFileTransformer decorator that suppresses processing of AspectJ
	 * classes in order to avoid potential LinkageErrors.
	 *
	 * AspectJClassBypassingClassFileTransformer 的作用仅仅是告诉 AspectJ 以 org.aspectj 开头的 或者 org/aspectj 开头的类不进行处理。
	 *
	 * @see org.springframework.context.annotation.LoadTimeWeavingConfiguration
	 */
	private static class AspectJClassBypassingClassFileTransformer implements ClassFileTransformer {

		private final ClassFileTransformer delegate;

		public AspectJClassBypassingClassFileTransformer(ClassFileTransformer delegate) {
			this.delegate = delegate;
		}

		@Override
		public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
				ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {

			// AspectJClassBypassingClassFileTransformer 的作用仅仅是告诉 AspectJ 以
			// org.aspectj 开头的 或者 org/aspectj 开头的类不进行处理。
			if (className.startsWith("org.aspectj") || className.startsWith("org/aspectj")) {
				return classfileBuffer;
			}
			//委托给 AspectJ 代理继续处理
			return this.delegate.transform(loader, className, classBeingRedefined, protectionDomain, classfileBuffer);
		}
	}

}
