package com.zwinsight.common.desensitize;

/**
 * 数据脱敏工具类（纯函数，无状态）
 * <p>
 * 根据指定的脱敏类型对字符串执行掩码处理。
 * </p>
 *
 * <ul>
 *   <li>PHONE: 前3后4，中间固定4个星号（138****1234）</li>
 *   <li>ID_CARD: 前3后4，中间以星号填充（110***********1234）</li>
 *   <li>BANK_CARD: 前4后4，中间以星号填充（6222************1234）</li>
 *   <li>EMAIL: 首字母 + 星号 + @域名（z***@example.com）</li>
 *   <li>ADDRESS: 前6字符 + 星号（北京市朝阳区****）</li>
 * </ul>
 *
 * <p>null/空字符串原样返回；长度不足 minLen 时全部替换为等长星号。</p>
 */
public final class DesensitizeUtil {

    private DesensitizeUtil() {
        // 工具类不允许实例化
    }

    /**
     * 根据脱敏类型对字符串执行掩码处理
     *
     * @param value 原始值
     * @param type  脱敏类型
     * @return 脱敏后的字符串
     */
    public static String desensitize(String value, DesensitizeType type) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        return switch (type) {
            case PHONE -> maskPhone(value);
            case ID_CARD -> maskIdCard(value);
            case BANK_CARD -> maskBankCard(value);
            case EMAIL -> maskEmail(value);
            case ADDRESS -> maskAddress(value);
        };
    }

    /**
     * 手机号脱敏：保留前3后4，中间固定4个星号
     * <p>示例：13812345678 → 138****5678</p>
     */
    private static String maskPhone(String value) {
        if (value.length() < DesensitizeType.PHONE.getMinLen()) {
            return "*".repeat(value.length());
        }
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }

    /**
     * 身份证号脱敏：保留前3后4，中间以星号填充
     * <p>示例：110101199001011234 → 110***********1234</p>
     */
    private static String maskIdCard(String value) {
        if (value.length() < DesensitizeType.ID_CARD.getMinLen()) {
            return "*".repeat(value.length());
        }
        return value.substring(0, 3)
                + "*".repeat(value.length() - 7)
                + value.substring(value.length() - 4);
    }

    /**
     * 银行卡号脱敏：保留前4后4，中间以星号填充
     * <p>示例：6222021234561234 → 6222********1234</p>
     */
    private static String maskBankCard(String value) {
        if (value.length() < DesensitizeType.BANK_CARD.getMinLen()) {
            return "*".repeat(value.length());
        }
        return value.substring(0, 4)
                + "*".repeat(value.length() - 8)
                + value.substring(value.length() - 4);
    }

    /**
     * 邮箱脱敏：保留首字母 + 星号 + @域名
     * <p>示例：zhangsan@example.com → z*******@example.com</p>
     */
    private static String maskEmail(String value) {
        int atIndex = value.indexOf('@');
        if (atIndex < 1) {
            // 无有效@符号或@在首位，视为无法识别邮箱格式，全部掩码
            return "*".repeat(value.length());
        }
        String username = value.substring(0, atIndex);
        String domain = value.substring(atIndex); // 包含 @ 符号
        if (username.length() <= 1) {
            // 用户名只有一个字符，保留原样（无需掩码）
            return username + domain;
        }
        return username.charAt(0) + "*".repeat(username.length() - 1) + domain;
    }

    /**
     * 地址脱敏：保留前6字符，其余以星号填充
     * <p>示例：北京市朝阳区建国路88号 → 北京市朝阳区*****</p>
     */
    private static String maskAddress(String value) {
        if (value.length() < DesensitizeType.ADDRESS.getMinLen()) {
            return "*".repeat(value.length());
        }
        if (value.length() == DesensitizeType.ADDRESS.getMinLen()) {
            // 长度恰好等于6，前缀占满全部字符，无可掩码部分
            return value;
        }
        return value.substring(0, 6) + "*".repeat(value.length() - 6);
    }
}
