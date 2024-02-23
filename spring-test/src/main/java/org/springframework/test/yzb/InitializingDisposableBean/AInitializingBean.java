package org.springframework.test.yzb.InitializingDisposableBean;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

public class AInitializingBean implements InitializingBean, DisposableBean {

	@Override
	public void afterPropertiesSet() throws Exception {
		System.out.println("实现 InitializingBean 接口");
	}

	@Override
	public void destroy() throws Exception {
		System.out.println("实现 DisposableBean 接口");
	}
}
