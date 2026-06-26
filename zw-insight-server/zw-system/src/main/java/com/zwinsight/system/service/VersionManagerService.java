package com.zwinsight.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.system.domain.SysVersion;
import com.zwinsight.system.mapper.SysVersionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 系统版本管理服务。
 *
 * <p>提供语义化版本号(x.y.z)校验、版本号唯一性校验、版本记录创建，
 * 以及按发布日期降序的版本列表与当前最新版本查询能力。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VersionManagerService {

    private final SysVersionMapper versionMapper;

    /** 语义化版本号正则: x.y.z */
    private static final Pattern SEMVER_PATTERN = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");

    /**
     * 创建版本记录。
     *
     * <p>校验版本号语义化格式与唯一性，操作人取自安全上下文。
     *
     * @param versionNo   版本号(语义化: x.y.z)
     * @param releaseDate 发布日期
     * @param changelog   更新日志(Markdown)
     * @return 创建后的版本记录
     */
    public SysVersion create(String versionNo, LocalDate releaseDate, String changelog) {
        // 1. 语义化格式校验
        if (!StringUtils.hasText(versionNo) || !SEMVER_PATTERN.matcher(versionNo).matches()) {
            throw new BusinessException(400, "版本号格式无效，需符合x.y.z");
        }
        if (releaseDate == null) {
            throw new BusinessException(400, "发布日期不能为空");
        }

        // 2. 唯一性校验
        LambdaQueryWrapper<SysVersion> existsWrapper = new LambdaQueryWrapper<>();
        existsWrapper.eq(SysVersion::getVersionNo, versionNo);
        if (versionMapper.selectCount(existsWrapper) > 0) {
            throw new BusinessException(409, "版本号已存在");
        }

        // 3. 持久化
        SysVersion version = new SysVersion();
        version.setVersionNo(versionNo);
        version.setReleaseDate(releaseDate);
        version.setChangelog(changelog);
        version.setOperatorId(SecurityContextHolder.getUserId());
        version.setCreatedAt(LocalDateTime.now());
        versionMapper.insert(version);

        log.info("创建系统版本记录成功: versionNo={}, releaseDate={}", versionNo, releaseDate);
        return version;
    }

    /**
     * 查询全部版本记录，按发布日期降序。
     *
     * @return 版本记录列表
     */
    public List<SysVersion> listAll() {
        LambdaQueryWrapper<SysVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(SysVersion::getReleaseDate);
        return versionMapper.selectList(wrapper);
    }

    /**
     * 查询当前最新版本(按发布日期降序取第一条)。
     *
     * @return 最新版本记录，若无记录则返回 {@code null}
     */
    public SysVersion getCurrent() {
        LambdaQueryWrapper<SysVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(SysVersion::getReleaseDate).last("LIMIT 1");
        return versionMapper.selectOne(wrapper);
    }
}
