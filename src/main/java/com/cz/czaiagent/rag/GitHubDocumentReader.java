package com.cz.czaiagent.rag;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * GitHub 仓库文档读取器，负责从 GitHub 仓库中获取信息并转换为 Spring AI 的 Document 对象。
 * <p>
 * 通过 GitHub REST API 获取仓库的基本信息（名称、描述、语言、Star 数等）和 README 内容，
 * 将这些信息封装为 Document 对象，供 RAG 检索增强生成使用。
 */
@Component
@Slf4j
public class GitHubDocumentReader {
    /**
     * GitHub REST API 的基础地址，所有 API 请求都基于此地址拼接
     */
    private static final String GITHUB_API_BASE = "https://api.github.com";

    /**
     * 根据仓库所有者和仓库名称，从 GitHub 获取仓库信息并转换为 Document 列表。
     * <p>
     * 该方法会依次获取仓库基本信息和 README 内容，并将它们分别封装为独立的 Document 对象。
     * 每个 Document 都会携带仓库的元数据（如仓库全名、所有者、语言、Star 数等），
     * 便于后续 RAG 检索时进行元数据过滤。*
     * @param owner 仓库所有者（GitHub 用户名或组织名），例如 "spring-projects"
     * @param repo  仓库名称，例如 "spring-ai"
     * @return 包含仓库信息和 README 内容的 Document 列表；若获取失败则返回空列表*/
    public List<Document> readRepository(String owner, String repo){
        // 创建一个空列表，用于存放所有从 GitHub 获取并转换后的 Document 对象
        List<Document> documents = new ArrayList<>();
        //拼接仓库的完整路径，格式为 "所有者/仓库名"，例如 "spring-projects/spring-ai"
        String repoFullName = owner + "/" + repo;
        log.info("开始读取 GitHub 仓库：{}", repoFullName);
        // 第一步：调用 GitHub API 获取仓库的基本信息
        Document repoInfoDoc = fetchRepoInfo(owner, repo);
        // 如果成功获取到仓库信息，则添加到文档列表中
        if (repoInfoDoc != null) {
            documents.add(repoInfoDoc);
        }
        // 第二步：调用 GitHub API 获取仓库的 README 内容
        Document readmeDoc = fetchReadme(owner, repo);
        // 如果成功获取到 README 内容，则添加到文档列表中
        if (readmeDoc != null) {
            documents.add(readmeDoc);
        }
        // 记录本次读取最终获取到的文档数量
        log.info("GitHub 仓库 {} 共读取到 {} 个文档", repoFullName, documents.size());
        // 返回所有成功获取的文档列表
        return documents;
    }
    /**
     * 通过 GitHub API 获取仓库的基本信息，并将其封装为一个 Document 对象。
     * 获取的信息包括：仓库全名、描述、编程语言、Star 数、Fork 数、开源协议等。
     * 这些信息会被拼接为一段结构化的文本，作为 Document 的内容。
     * 同时，关键字段也会被存入 Document 的元数据中，便于后续检索时进行过滤。*
     * @param owner 仓库所有者
     * @param repo  仓库名称
     * @return 包含仓库基本信息的 Document 对象；若请求失败或解析异常则返回 null
     */
    private Document fetchRepoInfo(String owner, String repo) {
        // 拼接获取仓库信息的 API 地址，例如：https://api.github.com/repos/spring-projects/spring-ai
        String url = GITHUB_API_BASE + "/repos/" + owner + "/" + repo;
        try {
            // 发送 HTTP GET 请求到 GitHub API，获取仓库信息的 JSON 响应
            String responseBody = HttpUtil.createGet(url)
                    // 设置请求头，GitHub API 要求必须提供 User-Agent，否则可能被拒绝
                    .header("User-Agent", "cz-ai-agent")
                    // 设置请求头，指定接受 JSON 格式的响应数据
                    .header("Accept", "application/vnd.github.v3+json")
                    // 执行请求并获取响应体字符串
                    .execute()
                    .body();
            // 使用 Hutool 的 JSONUtil 将响应字符串解析为 JSONObject 对象，便于按字段提取数据
            JSONObject json = JSONUtil.parseObj(responseBody);
            // 从 JSON 中提取仓库全名（如 "spring-projects/spring-ai"）
            String fullName = json.getStr("full_name");
            // 从 JSON 中提取仓库的描述信息，如果没有描述则默认为 "暂无描述"
            String description = json.getStr("description", "暂无描述");
            // 从 JSON 中提取仓库使用的主要编程语言
            String language = json.getStr("language", "未知");
            // 从 JSON 中提取仓库的 Star 数量
            int stargazersCount = json.getInt("stargazers_count", 0);
            // 从 JSON 中提取仓库的 Fork 数量
            int forksCount = json.getInt("forks_count", 0);
            // 从 JSON 中提取仓库的开源协议名称（如 MIT、Apache-2.0 等）
            String license = json.getJSONObject("license") != null
                    ? json.getJSONObject("license").getStr("name", "无")
                    : "无";
            // 将仓库的各项信息拼接为一段结构化的文本，作为 Document 的正文内容
            String content = String.format(
                    "仓库名称：%s\n仓库描述：%s\n编程语言：%s\nStar 数：%d\nFork 数：%d\n开源协议：%s",
                    fullName, description, language, stargazersCount, forksCount, license
            );
            // 构建 Document 的元数据 Map，将关键字段存入元数据，便于后续 RAG 检索时进行过滤
            Map<String, Object> metadata = new HashMap<>();
            // 记录数据来源为 GitHub，便于区分不同来源的文档
            metadata.put("source", "github");
            // 记录仓库全名
            metadata.put("repo", fullName);
            // 记录仓库的主要编程语言
            metadata.put("language", language);
            // 记录文档类型为仓库信息，便于与 README 等其他类型文档区分
            metadata.put("type", "repo_info");
            // 使用拼接好的内容和元数据创建 Document 对象并返回
            log.info("成功获取仓库信息：{}", fullName);
            return new Document(content, metadata);
        }catch (Exception e) {
            // 如果请求或解析过程中出现异常，记录错误日志并返回 null
            log.error("获取 GitHub 仓库信息失败：{}/{}", owner, repo, e);
            return null;
        }
    }

