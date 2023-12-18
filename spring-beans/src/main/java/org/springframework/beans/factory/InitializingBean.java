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

package org.springframework.beans.factory;

/**
 * Interface to be implemented by beans that need to react once all their properties
 * have been set by a {@link BeanFactory}: e.g. to perform custom initialization,
 * or merely to check that all mandatory properties have been set.
 *
 * <p>An alternative to implementing {@code InitializingBean} is specifying a custom
 * init method, for example in an XML bean definition. For a list of all bean
 * lifecycle methods, see the {@link BeanFactory BeanFactory javadocs}.
 *
 * InitializingBean： 实现此接口的 bean 会在初始化时调用其 afterPropertiesSet 方法来进 行 bean 的逻辑初始化。
 *
 * 这个类，顾名思义，也是用来初始化 bean 的。InitializingBean 接口为 bean 提供了初始化方法的方式，
 * 它只包括 afterPropertiesSet 方法，凡是继承该接口的类，在初始化 bean 的时候都会执行该方法。
 * 这个扩展点的触发时机在 postProcessAfterInitialization 之前。
 *
 * 使用场景：用户实现此接口，来进行系统启动的时候一些业务指标的初始化工作。
 *
 * 扩展方式为：
 *
 * public class NormalBeanA implements InitializingBean{
 *     @Override
 *     public void afterPropertiesSet() throws Exception {
 *         System.out.println("[InitializingBean] NormalBeanA");
 *     }
 * }
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @see DisposableBean
 * @see org.springframework.beans.factory.config.BeanDefinition#getPropertyValues()
 * @see org.springframework.beans.factory.support.AbstractBeanDefinition#getInitMethodName()
 */
public interface InitializingBean {

	/**
	 * Invoked by the containing {@code BeanFactory} after it has set all bean properties
	 * and satisfied {@link BeanFactoryAware}, {@code ApplicationContextAware} etc.
	 * <p>This method allows the bean instance to perform validation of its overall
	 * configuration and final initialization when all bean properties have been set.
	 * @throws Exception in the event of misconfiguration (such as failure to set an
	 * essential property) or if initialization fails for any other reason
	 */
	void afterPropertiesSet() throws Exception;

}
