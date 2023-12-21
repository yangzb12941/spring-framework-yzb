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

package org.springframework.aop.yangzbxmlaop;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;

import static org.springframework.tests.TestResourceUtils.qualifiedResource;

/**
 * @author Stephane Nicoll
 */
public class YangzbXmlAopTests {

	private DefaultListableBeanFactory beanFactory;

	@BeforeEach
	public void setup() {
		this.beanFactory = new DefaultListableBeanFactory();
		new XmlBeanDefinitionReader(this.beanFactory).loadBeanDefinitions(
				qualifiedResource(YangzbXmlAopTests.class, "context.xml"));
	}

	@Test
	public void testProxy() throws Exception {
		beanFactory.preInstantiateSingletons();
		IAccountService bean = (IAccountService)beanFactory.getBean("accountService");

		//3.执行方法
		bean.saveAccount();
		bean.updateAccount(1);
		bean.deleteAccount();
	}
}
