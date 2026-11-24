package com.footballcard.applet;

/**
 * Tap trung toan bo hang so giao thuc APDU (CLA, INS, SW, AID)
 * dung chung giua FootballMemberApplet (tren the jCardSim) va SmartCardService (POS).
 */
public final class CardConstants {

    private CardConstants() {}

    // ===== APPLET AID: "FBCARD" (6 bytes) =====
    public static final byte[] APPLET_AID_BYTES = new byte[] {
        (byte) 0xF0, 0x46, 0x42, 0x43, 0x41, 0x52, 0x44, 0x01
    };

    // ===== CLA =====
    public static final byte CLA_PROPRIETARY = (byte) 0x80;

    // ===== INS: XAC THUC & BAO MAT =====
    public static final byte INS_VERIFY_PIN         = (byte) 0x10;
    public static final byte INS_CHANGE_PIN         = (byte) 0x11;
    public static final byte INS_UNBLOCK_PIN        = (byte) 0x12; // Dung PUK mo khoa & dat lai PIN
    public static final byte INS_LOCK_CARD          = (byte) 0x13; // Khoa the khan cap (bao mat the)

    // ===== INS: THONG TIN CA NHAN (MA HOA AES-128) =====
    public static final byte INS_SET_PERSONAL_INFO  = (byte) 0x20;
    public static final byte INS_GET_PERSONAL_INFO  = (byte) 0x21;
    public static final byte INS_GET_ENCRYPTED_DUMP = (byte) 0x22; // Xem ban ma AES trong EEPROM
    public static final byte INS_SET_CARD_ID        = (byte) 0x23;
    public static final byte INS_GET_CARD_ID        = (byte) 0x24;

    // ===== INS: TAI CHINH, TICH DIEM & DOI QUA =====
    public static final byte INS_TOP_UP             = (byte) 0x30; // Nap tien (4 byte VND)
    public static final byte INS_PAY                = (byte) 0x31; // Thanh toan san / dich vu (4 byte VND + 1 byte txType)
    public static final byte INS_REDEEM_REWARD      = (byte) 0x32; // Doi qua bang diem (4 byte points)
    public static final byte INS_GET_SUMMARY        = (byte) 0x33; // So du, diem, tong chi tieu, hang, trang thai
    public static final byte INS_GET_TX_HISTORY     = (byte) 0x34; // 5 giao dich gan nhat luu tren the

    // ===== INS: CHU KY SO RSA CHONG GIA MAO THE =====
    public static final byte INS_GET_PUBLIC_KEY     = (byte) 0x40; // P1=0x00: Modulus, P1=0x01: Exponent
    public static final byte INS_SIGN_CHALLENGE     = (byte) 0x41; // Ky so challenge/hash giao dich

    // ===== LOAI GIAO DICH LUU TREN THE =====
    public static final byte TX_TOP_UP       = (byte) 0x01;
    public static final byte TX_PAY_BOOKING  = (byte) 0x02;
    public static final byte TX_PAY_SERVICE  = (byte) 0x03;
    public static final byte TX_REDEEM_GIFT  = (byte) 0x04;

    // ===== HANG THANH VIEN =====
    public static final byte TIER_BRONZE = 0; // < 2.000.000 VND
    public static final byte TIER_SILVER = 1; // >= 2.000.000 VND (x1.2 diem)
    public static final byte TIER_GOLD   = 2; // >= 5.000.000 VND (x1.5 diem)
    public static final byte TIER_VIP    = 3; // >= 10.000.000 VND (x2.0 diem)

    // ===== CAU HINH PIN / PUK =====
    public static final byte PIN_TRY_LIMIT = 3;
    public static final byte PIN_MAX_SIZE  = 8;
    public static final byte PUK_TRY_LIMIT = 5;
    public static final byte PUK_MAX_SIZE  = 8;

    public static final byte[] DEFAULT_PIN = new byte[] { '1', '2', '3', '4', '5', '6' };
    public static final byte[] DEFAULT_PUK = new byte[] { '8', '8', '8', '8', '8', '8', '8', '8' };

    // ===== CUSTOM STATUS WORDS =====
    public static final short SW_PIN_VERIFICATION_REQUIRED = (short) 0x6982;
    public static final short SW_CARD_LOCKED               = (short) 0x6983;
    public static final short SW_INSUFFICIENT_BALANCE      = (short) 0x6A01;
    public static final short SW_INSUFFICIENT_POINTS       = (short) 0x6A02;
    public static final short SW_INVALID_AMOUNT            = (short) 0x6A03;
    public static final short SW_DATA_TOO_LARGE            = (short) 0x6A84;
    public static final short SW_WRONG_PUK                 = (short) 0x6984;
}
