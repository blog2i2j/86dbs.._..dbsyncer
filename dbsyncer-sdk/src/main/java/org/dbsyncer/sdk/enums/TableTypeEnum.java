/**
 * DBSyncer Copyright 2020-2025 All Rights Reserved.
 */
package org.dbsyncer.sdk.enums;

/**
 * 表类型
 *
 * @author AE86
 * @version 1.0.0
 * @date 2021/08/26 21:13
 */
public enum TableTypeEnum {

    /**
     * 表
     */
    TABLE("TABLE"),

    /**
     * 视图
     */
    VIEW("VIEW"),

    /**
     * 物化视图
     */
    MATERIALIZED_VIEW("MATERIALIZED VIEW");

    private final String code;

    TableTypeEnum(String code) {
        this.code = code;
    }

    /**
     * 是否视图类型
     *
     * @param type
     * @return
     */
    public static boolean isView(String type) {
        return VIEW.getCode().equals(type);
    }

    public String getCode() {
        return code;
    }

}