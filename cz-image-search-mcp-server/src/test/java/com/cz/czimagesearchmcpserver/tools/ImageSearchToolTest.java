package com.cz.czimagesearchmcpserver.tools;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class ImageSearchToolTest {
    @Resource
    private ImageSearchTool imageSearchTool;
    @Test
    void searchImage() {
        String query = "帮我找一下星空图片";
        String searchImage = imageSearchTool.searchImage(query);
        System.out.println(searchImage);
    }

    @Test
    void searchMediumImages() {
    }
}