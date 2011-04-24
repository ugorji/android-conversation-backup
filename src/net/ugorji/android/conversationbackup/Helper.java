package net.ugorji.android.conversationbackup;

import java.io.Closeable;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.CallLog;
import android.text.TextUtils;
import android.util.Log;

public class Helper { 
  private static boolean INITED = false;
  static final String VERSION = "1.0.2";
  
  //TBD fix safety checks
  static final boolean 
    SAFETY_ALLOW_DELETE_AFTER_BACKUP = true, //change to true before shipping
    SAFETY_ALLOW_DELETE_TMP_DIR = true, //change to true before shipping
    SAFETY_DEV_MODE = false, //change to false before shipping
    SAFETY_XYZ = false; //random unused one

  static final String
    SAFETY_DEV_EMAIL_ADDRESS = null; //"ugorji@gmail.com"; //change to null before shipping
  
  static final Pattern
    SMIL_PATTERN = Pattern.compile(".*<smil>(.*)</smil>.*", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

  static final SimpleDateFormat 
    SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss zz");

  static final String 
    TAG = Helper.class.getSimpleName(),
    EXIT_ACTION = "net.ugorji.android.conversationbackup.EXIT_ACTION",
    UPDATE_PROGRESS_ACTION = "net.ugorji.android.conversationbackup.UPDATE_PROGRESS",
    SHOW_RESULT_ACTION = "net.ugorji.android.conversationbackup.SHOW_RESULT",
    SHARED_PREFERENCES_KEY = "shared_preferences",
    ASSET_EULA = "EULA",
    ASSET_HTML_VIEWER = "index.html",
    PREFERENCE_EULA_ACCEPTED = "eula.accepted";

  static final int 
    SEND_ARCHIVE_REQUEST = 2;

  static final IntentFilter 
    PROGRESS_INTENT_FILTER = new IntentFilter();

  static final File
    APP_DIR,
    RESULT_LOG_FILE;

  static {
    PROGRESS_INTENT_FILTER.addAction(Helper.UPDATE_PROGRESS_ACTION);
    APP_DIR = new File(Environment.getExternalStorageDirectory(), "Android/data/" + Helper.class.getPackage().getName() + "/files");
    RESULT_LOG_FILE  = new File(APP_DIR, "result-log.txt");
  }  

  public static class Cols {
    public static String 
      ID = "_id", // Telephony.Mms._ID
      THREAD_ID = "thread_id", // Telephony.Mms.THREAD_ID
      ADDRESS = "address", // Telephony.Sms.ADDRESS
      DATE = "date", // Telephony.Mms.DATE
      SUBJECT = "subject", // Telephony.Mms.SUBJECT
      BODY = "body", // Telephony.Sms.BODY
      FROM = "from", // Telephony.Mms.FROM
      TO = "to", // Telephony.Mms.TO
      ADDR_MSG_ID = "msg_id", // Telephony.Mms.Addr.MSG_ID
      ADDR_TYPE = "type", // Telephony.Mms.Addr.MSG_ID
      PART_MSG_ID = "mid", // Telephony.Mms.Part.MSG_ID
      PART_CONTENT_LOCATION = "cl", // Telephony.Mms.Part.CONTENT_LOCATION
      PART_NAME = "name", // Telephony.Mms.Part.NAME
      PART_TEXT = "text", // Telephony.Mms.Part.TEXT
      PART_FILENAME = "fn", // Telephony.Mms.Part.FILENAME
      PART_SEQ = "seq", // Telephony.Mms.Part.SEQ
      PART_CONTENT_TYPE = "ct", // Telephony.Mms.Part.CONTENT_TYPE
      PART_DATA = "_data", // Telephony.Mms.Part._DATA
      TYPE_DISCRIMINATOR_COLUMN = "transport_type", // Telephony.MmsSms.TYPE_DISCRIMINATOR_COLUMN
      MMS_MESSAGE_BOX = "msg_box", // Telephony.Mms.MESSAGE_BOX
      SMS_MESSAGE_BOX = "type", // Telephony.Sms.TYPE
      MMS_TRANSACTION_ID = "tr_id", // Telephony.Mms.TRANSACTION_ID
      // = "",
      XYZ = "";
    public static Uri 
      MMS_CONTENT_URI = Uri.parse("content://mms"), // Telephony.Mms.CONTENT_URI
      MMS_PART_CONTENT_URI = Uri.parse("content://mms/part"), // Telephony.Mms.CONTENT_URI + "part"
      MMS_ADDR_CONTENT_URI = Uri.parse("content://mms/addr"), // Telephony.Mms.CONTENT_URI + "part"
      SMS_CONTENT_URI = Uri.parse("content://sms"), // Telephony.Sms.CONTENT_URI
      CONVERSATIONS_CONTENT_URI = Uri.parse("content://mms-sms/conversations"), // Telephony.Sms...
      COMPLETE_CONVERSATIONS_CONTENT_URI = Uri.parse("content://mms-sms/complete-conversations"), // Telephony.Sms...
      XYZ2 = null;
    public static int 
      MESSAGE_BOX_INBOX  = 1, //if not in inbox, then the message was not sent by us
      PDU_HEADER_FROM = 0x89,
      XYZ3 = 0; 
  }
  
  public static class Summary {
    public long timestamp;
    public boolean backup_all_numbers;
    public boolean backup_messages;
    public boolean backup_mms_attachments;
    public boolean backup_call_records;
    public boolean delete_after_backup;
    public boolean random_question;
    public boolean email_backup;
    public String specific_numbers_to_backup_edit;
    public JSONObject toJSON() throws Exception {
      JSONObject jobj = new JSONObject();
      jobj.put("timestamp", timestamp);
      jobj.put("datetime", SDF.format(new Date(timestamp)));
      jobj.put("backup_all_numbers", backup_all_numbers);
      jobj.put("backup_messages", backup_messages);
      jobj.put("backup_mms_attachments", backup_mms_attachments);
      jobj.put("backup_call_records", backup_call_records);
      jobj.put("delete_after_backup", delete_after_backup);
      //jobj.put("random_question", random_question);
      jobj.put("email_backup", email_backup);
      jobj.put("specific_numbers_to_backup_edit", specific_numbers_to_backup_edit);
      return jobj;
    }
  }
  
  //Helper Object (Ho)
  public static class Ho implements Comparable<Ho>{
    public long timestamp;
    public long id;
    public String number;
    public int compareTo(Ho s2) {
      if(timestamp < s2.timestamp) return -1;
      else if(timestamp > s2.timestamp) return 1;
      else return 0;
      //return (int)(timestamp - s2.timestamp); //problem occurring due to truncation???
    }
    public JSONObject toJSON() throws Exception {
      //new JSONObject(this, FIELDS)
      JSONObject jobj = new JSONObject();
      jobj.put("timestamp", timestamp);
      jobj.put("datetime", SDF.format(new Date(timestamp)));
      jobj.put("number", number);
      jobj.put("id", id);
      return jobj;
    }
  }
  
  //Call Log (Cl)
  public static class Cl extends Ho {
    public int duration;
    public String type;
    public String name;
    public JSONObject toJSON() throws Exception {
      //new JSONObject(this, FIELDS)
      JSONObject jobj = super.toJSON();
      jobj.put("duration", duration);
      jobj.put("type", type);
      jobj.put("name", name);
      return jobj;
    }
  }
  
  //Message (Ms)
  public static class Ms extends Ho {
    //public static final String[] FIELDS = new String[] {"timestamp", "number", "id", "text", "subject", "mms"};
    public String text;
    public String subject;
    public boolean mms;
    public boolean sender;
    public List<MmsEntry> entries = new ArrayList<MmsEntry>();
    
    public JSONObject toJSON() throws Exception {
      //new JSONObject(this, FIELDS)
      JSONObject jobj = super.toJSON();
      jobj.put("text", text);
      jobj.put("subject", subject);
      jobj.put("mms", mms);
      jobj.put("sender", sender);
      if(mms && entries.size() > 0) {
        JSONArray jarr1 = new JSONArray();
        for(MmsEntry mse: entries) jarr1.put(mse.toJSON());
        jobj.put("entries", jarr1);
      }
      return jobj;
    }
  }
  
  public static class MmsEntry {
    //public static final String[] FIELDS = new String[] {"text", "filename"};
    public String text;
    public byte[] content;
    public String filename;
    public JSONObject toJSON() throws Exception {
      //new JSONObject(this, FIELDS)
      JSONObject jobj = new JSONObject();
      jobj.put("text", text);
      jobj.put("filename", filename);
      return jobj;
    }
  }
  
  public static class MsComparator implements Comparator<Ms> {
    public int compare(Ms o1, Ms o2) {
      return (int)(o1.timestamp - o2.timestamp);
    }
  }

  public static void init() {
    if(INITED) return;
    if(!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
      throw new RuntimeException("External storage e.g. SD card is not available");
    }
    if(APP_DIR.exists()) {
      if(!APP_DIR.isDirectory()) {
        throw new RuntimeException("A file exists where directory expected: " + APP_DIR);
      }
    } else {
      if(!APP_DIR.mkdirs()) {
        throw new RuntimeException("Unable to create directory: " + APP_DIR);
      }
    }
    INITED = true;
  }
  
  public static void deleteFile(File f) {
    if(f.isDirectory()) {
      for(File f2: f.listFiles()) deleteFile(f2);
    }
    if(!f.delete()) throw new RuntimeException("Unable to delete file: " + f);
  }

  public static void doSleep(long ms) {
    try { Thread.sleep(ms); }
    catch(InterruptedException ie) { }
  }

  public static void writeJSON(String tag, JSONArray jsonarr, File outfile) throws Exception {
    JSONObject jobj = new JSONObject();
    jobj.put(tag, jsonarr);
    writeJSON(jobj, outfile);
  }

  public static void writeJSON(JSONObject jobj, File outfile) throws Exception {
    PrintWriter pw = new PrintWriter(new FileWriter(outfile));
    pw.println(jobj.toString(2));   
    pw.flush();
    close(pw);
  }
  
  
  public static String write(Collection c, Object separator, Object prefix, Object postfix) {
    StringBuilder sb = new StringBuilder();
    boolean first = true;
    for(Object o: c) {
      if(!first) sb.append(separator);
      sb.append(prefix).append(o).append(postfix);
      first = false;
    }
    return sb.toString();
  }

  public static void writeToFile(File f, boolean append, String s, String post) {
    try {
      //FileWriter fw = new FileWriter(f, append); //was giving some folks FileNotFoundException
      FileWriter fw = ((f.exists() && append) ? new FileWriter(f, append) : new FileWriter(f));
      fw.write(s);
      if(post != null) fw.write(post);
      fw.flush();
      close(fw);
    } catch(Exception e) {
      throw new RuntimeException(e);
    }
  }
  
  public static String getFileContents(File f) {
    try {
      if(!f.exists()) return null;
      return read(new FileReader(f), true);
    } catch(Exception e) {
      Log.e(TAG, "Error getting file contents: " + f, e);
      throw new RuntimeException(e);
    }
  }
  
  public static String read(Reader fr, boolean closeAfter) {
    try {
      StringBuilder sb = new StringBuilder();
      char[] cbuf = new char[512];
      int clen = -1;
      while((clen = fr.read(cbuf, 0, cbuf.length)) != -1) {
        sb.append(cbuf, 0, clen);
      }
      return sb.toString();
    } catch(Exception e) {
      throw new RuntimeException(e);
    } finally {
      if(closeAfter) close(fr);
    }
  }
  
  public static void copy(InputStream r, OutputStream w, boolean closeAfter) {
    try {
      byte[] c = new byte[512];
      int numread = 0;
      while((numread = r.read(c)) != -1) {
        w.write(c, 0, numread);
      }
      w.flush();
    } catch(IOException exc) {
      throw new RuntimeException(exc);
    } finally {
      if(closeAfter) {
        close(r);
        close(r);
      }
    }
  }
  
  public static void debugAllContentResolvers(Context ctx) {
    String[] all = new String[] {
      CallLog.CONTENT_URI.toString(),
      "content://sms",
      "content://sms/inbox",
      "content://sms/conversations",
      "content://mms",
      "content://mms/conversations",
      "content://mms/part",
      "content://mms/addr",
      "content://mms/sent",
      "content://mms/inbox",
      "content://mms-sms/", 
      "content://mms-sms/conversations",
      "content://mms-sms/complete-conversations",
      "content://mms-sms/messages/byphone",
      "content://mms-sms/threadID",
      "content://mms-sms/canonical-address",
    };
    for(String s: all) debugContentResolvers(ctx, s);
  }
  
  public static void debugContentResolvers(Context ctx, String uri) {
    debugContentResolvers(ctx, Uri.parse(uri));
  }
  
  public static void debugContentResolvers(Context ctx, Uri uri) {
    try {
      Cursor cur = ctx.getContentResolver().query(uri, null, null, null, null);
      Log.d(TAG, "columns for uri: " + uri + ": " + Arrays.asList(cur.getColumnNames()).toString());
      cur.close();
    } catch(Exception exc) {
      Log.e(TAG, "Error finding columns for uri: " + uri); //, exc);
    }
  }  

  public static List<String> tokens(String stringrep, String separatorChars, boolean trim, boolean ignoreBlanks) {
    List<String> l = new ArrayList<String>();
    StringTokenizer stz = new StringTokenizer(stringrep, separatorChars);
    String s = null;
    while(stz.hasMoreTokens()) {
      s = stz.nextToken();
      if(trim) {
        s = s.trim();
      }
      if(!(ignoreBlanks && TextUtils.isEmpty(s))) l.add(s);
    }
    return l;
  }

  public static void close(Closeable... cs) {
    for(Closeable c: cs) {
      if(c != null) {
        try { c.close(); }
        catch(Exception e) { Log.e(TAG, "Error closing: " + c, e); }
      }
    }
  }

  static SharedPreferences getPreferences(Activity activity) {
    return activity.getSharedPreferences(Helper.SHARED_PREFERENCES_KEY, Activity.MODE_PRIVATE);
  }

}

