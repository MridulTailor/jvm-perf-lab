package lab.workload;

import java.util.SplittableRandom;

/**
 * Allocation-heavy workload that allocates short-lived objects of mixed sizes,
 * with a configurable fraction retained in a bounded circular buffer to exert
 * tenured (old generation) heap pressure.
 *
 * Deterministic for a given seed.
 */
public final class AllocationHeavy {

    public record ShortLivedPayload(long id, int kind, byte[] data, double score) {
        public long checksum() {
            long sum = id ^ kind ^ Double.doubleToLongBits(score);
            if (data != null && data.length > 0) {
                sum ^= data[0] ^ (long) data[data.length - 1] << 8 ^ (long) data.length << 16;
            }
            return sum;
        }
    }

    private final SplittableRandom rng;
    private final double retentionFraction;
    private final ShortLivedPayload[] retainedCache;
    private int cacheIndex = 0;
    private int retainedCount = 0;
    private long totalAllocatedBytes = 0;

    /**
     * @param seed fixed RNG seed for determinism
     * @param retentionFraction fraction in [0.0, 1.0] retained in cache
     * @param cacheCapacity maximum capacity of bounded retention cache
     */
    public AllocationHeavy(long seed, double retentionFraction, int cacheCapacity) {
        if (retentionFraction < 0.0 || retentionFraction > 1.0) {
            throw new IllegalArgumentException("retentionFraction must be in [0.0, 1.0]");
        }
        if (cacheCapacity <= 0) {
            throw new IllegalArgumentException("cacheCapacity must be > 0");
        }
        this.rng = new SplittableRandom(seed);
        this.retentionFraction = retentionFraction;
        this.retainedCache = new ShortLivedPayload[cacheCapacity];
    }

    /**
     * Executes one batch of allocations.
     *
     * @param batchSize number of objects to allocate in this step
     * @return checksum of allocated objects in this step
     */
    public long step(int batchSize) {
        long stepChecksum = 0;
        for (int i = 0; i < batchSize; i++) {
            long id = rng.nextLong();
            int kind = rng.nextInt(4);
            int dataSize = switch (kind) {
                case 0 -> 16;
                case 1 -> 64;
                case 2 -> 256;
                case 3 -> 1024;
                default -> 32;
            };

            byte[] data = new byte[dataSize];
            rng.nextBytes(data);
            double score = rng.nextDouble();

            ShortLivedPayload payload = new ShortLivedPayload(id, kind, data, score);
            stepChecksum = Long.rotateLeft(stepChecksum, 7) ^ payload.checksum();
            totalAllocatedBytes += (32 + dataSize);

            if (rng.nextDouble() < retentionFraction) {
                retainedCache[cacheIndex] = payload;
                cacheIndex = (cacheIndex + 1) % retainedCache.length;
                if (retainedCount < retainedCache.length) {
                    retainedCount++;
                }
            }
        }
        return stepChecksum;
    }

    public int getRetainedCount() {
        return retainedCount;
    }

    public int getCacheCapacity() {
        return retainedCache.length;
    }

    public long getTotalAllocatedBytes() {
        return totalAllocatedBytes;
    }

    public long computeRetainedChecksum() {
        long sum = 0;
        for (int i = 0; i < retainedCount; i++) {
            ShortLivedPayload p = retainedCache[i];
            if (p != null) {
                sum = Long.rotateLeft(sum, 11) ^ p.checksum();
            }
        }
        return sum;
    }
}
