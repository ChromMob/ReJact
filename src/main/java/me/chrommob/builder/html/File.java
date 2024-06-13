package me.chrommob.builder.html;

public class File {
    private final int parts;
    private final String id;
    private final String eventType;
    private final String lastModified;
    private final String name;
    private final String size;
    private final String type;
    private byte[] data;

    public File(int parts, String id, String eventType, String lastModified, String name, String size, String type, byte[] data) {
        this.parts = parts;
        this.id = id;
        this.eventType = eventType;
        this.lastModified = lastModified;
        this.name = name;
        this.size = size;
        this.type = type;
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

    public String type() {
        return type;
    }

    public byte[] data() {
        return data;
    }

    public void append(byte[] bytes, int offset, int length) {
        int currentLength = data == null ? 0 : data.length;
        this.data = new byte[currentLength + length];
        System.arraycopy(bytes, offset, this.data, 0, length);
    }

    @Override
    public String toString() {
        return "File{" +
                "id='" + id + '\'' +
                ", eventType='" + eventType + '\'' +
                ", lastModified='" + lastModified + '\'' +
                ", name='" + name + '\'' +
                ", size='" + size + '\'' +
                ", type='" + type + '\'' +
                '}';
    }
}
