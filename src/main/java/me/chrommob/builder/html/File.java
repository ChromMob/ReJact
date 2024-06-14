package me.chrommob.builder.html;

import me.chrommob.builder.html.constants.Internal;

public class File {
    private final boolean exists;
    private final int parts;
    private final String id;
    private final String eventType;
    private final String lastModified;
    private final String name;
    private final String size;
    private final String type;
    private byte[] data;

    public File(boolean exists, int parts, String id, String eventType, String lastModified, String name, String size, String type, byte[] data) {
        this.exists = exists;
        this.parts = parts;
        this.id = id;
        this.eventType = eventType;
        this.lastModified = lastModified;
        this.name = name;
        this.size = size;
        this.type = type;
    }

    public boolean exists() {
        return exists;
    }

    public int parts() {
        return parts;
    }

    public String id() {
        return id;
    }

    public String eventType() {
        return eventType;
    }

    public String lastModified() {
        return lastModified;
    }

    public String name() {
        return name;
    }

    public String size() {
        return size;
    }

    public boolean isBiggerThan(int size, Internal.SIZE_UNITS unit) {
        double sizeInBytes = size * unit.getSize();
        return sizeInBytes <= Double.parseDouble(this.size());
    }

    public String type() {
        return type;
    }

    public byte[] data() {
        return data;
    }

    public void append(byte[] bytes, int offset, int length) {
        int currentLength = data == null ? 0 : data.length;
        byte[] newData = new byte[currentLength + length];
        if (data != null) {
            System.arraycopy(data, 0, newData, 0, currentLength);
        }
        System.arraycopy(bytes, offset, newData, currentLength, length);
        data = newData;
    }

    @Override
    public String toString() {
        return "File{" +
                "exists=" + exists +
                ", parts=" + parts +
                ", id='" + id + '\'' +
                ", eventType='" + eventType + '\'' +
                ", lastModified='" + lastModified + '\'' +
                ", name='" + name + '\'' +
                ", size='" + size + '\'' +
                ", type='" + type + '\'' +
                ", data=" + data +
                '}';
    }
}
