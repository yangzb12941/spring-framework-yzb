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
 * Event raised when an {@code ApplicationContext} gets stopped.
 *
 * 当使用 ConfigurableApplicationContext 接口中的 stop() 停止 ApplicationContext 时，
 * 发布这个事件。你可以在接受到这个事件后做必要的清理的工作
 *
 * @author Mark Fisher
 * @author Juergen Hoeller
 * @since 2.5
 * @see ContextStartedEvent
 */
@SuppressWarnings("serial")
public class ContextStoppedEvent extends ApplicationContextEvent {

	/**
	 * Create a new ContextStoppedEvent.
	 * @param source the {@code ApplicationContext} that has been stopped
	 * (must not be {@code null})
	 */
	public ContextStoppedEvent(ApplicationContext source) {
		super(source);
	}

}
