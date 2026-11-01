package app;

import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;

/** تفويض لمرة واحدة على جهازك: يطبع REFRESH_TOKEN لتضعه في GitHub Secrets. */
public class OAuthSetup {
  static String b64(byte[] b) { return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }

  public static void main(String[] a) throws Exception {
    String cid = System.getenv("GOOGLE_CLIENT_ID"), cs = System.getenv("GOOGLE_CLIENT_SECRET");
    if (cid == null || cs == null) {
      System.err.println("اضبط GOOGLE_CLIENT_ID و GOOGLE_CLIENT_SECRET كمتغيرات بيئة أولاً.");
      return;
    }
    int port = 8765;
    String redirect = "http://127.0.0.1:" + port;
    byte[] rnd = new byte[32];
    new SecureRandom().nextBytes(rnd);
    String verifier = b64(rnd);
    String challenge = b64(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.UTF_8)));
    String state = UUID.randomUUID().toString();
    CompletableFuture<String> code = new CompletableFuture<>();
    HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
    srv.createContext("/", ex -> {
      String q = ex.getRequestURI().getRawQuery();
      Map<String, String> p = new HashMap<>();
      if (q != null) for (String kv : q.split("&")) {
        String[] s = kv.split("=", 2);
        if (s.length == 2) p.put(s[0], URLDecoder.decode(s[1], StandardCharsets.UTF_8));
      }
      String m = "طلب غير صالح";
      if (state.equals(p.get("state")) && p.get("code") != null) {
        code.complete(p.get("code"));
        m = "تم التفويض. يمكنك إغلاق هذه الصفحة والعودة إلى الطرفية.";
      }
      byte[] b = m.getBytes(StandardCharsets.UTF_8);
      ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
      ex.sendResponseHeaders(200, b.length);
      ex.getResponseBody().write(b);
      ex.close();
    });
    srv.start();
    String url = "https://accounts.google.com/o/oauth2/v2/auth?response_type=code&access_type=offline&prompt=consent"
        + "&client_id=" + Blogger.enc(cid) + "&redirect_uri=" + Blogger.enc(redirect)
        + "&scope=" + Blogger.enc("https://www.googleapis.com/auth/blogger")
        + "&state=" + state + "&code_challenge=" + challenge + "&code_challenge_method=S256";
    System.out.println("افتح هذا الرابط في المتصفح ووافق على الصلاحية:\n\n" + url + "\n");
    String c = code.get(5, TimeUnit.MINUTES);
    srv.stop(0);
    String body = "code=" + Blogger.enc(c) + "&client_id=" + Blogger.enc(cid) + "&client_secret=" + Blogger.enc(cs)
        + "&redirect_uri=" + Blogger.enc(redirect) + "&grant_type=authorization_code&code_verifier=" + verifier;
    HttpResponse<String> r = HttpClient.newHttpClient().send(
        HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/token"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
        HttpResponse.BodyHandlers.ofString());
    JsonNode j = new ObjectMapper().readTree(r.body());
    if (!j.hasNonNull("refresh_token")) {
      System.err.println("فشل: " + r.body());
      return;
    }
    System.out.println("GOOGLE_REFRESH_TOKEN=" + j.get("refresh_token").asText());
    System.out.println("\nضعه في GitHub Secrets. لا تشاركه ولا تحفظه في أي ملف.");
  }
}
