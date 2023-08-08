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
//如果你想改变实际的 bean 实例( 例如从配置元数据创建的对象 )，那么你最好使用BeanPostProcessor。
// 同样地，BeanFactoryPostProcessor 的作用域范围是容器级的。它只和你所使用的容器有关。
// 如果你在容器中定义一个 BeanFactoryPostProcessor，它仅仅对此容器中的bean 进行后置处理。
// BeanFactoryPostProcessor 不会对定义在另一个容器中的 bean 进行后置处理，即使这两个容器都是在同一层次上。
// 在 Spring 中存在对于 BeanFactoryPostProcessor 的典型应用，比如 PropertyPlaceholderConfigurer。
// BeanFactoryPostProcessor 的典型应用: PropertyPlaceholderConfigurer有时候，阅读 Spring 的 Bean 描述文件时，
// 你也许会遇到类似如下的一些配置:
// <bean id="message" class="distConfig.HelloMessage">
//    <property name="mes">
//      <value>${bean.message}</value>
//    </property>
// </bean>
//其中竟然出现了变量引用:S{bean.message}。这就是 Spring 的分散配置，可以在另外的配置文件中为 bean.message 指定值。
// 如在 bean.property 配置如下定义:bean.message=Hi,can you find me?
//当访问名为 message 的 bean 时，mes 属性就会被置为字符串“Hi,can you find me?”，但Spring 框架是怎么知道存在这样的配置文件呢?
// 这就要靠 PropertyPlaceholderConfigurer 这个类的 bean:
// <bean id="mesHandler" class="org.Springframework.beans,factory.config. PropertyPlaceholderConfigurer">
//  <property name="locations">
//  <list>
//   <value>config/bean.properties</value>
//  </list>
//  </property>
//</bean>
//在这个 bean 中指定了配置文件为 config/bean.properties。到这里似乎找到问题的答案了，但是其实还有个问题。
// 这个“mesHandler”只不过是 Spring 框架管理的一个 bean，并没有被别的 bean 或者对象引用,Spring 的 beanFactory 是怎么知道要从这个 bean 中获取配置信息的呢?
// 查看层级结构可以看出 PropertyPlaceholderConfigurer 这个类间接继承了 BeanFactoryPostProcessor 接口。
//
// 这是一个很特别的接口，当 Spring 加载任何实现了这个接口的 bean 的配置时，都会在 bean 工厂载入所有 bean 的配置之后执行 postProcessBeanFactory 方法。
// 在PropertyResourceConfigurer 类中实现了 postProcessBeanFactory 方法，在方法中先后调用了mergeProperties、convertProperties、processProperties 这3 个方法，
// 分别得到配置，将得到的配置转换为合适的类型，最后将配置内容告知 BeanFactory。
// 正是通过实现 BeanFactoryPostProcessor 接口， BeanFactory 在实例化任何 bean 之前获得配置信息 ，从而能够正确解析 bean 描述文件中的变量引用

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
