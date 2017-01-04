package com.oxsoft.irasutoya;

import lombok.Value;

@Value
public class SearchResult {
    private final Image[] images;
    private final String next;

    @Value
    public static class Image {
        private final String url;
        private final String description;
    }
}
