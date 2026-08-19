package com.cz.czaiagent.agent;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CzManusTest {
    @Resource
    private CzManus czManus;

    @Test
    void run() {
        String userPrompt = """  
                我的另一半居住在上海静安区，请帮我找到 5 公里内合适的约会地点，
                并详细介绍一下这些约会地点。  
                请先使用搜索工具查找这些约会地点的网络图片链接，
                然后使用下载工具把图片下载到本地，
                接着制定一份详细的约会计划，
                生成 PDF 时把下载好的图片嵌入进去一起输出。
                PDF 内容必须完整包含以下部分，不得省略或概括：
                1. 每个约会地点的详细介绍：包括地点特色、推荐理由、
                   地址与交通方式、适合约会的亮点，每个地点不少于 150 字；
                   并且每写完一个地点的介绍，必须紧跟一行 [图片:该地点对应图片的本地路径]，
                   让图片紧跟在对应地点介绍之后显示，禁止把所有图片集中放在文档末尾；
                2. 完整的一日约会行程安排：按时间顺序规划从早到晚的行程，
                   说明每个时间段去哪个地点、做什么、为什么这样安排；
                3. 约会小贴士：包括预算参考、注意事项、备选方案等。
                PDF 生成完成后，请使用邮件工具把这份 PDF 文件作为附件，
                发送到 3302076969@qq.com 这个邮箱，
                邮件主题为"上海静安区约会计划"，
                邮件正文写一段简短的约会计划摘要。
                要求：
                1. 所有内容（包括约会计划正文、PDF 内容和邮件正文）必须使用中文撰写；
                2. PDF 内容中不要使用 emoji 表情或特殊符号；
                3. PDF 文件名使用中文命名。""";
        String answer = czManus.run(userPrompt);
        Assertions.assertNotNull(answer);
        System.out.println(answer);
    }
}