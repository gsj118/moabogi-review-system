package org.mnu.domain;
import java.util.ArrayList;
import java.util.List;
public class WorkForm {
    private String title = "", mediaType = "MOVIE", synopsis = "", releaseYear = "";
    private List<Long> genreIds = new ArrayList<Long>();
    private boolean confirmDuplicate;
    public String getTitle() { return title; } public void setTitle(String v) { title = v == null ? "" : v.trim(); }
    public String getMediaType() { return mediaType; } public void setMediaType(String v) { mediaType = v; }
    public String getSynopsis() { return synopsis; } public void setSynopsis(String v) { synopsis = v == null ? "" : v; }
    public String getReleaseYear() { return releaseYear; } public void setReleaseYear(String v) { releaseYear = v == null ? "" : v; }
    public List<Long> getGenreIds() { return genreIds; } public void setGenreIds(List<Long> v) { genreIds = v == null ? new ArrayList<Long>() : v; }
    public boolean isConfirmDuplicate() { return confirmDuplicate; } public void setConfirmDuplicate(boolean v) { confirmDuplicate = v; }
}
