package org.vince.stress;

import ch.reto_hoehener.japng.Apng;
import ch.reto_hoehener.japng.ApngFactory;
import ch.reto_hoehener.japng.JapngException;
import io.quarkus.test.junit.QuarkusTest;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

@QuarkusTest
class CpuDatasetTest {

    private static final Logger log = Logger.getLogger(CpuDatasetTest.class);

    List<List<Integer>> biglist = loadSeries();

    ExecutorService executorService;

    @BeforeEach
    void before() {
        executorService = Executors.newFixedThreadPool(10);
    }

    @AfterEach
    void after() {
        executorService.shutdown();
    }

    @Test
    void simul() {
        simul(null, 65, 1.0f, 5.0f);
    }

    @Test
    void simulAllLimit5x() throws ExecutionException, InterruptedException {
        simul(5.0f);
    }

    @Test
    void simulAllNoLimit() throws ExecutionException, InterruptedException {
        simul(null);
    }

    @Test
    void simulP23() {
        Map<String, Simulation> simulations = new LinkedHashMap<>();
        Arrays.asList(65, 70, 75, 80, 85).forEach(p -> {
            Simulation simul = simul(null, p, 1.0f, null);
            simulations.put(simul.name, simul);
        });
        createPodView(23, simulations, 2150, 200);
    }

    @Test
    public void simulAllLimit5xDetailed() throws ExecutionException, InterruptedException {
        List<Future<?>> futures = new ArrayList<>();
        for (int p = 50; p <= 99; p++) {
            futures.add(simulAsynch(null, p, 1.0f, 5.0f));
        }
        int done = 0;
        for (Future<?> future : futures) {
            future.get();
            done++;
            log.info("done " + done + "/" + futures.size());
        }
    }

    private void simul(Float limitFactor) throws ExecutionException, InterruptedException {
        List<Future<?>> futures = new ArrayList<>();
        simul("harness", 50, 1.15f, limitFactor);
        for (int p = 50; p <= 95; p += 5) {
            futures.add(simulAsynch(null, p, 1.0f, limitFactor));
        }
        futures.add(simulAsynch(null, 99, 1.0f, limitFactor));
        for (Future<?> future : futures) {
            future.get();
        }
    }

    private Future<Simulation> simulAsynch(String name, int p, float requestFactor, Float limitFactor) {
        return executorService.submit(() -> simul(name, p, requestFactor, limitFactor));
    }

    private Simulation simul(String name, int p, float requestFactor, Float limitFactor) {
        log.info("---");
        log.info("p=" + p + " request_factor=" + requestFactor + " limit_factor=" + limitFactor);
        Simulation simulation = createSimulation();
        simulation.name = "P" + p;
        log.info("biggest instant: " + simulation.getBiggestInstant());
        log.info("sum of max: " + simulation.getSumOfMax());

        log.info("using %P" + p);
        simulation.useRequestAsPercentile(p, requestFactor, limitFactor);
        int sumOfRequests = simulation.getSumOfRequests();
        log.info("sum of requests = " + sumOfRequests);

        long start = System.currentTimeMillis();
        simulation.calculateCpu(sumOfRequests);
        log.info("simulation calculated in " + (System.currentTimeMillis() - start) + " ms");
        log.info("avg efficiency: " + simulation.getAvgEfficiencyPercent() + "%");
        int maxedOutInstantsCount = simulation.getMaxedOutInstants().size();
        log.info("number of maxed out cpu instants: " + maxedOutInstantsCount + " (" + simulation.getCpuFullDuringTimeAsPercent() + "%)");
        log.info("avg completion: " + ((int) (simulation.getAvgCompletion(false) * 1000)) / 10.0 + "%");
        long throttledPods = simulation.getNumberOfThrottledPods();
        long totalNumberOfValues = simulation.getTotalNumberOfValues();
        log.info("number of pods NOT satisfied: " + throttledPods + "/" + totalNumberOfValues + "(" + simulation.getNumberOfPodsNotSatisfiedAsPercent() + "%)");

        createImage(simulation, getImageName(name, p, requestFactor), "images/" + (limitFactor != null ? "lf" + limitFactor : "nolimit"));
        return simulation;
    }

    private String getImageName(String name, int p, float requestFactor) {
        return (name == null ? "" : name + "-") + "p" + p + "-rf" + requestFactor;
    }

