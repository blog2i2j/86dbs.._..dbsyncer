package org.dbsyncer.connector.database.sqlbuilder;

import org.dbsyncer.connector.database.Database;

import java.util.List;

/**
 * @author AE86
 * @version 1.0.0
 * @date 2019/9/26 23:58
 */
public class SqlBuilderInsert implements SqlBuilder {

    @Override
    public String buildSql(String tableName, String pk, List<String> filedNames, String queryFilter, String quotation, Database database) {
        StringBuilder sql = new StringBuilder();
        StringBuilder fs = new StringBuilder();
        StringBuilder vs = new StringBuilder();
        int size = filedNames.size();
        int end = size - 1;
        for (int i = 0; i < size; i++) {
            // "USERNAME"
            fs.append(quotation).append(filedNames.get(i)).append(quotation);
            vs.append("?");
            //如果不是最后一个字段
            if (i < end) {
                fs.append(", ");
                vs.append(", ");
            }
        }
        // INSERT INTO "USER"("USERNAME","AGE") VALUES (?,?)
        sql.insert(0, "INSERT INTO ").append(quotation).append(tableName).append(quotation).append("(").append(fs).append(") VALUES (").append(vs).append(")");
        return sql.toString();
    }

}