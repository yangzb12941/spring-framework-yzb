/*
 * Copyright 2002-2019 the original author or authors.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.springframework.beans.BeanMetadataAttribute;
import org.springframework.beans.BeanMetadataAttributeAccessor;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.RuntimeBeanNameReference;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.beans.factory.parsing.BeanEntry;
import org.springframework.beans.factory.parsing.ConstructorArgumentEntry;
import org.springframework.beans.factory.parsing.ParseState;
import org.springframework.beans.factory.parsing.PropertyEntry;
import org.springframework.beans.factory.parsing.QualifierEntry;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.AutowireCandidateQualifier;
import org.springframework.beans.factory.support.BeanDefinitionDefaults;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.LookupOverride;
import org.springframework.beans.factory.support.ManagedArray;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.support.ManagedMap;
import org.springframework.beans.factory.support.ManagedProperties;
import org.springframework.beans.factory.support.ManagedSet;
import org.springframework.beans.factory.support.MethodOverrides;
import org.springframework.beans.factory.support.ReplaceOverride;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.PatternMatchUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.xml.DomUtils;

/**
 * Stateful delegate class used to parse XML bean definitions.
 * Intended for use by both the main parser and any extension
 * {@link BeanDefinitionParser BeanDefinitionParsers} or
 * {@link BeanDefinitionDecorator BeanDefinitionDecorators}.
 *
 * 定义解析 Element 的各种方法
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @author Rod Johnson
 * @author Mark Fisher
 * @author Gary Russell
 * @since 2.0
 * @see ParserContext
 * @see DefaultBeanDefinitionDocumentReader
 */
public class BeanDefinitionParserDelegate {

	public static final String BEANS_NAMESPACE_URI = "http://www.springframework.org/schema/beans";

	public static final String MULTI_VALUE_ATTRIBUTE_DELIMITERS = ",; ";

	/**
	 * Value of a T/F attribute that represents true.
	 * Anything else represents false. Case seNsItive.
	 */
	public static final String TRUE_VALUE = "true";

	public static final String FALSE_VALUE = "false";

	public static final String DEFAULT_VALUE = "default";

	public static final String DESCRIPTION_ELEMENT = "description";

	public static final String AUTOWIRE_NO_VALUE = "no";

	public static final String AUTOWIRE_BY_NAME_VALUE = "byName";

	public static final String AUTOWIRE_BY_TYPE_VALUE = "byType";

	public static final String AUTOWIRE_CONSTRUCTOR_VALUE = "constructor";

	public static final String AUTOWIRE_AUTODETECT_VALUE = "autodetect";

	public static final String NAME_ATTRIBUTE = "name";

	public static final String BEAN_ELEMENT = "bean";

	public static final String META_ELEMENT = "meta";

	public static final String ID_ATTRIBUTE = "id";

	public static final String PARENT_ATTRIBUTE = "parent";

	public static final String CLASS_ATTRIBUTE = "class";

	public static final String ABSTRACT_ATTRIBUTE = "abstract";

	public static final String SCOPE_ATTRIBUTE = "scope";

	private static final String SINGLETON_ATTRIBUTE = "singleton";

	public static final String LAZY_INIT_ATTRIBUTE = "lazy-init";

	public static final String AUTOWIRE_ATTRIBUTE = "autowire";

	public static final String AUTOWIRE_CANDIDATE_ATTRIBUTE = "autowire-candidate";

	public static final String PRIMARY_ATTRIBUTE = "primary";

	public static final String DEPENDS_ON_ATTRIBUTE = "depends-on";

	public static final String INIT_METHOD_ATTRIBUTE = "init-method";

	public static final String DESTROY_METHOD_ATTRIBUTE = "destroy-method";

	public static final String FACTORY_METHOD_ATTRIBUTE = "factory-method";

	public static final String FACTORY_BEAN_ATTRIBUTE = "factory-bean";

	public static final String CONSTRUCTOR_ARG_ELEMENT = "constructor-arg";

	public static final String INDEX_ATTRIBUTE = "index";

	public static final String TYPE_ATTRIBUTE = "type";

	public static final String VALUE_TYPE_ATTRIBUTE = "value-type";

	public static final String KEY_TYPE_ATTRIBUTE = "key-type";

	public static final String PROPERTY_ELEMENT = "property";

	public static final String REF_ATTRIBUTE = "ref";

	public static final String VALUE_ATTRIBUTE = "value";

	public static final String LOOKUP_METHOD_ELEMENT = "lookup-method";

	public static final String REPLACED_METHOD_ELEMENT = "replaced-method";

	public static final String REPLACER_ATTRIBUTE = "replacer";

	public static final String ARG_TYPE_ELEMENT = "arg-type";

	public static final String ARG_TYPE_MATCH_ATTRIBUTE = "match";

	public static final String REF_ELEMENT = "ref";

	public static final String IDREF_ELEMENT = "idref";

	public static final String BEAN_REF_ATTRIBUTE = "bean";

	public static final String PARENT_REF_ATTRIBUTE = "parent";

	public static final String VALUE_ELEMENT = "value";

	public static final String NULL_ELEMENT = "null";

	public static final String ARRAY_ELEMENT = "array";

	public static final String LIST_ELEMENT = "list";

	public static final String SET_ELEMENT = "set";

	public static final String MAP_ELEMENT = "map";

	public static final String ENTRY_ELEMENT = "entry";

	public static final String KEY_ELEMENT = "key";

	public static final String KEY_ATTRIBUTE = "key";

	public static final String KEY_REF_ATTRIBUTE = "key-ref";

	public static final String VALUE_REF_ATTRIBUTE = "value-ref";

	public static final String PROPS_ELEMENT = "props";

	public static final String PROP_ELEMENT = "prop";

	public static final String MERGE_ATTRIBUTE = "merge";

	public static final String QUALIFIER_ELEMENT = "qualifier";

	public static final String QUALIFIER_ATTRIBUTE_ELEMENT = "attribute";

	public static final String DEFAULT_LAZY_INIT_ATTRIBUTE = "default-lazy-init";

	public static final String DEFAULT_MERGE_ATTRIBUTE = "default-merge";

	public static final String DEFAULT_AUTOWIRE_ATTRIBUTE = "default-autowire";

	public static final String DEFAULT_AUTOWIRE_CANDIDATES_ATTRIBUTE = "default-autowire-candidates";

	public static final String DEFAULT_INIT_METHOD_ATTRIBUTE = "default-init-method";

	public static final String DEFAULT_DESTROY_METHOD_ATTRIBUTE = "default-destroy-method";


	protected final Log logger = LogFactory.getLog(getClass());

	private final XmlReaderContext readerContext;

	private final DocumentDefaultsDefinition defaults = new DocumentDefaultsDefinition();

	private final ParseState parseState = new ParseState();

	/**
	 * Stores all used bean names so we can enforce uniqueness on a per
	 * beans-element basis. Duplicate bean ids/names may not exist within the
	 * same level of beans element nesting, but may be duplicated across levels.
	 */
	private final Set<String> usedNames = new HashSet<>();


	/**
	 * Create a new BeanDefinitionParserDelegate associated with the supplied
	 * {@link XmlReaderContext}.
	 */
	public BeanDefinitionParserDelegate(XmlReaderContext readerContext) {
		Assert.notNull(readerContext, "XmlReaderContext must not be null");
		this.readerContext = readerContext;
	}


	/**
	 * Get the {@link XmlReaderContext} associated with this helper instance.
	 */
	public final XmlReaderContext getReaderContext() {
		return this.readerContext;
	}

	/**
	 * Invoke the {@link org.springframework.beans.factory.parsing.SourceExtractor}
	 * to pull the source metadata from the supplied {@link Element}.
	 */
	@Nullable
	protected Object extractSource(Element ele) {
		return this.readerContext.extractSource(ele);
	}

	/**
	 * Report an error with the given message for the given source element.
	 */
	protected void error(String message, Node source) {
		this.readerContext.error(message, source, this.parseState.snapshot());
	}

	/**
	 * Report an error with the given message for the given source element.
	 */
	protected void error(String message, Element source) {
		this.readerContext.error(message, source, this.parseState.snapshot());
	}

	/**
	 * Report an error with the given message for the given source element.
	 */
	protected void error(String message, Element source, Throwable cause) {
		this.readerContext.error(message, source, this.parseState.snapshot(), cause);
	}


	/**
	 * Initialize the default settings assuming a {@code null} parent delegate.
	 */
	public void initDefaults(Element root) {
		initDefaults(root, null);
	}

	/**
	 * Initialize the default lazy-init, autowire, dependency check settings,
	 * init-method, destroy-method and merge settings. Support nested 'beans'
	 * element use cases by falling back to the given parent in case the
	 * defaults are not explicitly set locally.
	 * @see #populateDefaults(DocumentDefaultsDefinition, DocumentDefaultsDefinition, org.w3c.dom.Element)
	 * @see #getDefaults()
	 */
	public void initDefaults(Element root, @Nullable BeanDefinitionParserDelegate parent) {
		populateDefaults(this.defaults, (parent != null ? parent.defaults : null), root);
		this.readerContext.fireDefaultsRegistered(this.defaults);
	}