    /**
     * 通过 GitHub API 获取仓库的 README 文件内容，并将其封装为一个 Document 对象。
     * GitHub API 返回的 README 内容是 Base64 编码的，需要先解码才能获取原始文本。
     * README 的内容通常包含项目的介绍、安装指南、使用说明等，是 RAG 检索的重要知识来源。
     * @param owner 仓库所有者
     * @param repo  仓库名称
     * @return 包含 README 内容的 Document 对象；若请求失败、README 不存在或解码异常则返回 null
     */
    private Document fetchReadme(String owner, String repo) {
        // 拼接获取 README 的 API 地址，例如：https://api.github.com/repos/spring-projects/spring-ai/readme
        String url = GITHUB_API_BASE + "/repos/" + owner + "/" + repo + "/readme";
        try {
            // 发送 HTTP GET 请求到 GitHub API，获取 README 信息的 JSON 响应
            String responseBody = HttpUtil.createGet(url)
                    // 设置请求头，GitHub API 要求必须提供 User-Agent
                    .header("User-Agent", "cz-ai-agent")
                    // 设置请求头，指定接受 JSON 格式的响应数据
                    .header("Accept", "application/vnd.github.v3+json")
                    // 执行请求并获取响应体字符串
                    .execute()
                    .body();
            // 使用 Hutool 的 JSONUtil 将响应字符串解析为 JSONObject 对象
            JSONObject json = JSONUtil.parseObj(responseBody);
            // 从 JSON 中提取 README 文件的实际名称（如 "README.md"）
            String readmeName = json.getStr("name", "README.md");
            // 从 JSON 中提取 README 文件的内容，GitHub API 返回的内容是 Base64 编码的字符串
            String contentBase64 = json.getStr("content");
            // 如果内容为空，说明仓库没有 README 文件，记录日志并返回 null
            if (contentBase64 == null || contentBase64.isEmpty()) {
                log.warn("仓库 {}/{} 没有 README 文件", owner, repo);
                return null;
            }
            // 移除 Base64 字符串中可能存在的换行符，确保解码不会因格式问题而失败
            String cleanBase64 = contentBase64.replaceAll("\\s", "");
           // 使用 Java 内置的 Base64 解码器将编码内容还原为原始字节
            byte[] decodedBytes = Base64.getDecoder().decode(cleanBase64);
           // 将字节数组转换为 UTF-8 编码的字符串，得到 README 的原始 Markdown 文本
            String readmeContent = new String(decodedBytes, StandardCharsets.UTF_8);
            // 拼接仓库全名，用于元数据中标识 README 所属的仓库
            String repoFullName = owner + "/" + repo;
            // 构建 Document 的元数据 Map
            Map<String, Object> metadata = new HashMap<>();
            // 记录数据来源为 GitHub
            metadata.put("source", "github");
            // 记录仓库全名
            metadata.put("repo", repoFullName);
            // 记录文档类型为 README，便于与仓库信息等其他类型文档区分
            metadata.put("type", "readme");
            // 记录 README 文件名
            metadata.put("filename", readmeName);
            // 使用 README 的原始内容和元数据创建 Document 对象并返回
            log.info("成功获取仓库 README：{}，内容长度：{} 字符", repoFullName, readmeContent.length());
            return new Document(readmeContent, metadata);
        }catch (Exception e) {
            // 如果请求或解码过程中出现异常，记录错误日志并返回 null
            log.error("获取 GitHub README 失败：{}/{}", owner, repo, e);
            return null;
        }
    }
}