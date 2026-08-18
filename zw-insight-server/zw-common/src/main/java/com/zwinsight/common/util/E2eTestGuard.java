package com.zwinsight.common.util;

import java.lang.reflect.Field;

/**
 * E2E 测试数据识别守卫。
 *
 * <p>自动化测试套件（e2e/api-tests、e2e/tests/real）创建的单据统一携带
 * {@code E2E_TEST_{时间戳}} 前缀（契约见 zw-insight-web/e2e/api-tests/test-data.ts）。
 * 写路径测试会把单据推进到非草稿状态，而业务删除守卫「仅草稿状态可删除」会拦截
 * 测试清理导致残留累积——本守卫让状态级删除守卫对带标记的测试数据放行。</p>
 *
 * <p>边界约束：仅用于状态级守卫放行判定；引用完整性守卫（ReferenceCheck）不受影响，
 * 测试数据仍须按子→父逆序清理。</p>
 */
public final class E2eTestGuard {

    /** 测试数据统一前缀（与前端 e2e 套件 PREFIX 契约一致） */
    public static final String E2E_TEST_PREFIX = "E2E_TEST_";

    private E2eTestGuard() {
    }

    /**
     * 判定单个文本值是否为 E2E 测试数据标识（非空且以 {@link #E2E_TEST_PREFIX} 开头）。
     */
    public static boolean isE2eTestData(String value) {
        return value != null && value.startsWith(E2E_TEST_PREFIX);
    }

    /**
     * 扫描实体全部 String 字段（含父类继承字段），任一字段值携带
     * {@link #E2E_TEST_PREFIX} 前缀即判定为 E2E 测试数据。
     *
     * <p>各业务实体命名字段不统一（projectName/contractName/title/transferCode…），
     * 统一反射扫描避免逐实体核对名称字段，且新增实体自动生效。</p>
     *
     * @param entity 业务实体（可为 null）
     * @return true 表示该记录为自动化测试创建的数据
     */
    public static boolean containsE2eTestMarker(Object entity) {
        if (entity == null) {
            return false;
        }
        Class<?> clazz = entity.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.getType() != String.class) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(entity);
                    if (value instanceof String s && s.startsWith(E2E_TEST_PREFIX)) {
                        return true;
                    }
                } catch (IllegalAccessException ignored) {
                    // 字段不可访问时跳过，不影响判定结论
                }
            }
            clazz = clazz.getSuperclass();
        }
        return false;
    }

    /**
     * 主表实体 + 明细行联合判定：任一对象携带 {@link #E2E_TEST_PREFIX} 前缀即判定。
     *
     * <p>适用于主表无可控命名字段的实体（如材料入库主表仅自动编号 inboundCode、
     * 预算主表 projectName 不落库）——测试前缀落在明细行（materialName/itemName），
     * 删除守卫须连同明细一起扫描。</p>
     *
     * @param entity 主表实体（可为 null）
     * @param details 明细行集合（可为 null/空）
     * @return true 表示该记录为自动化测试创建的数据
     */
    public static boolean containsE2eTestMarker(Object entity, java.util.Collection<?> details) {
        if (containsE2eTestMarker(entity)) {
            return true;
        }
        if (details != null) {
            for (Object detail : details) {
                if (containsE2eTestMarker(detail)) {
                    return true;
                }
            }
        }
        return false;
    }
}
