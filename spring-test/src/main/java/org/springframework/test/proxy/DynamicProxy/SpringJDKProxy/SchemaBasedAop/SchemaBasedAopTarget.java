package org.springframework.test.proxy.DynamicProxy.SpringJDKProxy.SchemaBasedAop;

public class SchemaBasedAopTarget {

    public void aopMethodBefore(){
        System.out.println("SchemaBasedAopTarget.aopMethodBefore");
    }

    public void aopMethodAfter(){
        System.out.println("SchemaBasedAopTarget.aopMethodAfter");
    }
}
