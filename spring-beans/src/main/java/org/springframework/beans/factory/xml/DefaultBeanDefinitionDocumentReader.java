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

package org.springframework.beans.factory.xml;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;

/**
 * Default implementation of the {@link BeanDefinitionDocumentReader} interface that
 * reads bean definitions according to the "spring-beans" DTD and XSD format
 * (Spring's default XML bean definition format).
 *
 * <p>The structure, elements, and attribute names of the required XML document
 * are hard-coded in this class. (Of course a transform could be run if necessary
 * to produce this format). {@code <beans>} does not need to be the root
 * element of the XML document: this class will parse all bean definition elements
 * in the XML file, regardless of the actual root element.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Erik Wiersma
 * @since 18.12.2003
 */
public class DefaultBeanDefinitionDocumentReader implements BeanDefinitionDocumentReader {

	public static final String BEAN_ELEMENT = BeanDefinitionParserDelegate.BEAN_ELEMENT;

	public static final String NESTED_BEANS_ELEMENT = "beans";

	public static final String ALIAS_ELEMENT = "alias";

	public static final String NAME_ATTRIBUTE = "name";

	public static final String ALIAS_ATTRIBUTE = "alias";

	public static final String IMPORT_ELEMENT = "import";

	public static final String RESOURCE_ATTRIBUTE = "resource";

	public static final String PROFILE_ATTRIBUTE = "profile";


	protected final Log logger = LogFactory.getLog(getClass());

	@Nullable
	private XmlReaderContext readerContext;

	@Nullable
	private BeanDefinitionParserDelegate delegate;


	/**
	 * This implementation parses bean definitions according to the "spring-beans" XSD
	 * (or DTD, historically).
	 * <p>Opens a DOM Document; then initializes the default settings
	 * specified at the {@code <beans/>} level; then parses the contained bean definitions.
	 *
	 * 其中的参数doc是通过loadDocument加载转换出来的。在这个方法中很好地应用
	 * 了面向对象中单一职责的原则，将逻辑处理委托给单一的类进行处理，而这个逻辑处理类就是
	 * BeanDefinitionDocumentReader。BeanDefinitionDocumentReader是一个接口，而实例化的工作是在
	 * createBeanDefinitionDocumentReader()中完成的，而通过此方法，BeanDefinitionDocumentReader真正
	 * 的类型其实已经是 DefaultBeanDefinitionDocumentReader 了，进入DefaultBeanDefinitionDocumentReader后，
	 * 发现这个方法的重要目的之一就是提取root，以便于再次将root作为参数继续
	 * BeanDefinition的注册
	 */
	@Override
	public void registerBeanDefinitions(Document doc, XmlReaderContext readerContext) {
		this.readerContext = readerContext;
		//Element root = doc.getDocumentElement();
		doRegisterBeanDefinitions(doc.getDocumentElement());
	}

	/**
	 * Return the descriptor for the XML resource that this parser works on.
	 */
	protected final XmlReaderContext getReaderContext() {
		Assert.state(this.readerContext != null, "No XmlReaderContext available");
		return this.readerContext;
	}

	/**
	 * Invoke the {@link org.springframework.beans.factory.parsing.SourceExtractor}
	 * to pull the source metadata from the supplied {@link Element}.
	 */
	@Nullable
	protected Object extractSource(Element ele) {
		return getReaderContext().extractSource(ele);
	}


