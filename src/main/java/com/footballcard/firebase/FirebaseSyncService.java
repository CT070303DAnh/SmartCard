package com.footballcard.firebase;

import com.footballcard.card.CardModels;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.SetOptions;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dich vu dong bo Cloud Firestore giua phan mem POS (NetBeans + jCardSim)
 * va ung dung Android (FootballBookingAndroid).
 *
 * Ho tro che do Offline Demo neu chua dat file serviceAccountKey.json.
 */
public class FirebaseSyncService {

    private Firestore db;
    private boolean connected;
    private String statusMessage;

    public FirebaseSyncService() {
        this.connected = false;
        this.statusMessage = "Chưa kết nối Firebase (Chế độ Offline Demo)";
    }

    /**
     * Thử khởi tạo Firebase Admin SDK từ file serviceAccountKey.json.
     */
    public boolean tryConnect() {
        try {
            if (!FirebaseApp.getApps().isEmpty()) {
                db = FirestoreClient.getFirestore();
                connected = true;
                statusMessage = "Đã kết nối Cloud Firestore (Online)";
                return true;
            }

            InputStream keyStream = locateServiceAccountKey();
            if (keyStream == null) {
                connected = false;
                statusMessage = "Chế độ Offline Demo (chưa có serviceAccountKey.json)";
                return false;
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(keyStream))
                    .build();
            FirebaseApp.initializeApp(options);
            db = FirestoreClient.getFirestore();
            connected = true;
            statusMessage = "Đã kết nối Cloud Firestore (Online)";
            return true;
        } catch (Exception e) {
            connected = false;
            statusMessage = "Lỗi kết nối Firebase: " + e.getMessage();
            return false;
        }
    }

    private InputStream locateServiceAccountKey() throws Exception {
        File rootFile = new File("serviceAccountKey.json");
        if (rootFile.exists()) {
            return new FileInputStream(rootFile);
        }
        File configFile = new File("config/serviceAccountKey.json");
        if (configFile.exists()) {
            return new FileInputStream(configFile);
        }
        return getClass().getClassLoader().getResourceAsStream("serviceAccountKey.json");
    }

