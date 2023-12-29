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

package org.springframework.test.context.yangzbxmlaop;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericXmlApplicationContext;

import static org.springframework.tests.TestResourceUtils.qualifiedResource;

/**
 * @author Stephane Nicoll
 */
public class YangzbXmlAopFailTests {

	@Test
	public void testProxy_1() throws Exception {
		ApplicationContext context = new GenericXmlApplicationContext(
				qualifiedResource(YangzbXmlAopFailTests.class, "context.xml"));
		//切面类不能自己增强自己
		//Loger bean = (Loger)context.getBean("logger");
		//bean.check();

		IBookService bean = (IBookService)context.getBean("bookService");
		bean.find();
	}
}
