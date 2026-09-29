package com.zwinsight.security.service;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.util.RedisUtils;
import com.zwinsight.security.dto.CaptchaVO;
import com.zwinsight.security.dto.SliderCaptchaVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CaptchaService {
    private final RedisUtils redisUtils;
    private final SmsService smsService;

    @Value("${auth.captcha-enabled:true}")
    private boolean captchaEnabled;

    // ============ 图形验证码常量 ============
    private static final String CAPTCHA_PREFIX = "captcha:";
    private static final long CAPTCHA_TTL_SECONDS = 300L;

    // ============ 短信验证码常量 ============
    private static final String SMS_KEY_PREFIX = "sms:";
    private static final String SMS_FREQ_PREFIX = "sms:freq:";
    private static final String SMS_DAILY_PREFIX = "sms:daily:";
    private static final long SMS_TTL_SECONDS = 300L;
    private static final long SMS_FREQ_TTL_SECONDS = 60L;
    private static final int SMS_DAILY_LIMIT = 10;

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    // ============ 图形验证码方法（原有，保持向后兼容） ============

    /**
     * 生成图形验证码（原有方法，保持向后兼容）
     *
     * @param key 外部传入的 key
     * @return Base64 编码的验证码图片
     */
    public String generateCaptcha(String key) {
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(120, 40, 4, 20);
        String code = captcha.getCode();
        redisUtils.set(CAPTCHA_PREFIX + key, code, CAPTCHA_TTL_SECONDS, TimeUnit.SECONDS);
        return captcha.getImageBase64Data();
    }

    /**
     * 校验验证码（原有方法，保持向后兼容）
     *
     * @param key  验证码 key
     * @param code 用户输入的验证码
     * @return 校验是否通过
     */
    public boolean validateCaptcha(String key, String code) {
        if (!captchaEnabled) {
            return true;
        }
        Object cached = redisUtils.get(CAPTCHA_PREFIX + key);
        redisUtils.delete(CAPTCHA_PREFIX + key);
        return cached != null && cached.toString().equalsIgnoreCase(code);
    }

    /**
     * 生成图形验证码（新方法）
     * UUID 自动生成，使用 Hutool LineCaptcha 生成 4 位字母数字混合验证码
     * 存入 Redis: key=captcha:{uuid}, value=code, TTL=300s
     *
     * @return CaptchaVO 包含 uuid 和 Base64 图片（带 data:image/png;base64, 前缀）
     */
    public CaptchaVO generateImageCaptcha() {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(130, 48, 4, 80);
        String code = captcha.getCode();

        // 存入 Redis，TTL 5 分钟
        redisUtils.set(CAPTCHA_PREFIX + uuid, code, CAPTCHA_TTL_SECONDS, TimeUnit.SECONDS);

        // 获取 Base64 图片并添加前缀
        String imageBase64 = "data:image/png;base64," + captcha.getImageBase64();

        return new CaptchaVO(uuid, imageBase64);
    }

    /**
     * 校验图形验证码（新方法）
     * 大小写不敏感比对，无论成功失败都删除 key（一次性使用）
     *
     * @param uuid      验证码唯一标识
     * @param inputCode 用户输入的验证码
     * @return 校验是否通过
     */
    public boolean verifyImageCaptcha(String uuid, String inputCode) {
        if (uuid == null || inputCode == null) {
            return false;
        }
        String redisKey = CAPTCHA_PREFIX + uuid;
        Object cached = redisUtils.get(redisKey);
        // 无论校验成功失败，都删除 key（一次性使用）
        redisUtils.delete(redisKey);

        if (cached == null) {
            return false;
        }
        return cached.toString().equalsIgnoreCase(inputCode);
    }

    // ============ 短信验证码方法 ============

    /**
     * 发送短信验证码
     * <p>
     * 1. 校验手机号格式
     * 2. 检查 60 秒频率限制
     * 3. 检查每日 10 次限额
     * 4. 生成 6 位数字验证码
     * 5. 存入 Redis 并设置频率限制
     * 6. 调用短信服务发送
     *
     * @param phone 手机号
     */
    public void sendSmsCode(String phone) {
        // 1. 手机号格式校验
        if (phone == null || !PHONE_PATTERN.matcher(phone).matches()) {
            throw new BusinessException("手机号格式无效");
        }

        // 2. 频率限制检查（60秒内仅1次）
        String freqKey = SMS_FREQ_PREFIX + phone;
        if (Boolean.TRUE.equals(redisUtils.hasKey(freqKey))) {
            Long ttl = redisUtils.getExpire(freqKey, TimeUnit.SECONDS);
            throw new BusinessException("发送过于频繁，请" + ttl + "秒后重试");
        }

        // 3. 日限额检查（每日不超过10次）
        String dailyKey = SMS_DAILY_PREFIX + phone;
        Object dailyCount = redisUtils.get(dailyKey);
        if (dailyCount != null) {
            int count = Integer.parseInt(dailyCount.toString());
            if (count >= SMS_DAILY_LIMIT) {
                throw new BusinessException("今日短信发送次数已达上限(10次)");
            }
        }

        // 4. 生成6位数字验证码
        String code = String.format("%06d", new Random().nextInt(1000000));

        // 5. 存入 Redis: key=sms:{phone}, value=code, TTL=300s
        String smsKey = SMS_KEY_PREFIX + phone;
        redisUtils.set(smsKey, code, SMS_TTL_SECONDS, TimeUnit.SECONDS);

        // 6. 设置频率限制: key=sms:freq:{phone}, value="1", TTL=60s
        redisUtils.set(freqKey, "1", SMS_FREQ_TTL_SECONDS, TimeUnit.SECONDS);

        // 7. 日计数递增 + 首次设置到当天结束的 EXPIRE
        Long currentCount = redisUtils.increment(dailyKey);
        if (currentCount != null && currentCount == 1L) {
            // 首次发送，设置到当天结束的过期时间
            long secondsUntilEndOfDay = getSecondsUntilEndOfDay();
            redisUtils.expire(dailyKey, secondsUntilEndOfDay, TimeUnit.SECONDS);
        }

        // 8. 调用短信发送服务
        smsService.sendVerificationCode(phone, code);
    }

    /**
     * 校验短信验证码
     * <p>
     * 从 Redis 获取验证码，删除 key（一次性使用），精确匹配比对
     *
     * @param phone     手机号
     * @param inputCode 用户输入的验证码
     * @return 校验是否通过
     */
    public boolean verifySmsCode(String phone, String inputCode) {        if (phone == null || inputCode == null) {
            return false;
        }
        String smsKey = SMS_KEY_PREFIX + phone;
        Object cached = redisUtils.get(smsKey);
        // 删除 key（一次性使用）
        redisUtils.delete(smsKey);

        if (cached == null) {
            return false;
        }
        // 精确匹配（数字验证码不忽略大小写）
        return cached.toString().equals(inputCode);
    }

    /**
     * 校验短信验证码（非消费式 / peek）
     * <p>
     * 仅读取并比对，不删除 Redis 中的验证码。用于"先校验后重置"的分步流程：
     * 校验码步骤使用本方法保证验证码仍可在重置密码步骤被最终消费，
     * 避免一次性删除导致后续重置无法再次校验。
     *
     * @param phone     手机号
     * @param inputCode 用户输入的验证码
     * @return 校验是否通过
     */
    public boolean peekSmsCode(String phone, String inputCode) {
        if (phone == null || inputCode == null) {
            return false;
        }
        Object cached = redisUtils.get(SMS_KEY_PREFIX + phone);
        if (cached == null) {
            return false;
        }
        return cached.toString().equals(inputCode);
    }

    // ============ 滑块验证码（现代化替代扭曲图形码） ============
    private static final String SLIDER_PREFIX = "slider:";
    private static final String SLIDER_TOKEN_PREFIX = "slider:token:";
    private static final long SLIDER_TTL_SECONDS = 120L;
    private static final int SLIDER_WIDTH = 320;
    private static final int SLIDER_HEIGHT = 160;
    private static final int SLIDER_PIECE_SIZE = 52;
    private static final int SLIDER_TOLERANCE_PX = 6;

    /** 生成真正的拼图挑战；仅服务端保存目标 x，响应绝不泄露 x 或比例。 */
    public SliderCaptchaVO generateSlider() {
        Random random = new Random();
        int targetX = 90 + random.nextInt(SLIDER_WIDTH - SLIDER_PIECE_SIZE - 110);
        int targetY = 25 + random.nextInt(SLIDER_HEIGHT - SLIDER_PIECE_SIZE - 45);
        BufferedImage source = createSliderBackground(random);
        Shape pieceShape = createPieceShape(targetX, targetY);
        BufferedImage piece = new BufferedImage(SLIDER_PIECE_SIZE, SLIDER_PIECE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = piece.createGraphics();
        configureGraphics(pg);
        pg.translate(-targetX, -targetY);
        pg.setClip(pieceShape);
        pg.drawImage(source, 0, 0, null);
        pg.dispose();

        BufferedImage background = new BufferedImage(SLIDER_WIDTH, SLIDER_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D bg = background.createGraphics();
        configureGraphics(bg);
        bg.drawImage(source, 0, 0, null);
        bg.setColor(new Color(20, 30, 45, 150));
        bg.fill(pieceShape);
        bg.setColor(new Color(255, 255, 255, 210));
        bg.setStroke(new BasicStroke(2f));
        bg.draw(pieceShape);
        bg.dispose();

        String id = UUID.randomUUID().toString().replace("-", "");
        redisUtils.set(SLIDER_PREFIX + id, String.valueOf(targetX), SLIDER_TTL_SECONDS, TimeUnit.SECONDS);
        return new SliderCaptchaVO(id, toPngDataUrl(background), toPngDataUrl(piece), targetY,
                SLIDER_WIDTH, SLIDER_HEIGHT, SLIDER_PIECE_SIZE);
    }

    /** pct 契约：滑块左边缘 x 占图片宽度的比例，取值 [0,1]。 */
    public String verifySlider(String challengeId, double pct) {
        if (!Double.isFinite(pct) || pct < 0 || pct > 1) return null;
        return verifySliderX(challengeId, pct * SLIDER_WIDTH);
    }

    /** x 契约：滑块左边缘在原图坐标系中的像素值。 */
    public String verifySliderX(String challengeId, double x) {
        if (challengeId == null || challengeId.isBlank() || !Double.isFinite(x)
                || x < 0 || x > SLIDER_WIDTH - SLIDER_PIECE_SIZE) return null;
        String key = SLIDER_PREFIX + challengeId;
        Object cached = redisUtils.get(key);
        redisUtils.delete(key);
        if (cached == null) return null;
        int targetX;
        try { targetX = Integer.parseInt(cached.toString()); } catch (NumberFormatException e) { return null; }
        if (Math.abs(x - targetX) > SLIDER_TOLERANCE_PX) return null;
        String token = UUID.randomUUID().toString().replace("-", "");
        redisUtils.set(SLIDER_TOKEN_PREFIX + token, "1", SLIDER_TTL_SECONDS, TimeUnit.SECONDS);
        return token;
    }

    private BufferedImage createSliderBackground(Random random) {
        BufferedImage image = new BufferedImage(SLIDER_WIDTH, SLIDER_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        configureGraphics(g);
        Color a = new Color(70 + random.nextInt(70), 120 + random.nextInt(70), 160 + random.nextInt(70));
        Color b = new Color(180 + random.nextInt(60), 150 + random.nextInt(70), 80 + random.nextInt(100));
        g.setPaint(new GradientPaint(0, 0, a, SLIDER_WIDTH, SLIDER_HEIGHT, b));
        g.fillRect(0, 0, SLIDER_WIDTH, SLIDER_HEIGHT);
        for (int i = 0; i < 18; i++) {
            g.setColor(new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256), 55 + random.nextInt(55)));
            int size = 15 + random.nextInt(65);
            g.fill(new Ellipse2D.Double(random.nextInt(SLIDER_WIDTH), random.nextInt(SLIDER_HEIGHT), size, size));
        }
        g.setColor(new Color(255, 255, 255, 100));
        g.setStroke(new BasicStroke(3f));
        for (int i = 0; i < 5; i++) {
            int y = 15 + random.nextInt(SLIDER_HEIGHT - 30);
            g.drawLine(0, y, SLIDER_WIDTH, Math.max(0, Math.min(SLIDER_HEIGHT, y + random.nextInt(61) - 30)));
        }
        g.dispose();
        return image;
    }

    private Shape createPieceShape(int x, int y) {
        int s = SLIDER_PIECE_SIZE;
        int tab = 8;
        Path2D path = new Path2D.Double();
        path.moveTo(x, y);
        path.lineTo(x + s / 2 - tab, y);
        path.curveTo(x + s / 2 - tab, y + tab * 2, x + s / 2 + tab, y + tab * 2, x + s / 2 + tab, y);
        path.lineTo(x + s, y);
        path.lineTo(x + s, y + s / 2 - tab);
        path.curveTo(x + s - tab * 2, y + s / 2 - tab, x + s - tab * 2, y + s / 2 + tab, x + s, y + s / 2 + tab);
        path.lineTo(x + s, y + s);
        path.lineTo(x, y + s);
        path.closePath();
        return path;
    }

    private void configureGraphics(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    private String toPngDataUrl(BufferedImage image) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("生成滑块验证码图片失败", e);
        }
    }

    /**
     * 登录时消费一次性 sliderToken（存在即有效，用后即删）。
     */
    public boolean consumeSliderToken(String token) {
        if (token == null || token.isBlank()) return false;
        boolean ok = Boolean.TRUE.equals(redisUtils.hasKey(SLIDER_TOKEN_PREFIX + token));
        if (ok) redisUtils.delete(SLIDER_TOKEN_PREFIX + token);
        return ok;
    }

    // ============ IP 锁定机制 ============

    private static final String IP_FAIL_PREFIX = "login:ip:fail:";
    private static final String IP_LOCK_PREFIX = "login:ip:lock:";
    private static final int IP_FAIL_WINDOW_SECONDS = 300;   // 5 分钟窗口
    private static final int IP_FAIL_MAX_ATTEMPTS = 5;       // 最多 5 次
    private static final int IP_LOCK_DURATION_SECONDS = 900; // 锁定 15 分钟

    /**
     * 检查 IP 是否被锁定
     *
     * @param clientIp 客户端 IP
     * @throws BusinessException 如果 IP 被锁定
     */
    public void checkIpLock(String clientIp) {
        if (clientIp == null) {
            return;
        }
        String lockKey = IP_LOCK_PREFIX + clientIp;
        if (Boolean.TRUE.equals(redisUtils.hasKey(lockKey))) {
            Long ttl = redisUtils.getExpire(lockKey, TimeUnit.SECONDS);
            throw new BusinessException("登录失败次数过多，请" + (ttl != null ? ttl / 60 + 1 : 15) + "分钟后重试");
        }
    }

    /**
     * 记录 IP 登录失败
     * 5 分钟窗口内连续失败 5 次后锁定 15 分钟
     *
     * @param clientIp 客户端 IP
     */
    public void recordIpFailure(String clientIp) {
        if (clientIp == null) {
            return;
        }
        String failKey = IP_FAIL_PREFIX + clientIp;
        Long count = redisUtils.increment(failKey);
        if (count != null && count == 1L) {
            redisUtils.expire(failKey, IP_FAIL_WINDOW_SECONDS, TimeUnit.SECONDS);
        }
        if (count != null && count >= IP_FAIL_MAX_ATTEMPTS) {
            // 达到最大失败次数，设置锁定
            String lockKey = IP_LOCK_PREFIX + clientIp;
            redisUtils.set(lockKey, "1", IP_LOCK_DURATION_SECONDS, TimeUnit.SECONDS);
            // 清除失败计数
            redisUtils.delete(failKey);
        }
    }

    /**
     * 清除 IP 登录失败记录（登录成功时调用）
     *
     * @param clientIp 客户端 IP
     */
    public void clearIpFailure(String clientIp) {
        if (clientIp == null) {
            return;
        }
        redisUtils.delete(IP_FAIL_PREFIX + clientIp);
    }

    // ============ 工具方法 ============

    public boolean isCaptchaEnabled() {
        return captchaEnabled;
    }

    /**
     * 计算当天剩余秒数，用于设置日计数器 TTL
     */
    private long getSecondsUntilEndOfDay() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);
        return ChronoUnit.SECONDS.between(now, endOfDay);
    }
}
