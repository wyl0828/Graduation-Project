package cn.communityelderly.modules.elder.dto;

public class ElderQueryDTO {

    private Integer page = 1;
    private Integer size = 10;
    private String name;
    private String archiveNo;
    private String status;

    public Integer getPage() {
        return page != null && page > 0 ? page : 1;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size != null && size > 0 ? size : 10;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getArchiveNo() {
        return archiveNo;
    }

    public void setArchiveNo(String archiveNo) {
        this.archiveNo = archiveNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
