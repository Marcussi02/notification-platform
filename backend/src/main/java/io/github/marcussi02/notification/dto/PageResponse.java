package io.github.marcussi02.notification.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PageResponse<T> {

    private List<T> content;
    private long totalElements;
    private int totalPages;
    private int page;
    private int size;

    public static <E, D> PageResponse<D> from(Page<E> page, Function<E, D> mapper) {
        PageResponse<D> r = new PageResponse<>();
        r.content = page.getContent().stream().map(mapper).collect(Collectors.toList());
        r.totalElements = page.getTotalElements();
        r.totalPages = page.getTotalPages();
        r.page = page.getNumber();
        r.size = page.getSize();
        return r;
    }

    public List<T> getContent() { return content; }
    public void setContent(List<T> content) { this.content = content; }
    public long getTotalElements() { return totalElements; }
    public void setTotalElements(long totalElements) { this.totalElements = totalElements; }
    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
}
