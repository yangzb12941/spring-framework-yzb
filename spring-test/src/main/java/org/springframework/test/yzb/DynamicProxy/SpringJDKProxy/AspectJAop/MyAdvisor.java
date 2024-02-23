package org.springframework.test.yzb.DynamicProxy.SpringJDKProxy.AspectJAop;

import org.aspectj.lang.ProceedingJoinPoint;

/**
 * 自定义通知类，有多个方法，每个方法可以绑定到不同的通知种类上
 */
public class MyAdvisor {
    public void mybefore() {
        System.out.println("执行前置通知方法");
    }

    public void myafter() {
        System.out.println("执行后置通知方法");
    }

    public void myafterReturnging() {
        System.out.println("执行myafterReturning");
    }

    public Object myaround(ProceedingJoinPoint p) throws Throwable {
        System.out.println("执行环绕通知-前置");
        // 执行切点方法
        Object result = p.proceed();
        System.out.println("执行环绕通知-后置");
        return result;
    }

    public void mythrow() {
        System.out.println("执行异常通知方法");
    }
}
