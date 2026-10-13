package com.magraoxx.eichattv;

import android.content.Context;
import android.media.*;
import java.io.*;

public final class AiVoiceRecorder {
 public interface Callback { void onRecorded(File wav, String route); void onError(String error); }
 public interface ProbeCallback { void onResult(boolean ok, String report); }
 private final Context context; private volatile boolean stop;
 public AiVoiceRecorder(Context c){context=c.getApplicationContext();}

 public void probeBuiltIn(ProbeCallback cb){new Thread(()->{AudioRecord r=null;try{
  int rate=16000,channel=AudioFormat.CHANNEL_IN_MONO,fmt=AudioFormat.ENCODING_PCM_16BIT;
  int min=Math.max(AudioRecord.getMinBufferSize(rate,channel,fmt),4096);
  AudioDeviceInfo tv=TvMicrophone.findBuiltIn(context);
  if(tv==null){cb.onResult(false,"MIC interno não foi exposto pela TCL.");return;}
  r=new AudioRecord(MediaRecorder.AudioSource.MIC,rate,channel,fmt,min*2);
  boolean preferred=r.setPreferredDevice(tv);
  if(r.getState()!=AudioRecord.STATE_INITIALIZED){cb.onResult(false,"AudioRecord não inicializou. MIC interno id "+tv.getId());return;}
  r.startRecording();
  if(r.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING){cb.onResult(false,"startRecording falhou. MIC interno id "+tv.getId()+", preferido="+preferred);return;}
  byte[] buf=new byte[min];long end=System.currentTimeMillis()+2500;long samples=0,sum=0;int peak=0,reads=0;
  while(System.currentTimeMillis()<end){int n=r.read(buf,0,buf.length);if(n<0)throw new IOException("AudioRecord.read="+n);if(n==0)continue;reads++;for(int i=0;i+1<n;i+=2){short s=(short)((buf[i]&255)|(buf[i+1]<<8));int a=Math.abs((int)s);if(a>peak)peak=a;sum+=(long)s*s;samples++;}}
  AudioDeviceInfo routed=r.getRoutedDevice();double rms=samples==0?0:Math.sqrt((double)sum/samples);
  String route=routed==null?"não informado":routed.getProductName()+" [id "+routed.getId()+"]";
  boolean signal=samples>0&&peak>0;
  cb.onResult(signal,"CAPTURA DIRETA: "+(signal?"OK":"SEM SINAL")+"\nSolicitado: "+tv.getProductName()+" [id "+tv.getId()+"]\nsetPreferredDevice: "+preferred+"\nRota real: "+route+"\nLeituras: "+reads+" • amostras: "+samples+"\nRMS: "+Math.round(rms)+" • pico: "+peak);
 }catch(Throwable e){cb.onResult(false,"CAPTURA DIRETA FALHOU: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()));}finally{if(r!=null){try{if(r.getRecordingState()==AudioRecord.RECORDSTATE_RECORDING)r.stop();}catch(Throwable ignored){}try{r.release();}catch(Throwable ignored){}}}},"ei-chat-mic-probe").start();}

 public void record(Callback cb){stop=false;new Thread(()->{AudioRecord r=null;try{
  int rate=16000, channel=AudioFormat.CHANNEL_IN_MONO, fmt=AudioFormat.ENCODING_PCM_16BIT;
  int min=Math.max(AudioRecord.getMinBufferSize(rate,channel,fmt),4096);
  r=new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,rate,channel,fmt,min*2);
  AudioDeviceInfo tv=TvMicrophone.findBuiltIn(context); if(tv!=null) r.setPreferredDevice(tv);
  if(r.getState()!=AudioRecord.STATE_INITIALIZED)throw new Exception("AudioRecord não inicializou");
  File pcm=new File(context.getCacheDir(),"ei_chat_voice.pcm"); FileOutputStream out=new FileOutputStream(pcm);
  byte[] buf=new byte[min];r.startRecording();long start=System.currentTimeMillis(), lastVoice=start;
  while(!stop&&System.currentTimeMillis()-start<12000){int n=r.read(buf,0,buf.length);if(n>0){out.write(buf,0,n);long sum=0;for(int i=0;i+1<n;i+=2){short s=(short)((buf[i]&255)|(buf[i+1]<<8));sum+=(long)s*s;}double rms=Math.sqrt(sum/Math.max(1,n/2));if(rms>650)lastVoice=System.currentTimeMillis();if(System.currentTimeMillis()-start>1800&&System.currentTimeMillis()-lastVoice>1100)break;}}
  r.stop();out.close();File wav=new File(context.getCacheDir(),"ei_chat_voice.wav");writeWav(pcm,wav,rate);pcm.delete();AudioDeviceInfo routed=r.getRoutedDevice();String route=routed==null?(tv==null?"entrada padrão da TV":"microfone interno solicitado"):routed.getProductName().toString();cb.onRecorded(wav,route);
 }catch(Throwable e){cb.onError(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());}finally{if(r!=null)try{r.release();}catch(Exception ignored){}}},"ei-chat-record").start();}

 public void recordMusic(Callback cb){stop=false;new Thread(()->{AudioRecord r=null;FileOutputStream out=null;try{
  int rate=48000, channel=AudioFormat.CHANNEL_IN_MONO, fmt=AudioFormat.ENCODING_PCM_16BIT;
  int min=Math.max(AudioRecord.getMinBufferSize(rate,channel,fmt),8192);
  r=new AudioRecord(MediaRecorder.AudioSource.MIC,rate,channel,fmt,min*2);
  AudioDeviceInfo tv=TvMicrophone.findBuiltIn(context); if(tv!=null) r.setPreferredDevice(tv);
  if(r.getState()!=AudioRecord.STATE_INITIALIZED)throw new Exception("Não consegui abrir o microfone para reconhecer a música.");
  File pcm=new File(context.getCacheDir(),"ei_chat_music.pcm"); out=new FileOutputStream(pcm);
  byte[] buf=new byte[min];long start=System.currentTimeMillis();long samples=0,sum=0;int peak=0;
  r.startRecording();
  if(r.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)throw new Exception("O microfone não iniciou a captura da música.");
  while(!stop&&System.currentTimeMillis()-start<12000){int n=r.read(buf,0,buf.length);if(n<0)throw new IOException("AudioRecord.read="+n);if(n==0)continue;out.write(buf,0,n);for(int i=0;i+1<n;i+=2){short s=(short)((buf[i]&255)|(buf[i+1]<<8));int a=Math.abs((int)s);if(a>peak)peak=a;sum+=(long)s*s;samples++;}}
  r.stop();out.close();out=null;
  if(samples<rate*2L||peak<80)throw new Exception("Não ouvi áudio suficiente para identificar a música.");
  File wav=new File(context.getCacheDir(),"ei_chat_music.wav");writeWav(pcm,wav,rate);pcm.delete();
  AudioDeviceInfo routed=r.getRoutedDevice();String route=routed==null?(tv==null?"entrada padrão da TV":"microfone interno solicitado"):routed.getProductName().toString();
  cb.onRecorded(wav,route+" • 12 s");
 }catch(Throwable e){cb.onError(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());}finally{if(out!=null)try{out.close();}catch(Exception ignored){}if(r!=null){try{if(r.getRecordingState()==AudioRecord.RECORDSTATE_RECORDING)r.stop();}catch(Exception ignored){}try{r.release();}catch(Exception ignored){}}}},"ei-chat-music-record").start();}

 public void stop(){stop=true;}
 private static void writeWav(File pcm,File wav,int rate)throws Exception{byte[] data=java.nio.file.Files.readAllBytes(pcm.toPath());try(FileOutputStream o=new FileOutputStream(wav)){int size=data.length;o.write(new byte[]{'R','I','F','F'});le(o,size+36);o.write(new byte[]{'W','A','V','E','f','m','t',' '});le(o,16);les(o,1);les(o,1);le(o,rate);le(o,rate*2);les(o,2);les(o,16);o.write(new byte[]{'d','a','t','a'});le(o,size);o.write(data);}}
 private static void le(OutputStream o,int v)throws Exception{o.write(v&255);o.write(v>>8&255);o.write(v>>16&255);o.write(v>>24&255);}private static void les(OutputStream o,int v)throws Exception{o.write(v&255);o.write(v>>8&255);}
}
