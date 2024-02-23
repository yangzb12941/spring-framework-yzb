package org.springframework.test.yzb.DynamicProxy.SpringJDKProxy.SchemaBasedAop;

import org.springframework.aop.AfterReturningAdvice;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * 自定义的后置通知类
 */
public class MyAfterAdvisor implements AfterReturningAdvice {
    /**
     * 后置通知被调用的时候执行的方法
     * @param arg0  切点方法的返回值
     * @param arg1  切点方法的签名
     * @param arg2  传递给切点方法的参数
     * @param arg3  切点方法所在类的对象
     * @throws Throwable
     */
    @Override
    public void afterReturning(Object arg0, Method arg1, Object[] arg2, Object arg3) throws Throwable {
        System.out.println("arg0 " + arg0);
        System.out.println("arg1 " + arg1);
        System.out.println("arg2 " + Arrays.toString(arg2));
        System.out.println("arg3 " + arg3);
    }
}
