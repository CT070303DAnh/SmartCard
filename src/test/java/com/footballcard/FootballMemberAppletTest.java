package com.footballcard;

import com.footballcard.applet.CardConstants;
import com.footballcard.card.CardModels;
import com.footballcard.card.SmartCardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test kiem thu toan bo chuc nang cua FootballMemberApplet tren jCardSim.
 */
public class FootballMemberAppletTest {

    private SmartCardService card;

    @BeforeEach
    void setUp() {
        card = new SmartCardService();
        card.insertNewSimulatedCard();
    }

    @Test
    void testPinAuthLockAndPukUnblock() throws Exception {
        // Nhap sai 3 lan -> khoa the
        assertThrows(SmartCardService.CardException.class, () -> card.verifyPin("000000"));
        assertThrows(SmartCardService.CardException.class, () -> card.verifyPin("111111"));
        SmartCardService.CardException lockedEx = assertThrows(
                SmartCardService.CardException.class, () -> card.verifyPin("222222"));
        assertEquals(CardConstants.SW_CARD_LOCKED & 0xFFFF, lockedEx.sw);

        CardModels.CardSummary s1 = card.getSummary();
        assertTrue(s1.locked);
        assertEquals(0, s1.pinTriesRemaining);

        // Mo khoa bang PUK mac dinh va dat PIN moi 654321
        card.unblockPinWithPuk("88888888", "654321");
        card.verifyPin("654321");
        assertTrue(card.isPinVerifiedSession());
        assertFalse(card.getSummary().locked);
    }

    @Test
    void testAesEncryptedPersonalInfo() throws Exception {
        card.verifyPin("123456");

        CardModels.MemberInfo input = new CardModels.MemberInfo(
                "FC-2026-0001", "uid_abc123", "Nguyen Van A", "0912345678", "15/08/2002", "FC Bach Khoa"
        );
        card.savePersonalInfo(input);

        // Ban ma AES khong duoc chua plaintext
        String cipherHex = card.readEncryptedPersonalInfoHex();
        assertNotNull(cipherHex);
        assertFalse(cipherHex.contains("Nguyen"));

        // Doc va giai ma phai khop 100%
        CardModels.MemberInfo decoded = card.readPersonalInfo();
        assertEquals("FC-2026-0001", decoded.cardId);
        assertEquals("uid_abc123", decoded.firebaseUid);
        assertEquals("Nguyen Van A", decoded.fullName);
        assertEquals("0912345678", decoded.phoneNumber);
        assertEquals("FC Bach Khoa", decoded.teamName);
    }

    @Test
    void testTopUpPayLoyaltyRedeemAndHistory() throws Exception {
        card.verifyPin("123456");
        card.topUp(1_000_000);
        assertEquals(1_000_000, card.getSummary().balance);

        // Thanh toan 350.000 VND tien san -> cong 35 diem (hang Dong: 1 diem / 10.000)
        CardModels.PayResult payRes = card.pay(350_000, CardConstants.TX_PAY_BOOKING, "BK001");
        assertEquals(35, payRes.earnedPoints);
        assertEquals(650_000, payRes.newBalance);
        assertNotNull(payRes.rsaSignatureBase64);

        // Doi qua 20 diem -> con 15 diem
        int remainPts = card.redeemReward(20);
        assertEquals(15, remainPts);

        // Kiem tra lich su luu tren the (3 giao dich: doi qua, thanh toan, nap tien)
        List<CardModels.CardTxRecord> history = card.getOnCardTxHistory();
        assertEquals(3, history.size());
        assertEquals(CardConstants.TX_REDEEM_GIFT, history.get(0).type);
        assertEquals(CardConstants.TX_PAY_BOOKING, history.get(1).type);
        assertEquals(CardConstants.TX_TOP_UP, history.get(2).type);
    }

    @Test
    void testRsaCardAuthenticity() throws Exception {
        assertTrue(card.verifyCardAuthenticity());
    }
}
