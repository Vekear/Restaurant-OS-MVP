package com.restaurant.integration.wechat;

/**
 * 微信 code2session 结果（只保留业务需要的字段）。
 *
 * <p>刻意不承载 session_key：按 ADR-0010，MVP 不解密用户数据，session_key 不落库也不下发。
 *
 * @param openid  微信用户唯一标识（mock 模式为确定性伪 openid）
 * @param unionid 开放平台 unionid，可能为 null
 */
public record WxSession(String openid, String unionid) {
}
