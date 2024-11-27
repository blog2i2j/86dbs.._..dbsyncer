/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.connector.mysql.schema.support;

import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.schema.support.ByteType;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @Author 穿云
 * @Version 1.0.0
 * @Date 2024-11-26 22:59
 */
public final class MySQLByteType extends ByteType {

    enum TypeEnum {
        TINYINT
    }

    @Override
    public Set<String> getSupportedTypeName() {
        return Arrays.stream(TypeEnum.values()).map(type -> type.name()).collect(Collectors.toSet());
    }

    @Override
    protected Byte merge(Object val, Field field) {
        return 0;
    }

    @Override
    protected Byte getDefaultMergedVal() {
        return 0;
    }

    @Override
    protected Object convert(Object val, Field field) {
        return null;
    }

    @Override
    protected Object getDefaultConvertedVal() {
        return null;
    }

}