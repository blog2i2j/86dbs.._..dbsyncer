/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.parser.consumer;

import org.dbsyncer.listener.ChangedEvent;
import org.dbsyncer.listener.event.DDLChangedEvent;
import org.dbsyncer.listener.Watcher;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.parser.flush.impl.BufferActuatorRouter;
import org.dbsyncer.parser.LogService;
import org.dbsyncer.parser.LogType;
import org.dbsyncer.parser.model.Mapping;
import org.dbsyncer.parser.model.Meta;
import org.dbsyncer.parser.model.TableGroup;

import java.util.List;
import java.util.Map;

/**
 * @Version 1.0.0
 * @Author AE86
 * @Date 2023-11-12 01:32
 */
public abstract class AbstractConsumer<E extends ChangedEvent> implements Watcher {
    private BufferActuatorRouter bufferActuatorRouter;
    private ProfileComponent profileComponent;
    private LogService logService;
    private Meta meta;
    protected Mapping mapping;
    protected List<TableGroup> tableGroups;

    public AbstractConsumer init(BufferActuatorRouter bufferActuatorRouter, ProfileComponent profileComponent, LogService logService, Meta meta, Mapping mapping, List<TableGroup> tableGroups) {
        this.bufferActuatorRouter = bufferActuatorRouter;
        this.profileComponent = profileComponent;
        this.logService = logService;
        this.meta = meta;
        this.mapping = mapping;
        this.tableGroups = tableGroups;
        postProcessBeforeInitialization();
        return this;
    }

    public abstract void postProcessBeforeInitialization();

    public abstract void onChange(E e);

    public void onDDLChanged(DDLChangedEvent event) {
    }

    @Override
    public void changeEvent(ChangedEvent event) {
        event.getChangedOffset().setMetaId(meta.getId());
        if (event instanceof DDLChangedEvent) {
            onDDLChanged((DDLChangedEvent) event);
            return;
        }
        onChange((E) event);
    }

    @Override
    public void flushEvent(Map<String, String> snapshot) {
        meta.setSnapshot(snapshot);
        profileComponent.editConfigModel(meta);
    }

    @Override
    public void errorEvent(Exception e) {
        logService.log(LogType.TableGroupLog.INCREMENT_FAILED, e.getMessage());
    }

    @Override
    public long getMetaUpdateTime() {
        return meta.getUpdateTime();
    }

    protected void bind(String tableGroupId) {
        bufferActuatorRouter.bind(meta.getId(), tableGroupId);
    }

    protected void execute(String tableGroupId, ChangedEvent event) {
        bufferActuatorRouter.execute(meta.getId(), tableGroupId, event);
    }
}