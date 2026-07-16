package com.ld.poetry.utils.storage;

import com.ld.poetry.vo.FileVO;

import java.util.Collections;
import java.util.List;

/**
 * 存储适配器只负责物理文件操作，不修改资源数据库记录。
 */
public interface StoreService {

    List<StorageDeleteResult> deleteFiles(List<StorageResourceRef> resources);

    default void deleteFile(List<String> files) {
        if (files == null) {
            return;
        }
        deleteFiles(files.stream().map(StorageResourceRef::pathOnly).toList());
    }

    FileVO saveFile(FileVO fileVO);

    default StorageReadHandle openRead(StorageResourceRef resource, long maxBytes) {
        throw new UnsupportedOperationException("当前存储平台不支持读取原始文件");
    }

    default StorageRangeReadHandle openReadRange(StorageResourceRef resource,
                                                 long startInclusive,
                                                 long endInclusive) {
        throw new UnsupportedOperationException("当前存储平台不支持区间读取");
    }

    default StorageVerificationResult verify(StorageResourceRef resource) {
        return StorageVerificationResult.unknown("当前存储平台不支持主动校验");
    }

    /**
     * 生成面向客户端的受控访问地址。返回 null 表示该平台必须由服务端读取，
     * 控制器不得直接重定向到数据库中保存的物理地址。
     */
    default StorageClientAccess resolveClientAccess(StorageResourceRef resource) {
        return null;
    }

    /**
     * 将迁移层提供的确定性对象键转换为可读取的物理地址。
     * 无法预先确定访问地址的平台返回 null，上传成功后以平台响应为准。
     */
    default String resolveAccessPath(String storageKey) {
        return null;
    }

    /**
     * 从当前适配器明确可信的访问地址解析物理对象键。
     * 无法由平台配置确定性证明对象键时返回 null，调用方不得按文件名猜测。
     */
    default String resolveStorageKey(String accessPath) {
        return null;
    }

    /**
     * 严格迁移和受管上传要求写入前可重建目标引用，并且 createOnly=true 时拒绝覆盖。
     */
    default boolean supportsDeterministicWrite() {
        return false;
    }

    /**
     * 判断物理访问地址是否属于当前存储适配器配置的公开域名。
     */
    default boolean isPublicAccessPathTrusted(String accessPath) {
        return false;
    }

    default StorageCapability getCapability() {
        return new StorageCapability(getStoreName(), true, false, true, true, false, 0, Collections.emptyList());
    }

    String getStoreName();
}
