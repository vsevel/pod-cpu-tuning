package org.vince.stress;

import org.jboss.logging.Logger;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Path("/stress")
public class StressResource {

    private static final Logger log = Logger.getLogger(StressResource.class);

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        return "Hello RESTEasy";
    }

    @GET
    @Path("/prime")
    @Produces(MediaType.TEXT_PLAIN)
    public int prime(@QueryParam("n") long n) {
        return new PrimeNumber().nPrimes(n);
    }



    @GET
    @Path("/pi")
    @Produces(MediaType.TEXT_PLAIN)
    public String pi(@QueryParam("digits") int digits) {
        MathContext mc = new MathContext(digits + 1, RoundingMode.HALF_EVEN);
        BBP bbp = new BBP();
        // bbp.startProgressThread();
        BigDecimal bigDecimal = bbp.calcPi(mc);
        return "" + bigDecimal.toPlainString().substring(0, digits + 2);
    }

    @GET
    @Path("/primemt")
    @Produces(MediaType.TEXT_PLAIN)
    public String pi(@QueryParam("n") int n, @QueryParam("threads") int threads, @QueryParam("tasks") int tasks) throws ExecutionException, InterruptedException {
        log.info("n=" + n + " threads=" + threads + " tasks=" + tasks);
        ExecutorService executorService = Executors.newFixedThreadPool(threads);
        try {
            long time = 0;
            for (int i = 0; i < 2; i++) {
                long start = System.currentTimeMillis();
                List<Future<?>> futures = new ArrayList<>();
                for (int j = 0; j < tasks; j++) {
                    futures.add(executorService.submit(() -> new PrimeNumber().nPrimes(n)));
                }
                int done = 0;
                for (Future<?> future : futures) {
                    future.get();
                    done++;
                    log.info("finished " + done + " out of " + tasks);
                }
                time = System.currentTimeMillis() - start;
                log.info("run " + i + " => " + time + " ms");
            }
            return time + "ms";
        } finally {
            executorService.shutdown();
        }
    }
}