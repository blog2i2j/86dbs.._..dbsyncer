import com.google.protobuf.ByteString;
import org.dbsyncer.storage.binlog.AbstractBinlogRecorder;
import org.dbsyncer.storage.binlog.impl.BinlogColumnValue;
import org.dbsyncer.storage.binlog.proto.BinlogMessage;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Queue;

/**
 * @author AE86
 * @version 1.0.0
 * @date 2022/7/1 23:44
 */
public class BinlogMessageFieldTypeTest {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private BinlogColumnValue value = new BinlogColumnValue();

    private MessageTest messageTest = new MessageTest();

    @Test
    public void testMessageNumber() {
        // short
        short s = 32767;
        logger.info("short1:{}", s);
        ByteString shortBytes = messageTest.serializeValue(s);
        logger.info("bytes:{}", shortBytes.toByteArray());
        value.setValue(shortBytes);
        short s2 = value.asShort();
        logger.info("short2:{}", s2);

        // int
        int i = 1999999999;
        logger.info("int1:{}", i);
        ByteString intBytes = messageTest.serializeValue(i);
        logger.info("bytes:{}", intBytes.toByteArray());
        value.setValue(intBytes);
        int i2 = value.asInteger();
        logger.info("int2:{}", i2);

        // long
        long l = 8999999999999999999L;
        logger.info("long1:{}", l);
        ByteString longBytes = messageTest.serializeValue(l);
        logger.info("bytes:{}", longBytes.toByteArray());
        value.setValue(longBytes);
        long l2 = value.asLong();
        logger.info("long2:{}", l2);

        // float
        float f = 99999999999999999999999999999999999.99999999999999999999999999999999999f;
        logger.info("float1:{}", f);
        ByteString floatBytes = messageTest.serializeValue(f);
        logger.info("bytes:{}", floatBytes.toByteArray());
        value.setValue(floatBytes);
        float f2 = value.asFloat();
        logger.info("float2:{}", f2);

        // double
        double d = 999999.9999999999999999999999999d;
        logger.info("double1:{}", d);
        ByteString doubleBytes = messageTest.serializeValue(d);
        logger.info("bytes:{}", doubleBytes.toByteArray());
        value.setValue(doubleBytes);
        double d2 = value.asDouble();
        logger.info("double2:{}", d2);

        // double
        BigDecimal b = new BigDecimal(8888888.888888888888888f);
        logger.info("bigDecimal1:{}", b);
        ByteString bigDecimalBytes = messageTest.serializeValue(b);
        logger.info("bytes:{}", bigDecimalBytes.toByteArray());
        value.setValue(bigDecimalBytes);
        BigDecimal b2 = value.asBigDecimal();
        logger.info("bigDecimal2:{}", b2);

        // boolean
        boolean bool = true;
        logger.info("bool1:{}", bool);
        ByteString boolBytes = messageTest.serializeValue(bool);
        logger.info("bytes:{}", boolBytes.toByteArray());
        value.setValue(boolBytes);
        Boolean bool2 = value.asBoolean();
        logger.info("bool2:{}", bool2);
    }

    @Test
    public void testMessageDate() {
        // timestamp
        Timestamp timestamp = Timestamp.valueOf(LocalDateTime.now());
        logger.info("timestamp1:{}, l:{}", timestamp, timestamp.getTime());
        ByteString timestampBytes = messageTest.serializeValue(timestamp);
        logger.info("bytes:{}", timestampBytes.toByteArray());
        value.setValue(timestampBytes);
        Timestamp timestamp2 = value.asTimestamp();
        logger.info("timestamp2:{}, l:{}", timestamp2, timestamp2.getTime());

        // date
        Date date = new Date(timestamp.getTime());
        logger.info("date1:{}, l:{}", date, date.getTime());
        ByteString dateBytes = messageTest.serializeValue(date);
        logger.info("bytes:{}", dateBytes.toByteArray());
        value.setValue(dateBytes);
        Date date2 = value.asDate();
        logger.info("date2:{}, l:{}", date2, date2.getTime());

        // time
        Time time = new Time(timestamp.getTime());
        logger.info("time1:{}, l:{}", time, time.getTime());
        ByteString timeBytes = messageTest.serializeValue(time);
        logger.info("bytes:{}", timeBytes.toByteArray());
        value.setValue(timeBytes);
        Time time2 = value.asTime();
        logger.info("time2:{}, l:{}", time2, time2.getTime());
    }

    final class MessageTest extends AbstractBinlogRecorder {

        @Override
        protected Queue getQueue() {
            return null;
        }

        @Override
        protected Object deserialize(BinlogMessage message) {
            return null;
        }

        @Override
        protected Object resolveValue(int type, ByteString v) {
            return super.resolveValue(type, v);
        }

        @Override
        protected ByteString serializeValue(Object v) {
            return super.serializeValue(v);
        }
    }

}