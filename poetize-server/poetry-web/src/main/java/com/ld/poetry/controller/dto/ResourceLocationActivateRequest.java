package com.ld.poetry.controller.dto;

/**
 * 激活替代副本请求：把一个已验证的 RETAINED 副本提升为活动副本。
 *
 * <p>仅在删除当前活动副本前、或管理员主动切换活动存储时使用。要求：
 * <ul>
 *   <li>{@code expectedActiveLocationId} 必须与当前 {@code resource.activeLocationId} 一致（CAS）</li>
 *   <li>{@code replacementLocationId} 由路径变量指定，必须是同一资源下已完整回读验证的 RETAINED 副本</li>
 * </ul>
 *
 * @param expectedActiveLocationId 调用方读取到的当前活动副本 id，用于 CAS 防并发
 */
public record ResourceLocationActivateRequest(Long expectedActiveLocationId) {
}
