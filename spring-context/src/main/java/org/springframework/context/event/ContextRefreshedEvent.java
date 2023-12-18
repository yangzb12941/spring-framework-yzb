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

package org.springframework.context.event;

import org.springframework.context.ApplicationContext;

/**
 * Event raised when an {@code ApplicationContext} gets initialized or refreshed.
 *
 * ApplicationContext 被初始化或刷新时，该事件被发布。这也可以在 ConfigurableApplicationContext
 * 接口中使用 refresh() 方法来发生。此处的初始化是指：所有的 Bean 被成功装载，后处理 Bean 被检测并激活，
 * 所有 Singleton Bean 被预实例化，ApplicationContext 容器已就绪可用。
 *
 * @author Juergen Hoeller
 * @since 04.03.2003
 * @see ContextClosedEvent
 */
@SuppressWarnings("serial")
public class ContextRefreshedEvent extends ApplicationContextEvent {

	/**
	 * Create a new ContextRefreshedEvent.
	 * @param source the {@code ApplicationContext} that has been initialized
	 * or refreshed (must not be {@code null})
	 */
	public ContextRefreshedEvent(ApplicationContext source) {
		super(source);
	}

}
