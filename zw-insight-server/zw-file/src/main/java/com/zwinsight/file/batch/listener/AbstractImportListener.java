package com.zwinsight.file.batch.listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.zwinsight.file.batch.domain.ImportResult;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用导入监听器基类
 * <p>
 * 子类只需实现：
 * 1. validate(T data) - 单行校验逻辑
 * 2. batchSave(List<T> dataList) - 批量持久化
 * </p>
 *
 * @param <T> EasyExcel DTO 类型
 */
@Slf4j
public abstract class AbstractImportListener<T> implements ReadListener<T> {

    /**
     * 批量处理阈值
     */
    private static final int BATCH_SIZE = 500;

    /**
     * 缓存的有效数据（校验通过）
     */
    private final List<T> validDataList = new ArrayList<>();

    /**
     * 导入结果
     */
    @Getter
    private final ImportResult importResult = new ImportResult();

    /**
     * 当前行号计数（EasyExcel 行号从0开始，表头不计）
     */
    private int currentRow = 0;

    @Override
    public void invoke(T data, AnalysisContext context) {
        currentRow++;
        importResult.setTotalRows(currentRow);

        // 执行数据校验
        String errorMsg = validate(data);
        if (errorMsg != null) {
            // 校验失败，记录错误（行号+1 为 Excel 中用户可见行号，含表头）
            importResult.addError(currentRow + 1, errorMsg);
            return;
        }

        // 校验通过，加入缓存
        validDataList.add(data);

        // 达到批次阈值则执行批量保存
        if (validDataList.size() >= BATCH_SIZE) {
            saveData();
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        // 处理剩余数据
        if (!validDataList.isEmpty()) {
            saveData();
        }
        log.info("导入完成：总行数={}, 成功={}, 失败={}",
                importResult.getTotalRows(), importResult.getSuccessRows(), importResult.getFailedRows());
    }

    @Override
    public void onException(Exception exception, AnalysisContext context) throws Exception {
        // 解析异常时记录错误并继续
        currentRow++;
        importResult.setTotalRows(currentRow);
        importResult.addError(currentRow + 1, "数据解析异常: " + exception.getMessage());
        log.warn("第{}行解析异常: {}", currentRow + 1, exception.getMessage());
    }

    /**
     * 数据校验 - 子类实现
     *
     * @param data 一行数据
     * @return null 表示校验通过，否则返回错误原因
     */
    protected abstract String validate(T data);

    /**
     * 批量保存 - 子类实现
     *
     * @param dataList 校验通过的数据列表
     */
    protected abstract void batchSave(List<T> dataList);

    /**
     * 执行保存并清空缓存
     */
    private void saveData() {
        try {
            batchSave(new ArrayList<>(validDataList));
            importResult.incrementSuccess(validDataList.size());
        } catch (Exception e) {
            log.error("批量保存失败: {}", e.getMessage(), e);
            // 如果批量保存整体失败，标记这批数据全部失败
            for (int i = 0; i < validDataList.size(); i++) {
                importResult.addError(currentRow - validDataList.size() + i + 2, "批量保存失败: " + e.getMessage());
            }
            // 回退成功计数（不增加）
        } finally {
            validDataList.clear();
        }
    }
}
