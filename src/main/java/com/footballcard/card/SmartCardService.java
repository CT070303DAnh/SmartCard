package com.footballcard.card;

import com.footballcard.applet.CardConstants;
import com.footballcard.applet.FootballMemberApplet;
import com.licel.jcardsim.smartcardio.CardSimulator;
import com.licel.jcardsim.utils.AIDUtil;
import javacard.framework.AID;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;

/**
 * Tang dich vu giao tiep APDU giua phan mem POS (NetBeans) va The ao jCardSim.
 */
public class SmartCardService {

    private CardSimulator simulator;
    private AID appletAID;
    private boolean cardInserted;
    private boolean pinVerifiedSession;
    private Consumer<String> apduLogger;

    public SmartCardService() {
        this.cardInserted = false;
        this.pinVerifiedSession = false;
    }

    public void setApduLogger(Consumer<String> logger) {
        this.apduLogger = logger;
    }

    /**
     * Khoi tao the ao jCardSim moi (cai dat va chon FootballMemberApplet).
     */
    public void insertNewSimulatedCard() {
        simulator = new CardSimulator();
        appletAID = AIDUtil.create(CardConstants.APPLET_AID_BYTES);
        simulator.installApplet(appletAID, FootballMemberApplet.class);
        simulator.selectApplet(appletAID);
        cardInserted = true;
        pinVerifiedSession = false;
        log("THẺ ẢO ĐÃ ĐƯỢC CÀI ĐẶT & CHỌN APPLET (AID: " + toHex(CardConstants.APPLET_AID_BYTES) + ")");
    }

    public boolean isCardInserted() {
        return cardInserted && simulator != null;
    }

    public boolean isPinVerifiedSession() {
        return pinVerifiedSession;
    }

    public void ejectCard() {
        cardInserted = false;
        pinVerifiedSession = false;
        simulator = null;
        log("ĐÃ RÚT THẺ KHỎI ĐẦU ĐỌC ẢO.");
    }

    // =========================================================================
    // 1. XAC THUC PIN / PUK & KHOA THE
    // =========================================================================

    public void verifyPin(String pin) throws CardException {
        requireCard();
        byte[] data = pin.getBytes(StandardCharsets.US_ASCII);
        ResponseAPDU resp = send(CardConstants.INS_VERIFY_PIN, 0x00, 0x00, data);
        int sw = resp.getSW();
        if (sw == 0x9000) {
            pinVerifiedSession = true;
            return;
        }
        pinVerifiedSession = false;
        if (sw == (CardConstants.SW_CARD_LOCKED & 0xFFFF)) {
            throw new CardException("Thẻ đã bị KHÓA do nhập sai PIN quá 3 lần! Vui lòng dùng mã PUK để mở khóa.", sw);
        }
        if ((sw & 0xFFF0) == 0x63C0) {
            int remaining = sw & 0x0F;
            throw new CardException("Sai mã PIN! Còn lại " + remaining + " lần thử.", sw);
        }
        throw new CardException("Xác thực PIN thất bại (SW=" + hexSW(sw) + ")", sw);
    }

    public void changePin(String newPin) throws CardException {
        requireCard();
        byte[] data = newPin.getBytes(StandardCharsets.US_ASCII);
        ResponseAPDU resp = send(CardConstants.INS_CHANGE_PIN, 0x00, 0x00, data);
        checkSW(resp, "Đổi mã PIN thất bại");
        // Sau khi đổi PIN cần xác thực lại với PIN mới
        verifyPin(newPin);
    }