	/**
	 * Populate the given DocumentDefaultsDefinition instance with the default lazy-init,
	 * autowire, dependency check settings, init-method, destroy-method and merge settings.
	 * Support nested 'beans' element use cases by falling back to {@code parentDefaults}
	 * in case the defaults are not explicitly set locally.
	 * @param defaults the defaults to populate
	 * @param parentDefaults the parent BeanDefinitionParserDelegate (if any) defaults to fall back to
	 * @param root the root element of the current bean definition document (or nested beans element)
	 */
	protected void populateDefaults(DocumentDefaultsDefinition defaults, @Nullable DocumentDefaultsDefinition parentDefaults, Element root) {
		String lazyInit = root.getAttribute(DEFAULT_LAZY_INIT_ATTRIBUTE);
		if (isDefaultValue(lazyInit)) {
			// Potentially inherited from outer <beans> sections, otherwise falling back to false.
			lazyInit = (parentDefaults != null ? parentDefaults.getLazyInit() : FALSE_VALUE);
		}
		defaults.setLazyInit(lazyInit);

		String merge = root.getAttribute(DEFAULT_MERGE_ATTRIBUTE);
		if (isDefaultValue(merge)) {
			// Potentially inherited from outer <beans> sections, otherwise falling back to false.
			merge = (parentDefaults != null ? parentDefaults.getMerge() : FALSE_VALUE);
		}
		defaults.setMerge(merge);

		String autowire = root.getAttribute(DEFAULT_AUTOWIRE_ATTRIBUTE);
		if (isDefaultValue(autowire)) {
			// Potentially inherited from outer <beans> sections, otherwise falling back to 'no'.
			autowire = (parentDefaults != null ? parentDefaults.getAutowire() : AUTOWIRE_NO_VALUE);
		}
		defaults.setAutowire(autowire);

		if (root.hasAttribute(DEFAULT_AUTOWIRE_CANDIDATES_ATTRIBUTE)) {
			defaults.setAutowireCandidates(root.getAttribute(DEFAULT_AUTOWIRE_CANDIDATES_ATTRIBUTE));
		}
		else if (parentDefaults != null) {
			defaults.setAutowireCandidates(parentDefaults.getAutowireCandidates());
		}

		if (root.hasAttribute(DEFAULT_INIT_METHOD_ATTRIBUTE)) {
			defaults.setInitMethod(root.getAttribute(DEFAULT_INIT_METHOD_ATTRIBUTE));
		}
		else if (parentDefaults != null) {
			defaults.setInitMethod(parentDefaults.getInitMethod());
		}

		if (root.hasAttribute(DEFAULT_DESTROY_METHOD_ATTRIBUTE)) {
			defaults.setDestroyMethod(root.getAttribute(DEFAULT_DESTROY_METHOD_ATTRIBUTE));
		}
		else if (parentDefaults != null) {
			defaults.setDestroyMethod(parentDefaults.getDestroyMethod());
		}

		defaults.setSource(this.readerContext.extractSource(root));
	}

	/**
	 * Return the defaults definition object.
	 */
	public DocumentDefaultsDefinition getDefaults() {
		return this.defaults;
	}

	/**
	 * Return the default settings for bean definitions as indicated within
	 * the attributes of the top-level {@code <beans/>} element.
	 */
	public BeanDefinitionDefaults getBeanDefinitionDefaults() {
		BeanDefinitionDefaults bdd = new BeanDefinitionDefaults();
		bdd.setLazyInit(TRUE_VALUE.equalsIgnoreCase(this.defaults.getLazyInit()));
		bdd.setAutowireMode(getAutowireMode(DEFAULT_VALUE));
		bdd.setInitMethodName(this.defaults.getInitMethod());
		bdd.setDestroyMethodName(this.defaults.getDestroyMethod());
		return bdd;
	}

	/**
	 * Return any patterns provided in the 'default-autowire-candidates'
	 * attribute of the top-level {@code <beans/>} element.
	 */
	@Nullable
	public String[] getAutowireCandidatePatterns() {
		String candidatePattern = this.defaults.getAutowireCandidates();
		return (candidatePattern != null ? StringUtils.commaDelimitedListToStringArray(candidatePattern) : null);
	}


	/**
	 * Parses the supplied {@code <bean>} element. May return {@code null}
	 * if there were errors during parse. Errors are reported to the
	 * {@link org.springframework.beans.factory.parsing.ProblemReporter}.
	 */
	@Nullable
	public BeanDefinitionHolder parseBeanDefinitionElement(Element ele) {
		return parseBeanDefinitionElement(ele, null);
	}

	/**
	 * Parses the supplied {@code <bean>} element. May return {@code null}
	 * if there were errors during parse. Errors are reported to the
	 * {@link org.springframework.beans.factory.parsing.ProblemReporter}.
	 *
	 * 1.提取元素中的id以及name属性。
	 * 2．进一步解析其他所有属性并统一封装至 GenericBeanDefinition 类型的实例中。
	 * 3．如果检测到bean没有指定beanName，那么使用默认规则为此Bean生成beanName.
	 * 4、将获取到的信息封装到BeanDefinitionHolder的实例中.
	 *
	 */
	@Nullable
	public BeanDefinitionHolder parseBeanDefinitionElement(Element ele, @Nullable BeanDefinition containingBean) {
		//解析id属性
		String id = ele.getAttribute(ID_ATTRIBUTE);
		//解析name属性
		String nameAttr = ele.getAttribute(NAME_ATTRIBUTE);
		//分割name属性
		List<String> aliases = new ArrayList<>();
		if (StringUtils.hasLength(nameAttr)) {
			String[] nameArr = StringUtils.tokenizeToStringArray(nameAttr, MULTI_VALUE_ATTRIBUTE_DELIMITERS);
			aliases.addAll(Arrays.asList(nameArr));
		}

		String beanName = id;
		if (!StringUtils.hasText(beanName) && !aliases.isEmpty()) {
			beanName = aliases.remove(0);
			if (logger.isTraceEnabled()) {
				logger.trace("No XML 'id' specified - using '" + beanName +
						"' as bean name and " + aliases + " as aliases");
			}
		}

		if (containingBean == null) {
			checkNameUniqueness(beanName, aliases, ele);
		}

		AbstractBeanDefinition beanDefinition = parseBeanDefinitionElement(ele, beanName, containingBean);
		if (beanDefinition != null) {
			if (!StringUtils.hasText(beanName)) {
				try {
					//如果不存在beanName那么根据Spring 中提供的命名规则为当前bean生成对应的beanName
					if (containingBean != null) {
						beanName = BeanDefinitionReaderUtils.generateBeanName(
								beanDefinition, this.readerContext.getRegistry(), true);
					}
					else {
						beanName = this.readerContext.generateBeanName(beanDefinition);
						// Register an alias for the plain bean class name, if still possible,
						// if the generator returned the class name plus a suffix.
						// This is expected for Spring 1.2/2.0 backwards compatibility.
						String beanClassName = beanDefinition.getBeanClassName();
						if (beanClassName != null &&
								beanName.startsWith(beanClassName) && beanName.length() > beanClassName.length() &&
								!this.readerContext.getRegistry().isBeanNameInUse(beanClassName)) {
							aliases.add(beanClassName);
						}
					}
					if (logger.isTraceEnabled()) {
						logger.trace("Neither XML 'id' nor 'name' specified - " +
								"using generated bean name [" + beanName + "]");
					}
				}
				catch (Exception ex) {
					error(ex.getMessage(), ele);
					return null;
				}
			}
			String[] aliasesArray = StringUtils.toStringArray(aliases);
			return new BeanDefinitionHolder(beanDefinition, beanName, aliasesArray);
		}

		return null;
	}

	/**
	 * Validate that the specified bean name and aliases have not been used already
	 * within the current level of beans element nesting.
	 */
	protected void checkNameUniqueness(String beanName, List<String> aliases, Element beanElement) {
		String foundName = null;

		if (StringUtils.hasText(beanName) && this.usedNames.contains(beanName)) {
			foundName = beanName;
		}
		if (foundName == null) {
			foundName = CollectionUtils.findFirstMatch(this.usedNames, aliases);
		}
		if (foundName != null) {
			error("Bean name '" + foundName + "' is already used in this <beans> element", beanElement);
		}

		this.usedNames.add(beanName);
		this.usedNames.addAll(aliases);
	}

