/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.connector.sqlserver;

import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.connector.sqlserver.cdc.DqlSqlServerListener;
import org.dbsyncer.connector.sqlserver.validator.DqlSqlServerConfigValidator;
import org.dbsyncer.sdk.config.ReaderConfig;
import org.dbsyncer.sdk.connector.ConfigValidator;
import org.dbsyncer.sdk.connector.database.AbstractDQLConnector;
import org.dbsyncer.sdk.constant.DatabaseConstant;
import org.dbsyncer.sdk.enums.ListenerTypeEnum;
import org.dbsyncer.sdk.listener.DatabaseQuartzListener;
import org.dbsyncer.sdk.listener.Listener;
import org.dbsyncer.sdk.model.PageSql;

import java.util.List;

/**
 * DQLSqlServer连接器实现
 *
 * @Author AE86
 * @Version 1.0.0
 * @Date 2022-05-22 22:56
 */
public final class DQLSqlServerConnector extends AbstractDQLConnector {

    private final String TYPE = "DqlSqlServer";
    private final DqlSqlServerConfigValidator configValidator = new DqlSqlServerConfigValidator();

    @Override
    public String getConnectorType() {
        return TYPE;
    }

    @Override
    public ConfigValidator getConfigValidator() {
        return null;
    }

    @Override
    public Listener getListener(String listenerType) {
        if (ListenerTypeEnum.isTiming(listenerType)) {
            return new DatabaseQuartzListener();
        }

        if (ListenerTypeEnum.isLog(listenerType)) {
            return new DqlSqlServerListener();
        }
        return null;
    }

    @Override
    public String getPageSql(PageSql config) {
        List<String> primaryKeys = config.getPrimaryKeys();
        String orderBy = StringUtil.join(primaryKeys, ",");
        return String.format(DatabaseConstant.SQLSERVER_PAGE_SQL, orderBy, config.getQuerySql());
    }

    @Override
    public Object[] getPageArgs(ReaderConfig config) {
        int pageSize = config.getPageSize();
        int pageIndex = config.getPageIndex();
        return new Object[]{(pageIndex - 1) * pageSize + 1, pageIndex * pageSize};
    }
}