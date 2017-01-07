package com.oxsoft.irasutoya;

import com.github.gfx.android.orma.annotation.Column;
import com.github.gfx.android.orma.annotation.Table;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Table
@NoArgsConstructor
@AllArgsConstructor
public class ImageCache {
    @Column(indexed = true, unique = true)
    public String key;

    @Column(indexed = true)
    public String url;

    @Column(indexed = true)
    public String description;

    @Column(indexed = true)
    public long updated;
}
