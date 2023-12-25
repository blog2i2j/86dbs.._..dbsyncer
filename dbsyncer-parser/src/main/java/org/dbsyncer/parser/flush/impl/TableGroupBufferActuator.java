/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.parser.flush.impl;

import org.dbsyncer.common.config.TableGroupBufferConfig;
import org.dbsyncer.common.scheduled.ScheduledTaskService;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.common.util.ThreadPoolUtil;
import org.dbsyncer.common.util.UUIDUtil;
import org.dbsyncer.parser.flush.BufferRequest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.Executor;

/**
 * 表执行器（根据表消费数据，多线程批量写，按序执行）
 *
 * @Version 1.0.0
 * @Author AE86
 * @Date 2023-03-27 16:50
 */
@Component
public final class TableGroupBufferActuator extends GeneralBufferActuator implements Cloneable {

    @Resource
    private TableGroupBufferConfig tableGroupBufferConfig;

    @Resource
    private ScheduledTaskService scheduledTaskService;

    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    private String taskKey;

    private String tableGroupId;

    private volatile boolean running;

    @Override
    public void init() {
        // nothing to do
    }

    @Override
    protected boolean isRunning(BufferRequest request) {
        return running;
    }

    @Override
    public Executor getExecutor() {
        return threadPoolTaskExecutor;
    }

    public void buildConfig() {
        super.setConfig(tableGroupBufferConfig);
        super.buildQueueConfig();
        taskKey = UUIDUtil.getUUID();
        int coreSize = tableGroupBufferConfig.getThreadCoreSize();
        int maxSize = tableGroupBufferConfig.getMaxThreadSize();
        int queueCapacity = tableGroupBufferConfig.getThreadQueueCapacity();
        String threadNamePrefix = new StringBuilder("TableGroupExecutor-").append(tableGroupId).append(StringUtil.SYMBOL).toString();
        threadPoolTaskExecutor = ThreadPoolUtil.newThreadPoolTaskExecutor(coreSize, maxSize, queueCapacity, 30, threadNamePrefix);
        running = true;
        scheduledTaskService.start(taskKey, tableGroupBufferConfig.getBufferPeriodMillisecond(), this);
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    public void stop() {
        running = false;
        if (threadPoolTaskExecutor != null) {
            threadPoolTaskExecutor.shutdown();
        }
        scheduledTaskService.stop(taskKey);
    }

    public String getTableGroupId() {
        return tableGroupId;
    }

    public void setTableGroupId(String tableGroupId) {
        this.tableGroupId = tableGroupId;
    }
}