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

package org.apache.rocketmq.connect.runtime.connectorwrapper;

import com.google.common.collect.Lists;
import io.openmessaging.KeyValue;
import io.openmessaging.connector.api.component.task.source.SourceTask;
import io.openmessaging.connector.api.data.ConnectRecord;
import io.openmessaging.connector.api.data.Field;
import io.openmessaging.connector.api.data.RecordConverter;
import io.openmessaging.connector.api.data.RecordOffset;
import io.openmessaging.connector.api.data.RecordPartition;
import io.openmessaging.connector.api.data.Schema;
import io.openmessaging.connector.api.data.SchemaBuilder;
import io.openmessaging.internal.DefaultKeyValue;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.connect.runtime.common.ConnectKeyValue;
import org.apache.rocketmq.connect.runtime.config.ConnectorConfig;
import org.apache.rocketmq.connect.runtime.config.SourceConnectorConfig;
import org.apache.rocketmq.connect.runtime.config.WorkerConfig;
import org.apache.rocketmq.connect.runtime.connectorwrapper.testimpl.TestConverter;
import org.apache.rocketmq.connect.runtime.connectorwrapper.testimpl.TestPositionManageServiceImpl;
import org.apache.rocketmq.connect.runtime.connectorwrapper.testimpl.TestSourceTask;
import org.apache.rocketmq.connect.runtime.controller.isolation.Plugin;
import org.apache.rocketmq.connect.runtime.errors.ErrorMetricsGroup;
import org.apache.rocketmq.connect.runtime.errors.RetryWithToleranceOperator;
import org.apache.rocketmq.connect.runtime.errors.ToleranceType;
import org.apache.rocketmq.connect.runtime.metrics.ConnectMetrics;
import org.apache.rocketmq.connect.runtime.service.PositionManagementService;
import org.apache.rocketmq.connect.runtime.stats.ConnectStatsManager;
import org.apache.rocketmq.connect.runtime.stats.ConnectStatsService;
import org.apache.rocketmq.connect.runtime.utils.ConnectorTaskId;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.omg.CORBA.TIMEOUT;

@RunWith(MockitoJUnitRunner.class)
public class WorkerSourceTaskTest {

    ExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private WorkerSourceTask workerSourceTask;

    private WorkerConfig workerConfig;
    private ConnectorTaskId connectorTaskId = new ConnectorTaskId("testConnector", 1);
    private SourceTask sourceTask = new TestSourceTask();
    private ConnectKeyValue connectKeyValue = new ConnectKeyValue();
    private PositionManagementService positionManagementService = new TestPositionManageServiceImpl();
    private RecordConverter recordConverter = new TestConverter();
    @Mock
    private DefaultMQProducer defaultMQProducer;
    private AtomicReference<WorkerState> workerState = new AtomicReference<>(WorkerState.STARTED);
    private ConnectStatsManager connectStatsManager;
    private ConnectStatsService connectStatsService = new ConnectStatsService();
    private TransformChain<ConnectRecord> transformChain;
    private KeyValue keyValue = new DefaultKeyValue();
    @Mock
    private Plugin plugin;
    private RetryWithToleranceOperator retryWithToleranceOperator = new RetryWithToleranceOperator(1000, 1000, ToleranceType.ALL, new ErrorMetricsGroup(new ConnectorTaskId(), new ConnectMetrics(new WorkerConfig())));
    private ServerResponseMocker nameServerMocker;

    private ServerResponseMocker brokerMocker;

    private WorkerSourceTask.SourceTaskMetricsGroup sourceTaskMetricsGroup;

    private ConnectMetrics connectMetrics;

    private WorkerSourceTask.CalcSourceRecordWrite calcSourceRecordWrite;

    @Before
    public void before() throws MQClientException, InterruptedException {
        workerConfig = new WorkerConfig();
        workerConfig.setNamesrvAddr("127.0.0.1:9876");
        connectStatsManager = new ConnectStatsManager(workerConfig);
        connectKeyValue.put(SourceConnectorConfig.CONNECT_TOPICNAME, "TEST_TOPIC");
        keyValue.put(ConnectorConfig.TRANSFORMS, "testTransform");
        keyValue.put("transforms-testTransform-class", "org.apache.rocketmq.connect.runtime.connectorwrapper.TestTransform");
        transformChain = new TransformChain<>(keyValue, plugin);
        workerSourceTask = new WorkerSourceTask(workerConfig, connectorTaskId, sourceTask, this.getClass().getClassLoader(),
                connectKeyValue, positionManagementService, recordConverter, recordConverter, defaultMQProducer, workerState,
                connectStatsManager, connectStatsService, transformChain, retryWithToleranceOperator, null, new ConnectMetrics(new WorkerConfig()));
        nameServerMocker = NameServerMocker.startByDefaultConf(9876, 10911);
        brokerMocker = ServerResponseMocker.startServer(10911, "Hello World".getBytes(StandardCharsets.UTF_8));

        connectMetrics = new ConnectMetrics(workerConfig);
        sourceTaskMetricsGroup = new WorkerSourceTask.SourceTaskMetricsGroup(connectorTaskId, connectMetrics);
        calcSourceRecordWrite = new WorkerSourceTask.CalcSourceRecordWrite(100, sourceTaskMetricsGroup);

        workerSourceTask.doInitializeAndStart();

        Runnable runnable = () -> workerSourceTask.execute();
        executorService.submit(runnable);
    }

