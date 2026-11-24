package com.footballcard.applet;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.OwnerPIN;
import javacard.framework.Util;
import javacard.security.AESKey;
import javacard.security.KeyBuilder;
import javacard.security.KeyPair;
import javacard.security.RSAPrivateKey;
import javacard.security.RSAPublicKey;
import javacard.security.RandomData;
import javacard.security.Signature;
import javacardx.crypto.Cipher;

/**
 * Java Card Applet cho The Thanh Vien San Bong (chay tren jCardSim).
 *
 * Chuc nang chinh:
 * 1. Xac thuc PIN (toi da 3 lan sai -> tu dong khoa the), doi PIN, mo khoa bang PUK, khoa khan cap.
 * 2. Luu & ma hoa thong tin ca nhan bang AES-128-CBC (khoa sinh ngau nhien ngay tren the).
 * 3. Nap tien, thanh toan tien san/dich vu, tu dong tich diem theo hang thanh vien, doi qua.
 * 4. Luu lich su 5 giao dich gan nhat ngay trong bo nho EEPROM (Ring Buffer).
 * 5. Sinh cap khoa RSA-1024 tren the va ky so giao dich/challenge chong gia mao the.
 */
public class FootballMemberApplet extends Applet {

    // ===== BO NHO EEPROM =====
    private static final short MAX_CARD_ID_LEN    = 32;
    private static final short MAX_INFO_PLAIN_LEN = 208; // < 224 (boi so 16 cho AES)
    private static final short MAX_INFO_CIPHER_LEN = 224;
    private static final short AES_BLOCK_SIZE     = 16;

    // Ring buffer luu 5 giao dich gan nhat (moi record = 13 bytes: 1B type + 4B amount + 4B pointsDelta + 4B timestampSec)
    private static final byte MAX_TX_HISTORY = 5;
    private static final short TX_RECORD_SIZE = 13;

    private final OwnerPIN pin;
    private final OwnerPIN puk;
    private boolean cardLocked;

    // Card ID cong khai (de POS tra cuu booking & trang thai the tren Firebase)
    private final byte[] cardId;
    private short cardIdLen;

    // Thong tin ca nhan duoc ma hoa AES-128-CBC
    private final AESKey aesKey;
    private final byte[] aesIv;
    private final Cipher aesCipher;
    private final byte[] encryptedPersonalInfo;
    private short encryptedInfoLen;
    private short plainInfoLen;

    // Tai chinh & tich diem
    private int balance;        // VND
    private int loyaltyPoints;  // Diem tich luy
    private int totalSpent;     // Tong chi tieu -> tinh hang thanh vien

    // Lich su giao dich tren the
    private final byte[] txHistoryBuffer;
    private byte txCount;
    private byte txHeadIndex;

    // Cap khoa RSA-1024 ky so chong gia mao
    private final KeyPair rsaKeyPair;
    private final RSAPublicKey rsaPublicKey;
    private final RSAPrivateKey rsaPrivateKey;
    private final Signature rsaSignature;