	/**
	 * Parse the bean definition itself, without regard to name or aliases. May return
	 * {@code null} if problems occurred during the parsing of the bean definition.
	 */
	@Nullable
	public AbstractBeanDefinition parseBeanDefinitionElement(
			Element ele, String beanName, @Nullable BeanDefinition containingBean) {

		this.parseState.push(new BeanEntry(beanName));

		String className = null;
		//解析class属性
		if (ele.hasAttribute(CLASS_ATTRIBUTE)) {
			className = ele.getAttribute(CLASS_ATTRIBUTE).trim();
		}
		String parent = null;
		//解析parent属性
		if (ele.hasAttribute(PARENT_ATTRIBUTE)) {
			parent = ele.getAttribute(PARENT_ATTRIBUTE);
		}

		try {
			//创建用于承载属性的 AbstractBeanDefinition 类型GenericBeanDefinition
			//1. 创建用于属性承载的 BeanDefinition
			//BeanDefinition是一个接口，在Spring中存在三种实现：RootBeanDefinition、ChildBeanDefinition 以及
			// GenericBeanDefinition。三种实现均继承了 AbstractBeanDefinition，其中
			//BeanDefinition 是配置文件<bean>元素标签在容器中的内部表示形式。<bean>元素标签拥有
			//class、scope、lazy-init等配置属性，BeanDefinition则提供了相应的beanClass、scope、lazyInit
			//属性，BeanDefinition和<bean>中的属性是一一对应的。
			//
			// 其中RootBeanDefinition是最常用的实现类，它对应一般性的<bean>元素标签，
			// GenericBeanDefinition是自2.5版本以后新加入的bean文件配置属性定义类，是一站式服务类。

			//在配置文件中可以定义父<bean>和子<bean>，父<bean>用RootBeanDefinition表示，而子
			//<bean>用ChildBeanDefinition表示，而没有父<bean>的<bean>就使用RootBeanDefinition表示。
			//AbstractBeanDefinition对两者共同的类信息进行抽象。

			//spring通过 BeanDefinition 将配置文件中的<bean>配置信息转换为容器的内部表示，并将
			//这些BeanDefinition注册到BeanDefinitionRegistry中。
			// Spring容器的 BeanDefinitionRegistry 就像是spring配置信息的内存数据库，
			// 主要是以map的形式保存，后续操作直接从BeanDefinitionRegistry中读取配置信息。
			// RootBeanDefinition ->
			// ChildBeanDefinition ->       AbstractBeanDefinition -> BeanDefinition
			// GenericBeanDefinition ->
			AbstractBeanDefinition bd = createBeanDefinition(className, parent);
			//硬编码解析默认bean的各种属性
			parseBeanDefinitionAttributes(ele, beanName, containingBean, bd);
			//提取description
			bd.setDescription(DomUtils.getChildElementValueByTagName(ele, DESCRIPTION_ELEMENT));
			//解析元数据
			//<bean id="test" class="test.TestBean">
			//  <meta key="testStr" value="aaaa"/>
			// </bean>
			//这段代码并不会体现在MyTestBean的属性当中，而是一个额外的声明，当需要使用里面
			//的信息的时候可以通过 BeanDefinition 的getAttribute(key)方法进行获取。
			//对meta属性的解析代码如下：
			parseMetaElements(ele, bd);
			//解析lookup-method属性
			//同样，子元素lookup-method似乎并不是很常用，但是在某些时候它的确是非常有用的
			//属性，通常我们称它为获取器注入。引用Spring中的一句话：获取器注入是一种特
			//殊的方法注入，它是把一个方法声明为返回某种类型的bean，但实际要返回的bean是在配
			//置文件里面配置的，此方法可用在设计有些可插拔的功能上，解除程序依赖。我们看看具体
			//的应用。
			//1、首先创建一个父类
			// public class User{
			//   public void showMe(){
			//     System.out.println("aaaa");
			//   }
			// }
			//2、创建其子类并覆盖showMe方法
			//public class Teacher extends User{
			//   public void showMe(){
			//     System.out.println("bbbb");
			//   }
			// }
			//3、创建调用方法
			//public abstract class GetBeanTest{
			//   public void showMe(){
			//     this.getBean().showMe();
			//   }
			//   public abstract User getBean();
			// }
			//<bean id="getBeanTest" class="test.look.app.GetBeanTest">
			//  <lookup—method name ="getBean" bean = teacher"/>
			//</bean>

			//<bean id="teacher" class='test.lookup.bean.Teacher"/>
			//在配置文件中，我们看到了源码解析中提到的lookup-method子元素，这个配置完成的功能是
			//动态地将teacher所代表的bean作为getBean的返回值，运行测试方法我们会看到控制台上的输出：
			//bbbb
			parseLookupOverrideSubElements(ele, bd.getMethodOverrides());
			//解析replaced-method属性
			//方法替换：可以在运行时用新的方法替换现有的方法。与之前的look-up不同的是，
			//replaced-method不但可以动态地替换返回实体bean，而且还能动态地更改原有方法的逻辑。我
			//们来看看使用示例。
			//1、在changeMe中完成某个业务逻辑
			//public class TestChangeMethod{
			//   public void changeMe(){
			//     System.out.println("changeMe");
			//   }
			// }
			//2、在运营一段时间后需要改变原有的业务逻辑。
			//public class TestChangeMethodReplacer implements MethodReplacer{
			//   @Override
			//   public void reimplement(Object obj,Method method,Object[] args) throws Throwable{
			//     System.out.println("我替换了原有的方法");
			//     return null;
			//   }
			// }
			//3、替换后的类生效
			//<bean id="testChangeMethod" class="test.look.app.TestChangeMethod">
			//  <replaced-method name ="changeMe" bean = replacer"/>
			//</bean>
			//<bean id="replacer" class='test.lookup.bean.TestChangeMethodReplacer"/>
			//4、测试
			//public static void main(String[] args){
			//   ApplicationContext bf =
			//   new ClassPathXmlApplicationContext("test/replacemethod/replaceMethodTest.xml")；
			//   TestChangeMethod test = (TestChangeMethod)bf.getBean("testChangeMethod")；
			//   test.changeMe();
			//}
			// 控制台成功打印刷“我替换了原有的方法”.
			// 原理：就是AOP切面的方式。生成 TestChangeMethod 的动态代理，对方法changeMe进行织入。
			parseReplacedMethodSubElements(ele, bd.getMethodOverrides());

			//解析构造函数参数
			//<beans>
			//  <!-- 默认的情况下是按照参数的顺序注入，当指定index索引后就可以改变注入参数的顺序 -->
			//  <bean id="hel1oBean" class="com.HelloBean"
			//  <constructor-arg>
			//    <value>郝佳</value>
			//  </constructor-arg>
			//  <constructor-arg>
			//    <value>你好</value>
			//  </constructor-arg>
			//</bean>
			parseConstructorArgElements(ele, bd);
			//解析property子元素
			//<bean id="testClass" class="test.look.app.TestClass">
			//  <property name ="testStr" value = aaa"/>
			//</bean>
			//或者
			//<bean id="testClass" class="test.look.app.TestClass">
			//  <property name ="p"/>
			//    <list>
			//      <value> aa </value>
			//      <value> bb </value>
			//    </list>
			//  </property>
			//</bean>
			parsePropertyElements(ele, bd);
			//解析 qualifier 子元素
			//对于 qualifier 元素的获取，我们接触更多的是注解的形式，在使用spring框架中进行自动
			//注入时，spring容器中匹配的候选Bean数目必须有且仅有一个。当找不到一个匹配的Bean时，
			//Spring容器将抛出BeanCreationException异常，并指出必须至少拥有一个匹配的Bean。
			//Spring 允许我们通过 Qualifier 指定注入Bean的名称，这样歧义就消除了，而对于配置方式使用如下：
			//<bean id="myTestBean" Class="bean.MyTeStBean">
			//  <qualifier type="org.springframework.beans.factory.annotation.Qualifier" value="qf"/>
			//</bean>
			parseQualifierElements(ele, bd);

			bd.setResource(this.readerContext.getResource());
			bd.setSource(extractSource(ele));

			return bd;
		}
		catch (ClassNotFoundException ex) {
			error("Bean class [" + className + "] not found", ele, ex);
		}
		catch (NoClassDefFoundError err) {
			error("Class that bean class [" + className + "] depends on not found", ele, err);
		}
		catch (Throwable ex) {
			error("Unexpected failure during bean definition parsing", ele, ex);
		}
		finally {
			this.parseState.pop();
		}

		return null;
	}

	/**
	 * Apply the attributes of the given bean element to the given bean * definition.
	 * @param ele bean declaration element
	 * @param beanName bean name
	 * @param containingBean containing bean definition
	 * @return a bean definition initialized according to the bean element attributes
	 */
	public AbstractBeanDefinition parseBeanDefinitionAttributes(Element ele, String beanName,
			@Nullable BeanDefinition containingBean, AbstractBeanDefinition bd) {

		if (ele.hasAttribute(SINGLETON_ATTRIBUTE)) {
			// scope 与 singleton 个属性只能指定其中之一，不可以同时出现，否则Spring 将会报出异常
			error("Old 1.x 'singleton' attribute in use - upgrade to 'scope' declaration", ele);
		}
		//scope
		else if (ele.hasAttribute(SCOPE_ATTRIBUTE)) {
			bd.setScope(ele.getAttribute(SCOPE_ATTRIBUTE));
		}
		else if (containingBean != null) {
			// Take default from containing bean in case of an inner bean definition.
			//在嵌入 BeanDefinition 情况下且没有单独指定scope属性则使用父类默认的属性
			bd.setScope(containingBean.getScope());
		}

		//abstract
		if (ele.hasAttribute(ABSTRACT_ATTRIBUTE)) {
			bd.setAbstract(TRUE_VALUE.equals(ele.getAttribute(ABSTRACT_ATTRIBUTE)));
		}

		String lazyInit = ele.getAttribute(LAZY_INIT_ATTRIBUTE);
		if (isDefaultValue(lazyInit)) {
			lazyInit = this.defaults.getLazyInit();
		}
		//若没有设置成其他字符都会被设置为false
		bd.setLazyInit(TRUE_VALUE.equals(lazyInit));

		//autowire
		String autowire = ele.getAttribute(AUTOWIRE_ATTRIBUTE);
		bd.setAutowireMode(getAutowireMode(autowire));

		if (ele.hasAttribute(DEPENDS_ON_ATTRIBUTE)) {
			String dependsOn = ele.getAttribute(DEPENDS_ON_ATTRIBUTE);
			bd.setDependsOn(StringUtils.tokenizeToStringArray(dependsOn, MULTI_VALUE_ATTRIBUTE_DELIMITERS));
		}

		String autowireCandidate = ele.getAttribute(AUTOWIRE_CANDIDATE_ATTRIBUTE);
		if (isDefaultValue(autowireCandidate)) {
			String candidatePattern = this.defaults.getAutowireCandidates();
			if (candidatePattern != null) {
				String[] patterns = StringUtils.commaDelimitedListToStringArray(candidatePattern);
				bd.setAutowireCandidate(PatternMatchUtils.simpleMatch(patterns, beanName));
			}
		}
		else {
			bd.setAutowireCandidate(TRUE_VALUE.equals(autowireCandidate));
		}

		if (ele.hasAttribute(PRIMARY_ATTRIBUTE)) {
			bd.setPrimary(TRUE_VALUE.equals(ele.getAttribute(PRIMARY_ATTRIBUTE)));
		}

		if (ele.hasAttribute(INIT_METHOD_ATTRIBUTE)) {
			String initMethodName = ele.getAttribute(INIT_METHOD_ATTRIBUTE);
			bd.setInitMethodName(initMethodName);
		}
		else if (this.defaults.getInitMethod() != null) {
			bd.setInitMethodName(this.defaults.getInitMethod());
			bd.setEnforceInitMethod(false);
		}

		if (ele.hasAttribute(DESTROY_METHOD_ATTRIBUTE)) {
			String destroyMethodName = ele.getAttribute(DESTROY_METHOD_ATTRIBUTE);
			bd.setDestroyMethodName(destroyMethodName);
		}
		else if (this.defaults.getDestroyMethod() != null) {
			bd.setDestroyMethodName(this.defaults.getDestroyMethod());
			bd.setEnforceDestroyMethod(false);
		}

		if (ele.hasAttribute(FACTORY_METHOD_ATTRIBUTE)) {
			bd.setFactoryMethodName(ele.getAttribute(FACTORY_METHOD_ATTRIBUTE));
		}
		if (ele.hasAttribute(FACTORY_BEAN_ATTRIBUTE)) {
			bd.setFactoryBeanName(ele.getAttribute(FACTORY_BEAN_ATTRIBUTE));
		}

		return bd;
	}

