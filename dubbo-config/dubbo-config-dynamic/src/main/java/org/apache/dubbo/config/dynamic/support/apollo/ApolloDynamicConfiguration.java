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
package org.apache.dubbo.config.dynamic.support.apollo;

import com.ctrip.framework.apollo.Config;
import com.ctrip.framework.apollo.ConfigChangeListener;
import com.ctrip.framework.apollo.ConfigService;
import com.ctrip.framework.apollo.enums.PropertyChangeType;
import com.ctrip.framework.apollo.model.ConfigChange;
import com.ctrip.framework.apollo.model.ConfigChangeEvent;
import org.apache.dubbo.common.Constants;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.config.dynamic.AbstractDynamicConfiguration;
import org.apache.dubbo.config.dynamic.ConfigChangeType;
import org.apache.dubbo.config.dynamic.ConfigType;
import org.apache.dubbo.config.dynamic.ConfigurationListener;

import java.util.HashSet;
import java.util.Set;

/**
 *
 */
public class ApolloDynamicConfiguration extends AbstractDynamicConfiguration {
    private static final String APOLLO_ENV_KEY = "env";
    private static final String APOLLO_ADDR_KEY = "apollo.meta";
    private static final String APOLLO_CLUSTER_KEY = "apollo.cluster";
    /**
     * support two namespaces: application -> dubbo
     */
    private Config dubboConfig;
    private Config appConfig;

    public ApolloDynamicConfiguration() {

    }

    @Override
    public void init() {
        /**
         * Instead of using Dubbo's configuration, I would suggest use the original configuration method Apollo provides.
         */
//        String configEnv = env.getCompositeConf().getString(ENV_KEY);
//        String configCluster = env.getCompositeConf().getString(CLUSTER_KEY);
        String configEnv = url.getParameter(Constants.CONFIG_ENV_KEY);
        String configAddr = url.getAddress();
        String configCluster = url.getParameter(Constants.CONFIG_CLUSTER_KEY);
        if (configEnv != null) {
            System.setProperty(APOLLO_ENV_KEY, configEnv);
        }
        if (StringUtils.isEmpty(configEnv) && !Constants.ANYHOST_VALUE.equals(configAddr)) {
            System.setProperty(APOLLO_ADDR_KEY, configAddr);
        }
        if (configCluster != null) {
            System.setProperty(APOLLO_CLUSTER_KEY, configCluster);
        }

        dubboConfig = ConfigService.getConfig("dubbo");
        appConfig = ConfigService.getAppConfig();
    }

    @Override
    public void addListener(String key, ConfigurationListener listener) {
        Set<String> keys = new HashSet<>(1);
        keys.add(key);
        this.appConfig.addChangeListener(new ApolloListener(listener), keys);
        this.dubboConfig.addChangeListener(new ApolloListener(listener), keys);
    }

    @Override
    public String getConfig(String key, String group, ConfigurationListener listener) {
        Set<String> keys = new HashSet<>(1);
        keys.add(key);
        this.appConfig.addChangeListener(new ApolloListener(listener), keys);
        this.dubboConfig.addChangeListener(new ApolloListener(listener), keys);
        return getInternalProperty(key, group, 0L);
    }

    @Override
    protected String getInternalProperty(String key, String group, long timeout) {
        String value = appConfig.getProperty(key, null);
        if (value == null) {
            value = dubboConfig.getProperty(key, null);
        }

        return value;
    }

    public ConfigChangeType getChangeType(PropertyChangeType changeType) {
        if (changeType.equals(PropertyChangeType.DELETED)) {
            return ConfigChangeType.DELETED;
        }
        return ConfigChangeType.MODIFIED;
    }

    private class ApolloListener implements ConfigChangeListener {
        private ConfigurationListener listener;
        private URL url;

        public ApolloListener(ConfigurationListener listener) {
            this(listener.getUrl(), listener);
        }

        public ApolloListener(URL url, ConfigurationListener listener) {
            this.url = url;
            this.listener = listener;
        }

        @Override
        public void onChange(ConfigChangeEvent changeEvent) {
            for (String key : changeEvent.changedKeys()) {
                ConfigChange change = changeEvent.getChange(key);
                if (change.getPropertyName().endsWith(Constants.CONFIGURATORS_SUFFIX)) {
                    listener.process(new org.apache.dubbo.config.dynamic.ConfigChangeEvent(key, change.getNewValue(), ConfigType.CONFIGURATORS, getChangeType(change.getChangeType())));
                } else if (change.getPropertyName().endsWith(Constants.ROUTERS_SUFFIX)) {
                    listener.process(new org.apache.dubbo.config.dynamic.ConfigChangeEvent(key, change.getNewValue(), ConfigType.ROUTERS, getChangeType(change.getChangeType())));
                }
            }
        }
    }

}