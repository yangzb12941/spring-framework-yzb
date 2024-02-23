package org.springframework.test.yzb.DynamicProxy.SpringJDKProxy.SchemaBasedAop;

import org.springframework.aop.MethodBeforeAdvice;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * 创建自定义前置通知类
 */
public class MyBeforeAdvisor implements MethodBeforeAdvice {
    /**
     * 重写MethodBeforeAdvice中before，就是前置通知执行的代码
     * @param arg0  代表被调用的切点方法签名
     * @param arg1  代表传入切点方法的参数列表
     * @param arg2  代表调用的是哪个对象大的切点方法
     * @throws Throwable
     */
    @Override
    public void before(Method arg0, Object[] arg1, Object arg2) throws Throwable {
        System.out.println("arg0" + arg0);
        System.out.println("arg1" + Arrays.toString(arg1));
        System.out.println("arg3" + arg2);
    }
}
