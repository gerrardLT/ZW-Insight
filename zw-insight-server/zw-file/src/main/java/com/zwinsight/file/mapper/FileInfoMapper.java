package com.zwinsight.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.file.domain.FileInfo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FileInfoMapper extends BaseMapper<FileInfo> {
}
