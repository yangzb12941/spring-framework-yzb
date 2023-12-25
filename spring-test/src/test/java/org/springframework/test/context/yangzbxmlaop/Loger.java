package org.springframework.test.context.yangzbxmlaop;

import org.aspectj.lang.ProceedingJoinPoint;

public class Loger {
	public void check(){
		System.out.println("前置通知/增强:执行系统的权限验证");
	}
	public void logPrint(){
		System.out.println("后置通知/增强:执行日志的打印");
	}
	public void exception(){
		System.out.println("异常通知/增强:做出异常处理");
	}
	public void distory(){

		System.out.println("最终通知/增强:资源释放");
	}
	public Object around(ProceedingJoinPoint pjp) {
		try {
			//前置增强
			System.out.println("环绕通知---前置增强");
			//通过ProceedingJoinPoint 完成代理对象的方法调用
			Object result = null;//定义返回值变量
			Object[] args = pjp.getArgs();//获取参数列表
			result = pjp.proceed(args);
			//后置增强
			System.out.println("环绕通知---后置增强");
			return result;
		} catch (Throwable e) {
			//异常通知
			System.out.println("环绕通知----异常增强");
			throw new RuntimeException(e);
		} finally {
			//最终增强
			System.out.println("环绕通知----最终增强");
		}
	}
}