	/**
	 * Register each bean definition within the given root {@code <beans/>} element.
	 */
	@SuppressWarnings("deprecation")  // for Environment.acceptsProfiles(String...)
	protected void doRegisterBeanDefinitions(Element root) {
		// Any nested <beans> elements will cause recursion in this method. In
		// order to propagate and preserve <beans> default-* attributes correctly,
		// keep track of the current (parent) delegate, which may be null. Create
		// the new (child) delegate with a reference to the parent for fallback purposes,
		// then ultimately reset this.delegate back to its original (parent) reference.
		// this behavior emulates a stack of delegates without actually necessitating one.
		// 任何嵌套的＜beans＞元素都将导致此方法中的递归。为了正确传播和保存＜beans＞默认属性，
		// 请跟踪当前（父）委托，该委托可能为空。创建新的（子）委托，并将其引用到父级以进行回退，
		// 然后最终将this.delegate重置回其原始（父级）引用。这种行为模拟一堆委托，而实际上不需要委托。

		//专门处理解析
		BeanDefinitionParserDelegate parent = this.delegate;
		this.delegate = createDelegate(getReaderContext(), root, parent);

		if (this.delegate.isDefaultNamespace(root)) {
			//处理profile属性
			//<beans profile="dev">
			//...
			//</beans>
			//<beans profile="production">
			//...
			//</beans>
			//集成到Web环境中时，在web.xml中加入以下代码：
			//<comtext-param>
			//  <param-name> Spring.profiles.active</param-name>
			//  <param-value> dev</param-value>
			//</comtext-param>
			//有了这个特性我们就可以同时在配置文件中部署两套配置来适用于生产环境和开发环境，
			//这样可以方便的进行切换开发、部署环境，最常用的就是更换不同的数据库。

			//了解了profile的使用再来分析代码会清晰得多，首先程序会获取beans节点是否定义了
			//profile属性，如果定义了则会需要到环境变量中去寻找，所以这里首先断言environment不可
			//能为空，因为profile是可以同时指定多个的，需要程序对其拆分，并解析每个profile是都符
			//合环境变量中所定义的，不定义则不会浪费性能去解析。
			String profileSpec = root.getAttribute(PROFILE_ATTRIBUTE);
			if (StringUtils.hasText(profileSpec)) {
				String[] specifiedProfiles = StringUtils.tokenizeToStringArray(
						profileSpec, BeanDefinitionParserDelegate.MULTI_VALUE_ATTRIBUTE_DELIMITERS);
				// We cannot use Profiles.of(...) since profile expressions are not supported
				// in XML config. See SPR-12458 for details.
				if (!getReaderContext().getEnvironment().acceptsProfiles(specifiedProfiles)) {
					if (logger.isDebugEnabled()) {
						logger.debug("Skipped XML bean definition file due to specified profiles [" + profileSpec +
								"] not matching: " + getReaderContext().getResource());
					}
					return;
				}
			}
		}
		//解析前处理，留给子类实现
		preProcessXml(root);
		parseBeanDefinitions(root, this.delegate);
		//解析后处理，留给子类实现
		postProcessXml(root);

		this.delegate = parent;
	}

	protected BeanDefinitionParserDelegate createDelegate(
			XmlReaderContext readerContext, Element root, @Nullable BeanDefinitionParserDelegate parentDelegate) {

		BeanDefinitionParserDelegate delegate = new BeanDefinitionParserDelegate(readerContext);
		delegate.initDefaults(root, parentDelegate);
		return delegate;
	}

