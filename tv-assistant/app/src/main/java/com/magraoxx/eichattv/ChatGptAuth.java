package com.magraoxx.eichattv;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class ChatGptAuth {
    public interface Callback { void onSuccess(String message); void onError(String message); }

    private static final String AUTH = "https://auth.openai.com/api/accounts/authorize";
    private static final String TOKEN = "https://auth.openai.com/api/accounts/oauth/token";
    private static final String JWKS = "https://auth.openai.com/.well-known/jwks.json";
    private static final String RESOURCE = "https://api.openai.com/v1";
    private static final String SCOPES = "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct";
    private static final String PREFS = "ei_chat_auth";
    private static final String HOST = "host_id";
    private static final String PROFILE = "chatgpt_profile";
    private static final String AGENT = "Ei Chat TV";

    private final Context context;
    private final SecureSecretStore secure;

    public ChatGptAuth(Context context) {
        this.context = context.getApplicationContext();
        this.secure = new SecureSecretStore(context);
    }

    public boolean isConnected() {
        JSONObject p = profile();
        return p != null && p.optString("client_id").startsWith("oaiapp_") && p.optString("scope").contains("chatgpt.tokens.use.direct");
    }

    public String accountLabel() {
        JSONObject p = profile();
        if (p == null) return "Não conectado";
        String email = p.optString("email", "");
        return email.isEmpty() ? "ChatGPT conectado" : email;
    }

    public void signIn(Callback callback) {
        new Thread(() -> {
            ServerSocket server = null;
            try {
                server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
                server.setSoTimeout(10 * 60 * 1000);
                int port = server.getLocalPort();
                String redirect = "http://127.0.0.1:" + port + "/auth/callback";
                String state = random(32), nonce = random(32), verifier = random(64);
                String challenge = b64url(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
                String hostId = hostId();

                JSONObject old = profile();
                String clientId = old == null ? "dynamic_agent_client" : old.optString("client_id", "dynamic_agent_client");
                Uri.Builder b = Uri.parse(AUTH).buildUpon()
                        .appendQueryParameter("client_id", clientId)
                        .appendQueryParameter("redirect_uri", redirect)
                        .appendQueryParameter("response_type", "code")
                        .appendQueryParameter("scope", SCOPES)
                        .appendQueryParameter("resource", RESOURCE)
                        .appendQueryParameter("state", state)
                        .appendQueryParameter("nonce", nonce)
                        .appendQueryParameter("code_challenge_method", "S256")
                        .appendQueryParameter("code_challenge", challenge)
                        .appendQueryParameter("ext_agent_host_id", hostId);
                if ("dynamic_agent_client".equals(clientId)) b.appendQueryParameter("agent_name_hint", AGENT);
                else {
                    String hint = old.optString("id_token", "");
                    if (!hint.isEmpty()) b.appendQueryParameter("id_token_hint", hint);
                    String email = old.optString("email", "");
                    if (!email.isEmpty()) b.appendQueryParameter("login_hint", email);
                }

                Intent browser = new Intent(Intent.ACTION_VIEW, b.build());
                browser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(browser);

                Socket socket = server.accept();
                BufferedReader r = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                String request = r.readLine();
                Map<String,String> q = parseRequest(request);
                String html = "<html><body><h2>Ei Chat TV</h2><p>Login recebido. Você pode voltar para a TV.</p></body></html>";
                byte[] hb = html.getBytes(StandardCharsets.UTF_8);
                OutputStream so = socket.getOutputStream();
                so.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + hb.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                so.write(hb); so.flush(); socket.close();

                if (!state.equals(q.get("state"))) throw new Exception("O retorno do login não pôde ser validado.");
                if (q.containsKey("error")) throw new Exception("Autorização cancelada: " + q.get("error"));
                String code = q.get("code");
                if (code == null || code.isEmpty()) throw new Exception("O login não retornou um código de autorização.");
                String issued = q.get("client_id");
                if ("dynamic_agent_client".equals(clientId)) {
                    if (issued == null || !issued.startsWith("oaiapp_")) throw new Exception("O ChatGPT não retornou o client_id da instalação.");
                    clientId = issued;
                } else if (issued != null && !issued.equals(clientId)) throw new Exception("O client_id retornado não corresponde à conta salva.");

                JSONObject tokens = tokenExchange("authorization_code", clientId, code, verifier, redirect, null);
                JSONObject identity = verifyIdToken(tokens.getString("id_token"), clientId, nonce);
                String scope = tokens.optString("scope", q.get("scope") == null ? "" : q.get("scope"));
                if (!scope.contains("chatgpt.tokens.use.direct")) throw new Exception("A conta foi conectada, mas o uso do plano ChatGPT não foi autorizado.");

                JSONObject p = new JSONObject();
                p.put("client_id", clientId); p.put("host_id", hostId);
                p.put("subject", identity.getString("sub")); p.put("email", identity.optString("email", ""));
                p.put("id_token", tokens.getString("id_token")); p.put("access_token", tokens.getString("access_token"));
                p.put("refresh_token", tokens.optString("refresh_token", "")); p.put("scope", scope);
                p.put("expires_at", System.currentTimeMillis() + tokens.optLong("expires_in", 3600) * 1000L);
                saveProfile(p);
                callback.onSuccess("ChatGPT conectado" + (p.optString("email").isEmpty() ? "." : ": " + p.optString("email")));
            } catch (Throwable t) {
                callback.onError("Não consegui conectar ao ChatGPT: " + safe(t));
            } finally { if (server != null) try { server.close(); } catch (Exception ignored) {} }
        }, "ei-chat-auth").start();
    }

    public synchronized String accessToken() throws Exception {
        JSONObject p = profile();
        if (p == null) throw new Exception("Conecte sua conta ChatGPT primeiro.");
        if (System.currentTimeMillis() < p.optLong("expires_at", 0) - 120000) return p.getString("access_token");
        String refresh = p.optString("refresh_token", "");
        if (refresh.isEmpty()) throw new Exception("Sua sessão expirou. Conecte o ChatGPT novamente.");
        JSONObject t = tokenExchange("refresh_token", p.getString("client_id"), null, null, null, refresh);
        p.put("access_token", t.getString("access_token"));
        if (!t.optString("refresh_token", "").isEmpty()) p.put("refresh_token", t.getString("refresh_token"));
        if (!t.optString("id_token", "").isEmpty()) p.put("id_token", t.getString("id_token"));
        if (!t.optString("scope", "").isEmpty()) p.put("scope", t.getString("scope"));
        p.put("expires_at", System.currentTimeMillis() + t.optLong("expires_in", 3600) * 1000L);
        saveProfile(p); return p.getString("access_token");
    }

    public void signOut() { secure.clear(); }

    private JSONObject tokenExchange(String grant, String client, String code, String verifier, String redirect, String refresh) throws Exception {
        StringBuilder form = new StringBuilder();
        add(form, "grant_type", grant); add(form, "client_id", client); add(form, "resource", RESOURCE);
        if (code != null) add(form, "code", code);
        if (verifier != null) add(form, "code_verifier", verifier);
        if (redirect != null) add(form, "redirect_uri", redirect);
        if (refresh != null) add(form, "refresh_token", refresh);
        HttpURLConnection c = (HttpURLConnection) new URL(TOKEN).openConnection();
        c.setRequestMethod("POST"); c.setDoOutput(true); c.setConnectTimeout(15000); c.setReadTimeout(30000);
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded"); c.setRequestProperty("Accept", "application/json");
        c.getOutputStream().write(form.toString().getBytes(StandardCharsets.UTF_8));
        int status = c.getResponseCode(); String body = read(c, status >= 400);
        if (status < 200 || status >= 300) throw new Exception("Falha OAuth (" + status + ").");
        return new JSONObject(body);
    }

    private JSONObject verifyIdToken(String jwt, String clientId, String nonce) throws Exception {
        String[] parts = jwt.split("\\."); if (parts.length != 3) throw new Exception("ID token inválido.");
        JSONObject header = new JSONObject(new String(Base64.decode(parts[0], Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING), StandardCharsets.UTF_8));
        JSONObject payload = new JSONObject(new String(Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING), StandardCharsets.UTF_8));
        if (!"RS256".equals(header.optString("alg"))) throw new Exception("Algoritmo de assinatura inesperado.");
        if (!"https://auth.openai.com".equals(payload.optString("iss"))) throw new Exception("Emissor do login inválido.");
        Object aud = payload.opt("aud"); boolean audOk = clientId.equals(aud);
        if (aud instanceof JSONArray) { audOk = false; JSONArray a=(JSONArray)aud; for(int i=0;i<a.length();i++) if(clientId.equals(a.optString(i))) audOk=true; }
        if (!audOk || !nonce.equals(payload.optString("nonce"))) throw new Exception("Identidade do login não confere.");
        long now = System.currentTimeMillis()/1000L; if (payload.optLong("exp",0) < now-5 || payload.optString("sub","").isEmpty()) throw new Exception("ID token expirado ou incompleto.");

        HttpURLConnection c=(HttpURLConnection)new URL(JWKS).openConnection(); c.setConnectTimeout(10000); c.setReadTimeout(15000);
        JSONObject jwks=new JSONObject(read(c,false)); JSONArray keys=jwks.getJSONArray("keys"); JSONObject key=null;
        String kid=header.optString("kid"); for(int i=0;i<keys.length();i++) if(kid.equals(keys.getJSONObject(i).optString("kid"))) key=keys.getJSONObject(i);
        if(key==null) throw new Exception("Chave de assinatura não encontrada.");
        BigInteger n=new BigInteger(1, Base64.decode(key.getString("n"), Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING));
        BigInteger e=new BigInteger(1, Base64.decode(key.getString("e"), Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING));
        PublicKey pk=KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n,e));
        Signature sig=Signature.getInstance("SHA256withRSA"); sig.initVerify(pk); sig.update((parts[0]+"."+parts[1]).getBytes(StandardCharsets.US_ASCII));
        if(!sig.verify(Base64.decode(parts[2], Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING))) throw new Exception("Assinatura do login inválida.");
        return payload;
    }

    private String hostId() { android.content.SharedPreferences p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE); String h=p.getString(HOST,null); if(h==null){h="urn:uuid:"+UUID.randomUUID();p.edit().putString(HOST,h).apply();} return h; }
    private JSONObject profile(){ try{String s=secure.load();return s==null?null:new JSONObject(s);}catch(Exception e){return null;} }
    private void saveProfile(JSONObject p)throws Exception{secure.save(p.toString());}
    private static String random(int bytes){byte[] b=new byte[bytes];new SecureRandom().nextBytes(b);return b64url(b);}
    private static String b64url(byte[] b){return Base64.encodeToString(b,Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);}
    private static void add(StringBuilder b,String k,String v)throws Exception{if(b.length()>0)b.append('&');b.append(URLEncoder.encode(k,"UTF-8")).append('=').append(URLEncoder.encode(v,"UTF-8"));}
    private static String read(HttpURLConnection c,boolean error)throws Exception{BufferedReader r=new BufferedReader(new InputStreamReader(error?c.getErrorStream():c.getInputStream(),StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();String s;while((s=r.readLine())!=null)b.append(s);r.close();return b.toString();}
    private static Map<String,String> parseRequest(String line)throws Exception{Map<String,String> m=new HashMap<>();if(line==null)return m;String[] p=line.split(" ");if(p.length<2)return m;int q=p[1].indexOf('?');if(q<0)return m;for(String pair:p[1].substring(q+1).split("&")){String[] kv=pair.split("=",2);m.put(URLDecoder.decode(kv[0],"UTF-8"),URLDecoder.decode(kv.length>1?kv[1]:"","UTF-8"));}return m;}
    private static String safe(Throwable t){String s=t.getMessage();return s==null?t.getClass().getSimpleName():s;}
}
