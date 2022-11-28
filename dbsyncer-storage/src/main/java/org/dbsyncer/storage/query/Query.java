package org.dbsyncer.storage.query;

import org.dbsyncer.storage.enums.StorageEnum;

import java.util.ArrayList;
import java.util.List;

/**
 * @author AE86
 * @version 1.0.0
 * @date 2019/11/16 22:56
 */
public class Query {

    /**
     * {@link StorageEnum}
     */
    private StorageEnum type;

    private String metaId;

    private List<Param> params;

    private BooleanQuery booleanQuery;

    private int pageNum = 1;

    private int pageSize = 20;

    private boolean enableHighLightSearch;

    /**
     * 查询应用性能，不用排序查询，只用查询总量即可
     */
    private boolean queryTotal;

    public Query() {
        this.params = new ArrayList<>();
    }

    public Query(int pageNum, int pageSize) {
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.params = new ArrayList<>();
    }

    public void addFilter(String key, String value) {
        addFilter(key, value, false, false);
    }

    public void addFilter(String key, String value, boolean highlighter) {
        addFilter(key, value, highlighter, false);
    }

    public void addFilter(String key, String value, boolean highlighter, boolean number) {
        params.add(new Param(key, value, highlighter, number));
        if (highlighter) {
            enableHighLightSearch = highlighter;
        }
    }

    public StorageEnum getType() {
        return type;
    }

    public void setType(StorageEnum type) {
        this.type = type;
    }

    public String getMetaId() {
        return metaId;
    }

    public void setMetaId(String metaId) {
        this.metaId = metaId;
    }

    public List<Param> getParams() {
        return params;
    }

    public void setParams(List<Param> params) {
        this.params = params;
    }

    public BooleanQuery getBooleanQuery() {
        return booleanQuery;
    }

    public void setBooleanQuery(BooleanQuery booleanQuery) {
        this.booleanQuery = booleanQuery;
    }

    public int getPageNum() {
        return pageNum;
    }

    public void setPageNum(int pageNum) {
        this.pageNum = pageNum;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public boolean isEnableHighLightSearch() {
        return enableHighLightSearch;
    }

    public boolean isQueryTotal() {
        return queryTotal;
    }

    public void setQueryTotal(boolean queryTotal) {
        this.queryTotal = queryTotal;
    }
}