    public boolean isConnected() {
        return connected && db != null;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    // =========================================================================
    // 1. TIM USER & DONG BO THONG TIN THE LEN COLLECTION "users"
    // =========================================================================

    public Map<String, Object> findUserByEmailOrPhone(String query) throws Exception {
        if (!isConnected()) return null;
        String trimmed = query.trim();

        List<QueryDocumentSnapshot> byEmail = db.collection("users")
                .whereEqualTo("email", trimmed).limit(1).get().get().getDocuments();
        if (!byEmail.isEmpty()) {
            return toUserMap(byEmail.get(0));
        }

        List<QueryDocumentSnapshot> byPhone = db.collection("users")
                .whereEqualTo("phoneNumber", trimmed).limit(1).get().get().getDocuments();
        if (!byPhone.isEmpty()) {
            return toUserMap(byPhone.get(0));
        }

        DocumentSnapshot byUid = db.collection("users").document(trimmed).get().get();
        if (byUid.exists()) {
            return toUserMap(byUid);
        }
        return null;
    }

    public String checkCardStatusOnCloud(String uid) throws Exception {
        if (!isConnected() || uid == null || uid.isEmpty()) {
            return "active";
        }
        DocumentSnapshot doc = db.collection("users").document(uid).get().get();
        if (doc.exists() && doc.contains("cardStatus")) {
            return doc.getString("cardStatus");
        }
        return "active";
    }

    public void syncCardToUser(String uid, String cardId, CardModels.CardSummary summary, String rsaPubKeyBase64) throws Exception {
        if (!isConnected() || uid == null || uid.isEmpty()) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("cardId", cardId);
        updates.put("cardBalance", (double) summary.balance);
        updates.put("loyaltyPoints", (long) summary.loyaltyPoints);
        updates.put("totalSpent", (double) summary.totalSpent);
        updates.put("memberTier", summary.getTierKey());
        updates.put("cardStatus", summary.locked ? "locked" : "active");
        if (rsaPubKeyBase64 != null && !rsaPubKeyBase64.isEmpty()) {
            updates.put("cardPublicKey", rsaPubKeyBase64);
        }
        updates.put("updatedAt", FieldValue.serverTimestamp());

        db.collection("users").document(uid).set(updates, SetOptions.merge()).get();
    }

    // =========================================================================
    // 2. LAY DON DAT SAN (COLLECTION "bookings") & CHECK-IN THANH TOAN
    // =========================================================================

    public List<CardModels.BookingItem> fetchUnpaidBookingsForUser(String uid) throws Exception {
        List<CardModels.BookingItem> result = new ArrayList<>();
        if (!isConnected() || uid == null || uid.isEmpty()) {
            return result;
        }
        List<QueryDocumentSnapshot> docs = db.collection("bookings")
                .whereEqualTo("customerId", uid)
                .get().get().getDocuments();

        for (QueryDocumentSnapshot d : docs) {
            String payStatus = d.getString("paymentStatus");
            String bookStatus = d.getString("bookingStatus");
            if (!"paid".equals(payStatus) && !"cancelled".equals(bookStatus) && !"rejected".equals(bookStatus)) {
                CardModels.BookingItem item = new CardModels.BookingItem();
                item.bookingId = d.getId();
                item.customerId = d.getString("customerId");
                item.customerName = d.getString("customerName");
                item.pitchName = d.getString("pitchName");
                item.bookingDate = d.getString("bookingDate");
                item.startTime = d.getString("startTime");
                item.endTime = d.getString("endTime");
                Double amt = d.getDouble("totalAmount");
                item.totalAmount = amt != null ? amt : 0;
                item.paymentStatus = payStatus;
                item.bookingStatus = bookStatus;
                item.matchStatus = d.getString("matchStatus");
                result.add(item);
            }
        }
        return result;
    }

    /**
     * Cap nhat don dat san sau khi quet the thanh toan & check-in thanh cong.
     * App Android se tu dong cap nhat realtime!
     */
    public void markBookingPaidAndCheckedIn(String bookingId, String cardId, String rsaSignature) throws Exception {
        if (!isConnected() || bookingId == null || bookingId.isEmpty()) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("paymentStatus", "paid");
        updates.put("paymentMethod", "member_card");
        updates.put("bookingStatus", "approved");
        updates.put("matchStatus", "playing");
        updates.put("cardId", cardId);
        updates.put("cardSignature", rsaSignature);
        updates.put("updatedAt", FieldValue.serverTimestamp());

        db.collection("bookings").document(bookingId).update(updates).get();
    }

    // =========================================================================
    // 3. DOC DICH VU (COLLECTION "services") & GHI LICH SU GIAO DICH THE
    // =========================================================================

    public List<CardModels.ServiceItem> fetchServices() {
        List<CardModels.ServiceItem> list = new ArrayList<>();
        if (isConnected()) {
            try {
                List<QueryDocumentSnapshot> docs = db.collection("services").get().get().getDocuments();
                for (QueryDocumentSnapshot d : docs) {
                    Boolean active = d.getBoolean("isActive");
                    if (active == null || active) {
                        Double price = d.getDouble("price");
                        list.add(new CardModels.ServiceItem(
                                d.getId(),
                                d.getString("name"),
                                price != null ? price : 0,
                                d.getString("unit") != null ? d.getString("unit") : "luot"
                        ));
                    }
                }
            } catch (Exception ignored) {
                // Fallback sang danh sach mac dinh ben duoi
            }
        }
        if (list.isEmpty()) {
            list.add(new CardModels.ServiceItem("srv_water", "Nước uống / Bình trà đá", 25000, "bình"));
            list.add(new CardModels.ServiceItem("srv_bib", "Thuê áo pitch thi đấu", 30000, "bộ"));
            list.add(new CardModels.ServiceItem("srv_ball", "Thuê bóng thi đấu", 50000, "quả"));
            list.add(new CardModels.ServiceItem("srv_shoes", "Thuê giày đinh TF", 40000, "đôi"));
            list.add(new CardModels.ServiceItem("srv_referee", "Thuê trọng tài", 200000, "trận"));
        }
        return list;
    }

    public List<CardModels.RewardItem> fetchRewards() {
        List<CardModels.RewardItem> list = new ArrayList<>();
        if (isConnected()) {
            try {
                List<QueryDocumentSnapshot> docs = db.collection("rewards").get().get().getDocuments();
                for (QueryDocumentSnapshot d : docs) {
                    Long pts = d.getLong("pointsCost");
                    list.add(new CardModels.RewardItem(
                            d.getId(),
                            d.getString("name"),
                            pts != null ? pts.intValue() : 50,
                            d.getString("description") != null ? d.getString("description") : ""
                    ));
                }
            } catch (Exception ignored) {
                // Fallback mặc định
            }
        }
        if (list.isEmpty()) {
            list.add(new CardModels.RewardItem("rw_water", "Chai nước tăng lực Revive", 20, "Nhận ngay tại quầy"));
            list.add(new CardModels.RewardItem("rw_voucher50", "Voucher giảm 50.000 VND tiền sân", 80, "Áp dụng mọi khung giờ"));
            list.add(new CardModels.RewardItem("rw_socks", "Tất chống trơn đá bóng", 120, "Size tự do"));
            list.add(new CardModels.RewardItem("rw_jersey", "Áo đấu CLB chính hãng", 300, "Chọn size tại quầy"));
        }
        return list;
    }

    public void recordCardTransaction(String uid, String cardId, String type, int amount,
                                      int pointsDelta, String description, String bookingId,
                                      String rsaSignature) throws Exception {
        if (!isConnected()) return;

        DocumentReference ref = db.collection("card_transactions").document();
        Map<String, Object> tx = new HashMap<>();
        tx.put("txId", ref.getId());
        tx.put("userId", uid != null ? uid : "");
        tx.put("cardId", cardId != null ? cardId : "");
        tx.put("type", type);
        tx.put("amount", (double) amount);
        tx.put("pointsDelta", pointsDelta);
        tx.put("description", description);
        tx.put("bookingId", bookingId != null ? bookingId : "");
        tx.put("rsaSignature", rsaSignature != null ? rsaSignature : "");
        tx.put("createdAt", FieldValue.serverTimestamp());
        ref.set(tx).get();
    }

    private Map<String, Object> toUserMap(DocumentSnapshot doc) {
        Map<String, Object> map = new HashMap<>();
        if (doc.getData() != null) {
            map.putAll(doc.getData());
        }
        map.put("uid", doc.getId());
        return map;
    }
}
