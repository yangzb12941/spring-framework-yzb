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

package org.springframework.core;

/**
 * Interface to be implemented by transparent resource proxies that need to be
 * considered as equal to the underlying resource, for example for consistent
 * lookup key comparisons. Note that this interface does imply such special
 * semantics and does not constitute a general-purpose mixin!
 *
 * <p>Such wrappers will automatically be unwrapped for key comparisons in
 * {@link org.springframework.transaction.support.TransactionSynchronizationManager}.
 *
 * <p>Only fully transparent proxies, e.g. for redirection or service lookups,
 * are supposed to implement this interface. Proxies that decorate the target
 * object with new behavior, such as AOP proxies, do <i>not</i> qualify here!
 *
 * 要由透明资源代理实现的接口，这些代理需要被视为与基础资源相等，例如用于一致的查找关键字比较。
 * 请注意，这个接口确实暗示了这种特殊的语义，并且不构成通用的 mixin！
 *
 * 在org.springframework.transaction.support.TransactionSynchronizationManager中，
 * 将自动展开此类包装以进行密钥比较。 只有完全透明的代理，例如重定向或服务查找，才应该实现此接口。
 * 用新行为修饰目标对象的代理，如AOP代理，在这里不合格！
 *
 * @author Juergen Hoeller
 * @since 2.5.4
 * @see org.springframework.transaction.support.TransactionSynchronizationManager
 */
public interface InfrastructureProxy {

	/**
	 * Return the underlying resource (never {@code null}).
	 */
	Object getWrappedObject();

}
