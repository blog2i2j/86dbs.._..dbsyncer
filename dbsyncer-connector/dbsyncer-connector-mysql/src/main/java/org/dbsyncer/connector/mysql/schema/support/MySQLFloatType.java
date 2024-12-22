/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.connector.mysql.schema.support;

import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.schema.support.FloatType;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @Author 穿云
 * @Version 1.0.0
 * @Date 2024-11-26 22:59
 */
public final class MySQLFloatType extends FloatType {

    private enum TypeEnum {
        FLOAT("FLOAT"),
        FLOAT_UNSIGNED("FLOAT UNSIGNED"),
        FLOAT_UNSIGNED_ZEROFILL("FLOAT UNSIGNED ZEROFILL");

        private final String value;

        TypeEnum(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    @Override
    public Set<String> getSupportedTypeName() {
        return Arrays.stream(TypeEnum.values()).map(TypeEnum::getValue).collect(Collectors.toSet());
    }

    @Override
    protected Float merge(Object val, Field field) {
        if (val instanceof Number) {
            return ((Number) val).floatValue();
        }
        return throwUnsupportedException(val, field);
    }

    @Override
    protected Float getDefaultMergedVal() {
        return null;
    }

    @Override
    protected Object convert(Object val, Field field) {
        if (val instanceof Number) {
            return ((Number) val).shortValue();
        }
        return throwUnsupportedException(val, field);
    }

    @Override
    protected Object getDefaultConvertedVal() {
        return null;
    }

}