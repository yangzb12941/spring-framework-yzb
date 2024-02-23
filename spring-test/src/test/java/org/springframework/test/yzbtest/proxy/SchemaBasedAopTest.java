package org.springframework.test.yzbtest.proxy;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.test.yzb.DynamicProxy.SpringJDKProxy.SchemaBasedAop.SchemaBasedAopTarget;
import java.io.IOException;

public class SchemaBasedAopTest {

    @Test
    public void schemaBasedAopTest() throws IOException {
        ApplicationContext ac = new ClassPathXmlApplicationContext("classpath:org/springframework/test/proxy/SchemaBasedAopTest.xml");
        SchemaBasedAopTarget schemaBasedAopTarget = ac.getBean("schemaBasedAopTarget", SchemaBasedAopTarget.class);
        schemaBasedAopTarget.aopMethodBefore();
        schemaBasedAopTarget.aopMethodAfter();
    }
}
