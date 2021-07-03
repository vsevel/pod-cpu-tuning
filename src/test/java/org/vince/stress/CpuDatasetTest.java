package org.vince.stress;

import io.quarkus.test.junit.QuarkusTest;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;
import org.vince.stress.model.Instant;
import org.vince.stress.model.Pod;
import org.vince.stress.model.Simulation;
import org.vince.stress.model.Value;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

@QuarkusTest
class CpuDatasetTest {

    private static final Logger log = Logger.getLogger(CpuDatasetTest.class);

    @Test
    void simul() throws IOException {
        simul(null, 65, 1.0f, 5.0f, null);
    }

    @Test
    void simulAll() throws IOException {
        simul(null);
        simul(5.0f);
    }

    @Test
    void simulP42() throws IOException {
        Map<String, Simulation> simulations = new LinkedHashMap<>();
        simul(null, 65, 1.0f, 5.0f, simulations);
        simul(null, 70, 1.0f, 5.0f, simulations);
        simul(null, 75, 1.0f, 5.0f, simulations);
        simul(null, 80, 1.0f, 5.0f, simulations);
        simul(null, 85, 1.0f, 5.0f, simulations);
        // simul(null, 90, 1.0f, 5.0f, simulations);
        createPodView(42, simulations, 100, 250);
    }


    private void simul(Float limitFactor) throws IOException {
        simul("harness", 50, 1.15f, limitFactor, null);
        Map<String, Simulation> simulations = new LinkedHashMap<>();
        for (int p = 50; p <= 95; p += 5) {
            simul(null, p, 1.0f, limitFactor, simulations);
        }
        simul(null, 99, 1.0f, limitFactor, simulations);
    }

    private void simul(String name, int p, float requestFactor, Float limitFactor, Map<String, Simulation> simulations) throws IOException {
        log.info("---");
        log.info("p=" + p + " request_factor=" + requestFactor + " limit_factor=" + limitFactor);
        Simulation simulation = createSimulation();
        log.info("biggest instant: " + simulation.getBiggestInstant());
        log.info("sum of max: " + simulation.getSumOfMax());

        log.info("using %P" + p);
        simulation.useRequestAsPercentile(p, requestFactor, limitFactor);
        int sumOfRequests = simulation.getSumOfRequests();
        log.info("sum of requests = " + sumOfRequests);

        simulation.calculateCpu(sumOfRequests);
        log.info("avg efficiency: " + simulation.getAvgEfficiencyPercent() + "%");
        int maxedOutInstantsCount = simulation.getMaxedOutInstants().size();
        log.info("number of maxed out cpu instants: " + maxedOutInstantsCount + " (" + (int) (maxedOutInstantsCount * 100.0 / simulation.instants.size()) + "%)");
        log.info("avg completion: " + ((int) (simulation.getAvgCompletion(false) * 1000)) / 10.0 + "%");
        long throttledPods = simulation.getNumberOfThrottledPods();
        long totalNumberOfValues = simulation.getTotalNumberOfValues();
        log.info("number of pods NOT satisfied: " + throttledPods + "/" + totalNumberOfValues + "(" + ((int) (throttledPods * 10000.0 / totalNumberOfValues)) / 100.0 + "%)");

        createImage(simulation, getImageName(name, p, requestFactor), "images/" + (limitFactor != null ? "lf" + limitFactor : "nolimit"));
        if (simulations != null) {
            simulations.put("p" + p, simulation);
        }
    }

    private String getImageName(String name, int p, float requestFactor) {
        return (name == null ? "" : name + "-") + "p" + p + "-rf" + requestFactor;
    }

    private void createImage(Simulation simulation, String name, String dir) throws IOException {

        int xfactor = 100;
        int width = (simulation.pods.size() + 1) * xfactor;
        int height = simulation.instants.size();

        // Constructs a BufferedImage of one of the predefined image types.
        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        // Create a graphics which can be used to draw into the buffered image
        Graphics2D g2d = bufferedImage.createGraphics();

        int y = 0;
        for (Instant instant : simulation.instants) {
            int x = 0;
            for (Value value : instant.values) {
                g2d.setColor(getValueThrottledColor(value));
                g2d.fillRect(x, y, xfactor, 1);
                x += xfactor;
            }
            g2d.setColor(getEfficiencyColor(instant));
            g2d.fillRect(x, y, xfactor, 1);
            y++;
        }

        // Disposes of this graphics context and releases any system resources that it is using.
        g2d.dispose();

        // Save as PNG
        new File(dir).mkdirs();
        File file = new File(dir + "/" + name + ".png");
        ImageIO.write(bufferedImage, "png", file);
    }

    private Color getValueThrottledColor(Value value) {
        if (value.isThrottled()) {
            int throttledColor = (int) (value.getThrottledPercent() * 255) + 1;
            return new Color(255, throttledColor, throttledColor);
        } else {
            return Color.white;
        }
    }

    private Color getEfficiencyColor(Instant instant) {
        if (instant.getEfficiencyAsPercent() == 100) {
            return Color.red;
        } else {
            int efficiencyColor = 255 - (int) (instant.getEfficiencyAsPercent() / 100.0 * 255);
            return new Color(efficiencyColor, efficiencyColor, efficiencyColor);
        }
    }

