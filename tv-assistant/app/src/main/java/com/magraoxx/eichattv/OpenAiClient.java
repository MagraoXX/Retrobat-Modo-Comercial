package com.magraoxx.eichattv;

import android.content.Context;
import org.json.*;
import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;

public final class OpenAiClient {
 public interface Callback{void onSuccess(String text);void onError(String message);} private final ChatGptAuth auth;
 public OpenAiClient(Context c){auth=new ChatGptAuth(c);}

 // Sign in with ChatGPT currently supports text/image/file Responses input, not /audio/transcriptions.
 public void transcribe(File wav,Callback cb){cb.onError("O login ChatGPT não autoriza transcrição de áudio. Use o reconhecimento de voz do aparelho para converter a fala em texto.");}

 public void ask(String prompt,Callback cb){new Thread(()->{try{
   String token=auth.accessToken();String model=pickModel(token);
   JSONObject req=new JSONObject();req.put("model",model);req.put("store",false);req.put("stream",true);
   req.put("instructions","Você é Ei Chat, assistente de uma Android/Google TV. Responda em português do Brasil, de forma curta, natural e útil para ser falada em uma televisão.");
   JSONArray input=new JSONArray();JSONObject msg=new JSONObject();msg.put("role","user");msg.put("content",prompt);input.put(msg);req.put("input",input);
   HttpURLConnection c=(HttpURLConnection)new URL("https://api.openai.com/v1/responses").openConnection();c.setRequestMethod("POST");c.setDoOutput(true);c.setConnectTimeout(15000);c.setReadTimeout(90000);c.setRequestProperty("Authorization","Bearer "+token);c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Accept","text/event-stream");
   try(OutputStream o=c.getOutputStream()){o.write(req.toString().getBytes(StandardCharsets.UTF_8));}
   int s=c.getResponseCode();if(s<200||s>=300){String body=readAll(c.getErrorStream());throw new Exception(apiError(s,body));}
   StringBuilder out=new StringBuilder();boolean completed=false;BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8));String line;
   while((line=r.readLine())!=null){if(!line.startsWith("data:"))continue;String data=line.substring(5).trim();if(data.isEmpty()||"[DONE]".equals(data))continue;JSONObject ev=new JSONObject(data);String type=ev.optString("type","");if("response.output_text.delta".equals(type))out.append(ev.optString("delta",""));else if("response.completed".equals(type))completed=true;else if("response.failed".equals(type)){JSONObject response=ev.optJSONObject("response");JSONObject error=response==null?null:response.optJSONObject("error");throw new Exception(error==null?"A IA não concluiu a resposta.":error.optString("message",error.optString("code","Falha na IA")));}else if("response.incomplete".equals(type))throw new Exception("A resposta da IA ficou incompleta.");}
   r.close();if(!completed)throw new Exception("A conexão com a IA terminou antes de concluir a resposta.");String text=out.toString().trim();if(text.isEmpty())throw new Exception("A IA respondeu sem texto.");cb.onSuccess(text);
 }catch(Throwable e){cb.onError("IA: "+safe(e));}},"ei-chat-ai").start();}

 private String pickModel(String token)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL("https://api.openai.com/v1/models").openConnection();c.setRequestMethod("GET");c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setRequestProperty("Authorization","Bearer "+token);c.setRequestProperty("Accept","application/json");int s=c.getResponseCode();String body=readAll(s>=400?c.getErrorStream():c.getInputStream());if(s<200||s>=300)throw new Exception(apiError(s,body));JSONObject j=new JSONObject(body);JSONArray a=j.optJSONArray("models");if(a==null)throw new Exception("Sua conta não retornou modelos disponíveis.");for(int i=0;i<a.length();i++){JSONObject m=a.optJSONObject(i);if(m==null)continue;String slug=m.optString("slug",m.optString("id",""));if(!slug.isEmpty()&&"list".equals(m.optString("visibility","list")))return slug;}throw new Exception("Nenhum modelo do ChatGPT está disponível para esta conta.");}
 private static String readAll(InputStream in)throws Exception{if(in==null)return "";BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();String x;while((x=r.readLine())!=null)b.append(x);r.close();return b.toString();}
 private static String apiError(int s,String body){try{JSONObject j=new JSONObject(body);JSONObject e=j.optJSONObject("error");if(e!=null)return "OpenAI "+s+": "+e.optString("message",e.optString("code",body));String d=j.optString("detail","");return "OpenAI "+s+(d.isEmpty()?"":": "+d);}catch(Exception e){return "OpenAI "+s+(body==null||body.isEmpty()?"":": "+body);}}
 private static String safe(Throwable t){return t.getMessage()==null?t.getClass().getSimpleName():t.getMessage();}
}