    @After
    public void after() {
        executorService.shutdown();
        workerSourceTask.close();
        nameServerMocker.shutdown();
        brokerMocker.shutdown();
    }

    @Test
    public void removeMetricsTest() {
        Assertions.assertThatCode(() -> workerSourceTask.removeMetrics()).doesNotThrowAnyException();
    }

    @Test
    public void updateCommittableOffsetsTest() {
        Assertions.assertThatCode(() ->  workerSourceTask.updateCommittableOffsets()).doesNotThrowAnyException();
    }

    @Test
    public void prepareToSendRecordTest() {
        ConnectRecord connectRecord = new ConnectRecord(this.buildRecordPartition(), this.buildRecordOffset(), System.currentTimeMillis());
        final Optional<RecordOffsetManagement.SubmittedPosition> position = workerSourceTask.prepareToSendRecord(connectRecord);
        Assert.assertEquals(this.buildRecordOffset(), position.get().getPosition().getOffset());
    }

    @Test
    public void convertTransformedRecordTest() {
        ConnectRecord connectRecord = new ConnectRecord(this.buildRecordPartition(), this.buildRecordOffset(), System.currentTimeMillis());
        connectRecord.setData("Hello World");
        final Schema build = SchemaBuilder.string().build();
        build.setFields(Lists.newArrayList(new Field(0, "testFiled", build)));
        connectRecord.setSchema(build);
        final Message message = workerSourceTask.convertTransformedRecord("testTopic", connectRecord);
        Assert.assertEquals("testTopic", message.getTopic());
    }

    @Test
    public void initializeAndStartTest() {
        Assertions.assertThatCode(() -> workerSourceTask.initializeAndStart()).doesNotThrowAnyException();
    }

    @Test
    public void finalOffsetCommitTest() {
        Assertions.assertThatCode(() -> workerSourceTask.finalOffsetCommit(true)).doesNotThrowAnyException();
    }

    @Test
    public void commitOffsetsTest() {
        Assertions.assertThatCode(() -> workerSourceTask.commitOffsets()).doesNotThrowAnyException();
    }

    @Test
    public void commitSourceTaskTest() {
        Assertions.assertThatCode(() -> workerSourceTask.commitSourceTask()).doesNotThrowAnyException();
    }

    @Test
    public void recordPollReturnedTest() {
        Assertions.assertThatCode(() -> workerSourceTask.recordPollReturned(10, 1000)).doesNotThrowAnyException();
    }


    private RecordPartition buildRecordPartition() {
        Map<String, String> partition = new HashMap<>();
        partition.put("defaultPartition", "defaultPartition");
        RecordPartition recordPartition = new RecordPartition(partition);
        return recordPartition;
    }

    private RecordOffset buildRecordOffset() {
        Map<String, Long> offset = new HashMap<>();
        offset.put("defaultOffset", 1L);
        RecordOffset recordOffset = new RecordOffset(offset);
        return recordOffset;
    }

    @Test
    public void recordPollTest() {
        Assertions.assertThatCode(() ->  sourceTaskMetricsGroup.recordPoll(100, 1000)).doesNotThrowAnyException();
    }

    @Test
    public void recordWriteTest() {
        Assertions.assertThatCode(() ->  sourceTaskMetricsGroup.recordWrite(100)).doesNotThrowAnyException();
    }

    @Test
    public void skipRecordTest() {
        Assertions.assertThatCode(() ->  calcSourceRecordWrite.skipRecord()).doesNotThrowAnyException();
    }

    @Test
    public void completeRecordTest(){
        Assertions.assertThatCode(() ->  calcSourceRecordWrite.completeRecord()).doesNotThrowAnyException();
    }

    @Test
    public void retryRemainingTest() {
        Assertions.assertThatCode(() ->  calcSourceRecordWrite.retryRemaining()).doesNotThrowAnyException();
    }


}
