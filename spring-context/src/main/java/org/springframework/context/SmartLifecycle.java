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

package org.springframework.context;

/**
 * An extension of the {@link Lifecycle} interface for those objects that require to
 * be started upon ApplicationContext refresh and/or shutdown in a particular order.
 * The {@link #isAutoStartup()} return value indicates whether this object should
 * be started at the time of a context refresh. The callback-accepting
 * {@link #stop(Runnable)} method is useful for objects that have an asynchronous
 * shutdown process. Any implementation of this interface <i>must</i> invoke the
 * callback's {@code run()} method upon shutdown completion to avoid unnecessary
 * delays in the overall ApplicationContext shutdown.
 *
 * <p>This interface extends {@link Phased}, and the {@link #getPhase()} method's
 * return value indicates the phase within which this Lifecycle component should
 * be started and stopped. The startup process begins with the <i>lowest</i> phase
 * value and ends with the <i>highest</i> phase value ({@code Integer.MIN_VALUE}
 * is the lowest possible, and {@code Integer.MAX_VALUE} is the highest possible).
 * The shutdown process will apply the reverse order. Any components with the
 * same value will be arbitrarily ordered within the same phase.
 *
 * <p>Example: if component B depends on component A having already started,
 * then component A should have a lower phase value than component B. During
 * the shutdown process, component B would be stopped before component A.
 *
 * <p>Any explicit "depends-on" relationship will take precedence over the phase
 * order such that the dependent bean always starts after its dependency and
 * always stops before its dependency.
 *
 * <p>Any {@code Lifecycle} components within the context that do not also
 * implement {@code SmartLifecycle} will be treated as if they have a phase
 * value of 0. That way a {@code SmartLifecycle} implementation may start
 * before those {@code Lifecycle} components if it has a negative phase value,
 * or it may start after those components if it has a positive phase value.
 *
 * <p>Note that, due to the auto-startup support in {@code SmartLifecycle}, a
 * {@code SmartLifecycle} bean instance will usually get initialized on startup
 * of the application context in any case. As a consequence, the bean definition
 * lazy-init flag has very limited actual effect on {@code SmartLifecycle} beans.
 *
 * Lifecycle接口的扩展，用于那些需要在ApplicationContext刷新 and/or 按特定顺序关闭时启动的对象。
 * isAutoStartup（）返回值指示是否应在上下文刷新时启动此对象。回调接受stop(Runnable)方法对于具有
 * 异步关闭进程的对象非常有用。此接口的任何实现都必须在关闭完成时调用回调的run（）方法，以避免在整个
 * ApplicationContext关闭过程中出现不必要的延迟。 此接口扩展了Phased，getPhase（）方法的返回值
 * 指示应在其中started and stopped此Lifecycle组件的阶段。启动过程从最低相位值开始，到最高相位值
 * 结束（Integer.MIN_value是尽可能低的，Integer.MAX_value则是尽可能高的）。停机过程将采用相反的顺序。
 * 任何具有相同值的组件都将在同一阶段内任意排序。 示例：如果组件B依赖于组件A已经启动，那么组件A的相位值应该低于组件B。
 * 在停机过程中，组件B将在组件A之前停止。 任何显式的“依赖”关系都将优先于阶段顺序，这样依赖bean总是在其依赖项之后开始，
 * 而总是在依赖项之前停止。 上下文中未同时实现SmartLifecycle的任何Lifecycle组件都将被视为阶段值为0。
 *
 * 这样，如果SmartLifecycle的阶段值为负，则它可以在这些Lifecycle组件之前启动，如果阶段值为正，则它可能在这些组件之后启动。
 *
 * 请注意，由于SmartLifecycle中支持自动启动，在任何情况下，SmartLifecycle bean实例通常都会在应用程序上下文启动时初始化。
 * 因此，bean定义惰性init标志对SmartLifecycle bean的实际影响非常有限。
 *
 * @author Mark Fisher
 * @author Juergen Hoeller
 * @since 3.0
 * @see LifecycleProcessor
 * @see ConfigurableApplicationContext
 */
public interface SmartLifecycle extends Lifecycle, Phased {

	/**
	 * The default phase for {@code SmartLifecycle}: {@code Integer.MAX_VALUE}.
	 * <p>This is different from the common phase 0 associated with regular
	 * {@link Lifecycle} implementations, putting the typically auto-started
	 * {@code SmartLifecycle} beans into a separate later shutdown phase.
	 * @since 5.1
	 * @see #getPhase()
	 * @see org.springframework.context.support.DefaultLifecycleProcessor#getPhase(Lifecycle)
	 */
	int DEFAULT_PHASE = Integer.MAX_VALUE;


	/**
	 * Returns {@code true} if this {@code Lifecycle} component should get
	 * started automatically by the container at the time that the containing
	 * {@link ApplicationContext} gets refreshed.
	 * <p>A value of {@code false} indicates that the component is intended to
	 * be started through an explicit {@link #start()} call instead, analogous
	 * to a plain {@link Lifecycle} implementation.
	 * <p>The default implementation returns {@code true}.
	 * @see #start()
	 * @see #getPhase()
	 * @see LifecycleProcessor#onRefresh()
	 * @see ConfigurableApplicationContext#refresh()
	 */
	default boolean isAutoStartup() {
		return true;
	}

	/**
	 * Indicates that a Lifecycle component must stop if it is currently running.
	 * <p>The provided callback is used by the {@link LifecycleProcessor} to support
	 * an ordered, and potentially concurrent, shutdown of all components having a
	 * common shutdown order value. The callback <b>must</b> be executed after
	 * the {@code SmartLifecycle} component does indeed stop.
	 * <p>The {@link LifecycleProcessor} will call <i>only</i> this variant of the
	 * {@code stop} method; i.e. {@link Lifecycle#stop()} will not be called for
	 * {@code SmartLifecycle} implementations unless explicitly delegated to within
	 * the implementation of this method.
	 * <p>The default implementation delegates to {@link #stop()} and immediately
	 * triggers the given callback in the calling thread. Note that there is no
	 * synchronization between the two, so custom implementations may at least
	 * want to put the same steps within their common lifecycle monitor (if any).
	 * @see #stop()
	 * @see #getPhase()
	 */
	default void stop(Runnable callback) {
		stop();
		callback.run();
	}

	/**
	 * Return the phase that this lifecycle object is supposed to run in.
	 * <p>The default implementation returns {@link #DEFAULT_PHASE} in order to
	 * let stop callbacks execute after regular {@code Lifecycle} implementations.
	 * @see #isAutoStartup()
	 * @see #start()
	 * @see #stop(Runnable)
	 * @see org.springframework.context.support.DefaultLifecycleProcessor#getPhase(Lifecycle)
	 */
	@Override
	default int getPhase() {
		return DEFAULT_PHASE;
	}

}