	/**
	 * Create a bean definition for the given class name and parent name.
	 * @param className the name of the bean class
	 * @param parentName the name of the bean's parent bean
	 * @return the newly created bean definition
	 * @throws ClassNotFoundException if bean class resolution was attempted but failed
	 */
	protected AbstractBeanDefinition createBeanDefinition(@Nullable String className, @Nullable String parentName)
			throws ClassNotFoundException {

		return BeanDefinitionReaderUtils.createBeanDefinition(
				parentName, className, this.readerContext.getBeanClassLoader());
	}

	/**
	 * Parse the meta elements underneath the given element, if any.
	 */
	public void parseMetaElements(Element ele, BeanMetadataAttributeAccessor attributeAccessor) {
		NodeList nl = ele.getChildNodes();
		for (int i = 0; i < nl.getLength(); i++) {
			Node node = nl.item(i);
			if (isCandidateElement(node) && nodeNameEquals(node, META_ELEMENT)) {
				Element metaElement = (Element) node;
				String key = metaElement.getAttribute(KEY_ATTRIBUTE);
				String value = metaElement.getAttribute(VALUE_ATTRIBUTE);
				//使用 key、value 构造 BeanMetadataAttribute
				BeanMetadataAttribute attribute = new BeanMetadataAttribute(key, value);
				attribute.setSource(extractSource(metaElement));
				//记录信息
				attributeAccessor.addMetadataAttribute(attribute);
			}
		}
	}

	/**
	 * Parse the given autowire attribute value into
	 * {@link AbstractBeanDefinition} autowire constants.
	 */
	@SuppressWarnings("deprecation")
	public int getAutowireMode(String attrValue) {
		String attr = attrValue;
		if (isDefaultValue(attr)) {
			attr = this.defaults.getAutowire();
		}
		int autowire = AbstractBeanDefinition.AUTOWIRE_NO;
		if (AUTOWIRE_BY_NAME_VALUE.equals(attr)) {
			autowire = AbstractBeanDefinition.AUTOWIRE_BY_NAME;
		}
		else if (AUTOWIRE_BY_TYPE_VALUE.equals(attr)) {
			autowire = AbstractBeanDefinition.AUTOWIRE_BY_TYPE;
		}
		else if (AUTOWIRE_CONSTRUCTOR_VALUE.equals(attr)) {
			autowire = AbstractBeanDefinition.AUTOWIRE_CONSTRUCTOR;
		}
		else if (AUTOWIRE_AUTODETECT_VALUE.equals(attr)) {
			autowire = AbstractBeanDefinition.AUTOWIRE_AUTODETECT;
		}
		// Else leave default value.
		return autowire;
	}

	/**
	 * Parse constructor-arg sub-elements of the given bean element.
	 */
	public void parseConstructorArgElements(Element beanEle, BeanDefinition bd) {
		NodeList nl = beanEle.getChildNodes();
		for (int i = 0; i < nl.getLength(); i++) {
			Node node = nl.item(i);
			if (isCandidateElement(node) && nodeNameEquals(node, CONSTRUCTOR_ARG_ELEMENT)) {
				//解析 constructor-arg
				parseConstructorArgElement((Element) node, bd);
			}
		}
	}

	/**
	 * Parse property sub-elements of the given bean element.
	 */
	public void parsePropertyElements(Element beanEle, BeanDefinition bd) {
		NodeList nl = beanEle.getChildNodes();
		for (int i = 0; i < nl.getLength(); i++) {
			Node node = nl.item(i);
			if (isCandidateElement(node) && nodeNameEquals(node, PROPERTY_ELEMENT)) {
				parsePropertyElement((Element) node, bd);
			}
		}
	}

	/**
	 * Parse qualifier sub-elements of the given bean element.
	 */
	public void parseQualifierElements(Element beanEle, AbstractBeanDefinition bd) {
		NodeList nl = beanEle.getChildNodes();
		for (int i = 0; i < nl.getLength(); i++) {
			Node node = nl.item(i);
			if (isCandidateElement(node) && nodeNameEquals(node, QUALIFIER_ELEMENT)) {
				parseQualifierElement((Element) node, bd);
			}
		}
	}

	/**
	 * Parse lookup-override sub-elements of the given bean element.
	 */
	public void parseLookupOverrideSubElements(Element beanEle, MethodOverrides overrides) {
		NodeList nl = beanEle.getChildNodes();
		for (int i = 0; i < nl.getLength(); i++) {
			Node node = nl.item(i);
			//仅当在spring默认bean的子元素下且为 <lookup-method 时有效
			if (isCandidateElement(node) && nodeNameEquals(node, LOOKUP_METHOD_ELEMENT)) {
				Element ele = (Element) node;
				//获取要修饰的方法
				String methodName = ele.getAttribute(NAME_ATTRIBUTE);
				//获取配置返回的bean
				String beanRef = ele.getAttribute(BEAN_ELEMENT);
				LookupOverride override = new LookupOverride(methodName, beanRef);
				override.setSource(extractSource(ele));
				overrides.addOverride(override);
			}
		}
	}

	/**
	 * Parse replaced-method sub-elements of the given bean element.
	 */
	public void parseReplacedMethodSubElements(Element beanEle, MethodOverrides overrides) {
		NodeList nl = beanEle.getChildNodes();
		for (int i = 0; i < nl.getLength(); i++) {
			Node node = nl.item(i);
			//仅当在 Spring默认bean的子元素下且为<replaced-method时有效
			if (isCandidateElement(node) && nodeNameEquals(node, REPLACED_METHOD_ELEMENT)) {
				Element replacedMethodEle = (Element) node;
				//提取要替换的旧的方法
				String name = replacedMethodEle.getAttribute(NAME_ATTRIBUTE);
				//提取对应的新方法的替换方法
				String callback = replacedMethodEle.getAttribute(REPLACER_ATTRIBUTE);
				ReplaceOverride replaceOverride = new ReplaceOverride(name, callback);
				// Look for arg-type match elements.
				List<Element> argTypeEles = DomUtils.getChildElementsByTagName(replacedMethodEle, ARG_TYPE_ELEMENT);
				for (Element argTypeEle : argTypeEles) {
					String match = argTypeEle.getAttribute(ARG_TYPE_MATCH_ATTRIBUTE);
					//记录参数
					match = (StringUtils.hasText(match) ? match : DomUtils.getTextValue(argTypeEle));
					if (StringUtils.hasText(match)) {
						replaceOverride.addTypeIdentifier(match);
					}
				}
				replaceOverride.setSource(extractSource(replacedMethodEle));
				overrides.addOverride(replaceOverride);
			}
		}
	}

