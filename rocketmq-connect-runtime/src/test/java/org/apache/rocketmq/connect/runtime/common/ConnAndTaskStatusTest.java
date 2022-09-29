/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.rocketmq.connect.runtime.common;

import org.apache.rocketmq.connect.runtime.connectorwrapper.status.AbstractStatus;
import org.apache.rocketmq.connect.runtime.connectorwrapper.status.ConnectorStatus;
import org.apache.rocketmq.connect.runtime.connectorwrapper.status.TaskStatus;
import org.apache.rocketmq.connect.runtime.utils.ConnectorTaskId;
import org.junit.Assert;
import org.junit.Test;

public class ConnAndTaskStatusTest {

    private ConnAndTaskStatus connAndTaskStatus = new ConnAndTaskStatus();

    private static final String WORKER_ID = "defaultWorker";

    private static final Long GENERATION = 1L;

    private static final String TRACE = "testTrace";

    private static final String CONNECTOR_STATUS_ID = "defaultWorker";

    @Test
    public void getOrAddTest() {
        ConnectorTaskId connectorTaskId = new ConnectorTaskId();
        connectorTaskId.setConnector("testConnector");
        connectorTaskId.setTask(1);
        final ConnAndTaskStatus.CacheEntry<TaskStatus> add = connAndTaskStatus.getOrAdd(connectorTaskId);
        Assert.assertNull(add.get());

        final ConnAndTaskStatus.CacheEntry<ConnectorStatus> connector = connAndTaskStatus.getOrAdd("testConnector");
        Assert.assertNull(connector.get());
    }

    @Test
    public void cacheEntryTest() {
        ConnAndTaskStatus.CacheEntry cacheEntry = new ConnAndTaskStatus.CacheEntry<>();
        ConnectorStatus connectorStatus = new ConnectorStatus();
        connectorStatus.setState(AbstractStatus.State.RUNNING);
        connectorStatus.setGeneration(GENERATION);
        connectorStatus.setWorkerId(WORKER_ID);
        connectorStatus.setTrace(TRACE);
        connectorStatus.setId(CONNECTOR_STATUS_ID);
        cacheEntry.put(connectorStatus);

        final AbstractStatus status = cacheEntry.get();
        Assert.assertEquals(GENERATION, status.getGeneration());
        Assert.assertEquals(WORKER_ID, status.getWorkerId());
        Assert.assertEquals(AbstractStatus.State.RUNNING, status.getState());
        Assert.assertEquals(TRACE, status.getTrace());
        Assert.assertEquals(CONNECTOR_STATUS_ID, status.getId());

        cacheEntry.delete();
        Assert.assertEquals(true, cacheEntry.isDeleted());

        final boolean canWrite = cacheEntry.canWrite(status);
        Assert.assertTrue(canWrite);
    }
}
