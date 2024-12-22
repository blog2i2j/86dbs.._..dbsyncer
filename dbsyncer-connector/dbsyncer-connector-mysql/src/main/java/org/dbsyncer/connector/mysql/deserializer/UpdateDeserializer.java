/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.connector.mysql.deserializer;

import com.github.shyiko.mysql.binlog.event.TableMapEventData;
import com.github.shyiko.mysql.binlog.event.deserialization.UpdateRowsEventDataDeserializer;
import com.github.shyiko.mysql.binlog.io.ByteArrayInputStream;

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;

public class UpdateDeserializer extends UpdateRowsEventDataDeserializer {

    private final DatetimeV2Deserialize datetimeV2Deserialize = new DatetimeV2Deserialize();
    private final JsonBinaryDeserialize jsonBinaryDeserialize = new JsonBinaryDeserialize();

    public UpdateDeserializer(Map<Long, TableMapEventData> tableMapEventByTableId) {
        super(tableMapEventByTableId);
    }

    @Override
    protected Serializable deserializeTiny(ByteArrayInputStream inputStream) throws IOException {
        return inputStream.readInteger(1);
    }

    @Override
    protected Serializable deserializeDatetimeV2(int meta, ByteArrayInputStream inputStream) throws IOException {
        return datetimeV2Deserialize.deserializeDatetimeV2(meta, inputStream);
    }

    @Override
    protected byte[] deserializeJson(int meta, ByteArrayInputStream inputStream) throws IOException {
        return jsonBinaryDeserialize.deserializeJson(meta, inputStream);
    }

}