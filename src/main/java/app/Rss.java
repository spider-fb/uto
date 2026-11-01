package app;

import app.Models.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.regex.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

public class Rss {
  static String norm(String s) {
    return s == null ? "" : s.toLowerCase().replaceAll("[\\p{Punct}\\s]+", " ").trim();
  }

  static String key(String link) throws Exception {
    String n = link.toLowerCase().replaceAll("#.*$", "").replaceAll("/+$", "");
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(n.getBytes("UTF-8")));
  }

  static boolean ok(String u) { return u != null && (u.startsWith("http://") || u.startsWith("https://")); }

  static String first(Element e, String tag) {
    NodeList n = e.getElementsByTagName(tag);
    return n.getLength() == 0 ? "" : n.item(0).getTextContent().trim();
  }

  static String attr(Element e, String tag, String a) {
    NodeList n = e.getElementsByTagName(tag);
    return n.getLength() == 0 ? "" : ((Element) n.item(0)).getAttribute(a).trim();
  }

  static String atomLink(Element e) {
    NodeList n = e.getElementsByTagName("link");
    String fallback = "";
    for (int i = 0; i < n.getLength(); i++) {
      Element l = (Element) n.item(i);
      String rel = l.getAttribute("rel"), href = l.getAttribute("href");
      if (href.isEmpty()) continue;
      if (rel.isEmpty() || rel.equals("alternate")) return href;
      if (fallback.isEmpty()) fallback = href;
    }
    return fallback;
  }

  static String plain(String h) {
    String t = h.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ").replaceAll("(?s)<[^>]+>", " ");
    t = t.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&#39;", "'").replace("&amp;", "&");
    t = t.replaceAll("\\s+", " ").trim();
    return t.length() > 700 ? t.substring(0, 700) + "…" : t;
  }

  public static List<Book> fetch(HttpClient http, Source s) throws Exception {
    HttpRequest req = HttpRequest.newBuilder(URI.create(s.url)).timeout(Duration.ofSeconds(30))
        .header("User-Agent", "Mozilla/5.0 (rss-to-blogger)").GET().build();
    HttpResponse<byte[]> r = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
    if (r.statusCode() / 100 != 2) throw new IOException("HTTP " + r.statusCode());
    DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
    f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    f.setXIncludeAware(false);
    Document d = f.newDocumentBuilder().parse(new ByteArrayInputStream(r.body()));
    boolean atom = false;
    NodeList items = d.getElementsByTagName("item");
    if (items.getLength() == 0) { items = d.getElementsByTagName("entry"); atom = true; }
    Pattern img = Pattern.compile("<img[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    List<Book> out = new ArrayList<>();
    for (int i = 0; i < items.getLength(); i++) {
      Element e = (Element) items.item(i);
      String link = atom ? atomLink(e) : first(e, "link");
      if (link.isEmpty()) link = first(e, "guid");
      String title = plain(first(e, "title"));
      if (!ok(link) || title.isEmpty()) continue;
      String raw = first(e, "content:encoded");
      if (raw.isEmpty()) raw = first(e, "description");
      if (raw.isEmpty()) raw = first(e, "summary");
      if (raw.isEmpty()) raw = first(e, "content");
      String cover = attr(e, "media:thumbnail", "url");
      if (cover.isEmpty()) cover = attr(e, "media:content", "url");
      if (cover.isEmpty() && attr(e, "enclosure", "type").startsWith("image")) cover = attr(e, "enclosure", "url");
      if (cover.isEmpty()) { Matcher m = img.matcher(raw); if (m.find()) cover = m.group(1); }
      Book b = new Book();
      b.title = title; b.link = link; b.key = key(link); b.source = s.name;
      b.cover = ok(cover) ? cover : null;
      b.description = plain(raw);
      out.add(b);
    }
    return out;
  }
}
