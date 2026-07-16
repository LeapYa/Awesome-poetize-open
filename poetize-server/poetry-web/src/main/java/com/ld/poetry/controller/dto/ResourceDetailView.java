package com.ld.poetry.controller.dto;

import com.ld.poetry.entity.Resource;
import com.ld.poetry.entity.ResourceAlias;
import com.ld.poetry.entity.ResourceLocation;

import java.util.List;

/**
 * 资源详情视图：聚合逻辑资源、全部物理副本、活动别名与引用计数，
 * 供管理端资源详情对话框展示稳定 URL、活动存储、副本健康度与 SHA-256 可信状态。
 *
 * @param resource        逻辑资源行（含 publicId、contentState、resourceHash、hashSource、hashVerifiedAt、locationVersion、activeLocationId）
 * @param locations       全部物理副本（按 id 升序），含 status、storeType、contentHash、verifiedAt
 * @param aliases         全部历史别名（按 id 升序），含 status、aliasUrl、sourceType
 * @param referenceCount  稳定地址与全部活动别名的业务引用计数总和
 * @param stableUrl       稳定访问地址（/media/{publicId}）
 */
public record ResourceDetailView(
        Resource resource,
        List<ResourceLocation> locations,
        List<ResourceAlias> aliases,
        int referenceCount,
        String stableUrl
) {
}
