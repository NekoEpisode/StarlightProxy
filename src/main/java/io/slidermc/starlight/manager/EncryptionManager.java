package io.slidermc.starlight.manager;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.security.*;

public class EncryptionManager {
    private final KeyPair keyPair;
    private final SecureRandom secureRandom = new SecureRandom();

    public EncryptionManager() throws NoSuchAlgorithmException {
        this.keyPair = generateKeyPair();
    }

    private KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(1024, new SecureRandom());
        return keyPairGenerator.generateKeyPair();
    }

    public KeyPair getKeyPair() {
        return keyPair;
    }

    /** 获取 DER 编码的公钥字节，直接写入 EncryptionRequest */
    public byte[] getPublicKeyBytes() {
        return keyPair.getPublic().getEncoded();
    }

    /** 生成 4 字节随机 verifyToken */
    public byte[] generateVerifyToken() {
        byte[] token = new byte[4];
        secureRandom.nextBytes(token);
        return token;
    }

    /**
     * 用 RSA 私钥解密客户端发来的数据（sharedSecret 或 verifyToken）
     */
    public byte[] decryptRSA(byte[] encryptedData) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate());
        return cipher.doFinal(encryptedData);
    }

    /**
     * 计算 Mojang 要求的 serverId hash：
     * SHA-1(serverId + sharedSecret + publicKey)，结果是有符号十六进制字符串
     */
    public String computeServerIdHash(String serverId, byte[] sharedSecret) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        digest.update(serverId.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        digest.update(sharedSecret);
        digest.update(getPublicKeyBytes());
        // Minecraft 用有符号大整数十六进制（无前导零，负数带 '-'）
        return new BigInteger(digest.digest()).toString(16);
    }

    /**
     * 用 sharedSecret 创建 AES/CFB8 解密 Cipher（上游 pipeline 的 EncryptionDecoder 用）
     */
    public Cipher createDecryptCipher(byte[] sharedSecret) throws GeneralSecurityException {
        return createAesCipher(Cipher.DECRYPT_MODE, sharedSecret);
    }

    /**
     * 用 sharedSecret 创建 AES/CFB8 加密 Cipher（上游 pipeline 的 EncryptionEncoder 用）
     */
    public Cipher createEncryptCipher(byte[] sharedSecret) throws GeneralSecurityException {
        return createAesCipher(Cipher.ENCRYPT_MODE, sharedSecret);
    }

    private Cipher createAesCipher(int mode, byte[] sharedSecret) throws GeneralSecurityException {
        SecretKey secretKey = new SecretKeySpec(sharedSecret, "AES");
        // IV 与 sharedSecret 相同，这是 Minecraft 协议的规定
        IvParameterSpec iv = new IvParameterSpec(sharedSecret);
        Cipher cipher = Cipher.getInstance("AES/CFB8/NoPadding");
        cipher.init(mode, secretKey, iv);
        return cipher;
    }
}