	/**
	 * Parse a constructor-arg element.
	 * 涉及的逻辑其实并不复杂，首先是提取constructor-arg上必要的属性（index、type、name)。
	 * a、如果配置中指定了index属性，那么操作步骤如下。
	 * 1.解析Constructor-arg的子元素。
	 * 2．使用ConstructorArgumentValues.ValueHolder类型来封装解析出来的元素。
	 * 3、将type、name和index属性一并封装在ConstructorArgumentValues.ValueHolder类型中
	 * 并添加至当前BeanDefinition的constructorArgumentValues的indexedArgumentValues属性中
	 * b、如果没有指定index属性，那么操作步骤如下。
	 * 1.解析constructor-arg的子元素。
	 * 2．使用ConstructorArgumentValues.ValueHolder类型来封装解析出来的元素。
	 * 3．将type、name和index属性一并封装在ConstructorArgumentValues.ValueHolder类型中
	 * 并添加至当前BeanDefinition的constructorArgumentValues的genericArgumentValues属性中
	 * 可以看到，对于是否制定index属性来讲，Spring的处理流程是不同的，关键在于属性信
	 * 息被保存的位置。
	 * 那么了解了整个流程后，我们尝试着进一步了解解析构造函数配置中子元素的过程，进入 parsePropertyValue：
	 */
	public void parseConstructorArgElement(Element ele, BeanDefinition bd) {
		//提取index属性
		String indexAttr = ele.getAttribute(INDEX_ATTRIBUTE);
		//提取type属性
		String typeAttr = ele.getAttribute(TYPE_ATTRIBUTE);
		//提取name属性
		String nameAttr = ele.getAttribute(NAME_ATTRIBUTE);
		if (StringUtils.hasLength(indexAttr)) {
			try {
				int index = Integer.parseInt(indexAttr);
				if (index < 0) {
					error("'index' cannot be lower than 0", ele);
				}
				else {
					try {
						this.parseState.push(new ConstructorArgumentEntry(index));
						//解析ele对应的属性元素
						Object value = parsePropertyValue(ele, bd, null);
						ConstructorArgumentValues.ValueHolder valueHolder = new ConstructorArgumentValues.ValueHolder(value);
						if (StringUtils.hasLength(typeAttr)) {
							valueHolder.setType(typeAttr);
						}
						if (StringUtils.hasLength(nameAttr)) {
							valueHolder.setName(nameAttr);
						}
						valueHolder.setSource(extractSource(ele));
						//不允许重复指定相同参数
						if (bd.getConstructorArgumentValues().hasIndexedArgumentValue(index)) {
							error("Ambiguous constructor-arg entries for index " + index, ele);
						}
						else {
							bd.getConstructorArgumentValues().addIndexedArgumentValue(index, valueHolder);
						}
					}
					finally {
						this.parseState.pop();
					}
				}
			}
			catch (NumberFormatException ex) {
				error("Attribute 'index' of tag 'constructor-arg' must be an integer", ele);
			}
		}
		else {
			try {
				//没有index属性则忽略去属性，自动寻找
				this.parseState.push(new ConstructorArgumentEntry());
				Object value = parsePropertyValue(ele, bd, null);
				ConstructorArgumentValues.ValueHolder valueHolder = new ConstructorArgumentValues.ValueHolder(value);
				if (StringUtils.hasLength(typeAttr)) {
					valueHolder.setType(typeAttr);
				}
				if (StringUtils.hasLength(nameAttr)) {
					valueHolder.setName(nameAttr);
				}
				valueHolder.setSource(extractSource(ele));
				bd.getConstructorArgumentValues().addGenericArgumentValue(valueHolder);
			}
			finally {
				this.parseState.pop();
			}
		}
	}

	/**
	 * Parse a property element.
	 */
	public void parsePropertyElement(Element ele, BeanDefinition bd) {
		//获取配置元素中name的值
		String propertyName = ele.getAttribute(NAME_ATTRIBUTE);
		if (!StringUtils.hasLength(propertyName)) {
			error("Tag 'property' must have a 'name' attribute", ele);
			return;
		}
		this.parseState.push(new PropertyEntry(propertyName));
		try {
			//不允许多次对同一属性配置
			if (bd.getPropertyValues().contains(propertyName)) {
				error("Multiple 'property' definitions for property '" + propertyName + "'", ele);
				return;
			}
			Object val = parsePropertyValue(ele, bd, propertyName);
			PropertyValue pv = new PropertyValue(propertyName, val);
			parseMetaElements(ele, pv);
			pv.setSource(extractSource(ele));
			bd.getPropertyValues().addPropertyValue(pv);
		}
		finally {
			this.parseState.pop();
		}
	}

	/**
	 * Parse a qualifier element.
	 */
	public void parseQualifierElement(Element ele, AbstractBeanDefinition bd) {
		String typeName = ele.getAttribute(TYPE_ATTRIBUTE);
		if (!StringUtils.hasLength(typeName)) {
			error("Tag 'qualifier' must have a 'type' attribute", ele);
			return;
		}
		this.parseState.push(new QualifierEntry(typeName));
		try {
			AutowireCandidateQualifier qualifier = new AutowireCandidateQualifier(typeName);
			qualifier.setSource(extractSource(ele));
			String value = ele.getAttribute(VALUE_ATTRIBUTE);
			if (StringUtils.hasLength(value)) {
				qualifier.setAttribute(AutowireCandidateQualifier.VALUE_KEY, value);
			}
			NodeList nl = ele.getChildNodes();
			for (int i = 0; i < nl.getLength(); i++) {
				Node node = nl.item(i);
				if (isCandidateElement(node) && nodeNameEquals(node, QUALIFIER_ATTRIBUTE_ELEMENT)) {
					Element attributeEle = (Element) node;
					String attributeName = attributeEle.getAttribute(KEY_ATTRIBUTE);
					String attributeValue = attributeEle.getAttribute(VALUE_ATTRIBUTE);
					if (StringUtils.hasLength(attributeName) && StringUtils.hasLength(attributeValue)) {
						BeanMetadataAttribute attribute = new BeanMetadataAttribute(attributeName, attributeValue);
						attribute.setSource(extractSource(attributeEle));
						qualifier.addMetadataAttribute(attribute);
					}
					else {
						error("Qualifier 'attribute' tag must have a 'name' and 'value'", attributeEle);
						return;
					}
				}
			}
			bd.addQualifier(qualifier);
		}
		finally {
			this.parseState.pop();
		}
	}

	/**
	 * Get the value of a property element. May be a list etc.
	 * Also used for constructor arguments, "propertyName" being null in this case.
	 *
	 * 1.略过description或者meta.
	 * 2．提取constructor-arg上的ref和value属性，以便于根据规则验证正确性，其规则为在
	 * constructor-arg上不存在以下情况。
	 *   同时既有ref属性又有value属性。
	 *   存在ref属性或者value属性且又有子元素。
	 * 3．ref属性的处理。使用封装对应的ref名称，如：
	 * 4．value属性的处理。使用TypedStringVaIue封装，如：
	 * <constructor—arg>
	 * 5，子元素的处理，如：
	 * <constructor—arg>
	 *  <map>
	 *    <entry key="key" value="value"></entry>
	 *  </map>
	 * </constructor—arg>
	 */
	@Nullable
	public Object parsePropertyValue(Element ele, BeanDefinition bd, @Nullable String propertyName) {
		String elementName = (propertyName != null ?
				"<property> element for property '" + propertyName + "'" :
				"<constructor-arg> element");

		// Should only have one child element: ref, value, list, etc.
		// 一个属性只能对应一种类型：ref、value、list等
		NodeList nl = ele.getChildNodes();
		Element subElement = null;
		for (int i = 0; i < nl.getLength(); i++) {
			Node node = nl.item(i);
			//对应description 或者meta不处理
			if (node instanceof Element && !nodeNameEquals(node, DESCRIPTION_ELEMENT) &&
					!nodeNameEquals(node, META_ELEMENT)) {
				// Child element is what we're looking for.
				if (subElement != null) {
					error(elementName + " must not contain more than one sub-element", ele);
				}
				else {
					subElement = (Element) node;
				}
			}
		}
		//解析 constructor-arg 上的ref属性
		boolean hasRefAttribute = ele.hasAttribute(REF_ATTRIBUTE);
		//解析 constructor-arg 上的value属性
		boolean hasValueAttribute = ele.hasAttribute(VALUE_ATTRIBUTE);
		if ((hasRefAttribute && hasValueAttribute) ||
				((hasRefAttribute || hasValueAttribute) && subElement != null)) {
			/**
			 * 在 constructor-arg上不存在：
			 * 1、同时既有ref属性又有value属性
			 * 2、存在ref属性或者value属性且又有子元素
			 */
			error(elementName +
					" is only allowed to contain either 'ref' attribute OR 'value' attribute OR sub-element", ele);
		}

		if (hasRefAttribute) {
			//ref属性的处理，使用RuntimeBeanReference 封装对应的ref名称
			String refName = ele.getAttribute(REF_ATTRIBUTE);
			if (!StringUtils.hasText(refName)) {
				error(elementName + " contains empty 'ref' attribute", ele);
			}
			RuntimeBeanReference ref = new RuntimeBeanReference(refName);
			ref.setSource(extractSource(ele));
			return ref;
		}
		else if (hasValueAttribute) {
			//value 属性的处理，使用TypedStringValue封装
			TypedStringValue valueHolder = new TypedStringValue(ele.getAttribute(VALUE_ATTRIBUTE));
			valueHolder.setSource(extractSource(ele));
			return valueHolder;
		}
		else if (subElement != null) {
			//解析子元素
			//构造函数中又嵌入了子元素map的处理
			return parsePropertySubElement(subElement, bd);
		}
		else {
			// Neither child element nor "ref" or "value" attribute found.
			// 既没有ref也没有value也没有子元素，报错。
			error(elementName + " must specify a ref or value", ele);
			return null;
		}
	}

	/**
	 * Parse a value, ref or collection sub-element of a property or
	 * constructor-arg element.
	 * @param ele subelement of property element; we don't know which yet
	 * @param bd the current bean definition (if any)
	 */
	@Nullable
	public Object parsePropertySubElement(Element ele, @Nullable BeanDefinition bd) {
		return parsePropertySubElement(ele, bd, null);
	}

