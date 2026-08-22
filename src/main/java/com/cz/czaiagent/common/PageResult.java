package com.cz.czaiagent.common;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 通用分页结果
 */
@Data
public class PageResult<T> implements Serializable {

    private List<T> records;

    private long total;

    private int current;

    private int pageSize;

    public static <T> PageResult<T> of(List<T> records, long total, int current, int pageSize) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(records);
        result.setTotal(total);
        result.setCurrent(current);
        result.setPageSize(pageSize);
        return result;
    }
}
