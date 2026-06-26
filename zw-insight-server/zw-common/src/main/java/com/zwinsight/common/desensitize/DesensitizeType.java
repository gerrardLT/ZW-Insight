package com.zwinsight.common.desensitize;

/**
 * 数据脱敏类型枚举
 * <p>
 * 定义各类敏感数据的掩码策略参数：前缀保留长度、后缀保留长度、最低掩码要求长度。
 * </p>
 */
public enum DesensitizeType {

    /** 手机号：保留前3后4，中间4个星号 */
    PHONE(3, 4, 7),

    /** 身份证号：保留前3后4，中间星号 */
    ID_CARD(3, 4, 7),

    /** 银行卡号：保留前4后4，中间星号 */
    BANK_CARD(4, 4, 8),

    /** 邮箱：保留首字母+@+域名 */
    EMAIL(0, 0, 3),

    /** 地址：保留前6字符，其余星号 */
    ADDRESS(6, 0, 6);

    /** 前缀保留长度 */
    private final int prefixLen;

    /** 后缀保留长度 */
    private final int suffixLen;

    /** 最低掩码要求长度 */
    private final int minLen;

    DesensitizeType(int prefixLen, int suffixLen, int minLen) {
        this.prefixLen = prefixLen;
        this.suffixLen = suffixLen;
        this.minLen = minLen;
    }

    public int getPrefixLen() {
        return prefixLen;
    }

    public int getSuffixLen() {
        return suffixLen;
    }

    public int getMinLen() {
        return minLen;
    }
}
