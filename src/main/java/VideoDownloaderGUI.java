import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class VideoDownloaderGUI extends JFrame {

    private JTextArea txtUrls; // Thay đổi: Nhập nhiều dòng
    private JTextField txtBaseName; // Thay đổi: Tên chung (prefix)
    private JTextField txtSaveDir;
    private JButton btnBrowse;
    private JButton btnDownload;
    private JTextArea txtLog;
    private JProgressBar progressBar;
    private JLabel lblStatus; // Hiển thị đang tải bài mấy

    private String ffmpegPath;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                // Set Look and Feel cho giống Windows native
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                VideoDownloaderGUI frame = new VideoDownloaderGUI();
                frame.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public VideoDownloaderGUI() {
        setTitle("Tool Tải Video HLS Hàng Loạt - Dev by Tan");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setBounds(100, 100, 700, 600);
        setLocationRelativeTo(null);

        JPanel contentPane = new JPanel();
        contentPane.setBorder(new EmptyBorder(10, 10, 10, 10));
        contentPane.setLayout(new BorderLayout(0, 10));
        setContentPane(contentPane);

        // --- INPUT PANEL ---
        JPanel panelTop = new JPanel(new BorderLayout(0, 10));
        contentPane.add(panelTop, BorderLayout.NORTH);

        // 1. Khu vực nhập Link (TextArea)
        JPanel pnlUrl = new JPanel(new BorderLayout(5, 5));
        pnlUrl.add(new JLabel("Danh sách Link M3U8 (Mỗi link 1 dòng):"), BorderLayout.NORTH);
        txtUrls = new JTextArea(8, 50); // Cho phép nhập 8 dòng
        pnlUrl.add(new JScrollPane(txtUrls), BorderLayout.CENTER);
        panelTop.add(pnlUrl, BorderLayout.NORTH);

        // 2. Khu vực cấu hình (Grid)
        JPanel panelConfig = new JPanel(new GridLayout(2, 1, 0, 5));

        // Tên file gốc
        JPanel pnlName = new JPanel(new BorderLayout(10, 0));
        pnlName.add(new JLabel("Tên bài học (Prefix): "), BorderLayout.WEST);
        txtBaseName = new JTextField("Bai_Hoc"); // Mặc định
        txtBaseName.setToolTipText("Ví dụ nhập 'Java' -> Sẽ lưu thành Java_1.mp4, Java_2.mp4...");
        pnlName.add(txtBaseName, BorderLayout.CENTER);
        panelConfig.add(pnlName);

        // Thư mục lưu
        JPanel pnlDir = new JPanel(new BorderLayout(10, 0));
        pnlDir.add(new JLabel("Lưu tại thư mục:      "), BorderLayout.WEST);
        txtSaveDir = new JTextField(System.getProperty("user.dir"));
        btnBrowse = new JButton("Chọn...");
        pnlDir.add(txtSaveDir, BorderLayout.CENTER);
        pnlDir.add(btnBrowse, BorderLayout.EAST);
        panelConfig.add(pnlDir);

        panelTop.add(panelConfig, BorderLayout.CENTER);

        // --- CENTER LOG ---
        txtLog = new JTextArea();
        txtLog.setEditable(false);
        txtLog.setBackground(Color.BLACK);
        txtLog.setForeground(Color.GREEN);
        txtLog.setFont(new Font("Consolas", Font.PLAIN, 12));
        // Tự động scroll xuống dưới
        DefaultCaret caret = (DefaultCaret)txtLog.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        contentPane.add(new JScrollPane(txtLog), BorderLayout.CENTER);

        // --- BOTTOM ACTION ---
        JPanel panelBottom = new JPanel(new BorderLayout(0, 5));

        lblStatus = new JLabel("Sẵn sàng.");
        lblStatus.setFont(new Font("Segoe UI", Font.BOLD, 12));
        panelBottom.add(lblStatus, BorderLayout.NORTH);

        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        panelBottom.add(progressBar, BorderLayout.CENTER);

        btnDownload = new JButton("BẮT ĐẦU TẢI TẤT CẢ");
        btnDownload.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnDownload.setPreferredSize(new Dimension(100, 40));
        panelBottom.add(btnDownload, BorderLayout.SOUTH);

        contentPane.add(panelBottom, BorderLayout.SOUTH);

        // --- EVENTS ---
        btnBrowse.addActionListener(e -> chooseDirectory());
        btnDownload.addActionListener(e -> startBatchDownload());

        // Init FFmpeg background
        new Thread(this::initFFmpeg).start();
    }

    private void chooseDirectory() {
        JFileChooser fc = new JFileChooser(txtSaveDir.getText());
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            txtSaveDir.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    private void startBatchDownload() {
        String rawText = txtUrls.getText().trim();
        if (rawText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Chưa nhập link nào cả!");
            return;
        }

        String baseName = txtBaseName.getText().trim();
        if (baseName.isEmpty()) baseName = "Video";

        // Tách dòng và lọc dòng trống
        String[] lines = rawText.split("\\n");
        List<String> validLinks = new ArrayList<>();
        for (String line : lines) {
            if (!line.trim().isEmpty()) validLinks.add(line.trim());
        }

        if (validLinks.isEmpty()) return;

        // UI Setup
        btnDownload.setEnabled(false);
        txtUrls.setEditable(false);
        txtLog.setText("");
        progressBar.setValue(0);
        progressBar.setMaximum(validLinks.size());

        File saveDir = new File(txtSaveDir.getText());
        String finalBaseName = baseName;

        // Chạy Thread riêng để không đơ UI
        new Thread(() -> {
            int count = 0;
            int total = validLinks.size();

            log("=== BẮT ĐẦU TẢI " + total + " FILES ===");

            for (int i = 0; i < total; i++) {
                String url = validLinks.get(i);
                // Tạo tên file: Ten_1.mp4, Ten_2.mp4
                String fileName = finalBaseName + "_" + (i + 1) + ".mp4";
                File outputFile = new File(saveDir, fileName);

                // Update UI status
                int currentIdx = i + 1;
                SwingUtilities.invokeLater(() -> {
                    lblStatus.setText("Đang tải file " + currentIdx + "/" + total + ": " + fileName);
                    progressBar.setValue(currentIdx - 1); // Set giá trị cũ
                });

                log("\n>>> [" + currentIdx + "/" + total + "] Processing: " + fileName);

                // Gọi hàm tải đồng bộ (Chờ tải xong mới đi tiếp)
                boolean success = runFFmpegSync(url, outputFile.getAbsolutePath());

                if (success) {
                    log("✅ Đã xong file " + currentIdx);
                } else {
                    log("❌ Lỗi file " + currentIdx);
                }

                SwingUtilities.invokeLater(() -> progressBar.setValue(currentIdx));
            }

            SwingUtilities.invokeLater(() -> {
                lblStatus.setText("Hoàn tất chiến dịch!");
                JOptionPane.showMessageDialog(this, "Đã tải xong toàn bộ danh sách!");
                btnDownload.setEnabled(true);
                txtUrls.setEditable(true);
            });

        }).start();
    }

    // Hàm tải đồng bộ (Synchronous)
    private boolean runFFmpegSync(String url, String outputPath) {
        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-i");
        command.add(url);
        command.add("-c");
        command.add("copy");
        command.add("-bsf:a");
        command.add("aac_adtstoasc");
        command.add("-y"); // Overwrite
        command.add(outputPath);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        try {
            Process process = pb.start();
            // Đọc log
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Chỉ in ra log nếu cần thiết (để đỡ spam log)
                    // Hoặc in dòng thời gian để biết nó đang chạy
                    if (line.contains("time=") || line.contains("Opening")) {
                        String finalLine = line;
                        SwingUtilities.invokeLater(() -> txtLog.append(finalLine + "\n"));
                    }
                }
            }
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            log("Exception: " + e.getMessage());
            return false;
        }
    }

    private void initFFmpeg() {
        try {
            ffmpegPath = loadFFmpegFromJar();
            log("Engine loaded: " + ffmpegPath);
        } catch (IOException e) {
            log("Lỗi engine: " + e.getMessage());
        }
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> txtLog.append(msg + "\n"));
    }

    private String loadFFmpegFromJar() throws IOException {
        String os = System.getProperty("os.name").toLowerCase();
        String binaryName = os.contains("win") ? "ffmpeg.exe" : "ffmpeg";
        File tempFile = new File(System.getProperty("java.io.tmpdir"), "ffmpeg_" + System.currentTimeMillis() + (os.contains("win") ? ".exe" : ""));
        tempFile.deleteOnExit();

        try (InputStream in = getClass().getResourceAsStream("/" + binaryName)) {
            // Fallback: Nếu không tìm thấy trong resources thì tìm ở thư mục chạy
            if (in == null) {
                File localFile = new File(binaryName);
                if (localFile.exists()) return localFile.getAbsolutePath();
                throw new FileNotFoundException("Không tìm thấy " + binaryName + " trong resources hoặc thư mục gốc!");
            }
            Files.copy(in, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        tempFile.setExecutable(true);
        return tempFile.getAbsolutePath();
    }
}