package com.zwinsight.hr.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.hr.domain.BizVehicle;
import com.zwinsight.hr.mapper.BizVehicleMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * VehicleService（车辆管理）单元测试
 *
 * 覆盖场景:
 * - 新增车辆默认状态 IDLE / 已指定状态不覆盖
 * - 更新时车辆不存在抛异常
 * - 分页与删除
 */
@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    @Mock
    private BizVehicleMapper vehicleMapper;

    @InjectMocks
    private VehicleService vehicleService;

    @BeforeAll
    static void initTableInfo() {
        // 纯单元测试环境无 MyBatis 容器，需预初始化 Lambda 列缓存
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizVehicle.class);
    }

    @Test
    @DisplayName("新增车辆：未指定状态默认 IDLE")
    void save_defaultsToIdle() {
        BizVehicle vehicle = new BizVehicle();
        vehicle.setPlateNumber("苏A12345");

        vehicleService.save(vehicle);

        assertThat(vehicle.getVehicleStatus()).isEqualTo("IDLE");
        verify(vehicleMapper).insert(vehicle);
    }

    @Test
    @DisplayName("新增车辆：已指定状态保持不变")
    void save_keepsExplicitStatus() {
        BizVehicle vehicle = new BizVehicle();
        vehicle.setVehicleStatus("MAINTENANCE");

        vehicleService.save(vehicle);

        assertThat(vehicle.getVehicleStatus()).isEqualTo("MAINTENANCE");
    }

    @Test
    @DisplayName("更新车辆：不存在抛异常")
    void update_notFound_throwsException() {
        BizVehicle vehicle = new BizVehicle();
        vehicle.setId(999L);
        when(vehicleMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> vehicleService.update(vehicle))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("车辆不存在");
    }

    @Test
    @DisplayName("更新车辆：存在则更新")
    void update_found_updates() {
        BizVehicle existing = new BizVehicle();
        existing.setId(1L);
        when(vehicleMapper.selectById(1L)).thenReturn(existing);
        BizVehicle vehicle = new BizVehicle();
        vehicle.setId(1L);

        vehicleService.update(vehicle);

        verify(vehicleMapper).updateById(vehicle);
    }

    @Test
    @DisplayName("删除车辆：透传 deleteById")
    void delete_delegatesToMapper() {
        vehicleService.delete(1L);

        verify(vehicleMapper).deleteById(1L);
    }

    @Test
    @DisplayName("分页查询：返回 PageResult")
    void page_returnsPageResult() {
        Page<BizVehicle> stubPage = new Page<>(1, 10);
        stubPage.setTotal(5);
        when(vehicleMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(stubPage);

        PageResult<BizVehicle> result = vehicleService.page(1, 10, "苏A", "轿车");

        assertThat(result.getTotal()).isEqualTo(5);
    }
}
