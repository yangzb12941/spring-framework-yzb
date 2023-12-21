package org.springframework.aop.yangzbxmlaop;

public class BookServiceImpl implements IBookService {

	@Override
	public int save(int n) {
		System.out.println("添加");
		return 1;
	}

	@Override
	public int del() {
		System.out.println("删除");
		return 1;
	}

	@Override
	public int update() {
		System.out.println("修改");
		return 1;
	}

	@Override
	public void find() {
		System.out.println("查询");
	}

}
