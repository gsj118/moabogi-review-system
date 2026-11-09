package org.mnu.domain;

public class RecordForm {
    private long workId;
    private int version;
    private String viewedOn = "", rating = "", review = "", action = "PAST", token = "";
    public long getWorkId() { return workId; } public void setWorkId(long v) { workId = v; }
    public int getVersion() { return version; } public void setVersion(int v) { version = v; }
    public String getViewedOn() { return viewedOn; } public void setViewedOn(String v) { viewedOn = v == null ? "" : v; }
    public String getRating() { return rating; } public void setRating(String v) { rating = v == null ? "" : v; }
    public String getReview() { return review; } public void setReview(String v) { review = v == null ? "" : v; }
    public String getAction() { return action; } public void setAction(String v) { action = v; }
    public String getToken() { return token; } public void setToken(String v) { token = v; }
}