    public void unblockPinWithPuk(String puk, String newPin) throws CardException {
        requireCard();
        byte[] pukBytes = puk.getBytes(StandardCharsets.US_ASCII);
        byte[] pinBytes = newPin.getBytes(StandardCharsets.US_ASCII);
        byte[] payload = new byte[1 + pukBytes.length + 1 + pinBytes.length];
        payload[0] = (byte) pukBytes.length;
        System.arraycopy(pukBytes, 0, payload, 1, pukBytes.length);
        payload[1 + pukBytes.length] = (byte) pinBytes.length;
        System.arraycopy(pinBytes, 0, payload, 2 + pukBytes.length, pinBytes.length);

        ResponseAPDU resp = send(CardConstants.INS_UNBLOCK_PIN, 0x00, 0x00, payload);
        if (resp.getSW() == (CardConstants.SW_WRONG_PUK & 0xFFFF)) {
            throw new CardException("Mã PUK không chính xác!", resp.getSW());
        }
        checkSW(resp, "Mở khóa thẻ bằng PUK thất bại");
        pinVerifiedSession = false;
    }

    public void lockCardEmergency() throws CardException {
        requireCard();
        ResponseAPDU resp = send(CardConstants.INS_LOCK_CARD, 0x00, 0x00, null);
        checkSW(resp, "Khóa thẻ thất bại");
        pinVerifiedSession = false;
    }

    // =========================================================================
    // 2. CARD ID & THONG TIN CA NHAN (MA HOA AES-128)
    // =========================================================================

    public void setCardId(String cardId) throws CardException {
        requireCard();
        byte[] data = cardId.getBytes(StandardCharsets.UTF_8);
        ResponseAPDU resp = send(CardConstants.INS_SET_CARD_ID, 0x00, 0x00, data);
        checkSW(resp, "Ghi Card ID that bai");
    }

    public String getCardId() throws CardException {
        requireCard();
        ResponseAPDU resp = send(CardConstants.INS_GET_CARD_ID, 0x00, 0x00, null);
        checkSW(resp, "Doc Card ID that bai");
        return new String(resp.getData(), StandardCharsets.UTF_8);
    }

    public void savePersonalInfo(CardModels.MemberInfo info) throws CardException {
        requireCard();
        if (info.cardId != null && !info.cardId.isEmpty()) {
            setCardId(info.cardId);
        }
        byte[] data = info.serialize().getBytes(StandardCharsets.UTF_8);
        ResponseAPDU resp = send(CardConstants.INS_SET_PERSONAL_INFO, 0x00, 0x00, data);
        checkSW(resp, "Luu & ma hoa thong tin ca nhan that bai");
    }

    public CardModels.MemberInfo readPersonalInfo() throws CardException {
        requireCard();
        ResponseAPDU resp = send(CardConstants.INS_GET_PERSONAL_INFO, 0x00, 0x00, null);
        checkSW(resp, "Doc & giai ma thong tin ca nhan that bai");
        String raw = new String(resp.getData(), StandardCharsets.UTF_8);
        return CardModels.MemberInfo.deserialize(raw);
    }

    /**
     * Doc du lieu dang ma hoa AES thuc te trong EEPROM the (dung de demo bao mat).
     */
    public String readEncryptedPersonalInfoHex() throws CardException {
        requireCard();
        ResponseAPDU resp = send(CardConstants.INS_GET_ENCRYPTED_DUMP, 0x00, 0x00, null);
        checkSW(resp, "Doc du lieu ma hoa that bai");
        return toHex(resp.getData());
    }

    // =========================================================================
    // 3. NAP TIEN, THANH TOAN, TICH DIEM, DOI QUA & LICH SU TREN THE
    // =========================================================================

    public void topUp(int amountVnd) throws CardException {
        requireCard();
        byte[] payload = packAmountAndNow(amountVnd);
        ResponseAPDU resp = send(CardConstants.INS_TOP_UP, 0x00, 0x00, payload);
        checkSW(resp, "Nap tien that bai");
    }