	/**
	 * Parse the elements at the root level in the document:
	 * "import", "alias", "bean".
	 * @param root the DOM root element of the document
	 *
	 * Spring 的xml 配置里面有两大类Bean声明，一个是默认的：
	 * <bean id="test" class="test.TestBean"></bean>
	 * 另一类就是自定义的，如：
	 * <tx:annotation-driven/>
	 */
	protected void parseBeanDefinitions(Element root, BeanDefinitionParserDelegate delegate) {
		//对beans的处理
		if (delegate.isDefaultNamespace(root)) {
			NodeList nl = root.getChildNodes();
			for (int i = 0; i < nl.getLength(); i++) {
				Node node = nl.item(i);
				if (node instanceof Element) {
					Element ele = (Element) node;
					if (delegate.isDefaultNamespace(ele)) {
						//对bean的处理
						//而两种方式的读取及解析差别是非常大的，如果采用spring默认的配置，spring当然知
						//道该怎么做，但是如果是自定义的，那么就需要用户实现一些接口及配置了。

						// 对于根节点或者子节点如果是默认命名空间的话则采用parseDefaultElement方法进行解析，否则使用
						//delegate.parseCustomElement方法对自定义命名空间进行解析。而判断是否默认命名空间还是
						//自定义命名空间的办法其实是使用node.getNamespaceURI()获取命名空间，并与spring中固
						//定的命名空间http://www.springframework.org/schema/beans进行比对。如果一致则认为是默
						//认，否则就认为是自定义。而对于默认标签解析与自定义标签解析我们将会在下一章中进行讨论。
						parseDefaultElement(ele, delegate);
					}
					else {
						//对bean的处理 自定义标签解析
						//当 Spring 拿到一个元素时首先要做的是根据命名空间进行解析，
						//如果是默认的命名空间，则使用 parseDefaultElement 方法进行元素解析，否则使用 parseCustomElement
						//方法进行解析。
						//在很多情况下，我们需要为系统提供可配置化支持，简单的做法可以直接基于Spring的标
						//准bean来配置，但配置较为复杂或者需要更多丰富控制的时候，会显得非常笨拙。一般的做
						//法会用原生态的方式去解析定义好的XML文件，然后转化为配置对象。这种方式当然可以解
						//决所有问题，但实现起来比较烦琐，特别是在配置非常复杂的时候，解析工作是一个不得不考
						//虑的负担。spring提供了可扩展Schema的支持，这是一个不错的折中方案，扩展Spring自定
						//义标签配置大致需要以下几个步骤（前提是要把Spring的Core包加人项目中）。
						//1、创建一个需要扩展的组件。
						//2、定义一个XSD文件描述组件内容。
						//3、创建一个文件，实现BeanDefinitionParser接口，用来解析XSD文件中的定义和组件定义。
						//4、创建一个Handler文件，扩展自NamespaceHandlerSupport，目的是将组件注册到Spring容器。
						//5、编写Spring.handlers和Spring.schemas文件。
						delegate.parseCustomElement(ele);
					}
				}
			}
		}
		else {
			delegate.parseCustomElement(root);
		}
	}

	private void parseDefaultElement(Element ele, BeanDefinitionParserDelegate delegate) {
		//对import标签的处理
		if (delegate.nodeNameEquals(ele, IMPORT_ELEMENT)) {
			//使用import 是一个好办法，例如我们可以构造如下的Spring配置文件
			//<?xml version="1.0" encoding="UTF-8"?>
			//<!DOCTYPE beans PUBLIC "-//SPRING//DTD BEAN 2.0//EN" "https://www.springframework.org/dtd/spring-beans-2.0.dtd">
			//  <beans>
			//    <import resource="customerContext.xml"/>
			//    <import resource="systemContext.xml"/>
			//  </beans>
			//applicationContext.xml文件中使用import的方式导入所有模块配置文件，以后若有新模块的
			//加入，那就可以简单修改这个文件了。这样大大简化了配置后期维护的复杂度
			importBeanDefinitionResource(ele);
		}
		//对alias标签的处理
		//别名的配置方式：
		//1、<bean id="testBean" name="testBean,testBean2" class="com.test"/>
		//2、<bean id="testBean" class="com.test"/>
		//   <alias name="testBean" alias="testBean,testBean2"/>
		//3、考虑一个更为具体的例子，组件A在XML配置文件中定义了一个名为componentA的
		//Datasource类型的bean，但组件B却想在其XML文件中以componentB命名来引用此bean。
		//而且在主程序MyApp的XML配置文件中，希望以myApp的名字来引用此bean。最后容器加
		//载3个XML文件来生成最终的ApplicationContext。在此情形下，可通过在配置文件中添加下
		//列alias元素来实现：
		//<alias name="componentA" alias="componentB"/>
		//<alias name="componentA" alias="myApp"/>
		else if (delegate.nodeNameEquals(ele, ALIAS_ELEMENT)) {
			processAliasRegistration(ele);
		}
		//对bean标签的处理
		else if (delegate.nodeNameEquals(ele, BEAN_ELEMENT)) {
			processBeanDefinition(ele, delegate);
		}
		//对beans标签的处理
		else if (delegate.nodeNameEquals(ele, NESTED_BEANS_ELEMENT)) {
			// recurse
			doRegisterBeanDefinitions(ele);
		}
	}

