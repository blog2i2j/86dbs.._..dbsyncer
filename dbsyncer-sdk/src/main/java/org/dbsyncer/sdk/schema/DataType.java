/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.sdk.schema;

import org.dbsyncer.sdk.enums.DataTypeEnum;
import org.dbsyncer.sdk.model.Field;

/**
 * @Author 穿云
 * @Version 1.0.0
 * @Date 2024-11-23 23:00
 */
public interface DataType {

    /**
     * 转换为标准数据类型
     *
     * @param val 转换值
     * @param field 数据类型
     * @return Object
     */
    Object mergeValue(Object val, Field field);

    /**
     * 转换为指定数据类型
     *
     * @param val 转换值
     * @param field 数据类型
     * @return Object
     */
    Object convertValue(Object val, Field field);

    /**
     * 返回数据类型
     *
     * @return
     */
    DataTypeEnum getType();
}