	/**
	 * Parse a value, ref or collection sub-element of a property or
	 * constructor-arg element.
	 * @param ele subelement of property element; we don't know which yet
	 * @param bd the current bean definition (if any)
	 * @param defaultValueType the default type (class name) for any
	 * {@code <value>} tag that might be created
	 */
	@Nullable
	public Object parsePropertySubElement(Element ele, @Nullable BeanDefinition bd, @Nullable String defaultValueType) {
		if (!isDefaultNamespace(ele)) {
			return parseNestedCustomElement(ele, bd);
		}
		else if (nodeNameEquals(ele, BEAN_ELEMENT)) {
			BeanDefinitionHolder nestedBd = parseBeanDefinitionElement(ele, bd);
			if (nestedBd != null) {
				nestedBd = decorateBeanDefinitionIfRequired(ele, nestedBd, bd);
			}
			return nestedBd;
		}
		else if (nodeNameEquals(ele, REF_ELEMENT)) {
			// A generic reference to any name of any bean.
			//解析local
			String refName = ele.getAttribute(BEAN_REF_ATTRIBUTE);
			boolean toParent = false;
			if (!StringUtils.hasLength(refName)) {
				// A reference to the id of another bean in a parent context.
				//解析parent
				refName = ele.getAttribute(PARENT_REF_ATTRIBUTE);
				toParent = true;
				if (!StringUtils.hasLength(refName)) {
					error("'bean' or 'parent' is required for <ref> element", ele);
					return null;
				}
			}
			if (!StringUtils.hasText(refName)) {
				error("<ref> element contains empty target attribute", ele);
				return null;
			}
			RuntimeBeanReference ref = new RuntimeBeanReference(refName, toParent);
			ref.setSource(extractSource(ele));
			return ref;
		}
		//解析 idref
		else if (nodeNameEquals(ele, IDREF_ELEMENT)) {
			return parseIdRefElement(ele);
		}
		//解析 value
		else if (nodeNameEquals(ele, VALUE_ELEMENT)) {
			return parseValueElement(ele, defaultValueType);
		}
		else if (nodeNameEquals(ele, NULL_ELEMENT)) {
			// It's a distinguished null value. Let's wrap it in a TypedStringValue
			// object in order to preserve the source location.
			TypedStringValue nullHolder = new TypedStringValue(null);
			nullHolder.setSource(extractSource(ele));
			return nullHolder;
		}
		else if (nodeNameEquals(ele, ARRAY_ELEMENT)) {
			return parseArrayElement(ele, bd);
		}
		else if (nodeNameEquals(ele, LIST_ELEMENT)) {
			return parseListElement(ele, bd);
		}
		else if (nodeNameEquals(ele, SET_ELEMENT)) {
			return parseSetElement(ele, bd);
		}
		else if (nodeNameEquals(ele, MAP_ELEMENT)) {
			return parseMapElement(ele, bd);
		}
		else if (nodeNameEquals(ele, PROPS_ELEMENT)) {
			return parsePropsElement(ele);
		}
		else {
			error("Unknown property sub-element: [" + ele.getNodeName() + "]", ele);
			return null;
		}
	}

	/**
	 * Return a typed String value Object for the given 'idref' element.
	 */
	@Nullable
	public Object parseIdRefElement(Element ele) {
		// A generic reference to any name of any bean.
		String refName = ele.getAttribute(BEAN_REF_ATTRIBUTE);
		if (!StringUtils.hasLength(refName)) {
			error("'bean' is required for <idref> element", ele);
			return null;
		}
		if (!StringUtils.hasText(refName)) {
			error("<idref> element contains empty target attribute", ele);
			return null;
		}
		RuntimeBeanNameReference ref = new RuntimeBeanNameReference(refName);
		ref.setSource(extractSource(ele));
		return ref;
	}

	/**
	 * Return a typed String value Object for the given value element.
	 */
	public Object parseValueElement(Element ele, @Nullable String defaultTypeName) {
		// It's a literal value.
		String value = DomUtils.getTextValue(ele);
		String specifiedTypeName = ele.getAttribute(TYPE_ATTRIBUTE);
		String typeName = specifiedTypeName;
		if (!StringUtils.hasText(typeName)) {
			typeName = defaultTypeName;
		}
		try {
			TypedStringValue typedValue = buildTypedStringValue(value, typeName);
			typedValue.setSource(extractSource(ele));
			typedValue.setSpecifiedTypeName(specifiedTypeName);
			return typedValue;
		}
		catch (ClassNotFoundException ex) {
			error("Type class [" + typeName + "] not found for <value> element", ele, ex);
			return value;
		}
	}

	/**
	 * Build a typed String value Object for the given raw value.
	 * @see org.springframework.beans.factory.config.TypedStringValue
	 */
	protected TypedStringValue buildTypedStringValue(String value, @Nullable String targetTypeName)
			throws ClassNotFoundException {

		ClassLoader classLoader = this.readerContext.getBeanClassLoader();
		TypedStringValue typedValue;
		if (!StringUtils.hasText(targetTypeName)) {
			typedValue = new TypedStringValue(value);
		}
		else if (classLoader != null) {
			Class<?> targetType = ClassUtils.forName(targetTypeName, classLoader);
			typedValue = new TypedStringValue(value, targetType);
		}
		else {
			typedValue = new TypedStringValue(value, targetTypeName);
		}
		return typedValue;
	}

	/**
	 * Parse an array element.
	 */
	public Object parseArrayElement(Element arrayEle, @Nullable BeanDefinition bd) {
		String elementType = arrayEle.getAttribute(VALUE_TYPE_ATTRIBUTE);
		NodeList nl = arrayEle.getChildNodes();
		ManagedArray target = new ManagedArray(elementType, nl.getLength());
		target.setSource(extractSource(arrayEle));
		target.setElementTypeName(elementType);
		target.setMergeEnabled(parseMergeAttribute(arrayEle));
		parseCollectionElements(nl, target, bd, elementType);
		return target;
	}

	/**
	 * Parse a list element.
	 */
	public List<Object> parseListElement(Element collectionEle, @Nullable BeanDefinition bd) {
		String defaultElementType = collectionEle.getAttribute(VALUE_TYPE_ATTRIBUTE);
		NodeList nl = collectionEle.getChildNodes();
		ManagedList<Object> target = new ManagedList<>(nl.getLength());
		target.setSource(extractSource(collectionEle));
		target.setElementTypeName(defaultElementType);
		target.setMergeEnabled(parseMergeAttribute(collectionEle));
		parseCollectionElements(nl, target, bd, defaultElementType);
		return target;
	}

	/**
	 * Parse a set element.
	 */
	public Set<Object> parseSetElement(Element collectionEle, @Nullable BeanDefinition bd) {
		String defaultElementType = collectionEle.getAttribute(VALUE_TYPE_ATTRIBUTE);
		NodeList nl = collectionEle.getChildNodes();
		ManagedSet<Object> target = new ManagedSet<>(nl.getLength());
		target.setSource(extractSource(collectionEle));
		target.setElementTypeName(defaultElementType);
		target.setMergeEnabled(parseMergeAttribute(collectionEle));
		parseCollectionElements(nl, target, bd, defaultElementType);
		return target;
	}

	protected void parseCollectionElements(
			NodeList elementNodes, Collection<Object> target, @Nullable BeanDefinition bd, String defaultElementType) {

		for (int i = 0; i < elementNodes.getLength(); i++) {
			Node node = elementNodes.item(i);
			if (node instanceof Element && !nodeNameEquals(node, DESCRIPTION_ELEMENT)) {
				target.add(parsePropertySubElement((Element) node, bd, defaultElementType));
			}
		}
	}

	/**
	 * Parse a map element.
	 */
	public Map<Object, Object> parseMapElement(Element mapEle, @Nullable BeanDefinition bd) {
		String defaultKeyType = mapEle.getAttribute(KEY_TYPE_ATTRIBUTE);
		String defaultValueType = mapEle.getAttribute(VALUE_TYPE_ATTRIBUTE);

		List<Element> entryEles = DomUtils.getChildElementsByTagName(mapEle, ENTRY_ELEMENT);
		ManagedMap<Object, Object> map = new ManagedMap<>(entryEles.size());
		map.setSource(extractSource(mapEle));
		map.setKeyTypeName(defaultKeyType);
		map.setValueTypeName(defaultValueType);
		map.setMergeEnabled(parseMergeAttribute(mapEle));

		for (Element entryEle : entryEles) {
			// Should only have one value child element: ref, value, list, etc.
			// Optionally, there might be a key child element.
			NodeList entrySubNodes = entryEle.getChildNodes();
			Element keyEle = null;
			Element valueEle = null;
			for (int j = 0; j < entrySubNodes.getLength(); j++) {
				Node node = entrySubNodes.item(j);
				if (node instanceof Element) {
					Element candidateEle = (Element) node;
					if (nodeNameEquals(candidateEle, KEY_ELEMENT)) {
						if (keyEle != null) {
							error("<entry> element is only allowed to contain one <key> sub-element", entryEle);
						}
						else {
							keyEle = candidateEle;
						}
					}
					else {
						// Child element is what we're looking for.
						if (nodeNameEquals(candidateEle, DESCRIPTION_ELEMENT)) {
							// the element is a <description> -> ignore it
						}
						else if (valueEle != null) {
							error("<entry> element must not contain more than one value sub-element", entryEle);
						}
						else {
							valueEle = candidateEle;
						}
					}
				}
			}

			// Extract key from attribute or sub-element.
			Object key = null;
			boolean hasKeyAttribute = entryEle.hasAttribute(KEY_ATTRIBUTE);
			boolean hasKeyRefAttribute = entryEle.hasAttribute(KEY_REF_ATTRIBUTE);
			if ((hasKeyAttribute && hasKeyRefAttribute) ||
					(hasKeyAttribute || hasKeyRefAttribute) && keyEle != null) {
				error("<entry> element is only allowed to contain either " +
						"a 'key' attribute OR a 'key-ref' attribute OR a <key> sub-element", entryEle);
			}
			if (hasKeyAttribute) {
				key = buildTypedStringValueForMap(entryEle.getAttribute(KEY_ATTRIBUTE), defaultKeyType, entryEle);
			}
			else if (hasKeyRefAttribute) {
				String refName = entryEle.getAttribute(KEY_REF_ATTRIBUTE);
				if (!StringUtils.hasText(refName)) {
					error("<entry> element contains empty 'key-ref' attribute", entryEle);
				}
				RuntimeBeanReference ref = new RuntimeBeanReference(refName);
				ref.setSource(extractSource(entryEle));
				key = ref;
			}
			else if (keyEle != null) {
				key = parseKeyElement(keyEle, bd, defaultKeyType);
			}
			else {
				error("<entry> element must specify a key", entryEle);
			}

			// Extract value from attribute or sub-element.
			Object value = null;
			boolean hasValueAttribute = entryEle.hasAttribute(VALUE_ATTRIBUTE);
			boolean hasValueRefAttribute = entryEle.hasAttribute(VALUE_REF_ATTRIBUTE);
			boolean hasValueTypeAttribute = entryEle.hasAttribute(VALUE_TYPE_ATTRIBUTE);
			if ((hasValueAttribute && hasValueRefAttribute) ||
					(hasValueAttribute || hasValueRefAttribute) && valueEle != null) {
				error("<entry> element is only allowed to contain either " +
						"'value' attribute OR 'value-ref' attribute OR <value> sub-element", entryEle);
			}
			if ((hasValueTypeAttribute && hasValueRefAttribute) ||
				(hasValueTypeAttribute && !hasValueAttribute) ||
					(hasValueTypeAttribute && valueEle != null)) {
				error("<entry> element is only allowed to contain a 'value-type' " +
						"attribute when it has a 'value' attribute", entryEle);
			}
			if (hasValueAttribute) {
				String valueType = entryEle.getAttribute(VALUE_TYPE_ATTRIBUTE);
				if (!StringUtils.hasText(valueType)) {
					valueType = defaultValueType;
				}
				value = buildTypedStringValueForMap(entryEle.getAttribute(VALUE_ATTRIBUTE), valueType, entryEle);
			}
			else if (hasValueRefAttribute) {
				String refName = entryEle.getAttribute(VALUE_REF_ATTRIBUTE);
				if (!StringUtils.hasText(refName)) {
					error("<entry> element contains empty 'value-ref' attribute", entryEle);
				}
				RuntimeBeanReference ref = new RuntimeBeanReference(refName);
				ref.setSource(extractSource(entryEle));
				value = ref;
			}
			else if (valueEle != null) {
				value = parsePropertySubElement(valueEle, bd, defaultValueType);
			}
			else {
				error("<entry> element must specify a value", entryEle);
			}

			// Add final key and value to the Map.
			map.put(key, value);
		}

		return map;
	}

