package com.oxsoft.irasutoya;

import lombok.Value;

@Value
public class SearchQuery {
    public static final int TYPE_LABEL = 0;
    public static final int TYPE_LATEST = 1;
    public static final int TYPE_HISTORY = 2;

    private final int type;
    private final String name;
}