    public CardModels.PayResult pay(int amountVnd, byte txType, String referenceId) throws CardException {
        requireCard();
        byte[] payload = packAmountAndNow(amountVnd);
        ResponseAPDU resp = send(CardConstants.INS_PAY, txType & 0xFF, 0x00, payload);
        checkSW(resp, "Thanh toan that bai");

        ByteBuffer bb = ByteBuffer.wrap(resp.getData());
        CardModels.PayResult result = new CardModels.PayResult();
        result.earnedPoints = bb.getInt();
        result.newBalance = bb.getInt();

        // Ky so giao dich bang khoa RSA tren the
        String txChallenge = "PAY|" + referenceId + "|" + amountVnd + "|" + (System.currentTimeMillis() / 1000L);
        byte[] sig = signChallenge(txChallenge.getBytes(StandardCharsets.UTF_8));
        result.rsaSignatureBase64 = Base64.getEncoder().encodeToString(sig);
        return result;
    }

    public int redeemReward(int pointsCost) throws CardException {
        requireCard();
        byte[] payload = packAmountAndNow(pointsCost);
        ResponseAPDU resp = send(CardConstants.INS_REDEEM_REWARD, 0x00, 0x00, payload);
        checkSW(resp, "Doi qua that bai");
        return ByteBuffer.wrap(resp.getData()).getInt();
    }

    public CardModels.CardSummary getSummary() throws CardException {
        requireCard();
        ResponseAPDU resp = send(CardConstants.INS_GET_SUMMARY, 0x00, 0x00, null);
        checkSW(resp, "Doc trang thai the that bai");
        ByteBuffer bb = ByteBuffer.wrap(resp.getData());
        CardModels.CardSummary summary = new CardModels.CardSummary();
        summary.balance = bb.getInt();
        summary.loyaltyPoints = bb.getInt();
        summary.totalSpent = bb.getInt();
        summary.tierCode = bb.get();
        summary.locked = bb.get() == 1;
        summary.pinTriesRemaining = bb.get() & 0xFF;
        return summary;
    }

    public List<CardModels.CardTxRecord> getOnCardTxHistory() throws CardException {
        requireCard();
        ResponseAPDU resp = send(CardConstants.INS_GET_TX_HISTORY, 0x00, 0x00, null);
        checkSW(resp, "Doc lich su giao dich tren the that bai");
        byte[] data = resp.getData();
        List<CardModels.CardTxRecord> list = new ArrayList<>();
        if (data.length == 0) {
            return list;
        }
        int count = data[0] & 0xFF;
        ByteBuffer bb = ByteBuffer.wrap(data, 1, data.length - 1);
        for (int i = 0; i < count && bb.remaining() >= 13; i++) {
            CardModels.CardTxRecord r = new CardModels.CardTxRecord();
            r.type = bb.get();
            r.amount = bb.getInt();
            r.pointsDelta = bb.getInt();
            r.timestampSec = bb.getInt();
            list.add(r);
        }
        return list;
    }

    // =========================================================================
    // 4. XAC THUC CHU KY SO RSA (CHONG GIA MAO THE)
    // =========================================================================

    public PublicKey getCardRsaPublicKey() throws CardException {
        requireCard();
        ResponseAPDU modResp = send(CardConstants.INS_GET_PUBLIC_KEY, 0x00, 0x00, null);
        checkSW(modResp, "Doc RSA Modulus that bai");
        ResponseAPDU expResp = send(CardConstants.INS_GET_PUBLIC_KEY, 0x01, 0x00, null);
        checkSW(expResp, "Doc RSA Exponent that bai");
        try {
            BigInteger modulus = new BigInteger(1, modResp.getData());
            BigInteger exponent = new BigInteger(1, expResp.getData());
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePublic(new RSAPublicKeySpec(modulus, exponent));
        } catch (Exception e) {
            throw new CardException("Khong the tai tao RSA Public Key: " + e.getMessage(), 0x6F00);
        }
    }

    public String getCardRsaPublicKeyBase64() throws CardException {
        return Base64.getEncoder().encodeToString(getCardRsaPublicKey().getEncoded());
    }

