/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.connector.base;

import org.dbsyncer.common.model.Result;
import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.sdk.config.CommandConfig;
import org.dbsyncer.sdk.config.DDLConfig;
import org.dbsyncer.sdk.connector.AbstractConnector;
import org.dbsyncer.sdk.connector.ConnectorInstance;
import org.dbsyncer.sdk.listener.Listener;
import org.dbsyncer.sdk.model.ConnectorConfig;
import org.dbsyncer.sdk.model.MetaInfo;
import org.dbsyncer.sdk.model.Table;
import org.dbsyncer.sdk.plugin.PluginContext;
import org.dbsyncer.sdk.plugin.ReaderContext;
import org.dbsyncer.sdk.spi.ConnectorService;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 连接器工厂
 *
 * @Author AE86
 * @Version 1.0.0
 * @Date 2019-09-18 23:30
 */
@Component
public class ConnectorFactory implements DisposableBean {

    private final Map<String, ConnectorInstance> pool = new ConcurrentHashMap<>();

    private final Map<String, ConnectorService> service = new ConcurrentHashMap<>();

    private final Set<String> connectorTypes = new HashSet<>();

    @PostConstruct
    private void init() {
        ServiceLoader<ConnectorService> services = ServiceLoader.load(ConnectorService.class, Thread.currentThread().getContextClassLoader());
        for (ConnectorService s : services) {
            service.putIfAbsent(s.getConnectorType(), s);
            connectorTypes.add(s.getConnectorType());
        }
    }

    @Override
    public void destroy() {
        pool.values().forEach(this::disconnect);
        pool.clear();
    }

    /**
     * 建立连接，返回缓存连接对象
     *
     * @param config
     */
    public ConnectorInstance connect(ConnectorConfig config) {
        Assert.notNull(config, "ConnectorConfig can not be null.");
        ConnectorService connectorService = getConnectorService(config);
        String cacheKey = connectorService.getConnectorInstanceCacheKey(config);
        if (!pool.containsKey(cacheKey)) {
            synchronized (pool) {
                if (!pool.containsKey(cacheKey)) {
                    ConnectorInstance instance = connectorService.connect(config);
                    Assert.isTrue(connectorService.isAlive(instance), "连接配置异常");
                    pool.putIfAbsent(cacheKey, instance);
                }
            }
        }
        try {
            ConnectorInstance connectorInstance = pool.get(cacheKey);
            ConnectorInstance clone = (ConnectorInstance) connectorInstance.clone();
            clone.setConfig(config);
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new ConnectorException(e);
        }
    }

    /**
     * 获取监听器
     *
     * @param connectorType
     * @param listenerType
     * @return
     */
    public Listener getListener(String connectorType, String listenerType) {
        return getConnectorService(connectorType).getListener(listenerType);
    }

    /**
     * 检查连接配置是否可用
     *
     * @param config
     * @return
     */
    public boolean isAlive(ConnectorConfig config) {
        Assert.notNull(config, "ConnectorConfig can not be null.");
        ConnectorService connectorService = getConnectorService(config);
        String cacheKey = connectorService.getConnectorInstanceCacheKey(config);
        if (pool.containsKey(cacheKey)) {
            return connectorService.isAlive(pool.get(cacheKey));
        }
        return false;
    }

    /**
     * 获取配置表
     *
     * @param connectorInstance
     * @return
     */
    public List<Table> getTable(ConnectorInstance connectorInstance) {
        Assert.notNull(connectorInstance, "ConnectorInstance can not be null.");
        List tableList = getConnectorService(connectorInstance.getConfig()).getTable(connectorInstance);
        // 按升序展示表
        Collections.sort(tableList, Comparator.comparing(Table::getName));
        return tableList;
    }

    /**
     * 获取配置表元信息
     *
     * @param connectorInstance
     * @param tableName
     * @return
     */
    public MetaInfo getMetaInfo(ConnectorInstance connectorInstance, String tableName) {
        Assert.notNull(connectorInstance, "ConnectorInstance can not be null.");
        Assert.hasText(tableName, "tableName can not be empty.");
        return getConnectorService(connectorInstance.getConfig()).getMetaInfo(connectorInstance, tableName);
    }

    public Object getPosition(ConnectorInstance connectorInstance) {
        Assert.notNull(connectorInstance, "ConnectorInstance can not be null.");
        return getConnectorService(connectorInstance.getConfig()).getPosition(connectorInstance);
    }

