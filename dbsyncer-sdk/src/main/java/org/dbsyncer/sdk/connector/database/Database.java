/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.sdk.connector.database;

import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.sdk.SdkException;
import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.model.PageSql;
import org.dbsyncer.sdk.plugin.ReaderContext;

import java.util.List;

public interface Database {

    /**
     * 获取dbs唯一标识码
     *
     * @return
     */
    default String generateUniqueCode() {
        return StringUtil.EMPTY;
    }

    /**
     * 查询语句表名和字段带上引号（默认不加）
     *
     * @return
     */
    default String buildSqlWithQuotation() {
        return StringUtil.EMPTY;
    }

    /**
     * 获取表名称(可自定义处理系统关键字，函数名)
     *
     * @param tableName
     * @return
     */
    default String buildTableName(String tableName) {
        return tableName;
    }

    /**
     * 获取字段名称(可自定义处理系统关键字，函数名)
     *
     * @param field
     * @return
     */
    default String buildFieldName(Field field) {
        return field.getName();
    }

    /**
     * 获取主键字段名称(可自定义处理系统关键字，函数名)
     *
     * @param primaryKeys
     * @return
     */
    default List<String> buildPrimaryKeys(List<String> primaryKeys) {
        return primaryKeys;
    }

    /**
     * 获取分页SQL
     *
     * @param config
     * @return
     */
    String getPageSql(PageSql config);

    /**
     * 获取分页游标SQL
     *
     * @param pageSql
     * @return
     */
    default String getPageCursorSql(PageSql pageSql) {
        return "";
    }

    /**
     * 获取分页参数
     *
     * @param context
     * @return
     */
    Object[] getPageArgs(ReaderContext context);

    /**
     * 获取游标分页参数
     *
     * @param context
     * @return
     */
    default Object[] getPageCursorArgs(ReaderContext context) {
        throw new SdkException("Unsupported override method getPageCursorArgs:" + getClass().getName());
    }

    /**
     * 健康检查
     *
     * @return
     */
    default String getValidationQuery() {
        return "select 1";
    }

    /**
     * 是否使用游标查询
     *
     * @return
     */
    default boolean enableCursor() {
        return false;
    }

}