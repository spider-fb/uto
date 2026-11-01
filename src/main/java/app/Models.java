package app;

import java.util.*;

public class Models {
  public static class Source { public String id, name, url; public boolean enabled = true; }

  public static class Settings {
    public String target = "test";
    public Map<String, String> targets = new LinkedHashMap<>();
    public String blogId = "";
    public String publishMode = "draft"; // draft | live
    public int maxPostsPerRun = 5;
    public int maxHistory = 100;
    public List<String> labels = new ArrayList<>();
  }

  public static class Book {
    public String key, title, link, source, cover, description;
    public String status; // pending | draft | published | failed
    public String postId, postUrl, discoveredAt, publishedAt, error;
    public int attempts;
  }

  public static class Run {
    public String startedAt, finishedAt, target;
    public int discovered, published, drafts, failed, pending;
    public List<String> errors = new ArrayList<>();
  }

  public static class SourceStatus {
    public String id, name, url, checkedAt, error;
    public boolean enabled = true, ok;
    public int items;
  }
}
