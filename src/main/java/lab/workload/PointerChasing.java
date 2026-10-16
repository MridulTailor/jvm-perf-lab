package lab.workload;

import java.util.SplittableRandom;

/**
 * Pointer-chasing workload stressing memory locality and CPU cache hierarchy.
 * Traverses a linked graph of nodes arranged in a randomized permutation to defeat
 * hardware prefetchers and force L1/L2/L3 cache misses.
 *
 * Deterministic for a given seed.
 */
public final class PointerChasing {

    public static final class Node {
        public final long id;
        public final long payload;
        public Node next;
        public Node jump;

        public Node(long id, long payload) {
            this.id = id;
            this.payload = payload;
        }
    }

    private final Node[] nodes;
    private Node current;

    /**
     * Constructs a pointer-chasing graph.
     *
     * @param seed fixed RNG seed for permutation and payloads
     * @param nodeCount number of nodes in the graph (e.g. 100_000 to exceed L3 cache)
     */
    public PointerChasing(long seed, int nodeCount) {
        if (nodeCount <= 1) {
            throw new IllegalArgumentException("nodeCount must be > 1");
        }
        SplittableRandom rng = new SplittableRandom(seed);
        this.nodes = new Node[nodeCount];

        for (int i = 0; i < nodeCount; i++) {
            nodes[i] = new Node(i, rng.nextLong());
        }

        // Generate a full cycle permutation using Sattolo's algorithm
        int[] permutation = new int[nodeCount];
        for (int i = 0; i < nodeCount; i++) {
            permutation[i] = i;
        }
        for (int i = nodeCount; i > 1; i--) {
            int j = rng.nextInt(i - 1); // 0 <= j < i - 1 ensures a single cycle
            int tmp = permutation[i - 1];
            permutation[i - 1] = permutation[j];
            permutation[j] = tmp;
        }

        for (int i = 0; i < nodeCount; i++) {
            nodes[permutation[i]].next = nodes[permutation[(i + 1) % nodeCount]];
            int jumpTarget = rng.nextInt(nodeCount);
            nodes[i].jump = nodes[jumpTarget];
        }

        this.current = nodes[permutation[0]];
    }

    /**
     * Traverses a given number of steps through the node graph.
     *
     * @param steps number of pointer dereferences to perform
     * @return accumulated checksum across visited nodes
     */
    public long traverse(int steps) {
        long checksum = 0;
        Node curr = this.current;
        for (int i = 0; i < steps; i++) {
            checksum = Long.rotateLeft(checksum, 5) ^ curr.payload;
            // Alternate or branch between next and jump deterministically based on payload bit
            if ((curr.payload & 0x7) == 0) {
                curr = curr.jump;
            } else {
                curr = curr.next;
            }
        }
        this.current = curr;
        return checksum;
    }

    public int getNodeCount() {
        return nodes.length;
    }

    public Node getCurrent() {
        return current;
    }
}
