package net.ugorji.android.conversationbackup;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
import android.provider.CallLog;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
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
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

// import org.json.JSONException;

public class Helper {
  // private static boolean INITED = false;
  
  // static final Charset UTF_8 = Charset.forName("UTF-8")
  // static final Charset US_ASCII = Charset.forName("US-ASCII");

  // TODO: for production, set SAFETY_DEV_MODE=false
  static final boolean SAFETY_DEV_MODE = false;
  static final boolean INLINE_RESOURCES_IN_INDEX_HTML = false;
  static final boolean PREFER_EXTERNAL_DIR = true;
  
  static final boolean SAFETY_ALLOW_DELETE_AFTER_BACKUP = !SAFETY_DEV_MODE,
    SAFETY_ALLOW_DELETE_TMP_DIR = !SAFETY_DEV_MODE,
    SAFETY_RETURN_NULL_FOR_DISPLAY_NAME = false; 
  
  static final Pattern SMIL_PATTERN =
      Pattern.compile(".*<smil>(.*)</smil>.*", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

  static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss zz");

  static final String TAG = Helper.class.getSimpleName(),
      EXIT_ACTION = BuildConfig.APPLICATION_ID + "EXIT_ACTION",
      UPDATE_PROGRESS_ACTION = BuildConfig.APPLICATION_ID + "UPDATE_PROGRESS",
      ARCHIVES_ACTION = BuildConfig.APPLICATION_ID + "ARCHIVES",
      SHARED_PREFERENCES_KEY = "shared_preferences",
      ASSET_EULA = "EULA",
      PREFERENCE_EULA_ACCEPTED = "eula.accepted";

  static final String RESULT_LOG_FILE_NAME = "result-log.txt";
  
  static final int SEND_ARCHIVE_REQUEST = 2, SELECT_CONTACT_REQUEST = 4;

  static final int PROCESSING_NOTIFICATION_ID = 1;

  static final IntentFilter PROGRESS_INTENT_FILTER = new IntentFilter();
  static final IntentFilter ARCHIVES_INTENT_FILTER = new IntentFilter();

  static {
    PROGRESS_INTENT_FILTER.addAction(Helper.UPDATE_PROGRESS_ACTION);
    ARCHIVES_INTENT_FILTER.addAction(Helper.ARCHIVES_ACTION); 
  }

  static DialogInterface.OnClickListener CancelDialogOnClick = new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog1, int id1) {
        dialog1.cancel();
      }
    };

  static DialogInterface.OnClickListener DismissDialogOnClick = new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog1, int id1) {
        dialog1.dismiss();
      }
    };

  static void dismissAndFinish(DialogInterface d, Activity a) {
    d.dismiss();
    a.finish();
  }
  
  public static class MyDialogFrag extends DialogFragment {
    int cancelId;
    int dismissId;
    int finishId; // cancel dialog and finish the activity
    int msgId;
    String msg;
    View msgView;
    int titleId;
    String title;
    int actionMsgId;
    DialogInterface.OnClickListener action;
    String tag;

    {
      setCancelable(false);
    }
        
    void setMyAction(DialogInterface.OnClickListener action) { this.action = action; }
    
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
      
      if(titleId != 0) {
        builder.setTitle(getString(titleId));
      } else if(title != null) {
        builder.setTitle(title);
      }
      if(msgId != 0) {
        builder.setMessage(getString(msgId));
      } else if(msg != null) {
        builder.setMessage(msg);
      } else if(msgView != null) {
        ViewParent vp = msgView.getParent();
        if(vp != null && vp instanceof ViewGroup) ((ViewGroup)vp).removeView(msgView);
        builder.setView(msgView);
      }
      
      // create prompts
      // - action + finish
      // - action + cancel
      // - action + dismiss
      // - action
      // - finish (no action)
      // - dismiss (no action)

      if(action == null) {
        if(finishId != 0) {
          builder.setNeutralButton(getString(finishId),
                                   (dialog1, id1) -> dismissAndFinish(dialog1, getActivity()));
        } else if(dismissId != 0) {
          builder.setNeutralButton(getString(dismissId), Helper.DismissDialogOnClick);
        } else {
          builder.setNeutralButton(getString(R.string.prompt_ok), Helper.DismissDialogOnClick);
        }
      } else {
        if(finishId != 0) {
          builder.setPositiveButton(getString(actionMsgId), action);
          builder.setNegativeButton(getString(finishId),
                                    (dialog1, id1) -> dismissAndFinish(dialog1, getActivity()));
        } else if(cancelId != 0) {
          builder.setPositiveButton(getString(actionMsgId), action);
          builder.setNegativeButton(getString(cancelId), Helper.CancelDialogOnClick);
        } else if(dismissId != 0) {
          builder.setPositiveButton(getString(actionMsgId), action);
          builder.setNegativeButton(getString(dismissId), Helper.DismissDialogOnClick);
        } else {
          builder.setNeutralButton(getString(actionMsgId), action);
        }
      }
      builder.setCancelable(false);
      Dialog d = builder.create();
      d.setCanceledOnTouchOutside(false);
      return d;
    }
  }
  
  static class Cols {
    static String ID = "_id", // Telephony.Mms._ID
        THREAD_ID = "thread_id", // Telephony.Mms.THREAD_ID
        ADDRESS = "address", // Telephony.Sms.ADDRESS
        NAME = "name", // ...
        DISPLAY_NAME = "display_name", // ContactsContract.ContactNameColumns.DISPLAY_NAME_PRIMARY
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
    static Uri MMS_CONTENT_URI = Uri.parse("content://mms"), // Telephony.Mms.CONTENT_URI
        MMS_PART_CONTENT_URI =
            Uri.parse("content://mms/part"), // Telephony.Mms.CONTENT_URI + "part"
        MMS_ADDR_CONTENT_URI =
            Uri.parse("content://mms/addr"), // Telephony.Mms.CONTENT_URI + "part"
        SMS_CONTENT_URI = Uri.parse("content://sms"), // Telephony.Sms.CONTENT_URI
        CONVERSATIONS_CONTENT_URI =
            Uri.parse("content://mms-sms/conversations"), // Telephony.Sms...
        COMPLETE_CONVERSATIONS_CONTENT_URI =
            Uri.parse("content://mms-sms/complete-conversations"), // Telephony.Sms...
        PHONE_LOOKUP_URI = // ContactsContract.PhoneLookup.CONTENT_FILTER_URI
        Uri.parse("content://com.android.contacts/phone_lookup"),
        XYZ2 = null;
    static int MESSAGE_BOX_INBOX = 1, // if not in inbox, then the message was not sent by us
        PDU_HEADER_FROM = 0x89,
        XYZ3 = 0;
  }

  static class Summary {
    public long timestamp;
    public boolean backup_all_numbers;
    public boolean backup_messages;
    public boolean backup_mms_attachments;
    public boolean backup_call_records;
    public boolean delete_after_backup;
    public boolean random_question;
    public boolean share_archive;
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
      // jobj.put("random_question", random_question);
      jobj.put("share_archive", share_archive);
      jobj.put("specific_numbers_to_backup_edit", specific_numbers_to_backup_edit);
      return jobj;
    }

    public void parsePreferences(SharedPreferences sharedPreferences) {
      random_question = sharedPreferences.getBoolean("random_question", false);
      share_archive = sharedPreferences.getBoolean("share_archive", false);
      specific_numbers_to_backup_edit =
        sharedPreferences.getString("specific_numbers_to_backup_edit", "");

      backup_all_numbers = sharedPreferences.getBoolean("backup_all_numbers", false);
      backup_mms_attachments = sharedPreferences.getBoolean("backup_mms_attachments", false);
    
      backup_messages = sharedPreferences.getBoolean("backup_messages", false);
      backup_call_records = sharedPreferences.getBoolean("backup_call_records", false);
      delete_after_backup = sharedPreferences.getBoolean("delete_after_backup", false);
    }
  }

  // Helper Object (Ho)
  static class Ho implements Comparable<Ho> {
    public long timestamp;
    public long id;
    public String number;
    public String name;

    @Override
    public int compareTo(Ho s2) {
      if (timestamp < s2.timestamp) return -1;
      else if (timestamp > s2.timestamp) return 1;
      else return 0;
      // return (int)(timestamp - s2.timestamp); //problem occurring due to truncation???
    }

    public JSONObject toJSON() throws Exception {
      // new JSONObject(this, FIELDS)
      JSONObject jobj = new JSONObject();
      jobj.put("timestamp", timestamp);
      jobj.put("datetime", SDF.format(new Date(timestamp)));
      jobj.put("number", number);
      jobj.put("id", id);
      jobj.put("name", name);
      return jobj;
    }
  }

  // Call Log (Cl)
  static class Cl extends Ho {
    public int duration;
    public String type;

    @Override
    public JSONObject toJSON() throws Exception {
      // new JSONObject(this, FIELDS)
      JSONObject jobj = super.toJSON();
      jobj.put("duration", duration);
      jobj.put("type", type);
      return jobj;
    }
  }

  // Message (Ms)
  static class Ms extends Ho {
    // static final String[] FIELDS = new String[] {"timestamp", "number", "id", "text",
    // "subject", "mms"};
    public String text;
    public String subject;
    public boolean mms;
    public boolean sender;
    public List<MmsEntry> entries = new ArrayList<MmsEntry>();

    @Override
    public JSONObject toJSON() throws Exception {
      // new JSONObject(this, FIELDS)
      JSONObject jobj = super.toJSON();
      jobj.put("text", text);
      jobj.put("subject", subject);
      jobj.put("mms", mms);
      jobj.put("sender", sender);
      if (mms && entries.size() > 0) {
        JSONArray jarr1 = new JSONArray();
        for (MmsEntry mse : entries) jarr1.put(mse.toJSON());
        jobj.put("entries", jarr1);
      }
      return jobj;
    }
  }

  static class MmsEntry {
    // static final String[] FIELDS = new String[] {"text", "filename"};
    public String text;
    // do not store the byte[] content here, as we hold onto memory during processing
    // public byte[] content;
    public String filename;

    public JSONObject toJSON() throws Exception {
      // new JSONObject(this, FIELDS)
      JSONObject jobj = new JSONObject();
      jobj.put("text", text);
      jobj.put("filename", filename);
      return jobj;
    }
  }

  static class MsComparator implements Comparator<Ms> {
    @Override
    public int compare(Ms o1, Ms o2) {
      return (int) (o1.timestamp - o2.timestamp);
    }
  }

  static void deleteFile(File f) {
    if (f.isDirectory()) {
      for (File f2 : f.listFiles()) deleteFile(f2);
    }
    if (!f.delete()) throw new RuntimeException("Unable to delete file: " + f);
  }

  static void doSleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException ie) {
    }
  }

  static void copyAssets(Service svc, File outdir, String... fileNames) throws Exception {
    for (String fileName : fileNames) {
      FileOutputStream fos = new FileOutputStream(new File(outdir, fileName));
      InputStream htmlis = svc.getAssets().open(fileName);
      copy(htmlis, fos, true);
      close(fos, htmlis);
    }
  }

  static void writeJSON(String tag, JSONArray jsonarr, File outdir) throws Exception {
    JSONObject jobj = new JSONObject();
    jobj.put(tag, jsonarr);
    writeJSON(tag, jobj, outdir);
  }

  static void writeJSON(String tag, JSONObject jobj, File outdir) throws Exception {
    PrintWriter pw = new PrintWriter(new FileWriter(new File(outdir, "acb_" + tag + ".json")));
    pw.print("var acb_" + tag + " = ");
    // pw.println(jobj.toString(2));
    // jobj.write(pw);
    writeJSON(pw, jobj, 2, 0);
    pw.println();
    pw.flush();
    close(pw);
  }

  static String write(Collection<?> c, Object separator, Object prefix, Object postfix) {
    StringBuilder sb = new StringBuilder();
    boolean first = true;
    for (Object o : c) {
      if (!first) sb.append(separator);
      sb.append(prefix).append(o).append(postfix);
      first = false;
    }
    return sb.toString();
  }

  static String getFileContents(File f) {
    try {
      if (f == null || !f.exists()) return null;
      return read(new FileReader(f), true);
    } catch (Exception e) {
      Log.e(TAG, "Error getting file contents: " + f, e);
      throw new RuntimeException(e);
    }
  }

  static String read(Reader fr, boolean closeAfter) {
    try {
      StringBuilder sb = new StringBuilder();
      char[] cbuf = new char[512];
      int clen = -1;
      while ((clen = fr.read(cbuf, 0, cbuf.length)) != -1) {
        sb.append(cbuf, 0, clen);
      }
      return sb.toString();
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      if (closeAfter) close(fr);
    }
  }

  static void copy(InputStream r, OutputStream w, boolean closeAfter) {
    try {
      byte[] c = new byte[512];
      int numread = 0;
      while ((numread = r.read(c)) != -1) {
        w.write(c, 0, numread);
      }
      w.flush();
    } catch (IOException exc) {
      throw new RuntimeException(exc);
    } finally {
      if (closeAfter) {
        close(r);
        close(w);
      }
    }
  }

  static void debugAllContentResolvers(Context ctx) {
    String[] all =
        new String[] {
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
    for (String s : all) debugContentResolvers(ctx, s);
  }

  static void debugContentResolvers(Context ctx, String uri) {
    debugContentResolvers(ctx, Uri.parse(uri));
  }

  static void debugContentResolvers(Context ctx, Uri uri) {
    try {
      Cursor cur = ctx.getContentResolver().query(uri, null, null, null, null);
      Log.d(TAG, "columns for uri: " + uri + ": " + Arrays.asList(cur.getColumnNames()).toString());
      cur.close();
    } catch (Exception exc) {
      Log.e(TAG, "Error finding columns for uri: " + uri); // , exc);
    }
  }

  static List<String> tokens(
      String stringrep, String separatorChars, boolean trim, boolean ignoreBlanks) {
    List<String> l = new ArrayList<String>();
    StringTokenizer stz = new StringTokenizer(stringrep, separatorChars);
    String s = null;
    while (stz.hasMoreTokens()) {
      s = stz.nextToken();
      if (trim) {
        s = s.trim();
      }
      if (!(ignoreBlanks && TextUtils.isEmpty(s))) l.add(s);
    }
    return l;
  }

  static void close(Closeable... cs) {
    for (Closeable c : cs) {
      if (c != null) {
        try {
          c.close();
        } catch (Exception e) {
          Log.e(TAG, "Error closing: " + c, e);
        }
      }
    }
  }

  static SharedPreferences getPreferences(Context ctx) {
    return ctx.getSharedPreferences(Helper.SHARED_PREFERENCES_KEY, Activity.MODE_PRIVATE);
  }

  static File getArchivesDir(Context ctx) {
    // prior to KitKat, permissions were required to access external storage.
    // Also, the method Environment.getExternalStorageState is only available from LOLLIPOP.
    // So: we check to use external dir only from LOLLIPOP and up.
    if(PREFER_EXTERNAL_DIR && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      File xd = ctx.getExternalFilesDir(null);
      // Log.d(TAG, ">>>>>>>>>>>>>>>>> build version > lollipop: " + Build.VERSION.SDK_INT +
      //         ", external storage state: " + Environment.getExternalStorageState(xd));
      if(xd != null && Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState(xd))) {
        return xd;
      }
    }
    return ctx.getFilesDir();
  }
  
  static Summary getSummary(SharedPreferences prefs) {
    Helper.Summary summ = new Helper.Summary();
    summ.timestamp = System.currentTimeMillis();
    
    summ.parsePreferences(prefs);
    return summ;
  }
  
  static void writeJSON(Appendable sb, JSONObject jo, int indentFactor, int indent)
      throws Exception {
    int i;
    int length = jo.length();
    if (length == 0) {
      sb.append("{}");
      return;
    }
    @SuppressWarnings("unchecked")
    Iterator<String> keys = jo.keys();
    int newindent = indent + indentFactor;
    String object;
    sb.append("{");
    boolean addComma = false;
    if (length == 1) {
      object = keys.next();
      sb.append(JSONObject.quote(object.toString()));
      sb.append(": ");
      writeJSONValue(sb, jo.get(object), indentFactor, indent);
      // sb.append(valueToString(jo.get(object), indentFactor, indent));
      addComma = true;
    } else {
      while (keys.hasNext()) {
        object = keys.next();
        if (addComma) sb.append(",");
        sb.append('\n');
        addComma = true;
        for (i = 0; i < newindent; i += 1) {
          sb.append(' ');
        }
        sb.append(JSONObject.quote(object.toString()));
        sb.append(": ");
        writeJSONValue(sb, jo.get(object), indentFactor, indent);
        // sb.append(valueToString(jo.get(object), indentFactor, newindent));
      }
      if (addComma) {
        sb.append('\n');
        for (i = 0; i < indent; i += 1) {
          sb.append(' ');
        }
      }
    }
    sb.append('}');
  }

  // used for names.
  // Zip is messed up because it supports only IBM437 and UTF-8, and to support
  // UTF-8, you need to set a bit (which java doesnt) and it shouldn't be modified
  // utf-8 (which java uses).
  // A safe way now is to use only ascii bytes, but the
  // problem with that is that asian-based languages will all decode as ???.
  // Since we have users from korea, Japan, etc, this will not work.
  // We have to invent a way to use DOS-supported ASCII characters in filename only.
  static String toFilename(String name) {
    int len = 0;
    if (name == null || (len = name.length()) == 0) return name;
    int idx = name.lastIndexOf("/");
    if (idx >= 0) name = name.substring(idx + 1);
    len = name.length();
    StringBuilder sb = new StringBuilder(len);
    StringBuilder sb2 = new StringBuilder();
    for (int i = 0; i < len; i++) {
      char c = name.charAt(i);
      // } else if('!' <= c &&
      //   c <= '~' &&
      //   !(c == '"'  || c == '\'' || c == '/' || c == '\\' ||
      //   c == '*'  || c == ':'  || c == '<' || c == '>'  ||
      //     c == '?'  || c == '|'  || c == '!' )) {
      if (c == ' ' || c == '\t') {
        sb.append('~');
      } else if ((c >= 'A' && c <= 'Z')
          || (c >= 'a' && c <= 'z')
          || (c >= '0' && c <= '9')
          || (c == '.' || c == '-' || c == '_')) {
        sb.append(c);
      } else {
        sb.append('~');
        sb2.append(Integer.toHexString(c));
      }
    }
    name = sb.toString();
    if (sb2.length() > 0) {
      // original name should be at end, so extension stays at the end.
      name = sb2.toString() + "~~~~" + name;
    }

    // name = US_ASCII.decode(US_ASCII.encode(name)).toString();
    return name;
  }

  static void writeJSON(Appendable sb, JSONArray jo, int indentFactor, int indent)
      throws Exception {
    int len = jo.length();
    if (len == 0) {
      sb.append("[]");
      return;
    }
    int i;
    sb.append("[");
    if (len == 1) {
      writeJSONValue(sb, jo.get(0), indentFactor, indent);
    } else {
      int newindent = indent + indentFactor;
      sb.append('\n');
      for (i = 0; i < len; i += 1) {
        if (i > 0) {
          sb.append(",\n");
        }
        for (int j = 0; j < newindent; j += 1) {
          sb.append(' ');
        }
        writeJSONValue(sb, jo.get(i), indentFactor, newindent);
      }
      sb.append('\n');
      for (i = 0; i < indent; i += 1) {
        sb.append(' ');
      }
    }
    sb.append(']');
  }

  private static void writeJSONValue(Appendable sb, Object o, int indentFactor, int indent)
      throws Exception {
    if (o == null) sb.append("null");
    else if (o instanceof String) sb.append(JSONObject.quote((String) o));
    else if (o instanceof JSONObject) writeJSON(sb, (JSONObject) o, indentFactor, indent);
    else if (o instanceof JSONArray) writeJSON(sb, (JSONArray) o, indentFactor, indent);
    else sb.append(o.toString());
  }
}
