package com.zwinsight.archive.domain;

import org.apache.ibatis.type.Alias;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 鍔炲叕鐢ㄥ搧搴撳瓨
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Alias("ArchiveBizOfficeSupply")
@TableName("biz_office_supply")
public class BizOfficeSupply extends BaseEntity {

    /** 鐢ㄥ搧鍚嶇О */
    private String supplyName;

    /** 瑙勬牸鍨嬪彿 */
    private String specification;

    /** 鍗曚綅 */
    private String unit;

    /** 褰撳墠搴撳瓨鏁伴噺 */
    private BigDecimal currentStock;

    /** 绱鍏ュ簱閲?*/
    private BigDecimal totalInbound;

    /** 绱棰嗙敤閲?*/
    private BigDecimal totalIssued;

    /** 鏈€杩戝叆搴撴棩鏈?*/
    private LocalDate lastInboundDate;
}
