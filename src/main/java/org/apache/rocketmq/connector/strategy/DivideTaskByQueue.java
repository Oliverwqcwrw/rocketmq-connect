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
package org.apache.rocketmq.connector.strategy;

import io.openmessaging.KeyValue;
import io.openmessaging.internal.DefaultKeyValue;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.connector.config.ConfigDefine;
import org.apache.rocketmq.connector.config.DataType;
import org.apache.rocketmq.connector.config.TaskDivideConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DivideTaskByQueue extends TaskDivideStrategy {
    public List<KeyValue> divide(Map<String, List<MessageQueue>> topicRouteMap, TaskDivideConfig tdc) {

        List<KeyValue> config = new ArrayList<KeyValue>();

        for (String t: topicRouteMap.keySet()) {
            for (MessageQueue mq: topicRouteMap.get(t)) {
            }
        }

        return config;

    }
}
