import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class GraficoDesempenho extends JPanel {
    private final Map<String, Map<String, Long>> data;
    private final String titulo;

    public GraficoDesempenho(Map<String, Map<String, Long>> data, String titulo) {
        this.data = data;
        this.titulo = titulo;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        int width = getWidth();
        int height = getHeight();
        int margin = 50;

        int numSamples = data.keySet().size();
        long maxTime = data.values().stream()
                .flatMap(m -> m.values().stream())
                .max(Long::compare)
                .orElse(1L);

        int xScale = (width - 2 * margin) / numSamples;
        double yScale = (double) (height - 2 * margin) / maxTime;

        Color[] colors = {Color.RED, Color.BLUE, Color.GREEN};

        int x = margin;
        for (String sample : data.keySet()) {
            Map<String, Long> methodData = data.getOrDefault(sample, new HashMap<>());

            int methodIndex = 0;
            for (String method : methodData.keySet()) {
                long time = methodData.getOrDefault(method, 0L);

                int barHeight = (int) (time * yScale);
                g2d.setColor(colors[methodIndex % colors.length]);

                int barWidth = xScale / methodData.size();
                g2d.fillRect(x + methodIndex * barWidth, height - margin - barHeight, barWidth, barHeight);
                methodIndex++;
            }
            x += xScale;
        }

        g2d.setColor(Color.BLACK);
        g2d.drawLine(margin, height - margin, width - margin, height - margin);
        g2d.drawLine(margin, height - margin, margin, margin);

        // Desenhar rótulos das amostras (eixo X)
        int xLabel = margin;
        for (String sample : data.keySet()) {
            g2d.drawString(sample, xLabel + (xScale / 2) - 10, height - margin + 20);
            xLabel += xScale;
        }

        // Desenhar rótulos do eixo Y
        for (int i = 0; i <= 10; i++) {
            int yLabel = height - margin - (i * (height - 2 * margin) / 10);
            g2d.drawString(String.valueOf(maxTime * i / 10), margin - 40, yLabel + 5);
            g2d.drawLine(margin - 5, yLabel, margin, yLabel);
        }

        // Título do gráfico
        g2d.drawString(titulo, width / 2 - g2d.getFontMetrics().stringWidth(titulo) / 2, margin / 2);

        // Legenda
        int legendY = margin + 30;
        String[] methods = {"SerialCPU", "ParallelCPU", "ParallelGPU"};
        for (int i = 0; i < methods.length; i++) {
            g2d.setColor(colors[i % colors.length]);
            g2d.fillRect(margin, legendY + (i * 20), 15, 15);
            g2d.setColor(Color.BLACK);
            g2d.drawString(methods[i], margin + 20, legendY + 12 + (i * 20));
        }
    }

    public static Map<String, Map<String, Long>> loadDataFromCSV(String filePath) {
        Map<String, Map<String, Long>> data = new LinkedHashMap<>();
    
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line = br.readLine(); // Pular o cabeçalho
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");
                if (values.length < 4) continue; // Ignorar linhas incompletas

                String method = values[0].trim();
                String sample = values[1].trim();
                long time = Long.parseLong(values[3].trim());

                data.putIfAbsent(sample, new HashMap<>());
                data.get(sample).put(method, time);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NumberFormatException e) {
            System.err.println("Erro ao converter número: " + e.getMessage());
        }
        return data;
    }

    public static void main(String[] args) {
        String filePath = "results.csv";
        Map<String, Map<String, Long>> data = loadDataFromCSV(filePath);

        JFrame frame = new JFrame("Desempenho de Algoritmos");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.add(new GraficoDesempenho(data, "Desempenho de CPU/GPU para Diferentes Amostras"));
        frame.setVisible(true);
    }
}
