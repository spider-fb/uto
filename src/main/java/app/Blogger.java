package app;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

public class Blogger {
  static final String API = "https://www.googleapis.com/blogger/v3";
  static final ObjectMapper M = new ObjectMapper();
  final HttpClient http;
  final String token;

  static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

  public Blogger(HttpClient http, String cid, String secret, String refresh) throws Exception {
    this.http = http;
    String body = "client_id=" + enc(cid) + "&client_secret=" + enc(secret)
        + "&refresh_token=" + enc(refresh) + "&grant_type=refresh_token";
    HttpRequest r = HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/token"))
        .timeout(Duration.ofSeconds(30))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString(body)).build();
    this.token = call(r).path("access_token").asText();
    if (token.isEmpty()) throw new IOException("لم يتم الحصول على access token");
  }

  JsonNode call(HttpRequest r) throws Exception {
    HttpResponse<String> res = http.send(r, HttpResponse.BodyHandlers.ofString());
    if (res.statusCode() / 100 != 2) {
      String b = res.body();
      throw new IOException("HTTP " + res.statusCode() + " " + (b.length() > 200 ? b.substring(0, 200) : b));
    }
    return M.readTree(res.body());
  }

  HttpRequest.Builder auth(String path) {
    return HttpRequest.newBuilder(URI.create(API + path)).timeout(Duration.ofSeconds(30))
        .header("Authorization", "Bearer " + token);
  }

  public String blogId(String url) throws Exception {
    String id = call(auth("/blogs/byurl?url=" + enc(url)).GET().build()).path("id").asText();
    if (id.isEmpty()) throw new IOException("تعذر تحديد blogId للرابط " + url);
    return id;
  }

  public JsonNode publish(String blogId, String title, String html, List<String> labels, boolean draft)
      throws Exception {
    ObjectNode n = M.createObjectNode();
    n.put("kind", "blogger#post");
    n.put("title", title);
    n.put("content", html);
    if (labels != null && !labels.isEmpty()) n.set("labels", M.valueToTree(labels));
    return call(auth("/blogs/" + blogId + "/posts?isDraft=" + draft)
        .header("Content-Type", "application/json; charset=utf-8")
        .POST(HttpRequest.BodyPublishers.ofString(M.writeValueAsString(n))).build());
  }
}