	/**
	 * Build a typed String value Object for the given raw value.
	 * @see org.springframework.beans.factory.config.TypedStringValue
	 */
	protected final Object buildTypedStringValueForMap(String value, String defaultTypeName, Element entryEle) {
		try {
			TypedStringValue typedValue = buildTypedStringValue(value, defaultTypeName);
			typedValue.setSource(extractSource(entryEle));
			return typedValue;
		}
		catch (ClassNotFoundException ex) {
			error("Type class [" + defaultTypeName + "] not found for Map key/value type", entryEle, ex);
			return value;
		}
	}

	/**
	 * Parse a key sub-element of a map element.
	 */
	@Nullable
	protected Object parseKeyElement(Element keyEle, @Nullable BeanDefinition bd, String defaultKeyTypeName) {
		NodeList nl = keyEle.getChildNodes();
		Element subElement = null;
		for (int i = 0; i < nl.getLength(); i++) {
			Node node = nl.item(i);
			if (node instanceof Element) {
				// Child element is what we're looking for.
				if (subElement != null) {
					error("<key> element must not contain more than one value sub-element", keyEle);
				}
				else {
					subElement = (Element) node;
				}
			}
		}
		if (subElement == null) {
			return null;
		}
		return parsePropertySubElement(subElement, bd, defaultKeyTypeName);
	}

	/**
	 * Parse a props element.
	 */
	public Properties parsePropsElement(Element propsEle) {
		ManagedProperties props = new ManagedProperties();
		props.setSource(extractSource(propsEle));
		props.setMergeEnabled(parseMergeAttribute(propsEle));

		List<Element> propEles = DomUtils.getChildElementsByTagName(propsEle, PROP_ELEMENT);
		for (Element propEle : propEles) {
			String key = propEle.getAttribute(KEY_ATTRIBUTE);
			// Trim the text value to avoid unwanted whitespace
			// caused by typical XML formatting.
			String value = DomUtils.getTextValue(propEle).trim();
			TypedStringValue keyHolder = new TypedStringValue(key);
			keyHolder.setSource(extractSource(propEle));
			TypedStringValue valueHolder = new TypedStringValue(value);
			valueHolder.setSource(extractSource(propEle));
			props.put(keyHolder, valueHolder);
		}

		return props;
	}

	/**
	 * Parse the merge attribute of a collection element, if any.
	 */
	public boolean parseMergeAttribute(Element collectionElement) {
		String value = collectionElement.getAttribute(MERGE_ATTRIBUTE);
		if (isDefaultValue(value)) {
			value = this.defaults.getMerge();
		}
		return TRUE_VALUE.equals(value);
	}

	/**
	 * Parse a custom element (outside of the default namespace).
	 * @param ele the element to parse
	 * @return the resulting bean definition
	 *
	 * 在很多情况下，我们需要为系统提供可配置化支持，简单的做法可以直接基于Spring的标
	 * 准bean来配置，但配置较为复杂或者需要更多丰富控制的时候，会显得非常笨拙。一般的做
	 * 法会用原生态的方式去解析定义好的XML文件，然后转化为配置对象。这种方式当然可以解
	 * 决所有问题，但实现起来比较烦琐，特别是在配置非常复杂的时候，解析工作是一个不得不考
	 * 虑的负担。spring提供了可扩展Schema的支持，这是一个不错的折中方案，扩展Spring自定
	 * 义标签配置大致需要以下几个步骤（前提是要把Spring的Core包加人项目中）。
	 * 1、创建一个需要扩展的组件。
	 * 2、定义一个XSD文件描述组件内容。
	 * 3、创建一个文件，实现BeanDefinitionParser接口，用来解析XSD文件中的定义和组件定义。
	 * 4、创建一个Handler文件，扩展自NamespaceHandlerSupport，目的是将组件注册到Spring容器。
	 * 5、编写Spring.handlers和Spring.schemas文件。
	 *
	 * 1、首先创建一个POJO
	 * package test.customtag;
	 * public class User{
	 *   private String userName;
	 *   private String email;
	 *   //省略set/get方法
	 * }
	 * 2、定义一个XSD文件描述组件内容。
	 * <?xml version="1.0" encoding="UTF-8"?>
	 *     <schema xmlns="http://www.w3.org/2001/XMLSchema“
	 *     targetNamespace="http://www.lexueba.com/schema/user"
	 *     xmlns:tns="http://www.lexueba.com/schemayuser"
	 *     elementFormDefault="qualified">
	 *    <element name="user">
	 *        <complexType>
	 *            <attribute name="id" type="string"/>
	 *            <attribute name="userName" type="string"/>
	 *            <attribute name="email" type="string"/>
	 *        </complexType>
	 *   </element>
	 * </schema>
	 * 在上面的XSD文件中描述了一个新的 targetNamespace，并在这个空间中定义了一个 name为user 的 element，
	 * user 有 3 个属性 id、userName 和 email，其中 email 的类型为 string。
	 * 这3 个类主要用于验证 Spring 配置文件中自定义格式。XSD文件是XML DTD 的替代者，使用XML Schema
	 * 语言进行编写，这里对 XSD Schema 不做太多解释，有兴趣的读者可以参考相关的资料
	 *
	 * 3.创建一个文件，实现 BeanDefinitionParser 接口，用来解析XSD文件中的定义和组件定义。
	 * package test.customtag;
	 * Import org.Springframework.beans.factory.support.BeanDefinitionBuilder;
	 * import org.Springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
	 * import org.Springframework.util.StringUtils;import org.w3c.dom.Element;
	 * public class UserBeanDefinitionParser extends AbstractSingleBeanDefinitionParser {
	 *     //Element 对应的类
	 *     protected Class getBeanClass(Element element) {return User.class;}
	 *     //从element 中解析并提取对应的元素
	 *     protected void doParse(Element element,BeanDefinitionBuilder bean) {
	 *        String userName = element.qetAttribute("userName");
	 *        String email=element.getAttribute("email");
	 *        //将提取的数据放入到 BeanDefinitionBuilder 中，待到完成所有 bean 的解析后统一注册到beanFactory中
	 *        if (StringUtils.hasText(userName)) {
	 *           bean.addPropertyValue("userName"， userName);
	 *        }
	 *        if(StringUtils.hasText(email)){
	 *           bean.addPropertyValue("email"，email);
	 *        }
	 *    }
	 * }
	 * 4.创建一个 Handler 文件，扩展自 NamespaceHandlerSupport，目的是将组件注册到Spring容器。
	 * package test.customtag;
	 * import org.Springframework.beans.factory.xml.NamespaceHandlerSupport;
	 * public class MyNamespaceHandler extends NamespaceHandlerSupport{
	 *     public void init() {
	 *          registerBeanDefinitionParser("user"，new UserBeanDefinitionParser());
	 *     }
	 * }
	 * 以上代码很简单，无非是当遇到自定义标签<user:aaa 这样类似于以 user 开头的元素，就会把这个元素扔给对应的UserBeanDefinitionParser 去解析。
	 * 5.编写 Spring.handlers 和 Spring.schemas 文件，默认位置是在工程的/META-INF/文件夹下，当然，你可以通过 Spring 的扩展或者修改源码的方式改变路径。
	 * Spring.handlers。
	 * http\\:www.lexueba.com/schema/user=test.customtag.MyNamespaceHandler
	 * Spring.schemas。
	 * httpl://www.lexueba.com/schema/user.xsd=META-INF/Spring-test.xsd
	 * 到这里，自定义的配置就结束了，而 Spring 加载自定义的大致流程是遇到自定义标签然后就去
	 * Spring.handlers 和 Spring.schemas 中去找对应的 handler 和XSD（默认位置是/META-INF)下，
	 * 进而有找到对应的 handler 以及解析元素的 Parser，从而完成了整个自定义元素的解析，也就是说自定义与
	 * Spring中默认的标准配置不同在于Spring 将自定义标签解析的工作委托给了用户去实现。
	 * 6.创建测试配置文件，在配置文件中引入对应的命名空间以及 XSD 后，便可以直接使用自定义标签了。
	 * <beans xmlns="http://www.Springframework.org/schema/beans"
	 *        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	 *        xmlns:myname="http://www.lexueba.com/schema/user"
	 *        xsi:schemaLocation="http://www.Springframework.org/schema/beans
	 *                            http://www.Springframework.org/schema/beans/Spring-beans-2.0.xsd
	 *                            http://www,lexueba,com/schema/user http://www.lexuebacom/schema/user.xsd">
	 *      <myname:user id="testbean” userName="aaa" email="bbb"/>
	 * </beans>
	 */
	@Nullable
	public BeanDefinition parseCustomElement(Element ele) {
		return parseCustomElement(ele, null);
	}

