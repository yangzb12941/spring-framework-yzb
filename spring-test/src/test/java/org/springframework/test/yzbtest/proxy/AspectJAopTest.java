package org.springframework.test.yzbtest.proxy;

import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.AopContext;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.test.yzb.DynamicProxy.SpringJDKProxy.AspectJAop.AspectJAopTarget;
import org.springframework.test.yzb.DynamicProxy.SpringJDKProxy.utils.TestResourceUtils;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import java.io.IOException;

@EnableAspectJAutoProxy(exposeProxy = true)
public class AspectJAopTest {
    //没有切面效果
	//
    @Test
    public void aspectJAopTest_1(){
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
        ApplicationContext ac = new ClassPathXmlApplicationContext("classpath:org/springframework/test/yzbtest/proxy/AspectJAopTest-1.xml");
        AspectJAopTarget aspectJAopTargetA = ac.getBean("aspectJAopTarget", AspectJAopTarget.class);

		aspectJAopTargetA.aopMethodBefore();
        aspectJAopTargetA.aopMethodAfter();
    }
}
