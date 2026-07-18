package com.zwinsight.message.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 企业微信群机器人通知服务
 * <p>
 * 通过企业微信「群机器人」Webhook 真实推送文本消息，仅需配置一个 webhook 地址（key），
 * 无需 corpId/agentId/access_token，也无需维护用户与企微账号的映射关系，
 * 适合系统级告警/催办的群通知场景。
 * </p>
 * <p>
 * 配置项：
 * <pre>
 * wework:
 *   robot:
 *     enabled: false                        # 是否启用企微机器人推送
 *     webhook: https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=your-robot-key
 * </pre>
 * 官方文档：https://developer.work.weixin.qq.com/document/path/91770
 * </p>
 */
@Slf4j
@Service
public class WeChatWorkService {

    @Value("${wework.robot.enabled:false}")
    private boolean robotEnabled;

    @Value("${wework.robot.webhook:}")
    private String robotWebhook;

    /**
     * 通过企微群机器人发送文本消息。
     *
     * @param content 消息内容
     * @return 是否已实际发送（未启用时返回 false）
     */
    public boolean sendText(String content) {
        if (!robotEnabled) {
            log.debug("[WEWORK] 企微机器人未启用，跳过推送: content={}", content);
            return false;
        }
        if (StrUtil.isBlank(robotWebhook)) {
            log.warn("[WEWORK] 企微机器人已启用但未配置 wework.robot.webhook，跳过推送");
            return false;
        }

        JSONObject textNode = new JSONObject();
        textNode.set("content", content);
        JSONObject body = new JSONObject();
        body.set("msgtype", "text");
        body.set("text", textNode);

        try {
            String resp = HttpUtil.post(robotWebhook, body.toString());
            JSONObject respJson = JSONUtil.parseObj(resp);
            Integer errcode = respJson.getInt("errcode");
            if (errcode != null && errcode == 0) {
                log.info("[WEWORK] 企微机器人推送成功");
                return true;
            }
            log.error("[WEWORK] 企微机器人推送失败: errcode={}, errmsg={}", errcode, respJson.getStr("errmsg"));
            return false;
        } catch (Exception e) {
            log.error("[WEWORK] 企微机器人推送异常", e);
            return false;
        }
    }
}
