package com.footballcard.card;

import com.footballcard.applet.CardConstants;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Cac model DTO dung cho tang giao tiep the thong minh va giao dien POS.
 */
public final class CardModels {

    private CardModels() {}

    public static class MemberInfo {
        public String cardId;
        public String firebaseUid;
        public String fullName;
        public String phoneNumber;
        public String dateOfBirth;
        public String teamName;

        public MemberInfo() {}

        public MemberInfo(String cardId, String firebaseUid, String fullName,
                          String phoneNumber, String dateOfBirth, String teamName) {
            this.cardId = safe(cardId);
            this.firebaseUid = safe(firebaseUid);
            this.fullName = safe(fullName);
            this.phoneNumber = safe(phoneNumber);
            this.dateOfBirth = safe(dateOfBirth);
            this.teamName = safe(teamName);
        }

        public String serialize() {
            return safe(cardId) + "|" + safe(firebaseUid) + "|" + safe(fullName)
                    + "|" + safe(phoneNumber) + "|" + safe(dateOfBirth) + "|" + safe(teamName);
        }

        public static MemberInfo deserialize(String raw) {
            MemberInfo info = new MemberInfo();
            if (raw == null || raw.isEmpty()) {
                return info;
            }
            String[] parts = raw.split("\\|", -1);
            if (parts.length > 0) info.cardId = parts[0];
            if (parts.length > 1) info.firebaseUid = parts[1];
            if (parts.length > 2) info.fullName = parts[2];
            if (parts.length > 3) info.phoneNumber = parts[3];
            if (parts.length > 4) info.dateOfBirth = parts[4];
            if (parts.length > 5) info.teamName = parts[5];
            return info;
        }

        private static String safe(String s) {
            return s == null ? "" : s.replace("|", " ").trim();
        }
    }

    public static class CardSummary {
        public int balance;
        public int loyaltyPoints;
        public int totalSpent;
        public byte tierCode;
        public boolean locked;
        public int pinTriesRemaining;

        public String getTierName() {
            switch (tierCode) {
                case CardConstants.TIER_VIP:    return "VIP (x2.0 điểm)";
                case CardConstants.TIER_GOLD:   return "Vàng (x1.5 điểm)";
                case CardConstants.TIER_SILVER: return "Bạc (x1.2 điểm)";
                default:                        return "Đồng (x1.0 điểm)";
            }
        }

        public String getTierKey() {
            switch (tierCode) {
                case CardConstants.TIER_VIP:    return "vip";
                case CardConstants.TIER_GOLD:   return "gold";
                case CardConstants.TIER_SILVER: return "silver";
                default:                        return "bronze";
            }
        }
    }

    public static class PayResult {
        public int earnedPoints;
        public int newBalance;
        public String rsaSignatureBase64;
    }

    public static class CardTxRecord {
        public byte type;
        public int amount;
        public int pointsDelta;
        public int timestampSec;

        private static final DateTimeFormatter FMT = DateTimeFormatter
                .ofPattern("dd/MM/yyyy HH:mm:ss")
                .withZone(ZoneId.systemDefault());

        public String getTypeLabel() {
            switch (type) {
                case CardConstants.TX_TOP_UP:      return "Nạp tiền";
                case CardConstants.TX_PAY_BOOKING: return "Thanh toán sân";
                case CardConstants.TX_PAY_SERVICE: return "Mua dịch vụ";
                case CardConstants.TX_REDEEM_GIFT: return "Đổi quà";
                default:                           return "Khác";
            }
        }

        public String getFormattedTime() {
            return FMT.format(Instant.ofEpochSecond(Integer.toUnsignedLong(timestampSec)));
        }
    }

    public static class BookingItem {
        public String bookingId;
        public String customerId;
        public String customerName;
        public String pitchName;
        public String bookingDate;
        public String startTime;
        public String endTime;
        public double totalAmount;
        public String paymentStatus;
        public String bookingStatus;
        public String matchStatus;
    }

    public static class ServiceItem {
        public String serviceId;
        public String name;
        public double price;
        public String unit;

        public ServiceItem(String serviceId, String name, double price, String unit) {
            this.serviceId = serviceId;
            this.name = name;
            this.price = price;
            this.unit = unit;
        }

        @Override
        public String toString() {
            return String.format("%s - %,.0f VND / %s", name, price, unit);
        }
    }

    public static class RewardItem {
        public String rewardId;
        public String name;
        public int pointsCost;
        public String description;

        public RewardItem(String rewardId, String name, int pointsCost, String description) {
            this.rewardId = rewardId;
            this.name = name;
            this.pointsCost = pointsCost;
            this.description = description;
        }

        @Override
        public String toString() {
            return String.format("%s (%d điểm) - %s", name, pointsCost, description);
        }
    }
}
