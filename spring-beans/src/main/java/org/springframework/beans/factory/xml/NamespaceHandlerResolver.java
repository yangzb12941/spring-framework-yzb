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

package org.springframework.beans.factory.xml;

import org.springframework.lang.Nullable;

/**
 * Used by the {@link org.springframework.beans.factory.xml.DefaultBeanDefinitionDocumentReader} to
 * locate a {@link NamespaceHandler} implementation for a particular namespace URI.
 *
 * @author Rob Harrop
 * @since 2.0
 * @see NamespaceHandler
 * @see org.springframework.beans.factory.xml.DefaultBeanDefinitionDocumentReader
 */
@FunctionalInterface
public interface NamespaceHandlerResolver {

	/**
	 * Resolve the namespace URI and return the located {@link NamespaceHandler}
	 * implementation.
	 *
	 * 有了命名空间，就可以进行 NamespaceHandler 的提取了，继续之前的 parseCustomElement 函数的跟踪，
	 * 分析 NamespaceHandler handler = this.readerContext.getNamespaceHandlerResolver.resolve(namespaceUri),
	 * 在 readerContext 初始化的时候其属性 namespaceHandlerResolver 已经被初始化为了 DefaultNamespaceHandlerResolver 的实例，
	 * 所以，这里调用的 resolve 方法其实调用的是DefaultNamespaceHandlerResolver 类中的方法。
	 * 我们进入 DefaultNamespaceHandlerResolver 的resolve 方法进行查看。
	 *
	 * @param namespaceUri the relevant namespace URI
	 * @return the located {@link NamespaceHandler} (may be {@code null})
	 */
	@Nullable
	NamespaceHandler resolve(String namespaceUri);

}
