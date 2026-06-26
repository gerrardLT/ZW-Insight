package com.zwinsight.file.service;

import com.zwinsight.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * PDF 转换服务
 *
 * <p>通过外部 wkhtmltopdf 进程将渲染后的 HTML 转换为 PDF：
 * HTML 写入临时文件 → 调用 wkhtmltopdf 转换 → 读取生成的 PDF 字节数组。</p>
 *
 * <p>配置项：</p>
 * <ul>
 *     <li>{@code print.wkhtmltopdf.path} —— wkhtmltopdf 可执行文件路径，默认 {@code /usr/local/bin/wkhtmltopdf}</li>
 *     <li>{@code print.wkhtmltopdf.timeout-seconds} —— 进程执行超时时间（秒），默认 60</li>
 * </ul>
 */
@Slf4j
@Service
public class PdfConvertService {

    /**
     * wkhtmltopdf 可执行文件路径
     */
    @Value("${print.wkhtmltopdf.path:/usr/local/bin/wkhtmltopdf}")
    private String wkhtmltopdfPath;

    /**
     * 进程执行超时时间（秒）
     */
    @Value("${print.wkhtmltopdf.timeout-seconds:60}")
    private int timeoutSeconds;

    /**
     * 将 HTML 转换为 PDF
     *
     * @param html 渲染后的完整 HTML 字符串
     * @return PDF 字节数组
     */
    public byte[] convertHtmlToPdf(String html) {
        if (html == null || html.isEmpty()) {
            throw new BusinessException("HTML 内容为空，无法转换 PDF");
        }

        Path tempHtml = null;
        Path tempPdf = null;
        try {
            // 1. HTML 写入临时文件
            tempHtml = Files.createTempFile("zw-print-", ".html");
            tempPdf = Files.createTempFile("zw-print-", ".pdf");
            Files.write(tempHtml, html.getBytes(StandardCharsets.UTF_8));

            // 2. 构建并启动 wkhtmltopdf 进程
            ProcessBuilder processBuilder = new ProcessBuilder(
                    wkhtmltopdfPath,
                    "--encoding", "utf-8",
                    tempHtml.toAbsolutePath().toString(),
                    tempPdf.toAbsolutePath().toString());
            // 合并错误流到标准输出，便于失败时读取诊断信息
            processBuilder.redirectErrorStream(true);

            Process process;
            try {
                process = processBuilder.start();
            } catch (IOException e) {
                throw new BusinessException(500, "PDF转换失败: 无法启动 wkhtmltopdf，请检查路径配置 " + wkhtmltopdfPath, e);
            }

            // 3. 等待进程结束（带超时）
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new BusinessException("PDF转换超时");
            }

            // 4. 校验退出码
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                String output = readProcessOutput(process);
                throw new BusinessException("PDF转换失败: " + output);
            }

            // 5. 读取生成的 PDF 字节数组
            byte[] pdfBytes = Files.readAllBytes(tempPdf);
            if (pdfBytes.length == 0) {
                throw new BusinessException("PDF转换失败: 生成的 PDF 文件为空");
            }
            return pdfBytes;
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.error("PDF转换发生IO异常", e);
            throw new BusinessException(500, "PDF转换失败: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("PDF转换被中断");
        } finally {
            deleteQuietly(tempHtml);
            deleteQuietly(tempPdf);
        }
    }

    /**
     * 读取进程输出（标准输出已合并错误流）
     */
    private String readProcessOutput(Process process) {
        try (var inputStream = process.getInputStream()) {
            byte[] bytes = inputStream.readAllBytes();
            String output = new String(bytes, StandardCharsets.UTF_8).trim();
            return output.isEmpty() ? "未知错误" : output;
        } catch (IOException e) {
            log.warn("读取 wkhtmltopdf 进程输出失败", e);
            return "未知错误";
        }
    }

    /**
     * 静默删除临时文件
     */
    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("删除临时文件失败: {}", path, e);
        }
    }
}
