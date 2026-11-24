package com.footballcard.ui;

import com.footballcard.applet.CardConstants;
import com.footballcard.card.CardModels;
import com.footballcard.card.SmartCardService;
import com.footballcard.firebase.FirebaseSyncService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Giao diện POS quản lý Thẻ Thành Viên Sân Bóng (NetBeans + jCardSim + Firebase).
 */
public class MainFrame extends JFrame {

    private final SmartCardService cardService = new SmartCardService();
    private final FirebaseSyncService firebaseService = new FirebaseSyncService();
    private final NumberFormat vndFormat = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    // Thanh trạng thái trên cùng
    private JLabel lblCardStatus;
    private JLabel lblPinStatus;
    private JLabel lblFirebaseStatus;

    // Bảng mô phỏng thẻ bên trái
    private JLabel lblVisualCardId;
    private JLabel lblVisualHolder;
    private JLabel lblVisualTier;
    private JLabel lblVisualBalance;
    private JLabel lblVisualPoints;
    private JLabel lblVisualSpent;
    private JLabel lblVisualTries;

    // Tab 1: Thông tin cá nhân (AES)
    private JTextField txtSearchFirebaseUser;
    private JTextField txtCardId;
    private JTextField txtFirebaseUid;
    private JTextField txtFullName;
    private JTextField txtPhone;
    private JTextField txtDob;
    private JTextField txtTeamName;
    private JTextArea txtEncryptedDump;

    // Tab 2: Nạp tiền, dịch vụ & lịch sử trên thẻ
    private JTextField txtTopUpAmount;
    private JComboBox<CardModels.ServiceItem> cboServices;
    private JSpinner spnServiceQty;
    private DefaultTableModel txTableModel;

    // Tab 3: Check-in đơn đặt sân từ Android
    private DefaultTableModel bookingTableModel;
    private JTable tblBookings;
    private List<CardModels.BookingItem> loadedBookings = new ArrayList<>();
    private JTextField txtManualBookingAmount;

    // Tab 4: Tích điểm & Đổi quà
    private JList<CardModels.RewardItem> lstRewards;
    private DefaultListModel<CardModels.RewardItem> rewardListModel;

    // Tab 5: Bảo mật (PIN / PUK / RSA)
    private JPasswordField txtPinVerify;
    private JPasswordField txtNewPin;
    private JPasswordField txtPuk;
    private JPasswordField txtResetPin;
    private JTextArea txtRsaOutput;

    // Khung log APDU ở dưới cùng
    private JTextArea txtApduLog;

    private CardModels.MemberInfo currentMember = new CardModels.MemberInfo();

    public MainFrame() {
        super("Hệ Thống Thẻ Thành Viên Sân Bóng - jCardSim + Firebase POS");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 780);
        setLocationRelativeTo(null);

