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
 * Event raised when an {@code ApplicationContext} gets closed.
 *
 * 当使用 ConfigurableApplicationContext 接口中的 close()方法关闭 ApplicationContext 时，
 * 该事件被发布。一个已关闭的上下文到达生命周期末端；它不能被刷新或重启
 *
 * @author Juergen Hoeller
 * @since 12.08.2003
 * @see ContextRefreshedEvent
 */
@SuppressWarnings("serial")
public class ContextClosedEvent extends ApplicationContextEvent {

	/**
	 * Creates a new ContextClosedEvent.
	 * @param source the {@code ApplicationContext} that has been closed
	 * (must not be {@code null})
	 */
	public ContextClosedEvent(ApplicationContext source) {
		super(source);
	}

}
