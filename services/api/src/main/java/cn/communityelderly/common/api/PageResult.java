package cn.communityelderly.common.api;

import java.util.Collections;
import java.util.List;

public class PageResult<T> {
    private List<T> items;
    private long page;
    private long pageSize;
    private long total;

    public PageResult() {
        this.items = Collections.emptyList();
    }

    public PageResult(List<T> items, long page, long pageSize, long total) {
        this.items = items != null ? items : Collections.emptyList();
        this.page = page;
        this.pageSize = pageSize;
        this.total = total;
    }

    public static <T> PageResult<T> of(List<T> items, long page, long pageSize, long total) {
        return new PageResult<>(items, page, pageSize, total);
    }

    public List<T> getItems() {
        return items;
    }

    public void setItems(List<T> items) {
        this.items = items;
    }

    public long getPage() {
        return page;
    }

    public void setPage(long page) {
        this.page = page;
    }

    public long getPageSize() {
        return pageSize;
    }

    public void setPageSize(long pageSize) {
        this.pageSize = pageSize;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }
}