    public byte[] signChallenge(byte[] challenge) throws CardException {
        requireCard();
        ResponseAPDU resp = send(CardConstants.INS_SIGN_CHALLENGE, 0x00, 0x00, challenge);
        checkSW(resp, "Ky so RSA that bai");
        return resp.getData();
    }

    public boolean verifyCardAuthenticity() throws CardException {
        PublicKey pubKey = getCardRsaPublicKey();
        byte[] challenge = ("CHALLENGE-" + System.nanoTime()).getBytes(StandardCharsets.UTF_8);
        byte[] sigBytes = signChallenge(challenge);
        try {
            Signature verifier = Signature.getInstance("SHA1withRSA");
            verifier.initVerify(pubKey);
            verifier.update(challenge);
            return verifier.verify(sigBytes);
        } catch (Exception e) {
            throw new CardException("Loi xac minh chu ky RSA: " + e.getMessage(), 0x6F00);
        }
    }

    // =========================================================================
    // APDU TRANSPORT & HELPERS
    // =========================================================================

    private void requireCard() throws CardException {
        if (!isCardInserted()) {
            throw new CardException("Chưa đưa thẻ ảo vào đầu đọc! Hãy bấm 'Khởi tạo / Quẹt thẻ ảo'.", 0x6F00);
        }
    }

    private ResponseAPDU send(byte ins, int p1, int p2, byte[] data) {
        CommandAPDU cmd = (data == null || data.length == 0)
                ? new CommandAPDU(CardConstants.CLA_PROPRIETARY & 0xFF, ins & 0xFF, p1, p2)
                : new CommandAPDU(CardConstants.CLA_PROPRIETARY & 0xFF, ins & 0xFF, p1, p2, data);
        log(">> CMD : " + toHex(cmd.getBytes()));
        ResponseAPDU resp = simulator.transmitCommand(cmd);
        log("<< RESP: " + toHex(resp.getBytes()) + " [SW=" + hexSW(resp.getSW()) + "]");
        return resp;
    }

    private void checkSW(ResponseAPDU resp, String errorPrefix) throws CardException {
        int sw = resp.getSW();
        if (sw == 0x9000) return;
        if (sw == (CardConstants.SW_PIN_VERIFICATION_REQUIRED & 0xFFFF)) {
            throw new CardException("Vui lòng xác thực mã PIN trước khi thực hiện thao tác này!", sw);
        }
        if (sw == (CardConstants.SW_CARD_LOCKED & 0xFFFF)) {
            throw new CardException("Thẻ đang bị KHÓA! Vui lòng mở khóa bằng mã PUK.", sw);
        }
        if (sw == (CardConstants.SW_INSUFFICIENT_BALANCE & 0xFFFF)) {
            throw new CardException("Số dư trong thẻ không đủ để thanh toán!", sw);
        }
        if (sw == (CardConstants.SW_INSUFFICIENT_POINTS & 0xFFFF)) {
            throw new CardException("Điểm tích lũy không đủ để đổi quà này!", sw);
        }
        if (sw == (CardConstants.SW_INVALID_AMOUNT & 0xFFFF)) {
            throw new CardException("Số tiền / số điểm không hợp lệ!", sw);
        }
        throw new CardException(errorPrefix + " (SW=" + hexSW(sw) + ")", sw);
    }

    private static byte[] packAmountAndNow(int amount) {
        int nowSec = (int) (System.currentTimeMillis() / 1000L);
        return ByteBuffer.allocate(8).putInt(amount).putInt(nowSec).array();
    }

    private void log(String msg) {
        if (apduLogger != null) {
            apduLogger.accept(msg);
        }
    }

    public static String toHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "(rong)";
        StringBuilder sb = new StringBuilder(bytes.length * 3);
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

    private static String hexSW(int sw) {
        return String.format("%04X", sw & 0xFFFF);
    }

    public static class CardException extends Exception {
        public final int sw;

        public CardException(String message, int sw) {
            super(message);
            this.sw = sw;
        }
    }
}
