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

package org.springframework.beans.factory.config;

import org.springframework.beans.BeansException;

/**
 * Factory hook that allows for custom modification of an application context's
 * bean definitions, adapting the bean property values of the context's underlying
 * bean factory.
 *
 * <p>Useful for custom config files targeted at system administrators that
 * override bean properties configured in the application context. See
 * {@link PropertyResourceConfigurer} and its concrete implementations for
 * out-of-the-box solutions that address such configuration needs.
 *
 * <p>A {@code BeanFactoryPostProcessor} may interact with and modify bean
 * definitions, but never bean instances. Doing so may cause premature bean
 * instantiation, violating the container and causing unintended side-effects.
 * If bean instance interaction is required, consider implementing
 * {@link BeanPostProcessor} instead.
 *
 * <h3>Registration</h3>
 * <p>An {@code ApplicationContext} auto-detects {@code BeanFactoryPostProcessor}
 * beans in its bean definitions and applies them before any other beans get created.
 * A {@code BeanFactoryPostProcessor} may also be registered programmatically
 * with a {@code ConfigurableApplicationContext}.
 *
 * <h3>Ordering</h3>
 * <p>{@code BeanFactoryPostProcessor} beans that are autodetected in an
 * {@code ApplicationContext} will be ordered according to
 * {@link org.springframework.core.PriorityOrdered} and
 * {@link org.springframework.core.Ordered} semantics. In contrast,
 * {@code BeanFactoryPostProcessor} beans that are registered programmatically
 * with a {@code ConfigurableApplicationContext} will be applied in the order of
 * registration; any ordering semantics expressed through implementing the
 * {@code PriorityOrdered} or {@code Ordered} interface will be ignored for
 * programmatically registered post-processors. Furthermore, the
 * {@link org.springframework.core.annotation.Order @Order} annotation is not
 * taken into account for {@code BeanFactoryPostProcessor} beans.
 *
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 06.07.2003
 * @see BeanPostProcessor
 * @see PropertyResourceConfigurer
 */
// BeanFactoryPostProcessor是容器启动阶段Spring提供的一个扩展点，主要负责对注册到BeanDefinitionRegistry
// 中的一个个的BeanDefinition进行一定程度上的修改与替换。作用如下：
// 配置元信息中的属性注入。
// 例如：配置数据库dataSource bean的时候，属性具体的值可以通过占位符的方式${jdbc.maxIdle}。
// BeanFactoryPostProcessor就会对注册到BeanDefinitionRegistry中的BeanDefinition做最后的修改，
// 替换$占位符为配置文件中的真实的数据。
// <bean id="dataSource"
//    class="org.apache.commons.dbcp.BasicDataSource"
//    destroy-method="close">
//    <property name="maxIdle" value="${jdbc.maxIdle}"></property>
//    <property name="maxActive" value="${jdbc.maxActive}"></property>
//    <property name="maxWait" value="${jdbc.maxWait}"></property>
//    <property name="minIdle" value="${jdbc.minIdle}"></property>
//
//    <property name="driverClassName"
//        value="${jdbc.driverClassName}">
//    </property>
//    <property name="url" value="${jdbc.url}"></property>
//
//    <property name="username" value="${jdbc.username}"></property>
//    <property name="password" value="${jdbc.password}"></property>
//</bean>
// 至此，整个容器启动阶段就算完成了，容器的【启动阶段】的最终产物就是注册到BeanDefinitionRegistry中的一个个BeanDefinition了，
// 这就是Spring为Bean实例化所做的预热的工作。
// 容器启动阶段做了哪些操作:
//                                         开始
//               	                        |
//                                   	    v
//     配置元信息 <-----加载----> 	  BeanDefinitionReader
//                                   	   生成
//                                   	    v
//                                    BeanDefinition
//                                   	  注册到                      --->BeanFactoryPostProcessor(扩展点)
//                                   	    v                        |                  |
//                                    BeanDefinitionRegistry --修改、替换、增强            |
//                                          ^-------------------------------------------|
//接下来就是【实例化】阶段：将BeanDefinitionRegistry中的一个个的BeanDefinition的，通过反射方式实例化对象。

@FunctionalInterface
public interface BeanFactoryPostProcessor {

	/**
	 * Modify the application context's internal bean factory after its standard
	 * initialization. All bean definitions will have been loaded, but no beans
	 * will have been instantiated yet. This allows for overriding or adding
	 * properties even to eager-initializing beans.
	 * @param beanFactory the bean factory used by the application context
	 * @throws org.springframework.beans.BeansException in case of errors
	 */
	void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException;

}
