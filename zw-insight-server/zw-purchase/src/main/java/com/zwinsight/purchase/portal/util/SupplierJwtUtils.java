package com.zwinsight.purchase.portal.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 供应商门户专用 JWT 工具
 * <p>
 * 使用独立的 secret 和 subject 前缀，与主系统 JWT 完全隔离。
 */
@Component
public class SupplierJwtUtils {

    @Value("${supplier.jwt.secret:ZwInsightSupplierPortal2024SecretKey!}")
    private String secret;

    @Value("${supplier.jwt.expiration:86400000}")
    private long expiration;

    private static final String SUBJECT_PREFIX = "supplier";

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] paddedKey = new byte[32];
            System.arraycopy(keyBytes, 0, paddedKey, 0, keyBytes.length);
            return Keys.hmacShaKeyFor(paddedKey);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成供应商 JWT
     */
    public String generateToken(Long supplierId, String phone, String supplierName) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("supplierId", supplierId);
        claims.put("phone", phone);
        claims.put("supplierName", supplierName);

        return Jwts.builder()
                .claims(claims)
                .subject(SUBJECT_PREFIX)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 解析 token
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 获取供应商ID
     */
    public Long getSupplierId(String token) {
        Claims claims = parseToken(token);
        return claims.get("supplierId", Long.class);
    }

    /**
     * 获取手机号
     */
    public String getPhone(String token) {
        Claims claims = parseToken(token);
        return claims.get("phone", String.class);
    }

    /**
     * 获取供应商名称
     */
    public String getSupplierName(String token) {
        Claims claims = parseToken(token);
        return claims.get("supplierName", String.class);
    }

    /**
     * 验证 token 是否有效（未过期、签名正确、subject 为 supplier）
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = parseToken(token);
            if (!SUBJECT_PREFIX.equals(claims.getSubject())) {
                return false;
            }
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    public long getExpiration() {
        return expiration;
    }
}