    private FootballMemberApplet() {
        // 1. Khoi tao PIN & PUK
        pin = new OwnerPIN(CardConstants.PIN_TRY_LIMIT, CardConstants.PIN_MAX_SIZE);
        pin.update(CardConstants.DEFAULT_PIN, (short) 0, (byte) CardConstants.DEFAULT_PIN.length);

        puk = new OwnerPIN(CardConstants.PUK_TRY_LIMIT, CardConstants.PUK_MAX_SIZE);
        puk.update(CardConstants.DEFAULT_PUK, (short) 0, (byte) CardConstants.DEFAULT_PUK.length);

        cardLocked = false;

        // 2. Khoi tao Card ID
        cardId = new byte[MAX_CARD_ID_LEN];
        cardIdLen = 0;

        // 3. Sinh khoa AES-128 & IV ngau nhien ngay tren the
        RandomData random = RandomData.getInstance(RandomData.ALG_SECURE_RANDOM);
        byte[] rawKey = new byte[AES_BLOCK_SIZE];
        random.generateData(rawKey, (short) 0, AES_BLOCK_SIZE);

        aesKey = (AESKey) KeyBuilder.buildKey(KeyBuilder.TYPE_AES, KeyBuilder.LENGTH_AES_128, false);
        aesKey.setKey(rawKey, (short) 0);
        Util.arrayFillNonAtomic(rawKey, (short) 0, AES_BLOCK_SIZE, (byte) 0); // Xoa key tam

        aesIv = new byte[AES_BLOCK_SIZE];
        random.generateData(aesIv, (short) 0, AES_BLOCK_SIZE);

        aesCipher = Cipher.getInstance(Cipher.ALG_AES_BLOCK_128_CBC_NOPAD, false);
        encryptedPersonalInfo = new byte[MAX_INFO_CIPHER_LEN];
        encryptedInfoLen = 0;
        plainInfoLen = 0;

        // 4. Tai chinh & Lich su
        balance = 0;
        loyaltyPoints = 0;
        totalSpent = 0;
        txHistoryBuffer = new byte[(short) (MAX_TX_HISTORY * TX_RECORD_SIZE)];
        txCount = 0;
        txHeadIndex = 0;

        // 5. Sinh cap khoa RSA-1024 tren the
        rsaKeyPair = new KeyPair(KeyPair.ALG_RSA, KeyBuilder.LENGTH_RSA_1024);
        rsaKeyPair.genKeyPair();
        rsaPublicKey = (RSAPublicKey) rsaKeyPair.getPublic();
        rsaPrivateKey = (RSAPrivateKey) rsaKeyPair.getPrivate();
        rsaSignature = Signature.getInstance(Signature.ALG_RSA_SHA_PKCS1, false);

        register();
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new FootballMemberApplet();
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }

        byte[] buf = apdu.getBuffer();
        if (buf[ISO7816.OFFSET_CLA] != CardConstants.CLA_PROPRIETARY) {
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
        }

