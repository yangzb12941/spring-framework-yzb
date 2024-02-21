package org.springframework.test.proxy.DynamicProxy.SpringJDKProxy.AspectJAop;

public class AspectJAopTarget {

    public void aopMethodBefore(){
        System.out.println("AspectJAopTarget.aopMethodBefore");
    }

    public void aopMethodAfter(){
        System.out.println("AspectJAopTarget.aopMethodAfter");
    }
}
