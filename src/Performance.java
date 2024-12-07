import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class Performance extends JPanel {
    private final Map<String, Map<String, Long>> performanceData;
    private final String chartTitle;

    public Performance(Map<String, Map<String, Long>> performanceData, String chartTitle) {
        this.performanceData = performanceData;
        this.chartTitle = chartTitle;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        int width = getWidth();
        int height = getHeight();
        int padding = 50;

        int numSamples = performanceData.keySet().size();
        long maxTime = performanceData.values().stream()
                .flatMap(map -> map.values().stream())
                .max(Long::compare)
                .orElse(1L);

        int sampleSpacing = (width - 2 * padding) / numSamples;
        double timeScalingFactor = (double) (height - 2 * padding) / maxTime;

        Color[] barColors = { Color.RED, Color.BLUE, Color.GREEN };
        int barPositionX = padding;

        for (String sample : performanceData.keySet()) {
            Map<String, Long> methodTimes = performanceData.get(sample);
            int methodIndex = 0;

            for (Map.Entry<String, Long> entry : methodTimes.entrySet()) {
                long executionTime = entry.getValue();
                int barHeight = (int) (executionTime * timeScalingFactor);

                g2d.setColor(barColors[methodIndex % barColors.length]);
                int barWidth = sampleSpacing / methodTimes.size();
                g2d.fillRect(barPositionX + methodIndex * barWidth, height - padding - barHeight, barWidth, barHeight);
                methodIndex++;
            }
            barPositionX += sampleSpacing;
        }

        g2d.setColor(Color.BLACK);
        g2d.drawLine(padding, height - padding, width - padding, height - padding);
        g2d.drawLine(padding, height - padding, padding, padding);

        int labelXPosition = padding;
        for (String sample : performanceData.keySet()) {
            g2d.drawString(sample, labelXPosition + (sampleSpacing / 2) - 10, height - padding + 20);
            labelXPosition += sampleSpacing;
        }

        for (int i = 0; i <= 10; i++) {
            int yPosition = height - padding - (i * (height - 2 * padding) / 10);
            g2d.drawString(String.valueOf(maxTime * i / 10), padding - 40, yPosition + 5);
            g2d.drawLine(padding - 5, yPosition, padding, yPosition);
        }

        g2d.drawString(chartTitle, width / 2 - g2d.getFontMetrics().stringWidth(chartTitle) / 2, padding / 2);

        int legendPositionY = padding + 30;
        String[] methods = { "SerialCPU", "ParallelCPU", "ParallelGPU" };
        for (int i = 0; i < methods.length; i++) {
            g2d.setColor(barColors[i % barColors.length]);
            g2d.fillRect(padding, legendPositionY + (i * 20), 15, 15);
            g2d.setColor(Color.BLACK);
            g2d.drawString(methods[i], padding + 20, legendPositionY + 12 + (i * 20));
        }

        g2d.drawString("Samples", width / 2 - g2d.getFontMetrics().stringWidth("Samples") / 2, height - padding + 40);
        g2d.rotate(-Math.PI / 2);
        g2d.drawString("Execution Time (ms)", -height / 2 - g2d.getFontMetrics().stringWidth("Execution Time (ms)") / 2,
                padding - 20);
        g2d.rotate(Math.PI / 2);
    }

    public static Map<String, Map<String, Long>> parseCSV(String filePath) {
        Map<String, Map<String, Long>> parsedData = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            reader.readLine();
            String line;

            while ((line = reader.readLine()) != null) {
                String[] values = line.split(",");
                if (values.length < 4)
                    continue;

                String method = values[0].trim();
                String sample = values[1].trim();
                long executionTime = Long.parseLong(values[3].trim());

                parsedData.computeIfAbsent(sample, _ -> new HashMap<>()).put(method, executionTime);
            }
        } catch (IOException | NumberFormatException e) {
            e.printStackTrace();
        }
        return parsedData;
    }

    public static void main(String[] args) {
        String csvFilePath = "./resources/results.csv";
        Map<String, Map<String, Long>> performanceData = parseCSV(csvFilePath);

        JFrame frame = new JFrame("Performance Comparison");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.add(new Performance(performanceData, "CPU/GPU Performance Comparison"));
        frame.setVisible(true);
    }
}
