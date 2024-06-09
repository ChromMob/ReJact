package me.chrommob.builder.html.tags;


import me.chrommob.builder.html.constants.HeadingLevel;

public class HeadingTag extends Tag {
    public HeadingTag(HeadingLevel level) {
        super("h" + (level.ordinal() + 1));
    }
}