        byte ins = buf[ISO7816.OFFSET_INS];
        switch (ins) {
            // --- Xac thuc & Bao mat ---
            case CardConstants.INS_VERIFY_PIN:
                verifyPin(apdu);
                break;
            case CardConstants.INS_CHANGE_PIN:
                changePin(apdu);
                break;
            case CardConstants.INS_UNBLOCK_PIN:
                unblockPin(apdu);
                break;
            case CardConstants.INS_LOCK_CARD:
                lockCard();
                break;

            // --- Thong tin ca nhan (AES) ---
            case CardConstants.INS_SET_CARD_ID:
                setCardId(apdu);
                break;
            case CardConstants.INS_GET_CARD_ID:
                getCardId(apdu);
                break;
            case CardConstants.INS_SET_PERSONAL_INFO:
                setPersonalInfo(apdu);
                break;
            case CardConstants.INS_GET_PERSONAL_INFO:
                getPersonalInfo(apdu);
                break;
            case CardConstants.INS_GET_ENCRYPTED_DUMP:
                getEncryptedDump(apdu);
                break;

            // --- Tai chinh, Tich diem & Doi qua ---
            case CardConstants.INS_TOP_UP:
                topUp(apdu);
                break;
            case CardConstants.INS_PAY:
                pay(apdu);
                break;
            case CardConstants.INS_REDEEM_REWARD:
                redeemReward(apdu);
                break;
            case CardConstants.INS_GET_SUMMARY:
                getSummary(apdu);
                break;
            case CardConstants.INS_GET_TX_HISTORY:
                getTxHistory(apdu);
                break;

            // --- Chu ky so RSA ---
            case CardConstants.INS_GET_PUBLIC_KEY:
                getPublicKey(apdu);
                break;
            case CardConstants.INS_SIGN_CHALLENGE:
                signChallenge(apdu);
                break;

            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    // =========================================================================
    // 1. XAC THUC PIN / PUK & KHOA THE
    // =========================================================================

    private void checkNotLocked() {
        if (cardLocked || pin.getTriesRemaining() == 0) {
            cardLocked = true;
            ISOException.throwIt(CardConstants.SW_CARD_LOCKED);
        }
    }

    private void checkPinValidated() {
        checkNotLocked();
        if (!pin.isValidated()) {
            ISOException.throwIt(CardConstants.SW_PIN_VERIFICATION_REQUIRED);
        }
    }

    private void verifyPin(APDU apdu) {
        checkNotLocked();
        byte[] buf = apdu.getBuffer();
        short len = apdu.setIncomingAndReceive();
        if (len < 4 || len > CardConstants.PIN_MAX_SIZE) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        if (!pin.check(buf, ISO7816.OFFSET_CDATA, (byte) len)) {
            byte tries = pin.getTriesRemaining();
            if (tries == 0) {
                cardLocked = true;
                ISOException.throwIt(CardConstants.SW_CARD_LOCKED);
            }
            // Tra ve 0x63C<tries> theo chuan ISO 7816-4
            ISOException.throwIt((short) (0x63C0 | (tries & 0x0F)));
        }
    }

    private void changePin(APDU apdu) {
        checkPinValidated();
        byte[] buf = apdu.getBuffer();
        short len = apdu.setIncomingAndReceive();
        if (len < 4 || len > CardConstants.PIN_MAX_SIZE) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        pin.update(buf, ISO7816.OFFSET_CDATA, (byte) len);
        pin.resetAndUnblock();
    }

    /**
     * Data format: [1 byte pukLen][pukBytes...][1 byte newPinLen][newPinBytes...]
     */
    private void unblockPin(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        short len = apdu.setIncomingAndReceive();
        if (len < 6) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        short offset = ISO7816.OFFSET_CDATA;
        byte pukLen = buf[offset++];
        if (pukLen <= 0 || pukLen > CardConstants.PUK_MAX_SIZE || (short) (1 + pukLen + 1) > len) {
            ISOException.throwIt(ISO7816.SW_WRONG_DATA);
        }
        if (!puk.check(buf, offset, pukLen)) {
            ISOException.throwIt(CardConstants.SW_WRONG_PUK);
        }
        offset += pukLen;
        byte newPinLen = buf[offset++];
        if (newPinLen < 4 || newPinLen > CardConstants.PIN_MAX_SIZE || (short) (offset - ISO7816.OFFSET_CDATA + newPinLen) != len) {
            ISOException.throwIt(ISO7816.SW_WRONG_DATA);
        }
        pin.update(buf, offset, newPinLen);
        pin.resetAndUnblock();
        cardLocked = false;
    }

    private void lockCard() {
        cardLocked = true;
        pin.reset();
    }

    // =========================================================================
    // 2. CARD ID & THONG TIN CA NHAN MA HOA AES-128-CBC
    // =========================================================================

    private void setCardId(APDU apdu) {
        checkPinValidated();
        byte[] buf = apdu.getBuffer();
        short len = apdu.setIncomingAndReceive();
        if (len <= 0 || len > MAX_CARD_ID_LEN) {
            ISOException.throwIt(CardConstants.SW_DATA_TOO_LARGE);
        }
        Util.arrayCopyNonAtomic(buf, ISO7816.OFFSET_CDATA, cardId, (short) 0, len);
        cardIdLen = len;
    }

    private void getCardId(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        Util.arrayCopyNonAtomic(cardId, (short) 0, buf, (short) 0, cardIdLen);
        apdu.setOutgoingAndSend((short) 0, cardIdLen);
    }

    private void setPersonalInfo(APDU apdu) {
        checkPinValidated();
        byte[] buf = apdu.getBuffer();
        short len = apdu.setIncomingAndReceive();
        if (len <= 0 || len > MAX_INFO_PLAIN_LEN) {
            ISOException.throwIt(CardConstants.SW_DATA_TOO_LARGE);
        }

        // Pad thanh boi so 16 bytes (zero padding, luu plainInfoLen rieng)
        short paddedLen = (short) (((len + AES_BLOCK_SIZE - 1) / AES_BLOCK_SIZE) * AES_BLOCK_SIZE);
        byte[] padded = new byte[paddedLen];
        Util.arrayCopyNonAtomic(buf, ISO7816.OFFSET_CDATA, padded, (short) 0, len);

        aesCipher.init(aesKey, Cipher.MODE_ENCRYPT, aesIv, (short) 0, AES_BLOCK_SIZE);
        encryptedInfoLen = aesCipher.doFinal(padded, (short) 0, paddedLen, encryptedPersonalInfo, (short) 0);
        plainInfoLen = len;

        // Xoa plaintext tam trong RAM
        Util.arrayFillNonAtomic(padded, (short) 0, paddedLen, (byte) 0);
    }

    private void getPersonalInfo(APDU apdu) {
        checkPinValidated();
        if (encryptedInfoLen == 0) {
            apdu.setOutgoingAndSend((short) 0, (short) 0);
            return;
        }
        byte[] buf = apdu.getBuffer();
        aesCipher.init(aesKey, Cipher.MODE_DECRYPT, aesIv, (short) 0, AES_BLOCK_SIZE);
        aesCipher.doFinal(encryptedPersonalInfo, (short) 0, encryptedInfoLen, buf, (short) 0);
        apdu.setOutgoingAndSend((short) 0, plainInfoLen);
    }

    private void getEncryptedDump(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        Util.arrayCopyNonAtomic(encryptedPersonalInfo, (short) 0, buf, (short) 0, encryptedInfoLen);
        apdu.setOutgoingAndSend((short) 0, encryptedInfoLen);
    }

    // =========================================================================
    // 3. NAP TIEN, THANH TOAN, TICH DIEM, DOI QUA & LICH SU TREN THE
    // =========================================================================

    /**
     * Data: [4B amount VND][4B timestampSec]
     */
    private void topUp(APDU apdu) {
        checkPinValidated();
        byte[] buf = apdu.getBuffer();
        short len = apdu.setIncomingAndReceive();
        if (len != 8) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        int amount = getInt(buf, ISO7816.OFFSET_CDATA);
        int timestamp = getInt(buf, (short) (ISO7816.OFFSET_CDATA + 4));
        if (amount <= 0) {
            ISOException.throwIt(CardConstants.SW_INVALID_AMOUNT);
        }
        balance += amount;
        appendTxHistory(CardConstants.TX_TOP_UP, amount, 0, timestamp);
    }

    /**
     * P1 = txType (TX_PAY_BOOKING hoac TX_PAY_SERVICE)
     * Data: [4B amount VND][4B timestampSec]
     * Response: [4B earnedPoints][4B newBalance]
     */
    private void pay(APDU apdu) {
        checkPinValidated();
        byte[] buf = apdu.getBuffer();
        byte txType = buf[ISO7816.OFFSET_P1];
        if (txType != CardConstants.TX_PAY_BOOKING && txType != CardConstants.TX_PAY_SERVICE) {
            txType = CardConstants.TX_PAY_SERVICE;
        }
        short len = apdu.setIncomingAndReceive();
        if (len != 8) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        int amount = getInt(buf, ISO7816.OFFSET_CDATA);
        int timestamp = getInt(buf, (short) (ISO7816.OFFSET_CDATA + 4));
        if (amount <= 0) {
            ISOException.throwIt(CardConstants.SW_INVALID_AMOUNT);
        }
        if (balance < amount) {
            ISOException.throwIt(CardConstants.SW_INSUFFICIENT_BALANCE);
        }

        balance -= amount;
        totalSpent += amount;

        // Tinh diem tich luy: 1 diem / 10.000 VND, nhan he so theo hang thanh vien
        int basePoints = amount / 10000;
        byte tier = computeTier();
        int earnedPoints;
        if (tier == CardConstants.TIER_VIP) {
            earnedPoints = basePoints * 2;
        } else if (tier == CardConstants.TIER_GOLD) {
            earnedPoints = (basePoints * 3) / 2;
        } else if (tier == CardConstants.TIER_SILVER) {
            earnedPoints = (basePoints * 6) / 5;
        } else {
            earnedPoints = basePoints;
        }
        loyaltyPoints += earnedPoints;

        appendTxHistory(txType, amount, earnedPoints, timestamp);

        // Tra ve earnedPoints + newBalance
        setInt(buf, (short) 0, earnedPoints);
        setInt(buf, (short) 4, balance);
        apdu.setOutgoingAndSend((short) 0, (short) 8);
    }

    /**
     * Data: [4B pointsCost][4B timestampSec]
     * Response: [4B remainingPoints]
     */
    private void redeemReward(APDU apdu) {
        checkPinValidated();
        byte[] buf = apdu.getBuffer();
        short len = apdu.setIncomingAndReceive();
        if (len != 8) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        int pointsCost = getInt(buf, ISO7816.OFFSET_CDATA);
        int timestamp = getInt(buf, (short) (ISO7816.OFFSET_CDATA + 4));
        if (pointsCost <= 0) {
            ISOException.throwIt(CardConstants.SW_INVALID_AMOUNT);
        }
        if (loyaltyPoints < pointsCost) {
            ISOException.throwIt(CardConstants.SW_INSUFFICIENT_POINTS);
        }

        loyaltyPoints -= pointsCost;
        appendTxHistory(CardConstants.TX_REDEEM_GIFT, 0, -pointsCost, timestamp);

        setInt(buf, (short) 0, loyaltyPoints);
        apdu.setOutgoingAndSend((short) 0, (short) 4);
    }

    /**
     * Response (15 bytes):
     * [0..3]  balance (int)
     * [4..7]  loyaltyPoints (int)
     * [8..11] totalSpent (int)
     * [12]    tier (0..3)
     * [13]    cardLocked (0 or 1)
     * [14]    pinTriesRemaining
     */
    private void getSummary(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        setInt(buf, (short) 0, balance);
        setInt(buf, (short) 4, loyaltyPoints);
        setInt(buf, (short) 8, totalSpent);
        buf[12] = computeTier();
        buf[13] = (byte) (cardLocked ? 1 : 0);
        buf[14] = pin.getTriesRemaining();
        apdu.setOutgoingAndSend((short) 0, (short) 15);
    }

    private void getTxHistory(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        buf[0] = txCount;
        short outOffset = 1;
        // Tra ve theo thu tu moi nhat -> cu nhat
        for (byte i = 0; i < txCount; i++) {
            short idx = (short) ((txHeadIndex - 1 - i + MAX_TX_HISTORY) % MAX_TX_HISTORY);
            short srcOffset = (short) (idx * TX_RECORD_SIZE);
            Util.arrayCopyNonAtomic(txHistoryBuffer, srcOffset, buf, outOffset, TX_RECORD_SIZE);
            outOffset += TX_RECORD_SIZE;
        }
        apdu.setOutgoingAndSend((short) 0, outOffset);
    }

    private void appendTxHistory(byte type, int amount, int pointsDelta, int timestamp) {
        short dst = (short) (txHeadIndex * TX_RECORD_SIZE);
        txHistoryBuffer[dst] = type;
        setInt(txHistoryBuffer, (short) (dst + 1), amount);
        setInt(txHistoryBuffer, (short) (dst + 5), pointsDelta);
        setInt(txHistoryBuffer, (short) (dst + 9), timestamp);

        txHeadIndex = (byte) ((txHeadIndex + 1) % MAX_TX_HISTORY);
        if (txCount < MAX_TX_HISTORY) {
            txCount++;
        }
    }

    private byte computeTier() {
        if (totalSpent >= 10_000_000) {
            return CardConstants.TIER_VIP;
        } else if (totalSpent >= 5_000_000) {
            return CardConstants.TIER_GOLD;
        } else if (totalSpent >= 2_000_000) {
            return CardConstants.TIER_SILVER;
        }
        return CardConstants.TIER_BRONZE;
    }

    // =========================================================================
    // 4. CHU KY SO RSA CHONG GIA MAO THE
    // =========================================================================

    private void getPublicKey(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        byte p1 = buf[ISO7816.OFFSET_P1];
        short len;
        if (p1 == 0x00) {
            len = rsaPublicKey.getModulus(buf, (short) 0);
        } else if (p1 == 0x01) {
            len = rsaPublicKey.getExponent(buf, (short) 0);
        } else {
            ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
            return;
        }
        apdu.setOutgoingAndSend((short) 0, len);
    }

    private void signChallenge(APDU apdu) {
        checkNotLocked();
        byte[] buf = apdu.getBuffer();
        short len = apdu.setIncomingAndReceive();
        if (len <= 0 || len > 128) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        byte[] challenge = new byte[len];
        Util.arrayCopyNonAtomic(buf, ISO7816.OFFSET_CDATA, challenge, (short) 0, len);

        rsaSignature.init(rsaPrivateKey, Signature.MODE_SIGN);
        short sigLen = rsaSignature.sign(challenge, (short) 0, len, buf, (short) 0);
        apdu.setOutgoingAndSend((short) 0, sigLen);
    }

    // =========================================================================
    // HELPER DOC / GHI INT 4-BYTE BIG-ENDIAN
    // =========================================================================

    private static int getInt(byte[] buf, short off) {
        return ((buf[off] & 0xFF) << 24)
             | ((buf[(short) (off + 1)] & 0xFF) << 16)
             | ((buf[(short) (off + 2)] & 0xFF) << 8)
             | (buf[(short) (off + 3)] & 0xFF);
    }

    private static void setInt(byte[] buf, short off, int value) {
        buf[off]               = (byte) (value >>> 24);
        buf[(short) (off + 1)] = (byte) (value >>> 16);
        buf[(short) (off + 2)] = (byte) (value >>> 8);
        buf[(short) (off + 3)] = (byte) value;
    }
}
