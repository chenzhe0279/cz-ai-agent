package com.cz.czaiagent.rag;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 基于百度翻译 API 的查询转换器，将用户的查询文本进行翻译转换。
 * 与 Spring AI 内置的基于大模型的查询转换器不同，
 * 本转换器通过调用百度翻译 API 实现翻译功能，
 * 无需消耗大模型 Token，从而显著降低使用成本。
 */
@Component
@Slf4j
public class TranslationQueryTransformer implements QueryTransformer {

    /**
     * 百度翻译 API 的请求地址
     */
    private static final String BAIDU_TRANSLATE_API = "https://fanyi-api.baidu.com/api/trans/vip/translate";

    /**
     * 百度翻译应用的 APP ID，从配置文件中读取
     */
    @Value("${baidu.translate.app-id}")
    private String appId;

    /**
     * 百度翻译应用的密钥，从配置文件中读取，用于生成签名
     */
    @Value("${baidu.translate.security-key}")
    private String securityKey;
    /**
     * 对用户查询进行翻译转换，默认将中文翻译为英文。
     * 该方法会调用百度翻译 API，将原始查询文本从中文翻译为英文，
     * 并将翻译结果封装为新的 Query 对象返回。
     * 如果翻译过程中出现异常，则记录错误日志并返回原始查询（不做任何转换），
     * 确保翻译失败不会阻断整个 RAG 检索流程。
     * @param query 用户原始的查询对象，包含原始文本内容
     * @return 翻译后的新 Query 对象；若翻译失败则返回原始 Query 对象
     */
    @Override
    public Query transform(Query query) {
        // 获取用户原始查询文本
        String originalText = query.text();
        log.info("翻译查询转换器 - 原始查询：{}", originalText);

        try {
            // 调用百度翻译 API，将中文文本翻译为英文
            String translatedText = translate(originalText, "zh", "en");

            // 如果翻译结果为空或翻译失败，则返回原始查询
            if (translatedText == null || translatedText.isEmpty()) {
                log.warn("翻译结果为空，返回原始查询");
                return query;
            }

            // 记录翻译成功日志，输出翻译前后的文本对比
            log.info("翻译查询转换器 - 翻译结果：{}", translatedText);

            // 将翻译后的文本封装为新的 Query 对象并返回
            return new Query(translatedText);
        } catch (Exception e) {
            // 翻译过程中出现异常，记录错误日志并返回原始查询，确保不阻断 RAG 流程
            log.error("翻译查询转换器异常，返回原始查询", e);
            return query;
        }
    }

    /**
     * 调用百度翻译 API 执行实际的文本翻译。
     * 百度翻译 API 的签名生成流程：
     * 1. 生成一个随机盐值（salt）
     * 2. 将 appId + 待翻译文本 + salt + securityKey 拼接成一个字符串
     * 3. 对拼接后的字符串计算 MD5 摘要，得到签名（sign）
     * 4. 将待翻译文本、源语言、目标语言、appId、salt、sign 作为请求参数发送
     * @param text       待翻译的原始文本
     * @param langSource 源语言代码，例如 "zh" 表示中文，"en" 表示英文
     * @param langTarget 目标语言代码，例如 "en" 表示英文，"zh" 表示中文
     * @return 翻译后的文本；若请求失败或解析异常则返回 null
     */
    private String translate(String text, String langSource, String langTarget) {
        try {
            // 生成随机盐值，用于防止重放攻击，确保每次请求的签名都不同
            String salt = IdUtil.fastSimpleUUID();

            // 按照百度翻译 API 的签名规则，拼接原始字符串：appId + 待翻译文本 + salt + securityKey
            String signString = appId + text + salt + securityKey;

            // 对拼接后的字符串计算 MD5 摘要，生成签名（sign）
            String sign = DigestUtil.md5Hex(signString);

            // 构建百度翻译 API 的请求参数 Map
            Map<String, Object> params = new HashMap<>();
            // q：待翻译的文本内容
            params.put("q", text);
            // from：源语言代码，如 "zh"（中文）、"en"（英文）、"auto"（自动检测）
            params.put("from", langSource);
            // to：目标语言代码，如 "en"（英文）、"zh"（中文）
            params.put("to", langTarget);
            // appid：百度翻译应用的 APP ID
            params.put("appid", appId);
            // salt：随机盐值，与签名生成时使用的盐值保持一致
            params.put("salt", salt);
            // sign：签名，用于百度翻译服务端验证请求的合法性
            params.put("sign", sign);

            // 发送 HTTP GET 请求到百度翻译 API，并获取 JSON 格式的响应字符串
            String responseBody = HttpUtil.get(BAIDU_TRANSLATE_API, params);

            // 使用 Hutool 的 JSONUtil 将响应字符串解析为 JSONObject 对象
            JSONObject json = JSONUtil.parseObj(responseBody);

            // 检查 API 响应中是否包含 error_code 字段，如果存在则表示翻译请求出错
            if (json.containsKey("error_code")) {
                // 记录错误码和错误信息，便于排查问题
                log.warn("百度翻译 API 返回错误，错误码：{}，错误信息：{}",
                        json.getStr("error_code"), json.getStr("error_msg"));
                return null;
            }

            // 从响应的 trans_result 数组中提取翻译结果
            // trans_result 是一个 JSON 数组，每个元素包含 src（原文）和 dst（译文）
            JSONArray transResult = json.getJSONArray("trans_result");

            // 如果翻译结果数组为空，则返回 null
            if (transResult == null || transResult.isEmpty()) {
                log.warn("百度翻译 API 返回空的翻译结果");
                return null;
            }

            // 取第一条翻译结果（百度翻译可能返回多条候选翻译，这里只取第一条）
            JSONObject firstResult = transResult.getJSONObject(0);
            // 获取翻译后的文本内容
            String translatedText = firstResult.getStr("dst");

            // 返回翻译结果文本
            return translatedText;
        } catch (Exception e) {
            // 请求或解析过程中出现异常，记录错误日志并返回 null
            log.error("调用百度翻译 API 失败", e);
            return null;
        }
    }
}
