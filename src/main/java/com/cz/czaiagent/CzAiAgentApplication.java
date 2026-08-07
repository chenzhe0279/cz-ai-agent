package com.cz.czaiagent;


import org.springframework.ai.autoconfigure.vectorstore.pgvector.PgVectorStoreAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication(exclude = PgVectorStoreAutoConfiguration.class)
//@MapperScan("com.cz.czaiagent.mapper")
@EnableAspectJAutoProxy(exposeProxy = true)
public class CzAiAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(CzAiAgentApplication.class, args);
    }

}
