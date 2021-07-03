package org.vince.stress;

import org.jboss.logging.Logger;

public class PrimeNumber {

    private static final Logger log = Logger.getLogger(PrimeNumber.class);

    public int nPrimes(long n) {
        int count = 0;
        for (long i = 0; i < n; i++) {
            if(isPrime(i)) {
                count++;
            }
        }
        return count;
    }

    public boolean isPrime(long n) {
        if (n <= 1) {
            return false;
        }
        for (long i = 2; i < Math.sqrt(n); i++) {
            if (n % i == 0) {
                return false;
            }
        }
        return true;
    }
}
