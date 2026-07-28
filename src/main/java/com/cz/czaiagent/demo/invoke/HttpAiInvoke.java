package com.cz.czaiagent.demo.invoke;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * 阿里灵积 AI 调用示例 - 使用纯 HTTP 请求方式
 * 这个类演示了如何不依赖官方 SDK，直接通过 HTTP 协议调用阿里云百炼的 Qwen 模型
 */
public class HttpAiInvoke {

    public static void main(String[] args) {

        // 1. 定义 API 请求地址（阿里云百炼文本生成服务的固定 endpoint）
        // 注意：这是公网网关地址，所有地域通用
        String url = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation";

        // 2. 构建 HTTP 请求头（headers）
        // 使用 HashMap 存储键值对
        Map<String, String> headers = new HashMap<>();
        
        // 设置认证头：Bearer Token 认证，后面拼接实际的 API Key
        // 格式：Authorization: Bearer sk-xxxxxxxx
        headers.put("Authorization", "Bearer " + TestApiKey.API_KEY);
        
        // 设置内容类型为 JSON，告诉服务器请求体是 JSON 格式
        headers.put("Content-Type", "application/json");

        // 3. 构建 HTTP 请求体（body）- 最核心的部分
        // 最外层 JSON 对象
        JSONObject requestBody = new JSONObject();
        
        // 指定要使用的模型名称，这里使用 qwen-plus（性价比和性能都很好的版本）
        requestBody.put("model", "qwen-plus");

        // 4. 构建 input 对象，存放对话消息
        JSONObject input = new JSONObject();
        
        // 创建消息数组，容量为 2（系统消息 + 用户消息）
        JSONObject[] messages = new JSONObject[2];

        // 5. 构建系统消息（system message）
        // 系统消息用于设定 AI 的角色、行为准则和回答风格
        JSONObject systemMessage = new JSONObject();
        systemMessage.put("role", "system");    // 角色：system
        systemMessage.put("content", "You are a helpful assistant.");  // 系统提示词
        messages[0] = systemMessage;            // 放入消息数组第 0 位

        // 6. 构建用户消息（user message）
        // 这是用户实际提出的问题
        JSONObject userMessage = new JSONObject();
        userMessage.put("role", "user");        // 角色：user
        userMessage.put("content", "你是谁？");   // 用户的问题
        messages[1] = userMessage;              // 放入消息数组第 1 位

        // 7. 将消息数组放入 input 对象
        input.put("messages", messages);
        requestBody.put("input", input);        // 将 input 放入最外层

        // 8. 构建 parameters 参数，控制模型的输出行为
        JSONObject parameters = new JSONObject();
        // result_format: 返回结果的格式，message 表示返回标准消息格式
        // 可选值：message（标准格式） 或 text（纯文本，不推荐）
        parameters.put("result_format", "message");
        requestBody.put("parameters", parameters);  // 将 parameters 放入最外层

        // 9. 发送 HTTP POST 请求
        // 链式调用：创建 POST 请求 -> 添加请求头 -> 设置请求体 -> 执行请求
        HttpResponse response = HttpRequest.post(url)    // 创建 POST 请求对象
                .addHeaders(headers)                     // 批量添加之前构建的请求头
                .body(requestBody.toString())            // 将 JSON 对象转为字符串作为请求体
                .execute();                              // 执行请求，获取响应对象

        // 10. 处理服务器响应
        // isOk() 方法判断 HTTP 状态码是否在 200-299 范围内（表示请求成功）
        if (response.isOk()) {
            // 请求成功：打印成功信息和响应体
            System.out.println("请求成功，响应内容：");
            System.out.println(response.body());
        } else {
            // 请求失败：打印状态码和错误信息，便于排查问题
            System.out.println("请求失败，状态码：" + response.getStatus());
            System.out.println("响应内容：" + response.body());
        }
        // 注意：程序执行完毕，main 方法结束，JVM 自动退出
    }
}