	/**
	 * Parse a custom element (outside of the default namespace).
	 * @param ele the element to parse
	 * @param containingBd the containing bean definition (if any)
	 * @return the resulting bean definition
	 */
	@Nullable
	//containingBd 为父类 bean, 对顶层元素的解析应设置为null
	public BeanDefinition parseCustomElement(Element ele, @Nullable BeanDefinition containingBd) {
		//获取对应的命名空间
		String namespaceUri = getNamespaceURI(ele);
		if (namespaceUri == null) {
			return null;
		}
		//根据命名空间找到对应的 NamespaceHandler
		NamespaceHandler handler = this.readerContext.getNamespaceHandlerResolver().resolve(namespaceUri);
		if (handler == null) {
			error("Unable to locate Spring NamespaceHandler for XML schema namespace [" + namespaceUri + "]", ele);
			return null;
		}
		//调用自定义的 NamespaceHandler 进行解析
		return handler.parse(ele, new ParserContext(this.readerContext, this, containingBd));
	}

	/**
	 * Decorate the given bean definition through a namespace handler, if applicable.
	 * @param ele the current element
	 * @param originalDef the current bean definition
	 * @return the decorated bean definition
	 *
	 * 这里将函数中第三个参数设置为空，那么第三个参数是做什么用的呢？什么情况下不为空
	 * 呢？其实这第三个参数是父类bean，当对某个嵌套配置进行分析时，这里需要传递父类
	 * beanDefinition。分析源码得知这里传递的参数其实是为了使用父类的scope属性，以备子类若
	 * 没有设置scope时默认使用父类的属性，这里分析的是顶层配置，所以传递null。将第三个参
	 * 数设置为空后进一步跟踪函数：
	 */
	public BeanDefinitionHolder decorateBeanDefinitionIfRequired(Element ele, BeanDefinitionHolder originalDef) {
		return decorateBeanDefinitionIfRequired(ele, originalDef, null);
	}

	/**
	 * Decorate the given bean definition through a namespace handler, if applicable.
	 * @param ele the current element
	 * @param originalDef the current bean definition
	 * @param containingBd the containing bean definition (if any)
	 * @return the decorated bean definition
	 *
	 * 我们总结下decorateBeanDefinitionIfRequired方法的作用，在decorateBeanDefinitionIfRequired
	 * 中我们可以看到对于程序默认的标签的处理其实是直接略过的，因为默认的标签到这里已经被
	 * 处理完了，这里只对自定义的标签或者说对bean的自定义属性感兴趣。在方法中实现了寻找
	 * 自定义标签并根据自定义标签寻找命名空间处理器，并进行进一步的解析。
	 */
	public BeanDefinitionHolder decorateBeanDefinitionIfRequired(
			Element ele, BeanDefinitionHolder originalDef, @Nullable BeanDefinition containingBd) {

		BeanDefinitionHolder finalDefinition = originalDef;

		// Decorate based on custom attributes first.
		NamedNodeMap attributes = ele.getAttributes();
		//遍历所有的属性，看看是否有适用于修饰的属性
		for (int i = 0; i < attributes.getLength(); i++) {
			Node node = attributes.item(i);
			finalDefinition = decorateIfRequired(node, finalDefinition, containingBd);
		}

		// Decorate based on custom nested elements.
		NodeList children = ele.getChildNodes();
		//遍历所有的子节点，看看是否有适用于修饰的子元素
		for (int i = 0; i < children.getLength(); i++) {
			Node node = children.item(i);
			if (node.getNodeType() == Node.ELEMENT_NODE) {
				finalDefinition = decorateIfRequired(node, finalDefinition, containingBd);
			}
		}
		return finalDefinition;
	}

	/**
	 * Decorate the given bean definition through a namespace handler,
	 * if applicable.
	 * @param node the current child node
	 * @param originalDef the current bean definition
	 * @param containingBd the containing bean definition (if any)
	 * @return the decorated bean definition
	 *
	 * 程序走到这里，条理其实已经非常清楚了，首先获取属性或者元素的命名空间，以此
	 * 来判断该元素或者属性是否适用于自定义标签的解析条件，找出自定义类型所对应的
	 * NamespaceHandler并进行进一步解析。在自定义标签解析的章节我们会重点讲解，这里暂时先略过。
	 */
	public BeanDefinitionHolder decorateIfRequired(
			Node node, BeanDefinitionHolder originalDef, @Nullable BeanDefinition containingBd) {

		//获取自定义标签的命名空间
		String namespaceUri = getNamespaceURI(node);
		//对于非默认标签进行修饰
		if (namespaceUri != null && !isDefaultNamespace(namespaceUri)) {
			//根据命名空间找到对应的处理器
			NamespaceHandler handler = this.readerContext.getNamespaceHandlerResolver().resolve(namespaceUri);
			if (handler != null) {
				//进行修饰
				BeanDefinitionHolder decorated =
						handler.decorate(node, originalDef, new ParserContext(this.readerContext, this, containingBd));
				if (decorated != null) {
					return decorated;
				}
			}
			else if (namespaceUri.startsWith("http://www.springframework.org/schema/")) {
				error("Unable to locate Spring NamespaceHandler for XML schema namespace [" + namespaceUri + "]", node);
			}
			else {
				// A custom namespace, not to be handled by Spring - maybe "xml:...".
				if (logger.isDebugEnabled()) {
					logger.debug("No Spring NamespaceHandler found for XML schema namespace [" + namespaceUri + "]");
				}
			}
		}
		return originalDef;
	}

	@Nullable
	private BeanDefinitionHolder parseNestedCustomElement(Element ele, @Nullable BeanDefinition containingBd) {
		BeanDefinition innerDefinition = parseCustomElement(ele, containingBd);
		if (innerDefinition == null) {
			error("Incorrect usage of element '" + ele.getNodeName() + "' in a nested manner. " +
					"This tag cannot be used nested inside <property>.", ele);
			return null;
		}
		String id = ele.getNodeName() + BeanDefinitionReaderUtils.GENERATED_BEAN_NAME_SEPARATOR +
				ObjectUtils.getIdentityHexString(innerDefinition);
		if (logger.isTraceEnabled()) {
			logger.trace("Using generated bean name [" + id +
					"] for nested custom element '" + ele.getNodeName() + "'");
		}
		return new BeanDefinitionHolder(innerDefinition, id);
	}


	/**
	 * Get the namespace URI for the supplied node.
	 * <p>The default implementation uses {@link Node#getNamespaceURI}.
	 * Subclasses may override the default implementation to provide a
	 * different namespace identification mechanism.
	 *
	 * 标签的解析是从命名空间的提起开始的，无论是区分 Spring 中默认标签和自定义标签
	 * 还是区分自定义标签中不同标签的处理器都是以标签所提供的命名空间为基础的，
	 * 而至于如何提取对应元素的命名空间其实并不需要我们亲自去实现，在 org.w3c.dom.Node 中已经提供了方法供我们直接调用:
	 *
	 * @param node the node
	 */
	@Nullable
	public String getNamespaceURI(Node node) {
		return node.getNamespaceURI();
	}

	/**
	 * Get the local name for the supplied {@link Node}.
	 * <p>The default implementation calls {@link Node#getLocalName}.
	 * Subclasses may override the default implementation to provide a
	 * different mechanism for getting the local name.
	 * @param node the {@code Node}
	 */
	public String getLocalName(Node node) {
		return node.getLocalName();
	}

	/**
	 * Determine whether the name of the supplied node is equal to the supplied name.
	 * <p>The default implementation checks the supplied desired name against both
	 * {@link Node#getNodeName()} and {@link Node#getLocalName()}.
	 * <p>Subclasses may override the default implementation to provide a different
	 * mechanism for comparing node names.
	 * @param node the node to compare
	 * @param desiredName the name to check for
	 */
	public boolean nodeNameEquals(Node node, String desiredName) {
		return desiredName.equals(node.getNodeName()) || desiredName.equals(getLocalName(node));
	}

	/**
	 * Determine whether the given URI indicates the default namespace.
	 */
	public boolean isDefaultNamespace(@Nullable String namespaceUri) {
		return (!StringUtils.hasLength(namespaceUri) || BEANS_NAMESPACE_URI.equals(namespaceUri));
	}

	/**
	 * Determine whether the given node indicates the default namespace.
	 */
	public boolean isDefaultNamespace(Node node) {
		return isDefaultNamespace(getNamespaceURI(node));
	}

	private boolean isDefaultValue(String value) {
		return (DEFAULT_VALUE.equals(value) || "".equals(value));
	}

	private boolean isCandidateElement(Node node) {
		return (node instanceof Element && (isDefaultNamespace(node) || !isDefaultNamespace(node.getParentNode())));
	}

}
