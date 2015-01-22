/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gridgain.grid.kernal.processors.cache;

import org.apache.ignite.*;
import org.apache.ignite.configuration.*;
import org.apache.ignite.internal.*;
import org.apache.ignite.transactions.*;
import org.gridgain.grid.kernal.processors.cache.distributed.dht.*;
import org.gridgain.grid.kernal.processors.cache.distributed.near.*;
import org.apache.ignite.spi.discovery.tcp.*;
import org.apache.ignite.spi.discovery.tcp.ipfinder.*;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.*;
import org.gridgain.testframework.junits.common.*;

import java.util.*;
import java.util.concurrent.*;

import static org.apache.ignite.transactions.IgniteTxConcurrency.*;
import static org.apache.ignite.transactions.IgniteTxIsolation.*;

/**
 * Checks multithreaded put/get cache operations on one node.
 */
public abstract class IgniteTxConcurrentGetAbstractTest extends GridCommonAbstractTest {
    /** Debug flag. */
    private static final boolean DEBUG = false;

    /** */
    protected static TcpDiscoveryIpFinder ipFinder = new TcpDiscoveryVmIpFinder(true);

    /** */
    private static final int THREAD_NUM = 20;

    /**
     * Default constructor.
     *
     */
    protected IgniteTxConcurrentGetAbstractTest() {
        super(true /** Start grid. */);
    }

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String gridName) throws Exception {
        IgniteConfiguration cfg = super.getConfiguration(gridName);

        TcpDiscoverySpi spi = new TcpDiscoverySpi();

        spi.setIpFinder(ipFinder);

        cfg.setDiscoverySpi(spi);

        return cfg;
    }

    /**
     * @param g Grid.
     * @return Near cache.
     */
    GridNearCacheAdapter<String, Integer> near(Ignite g) {
        return (GridNearCacheAdapter<String, Integer>)((GridKernal)g).<String, Integer>internalCache();
    }

    /**
     * @param g Grid.
     * @return DHT cache.
     */
    GridDhtCacheAdapter<String, Integer> dht(Ignite g) {
        return near(g).dht();
    }

    /**
     * JUnit.
     *
     * @throws Exception If failed.
     */
    public void testPutGet() throws Exception {
        // Random key.
        final String key = UUID.randomUUID().toString();

        final Ignite ignite = grid();

        ignite.cache(null).put(key, "val");

        GridCacheEntryEx<String,Integer> dhtEntry = dht(ignite).peekEx(key);

        if (DEBUG)
            info("DHT entry [hash=" + System.identityHashCode(dhtEntry) + ", entry=" + dhtEntry + ']');

        String val = txGet(ignite, key);

        assertNotNull(val);

        info("Starting threads: " + THREAD_NUM);

        multithreaded(new Callable<String>() {
            @Override public String call() throws Exception {
                return txGet(ignite, key);
            }
        }, THREAD_NUM, "getter-thread");
    }

    /**
     * @param ignite Grid.
     * @param key Key.
     * @return Value.
     * @throws Exception If failed.
     */
    private String txGet(Ignite ignite, String key) throws Exception {
        try (IgniteTx tx = ignite.cache(null).txStart(PESSIMISTIC, REPEATABLE_READ)) {
            GridCacheEntryEx<String, Integer> dhtEntry = dht(ignite).peekEx(key);

            if (DEBUG)
                info("DHT entry [hash=" + System.identityHashCode(dhtEntry) + ", xid=" + tx.xid() +
                    ", entry=" + dhtEntry + ']');

            String val = ignite.<String, String>cache(null).get(key);

            assertNotNull(val);
            assertEquals("val", val);

            tx.commit();

            return val;
        }
    }
}
