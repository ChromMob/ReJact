package me.chrommob.builder.html;

public class FileProgress {
    private final int part;
    private final int total;

    public FileProgress(int part, int total, String id) {
        this.part = part;
        this.total = total;
    }

    public String getPercentage() {
        return (int) (100 * (double) part / (double) total) + "%";
    }

    public boolean isComplete() {
        return part == total;
    }
}
