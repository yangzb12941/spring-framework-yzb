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

package org.springframework.beans.factory;

import org.springframework.lang.Nullable;

/**
 * Sub-interface implemented by bean factories that can be part
 * of a hierarchy.
 *
 * <p>The corresponding {@code setParentBeanFactory} method for bean
 * factories that allow setting the parent in a configurable
 * fashion can be found in the ConfigurableBeanFactory interface.
 *
 * 继承 BeanFactory ，也就是在 BeanFactory 定义的功能的基础
 * 上增加了对 parentFactory 支持。
 *
 * HierarchicalBeanFactory：父子级联 IoC 容器的接口，子容器可以通过接口方法访问父容器；
 * 通过 HierarchicalBeanFactory 接口， Spring 的 IoC 容器可以建立父子层级关联的容器体系，
 * 子容器可以访问父容器中的 Bean，但父容器不能访问子容器的 Bean。
 * Spring 使用父子容器实现了很多功能，比如在 Spring MVC 中，展现层 Bean 位于一个子容器中，
 * 而业务层和持久层的 Bean 位于父容器中。
 * 这样，展现层 Bean 就可以引用业务层和持久层的 Bean，而业务层和持久层的 Bean 则看不到展现层的 Bean。
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 07.07.2003
 * @see org.springframework.beans.factory.config.ConfigurableBeanFactory#setParentBeanFactory
 */
public interface HierarchicalBeanFactory extends BeanFactory {

	/**
	 * Return the parent bean factory, or {@code null} if there is none.
	 */
	@Nullable
	BeanFactory getParentBeanFactory();

	/**
	 * Return whether the local bean factory contains a bean of the given name,
	 * ignoring beans defined in ancestor contexts.
	 * <p>This is an alternative to {@code containsBean}, ignoring a bean
	 * of the given name from an ancestor bean factory.
	 * @param name the name of the bean to query
	 * @return whether a bean with the given name is defined in the local factory
	 * @see BeanFactory#containsBean
	 */
	boolean containsLocalBean(String name);

}