	/**
	 * Parse an "import" element and load the bean definitions
	 * from the given resource into the bean factory.
	 *
	 * 			//使用import 是一个好办法，例如我们可以构造如下的Spring配置文件
	 * 			//<?xml version="1.0" encoding="UTF-8"?>
	 * 			//<!DOCTYPE beans PUBLIC "-//SPRING//DTD BEAN 2.0//EN" "https://www.springframework.org/dtd/spring-beans-2.0.dtd">
	 * 			//  <beans>
	 * 			//    <import resource="customerContext.xml"/>
	 * 			//    <import resource="systemContext.xml"/>
	 * 			//  </beans>
	 * 			//applicationContext.xml文件中使用import的方式导入所有模块配置文件，以后若有新模块的
	 * 			//加入，那就可以简单修改这个文件了。这样大大简化了配置后期维护的复杂度
	 *
	 * 1.获取resource属性所表示的路径。
	 * 2．解析路径中的系统属性，格式如"S{user.dir}".
	 * 3．判定location是绝对路径还是相对路径。
	 * 4．如果是绝对路径则递归调用bean的解析过程，进行另一次的解析。
	 * 5．如果是相对路径则计算出绝对路径并进行解析。
	 * 6．通知监听器，解析完成。
	 */
	protected void importBeanDefinitionResource(Element ele) {
		//获取resource属性
		String location = ele.getAttribute(RESOURCE_ATTRIBUTE);
		//如果不存在resource属性则不做任何处理
		if (!StringUtils.hasText(location)) {
			getReaderContext().error("Resource location must not be empty", ele);
			return;
		}

		// Resolve system properties: e.g. "${user.dir}"
		//解析系统属性，格式如："${user.dir}"
		location = getReaderContext().getEnvironment().resolveRequiredPlaceholders(location);

		Set<Resource> actualResources = new LinkedHashSet<>(4);

		// Discover whether the location is an absolute or relative URI
		//判定localtion 是决定URI还是相对URI
		boolean absoluteLocation = false;
		try {
			absoluteLocation = ResourcePatternUtils.isUrl(location) || ResourceUtils.toURI(location).isAbsolute();
		}
		catch (URISyntaxException ex) {
			// cannot convert to an URI, considering the location relative
			// unless it is the well-known Spring prefix "classpath*:"
		}

		// Absolute or relative?
		// 如果是绝对URI则直接根据地址加载对应的配置文件
		if (absoluteLocation) {
			try {
				int importCount = getReaderContext().getReader().loadBeanDefinitions(location, actualResources);
				if (logger.isTraceEnabled()) {
					logger.trace("Imported " + importCount + " bean definitions from URL location [" + location + "]");
				}
			}
			catch (BeanDefinitionStoreException ex) {
				getReaderContext().error(
						"Failed to import bean definitions from URL location [" + location + "]", ele, ex);
			}
		}
		else {
			// No URL -> considering resource location as relative to the current file.
			// 如果是相对地址则根据相对地址计算出绝对地址
			try {
				int importCount;
				////Resource存在多个子实现类，如VfsResource、FileSystemResource等，
				//而每个resource的createRelative方式实现都不一样，所以这里先使用子类的方法尝
				//试解析
				Resource relativeResource = getReaderContext().getResource().createRelative(location);
				if (relativeResource.exists()) {
					importCount = getReaderContext().getReader().loadBeanDefinitions(relativeResource);
					actualResources.add(relativeResource);
				}
				else {
					//如果解析不成功，则使用默认的解析器ResourcePatternResolver进行解析。
					String baseLocation = getReaderContext().getResource().getURL().toString();
					importCount = getReaderContext().getReader().loadBeanDefinitions(
							StringUtils.applyRelativePath(baseLocation, location), actualResources);
				}
				if (logger.isTraceEnabled()) {
					logger.trace("Imported " + importCount + " bean definitions from relative location [" + location + "]");
				}
			}
			catch (IOException ex) {
				getReaderContext().error("Failed to resolve current resource location", ele, ex);
			}
			catch (BeanDefinitionStoreException ex) {
				getReaderContext().error(
						"Failed to import bean definitions from relative location [" + location + "]", ele, ex);
			}
		}
		//解析后进行监听器激活处理
		Resource[] actResArray = actualResources.toArray(new Resource[0]);
		getReaderContext().fireImportProcessed(location, actResArray, extractSource(ele));
	}

