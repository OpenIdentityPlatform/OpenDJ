/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.ldap;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;

import org.forgerock.util.Function;
import org.forgerock.util.Reject;
import org.forgerock.util.annotations.VisibleForTesting;
import org.forgerock.util.promise.NeverThrowsException;

/**
 * An implementation of "consistent hashing" supporting per-partition weighting. This implementation is thread safe
 * and allows partitions to be added and removed during use.
 * <p>
 * This implementation maps partitions to one or more points on a circle ranging from {@link Integer#MIN_VALUE} to
 * {@link Integer#MAX_VALUE}. The number of points per partition is dictated by the partition's weight. A partition
 * with a weight which is higher than another partition will receive a proportionally higher load.
 *
 * @param <P> The type of partition object.
 *
 * @see <a href="http://citeseerx.ist.psu.edu/viewdoc/summary?doi=10.1.1.23.3738">Consistent Hashing and Random
 * Trees</a>
 * @see <a href="http://www8.org/w8-papers/2a-webserver/caching/paper2.html">Web Caching with Consistent Hashing</a>
 */
public final class ConsistentHashMap<P> {
    // TODO: add methods for determining which partitions will need to be rebalanced when a partition is added or
    // removed.
    /** The default weight. The value is relatively high in order to minimize the risk of imbalances. */
    private static final int DEFAULT_WEIGHT = 200;
    /** Default hash function based on MD5. */
    @VisibleForTesting
    static final Function<Object, Integer, NeverThrowsException> MD5 =
            new Function<Object, Integer, NeverThrowsException>() {
                @Override
                public Integer apply(final Object key) {
                    final byte[] bytes = key.toString().getBytes(UTF_8);
                    final byte[] digest = getMD5Digest().digest(bytes);
                    return ByteString.wrap(digest).toInt();
                }

                private MessageDigest getMD5Digest() {
                    // TODO: we may want to cache these.
                    try {
                        return MessageDigest.getInstance("MD5");
                    } catch (NoSuchAlgorithmException e) {
                        throw new RuntimeException(e);
                    }
                }
            };
    /** Synchronizes updates. Reads are protected by copy on write. */
    private final ReentrantLock writeLock = new ReentrantLock();
    /** Consistent hash map circle. */
    private volatile NavigableMap<Integer, Node<P>> circle = new TreeMap<>();
    /** Maps partition IDs to their partition. */
    private volatile Map<String, P> partitions = new LinkedHashMap<>();
    /** Function used for hashing keys. */
    private final Function<Object, Integer, NeverThrowsException> hashFunction;

    /** Creates a new consistent hash map which will hash keys using MD5. */
    public ConsistentHashMap() {
        this(MD5);
    }

    /**
     * Creates a new consistent hash map which will hash keys using the provided hash function.
     *
     * @param hashFunction
     *         The function which should be used for hashing keys.
     */
    public ConsistentHashMap(final Function<Object, Integer, NeverThrowsException> hashFunction) {
        this.hashFunction = hashFunction;
    }

    /**
     * Puts a partition into this consistent hash map using the default weight which is sufficiently high to ensure a
     * reasonably uniform distribution among all partitions having the same weight.
     *
     * @param partitionId
     *         The partition ID.
     * @param partition
     *         The partition.
     * @return This consistent hash map.
     */
    public ConsistentHashMap<P> put(final String partitionId, final P partition) {
        return put(partitionId, partition, DEFAULT_WEIGHT);
    }

    /**
     * Puts a partition into this consistent hash map using the specified weight. If all partitions have the same
     * weight then they will each receive a similar amount of load. A partition having a weight which is twice that
     * of another will receive twice the load. Weight values should generally be great than 200 in order to minimize
     * the risk of unexpected imbalances due to the way in which logical partitions are mapped to real partitions.
     *
     * @param partitionId
     *         The partition ID.
     * @param partition
     *         The partition.
     * @param weight
     *         The partition's weight, which should typically be over 200 and never negative.
     * @return This consistent hash map.
     */
    public ConsistentHashMap<P> put(final String partitionId, final P partition, final int weight) {
        Reject.ifNull(partitionId, "partitionId must be non-null");
        Reject.ifNull(partition, "partition must be non-null");
        Reject.ifTrue(weight < 0, "Weight must be a positive integer");

        final Node<P> node = new Node<>(partitionId, partition, weight);
        writeLock.lock();
        try {
            final TreeMap<Integer, Node<P>> newCircle = new TreeMap<>(circle);
            for (int i = 0; i < weight; i++) {
                newCircle.put(hashFunction.apply(partitionId + i), node);
            }

            final Map<String, P> newPartitions = new LinkedHashMap<>(partitions);
            newPartitions.put(partitionId, partition);

            // It doesn't matter that these assignments are not atomic.
            circle = newCircle;
            partitions = newPartitions;
        } finally {
            writeLock.unlock();
        }
        return this;
    }

