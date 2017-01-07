package com.oxsoft.irasutoya;

import com.github.gfx.android.orma.annotation.Column;
import com.github.gfx.android.orma.annotation.Table;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Table
@NoArgsConstructor
@AllArgsConstructor
public class Label {
    @Column(indexed = true, unique = true)
    public String label;
}