        cardService.setApduLogger(this::appendApduLog);
        initUI();
        initServices();
    }

    private void initServices() {
        firebaseService.tryConnect();
        updateFirebaseBadge();
        reloadServicesAndRewards();
    }

    private void initUI() {
        setLayout(new BorderLayout(8, 8));
        ((JComponent) getContentPane()).setBorder(new EmptyBorder(8, 8, 8, 8));

        add(buildTopBar(), BorderLayout.NORTH);
        add(buildLeftCardPanel(), BorderLayout.WEST);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 13));
        tabs.addTab("1. Thông tin & Mã hóa AES", buildTabPersonalInfo());
        tabs.addTab("2. Nạp tiền & Dịch vụ", buildTabFinance());
        tabs.addTab("3. Check-in Đơn Đặt Sân", buildTabBookings());
        tabs.addTab("4. Tích điểm & Đổi quà", buildTabRewards());
        tabs.addTab("5. Bảo mật (PIN/PUK/RSA)", buildTabSecurity());
        add(tabs, BorderLayout.CENTER);

        add(buildBottomApduLog(), BorderLayout.SOUTH);
    }

    // =========================================================================
    // THANH TRÊN CÙNG & BẢNG MÔ PHỎNG THẺ BÊN TRÁI
    // =========================================================================

    private JPanel buildTopBar() {
        JPanel panel = new JPanel(new BorderLayout(10, 0));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(210, 215, 220)),
                new EmptyBorder(8, 12, 8, 12)
        ));
        panel.setBackground(new Color(245, 248, 252));

        JPanel leftBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftBtns.setOpaque(false);

        JButton btnInsert = new JButton("Khởi tạo / Quẹt thẻ ảo");
        btnInsert.setBackground(new Color(25, 135, 84));
        btnInsert.setForeground(Color.BLACK);
        btnInsert.addActionListener(e -> onInsertCard());

        JButton btnQuickVerify = new JButton("Nhập PIN nhanh");
        btnQuickVerify.setBackground(new Color(13, 110, 253));
        btnQuickVerify.setForeground(Color.BLACK);
        btnQuickVerify.addActionListener(e -> onQuickVerifyPin());

        JButton btnEject = new JButton("Rút thẻ");
        btnEject.addActionListener(e -> onEjectCard());

        JButton btnReconnectFb = new JButton("Kết nối Firebase");
        btnReconnectFb.addActionListener(e -> {
            firebaseService.tryConnect();
            updateFirebaseBadge();
            reloadServicesAndRewards();
            JOptionPane.showMessageDialog(this, firebaseService.getStatusMessage());
        });

        leftBtns.add(btnInsert);
        leftBtns.add(btnQuickVerify);
        leftBtns.add(btnEject);
        leftBtns.add(btnReconnectFb);

        JPanel rightStatus = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        rightStatus.setOpaque(false);
        lblCardStatus = createBadge("THẺ: CHƯA CÓ", new Color(108, 117, 125));
        lblPinStatus = createBadge("PIN: CHƯA XÁC THỰC", new Color(220, 53, 69));
        lblFirebaseStatus = createBadge("FIREBASE: OFFLINE", new Color(255, 152, 0));
        rightStatus.add(lblCardStatus);
        rightStatus.add(lblPinStatus);
        rightStatus.add(lblFirebaseStatus);

        panel.add(leftBtns, BorderLayout.WEST);
        panel.add(rightStatus, BorderLayout.EAST);
        return panel;
    }

    private JPanel buildLeftCardPanel() {
        JPanel wrapper = new JPanel(new BorderLayout(0, 10));
        wrapper.setPreferredSize(new Dimension(280, 0));

        // Thẻ thành viên trực quan
        JPanel cardBox = new JPanel();
        cardBox.setLayout(new BoxLayout(cardBox, BoxLayout.Y_AXIS));
        cardBox.setBackground(new Color(20, 61, 89));
        cardBox.setBorder(new EmptyBorder(16, 16, 16, 16));

        JLabel title = new JLabel("THẺ THÀNH VIÊN SÂN BÓNG");
        title.setForeground(new Color(244, 180, 26));
        title.setFont(new Font("Segoe UI", Font.BOLD, 14));

        lblVisualCardId = makeCardLabel("ID: ---", 13, Font.PLAIN, new Color(200, 220, 240));
        lblVisualHolder = makeCardLabel("Chủ thẻ: (Chưa đọc)", 15, Font.BOLD, Color.WHITE);
        lblVisualTier = makeCardLabel("Hạng: Đồng", 13, Font.BOLD, new Color(130, 224, 170));
        lblVisualBalance = makeCardLabel("Số dư: 0 VND", 18, Font.BOLD, new Color(255, 215, 64));
        lblVisualPoints = makeCardLabel("Điểm tích lũy: 0 điểm", 14, Font.BOLD, Color.WHITE);
        lblVisualSpent = makeCardLabel("Tổng chi tiêu: 0 VND", 12, Font.PLAIN, new Color(200, 220, 240));
        lblVisualTries = makeCardLabel("Số lần thử PIN còn: 3", 12, Font.PLAIN, new Color(255, 180, 180));

        cardBox.add(title);
        cardBox.add(Box.createVerticalStrut(10));
        cardBox.add(lblVisualCardId);
        cardBox.add(Box.createVerticalStrut(6));
        cardBox.add(lblVisualHolder);
        cardBox.add(Box.createVerticalStrut(6));
        cardBox.add(lblVisualTier);
        cardBox.add(Box.createVerticalStrut(14));
        cardBox.add(lblVisualBalance);
        cardBox.add(Box.createVerticalStrut(6));
        cardBox.add(lblVisualPoints);
        cardBox.add(Box.createVerticalStrut(10));
        cardBox.add(lblVisualSpent);
        cardBox.add(Box.createVerticalStrut(4));
        cardBox.add(lblVisualTries);

        JButton btnRefresh = new JButton("Làm mới thông tin thẻ");
        btnRefresh.addActionListener(e -> refreshCardSummary());

        JTextArea guide = new JTextArea(
                "Hướng dẫn demo nhanh:\n"
                + "1. Bấm 'Khởi tạo / Quẹt thẻ ảo'\n"
                + "2. Bấm 'Nhập PIN nhanh' (mặc định: 123456)\n"
                + "3. Tab 1: Ghi thông tin & xem mã hóa AES\n"
                + "4. Tab 2/3/4: Nạp tiền, Thanh toán, Đổi quà\n"
                + "5. Tab 5: Thử sai PIN 3 lần -> Mở bằng PUK (88888888) & Kiểm tra RSA"
        );
        guide.setEditable(false);
        guide.setLineWrap(true);
        guide.setWrapStyleWord(true);
        guide.setBackground(new Color(248, 249, 250));
        guide.setBorder(BorderFactory.createTitledBorder("Ghi chú Demo"));
        guide.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        wrapper.add(cardBox, BorderLayout.NORTH);
        wrapper.add(btnRefresh, BorderLayout.CENTER);
        wrapper.add(guide, BorderLayout.SOUTH);
        return wrapper;
    }

    // =========================================================================
    // TAB 1: THÔNG TIN CÁ NHÂN & MÃ HÓA AES-128
    // =========================================================================

    private JPanel buildTabPersonalInfo() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel searchPanel = new JPanel(new BorderLayout(8, 0));
        searchPanel.setBorder(new TitledBorder("Liên kết tài khoản từ ứng dụng Android (Firestore)"));
        txtSearchFirebaseUser = new JTextField("customer@test.com");
        JButton btnFindUser = new JButton("Tìm user trên Firebase");
        btnFindUser.addActionListener(e -> onFindFirebaseUser());
        searchPanel.add(new JLabel("Email / SĐT / UID: "), BorderLayout.WEST);
        searchPanel.add(txtSearchFirebaseUser, BorderLayout.CENTER);
        searchPanel.add(btnFindUser, BorderLayout.EAST);

        JPanel form = new JPanel(new GridLayout(6, 2, 8, 8));
        form.setBorder(new TitledBorder("Thông tin chủ thẻ (Lưu mã hóa AES-128-CBC trong thẻ)"));
        txtCardId = new JTextField("FC-2026-0001");
        txtFirebaseUid = new JTextField("");
        txtFullName = new JTextField("Nguyễn Văn A");
        txtPhone = new JTextField("0912345678");
        txtDob = new JTextField("15/08/2002");
        txtTeamName = new JTextField("FC Bách Khoa");

        form.add(new JLabel("Mã thẻ (Card ID):")); form.add(txtCardId);
        form.add(new JLabel("Firebase UID (từ Android):")); form.add(txtFirebaseUid);
        form.add(new JLabel("Họ và tên:")); form.add(txtFullName);
        form.add(new JLabel("Số điện thoại:")); form.add(txtPhone);
        form.add(new JLabel("Ngày sinh:")); form.add(txtDob);
        form.add(new JLabel("Đội bóng:")); form.add(txtTeamName);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        JButton btnSave = new JButton("Lưu & Mã hóa AES lên thẻ (+ Đồng bộ Firebase)");
        btnSave.setBackground(new Color(25, 135, 84));
        btnSave.setForeground(Color.BLACK);
        btnSave.addActionListener(e -> onSavePersonalInfo());

        JButton btnRead = new JButton("Đọc & Giải mã từ thẻ");
        btnRead.addActionListener(e -> onReadPersonalInfo());

        JButton btnDump = new JButton("Xem bản mã AES trong EEPROM");
        btnDump.addActionListener(e -> onViewEncryptedDump());

        btnRow.add(btnSave);
        btnRow.add(btnRead);
        btnRow.add(btnDump);

        JPanel topForm = new JPanel(new BorderLayout(6, 6));
        topForm.add(searchPanel, BorderLayout.NORTH);
        topForm.add(form, BorderLayout.CENTER);
        topForm.add(btnRow, BorderLayout.SOUTH);

        txtEncryptedDump = new JTextArea(6, 40);
        txtEncryptedDump.setEditable(false);
        txtEncryptedDump.setLineWrap(true);
        txtEncryptedDump.setFont(new Font("Consolas", Font.PLAIN, 12));
        JScrollPane dumpScroll = new JScrollPane(txtEncryptedDump);
        dumpScroll.setBorder(new TitledBorder("Dữ liệu thực tế lưu trong EEPROM thẻ (Bản mã AES-128 Hex)"));

        root.add(topForm, BorderLayout.NORTH);
        root.add(dumpScroll, BorderLayout.CENTER);
        return root;
    }

    // =========================================================================
    // TAB 2: NẠP TIỀN, THANH TOÁN DỊCH VỤ & LỊCH SỬ TRÊN THẺ
    // =========================================================================

    private JPanel buildTabFinance() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel actionsPanel = new JPanel(new GridLayout(1, 2, 10, 10));

        // Hộp nạp tiền
        JPanel topUpBox = new JPanel(new BorderLayout(6, 6));
        topUpBox.setBorder(new TitledBorder("Nạp tiền vào thẻ"));
        txtTopUpAmount = new JTextField("500000");
        JPanel quickBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        for (int amt : new int[]{100000, 200000, 500000, 1000000}) {
            JButton b = new JButton(vndFormat.format(amt));
            b.addActionListener(e -> txtTopUpAmount.setText(String.valueOf(amt)));
            quickBtns.add(b);
        }
        JButton btnTopUp = new JButton("Xác nhận Nạp tiền");
        btnTopUp.setBackground(new Color(13, 110, 253));
        btnTopUp.setForeground(Color.BLACK);
        btnTopUp.addActionListener(e -> onTopUp());

        JPanel topUpInput = new JPanel(new BorderLayout(6, 0));
        topUpInput.add(new JLabel("Số tiền (VND): "), BorderLayout.WEST);
        topUpInput.add(txtTopUpAmount, BorderLayout.CENTER);
        topUpBox.add(topUpInput, BorderLayout.NORTH);
        topUpBox.add(quickBtns, BorderLayout.CENTER);
        topUpBox.add(btnTopUp, BorderLayout.SOUTH);

        // Hộp thanh toán dịch vụ
        JPanel serviceBox = new JPanel(new BorderLayout(6, 6));
        serviceBox.setBorder(new TitledBorder("Thanh toán dịch vụ sân bóng (collection 'services')"));
        cboServices = new JComboBox<>();
        spnServiceQty = new JSpinner(new SpinnerNumberModel(1, 1, 50, 1));
        JPanel srvCenter = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        srvCenter.add(new JLabel("Số lượng:"));
        srvCenter.add(spnServiceQty);

        JButton btnPayService = new JButton("Thanh toán dịch vụ bằng thẻ");
        btnPayService.setBackground(new Color(220, 53, 69));
        btnPayService.setForeground(Color.BLACK);
        btnPayService.addActionListener(e -> onPayService());

        serviceBox.add(cboServices, BorderLayout.NORTH);
        serviceBox.add(srvCenter, BorderLayout.CENTER);
        serviceBox.add(btnPayService, BorderLayout.SOUTH);

        actionsPanel.add(topUpBox);
        actionsPanel.add(serviceBox);

        // Bảng 5 giao dịch gần nhất trên thẻ
        txTableModel = new DefaultTableModel(new String[]{"Loại giao dịch", "Số tiền (VND)", "Điểm (+/-)", "Thời gian"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        JTable tblTx = new JTable(txTableModel);
        JScrollPane txScroll = new JScrollPane(tblTx);
        txScroll.setBorder(new TitledBorder("Lịch sử 5 giao dịch gần nhất lưu trực tiếp trong EEPROM thẻ"));

        root.add(actionsPanel, BorderLayout.NORTH);
        root.add(txScroll, BorderLayout.CENTER);
        return root;
    }

    // =========================================================================
    // TAB 3: CHECK-IN & THANH TOÁN ĐƠN ĐẶT SÂN TỪ ANDROID
    // =========================================================================

    private JPanel buildTabBookings() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JButton btnLoadBookings = new JButton("Tải đơn đặt sân của chủ thẻ từ Firebase");
        btnLoadBookings.addActionListener(e -> onLoadBookingsFromFirebase());
        JButton btnPaySelectedBooking = new JButton("Quẹt thẻ Thanh toán & Check-in sân (Realtime Android)");
        btnPaySelectedBooking.setBackground(new Color(25, 135, 84));
        btnPaySelectedBooking.setForeground(Color.BLACK);
        btnPaySelectedBooking.addActionListener(e -> onPaySelectedBooking());

        topBar.add(btnLoadBookings);
        topBar.add(btnPaySelectedBooking);

        bookingTableModel = new DefaultTableModel(
                new String[]{"Mã đơn", "Sân bóng", "Ngày", "Khung giờ", "Tổng tiền (VND)", "Trạng thái"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        tblBookings = new JTable(bookingTableModel);
        JScrollPane scroll = new JScrollPane(tblBookings);
        scroll.setBorder(new TitledBorder("Đơn đặt sân chưa thanh toán trên Firestore"));

        // Thanh toán thủ công cho Offline Demo
        JPanel manualPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        manualPanel.setBorder(new TitledBorder("Hoặc thanh toán tiền sân nhanh (Offline Demo)"));
        txtManualBookingAmount = new JTextField("350000", 12);
        JButton btnPayManual = new JButton("Thanh toán tiền sân ngay");
        btnPayManual.addActionListener(e -> onPayManualPitch());
        manualPanel.add(new JLabel("Tiền sân (VND):"));
        manualPanel.add(txtManualBookingAmount);
        manualPanel.add(btnPayManual);

        root.add(topBar, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(manualPanel, BorderLayout.SOUTH);
        return root;
    }

    // =========================================================================
    // TAB 4: TÍCH ĐIỂM & ĐỔI QUÀ
    // =========================================================================

    private JPanel buildTabRewards() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));

        rewardListModel = new DefaultListModel<>();
        lstRewards = new JList<>(rewardListModel);
        lstRewards.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        lstRewards.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scroll = new JScrollPane(lstRewards);
        scroll.setBorder(new TitledBorder("Danh mục quà tặng đổi điểm"));

        JButton btnRedeem = new JButton("Đổi quà bằng điểm trên thẻ");
        btnRedeem.setBackground(new Color(111, 66, 193));
        btnRedeem.setForeground(Color.BLACK);
        btnRedeem.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnRedeem.addActionListener(e -> onRedeemReward());

        root.add(scroll, BorderLayout.CENTER);
        root.add(btnRedeem, BorderLayout.SOUTH);
        return root;
    }

    // =========================================================================
    // TAB 5: BẢO MẬT (PIN / PUK / KHÓA THẺ / CHỮ KÝ SỐ RSA)
    // =========================================================================

    private JPanel buildTabSecurity() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel grid = new JPanel(new GridLayout(1, 3, 10, 10));

        // Hộp 1: Xác thực & Đổi PIN
        JPanel pinBox = new JPanel(new GridLayout(5, 1, 6, 6));
        pinBox.setBorder(new TitledBorder("Xác thực & Đổi PIN"));
        txtPinVerify = new JPasswordField("123456");
        JButton btnVerify = new JButton("Xác thực PIN");
        btnVerify.addActionListener(e -> doVerifyPin(new String(txtPinVerify.getPassword())));
        txtNewPin = new JPasswordField();
        JButton btnChangePin = new JButton("Đổi sang PIN mới");
        btnChangePin.addActionListener(e -> onChangePin());
        pinBox.add(labeled("PIN hiện tại (mặc định 123456):", txtPinVerify));
        pinBox.add(btnVerify);
        pinBox.add(labeled("PIN mới (4-8 ký tự):", txtNewPin));
        pinBox.add(btnChangePin);

        // Hộp 2: Mở khóa PUK & Khóa khẩn cấp
        JPanel pukBox = new JPanel(new GridLayout(5, 1, 6, 6));
        pukBox.setBorder(new TitledBorder("Mở khóa PUK & Khóa khẩn cấp"));
        txtPuk = new JPasswordField("88888888");
        txtResetPin = new JPasswordField("123456");
        JButton btnUnblock = new JButton("Mở khóa thẻ bằng PUK");
        btnUnblock.addActionListener(e -> onUnblockWithPuk());
        JButton btnLock = new JButton("Khóa thẻ khẩn cấp (Báo mất)");
        btnLock.setBackground(new Color(220, 53, 69));
        btnLock.setForeground(Color.BLACK);
        btnLock.addActionListener(e -> onLockCard());
        pukBox.add(labeled("Mã PUK Admin (mặc định 88888888):", txtPuk));
        pukBox.add(labeled("PIN đặt lại:", txtResetPin));
        pukBox.add(btnUnblock);
        pukBox.add(btnLock);

        // Hộp 3: Xác thực RSA
        JPanel rsaBox = new JPanel(new GridLayout(3, 1, 6, 6));
        rsaBox.setBorder(new TitledBorder("Xác thực thẻ thật (RSA-1024)"));
        JButton btnVerifyRsa = new JButton("Chạy Challenge-Response RSA");
        btnVerifyRsa.setBackground(new Color(13, 110, 253));
        btnVerifyRsa.setForeground(Color.BLACK);
        btnVerifyRsa.addActionListener(e -> onVerifyRsaAuthenticity());
        JButton btnShowPubKey = new JButton("Xuất RSA Public Key (Base64)");
        btnShowPubKey.addActionListener(e -> onShowRsaPublicKey());
        JButton btnCheckCloudLost = new JButton("Kiểm tra báo mất từ Android");
        btnCheckCloudLost.addActionListener(e -> onCheckCloudCardStatus());
        rsaBox.add(btnVerifyRsa);
        rsaBox.add(btnShowPubKey);
        rsaBox.add(btnCheckCloudLost);

        grid.add(pinBox);
        grid.add(pukBox);
        grid.add(rsaBox);

        txtRsaOutput = new JTextArea(6, 40);
        txtRsaOutput.setEditable(false);
        txtRsaOutput.setLineWrap(true);
        txtRsaOutput.setFont(new Font("Consolas", Font.PLAIN, 12));
        JScrollPane rsaScroll = new JScrollPane(txtRsaOutput);
        rsaScroll.setBorder(new TitledBorder("Kết quả kiểm tra bảo mật / RSA Public Key"));

        root.add(grid, BorderLayout.NORTH);
        root.add(rsaScroll, BorderLayout.CENTER);
        return root;
    }

    private JPanel buildBottomApduLog() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Live APDU Console (Giao tiếp thực tế với jCardSim)"));
        txtApduLog = new JTextArea(6, 80);
        txtApduLog.setEditable(false);
        txtApduLog.setFont(new Font("Consolas", Font.PLAIN, 11));
        txtApduLog.setBackground(new Color(24, 28, 36));
        txtApduLog.setForeground(new Color(144, 238, 144));

        JButton btnClearLog = new JButton("Xóa Log");
        btnClearLog.addActionListener(e -> txtApduLog.setText(""));
        panel.add(new JScrollPane(txtApduLog), BorderLayout.CENTER);
        panel.add(btnClearLog, BorderLayout.EAST);
        return panel;
    }

    // =========================================================================
    // XỬ LÝ SỰ KIỆN
    // =========================================================================

    private void onInsertCard() {
        cardService.insertNewSimulatedCard();
        updateCardAndPinBadges();
        refreshCardSummary();
        JOptionPane.showMessageDialog(this,
                "Đã khởi tạo thẻ ảo jCardSim thành công!\nMã PIN mặc định: 123456\nMã PUK mặc định: 88888888",
                "Quẹt thẻ thành công", JOptionPane.INFORMATION_MESSAGE);
    }

    private void onEjectCard() {
        cardService.ejectCard();
        currentMember = new CardModels.MemberInfo();
        updateCardAndPinBadges();
        lblVisualCardId.setText("ID: ---");
        lblVisualHolder.setText("Chủ thẻ: (Chưa đọc)");
        lblVisualBalance.setText("Số dư: 0 VND");
        lblVisualPoints.setText("Điểm tích lũy: 0 điểm");
        txTableModel.setRowCount(0);
    }

    private void onQuickVerifyPin() {
        JPasswordField pf = new JPasswordField("123456");
        int ok = JOptionPane.showConfirmDialog(this, pf, "Nhập mã PIN (mặc định: 123456)",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok == JOptionPane.OK_OPTION) {
            doVerifyPin(new String(pf.getPassword()));
        }
    }

    private void doVerifyPin(String pin) {
        try {
            cardService.verifyPin(pin);
            updateCardAndPinBadges();
            refreshCardSummary();
            JOptionPane.showMessageDialog(this, "Xác thực mã PIN thành công!", "Thành công", JOptionPane.INFORMATION_MESSAGE);
        } catch (SmartCardService.CardException ex) {
            updateCardAndPinBadges();
            refreshCardSummary();
            showError(ex.getMessage());
        }
    }

    private void onFindFirebaseUser() {
        if (!firebaseService.isConnected()) {
            showError("Chưa kết nối Firebase! Hãy đặt file serviceAccountKey.json vào D:\\SmartCard\\");
            return;
        }
        try {
            Map<String, Object> user = firebaseService.findUserByEmailOrPhone(txtSearchFirebaseUser.getText());
            if (user == null) {
                showError("Không tìm thấy user này trên Firestore.");
                return;
            }
            txtFirebaseUid.setText(String.valueOf(user.getOrDefault("uid", "")));
            txtFullName.setText(String.valueOf(user.getOrDefault("displayName", "")));
            txtPhone.setText(String.valueOf(user.getOrDefault("phoneNumber", "")));
            if (user.get("cardId") != null && !String.valueOf(user.get("cardId")).isEmpty()) {
                txtCardId.setText(String.valueOf(user.get("cardId")));
            }
            JOptionPane.showMessageDialog(this, "Đã tải thông tin user: " + txtFullName.getText());
        } catch (Exception ex) {
            showError("Lỗi tìm user: " + ex.getMessage());
        }
    }

    private void onSavePersonalInfo() {
        try {
            CardModels.MemberInfo info = new CardModels.MemberInfo(
                    txtCardId.getText(),
                    txtFirebaseUid.getText(),
                    txtFullName.getText(),
                    txtPhone.getText(),
                    txtDob.getText(),
                    txtTeamName.getText()
            );
            cardService.savePersonalInfo(info);
            currentMember = info;
            lblVisualCardId.setText("ID: " + info.cardId);
            lblVisualHolder.setText("Chủ thẻ: " + info.fullName);

            txtEncryptedDump.setText(cardService.readEncryptedPersonalInfoHex());

            syncCardStateToFirebase(true);
            refreshCardSummary();
            JOptionPane.showMessageDialog(this, "Đã mã hóa AES-128 và lưu thông tin lên thẻ thành công!");
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    private void onReadPersonalInfo() {
        try {
            CardModels.MemberInfo info = cardService.readPersonalInfo();
            currentMember = info;
            txtCardId.setText(info.cardId);
            txtFirebaseUid.setText(info.firebaseUid);
            txtFullName.setText(info.fullName);
            txtPhone.setText(info.phoneNumber);
            txtDob.setText(info.dateOfBirth);
            txtTeamName.setText(info.teamName);
            lblVisualCardId.setText("ID: " + info.cardId);
            lblVisualHolder.setText("Chủ thẻ: " + info.fullName);
            JOptionPane.showMessageDialog(this, "Đã giải mã AES và đọc thông tin chủ thẻ thành công!");
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    private void onViewEncryptedDump() {
        try {
            String hex = cardService.readEncryptedPersonalInfoHex();
            txtEncryptedDump.setText(hex);
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    private void onTopUp() {
        try {
            int amount = Integer.parseInt(txtTopUpAmount.getText().trim());
            cardService.topUp(amount);
            syncCardStateToFirebase(false);
            firebaseService.recordCardTransaction(
                    currentMember.firebaseUid, currentMember.cardId,
                    "topup", amount, 0, "Nạp tiền tại quầy POS", "", ""
            );
            refreshCardSummary();
            JOptionPane.showMessageDialog(this, "Nạp thành công " + vndFormat.format(amount) + " VND vào thẻ!");
        } catch (NumberFormatException nfe) {
            showError("Vui lòng nhập số tiền hợp lệ!");
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    private void onPayService() {
        CardModels.ServiceItem srv = (CardModels.ServiceItem) cboServices.getSelectedItem();
        if (srv == null) return;
        int qty = (Integer) spnServiceQty.getValue();
        int total = (int) Math.round(srv.price * qty);
        try {
            CardModels.PayResult res = cardService.pay(total, CardConstants.TX_PAY_SERVICE, srv.serviceId);
            syncCardStateToFirebase(false);
            firebaseService.recordCardTransaction(
                    currentMember.firebaseUid, currentMember.cardId,
                    "pay_service", total, res.earnedPoints,
                    "Mua dịch vụ: " + srv.name + " x" + qty, "", res.rsaSignatureBase64
            );
            refreshCardSummary();
            JOptionPane.showMessageDialog(this, String.format(
                    "Thanh toán dịch vụ thành công!\nSố tiền: %s VND\nĐiểm cộng thêm: +%d điểm\nSố dư mới: %s VND",
                    vndFormat.format(total), res.earnedPoints, vndFormat.format(res.newBalance)));
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    private void onLoadBookingsFromFirebase() {
        if (!firebaseService.isConnected()) {
            showError("Đang ở chế độ Offline Demo. Bạn có thể dùng nút 'Thanh toán tiền sân ngay' bên dưới.");
            return;
        }
        String uid = txtFirebaseUid.getText().trim();
        if (uid.isEmpty()) {
            showError("Chưa có Firebase UID! Hãy tìm user ở Tab 1 hoặc đọc thông tin thẻ trước.");
            return;
        }
        try {
            loadedBookings = firebaseService.fetchUnpaidBookingsForUser(uid);
            bookingTableModel.setRowCount(0);
            for (CardModels.BookingItem b : loadedBookings) {
                bookingTableModel.addRow(new Object[]{
                        b.bookingId, b.pitchName, b.bookingDate,
                        b.startTime + " - " + b.endTime,
                        vndFormat.format(b.totalAmount),
                        b.bookingStatus + " / " + b.paymentStatus
                });
            }
            if (loadedBookings.isEmpty()) {
                JOptionPane.showMessageDialog(this, "User này không có đơn đặt sân nào chưa thanh toán.");
            }
        } catch (Exception ex) {
            showError("Lỗi tải đơn đặt sân: " + ex.getMessage());
        }
    }

    private void onPaySelectedBooking() {
        int row = tblBookings.getSelectedRow();
        if (row < 0 || row >= loadedBookings.size()) {
            showError("Hãy chọn 1 đơn đặt sân trong bảng trước!");
            return;
        }
        CardModels.BookingItem b = loadedBookings.get(row);
        int amount = (int) Math.round(b.totalAmount);
        try {
            if ("lost".equalsIgnoreCase(firebaseService.checkCardStatusOnCloud(currentMember.firebaseUid))) {
                cardService.lockCardEmergency();
                updateCardAndPinBadges();
                refreshCardSummary();
                showError("CẢNH BÁO: Thẻ này đã bị chủ thẻ BÁO MẤT trên app Android! Hệ thống đã tự động KHÓA thẻ.");
                return;
            }
            CardModels.PayResult res = cardService.pay(amount, CardConstants.TX_PAY_BOOKING, b.bookingId);
            firebaseService.markBookingPaidAndCheckedIn(b.bookingId, currentMember.cardId, res.rsaSignatureBase64);
            syncCardStateToFirebase(false);
            firebaseService.recordCardTransaction(
                    currentMember.firebaseUid, currentMember.cardId,
                    "pay_booking", amount, res.earnedPoints,
                    "Check-in & Thanh toán sân: " + b.pitchName, b.bookingId, res.rsaSignatureBase64
            );
            refreshCardSummary();
            onLoadBookingsFromFirebase();
            JOptionPane.showMessageDialog(this, String.format(
                    "CHECK-IN & THANH TOÁN THÀNH CÔNG!\nSân: %s\nSố tiền: %s VND\nĐiểm tích lũy: +%d điểm\nApp Android đã được cập nhật Realtime!",
                    b.pitchName, vndFormat.format(amount), res.earnedPoints));
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    private void onPayManualPitch() {
        try {
            int amount = Integer.parseInt(txtManualBookingAmount.getText().trim());
            CardModels.PayResult res = cardService.pay(amount, CardConstants.TX_PAY_BOOKING, "LOCAL-BOOKING");
            syncCardStateToFirebase(false);
            refreshCardSummary();
            JOptionPane.showMessageDialog(this, String.format(
                    "Thanh toán tiền sân thành công!\nSố tiền: %s VND\nĐiểm cộng thêm: +%d điểm\nSố dư mới: %s VND",
                    vndFormat.format(amount), res.earnedPoints, vndFormat.format(res.newBalance)));
        } catch (NumberFormatException nfe) {
            showError("Số tiền không hợp lệ!");
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    private void onRedeemReward() {
        CardModels.RewardItem reward = lstRewards.getSelectedValue();
        if (reward == null) {
            showError("Vui lòng chọn 1 phần quà để đổi!");
            return;
        }
        try {
            int remaining = cardService.redeemReward(reward.pointsCost);
            syncCardStateToFirebase(false);
            firebaseService.recordCardTransaction(
                    currentMember.firebaseUid, currentMember.cardId,
                    "redeem_reward", 0, -reward.pointsCost,
                    "Đổi quà: " + reward.name, "", ""
            );
            refreshCardSummary();
            JOptionPane.showMessageDialog(this, String.format(
                    "Đổi quà '%s' thành công!\nTrừ: %d điểm\nĐiểm còn lại: %d điểm",
                    reward.name, reward.pointsCost, remaining));
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    private void onChangePin() {
        try {
            cardService.changePin(new String(txtNewPin.getPassword()));
            txtNewPin.setText("");
            JOptionPane.showMessageDialog(this, "Đổi mã PIN thành công!");
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    private void onUnblockWithPuk() {
        try {
            cardService.unblockPinWithPuk(new String(txtPuk.getPassword()), new String(txtResetPin.getPassword()));
            updateCardAndPinBadges();
            refreshCardSummary();
            syncCardStateToFirebase(false);
            JOptionPane.showMessageDialog(this, "Mở khóa thẻ & đặt lại PIN thành công! Hãy xác thực lại bằng PIN mới.");
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    private void onLockCard() {
        try {
            cardService.lockCardEmergency();
            updateCardAndPinBadges();
            refreshCardSummary();
            syncCardStateToFirebase(false);
            JOptionPane.showMessageDialog(this, "Đã KHÓA thẻ khẩn cấp thành công!");
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    private void onVerifyRsaAuthenticity() {
        try {
            boolean ok = cardService.verifyCardAuthenticity();
            txtRsaOutput.setText(ok
                    ? "[OK] XÁC THỰC RSA-1024 THÀNH CÔNG!\nThẻ đã ký Challenge bằng Private Key nội bộ và được xác minh hợp lệ bằng Public Key -> THẺ CHÍNH HÃNG, KHÔNG BỊ GIẢ MẠO."
                    : "[FAIL] Chữ ký RSA không hợp lệ!");
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    private void onShowRsaPublicKey() {
        try {
            String pub = cardService.getCardRsaPublicKeyBase64();
            txtRsaOutput.setText("RSA-1024 Public Key (Base64 X.509):\n" + pub);
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    private void onCheckCloudCardStatus() {
        try {
            String st = firebaseService.checkCardStatusOnCloud(txtFirebaseUid.getText().trim());
            txtRsaOutput.setText("Trạng thái thẻ trên Firestore (users.cardStatus): " + st);
            if ("lost".equalsIgnoreCase(st)) {
                cardService.lockCardEmergency();
                updateCardAndPinBadges();
                refreshCardSummary();
                txtRsaOutput.append("\n-> Phát hiện báo mất từ Android! Đã tự động khóa thẻ.");
            }
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    // =========================================================================
    // CẬP NHẬT GIAO DIỆN & TIỆN ÍCH
    // =========================================================================

    private void refreshCardSummary() {
        if (!cardService.isCardInserted()) return;
        try {
            CardModels.CardSummary s = cardService.getSummary();
            lblVisualBalance.setText("Số dư: " + vndFormat.format(s.balance) + " VND");
            lblVisualPoints.setText("Điểm tích lũy: " + s.loyaltyPoints + " điểm");
            lblVisualSpent.setText("Tổng chi tiêu: " + vndFormat.format(s.totalSpent) + " VND");
            lblVisualTier.setText("Hạng: " + s.getTierName());
            lblVisualTries.setText("Số lần thử PIN còn: " + s.pinTriesRemaining + (s.locked ? " (ĐÃ KHÓA)" : ""));

            if (s.locked) {
                lblPinStatus.setText("THẺ: ĐÃ BỊ KHÓA");
                lblPinStatus.setBackground(new Color(180, 30, 40));
            }

            List<CardModels.CardTxRecord> history = cardService.getOnCardTxHistory();
            txTableModel.setRowCount(0);
            for (CardModels.CardTxRecord r : history) {
                txTableModel.addRow(new Object[]{
                        r.getTypeLabel(),
                        r.amount > 0 ? vndFormat.format(r.amount) : "-",
                        r.pointsDelta > 0 ? ("+" + r.pointsDelta) : (r.pointsDelta < 0 ? String.valueOf(r.pointsDelta) : "0"),
                        r.getFormattedTime()
                });
            }
        } catch (Exception ignored) {
        }
    }

    private void syncCardStateToFirebase(boolean includePubKey) {
        if (!firebaseService.isConnected()) return;
        String uid = currentMember.firebaseUid;
        if (uid == null || uid.isEmpty()) {
            uid = txtFirebaseUid.getText().trim();
        }
        if (uid.isEmpty()) return;
        try {
            CardModels.CardSummary summary = cardService.getSummary();
            String pub = includePubKey ? cardService.getCardRsaPublicKeyBase64() : null;
            firebaseService.syncCardToUser(uid, txtCardId.getText().trim(), summary, pub);
        } catch (Exception ex) {
            appendApduLog("[LỖI ĐỒNG BỘ FIREBASE] " + ex.getMessage());
        }
    }

    private void reloadServicesAndRewards() {
        cboServices.removeAllItems();
        for (CardModels.ServiceItem s : firebaseService.fetchServices()) {
            cboServices.addItem(s);
        }
        rewardListModel.clear();
        for (CardModels.RewardItem r : firebaseService.fetchRewards()) {
            rewardListModel.addElement(r);
        }
    }

    private void updateCardAndPinBadges() {
        if (cardService.isCardInserted()) {
            lblCardStatus.setText("THẺ: ĐÃ KẾT NỐI");
            lblCardStatus.setBackground(new Color(25, 135, 84));
        } else {
            lblCardStatus.setText("THẺ: CHƯA CÓ");
            lblCardStatus.setBackground(new Color(108, 117, 125));
        }

        if (cardService.isPinVerifiedSession()) {
            lblPinStatus.setText("PIN: ĐÃ XÁC THỰC");
            lblPinStatus.setBackground(new Color(25, 135, 84));
        } else {
            lblPinStatus.setText("PIN: CHƯA XÁC THỰC");
            lblPinStatus.setBackground(new Color(220, 53, 69));
        }
    }

    private void updateFirebaseBadge() {
        if (firebaseService.isConnected()) {
            lblFirebaseStatus.setText("FIREBASE: ONLINE");
            lblFirebaseStatus.setBackground(new Color(25, 135, 84));
        } else {
            lblFirebaseStatus.setText("FIREBASE: OFFLINE DEMO");
            lblFirebaseStatus.setBackground(new Color(230, 126, 34));
        }
    }

    private void appendApduLog(String line) {
        SwingUtilities.invokeLater(() -> {
            txtApduLog.append(line + "\n");
            txtApduLog.setCaretPosition(txtApduLog.getDocument().getLength());
        });
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Thông báo", JOptionPane.ERROR_MESSAGE);
    }

    private static JLabel createBadge(String text, Color bg) {
        JLabel lbl = new JLabel(text);
        lbl.setOpaque(true);
        lbl.setBackground(bg);
        lbl.setForeground(Color.WHITE);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lbl.setBorder(new EmptyBorder(4, 8, 4, 8));
        return lbl;
    }

    private static JLabel makeCardLabel(String text, int size, int style, Color color) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", style, size));
        lbl.setForeground(color);
        return lbl;
    }

    private static JPanel labeled(String title, JComponent field) {
        JPanel p = new JPanel(new BorderLayout(4, 2));
        p.add(new JLabel(title), BorderLayout.NORTH);
        p.add(field, BorderLayout.CENTER);
        return p;
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}
