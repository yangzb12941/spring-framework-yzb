package org.springframework.test.yzbtest.InitializingDisposableBeanTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.test.yzb.DynamicProxy.SpringJDKProxy.AspectJAop.AspectJAopTarget;
import org.springframework.test.yzb.DynamicProxy.SpringJDKProxy.utils.TestResourceUtils;
import org.springframework.test.yzb.InitializingDisposableBean.AInitializingBean;

import java.io.IOException;

public class AInitializingBeanTest {

	//InitializingBean 和 DisposableBean 接口调用时机验证
	@Test
	public void aInitializingBeanTest() throws IOException {
		ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext("classpath:org/springframework/test/proxy/AInitializingBeanTest.xml");
		AInitializingBean aInitializingBean = ac.getBean("aInitializingBean", AInitializingBean.class);
		ac.close();
	}
}