	/**
	 * Process the given alias element, registering the alias with the registry.
	 */
	protected void processAliasRegistration(Element ele) {
		//获取beanName
		String name = ele.getAttribute(NAME_ATTRIBUTE);
		//获取alias
		String alias = ele.getAttribute(ALIAS_ATTRIBUTE);
		boolean valid = true;
		if (!StringUtils.hasText(name)) {
			getReaderContext().error("Name must not be empty", ele);
			valid = false;
		}
		if (!StringUtils.hasText(alias)) {
			getReaderContext().error("Alias must not be empty", ele);
			valid = false;
		}
		if (valid) {
			try {
				//注册alias
				getReaderContext().getRegistry().registerAlias(name, alias);
			}
			catch (Exception ex) {
				getReaderContext().error("Failed to register alias '" + alias +
						"' for bean with name '" + name + "'", ele, ex);
			}
			//别名注册后通知监听器做相应处理
			getReaderContext().fireAliasRegistered(name, alias, extractSource(ele));
		}
	}

	/**
	 * Process the given bean element, parsing the bean definition
	 * and registering it with the registry.
	 *
	 * 1.首先委托BeanDefinitionDelegate类的parseBeanDefinitionElement方法进行元素解析，
	 * 返回BeanDefinitionHolder类型的实例bdHolder，经过这个方法后，bdHolder实例已经包含我
	 * 们配置文件中配置的各种属性了，例如class、name、id、alias之类的属性。
	 *
	 * 2．当返回的bdHolder不为空的情况下若存在默认标签的子节点下再有自定义属性，还需要
	 * 再次对自定义标签进行解析。
	 *
	 * 3．解析完成后，需要对解析后的bdHolder进行注册，同样，注册操作委托给了BeanDefinitionReaderUtils的registerBeanDefinition方法。
	 *
	 * 4．最后发出响应事件，通知相关的监听器，这个bean已经加载完成了。
	 */
	protected void processBeanDefinition(Element ele, BeanDefinitionParserDelegate delegate) {
		BeanDefinitionHolder bdHolder = delegate.parseBeanDefinitionElement(ele);
		if (bdHolder != null) {
			//自定义标签的解析:自定义属性
			//<bean id="test" class="test.MyClass">
			// <mybean:user username="aaa"/>
			//</bean>
			bdHolder = delegate.decorateBeanDefinitionIfRequired(ele, bdHolder);
			try {
				// Register the final decorated instance.
				//注册解析的 BeanDefinition
				BeanDefinitionReaderUtils.registerBeanDefinition(bdHolder, getReaderContext().getRegistry());
			}
			catch (BeanDefinitionStoreException ex) {
				getReaderContext().error("Failed to register bean definition with name '" +
						bdHolder.getBeanName() + "'", ele, ex);
			}
			// Send registration event.
			//通知监听器解析及注册完成
			//这里的实现只为扩展，当程序开发人员需要对注册BeanDefinition事件进行监听
			//时可以通过注册监听器的方式并将处理逻辑写入监听器中，目前在Spring中并没有对此事件做
			//任何逻辑处理。
			getReaderContext().fireComponentRegistered(new BeanComponentDefinition(bdHolder));
		}
	}


	/**
	 * Allow the XML to be extensible by processing any custom element types first,
	 * before we start to process the bean definitions. This method is a natural
	 * extension point for any other custom pre-processing of the XML.
	 * <p>The default implementation is empty. Subclasses can override this method to
	 * convert custom elements into standard Spring bean definitions, for example.
	 * Implementors have access to the parser's bean definition reader and the
	 * underlying XML resource, through the corresponding accessors.
	 * @see #getReaderContext()
	 */
	protected void preProcessXml(Element root) {
	}

	/**
	 * Allow the XML to be extensible by processing any custom element types last,
	 * after we finished processing the bean definitions. This method is a natural
	 * extension point for any other custom post-processing of the XML.
	 * <p>The default implementation is empty. Subclasses can override this method to
	 * convert custom elements into standard Spring bean definitions, for example.
	 * Implementors have access to the parser's bean definition reader and the
	 * underlying XML resource, through the corresponding accessors.
	 * @see #getReaderContext()
	 */
	protected void postProcessXml(Element root) {
	}

}
