package org.springframework.test.yzbtest.proxy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.test.yzb.InitializingDisposableBean.AInitializingBean;
import org.springframework.test.yzb.DynamicProxy.SpringJDKProxy.AspectJAop.AspectJAopTarget;
import org.springframework.test.yzb.DynamicProxy.SpringJDKProxy.utils.TestResourceUtils;

import java.io.IOException;

public class AspectJAopTest {
    //没有切面效果
	//
    @Test
    public void aspectJAopTest_1() throws IOException {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        new XmlBeanDefinitionReader(beanFactory).loadBeanDefinitions(
                TestResourceUtils.qualifiedResource(AspectJAopTest.class, "1.xml"));

        beanFactory.preInstantiateSingletons();
        AspectJAopTarget aspectJAopTarget = (AspectJAopTarget)beanFactory.getBean("aspectJAopTarget");

        aspectJAopTarget.aopMethodBefore();
        aspectJAopTarget.aopMethodAfter();
    }

    //有切面效果
    @Test
    public void aspectJAopTest_2() throws IOException {
        ApplicationContext ac = new ClassPathXmlApplicationContext("classpath:org/springframework/test/proxy/AspectJAopTest-1.xml");
        AspectJAopTarget aspectJAopTarget = ac.getBean("aspectJAopTarget", AspectJAopTarget.class);
        aspectJAopTarget.aopMethodBefore();
        aspectJAopTarget.aopMethodAfter();
    }

	//InitializingBean 和 DisposableBean 接口调用时机验证
	@Test
	public void aInitializingBeanTest() throws IOException {
		ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext("classpath:org/springframework/test/proxy/AInitializingBeanTest.xml");
		AInitializingBean aInitializingBean = ac.getBean("aInitializingBean", AInitializingBean.class);
		ac.close();
	}
}
