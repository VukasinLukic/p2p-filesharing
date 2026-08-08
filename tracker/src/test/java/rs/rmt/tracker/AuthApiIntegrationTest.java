package rs.rmt.tracker;

import com.sun.net.httpserver.HttpServer;
import rs.rmt.tracker.registry.TrackerRegistry;
import rs.rmt.tracker.testutil.Assert;
import rs.rmt.tracker.users.SessionStore;
import rs.rmt.tracker.users.UserStore;
import rs.rmt.tracker.util.Json;
import rs.rmt.tracker.util.Router;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/** Drives the auth endpoints over real HTTP against the real router - register, login, me, logout. */
public class AuthApiIntegrationTest {
    private HttpServer server;
    private String baseUrl;
    private final HttpClient client = HttpClient.newHttpClient();

    private void startServer() throws Exception {
        Router router = TrackerMain.buildRouter(new TrackerRegistry(), new UserStore(), new SessionStore());
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", router);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    private void stopServer() {
        if (server != null) server.stop(0);
    }

    public void testRegisterLoginMeLogoutFlow() throws Exception {
        startServer();
        try {
            HttpResponse<String> registerResp = post("/api/auth/register", Json.stringify(Json.obj(
                    "username", "milica", "password", "tajna123", "displayName", "Milica M.")), null);
            Assert.assertEquals(201, registerResp.statusCode(), "registration creates the account");
            Map<String, Object> registerBody = Json.parseObject(registerResp.body());
            Assert.assertNotNull(registerBody.get("token"), "registration returns a session token right away");

            HttpResponse<String> loginResp = post("/api/auth/login",
                    Json.stringify(Json.obj("username", "milica", "password", "tajna123")), null);
            Assert.assertEquals(200, loginResp.statusCode(), "login with correct credentials succeeds");
            Map<String, Object> loginBody = Json.parseObject(loginResp.body());
            String token = (String) loginBody.get("token");
            Assert.assertNotNull(token, "login returns a token");

            @SuppressWarnings("unchecked")
            Map<String, Object> user = (Map<String, Object>) loginBody.get("user");
            Assert.assertEquals("Milica M.", user.get("displayName"), "profile comes back with the login");
            Assert.assertFalse(loginResp.body().contains("passwordHash"), "login response must not leak the hash");

            HttpResponse<String> meResp = get("/api/auth/me", token);
            Assert.assertEquals(200, meResp.statusCode(), "the token identifies the account");
            Assert.assertEquals("milica", Json.parseObject(meResp.body()).get("username"), "/me returns that account");

            HttpResponse<String> logoutResp = post("/api/auth/logout", "", token);
            Assert.assertEquals(200, logoutResp.statusCode(), "logout succeeds");
            Assert.assertEquals(401, get("/api/auth/me", token).statusCode(),
                    "the token must stop working after logout");
        } finally {
            stopServer();
        }
    }

    public void testLoginFailuresAndUnauthenticatedAccess() throws Exception {
        startServer();
        try {
            post("/api/auth/register", Json.stringify(Json.obj(
                    "username", "marko", "password", "lozinka123")), null);

            HttpResponse<String> wrongPassword = post("/api/auth/login",
                    Json.stringify(Json.obj("username", "marko", "password", "pogresna")), null);
            HttpResponse<String> unknownUser = post("/api/auth/login",
                    Json.stringify(Json.obj("username", "nepostojeci", "password", "lozinka123")), null);

            Assert.assertEquals(401, wrongPassword.statusCode(), "wrong password is unauthorised");
            Assert.assertEquals(401, unknownUser.statusCode(), "unknown user is unauthorised");
            Assert.assertEquals(wrongPassword.body(), unknownUser.body(),
                    "both failures must look identical so usernames can't be enumerated");

            Assert.assertEquals(401, get("/api/auth/me", null).statusCode(), "/me without a token is 401");
            Assert.assertEquals(401, get("/api/auth/me", "izmisljen-token").statusCode(), "/me with a bogus token is 401");
        } finally {
            stopServer();
        }
    }

    public void testInvalidRegistrationIsRejectedWith400() throws Exception {
        startServer();
        try {
            HttpResponse<String> shortPassword = post("/api/auth/register",
                    Json.stringify(Json.obj("username", "validno", "password", "kratk")), null);
            Assert.assertEquals(400, shortPassword.statusCode(), "too-short password is a client error, not a 500");

            post("/api/auth/register", Json.stringify(Json.obj("username", "zauzet", "password", "lozinka123")), null);
            HttpResponse<String> duplicate = post("/api/auth/register",
                    Json.stringify(Json.obj("username", "zauzet", "password", "lozinka123")), null);
            Assert.assertEquals(400, duplicate.statusCode(), "duplicate username is rejected");
            Assert.assertNotNull(Json.parseObject(duplicate.body()).get("error"), "rejection explains why");
        } finally {
            stopServer();
        }
    }

    public void testUserDirectoryListsProfilesWithoutCredentials() throws Exception {
        startServer();
        try {
            post("/api/auth/register", Json.stringify(Json.obj(
                    "username", "ana", "password", "lozinka123", "displayName", "Ana A.")), null);
            post("/api/auth/register", Json.stringify(Json.obj(
                    "username", "bojan", "password", "lozinka123")), null);

            HttpResponse<String> resp = get("/api/users", null);
            Assert.assertEquals(200, resp.statusCode(), "user directory is readable");
            List<Object> users = Json.parseArray(resp.body());
            Assert.assertEquals(2, users.size(), "both accounts are listed");
            Assert.assertFalse(resp.body().contains("passwordHash"), "directory must not leak credentials");
            Assert.assertFalse(resp.body().contains("passwordSalt"), "directory must not leak salts");
        } finally {
            stopServer();
        }
    }

    public void testPeerEndpointsStillWorkWithoutAnAccount() throws Exception {
        startServer();
        try {
            // File sharing must not start requiring a login just because accounts now exist.
            HttpResponse<String> resp = post("/api/peers/register", Json.stringify(Json.obj("port", 9001)), null);
            Assert.assertEquals(200, resp.statusCode(), "peer registration stays anonymous");
            Assert.assertNotNull(Json.parseObject(resp.body()).get("peerId"), "peer still gets an id");
        } finally {
            stopServer();
        }
    }

    // ---------- helpers ----------

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(baseUrl + path)).GET();
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
