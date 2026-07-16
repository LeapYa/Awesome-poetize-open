package com.ld.poetry.vo;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class FileVO {

    private String type;

    private String storeType;

    private String relativePath;

    private String absolutePath;

    private String visitPath;

    private MultipartFile file;

    private String originalName;

    private String resourceHash;

    private String storageKey;

    private Boolean reuseExistingResource = false;

    /**
     * 为 true 时存储适配器必须拒绝覆盖已存在对象。
     */
    private Boolean createOnly = false;
}
