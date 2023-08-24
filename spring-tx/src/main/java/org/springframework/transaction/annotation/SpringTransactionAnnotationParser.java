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

package org.springframework.transaction.annotation;

import java.io.Serializable;
import java.lang.reflect.AnnotatedElement;
import java.util.ArrayList;
import java.util.List;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.Nullable;
import org.springframework.transaction.interceptor.NoRollbackRuleAttribute;
import org.springframework.transaction.interceptor.RollbackRuleAttribute;
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute;
import org.springframework.transaction.interceptor.TransactionAttribute;

/**
 * Strategy implementation for parsing Spring's {@link Transactional} annotation.
 *
 * @author Juergen Hoeller
 * @since 2.5
 */
@SuppressWarnings("serial")
public class SpringTransactionAnnotationParser implements TransactionAnnotationParser, Serializable {

	@Override
	public boolean isCandidateClass(Class<?> targetClass) {
		return AnnotationUtils.isCandidateClass(targetClass, Transactional.class);
	}

	@Override
	@Nullable
	public TransactionAttribute parseTransactionAnnotation(AnnotatedElement element) {
		AnnotationAttributes attributes = AnnotatedElementUtils.findMergedAnnotationAttributes(
				element, Transactional.class, false, false);
		if (attributes != null) {
			// 至此，我们终于看到了想看到的获取注解标记的代码。 首先会判断当前的类是否含有
			// Transactional 注解，这是事务属性的基础，当然如果有的话会继续调用 parseTransactionAnnotation 方法解析详细的属性。
			return parseTransactionAnnotation(attributes);
		}
		else {
			return null;
		}
	}

	public TransactionAttribute parseTransactionAnnotation(Transactional ann) {
		return parseTransactionAnnotation(AnnotationUtils.getAnnotationAttributes(ann, false, false));
	}

	/**
	 * 我们的现在的任务是找出某个增强器是否适合于对应的类，而是否匹配的关键则在于是否从指定的类或类中的方法中找到对应的事务属性，
	 * 现在，我们以 UserServiceImpl 为例，已经在它的接口 UserService 中找到了事务属性，所以，它是与事务增强器匹配的，
	 * 也就是它会被事务功能修饰。 至此，事务功能的初始化工作便结束了，当判断某个 bean 适用于事务增强时，也就是适
	 * 用于增强器 BeanFactoryTransactionAttributeSourceAdvisor ， 没错，还是这个类，
	 * 所以说，在自定义标签解析时，注入的类成为了整个事务功能的基础。
	 *
	 * BeanFactoryTransactionAttributeSourceAdvisor 作为 Advisor 的实现类，自然要遵从 Advisor 的处理方式，
	 * 当代理被调用时会调用这个类的增强方法，也就是此 bean 的 Advise ， 又因为在解析事务定义标签时我们把
	 * TransactionInterceptor 类型的 bean 注入到了 BeanFactoryTransactionAttributeSourceAdvisor 中，
	 * 所以，在调用事务增强器增强的代理类时会首先执行 TransactionInterceptor 进行增强，
	 * 同时，也就是在 TransactionInterceptor 类中的 invoke 方法中完 成了整个事务的逻辑。
	 *
	 * @param attributes
	 * @return
	 */
	protected TransactionAttribute parseTransactionAnnotation(AnnotationAttributes attributes) {
		RuleBasedTransactionAttribute rbta = new RuleBasedTransactionAttribute();
		//解析 propagation
		Propagation propagation = attributes.getEnum("propagation");
		rbta.setPropagationBehavior(propagation.value());
		// 解析 isolation
		Isolation isolation = attributes.getEnum("isolation");
		rbta.setIsolationLevel(isolation.value());
		// 解析 timeout
		rbta.setTimeout(attributes.getNumber("timeout").intValue());
		// 解析 readOnly
		rbta.setReadOnly(attributes.getBoolean("readOnly"));
		// 解析 value
		rbta.setQualifier(attributes.getString("value"));

		List<RollbackRuleAttribute> rollbackRules = new ArrayList<>();
		// 解析 rollbackFor 列表
		for (Class<?> rbRule : attributes.getClassArray("rollbackFor")) {
			rollbackRules.add(new RollbackRuleAttribute(rbRule));
		}
		// 解析 rollbackForClassName 列表
		for (String rbRule : attributes.getStringArray("rollbackForClassName")) {
			rollbackRules.add(new RollbackRuleAttribute(rbRule));
		}
		// 解析 noRollbackFor 列表
		for (Class<?> rbRule : attributes.getClassArray("noRollbackFor")) {
			rollbackRules.add(new NoRollbackRuleAttribute(rbRule));
		}
		// 解析 noRollbackForClassName 列表
		for (String rbRule : attributes.getStringArray("noRollbackForClassName")) {
			rollbackRules.add(new NoRollbackRuleAttribute(rbRule));
		}
		rbta.setRollbackRules(rollbackRules);

		return rbta;
	}


	@Override
	public boolean equals(@Nullable Object other) {
		return (this == other || other instanceof SpringTransactionAnnotationParser);
	}

	@Override
	public int hashCode() {
		return SpringTransactionAnnotationParser.class.hashCode();
	}

}
