/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.context.config;

import org.w3c.dom.Element;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.weaving.AspectJWeavingEnabler;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;

/**
 * Parser for the &lt;context:load-time-weaver/&gt; element.
 *
 * @author Juergen Hoeller
 * @since 2.5
 */
class LoadTimeWeaverBeanDefinitionParser extends AbstractSingleBeanDefinitionParser {

	/**
	 * The bean name of the internally managed AspectJ weaving enabler.
	 * @since 4.3.1
	 */
	public static final String ASPECTJ_WEAVING_ENABLER_BEAN_NAME =
			"org.springframework.context.config.internalAspectJWeavingEnabler";

	private static final String ASPECTJ_WEAVING_ENABLER_CLASS_NAME =
			"org.springframework.context.weaving.AspectJWeavingEnabler";

	private static final String DEFAULT_LOAD_TIME_WEAVER_CLASS_NAME =
			"org.springframework.context.weaving.DefaultContextLoadTimeWeaver";

	private static final String WEAVER_CLASS_ATTRIBUTE = "weaver-class";

	private static final String ASPECTJ_WEAVING_ATTRIBUTE = "aspectj-weaving";


	@Override
	protected String getBeanClassName(Element element) {
		if (element.hasAttribute(WEAVER_CLASS_ATTRIBUTE)) {
			return element.getAttribute(WEAVER_CLASS_ATTRIBUTE);
		}
		return DEFAULT_LOAD_TIME_WEAVER_CLASS_NAME;
	}

	@Override
	protected String resolveId(Element element, AbstractBeanDefinition definition, ParserContext parserContext) {
		return ConfigurableApplicationContext.LOAD_TIME_WEAVER_BEAN_NAME;
	}

	/**
	 * 1、是否开启 AspectJ。
	 * 之前虽然反复提到了在配置文件中加入了<context: load-time-weaver/>便相当于加入了
	 * AspectJ 开关。 但是，并不是配置了这个标签就意味着开启了 AspectJ 功能，
	 * 这个标签中还有一个属性 aspectj-weaving，这个属性有 3 个备选值， on 、 off 和 autodetect，
	 * 默认为 autodetect，也就是说，如果我们只是使用了<context: load-time-weaver/>，
	 * 那么 Spring 会帮助我们检测是否可 以使用 AspectJ 功能，而检测的依据便是文件 META-INF/aop.xml
	 * 是否存在，看看在 Spring 中 的实现方式。
	 *
	 * 2. 将 org.Springframework.context.weaving.AspectJWeavingEnabler 封装在 BeanDefinition
	 * 中注册。当通过 AspectJ 功能验证后便可以进行 AspectJWeavingEnabler 的注册了， 注册的方式很简
	 * 单，无非是将类路径注册在新初始化的 RootBeanDefinition 中，在 RootBeanDefinition 的获取时
	 * 会转换成对应的 class 。
	 * 尽管在 init 方法中注册了 AspectJWeavingEnabler,但是对于标签本身 Spring 也会以 bean 的形式保存，
	 * 也就是当 Spring 解，析到<context: load-time-weaver/>标签的时候也会产生一个 bean ,而这个 bean 中的信息是什么呢？
	 * 当 Spring 在读取到自定义标签<context: load-time-weaver/>
	 * 后会产生一个bean，而这个bean 的 id 为 loadTimeWeaver,
	 * class 为 org.Springframework.context.weaving.DefaultContextLoadTimeWeaver，
	 * 也就是完成了 DefaultContextLoadTimeWeaver类的注册。
	 * 完成了以上的注册功能后，并不意味这在 Spring 中就可以使用 AspectJ 了，因为我们还有一个很
	 * 重要的步骤忽略了，就是 LoadTimeWeaverAwareProcessor 的注册。
	 * 在 AbstractApplicationContext 中的 prepareBeanFactory 函数是在容器初始化时候调用的，
	 * 也就是说只有注册了 LoadTimeWeaverAwareProcessor 才会激活整个 AspectJ 的功能。
	 *
	 * @param element the XML element being parsed
	 * @param parserContext the object encapsulating the current state of the parsing process
	 * @param builder used to define the {@code BeanDefinition}
	 */
	@Override
	protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
		builder.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);

		if (isAspectJWeavingEnabled(element.getAttribute(ASPECTJ_WEAVING_ATTRIBUTE), parserContext)) {
			if (!parserContext.getRegistry().containsBeanDefinition(ASPECTJ_WEAVING_ENABLER_BEAN_NAME)) {
				RootBeanDefinition def = new RootBeanDefinition(ASPECTJ_WEAVING_ENABLER_CLASS_NAME);
				parserContext.registerBeanComponent(
						new BeanComponentDefinition(def, ASPECTJ_WEAVING_ENABLER_BEAN_NAME));
			}

			if (isBeanConfigurerAspectEnabled(parserContext.getReaderContext().getBeanClassLoader())) {
				new SpringConfiguredBeanDefinitionParser().parse(element, parserContext);
			}
		}
	}

	protected boolean isAspectJWeavingEnabled(String value, ParserContext parserContext) {
		if ("on".equals(value)) {
			return true;
		}
		else if ("off".equals(value)) {
			return false;
		}
		else {
			// Determine default...
			ClassLoader cl = parserContext.getReaderContext().getBeanClassLoader();
			return (cl != null && cl.getResource(AspectJWeavingEnabler.ASPECTJ_AOP_XML_RESOURCE) != null);
		}
	}

	protected boolean isBeanConfigurerAspectEnabled(@Nullable ClassLoader beanClassLoader) {
		return ClassUtils.isPresent(SpringConfiguredBeanDefinitionParser.BEAN_CONFIGURER_ASPECT_CLASS_NAME,
				beanClassLoader);
	}

}
