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

package org.apache.rocketmq.connect.runtime.errors;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.connect.runtime.common.ConnectKeyValue;
import org.apache.rocketmq.connect.runtime.config.ConnectConfig;
import org.apache.rocketmq.connect.runtime.connectorwrapper.NameServerMocker;
import org.apache.rocketmq.connect.runtime.connectorwrapper.ServerResponseMocker;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class DeadLetterQueueReporterTest {

    private ServerResponseMocker nameSrvMocker;

    private ServerResponseMocker brokerMocker;

    @Before
    public void before() {
        nameSrvMocker = NameServerMocker.startByDefaultConf(9876, 10911);
        brokerMocker = ServerResponseMocker.startServer(10911, "Hello Wrold".getBytes(StandardCharsets.UTF_8));
    }

    @After
    public void after() {
        nameSrvMocker.shutdown();
        brokerMocker.shutdown();
    }

    @Test
    public void buildTest() {
        final DeadLetterQueueReporter reporter = buildDeadLetterQueueReporter();
        Assert.assertNotNull(reporter);
    }

    @Test
    public void reportTest() {
        ProcessingContext processingContext = new ProcessingContext();
        MessageExt messageExt = new MessageExt();
        messageExt.setBrokerName("mockBrokerName");
        messageExt.setQueueId(0);
        messageExt.setTopic("mockTopic");
        messageExt.setBody("Hello World".getBytes(StandardCharsets.UTF_8));
        processingContext.consumerRecord(messageExt);
        final DeadLetterQueueReporter reporter = buildDeadLetterQueueReporter();

        Assertions.assertThatCode(() -> reporter.report(processingContext)).doesNotThrowAnyException();
    }

    private DeadLetterQueueReporter buildDeadLetterQueueReporter() {
        ConnectKeyValue sinkConfig = new ConnectKeyValue();
        Map<String, String> properties = new HashMap<>();
        properties.put(DeadLetterQueueConfig.DLQ_TOPIC_NAME_CONFIG, "DEAD_LETTER_TOPIC");
        sinkConfig.setProperties(properties);
        ConnectConfig workerConfig = new ConnectConfig();
        final DeadLetterQueueReporter deadLetterQueueReporter = DeadLetterQueueReporter.build("fileSinkConnector", sinkConfig, workerConfig);
        return deadLetterQueueReporter;
    }
}