    /**
     * 获取连接器同步参数
     *
     * @param sourceCommandConfig
     * @param targetCommandConfig
     * @return
     */
    public Map<String, String> getCommand(CommandConfig sourceCommandConfig, CommandConfig targetCommandConfig) {
        Assert.notNull(sourceCommandConfig, "SourceCommandConfig can not be null.");
        Assert.notNull(targetCommandConfig, "TargetCommandConfig can not be null.");
        Map<String, String> map = new HashMap<>();
        Map sCmd = getConnectorService(sourceCommandConfig.getConnectorType()).getSourceCommand(sourceCommandConfig);
        if (!CollectionUtils.isEmpty(sCmd)) {
            map.putAll(sCmd);
        }

        Map tCmd = getConnectorService(targetCommandConfig.getConnectorType()).getTargetCommand(targetCommandConfig);
        if (!CollectionUtils.isEmpty(tCmd)) {
            map.putAll(tCmd);
        }
        return map;
    }

    public long getCount(ConnectorInstance connectorInstance, Map<String, String> command) {
        Assert.notNull(connectorInstance, "ConnectorInstance can not null");
        Assert.notNull(command, "command can not null");
        return getConnectorService(connectorInstance.getConfig()).getCount(connectorInstance, command);
    }

    public Result reader(ReaderContext context) {
        ConnectorInstance connectorInstance = context.getSourceConnectorInstance();
        Assert.notNull(connectorInstance, "ConnectorInstance can not null");
        Assert.notNull(context, "ReaderContext can not null");
        Result result = getConnectorService(connectorInstance.getConfig()).reader(connectorInstance, context);
        Assert.notNull(result, "Connector reader result can not null");
        return result;
    }

    public Result writer(PluginContext context) {
        ConnectorInstance targetInstance = context.getTargetConnectorInstance();
        Assert.notNull(targetInstance, "targetConnectorInstance can not null");
        ConnectorService targetConnector = getConnectorService(targetInstance.getConfig());
        if (targetConnector instanceof AbstractConnector) {
            AbstractConnector conn = (AbstractConnector) targetConnector;
            try {
                // 支持标准解析器
                if (context.isEnableSchemaResolver() && targetConnector.getSchemaResolver() != null) {
                    conn.convertProcessBeforeWriter(context, targetConnector.getSchemaResolver());
                } else {
                    conn.convertProcessBeforeWriter(context, targetInstance);
                }
            } catch (Exception e) {
                Result result = new Result();
                result.getError().append(e.getMessage());
                result.addFailData(context.getTargetList());
                return result;
            }
        }

        Result result = targetConnector.writer(targetInstance, context);
        Assert.notNull(result, "Connector writer batch result can not null");
        return result;
    }

    public Result writerDDL(ConnectorInstance connectorInstance, DDLConfig ddlConfig) {
        Assert.notNull(connectorInstance, "ConnectorInstance can not null");
        Result result = getConnectorService(connectorInstance.getConfig()).writerDDL(connectorInstance, ddlConfig);
        Assert.notNull(result, "Connector writer batch result can not null");
        return result;
    }

    public ConnectorService getConnectorService(ConnectorConfig connectorConfig) {
        Assert.notNull(connectorConfig, "ConnectorConfig can not null");
        return getConnectorService(connectorConfig.getConnectorType());
    }

    public ConnectorService getConnectorService(String connectorType) {
        ConnectorService connectorService = service.get(connectorType);
        if (connectorService == null) {
            Assert.isTrue(false, "Unsupported connector type:" + connectorType);
        }
        return connectorService;
    }

    public Set<String> getConnectorTypeAll() {
        return connectorTypes;
    }

    /**
     * 断开连接
     *
     * @param config
     * @return
     */
    public void disconnect(ConnectorConfig config) {
        Assert.notNull(config, "ConnectorConfig can not be null.");
        String cacheKey = getConnectorService(config).getConnectorInstanceCacheKey(config);
        ConnectorInstance connectorInstance = pool.get(cacheKey);
        if (connectorInstance != null) {
            disconnect(connectorInstance);
            pool.remove(cacheKey);
        }
    }

    private void disconnect(ConnectorInstance connectorInstance) {
        Assert.notNull(connectorInstance, "ConnectorInstance can not be null.");
        getConnectorService(connectorInstance.getConfig()).disconnect(connectorInstance);
    }

}