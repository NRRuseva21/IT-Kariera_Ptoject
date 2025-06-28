import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ArduinoMonitorApp extends JFrame {

    private static final String ESP32_IP_ADDRESS = "172.20.10.3";
    private static final int REFRESH_INTERVAL_SECONDS = 5;

    private JLabel tempLabel;
    private JLabel humidityLabel;
    private JLabel mq2Label;
    private JLabel statusLabel;
    private JLabel lastUpdatedLabel;

    private JTable dataTable;
    private DefaultTableModel tableModel;

    private ScheduledExecutorService scheduler;
    private SimpleDateFormat timeFormat;

    public ArduinoMonitorApp() {
        setTitle("Мониторинг на Пожароизвестяване (ESP32)");
        setSize(700, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(true);

        setLayout(new BorderLayout(10, 10));

        timeFormat = new SimpleDateFormat("HH:mm:ss");

        JPanel headerPanel = new JPanel();
        headerPanel.setBackground(new Color(0, 86, 179));
        JLabel titleLabel = new JLabel("Състояние на сензорите и история");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 22));
        titleLabel.setForeground(Color.WHITE);
        headerPanel.add(titleLabel);
        add(headerPanel, BorderLayout.NORTH);

        JPanel mainContentPanel = new JPanel();
        mainContentPanel.setLayout(new GridLayout(2, 1, 10, 10));
        mainContentPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        mainContentPanel.setBackground(Color.WHITE);

        JPanel currentDataPanel = new JPanel(new GridLayout(4, 1, 10, 10));
        currentDataPanel.setBackground(Color.WHITE);

        tempLabel = new JLabel("Температура: Зареждане...");
        humidityLabel = new JLabel("Влажност: Зареждане...");
        mq2Label = new JLabel("Газ/Дим (MQ-2): Зареждане...");
        statusLabel = new JLabel("Състояние: Зареждане...");

        Font dataFont = new Font("Arial", Font.PLAIN, 16);
        tempLabel.setFont(dataFont);
        humidityLabel.setFont(dataFont);
        mq2Label.setFont(dataFont);
        statusLabel.setFont(new Font("Arial", Font.BOLD, 18));

        currentDataPanel.add(tempLabel);
        currentDataPanel.add(humidityLabel);
        currentDataPanel.add(mq2Label);
        currentDataPanel.add(statusLabel);

        mainContentPanel.add(currentDataPanel);

        String[] columnNames = {"Време", "Температура (°C)", "Влажност (%)", "Газ/Дим (MQ-2)", "Състояние"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        dataTable = new JTable(tableModel);
        dataTable.setFillsViewportHeight(true);
        dataTable.setFont(new Font("Arial", Font.PLAIN, 12));
        dataTable.getTableHeader().setFont(new Font("Arial", Font.BOLD, 12));

        JScrollPane scrollPane = new JScrollPane(dataTable);
        scrollPane.setBorder(BorderFactory.createTitledBorder("История на показанията"));

        mainContentPanel.add(scrollPane);

        add(mainContentPanel, BorderLayout.CENTER);

        JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        footerPanel.setBackground(new Color(244, 244, 244)); // #f4f4f4
        lastUpdatedLabel = new JLabel("Обновяване на всеки " + REFRESH_INTERVAL_SECONDS + " секунди");
        lastUpdatedLabel.setFont(new Font("Arial", Font.ITALIC, 12));
        lastUpdatedLabel.setForeground(new Color(119, 119, 119)); // #777
        footerPanel.add(lastUpdatedLabel);
        add(footerPanel, BorderLayout.SOUTH);

        setVisible(true);

        startAutoRefresh();
    }

    private void startAutoRefresh() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                readAndDisplaySensorData();
            }
        }, 0, REFRESH_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void readAndDisplaySensorData() {
        try {
            URL url = new URL("http://" + ESP32_IP_ADDRESS);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String inputLine;
                StringBuilder content = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    content.append(inputLine);
                }
                in.close();
                connection.disconnect();

                String htmlContent = content.toString();

                parseAndDisplayData(htmlContent);

            } else {
                updateLabelsOnError("Грешка при HTTP заявка: " + responseCode);
                System.err.println("Грешка при HTTP заявка: " + responseCode);
            }
        } catch (Exception e) {
            updateLabelsOnError("Невъзможна връзка с ESP32: " + e.getMessage());
            System.err.println("Грешка при свързване или четене от ESP32: " + e.getMessage());
        }
    }

    private void parseAndDisplayData(String htmlContent) {
        String temperature = "Н/Д";
        String humidity = "Н/Д";
        String mq2Value = "Н/Д";
        String statusMessage = "Няма данни.";
        Color statusColor = Color.BLACK;

        Pattern tempPattern = Pattern.compile("<b>Температура:</b> ([\\d.]+)");
        Pattern humPattern = Pattern.compile("<b>Влажност:</b> ([\\d.]+)");
        Pattern mq2Pattern = Pattern.compile("<b>Газ/Дим \\(MQ-2\\):</b> (\\d+)");
        Pattern statusPattern = Pattern.compile("<span class='(status-normal|status-warning|status-critical)'>(.*?)</span>");

        Matcher matcher;

        matcher = tempPattern.matcher(htmlContent);
        if (matcher.find()) {
            temperature = matcher.group(1);
        }

        matcher = humPattern.matcher(htmlContent);
        if (matcher.find()) {
            humidity = matcher.group(1);
        }

        matcher = mq2Pattern.matcher(htmlContent);
        if (matcher.find()) {
            mq2Value = matcher.group(1);
        }

        matcher = statusPattern.matcher(htmlContent);
        if (matcher.find()) {
            String cssClass = matcher.group(1);
            statusMessage = matcher.group(2);

            switch (cssClass) {
                case "status-normal":
                    statusColor = Color.GREEN.darker().darker();
                    break;
                case "status-warning":
                    statusColor = Color.ORANGE.darker();
                    break;
                case "status-critical":
                    statusColor = Color.RED.darker();
                    break;
                default:
                    statusColor = Color.BLACK;
                    break;
            }
        }

        final String displayTemperature = temperature + " °C";
        final String displayHumidity = humidity + " %";
        final String displayMq2Value = mq2Value + " / 4095";
        final String displayStatusMessage = statusMessage;
        final Color displayStatusColor = statusColor;

        final String currentTime = timeFormat.format(new Date());

        final String finalTemperatureForTable = temperature;
        final String finalHumidityForTable = humidity;
        final String finalMq2ValueForTable = mq2Value;


        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                tempLabel.setText("Температура: " + displayTemperature);
                humidityLabel.setText("Влажност: " + displayHumidity);
                mq2Label.setText("Газ/Дим (MQ-2): " + displayMq2Value);
                statusLabel.setText("Състояние: " + displayStatusMessage);
                statusLabel.setForeground(displayStatusColor);

                tableModel.addRow(new Object[]{
                        currentTime,
                        finalTemperatureForTable,
                        finalHumidityForTable,
                        finalMq2ValueForTable,
                        displayStatusMessage
                });

                int rowCount = tableModel.getRowCount();
                if (rowCount > 0) {
                    dataTable.scrollRectToVisible(dataTable.getCellRect(rowCount - 1, 0, true));
                }
            }
        });
    }

    private void updateLabelsOnError(final String errorMessage) {
        final Color errorColor = Color.RED.darker();
        final String finalErrorMessage = errorMessage;
        final String currentTime = timeFormat.format(new Date());

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                tempLabel.setText("Температура: Грешка!");
                humidityLabel.setText("Влажност: Грешка!");
                mq2Label.setText("Газ/Дим (MQ-2): Грешка!");
                statusLabel.setText("Състояние: " + finalErrorMessage);
                statusLabel.setForeground(errorColor);
                tableModel.addRow(new Object[]{
                        currentTime,
                        "Грешка",
                        "Грешка",
                        "Грешка",
                        "Грешка: " + finalErrorMessage
                });
                int rowCount = tableModel.getRowCount();
                if (rowCount > 0) {
                    dataTable.scrollRectToVisible(dataTable.getCellRect(rowCount - 1, 0, true));
                }
            }
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new ArduinoMonitorApp();
            }
        });
    }
}