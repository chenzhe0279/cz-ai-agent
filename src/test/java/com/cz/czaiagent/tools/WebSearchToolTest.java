package com.cz.czaiagent.tools;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class WebSearchToolTest {

    @Value("${search-api.api-key}")
    private String searchApiKey;

    @Resource
    private  WebSearchTool webSearchTool;
    @Test
    public void testSearchWeb() {
        //WebSearchTool tool = new WebSearchTool();
        String query = "程序员鱼皮编程导航 codefather.cn";
        //String result = tool.searchWeb(query);
        String result = webSearchTool.searchWeb(query);
        assertNotNull(result);
    }
}

