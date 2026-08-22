package com.cz.czaiagent.rag;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetriever;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetrieverOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 配置类，用于初始化基于云知识库的检索增强顾问Bean
 */
//@Configuration
@Slf4j
public class LoveAppRagCloudAdvisorConfig {

    @Value("${spring.ai.dashscope.api-key}")
    private String dashScopeApiKey;

    private final String KONWLEDGE_INDEX = "恋爱大师";

    /**
     * 创建并注册一个RAG（检索增强生成）顾问Bean到Spring容器中。
     * <p>
     * 该顾问的工作流程如下：
     * 1. 当用户向大模型提问时，该顾问会自动拦截请求；
     * 2. 根据用户的问题，从DashScope云端的知识库中检索出相关的文档片段；
     * 3. 将检索到的文档作为补充上下文，拼接到用户的原始问题中；
     * 4. 最终将增强后的提示词发送给大模型，从而获得更准确、更有针对性的回答。
     * </p>
     * <p>
     * 简单来说，就是让大模型在回答之前先"查资料"，做到"开卷考试"，提升回答质量。
     * </p>
     *
     * @return 一个配置好的 {@link RetrievalAugmentationAdvisor} 实例，
     *         内部集成了指向"恋爱大师"知识库的文档检索器，
     *         可直接被Spring AI的ChatClient使用
     */
    //@Bean
    public Advisor loveAppRagCloudAdvisor(){
        // 第一步：创建DashScope云端API客户端，使用配置文件中注入的API密钥进行身份认证
        DashScopeApi dashScopeApi = new DashScopeApi(dashScopeApiKey);
        // 第二步：创建文档检索器，并指定要从哪个知识库索引中检索文档
        // 这里指定的索引名称为"恋爱大师"，即所有检索操作都在该知识库范围内进行
        DocumentRetriever documentRetriever = new DashScopeDocumentRetriever(dashScopeApi,
                DashScopeDocumentRetrieverOptions.builder().
                        withIndexName(KONWLEDGE_INDEX).
                        build());

        // 第三步：将文档检索器装配到RetrievalAugmentationAdvisor中，
        // 构建完成后返回该Advisor，Spring会自动将其纳入Bean管理
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)
                .build();
    }
}
