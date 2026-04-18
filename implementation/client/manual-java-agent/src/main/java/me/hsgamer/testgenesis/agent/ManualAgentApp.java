package me.hsgamer.testgenesis.agent;

import me.hsgamer.testgenesis.client.Agent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.concurrent.CompletableFuture;

/**
 * Main application for the Manual Testing Agent.
 * Provides a Swing UI to guide users through manual test steps.
 */
public class ManualAgentApp extends JFrame {
    private static final Logger logger = LoggerFactory.getLogger(ManualAgentApp.class);
    
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel container = new JPanel(cardLayout);
    
    private final JPanel loginPanel;
    private final JPanel idlePanel;
    private final JLabel idleStatusLabel = new JLabel("● CONNECTED", SwingConstants.CENTER);
    private final JLabel idleHubLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel idleAgentLabel = new JLabel("", SwingConstants.CENTER);

    private Agent agent;

    public ManualAgentApp() {
        setTitle("TestGenesis Manual Agent");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(600, 450);
        setMinimumSize(new Dimension(400, 300));
        setLocationRelativeTo(null);
        
        loginPanel = createLoginPanel();
        idlePanel = createIdlePanel();
        
        container.add(loginPanel, "LOGIN");
        container.add(idlePanel, "IDLE");
        
        add(container);
    }

