package com.zwinsight.site.dto;

import java.util.List;

/**
 * 检查方案快照 DTO
 * 用于序列化为 JSON 存储到 biz_inspection.scheme_snapshot 字段
 */
public class SchemeSnapshotDTO {

    private Long schemeId;
    private String schemeName;
    private List<ItemDTO> items;

    public Long getSchemeId() { return schemeId; }
    public void setSchemeId(Long schemeId) { this.schemeId = schemeId; }
    public String getSchemeName() { return schemeName; }
    public void setSchemeName(String schemeName) { this.schemeName = schemeName; }
    public List<ItemDTO> getItems() { return items; }
    public void setItems(List<ItemDTO> items) { this.items = items; }

    /**
     * 检查项 DTO
     */
    public static class ItemDTO {
        private String itemName;
        private String checkStandard;
        private String checkMethod;

        public String getItemName() { return itemName; }
        public void setItemName(String itemName) { this.itemName = itemName; }
        public String getCheckStandard() { return checkStandard; }
        public void setCheckStandard(String checkStandard) { this.checkStandard = checkStandard; }
        public String getCheckMethod() { return checkMethod; }
        public void setCheckMethod(String checkMethod) { this.checkMethod = checkMethod; }
    }
}
