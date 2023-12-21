package org.springframework.aop.yangzbxmlaop;

/**
 * 账户的业务层接口
 *  下面有三个不同类型的方法
 */
public interface IAccountService {
	/**
	 * 模拟保存账户
	 * 无参无返回值
	 */
	void saveAccount();
	/**
	 * 模拟更新账户
	 * 有参无返回值
	 */
	void updateAccount(int i);
	/**
	 * 删除账户
	 * 无参有返回值
	 */
	int  deleteAccount();
}
