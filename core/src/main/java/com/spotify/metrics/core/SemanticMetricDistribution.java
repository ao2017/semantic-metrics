/*
 * Copyright (C) 2016 - 2020 Spotify AB.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.spotify.metrics.core;


import com.google.common.annotations.VisibleForTesting;
import com.tdunning.math.stats.TDigest;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Semantic Metric implementation of {@link Distribution}.
 * This implementation ensures threadsafety for recording  data
 * and retrieving distribution point value.
 *
 * {@link SemanticMetricDistribution} is backed by Ted Dunning T-digest implementation.
 *
 * <p>{@link TDigest} "sketch" are generated by clustering real-valued samples and
 * retaining the mean and number of samples for each cluster.
 * The generated data structure is mergeable and produces fairly
 * accurate percentile even for long-tail distribution.
 *
 * <p> We are using T-digest compression level of 100.
 * With that level of compression, our own benchmark using Pareto distribution
 * dataset, shows P99 error rate  is less than 2% .
 * From P99.9 to P99.999 the error rate is slightly higher than 2%.
 *
 */
public final class SemanticMetricDistribution implements Distribution {

    private static final int COMPRESSION_DEFAULT_LEVEL = 100;
    private final AtomicReference<TDigest> distRef;

    SemanticMetricDistribution() {
        this.distRef = new AtomicReference<>(create());
    }

    @Override
    public synchronized void record(double val) {
        distRef.get().add(val);
    }

    @Override
    public java.nio.ByteBuffer getValueAndFlush() {
        TDigest curVal;
        synchronized (this) {
            curVal = distRef.getAndSet(create()); // reset tdigest
        }
        ByteBuffer byteBuffer = ByteBuffer.allocate(curVal.smallByteSize());
        curVal.asSmallBytes(byteBuffer);
        return byteBuffer;
    }


    @Override
    public long getCount() {
        return distRef.get().size();
    }

    @VisibleForTesting
    TDigest tDigest() {
        return distRef.get();
    }

    private TDigest create() {
        return TDigest.createDigest(COMPRESSION_DEFAULT_LEVEL);
    }
}
