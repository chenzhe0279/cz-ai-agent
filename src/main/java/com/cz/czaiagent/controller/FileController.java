package com.cz.czaiagent.controller;

import cn.hutool.core.util.StrUtil;
import com.cz.czaiagent.constant.FileConstant;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * 静态文件访问接口（公开，无需登录），用于展示上传的头像等
 */
@RestController
@RequestMapping("/file")
public class FileController {

    private static final String AVATAR_DIR = FileConstant.FILE_SAVE_DIR + "/avatar";

    private static final Pattern SAFE_FILE_NAME = Pattern.compile("^[A-Za-z0-9._-]+$");

    /**
     * 读取头像文件，例如 /file/avatar/xxxx.jpg
     */
    @GetMapping("/avatar/{filename}")
    public ResponseEntity<Resource> getAvatar(@PathVariable String filename) {
        if (!SAFE_FILE_NAME.matcher(filename).matches()) {
            return ResponseEntity.badRequest().build();
        }
        File file = new File(AVATAR_DIR, filename);
        if (!file.isFile()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(resolveMediaType(filename))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)))
                .body(new FileSystemResource(file));
    }

    private MediaType resolveMediaType(String filename) {
        String ext = StrUtil.subAfter(filename, ".", true).toLowerCase();
        return switch (ext) {
            case "png" -> MediaType.IMAGE_PNG;
            case "gif" -> MediaType.IMAGE_GIF;
            case "webp" -> MediaType.parseMediaType("image/webp");
            default -> MediaType.IMAGE_JPEG;
        };
    }
}
