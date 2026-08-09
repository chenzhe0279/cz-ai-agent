package com.cz.czaiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;

@Slf4j
public class LoveAppContextualQueryAugmenterFactory {

    /**
     * 创建恋爱应用专用的上下文查询增强器
     * <p>
     * 当RAG检索未命中任何相关文档时，使用自定义的兜底回复引导用户，
     * 限制AI仅回答恋爱相关问题，拒绝无关提问。
     *
     * @return 配置了兜底回复模板的 {@link ContextualQueryAugmenter} 实例
     */
    public static ContextualQueryAugmenter createInstance() {
        // 定义知识库未命中时的兜底回复模板，引导用户仅提问恋爱相关内容
        PromptTemplate emptyContextPromptTemplate = new PromptTemplate("""
                你应该输出下面的内容：
                抱歉，我只能回答恋爱相关的问题，别的没办法帮到您哦，
                有问题可以联系编程导航客服 https://codefather.cn
                """);
        // 构建不允许空上下文的查询增强器，并绑定兜底回复模板
        return ContextualQueryAugmenter.builder()
                .allowEmptyContext(false)
                .emptyContextPromptTemplate(emptyContextPromptTemplate)
                .build();
    }
}
