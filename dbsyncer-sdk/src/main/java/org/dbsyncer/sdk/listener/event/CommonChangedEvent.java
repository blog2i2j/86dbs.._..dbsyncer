/**
 * DBSyncer Copyright 2019-2024 All Rights Reserved.
 */
package org.dbsyncer.sdk.listener.event;

import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.sdk.listener.ChangedEvent;
import org.dbsyncer.sdk.model.ChangedOffset;

/**
 * 通用变更事件
 *
 * @version 1.0.0
 * @Author AE86
 * @Date 2023-08-20 20:00
 */
public abstract class CommonChangedEvent implements ChangedEvent {

    /**
     * traceId
     */
    private String traceId = StringUtil.EMPTY;
    /**
     * 变更表名称
     */
    private String sourceTableName;
    /**
     * 变更事件
     */
    private String event;
    /**
     * 增量偏移量
     */
    private final ChangedOffset changedOffset = new ChangedOffset();

    @Override
    public String getTraceId() {
        return traceId;
    }

    @Override
    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    @Override
    public String getSourceTableName() {
        return sourceTableName;
    }

    @Override
    public void setSourceTableName(String sourceTableName) {
        this.sourceTableName = sourceTableName;
    }

    @Override
    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    @Override
    public ChangedOffset getChangedOffset() {
        return changedOffset;
    }

    public void setNextFileName(String nextFileName) {
        changedOffset.setNextFileName(nextFileName);
    }

    public void setPosition(Object position) {
        changedOffset.setPosition(position);
    }
}