    /**
     * Removes the partition that was previously added using the provided partition ID.
     *
     * @param partitionId
     *         The partition ID.
     * @return This consistent hash map.
     */
    public ConsistentHashMap<P> remove(final String partitionId) {
        Reject.ifNull(partitionId, "partitionId must be non-null");

        writeLock.lock();
        try {
            if (partitions.containsKey(partitionId)) {
                final TreeMap<Integer, Node<P>> newCircle = new TreeMap<>(circle);
                final Node<P> node = newCircle.remove(hashFunction.apply(partitionId + 0));
                for (int i = 1; i < node.weight; i++) {
                    newCircle.remove(hashFunction.apply(partitionId + i));
                }

                final Map<String, P> newPartitions = new LinkedHashMap<>(partitions);
                newPartitions.remove(partitionId);

                // It doesn't matter that these assignments are not atomic.
                circle = newCircle;
                partitions = newPartitions;
            }
        } finally {
            writeLock.unlock();
        }
        return this;
    }

    /**
     * Returns the partition from this map corresponding to the provided key's hash, or {@code null} if this map is
     * empty.
     *
     * @param key
     *         The key for which a corresponding partition is to be returned.
     * @return The partition from this map corresponding to the provided key's hash, or {@code null} if this map is
     * empty.
     */
    P get(final Object key) {
        final NavigableMap<Integer, Node<P>> circleSnapshot = circle;
        final Map.Entry<Integer, Node<P>> ceilingEntry = circleSnapshot.ceilingEntry(hashFunction.apply(key));
        if (ceilingEntry != null) {
            return ceilingEntry.getValue().partition;
        }
        final Map.Entry<Integer, Node<P>> firstEntry = circleSnapshot.firstEntry();
        return firstEntry != null ? firstEntry.getValue().partition : null;
    }

    /**
     * Returns a collection containing all of the partitions contained in this consistent hash map.
     *
     * @return A collection containing all of the partitions contained in this consistent hash map.
     */
    Collection<P> getAll() {
        return partitions.values();
    }

    /**
     * Returns the number of partitions in this consistent hash map.
     *
     * @return The number of partitions in this consistent hash map.
     */
    int size() {
        return partitions.size();
    }

    /**
     * Returns {@code true} if there are no partitions in this consistent hash map.
     *
     * @return {@code true} if there are no partitions in this consistent hash map.
     */
    boolean isEmpty() {
        return partitions.isEmpty();
    }

    /**
     * Returns a map whose keys are the partitions stored in this map and whose values are the actual weights associated
     * with each partition. The sum of the weights will be equal to 2^32.
     * <p/>
     * This method is intended for testing, but may one day be used in order to query the current status of the
     * load-balancer and, in particular, the weighting associated with each partition as a percentage.
     *
     * @return A map whose keys are the partitions stored in this map and whose values are the actual weights associated
     * with each partition.
     */
    @VisibleForTesting
    Map<P, Long> getWeights() {
        final NavigableMap<Integer, Node<P>> circleSnapshot = circle;
        final IdentityHashMap<P, Long> weights = new IdentityHashMap<>();
        Map.Entry<Integer, Node<P>> previousEntry = null;
        for (final Map.Entry<Integer, Node<P>> entry : circleSnapshot.entrySet()) {
            final long index = entry.getKey();
            final P partition = entry.getValue().partition;
            if (previousEntry == null) {
                // Special case for first value since the range begins with the last entry.
                final long range1 = (long) Integer.MAX_VALUE - circleSnapshot.lastEntry().getKey();
                final long range2 = index - Integer.MIN_VALUE;
                weights.put(partition, range1 + range2 + 1);
            } else {
                final long start = previousEntry.getKey();
                final long end = entry.getKey();
                if (weights.containsKey(partition)) {
                    weights.put(partition, weights.get(partition) + (end - start));
                } else {
                    weights.put(partition, end - start);
                }
            }
            previousEntry = entry;
        }
        return weights;
    }

    @Override
    public String toString() {
        return getWeights().toString();
    }

    /** A partition stored in the consistent hash map circle. */
    private static final class Node<P> {
        private final String partitionId;
        private final P partition;
        private final int weight;

        private Node(final String partitionId, final P partition, final int weight) {
            this.partitionId = partitionId;
            this.partition = partition;
            this.weight = weight;
        }

        @Override
        public String toString() {
            return partitionId;
        }
    }
}