    private JPanel createLoginPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(40, 40, 40, 40));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.weightx = 1.0;

        JLabel title = new JLabel("Agent Registration", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 24));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        panel.add(title, gbc);

        gbc.gridwidth = 1;
        gbc.gridy = 1;
        panel.add(new JLabel("Hub URL:"), gbc);
        
        JTextField urlField = new JTextField("http://localhost:9000", 20);
        gbc.gridx = 1;
        panel.add(urlField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        panel.add(new JLabel("Agent Name:"), gbc);
        
        JTextField nameField = new JTextField("ManualAgent-" + (int)(Math.random() * 1000), 20);
        gbc.gridx = 1;
        panel.add(nameField, gbc);

        JButton connectBtn = new JButton("Connect to Hub");
        connectBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        connectBtn.setPreferredSize(new Dimension(0, 40));
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(30, 10, 10, 10);
        panel.add(connectBtn, gbc);

        connectBtn.addActionListener(e -> {
            String url = urlField.getText().trim();
            String name = nameField.getText().trim();
            if (url.isEmpty() || name.isEmpty()) return;
            
            connectBtn.setEnabled(false);
            connectBtn.setText("Connecting...");
            
            new Thread(() -> {
                try {
                    agent = new Agent(url, name);
                    agent.registerTestProcessor(new ManualTestProcessor(this));
                    
                    // Show idle screen before starting connection loop
                    SwingUtilities.invokeLater(() -> {
                        idleHubLabel.setText("Hub: " + url);
                        idleAgentLabel.setText("Agent: " + name);
                        showIdle();
                    });
                    
                    agent.start();
                } catch (Exception ex) {
                    logger.error("Connection failed", ex);
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this, "Failed to connect: " + ex.getMessage(), "Connection Error", JOptionPane.ERROR_MESSAGE);
                        connectBtn.setEnabled(true);
                        connectBtn.setText("Connect to Hub");
                        showLogin();
                    });
                }
            }).start();
        });

        return panel;
    }

    private JPanel createIdlePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(new Color(240, 244, 248));
        
        idleStatusLabel.setForeground(new Color(35, 134, 54));
        idleStatusLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        
        JLabel mainLabel = new JLabel("Waiting for Manual Tests", SwingConstants.CENTER);
        mainLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        
        idleHubLabel.setForeground(new Color(100, 116, 139));
        idleAgentLabel.setForeground(new Color(100, 116, 139));
        idleAgentLabel.setFont(new Font("SansSerif", Font.BOLD, 14));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(idleStatusLabel, gbc);
        
        gbc.gridy = 1;
        gbc.insets = new Insets(10, 0, 0, 0);
        panel.add(mainLabel, gbc);

        gbc.gridy = 2;
        gbc.insets = new Insets(20, 0, 0, 0);
        panel.add(idleHubLabel, gbc);
        
        gbc.gridy = 3;
        gbc.insets = new Insets(5, 0, 0, 0);
        panel.add(idleAgentLabel, gbc);
        
        return panel;
    }

    public void showLogin() {
        cardLayout.show(container, "LOGIN");
        setTitle("TestGenesis Manual Agent");
    }

    public void showIdle() {
        cardLayout.show(container, "IDLE");
        setTitle("TestGenesis Manual Agent [IDLE]");
    }

    public void bringToFront() {
        SwingUtilities.invokeLater(() -> {
            if (!isVisible()) {
                setVisible(true);
            }
            if (getExtendedState() == JFrame.ICONIFIED) {
                setExtendedState(JFrame.NORMAL);
            }
            toFront();
            requestFocus();
        });
    }

    public void showAcknowledgePrompt(String sessionId, CompletableFuture<Boolean> future) {
        bringToFront();
        setTitle("TestGenesis Manual Agent [PENDING]");
        
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(new Color(240, 244, 248));
        panel.setBorder(new EmptyBorder(40, 40, 40, 40));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        JLabel titleLabel = new JLabel("New Test Session", SwingConstants.CENTER);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 24));
        panel.add(titleLabel, gbc);
        
        gbc.gridy = 1;
        gbc.insets = new Insets(10, 0, 0, 0);
        JLabel idLabel = new JLabel("ID: " + sessionId, SwingConstants.CENTER);
        idLabel.setForeground(new Color(100, 116, 139));
        panel.add(idLabel, gbc);
        
        gbc.gridy = 2;
        gbc.insets = new Insets(30, 0, 0, 0);
        JLabel questionLabel = new JLabel("Are you ready to start this manual test?", SwingConstants.CENTER);
        questionLabel.setFont(new Font("SansSerif", Font.PLAIN, 18));
        panel.add(questionLabel, gbc);
        
        gbc.gridy = 3;
        gbc.insets = new Insets(40, 0, 0, 0);
        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 20, 0));
        buttonPanel.setOpaque(false);
        
        JButton yesBtn = new JButton("YES, START");
        yesBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        yesBtn.setBackground(new Color(35, 134, 54));
        yesBtn.setForeground(Color.WHITE);
        yesBtn.setFocusPainted(false);
        
        JButton noBtn = new JButton("NO, REJECT");
        noBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        noBtn.setBackground(new Color(248, 81, 73));
        noBtn.setForeground(Color.WHITE);
        noBtn.setFocusPainted(false);
        
        buttonPanel.add(noBtn);
        buttonPanel.add(yesBtn);
        panel.add(buttonPanel, gbc);
        
        yesBtn.addActionListener(e -> future.complete(true));
        noBtn.addActionListener(e -> future.complete(false));
        
        container.add(panel, "ACK");
        cardLayout.show(container, "ACK");
        revalidate();
        repaint();
    }

    public void showTestStep(int index, int total, String description, CompletableFuture<ManualTestProcessor.StepResult> future) {
        bringToFront();
        setTitle("TestGenesis Manual Agent [TESTING]");
        
        JPanel testPanel = new JPanel(new BorderLayout(15, 15));
        testPanel.setBorder(new EmptyBorder(25, 25, 25, 25));

        // Header
        JPanel header = new JPanel(new BorderLayout());
        JLabel progressLabel = new JLabel("STEP " + index + " / " + total);
        progressLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        progressLabel.setForeground(new Color(100, 116, 139));
        header.add(progressLabel, BorderLayout.WEST);
        
        JProgressBar progressBar = new JProgressBar(0, total);
        progressBar.setValue(index);
        progressBar.setPreferredSize(new Dimension(0, 10));
        header.add(progressBar, BorderLayout.SOUTH);
        testPanel.add(header, BorderLayout.NORTH);

        // Content
        JTextArea stepArea = new JTextArea(description);
        stepArea.setLineWrap(true);
        stepArea.setWrapStyleWord(true);
        stepArea.setEditable(false);
        stepArea.setMargin(new Insets(15, 15, 15, 15));
        stepArea.setFont(new Font("Serif", Font.PLAIN, 20));
        
        JScrollPane scrollPane = new JScrollPane(stepArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(226, 232, 240)));
        testPanel.add(scrollPane, BorderLayout.CENTER);

        // Footer
        JPanel footer = new JPanel(new BorderLayout(10, 10));
        
        JPanel reasonPanel = new JPanel(new BorderLayout(10, 5));
        reasonPanel.add(new JLabel("Failure Reason:"), BorderLayout.NORTH);
        JTextField reasonField = new JTextField();
        reasonField.setPreferredSize(new Dimension(0, 35));
        reasonPanel.add(reasonField, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 15, 15));
        buttonPanel.setPreferredSize(new Dimension(0, 50));
        
        JButton passBtn = new JButton("PASSED");
        passBtn.setFont(new Font("SansSerif", Font.BOLD, 16));
        passBtn.setBackground(new Color(35, 134, 54));
        passBtn.setForeground(Color.WHITE);
        passBtn.setFocusPainted(false);
        
        JButton failBtn = new JButton("FAILED");
        failBtn.setFont(new Font("SansSerif", Font.BOLD, 16));
        failBtn.setBackground(new Color(248, 81, 73));
        failBtn.setForeground(Color.WHITE);
        failBtn.setFocusPainted(false);
        
        buttonPanel.add(failBtn);
        buttonPanel.add(passBtn);
        
        footer.add(reasonPanel, BorderLayout.NORTH);
        footer.add(buttonPanel, BorderLayout.SOUTH);
        
        testPanel.add(footer, BorderLayout.SOUTH);

        passBtn.addActionListener(e -> future.complete(new ManualTestProcessor.StepResult(true, "Completed successfully")));
        failBtn.addActionListener(e -> {
            String reason = reasonField.getText().trim();
            if (reason.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please provide a reason for failure.", "Reason Required", JOptionPane.WARNING_MESSAGE);
                return;
            }
            future.complete(new ManualTestProcessor.StepResult(false, reason));
        });

        container.add(testPanel, "TEST");
        cardLayout.show(container, "TEST");
        revalidate();
        repaint();
    }

    public static void main(String[] args) {
        // Set Look and Feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            ManualAgentApp app = new ManualAgentApp();
            app.setVisible(true);
        });
    }
}
