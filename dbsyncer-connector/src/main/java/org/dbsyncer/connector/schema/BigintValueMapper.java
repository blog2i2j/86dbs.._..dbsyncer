package org.dbsyncer.connector.schema;

import org.dbsyncer.connector.AbstractValueMapper;
import org.dbsyncer.connector.ConnectorException;
import org.dbsyncer.connector.ConnectorMapper;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * @author AE86
 * @version 1.0.0
 * @date 2022/8/25 0:07
 */
public class BigintValueMapper extends AbstractValueMapper<Long> {

    @Override
    protected Long convert(ConnectorMapper connectorMapper, Object val) {
        if (val instanceof BigDecimal) {
            BigDecimal bitDec = (BigDecimal) val;
            return bitDec.longValue();
        }
        if (val instanceof BigInteger) {
            BigInteger bitInt = (BigInteger) val;
            return bitInt.longValue();
        }
        if (val instanceof Integer) {
            Integer integer = (Integer) val;
            return new Long(integer);
        }

        throw new ConnectorException(String.format("%s can not find type [%s], val [%s]", getClass().getSimpleName(), val.getClass(), val));
    }
}