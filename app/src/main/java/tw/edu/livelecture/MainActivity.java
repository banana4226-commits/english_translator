package tw.edu.livelecture;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.icu.text.Transliterator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements RecognitionListener {
    private static final int REQUEST_MIC=123, REQUEST_SAVE=456;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final List<String> transcript=new ArrayList<>();
    private TextView status,english,chinese,history;
    private Button start;
    private Translator translator;
    private Transliterator trad;
    private SpeechRecognizer speech;
    private boolean ready=false,listening=false,recognizing=false,queued=false;
    private String partial="",lastPreview="";
    private int serial=0,errors=0;
    private final Runnable tick=new Runnable(){
        @Override public void run(){
            if(!listening)return;
            if(ready && partial.length()>7 && !partial.equals(lastPreview)){
                String input=partial; int id=serial;
                lastPreview=input;
                translator.translate(input).addOnSuccessListener(s->{
                    if(listening && id==serial) chinese.setText(toTraditional(s));
                });
            }
            handler.postDelayed(this,1600);
        }
    };
    private String toTraditional(String s){
        try{return trad.transliterate(s).replace("視頻","影片").replace("軟件","軟體").replace("信息","資訊").replace("網絡","網路");}
        catch(Exception e){return s;}
    }
    private TextView label(String text,int size){
        TextView v=new TextView(this);v.setText(text);v.setTextColor(Color.rgb(30,40,55));v.setTextSize(size);
        v.setPadding(12,15,12,15);return v;
    }
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20,20,20,15);root.setBackgroundColor(Color.rgb(249,250,252));
        TextView title=label("課堂即時英語翻譯",24);root.addView(title);
        status=label("首次開啟：正在下載翻譯模型…",14);root.addView(status);
        LinearLayout actions=new LinearLayout(this);
        start=new Button(this);start.setText("開始收音");actions.addView(start,new LinearLayout.LayoutParams(0,-2,1));
        Button export=new Button(this);export.setText("匯出 TXT");actions.addView(export,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(actions);
        root.addView(label("English / 老師原文",15));
        english=label("等待收音…",19);root.addView(english);
        root.addView(label("繁體中文",15));
        chinese=label("等待翻譯…",24);root.addView(chinese);
        root.addView(label("課堂紀錄",15));
        ScrollView scroll=new ScrollView(this);
        history=label("尚無紀錄",15);scroll.addView(history);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
        start.setOnClickListener(v->{if(listening)stop();else begin();});
        export.setOnClickListener(v->exportText());
        trad=Transliterator.getInstance("Hans-Hant");
        translator=Translation.getClient(new TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.CHINESE).build());
        translator.downloadModelIfNeeded().addOnSuccessListener(x->{ready=true;status.setText("模型已就緒，請按開始收音");})
                .addOnFailureListener(e->status.setText("模型下載失敗：請確認網路並重新開啟"));
    }
    private void begin(){
        if(!ready){Toast.makeText(this,"請先等翻譯模型下載",Toast.LENGTH_SHORT).show();return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQUEST_MIC);return;
        }
        if(!SpeechRecognizer.isRecognitionAvailable(this)){status.setText("找不到語音辨識服務，請啟用 Google 語音服務");return;}
        listening=true;errors=0;start.setText("停止收音");getWindow().getDecorView().setKeepScreenOn(true);
        handler.post(tick);restart(0);
    }
    @Override public void onRequestPermissionsResult(int req,String[] perms,int[] result){
        super.onRequestPermissionsResult(req,perms,result);
        if(req==REQUEST_MIC && result.length>0 && result[0]==PackageManager.PERMISSION_GRANTED)begin();
    }
    private void restart(long delay){
        if(!listening||queued)return;
        queued=true;
        handler.postDelayed(()->{
            queued=false;
            if(!listening||recognizing)return;
            try{
                if(speech==null){speech=SpeechRecognizer.createSpeechRecognizer(this);speech.setRecognitionListener(this);}
                Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"en-GB");
                i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);
                i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);
                recognizing=true;speech.startListening(i);status.setText("正在收音（英式英文）");
            }catch(Exception e){recognizing=false;status.setText("重新啟動收音中");restart(1500);}
        },delay);
    }
    private void stop(){
        listening=false;recognizing=false;queued=false;serial++;partial="";
        handler.removeCallbacksAndMessages(null);
        if(speech!=null){speech.cancel();speech.destroy();speech=null;}
        start.setText("開始收音");status.setText("已停止，可匯出紀錄");
        getWindow().getDecorView().setKeepScreenOn(false);
    }
    private String text(Bundle b){
        ArrayList<String> r=b==null?null:b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return r==null||r.isEmpty()?"":r.get(0).trim();
    }
    @Override public void onPartialResults(Bundle b){
        if(!listening)return;String value=text(b);
        if(!value.isEmpty()&&!value.equals(partial)){
            if(partial.isEmpty()){serial++;lastPreview="";chinese.setText("即時翻譯中…");}
            partial=value;english.setText(value);
        }
    }
    @Override public void onResults(Bundle b){
        recognizing=false;if(!listening)return;
        String value=text(b);partial="";lastPreview="";serial++;errors=0;
        if(!value.isEmpty()){
            int id=serial;english.setText(value);chinese.setText("翻譯中…");
            String stamp=new SimpleDateFormat("HH:mm:ss",Locale.TAIWAN).format(new Date());
            int index=transcript.size();transcript.add("["+stamp+"] EN: "+value+"\nZH-TW: （翻譯中）");
            render();
            translator.translate(value).addOnSuccessListener(s->{
                String zh=toTraditional(s);
                transcript.set(index,"["+stamp+"] EN: "+value+"\nZH-TW: "+zh);render();
                if(id==serial && partial.isEmpty())chinese.setText(zh);
            }).addOnFailureListener(e->{
                transcript.set(index,"["+stamp+"] EN: "+value+"\nZH-TW: （翻譯失敗）");render();
            });
        }
        restart(500);
    }
    private void render(){
        int from=Math.max(0,transcript.size()-30);StringBuilder sb=new StringBuilder();
        if(from>0)sb.append("僅顯示最近30段；匯出包含全部內容\n\n");
        for(int i=from;i<transcript.size();i++)sb.append(transcript.get(i)).append("\n\n");
        history.setText(sb.toString());
    }
    @Override public void onError(int error){
        recognizing=false;if(!listening)return;partial="";serial++;
        if(error==SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS){stop();status.setText("請允許麥克風權限");return;}
        errors++;
        if(error==SpeechRecognizer.ERROR_CLIENT||error==SpeechRecognizer.ERROR_RECOGNIZER_BUSY){
            if(speech!=null){speech.destroy();speech=null;}
        }
        status.setText("語音辨識暫停，自動重試："+error);
        restart(error==SpeechRecognizer.ERROR_NO_MATCH||error==SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                ?650:Math.min(16000,1000L*(1L<<Math.min(errors,4))));
    }
    @Override public void onReadyForSpeech(Bundle b){}
    @Override public void onBeginningOfSpeech(){}
    @Override public void onRmsChanged(float f){}
    @Override public void onBufferReceived(byte[] b){}
    @Override public void onEndOfSpeech(){}
    @Override public void onEvent(int type,Bundle b){}
    private void exportText(){
        if(transcript.isEmpty()){Toast.makeText(this,"沒有可匯出的紀錄",Toast.LENGTH_SHORT).show();return;}
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TITLE,"課堂翻譯_"+new SimpleDateFormat("yyyyMMdd_HHmm",Locale.TAIWAN).format(new Date())+".txt");
        startActivityForResult(i,REQUEST_SAVE);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=REQUEST_SAVE||result!=RESULT_OK||data==null||data.getData()==null)return;
        try(OutputStream stream=getContentResolver().openOutputStream(data.getData())){
            if(stream==null)throw new Exception("無法寫入");
            stream.write(String.join("\n\n",transcript).getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this,"已匯出課堂紀錄",Toast.LENGTH_SHORT).show();
        }catch(Exception e){Toast.makeText(this,"儲存失敗："+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    @Override protected void onDestroy(){
        stop();if(translator!=null)translator.close();super.onDestroy();
    }
}
