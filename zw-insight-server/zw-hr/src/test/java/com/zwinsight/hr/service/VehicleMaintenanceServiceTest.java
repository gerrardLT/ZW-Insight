package com.zwinsight.hr.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.hr.domain.BizVehicleMaintenance;
import com.zwinsight.hr.mapper.BizVehicleMaintenanceMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * VehicleMaintenanceService（车辆维保记录）单元测试
 *
 * 覆盖场景:
 * - 新增维保记录
 * - 分页查询（按车辆筛选）
 */
@ExtendWith(MockitoExtension.class)
class VehicleMaintenanceServiceTest {

    @Mock
    private BizVehicleMaintenanceMapper maintenanceMapper;

    @InjectMocks
    private VehicleMaintenanceService vehicleMaintenanceService;

    @BeforeAll
    static void initTableInfo() {
        // 纯单元测试环境无 MyBatis 容器，需预初始化 Lambda 列缓存
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizVehicleMaintenance.class);
    }

    @Test
    @DisplayName("新增维保记录：透传 insert")
    void save_delegatesToMapper() {
        BizVehicleMaintenance maintenance = new BizVehicleMaintenance();
        maintenance.setVehicleId(10L);

        vehicleMaintenanceService.save(maintenance);

        verify(maintenanceMapper).insert(maintenance);
    }

    @Test
    @DisplayName("分页查询：返回 PageResult")
    void page_returnsPageResult() {
        Page<BizVehicleMaintenance> stubPage = new Page<>(1, 10);
        stubPage.setTotal(3);
        when(maintenanceMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(stubPage);

        PageResult<BizVehicleMaintenance> result = vehicleMaintenanceService.page(1, 10, 10L);

        assertThat(result.getTotal()).isEqualTo(3);
    }

    @Test
    @DisplayName("分页查询：车辆ID为空时不加筛选条件")
    void page_nullVehicleId_returnsPageResult() {
        Page<BizVehicleMaintenance> stubPage = new Page<>(1, 10);
        stubPage.setTotal(0);
        when(maintenanceMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(stubPage);

        PageResult<BizVehicleMaintenance> result = vehicleMaintenanceService.page(1, 10, null);

        assertThat(result.getTotal()).isZero();
    }
}
