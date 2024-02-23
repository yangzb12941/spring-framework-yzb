package org.springframework.test.yzb.DynamicProxy.SpringJDKProxy.SchemaBasedAop;

public class SchemaBasedAopTarget {

    public void aopMethodBefore(){
        System.out.println("SchemaBasedAopTarget.aopMethodBefore");
    }

    public void aopMethodAfter(){
        System.out.println("SchemaBasedAopTarget.aopMethodAfter");
    }
}