    @SuppressWarnings({"unused"})
    private void createImage(Simulation simulation, String name, String dir) {

        int yfactor = 100;
        int height = (simulation.pods.size() + 1) * yfactor;
        int width = simulation.instants.size();

        // Constructs a BufferedImage of one of the predefined image types.
        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        // Create a graphics which can be used to draw into the buffered image
        Graphics2D g2d = bufferedImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int x = 0;
        for (Instant instant : simulation.instants) {
            int y = 0;
            for (Value value : instant.values) {
                g2d.setColor(getValueThrottledColor(value));
                g2d.fillRect(x, y, 1, yfactor);
                y += yfactor;
            }
            g2d.setColor(getEfficiencyColor(instant));
            g2d.fillRect(x, y, 1, yfactor);
            x++;
        }

        Font font = new Font("Calibri", Font.PLAIN, 100);
        g2d.setFont(font);
        g2d.setColor(Color.blue);

        int xtextoffset = 40;
        int xbaroffset = 1500;
        int ytext = 120;
        int xtext2offset = 1000;

        g2d.drawString("%" + simulation.name, xtextoffset, ytext);

        ytext += 100;
        g2d.drawString("Host Cpu (milli)", xtextoffset, ytext);
        g2d.drawString("" + simulation.getSumOfRequests(), xtextoffset + xtext2offset, ytext);
        g2d.fillRect(xbaroffset, ytext - 80, (int) ((simulation.getSumOfRequests() * 1.0 / 40000) * (width - xbaroffset)), 90);

        ytext += 100;
        g2d.drawString("Host Cpu Efficiency", xtextoffset, ytext);
        g2d.drawString(simulation.getAvgEfficiencyPercent() + "%", xtextoffset + xtext2offset, ytext);
        g2d.fillRect(xbaroffset, ytext - 80, (int) ((simulation.getAvgEfficiencyPercent() * 1.0 / 100) * (width - xbaroffset)), 90);

        ytext += 100;
        g2d.drawString("Host Cpu Full", xtextoffset, ytext);
        g2d.drawString(simulation.getCpuFullDuringTimeAsPercent() + "%", xtextoffset + xtext2offset, ytext);
        g2d.fillRect(xbaroffset, ytext - 80, (int) ((simulation.getCpuFullDuringTimeAsPercent() * 1.0 / 100) * (width - xbaroffset)), 90);

        ytext += 100;
        g2d.drawString("Throttled Pods", xtextoffset, ytext);
        g2d.drawString(simulation.getNumberOfPodsNotSatisfiedAsPercent() + "%", xtextoffset + xtext2offset, ytext);
        g2d.fillRect(xbaroffset, ytext - 80, (int) ((simulation.getNumberOfPodsNotSatisfiedAsPercent() / 100) * (width - xbaroffset)), 90);

        // Disposes of this graphics context and releases any system resources that it is using.
        g2d.dispose();

        // Save as PNG
        new File(dir).mkdirs();
        File file = new File(dir + "/" + name + ".png");
        try {
            ImageIO.write(bufferedImage, "png", file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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

    private Simulation createSimulation() {

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

    private void createPodView(int podId, Map<String, Simulation> simulations, int skip, int limit) {
        try (FileWriter fileWriter = new FileWriter("test" + podId + ".csv");
             PrintWriter out = new PrintWriter(fileWriter)) {
            Simulation simul0 = simulations.values().stream().findFirst().orElseThrow();
            out.print(";");
            out.println(IntStream.range(0, simul0.instants.size()).skip(skip).limit(limit).mapToObj(Integer::toString).collect(joining(";")));
            simulations.forEach((name, simul) -> {
                out.println(name + ";" + simul.instants.stream().skip(skip).limit(limit).map(instant -> "" + instant.values.get(podId).realMillicores).collect(joining(";")));
            });
            out.println("ideal;" + simul0.instants.stream().skip(skip).limit(limit).map(instant -> "" + instant.values.get(podId).idealMillicores).collect(joining(";")));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        simulations.forEach((name, simul) -> {
            Pod pod = simul.pods.get(podId);
            int podRequest = pod.request;
            Integer podLimit = pod.limit;
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

    private List<List<Integer>> loadSeries() {
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
        } catch (IOException e) {
            throw new RuntimeException(e);
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


    @Test
        // Xmx=8g
    void apng() throws JapngException {
        String runId = "lf5.0";
        // String runId = "nolimit";
        Apng apng = ApngFactory.createApng();
        File dir = new File("images/" + runId);
        int p = 99;
        while (p >= 50) {
            log.info("adding frame " + p);
            apng.addFrame(new File(dir, "p" + p + "-rf1.0.png"), 300);
            p--;
        }
        apng.assemble(new File("anim_" + runId + ".png"));
    }


}
