package com.zwinsight.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwinsight.message.domain.MsgMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MsgMessageMapper extends BaseMapper<MsgMessage> {
}