    private Simulation createSimulation() throws IOException {

        List<List<Integer>> biglist = loadSeries();
        log.info("loaded " + biglist.size() + " series for " + biglist.get(0).size() + " pods");
        int podCount = biglist.get(0).size();

        List<Instant> instants = IntStream.range(0, biglist.size()).mapToObj(Instant::new).collect(toList());
        List<Pod> pods = IntStream.range(0, podCount).mapToObj(Pod::new).collect(toList());

        pods.forEach(pod -> {
            pod.values = biglist.stream()
                    .map(list -> list.get(pod.id))
                    .map(v -> new Value(pod, v))
                    .collect(toList());
            pod.init();
        });

        instants.forEach(instant -> {
            instant.values = pods.stream().map(pod -> pod.values.get(instant.id)).collect(toList());
            instant.values.forEach(v -> v.instant = instant);
        });

        Simulation simulation = new Simulation();
        simulation.instants = instants;
        simulation.pods = pods;
        return simulation;
    }

    private void createPodView(int podId, Map<String, Simulation> simulations, int skip, int limit) throws IOException {
        try (FileWriter fileWriter = new FileWriter("test" + podId + ".csv");
             PrintWriter out = new PrintWriter(fileWriter)) {
            Simulation simul0 = simulations.values().stream().findFirst().orElseThrow();
            out.print(";");
            out.println(IntStream.range(0, simul0.instants.size()).skip(skip).limit(limit).mapToObj(Integer::toString).collect(joining(";")));
            simulations.forEach((name, simul) -> {
                out.println(name + ";" + simul.instants.stream().skip(skip).limit(limit).map(instant -> "" + instant.values.get(podId).realMillicores).collect(joining(";")));
            });
            out.println("ideal;" + simul0.instants.stream().skip(skip).limit(limit).map(instant -> "" + instant.values.get(podId).idealMillicores).collect(joining(";")));
        }

        simulations.forEach((name, simul) -> {
            Pod pod = simul.pods.get(podId);
            int podRequest = pod.request;
            int podLimit = pod.limit;
            log.info(name + " pod request=" + podRequest + " limit=" + podLimit);
        });
    }

    @Test
    void efficiencyMax() throws IOException {
        int totalCores = 30;
        List<List<Integer>> biglist = loadSeries();
        log.info("loaded " + biglist.size() + " series for " + biglist.get(0).size() + " pods");
        double fullEfficiency = 0.0;
        for (List<Integer> list : biglist) {
            int sum = list.stream().reduce(0, Integer::sum);
            double efficiency = sum / (totalCores * 1000.0);
            fullEfficiency += efficiency;
            // log.info("efficiency => " + efficiency);
        }
        log.info("avg efficiency = " + (int) (fullEfficiency / biglist.size() * 100) + "%");
    }

    @Test
    void percent() throws IOException {
        List<List<Integer>> biglist = loadSeries();
        int podCount = biglist.get(0).size();
        Arrays.asList(50, 90, 95, 99, 100).forEach(p -> {
            int sum = 0;
            for (int i = 0; i < podCount; i++) {
                int podId = i;
                List<Integer> podValues = biglist.stream().map(list -> list.get(podId)).sorted().collect(toList());
                int value = p == 100 ? podValues.get(podValues.size() - 1) : podValues.get((int) (podValues.size() * 1.0 * p / 100));
                sum += value;
            }

        });
    }

    private List<List<Integer>> loadSeries() throws IOException {
        List<List<Integer>> biglist = new ArrayList<>();
        try (FileReader fr = new FileReader("serie.csv");
             BufferedReader br = new BufferedReader(fr)) {
            String line = br.readLine(); // skip header
            while ((line = br.readLine()) != null) {
                String[] csvLine = line.split(";");
                List<Integer> list = Arrays.stream(csvLine)
                        .skip(1)
                        .map(Integer::valueOf)
                        .collect(toList());
                biglist.add(list.subList(0, list.size() - 1));
            }
        }
        return biglist;
    }

    @Test
    void buildSerie() throws IOException {
        File dir = new File("../GWA-T-13_Materna-Workload-Traces/Materna-Trace-1");
        List<File> files = Arrays.stream(Objects.requireNonNull(dir.listFiles()))
                .filter(file -> file.getName().endsWith(".csv"))
                .sorted()
                .collect(toList());
        List<List<Integer>> biglist = new ArrayList<>();
        for (File file : files) {
            try (FileReader fr = new FileReader(file);
                 BufferedReader br = new BufferedReader(fr)) {
                List<Integer> list = new ArrayList<>();
                String line = br.readLine(); // skip header
                while ((line = br.readLine()) != null) {
                    String[] csvLine = line.split(";");
                    int coresTotal = Integer.parseInt(csvLine[1].replace("\"", ""));
                    int millicores = new BigDecimal(csvLine[4].replace(",", ".").replace("\"", ""))
                            .divide(BigDecimal.valueOf(100))
                            .multiply(BigDecimal.valueOf(coresTotal))
                            .multiply(BigDecimal.valueOf(1000))
                            .toBigInteger().intValue();
                    list.add(millicores);
                }
                if (list.size() > 8000) {
                    biglist.add(list);
                }
            }
        }

        int max = 0;

        try (FileWriter fw = new FileWriter("serie.csv");
             PrintWriter out = new PrintWriter(fw)) {

            int seriesCount = 50;

            for (int i = 1; i <= seriesCount; i++) {
                out.print(";POD_" + i);
            }
            out.println(";sum");

            for (int i = 0; i < 8000; i++) {
                int sum = 0;
                for (int j = 0; j < seriesCount; j++) {
                    out.print(j == 0 ? "T_" + i + ";" : ";");
                    int value = biglist.get(j).get(i);
                    sum += value;
                    out.print(value);
                }
                out.println(";" + sum);
                max = Math.max(max, sum);
            }
        }
        log.info("max = " + max);
    }


}
