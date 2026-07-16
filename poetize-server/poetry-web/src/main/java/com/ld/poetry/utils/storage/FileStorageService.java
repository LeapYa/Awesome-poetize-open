package com.ld.poetry.utils.storage;


import com.ld.poetry.handle.PoetryRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * 用来处理文件存储，对接多个平台
 */
@Slf4j
@Component
public class FileStorageService implements ApplicationContextAware {

    @Value("${store.type}")
    private String defaultType;

    private final Map<String, StoreService> storeServiceMap = new HashMap<>();

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        Map<String, StoreService> consumeService = applicationContext.getBeansOfType(StoreService.class);
        if (!CollectionUtils.isEmpty(consumeService)) {
            for (StoreService value : consumeService.values()) {
                storeServiceMap.put(value.getStoreName(), value);
                log.info("注册存储服务: {} -> {}", value.getStoreName(), value.getClass().getSimpleName());
            }
        }
        log.info("存储服务初始化完成，可用服务: {}", storeServiceMap.keySet());
    }

    /**
     * 获取对应的存储平台，如果存储平台不存在则抛出异常
     */
    public StoreService getFileStorageByStoreType(String storeType) {
        if (!StringUtils.hasText(storeType) || !storeServiceMap.containsKey(storeType)) {
            throw new PoetryRuntimeException("没有找到对应的存储平台：" + storeType);
        }

        return storeServiceMap.get(storeType);
    }

    public List<StorageCapability> listCapabilities() {
        return storeServiceMap.values().stream()
                .map(StoreService::getCapability)
                .sorted(Comparator.comparing(StorageCapability::storeType))
                .toList();
    }

    public List<StoreService> listFileStorages() {
        return storeServiceMap.values().stream()
                .sorted(Comparator.comparing(StoreService::getStoreName))
                .toList();
    }

    public List<StorageCapability> listMigrationTargets() {
        return storeServiceMap.values().stream()
                .filter(StoreService::supportsDeterministicWrite)
                .map(StoreService::getCapability)
                .filter(StorageCapability::enabled)
                .filter(StorageCapability::uploadSupported)
                .filter(StorageCapability::readSupported)
                .filter(StorageCapability::verifySupported)
                .sorted(Comparator.comparing(StorageCapability::storeType))
                .toList();
    }

    /**
     * 获取对应的存储平台，如果没有指定则使用默认值
     */
    public StoreService getFileStorage(String storeType) {
        log.info("getFileStorage 被调用 - 传入的 storeType: {}, defaultType: {}", storeType, defaultType);
        if (!StringUtils.hasText(storeType)) {
            storeType = defaultType;
            log.info("storeType 为空，使用默认值: {}", storeType);
        }

        log.info("当前可用的存储服务: {}", storeServiceMap.keySet());
        if (!storeServiceMap.containsKey(storeType)) {
            log.error("没有找到存储平台: {}, 可用的服务: {}", storeType, storeServiceMap.keySet());
            throw new PoetryRuntimeException("没有找到对应的存储平台：" + storeType);
        }

        return storeServiceMap.get(storeType);
    }
}
