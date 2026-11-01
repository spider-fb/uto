package app;

import app.Models.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import java.net.http.HttpClient;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.stream.*;

public class Main {
  static final ObjectMapper M = new ObjectMapper()
      .enable(SerializationFeature.INDENT_OUTPUT)
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .setSerializationInclusion(JsonInclude.Include.NON_NULL);

  static <T> T read(Path p, TypeReference<T> t, T def) throws Exception {
    return Files.exists(p) && Files.size(p) > 0 ? M.readValue(p.toFile(), t) : def;
  }

  static void write(Path p, Object o) throws Exception {
    Files.createDirectories(p.getParent());
    M.writeValue(p.toFile(), o);
  }

  static String env(String k) { String v = System.getenv(k); return v == null ? "" : v.trim(); }

  static String msg(Exception e) {
    String m = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    return m.length() > 300 ? m.substring(0, 300) : m;
  }

  static String esc(String s) {
    return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;");
  }

  static String render(Book b) {
    StringBuilder h = new StringBuilder("<div dir=\"rtl\" style=\"text-align:right;line-height:1.9\">");
    if (b.cover != null)
      h.append("<div style=\"text-align:center\"><img src=\"").append(esc(b.cover)).append("\" alt=\"")
          .append(esc(b.title)).append("\" style=\"max-width:100%;height:auto;max-height:420px\"/></div>");
    h.append("<p>").append(esc(b.description)).append("</p>");
    h.append("<p><a href=\"").append(esc(b.link)).append("\" rel=\"nofollow noopener\" target=\"_blank\">فتح صفحة الكتاب</a></p>");
    h.append("<p><small>المصدر: ").append(esc(b.source)).append("</small></p></div>");
    return h.toString();
  }

  static long count(List<Book> l, String s) { return l.stream().filter(b -> s.equals(b.status)).count(); }

  public static void main(String[] args) throws Exception {
    Path data = Path.of(env("DATA_DIR").isEmpty() ? "data" : env("DATA_DIR"));
    Settings st = read(data.resolve("settings.json"), new TypeReference<Settings>() {}, new Settings());
    List<Source> sources = read(data.resolve("sources.json"), new TypeReference<List<Source>>() {}, new ArrayList<>());
    List<Book> books = read(data.resolve("published.json"), new TypeReference<List<Book>>() {}, new ArrayList<>());
    List<Run> history = read(data.resolve("execution-history.json"), new TypeReference<List<Run>>() {}, new ArrayList<>());
    if (!env("TARGET").isEmpty()) st.target = env("TARGET");
    String targetUrl = st.targets.get(st.target);

    Run run = new Run();
    run.startedAt = Instant.now().toString();
    run.target = st.target;
    HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(java.time.Duration.ofSeconds(20)).build();

    Set<String> keys = new HashSet<>(), titles = new HashSet<>();
    for (Book b : books) { keys.add(b.key); titles.add(Rss.norm(b.title)); }

    // 1-4: read RSS, extract books, skip duplicates
    List<SourceStatus> ss = new ArrayList<>();
    for (Source s : sources) {
      SourceStatus x = new SourceStatus();
      x.id = s.id; x.name = s.name; x.url = s.url; x.enabled = s.enabled;
      x.checkedAt = Instant.now().toString();
      ss.add(x);
      if (!s.enabled) continue;
      try {
        List<Book> items = Rss.fetch(http, s);
        x.ok = true; x.items = items.size();
        for (Book b : items) {
          if (keys.contains(b.key) || titles.contains(Rss.norm(b.title))) continue;
          keys.add(b.key); titles.add(Rss.norm(b.title));
          b.status = "pending"; b.discoveredAt = x.checkedAt;
          books.add(b); run.discovered++;
        }
      } catch (Exception e) {
        x.error = msg(e);
        run.errors.add("المصدر " + s.name + ": " + x.error);
      }
    }

    // 5-6: generate Blogger HTML, publish or save as drafts
    boolean live = "live".equals(st.publishMode);
    List<Book> queue = books.stream().filter(b -> "pending".equals(b.status))
        .limit(Math.max(0, st.maxPostsPerRun)).collect(Collectors.toList());
    if (!queue.isEmpty()) {
      String cid = env("GOOGLE_CLIENT_ID"), cs = env("GOOGLE_CLIENT_SECRET"), rt = env("GOOGLE_REFRESH_TOKEN");
      if (cid.isEmpty() || cs.isEmpty() || rt.isEmpty()) {
        run.errors.add("أسرار Google غير مضبوطة: تم تخطي النشر وبقيت الكتب قيد الانتظار");
      } else if (targetUrl == null && st.blogId.isEmpty()) {
        run.errors.add("الهدف '" + st.target + "' غير معرّف في settings.json");
      } else {
        try {
          Blogger bl = new Blogger(http, cid, cs, rt);
          String blogId = st.blogId.isEmpty() ? bl.blogId(targetUrl) : st.blogId;
          for (Book b : queue) {
            try {
              JsonNode r = bl.publish(blogId, b.title, render(b), st.labels, !live);
              b.postId = r.path("id").asText();
              b.postUrl = r.path("url").asText("");
              b.status = live ? "published" : "draft";
              b.publishedAt = Instant.now().toString();
              b.description = null; b.error = null;
              if (live) run.published++; else run.drafts++;
            } catch (Exception e) {
              b.attempts++; b.error = msg(e);
              run.errors.add("فشل نشر \"" + b.title + "\": " + b.error);
              if (b.attempts >= 3) { b.status = "failed"; run.failed++; }
              if (b.error.startsWith("HTTP 429") || b.error.startsWith("HTTP 403")) break; // quota / permission
            }
          }
        } catch (Exception e) {
          run.errors.add("Blogger: " + msg(e));
        }
      }
    }

    // 7-9: persist state, logs and dashboard data
    run.pending = (int) count(books, "pending");
    run.finishedAt = Instant.now().toString();
    history.add(run);
    while (history.size() > Math.max(1, st.maxHistory)) history.remove(0);
    write(data.resolve("published.json"), books);
    write(data.resolve("execution-history.json"), history);

    Map<String, Object> counts = new LinkedHashMap<>();
    counts.put("total", books.size());
    counts.put("published", count(books, "published"));
    counts.put("drafts", count(books, "draft"));
    counts.put("pending", count(books, "pending"));
    counts.put("failed", count(books, "failed"));
    List<Book> recent = books.stream()
        .sorted(Comparator.comparing((Book b) -> b.publishedAt != null ? b.publishedAt : b.discoveredAt).reversed())
        .limit(150).collect(Collectors.toList());
    List<Run> lastRuns = new ArrayList<>(history.subList(Math.max(0, history.size() - 30), history.size()));
    Collections.reverse(lastRuns);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("generatedAt", run.finishedAt);
    out.put("lastSync", run.finishedAt);
    out.put("target", st.target);
    out.put("targetUrl", targetUrl == null ? "" : targetUrl);
    out.put("publishMode", st.publishMode);
    out.put("counts", counts);
    out.put("sources", ss);
    out.put("books", recent);
    out.put("history", lastRuns);
    write(Path.of("docs/data/status.json"), out);

    System.out.printf("discovered=%d published=%d drafts=%d failed=%d pending=%d errors=%d%n",
        run.discovered, run.published, run.drafts, run.failed, run.pending, run.errors.size());
  }
}
