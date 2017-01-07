package com.oxsoft.irasutoya;

import com.github.gfx.android.orma.annotation.Column;
import com.github.gfx.android.orma.annotation.Table;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Table
@NoArgsConstructor
@AllArgsConstructor
public class ImageCache {
    @Column(unique = true)
    public String url;

    @Column
    public String description;
}
