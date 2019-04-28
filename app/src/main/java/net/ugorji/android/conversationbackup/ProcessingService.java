package net.ugorji.android.conversationbackup;

import android.Manifest;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.ugorji.android.conversationbackup.Helper.Cl;
import net.ugorji.android.conversationbackup.Helper.MmsEntry;
import net.ugorji.android.conversationbackup.Helper.Ms;
import org.json.JSONArray;
import org.json.JSONObject;

public class ProcessingService extends IntentService {
  private static final String TAG = ProcessingService.class.getSimpleName();
  private static final DateFormat DF = new SimpleDateFormat("yyyy_MM_dd__HH_mm_ss");

  private Intent updateIntent;
  // private Intent notificationIntent;
  // private PendingIntent notificationContentIntent;
  private NotificationManager notificationManager;
  // private File appdir;
  // private File resultLogFile;
  private Map<String, String> numberToDisplayName = new HashMap<String, String>();

  public ProcessingService() {
    super("ProcessingService");
    updateIntent = new Intent(Helper.UPDATE_PROGRESS_ACTION);
  }

  @Override
  protected void onHandleIntent(Intent intent) {
    try {
      // runMyServiceBS();
      // Helper.debugAllContentResolvers(this);
      boolean[] args = intent.getBooleanArrayExtra(BuildConfig.APPLICATION_ID + ".perms");
      // appdir = new File(intent.getStringExtra(BuildConfig.APPLICATION_ID + ".appdir"));
      // resultLogFile = new File(intent.getStringExtra(BuildConfig.APPLICATION_ID + ".log"));
      runMyService(args);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    } // finally { //finish(); }
  }

  private void runMyService(boolean[] args) throws Exception {
    SharedPreferences sharedPreferences = Helper.getPreferences(this);

    JSONObject callLogJs = new JSONObject();
    JSONObject messagesJs = new JSONObject();
    JSONObject summaryJs = new JSONObject();
    
    Helper.Summary summ = Helper.getSummary(sharedPreferences);
    
    summ.backup_messages = summ.backup_messages && args[0];
    summ.backup_mms_attachments = summ.backup_mms_attachments && args[0];
    summ.backup_call_records = summ.backup_call_records && args[1];
    summ.delete_after_backup = summ.delete_after_backup && args[4] && args[5] && args[6];

    // DO SAFETY STUFF
    summ.delete_after_backup = summ.delete_after_backup && Helper.SAFETY_ALLOW_DELETE_AFTER_BACKUP;
    
    // specific_numbers_to_backup_edit = Helper.write
    //  (Helper.tokens(specific_numbers_to_backup_edit, " ,'\"", true, true), ",", "'", "'");

    // String specific_numbers_to_backup_edit_where_clause = Helper.write
    //   (Helper.tokens(summ.specific_numbers_to_backup_edit, " ,'\"", true, true),
    //    " OR ", CallLog.Calls.NUMBER + " like '%", "'");
    // String specific_addresses_to_backup_edit_where_clause = Helper.write
    //   (Helper.tokens(summ.specific_numbers_to_backup_edit, " ,'\"", true, true),
    //    " OR ", Helper.Cols.ADDRESS + " like '%", "'");

    String specific_numbers_to_backup_edit_where_clause =
        Helper.write(
            Helper.tokens(summ.specific_numbers_to_backup_edit, " ,'\"", true, true),
            " OR ",
            " PHONE_NUMBERS_EQUAL(" + CallLog.Calls.NUMBER + ", '",
            "', 0)");
    String specific_addresses_to_backup_edit_where_clause =
        Helper.write(
            Helper.tokens(summ.specific_numbers_to_backup_edit, " ,'\"", true, true),
            " OR ",
            " PHONE_NUMBERS_EQUAL(" + Helper.Cols.ADDRESS + ", '",
            "', 0)");

    String s = null;
    // boolean  = sharedPreferences.getBoolean("");

    String backupDestName = "android_conversation_backup__" + DF.format(new Date());
    File tmpdir = new File(getCacheDir(), backupDestName);
    if (!tmpdir.mkdirs())
      throw new RuntimeException("Unable to create directory for backup files: " + tmpdir);

    // initialize result file
    // Helper.writeToResultLog(this, false, "", null);
    // Helper.writeToFile(resultLogFile, false, "", null);

    int numsteps = 0;
    if (summ.backup_call_records) numsteps++;
    if (summ.backup_messages) numsteps++;
    if (summ.backup_mms_attachments) numsteps++;
    if (summ.delete_after_backup) {
      if (summ.backup_call_records) numsteps++;
      if (summ.backup_messages) numsteps++;
    }
    if (summ.share_archive) numsteps++;
    numsteps++; // for zipping all files
    int percentIncr = (100 / numsteps) - 1; // reduce slightly, so we're never at 100%
    int percentCompl = 0;
    String callLogsWhereClause = null;
    String messagesWhereClause = null;
    if (summ.backup_call_records) {
      updateProgress(
          getString(R.string.progress_backup_call_records) + getString(R.string.ellipsis),
          percentCompl);
      // String whereClause = (backup_all_numbers ? null :
      //                      (CallLog.Calls.NUMBER + " IN (" + specific_numbers_to_backup_edit +
      // ")"));
      String whereClause =
          (summ.backup_all_numbers ? null : specific_numbers_to_backup_edit_where_clause);
      callLogsWhereClause = whereClause;
      String[] projection =
          new String[] {
            Calls._ID, Calls.TYPE, Calls.CACHED_NAME,
            Calls.NUMBER, Calls.DATE, Calls.DURATION
          };
      Log.d(TAG, String.format("backup_call_records: whereClause: %s", callLogsWhereClause));
      if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG)
          != PackageManager.PERMISSION_GRANTED) {
        throw new RuntimeException("Unexpected permission denial");
      }
      Cursor cur =
          getContentResolver()
              .query(CallLog.Calls.CONTENT_URI, projection, whereClause, null, Calls.DATE);
      JSONArray jsonarr = new JSONArray();
      if (cur.moveToFirst()) {
        do {
          Cl cl = new Cl();
          cl.id = cur.getLong(cur.getColumnIndex(Calls._ID));
          cl.name = cur.getString(cur.getColumnIndex(Calls.CACHED_NAME));
          cl.duration = cur.getInt(cur.getColumnIndex(Calls.DURATION));
          cl.number = cur.getString(cur.getColumnIndex(Calls.NUMBER));
          cl.timestamp = cur.getLong(cur.getColumnIndex(Calls.DATE));
          int cti = cur.getInt(cur.getColumnIndex(Calls.TYPE));
          if (cti == Calls.INCOMING_TYPE) cl.type = "incoming";
          else if (cti == Calls.OUTGOING_TYPE) cl.type = "outgoing";
          else if (cti == Calls.MISSED_TYPE) cl.type = "missed";
          jsonarr.put(cl.toJSON());
          // JSONObject jobj = new JSONObject();
          // //for(String proj0: projection) jobj.put(proj0,
          // cur.getString(cur.getColumnIndex(proj0)));
          // jobj.put("id", cur.getString(cur.getColumnIndex(Calls._ID)));
          // jobj.put("name", cur.getString(cur.getColumnIndex(Calls.CACHED_NAME)));
          // jobj.put("duration", cur.getString(cur.getColumnIndex(Calls.DURATION)));
          // jobj.put("number", cur.getString(cur.getColumnIndex(Calls.NUMBER)));
          // String calltype = "";
          // int cti = cur.getInt(cur.getColumnIndex(Calls.TYPE));
          // if(cti == Calls.INCOMING_TYPE) calltype = "incoming";
          // else if(cti == Calls.OUTGOING_TYPE) calltype = "outgoing";
          // else if(cti == Calls.MISSED_TYPE) calltype = "missed";
          // jobj.put("type", calltype);
          // jobj.put("date", cur.getLong(cur.getColumnIndex(Calls.DATE)));
          // jobj.put("datetime", Helper.SDF.format(new
          // Date(cur.getLong(cur.getColumnIndex(Calls.DATE)))));
          // jsonarr.put(jobj);

        } while (cur.moveToNext());
      }
      cur.close();
      
      callLogJs.put("call_logs", jsonarr);
      // Helper.writeJSON("call_logs", jsonarr, tmpdir);
      updateProgress(
          getString(R.string.progress_backup_call_records) + getString(R.string.done),
          (percentCompl += percentIncr));
    }

    Set<Long> msThreadIds = new HashSet<Long>();
    if (summ.backup_messages) {
      updateProgress(
          getString(R.string.retrieving_messages) + getString(R.string.ellipsis), percentCompl);
      List<Ms> allms = new ArrayList<Ms>();
      // String whereClause = (backup_all_numbers ? null :
      //                      (Helper.Cols.ADDRESS + " IN (" + specific_numbers_to_backup_edit +
      // ")"));
      String whereClause =
          (summ.backup_all_numbers ? null : specific_addresses_to_backup_edit_where_clause);
      messagesWhereClause = whereClause;
      String[] projection = new String[] {Helper.Cols.ADDRESS, Helper.Cols.THREAD_ID};
      Log.d(TAG, String.format("backup_messages: whereClause: %s", messagesWhereClause));
      Cursor cur =
          getContentResolver()
              .query(
                  Helper.Cols.COMPLETE_CONVERSATIONS_CONTENT_URI,
                  projection,
                  whereClause,
                  null,
                  null);
      if (cur.moveToFirst()) {
        do {
          msThreadIds.add(cur.getLong(cur.getColumnIndex(Helper.Cols.THREAD_ID)));
        } while (cur.moveToNext());
      }
      cur.close();

      whereClause =
          Helper.Cols.THREAD_ID + " IN (" + Helper.write(msThreadIds, ", ", "'", "'") + ")";
      projection =
          new String[] {
            Helper.Cols.ID,
            Helper.Cols.THREAD_ID,
            Helper.Cols.NAME,
            Helper.Cols.ADDRESS,
            Helper.Cols.DATE,
            Helper.Cols.SUBJECT,
            Helper.Cols.BODY,
            Helper.Cols.MMS_MESSAGE_BOX,
            Helper.Cols.SMS_MESSAGE_BOX,
            Helper.Cols.MMS_TRANSACTION_ID,
          }; // Helper.Cols.TYPE_DISCRIMINATOR_COLUMN,
      // try null projection, to see if we get MMS - projection cannot be null
      // projection = null;
      cur =
          getContentResolver()
              .query(
                  Helper.Cols.COMPLETE_CONVERSATIONS_CONTENT_URI,
                  projection,
                  whereClause,
                  null,
                  Helper.Cols.DATE);
      Log.d(TAG, "Column Names: " + Arrays.toString(cur.getColumnNames()));
      if (cur.moveToFirst()) {
        do {
          Ms sms = new Ms();
          sms.id = cur.getLong(cur.getColumnIndex(Helper.Cols.ID));
          sms.timestamp = cur.getLong(cur.getColumnIndex(Helper.Cols.DATE));
          sms.number = cur.getString(cur.getColumnIndex(Helper.Cols.ADDRESS));
          sms.text = cur.getString(cur.getColumnIndex(Helper.Cols.BODY));
          sms.subject = cur.getString(cur.getColumnIndex(Helper.Cols.SUBJECT));
          // sms.mms =
          // cur.getString(cur.getColumnIndex(Helper.Cols.TYPE_DISCRIMINATOR_COLUMN)).equals("mms");
          sms.mms =
              ((s = cur.getString(cur.getColumnIndex(Helper.Cols.MMS_TRANSACTION_ID))) != null
                  && s.trim().length() > 0);
          String msgboxCol = (sms.mms ? Helper.Cols.MMS_MESSAGE_BOX : Helper.Cols.SMS_MESSAGE_BOX);
          sms.sender = (cur.getInt(cur.getColumnIndex(msgboxCol)) != Helper.Cols.MESSAGE_BOX_INBOX);
          // sms.name = cur.getString(cur.getColumnIndex(Helper.Cols.NAME)); //null; //TBD

          Log.d(TAG, String.format("MS Info: Number: %s, Name: %s", sms.number, sms.name));
          if (sms.mms) {
            sms.timestamp = sms.timestamp * 1000; // mms timestamp is in seconds
            Log.d(TAG, "Found mms: #: " + sms.number + ", text: " + sms.text + ", id: " + sms.id);
            // Helper.Cols.ADDR_MSG_ID + " = " + sms.id + " AND " +
            whereClause =
                (Helper.Cols.ADDR_TYPE
                    + (sms.sender ? " != " : " = ")
                    + Helper.Cols.PDU_HEADER_FROM);
            Cursor curpart =
                getContentResolver()
                    .query(
                        Uri.parse("content://mms/" + sms.id + "/addr"),
                        new String[] {
                          Helper.Cols.ADDRESS, Helper.Cols.ADDR_MSG_ID, Helper.Cols.ADDR_TYPE
                        },
                        whereClause,
                        null,
                        null);
            if (curpart.moveToFirst()) {
              sms.number = curpart.getString(curpart.getColumnIndex(Helper.Cols.ADDRESS));
            }
            curpart.close();
          }
          sms.name = getDisplayName(sms.number);

          allms.add(sms);
        } while (cur.moveToNext());
      }

      cur.close();
      cur = null;

      updateProgress(
          getString(R.string.retrieving_messages) + getString(R.string.done),
          (percentCompl += percentIncr));

      // Now get MMS attachments if requested
      if (summ.backup_mms_attachments) {
        updateProgress(
            getString(R.string.backing_up_mms_attachments) + getString(R.string.ellipsis),
            percentCompl);
        for (Ms mms : allms) {
          if (mms.mms) {
            Log.d(TAG, "Backing up Attachment for: " + mms.id);
            Cursor curpart =
                getContentResolver()
                    .query(
                        Helper.Cols.MMS_PART_CONTENT_URI,
                        new String[] {
                          Helper.Cols.ID,
                          Helper.Cols.PART_DATA,
                          Helper.Cols.PART_MSG_ID,
                          Helper.Cols.PART_FILENAME,
                          Helper.Cols.PART_NAME,
                          Helper.Cols.PART_SEQ,
                          Helper.Cols.PART_TEXT,
                          Helper.Cols.PART_CONTENT_TYPE,
                          Helper.Cols.PART_CONTENT_LOCATION
                        },
                        Helper.Cols.PART_MSG_ID + "=" + mms.id,
                        null,
                        Helper.Cols.PART_SEQ);
            if (curpart.moveToFirst()) {
              ByteArrayOutputStream baos = new ByteArrayOutputStream(256);
              byte[] buffer = new byte[256];
              do {
                MmsEntry mmse = new MmsEntry();
                mmse.text = curpart.getString(curpart.getColumnIndex(Helper.Cols.PART_TEXT));
                // if text matches <smil> ... </smil>, skip it
                if (mmse.text != null && Helper.SMIL_PATTERN.matcher(mmse.text).matches()) continue;
                mms.entries.add(mmse);
                mmse.filename =
                    Helper.toFilename(
                        curpart.getString(curpart.getColumnIndex(Helper.Cols.PART_FILENAME)));
                Log.d(TAG, "Backing up Attachment for: " + mms.id + " ... " + mmse.filename);
                // Uri partURI =
                // Uri.parse(curpart.getString(curpart.getColumnIndex(Helper.Cols.PART_DATA)));
                String partId = curpart.getString(curpart.getColumnIndex(Helper.Cols.ID));
                String partData = curpart.getString(curpart.getColumnIndex(Helper.Cols.PART_DATA));
                String partLoc =
                    curpart.getString(curpart.getColumnIndex(Helper.Cols.PART_CONTENT_LOCATION));
                Uri partURI = Uri.withAppendedPath(Helper.Cols.MMS_PART_CONTENT_URI, partId);
                Log.d(TAG,
                    "Attachment for message: " + mms.id + ", part: " + partId + ", partUri: " + partURI
                        + ", partData: " + partData  + ", partLoc: " + partLoc + ", filename: " + mmse.filename
                        + ", text: " + mmse.text);
                // e.g. smil.xml has no data (so don't even try to find stuff)
                if (partData != null) {
                  mmse.filename = Helper.toFilename(partLoc);
                  baos.reset();
                  InputStream is = null;
                  try {
                    is = getContentResolver().openInputStream(partURI);
                    if (is != null) {
                      // byte[] buffer = new byte[256];
                      int len = -1;
                      while ((len = is.read(buffer)) >= 0) baos.write(buffer, 0, len);
                    }
                  } finally {
                    baos.flush();
                    Helper.close(is, baos);
                    // mmse.content = baos.toByteArray();
                  }
                  // write attachment to file
                  OutputStream ffos =
                      new FileOutputStream(new File(tmpdir, "mms." + mms.id + "." + mmse.filename));
                  // ffos.write(mmse.content);
                  baos.writeTo(ffos);
                  ffos.flush();
                  Helper.close(ffos);
                }
              } while (curpart.moveToNext());
            }
            curpart.close();

            // now check if text is blank and there's any text field, and set value
            if (mms.text == null || mms.text.trim().length() == 0) {
              for (MmsEntry mmsee : mms.entries) {
                if (mmsee.text != null && mmsee.text.trim().length() > 0) {
                  mms.text = mmsee.text;
                  break;
                }
              }
            }
          }
        }
        updateProgress(
            getString(R.string.backing_up_mms_attachments) + getString(R.string.done),
            (percentCompl += percentIncr));
      }

      // need to sort again, since the mms dates are all messed up and threw off the initial db sort
      Collections.sort(allms);

      JSONArray jsonarr = new JSONArray();
      for (Ms ms : allms) jsonarr.put(ms.toJSON());
      // Helper.writeJSON("messages", jsonarr, tmpdir);
      messagesJs.put("messages", jsonarr);
      updateProgress(
          getString(R.string.writing_messages_to_json) + getString(R.string.done), percentCompl);
    }

    // now create summary file
    // Helper.writeJSON("summary", summ.toJSON(), tmpdir);
    summaryJs = summ.toJSON();

    if (!Helper.INLINE_RESOURCES_IN_INDEX_HTML) {
      Helper.writeJSON("call_logs", callLogJs, tmpdir);
      Helper.writeJSON("messages", messagesJs, tmpdir);
      Helper.writeJSON("summary", summaryJs, tmpdir);
      Helper.copyAssets(this, tmpdir, "acb_style.css");
    } 
   
    // we now create a index.html with js, css and html all inline
    FileOutputStream fos = new FileOutputStream(new File(tmpdir, "index.html"));
    PrintWriter pw = new PrintWriter(fos);
    pw.println("<!DOCTYPE html>\n<html>\n<head>\n");
    pw.flush();
    InputStream fis = getAssets().open("index.head.snippet.html");
    Helper.copy(fis, fos, false);
    Helper.close(fis);
    fos.flush();
    if (Helper.INLINE_RESOURCES_IN_INDEX_HTML) {
      pw.println("<style>\n");
      pw.flush();
      fis = getAssets().open("acb_style.css");
      Helper.copy(fis, fos, false);
      Helper.close(fis);
      fos.flush();
      pw.println("</style>\n");
    } else {
      pw.println("<link href=\"acb_style.css\" rel=\"stylesheet\" type=\"text/css\"/>");
      for(String s2: new String[]{"summary", "call_logs", "messages"}) {
        pw.println("<script src=\"acb_" + s2 + ".json\"></script>");
      }
    }
    pw.println("<script>\n");
    if (Helper.INLINE_RESOURCES_IN_INDEX_HTML) {
      pw.print("var acb_summary = ");
      Helper.writeJSON(pw, summaryJs, 2, 0);
      pw.println();
      pw.print("var acb_call_logs = ");
      Helper.writeJSON(pw, callLogJs, 2, 0);
      pw.println();
      pw.print("var acb_messages = ");
      Helper.writeJSON(pw, messagesJs, 2, 0);
      pw.println();
    }
    pw.flush();
    fis = getAssets().open("acb_script.js");
    Helper.copy(fis, fos, false);
    Helper.close(fis);
    fos.flush();
    pw.println("\n$(document).ready(doInit);");
    pw.println("</script>\n</head>");
    pw.flush();
    fis = getAssets().open("index.body.html");
    Helper.copy(fis, fos, false);
    Helper.close(fis);
    fos.flush();
    pw.println("</html>");
    pw.flush();
    Helper.close(fos);
    Helper.close(pw);

    // now package all into a zip file
    // FileOutputStream zfos = openFileOutput(backupDestName + ".zip",
    //                                        Context.MODE_PRIVATE);
    // File zipfile = getFileStreamPath(backupDestName + ".zip");
    // // File zipfile = new File(getFilesDir(), backupDestName + ".zip");
    File zipfile = new File(Helper.getArchivesDir(this),  backupDestName + ".zip");
    FileOutputStream zfos = new FileOutputStream(zipfile);
    updateIntent.putExtra("zipfile", zipfile.getAbsolutePath());
    updateProgress(
        getString(R.string.creating_zip_file) + getString(R.string.ellipsis), percentCompl);
    File[] filess = tmpdir.listFiles();
    ZipOutputStream zout = new ZipOutputStream(zfos); // new FileOutputStream(zipfile));
    byte[] zbuf = new byte[1024];
    if (filess != null && filess.length > 0) {
      for (File f : filess) {
        String fname = f.getName();
        // if(fname.startsWith("acb_") && (fname.endsWith(".js") || fname.endsWith(".css"))) continue;
        FileInputStream fin = new FileInputStream(f);
        zout.putNextEntry(new ZipEntry(fname));
        int flen = -1;
        while ((flen = fin.read(zbuf)) != -1) {
          zout.write(zbuf, 0, flen);
        }
        zout.flush();
        zout.closeEntry();
        Helper.close(fin);
      }
    }
    zout.finish();
    zout.flush();
    Helper.close(zfos);
    Helper.close(zout);
    // recursively delete the tmp dir
    if (Helper.SAFETY_ALLOW_DELETE_TMP_DIR) Helper.deleteFile(tmpdir);
    // skip the things for delete after backup, share backup, etc
    updateProgress(
        getString(R.string.creating_zip_file) + getString(R.string.done),
        (percentCompl += percentIncr));

    if (summ.delete_after_backup) {
      // delete call logs
      if (summ.backup_call_records) {
        updateProgress(
            getString(R.string.deleting_call_logs) + getString(R.string.ellipsis), percentCompl);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED) {
          throw new RuntimeException("Unexpected permission denial");
        }
        getContentResolver().delete(CallLog.Calls.CONTENT_URI, callLogsWhereClause, null);
        updateProgress(
            getString(R.string.deleting_call_logs) + getString(R.string.done),
            (percentCompl += percentIncr));
      }
      // delete messages
      if (summ.backup_messages) {
        updateProgress(
            getString(R.string.deleting_messages) + getString(R.string.ellipsis), percentCompl);
        // we need a diff way to delete the messages TBD
        // getContentResolver().delete(Helper.Cols.COMPLETE_CONVERSATIONS_CONTENT_URI,
        // messagesWhereClause, null);
        // getContentResolver().delete(Helper.Cols.CONVERSATIONS_CONTENT_URI, messagesWhereClause,
        // null);
        getContentResolver()
            .delete(
                Helper.Cols.CONVERSATIONS_CONTENT_URI,
                Helper.Cols.THREAD_ID + " IN (" + Helper.write(msThreadIds, ", ", "'", "'") + ")",
                null);
        updateProgress(
            getString(R.string.deleting_messages) + getString(R.string.done),
            (percentCompl += percentIncr));
      }
    }

    updateIntent.putExtra("share_archive", summ.share_archive);
    updateProgress(getString(R.string.completed_processing), 100);
  }

  // @SuppressWarnings("unused")
  // private void runMyServiceBS() {
  //   // use the values here to do the work
  //   for (int i = 0; i <= 10; i++) {
  //     updateProgress("doing some work" + " [" + (i * 10) + "%]", i * 10);
  //   }
  // }

  private void updateProgress(String message, int percent) {
    String longMsg = message + " (" + percent + "%)";
    // Add longMsg to log file
    // Helper.writeToResultLog(this, true, longMsg, "\n");
    // Helper.writeToFile(resultLogFile, true, longMsg, "\n");

    Log.d(TAG, "ProgressMessage: " + longMsg);

    updateIntent.putExtra("message", message);
    updateIntent.putExtra("percent_completed", percent);
    sendBroadcast(updateIntent);

    if (notificationManager == null) {
      notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }
    Intent notificationIntent = new Intent(this, HomeActivity.class);
    // notificationIntent.putExtra(BuildConfig.APPLICATION_ID + ".log", resultLogFile.getAbsolutePath());
    PendingIntent notificationContentIntent =
        PendingIntent.getActivity(this, 0, notificationIntent, 0);
    Notification notification =
        new NotificationCompat.Builder(getApplicationContext())
            .setSmallIcon(R.drawable.icon)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(longMsg)
            .setContentIntent(notificationContentIntent)
            .build();

    notificationManager.notify(Helper.PROCESSING_NOTIFICATION_ID, notification);
  }

  private String getDisplayName(String number) {
    Log.d(TAG, "getDisplayName: " + number);
    if (Helper.SAFETY_RETURN_NULL_FOR_DISPLAY_NAME) return null;
    if (numberToDisplayName.containsKey(number)) return numberToDisplayName.get(number);
    String n = null;
    Uri uri = Uri.withAppendedPath(Helper.Cols.PHONE_LOOKUP_URI, Uri.encode(number));
    Cursor cur =
        getContentResolver().query(uri, new String[] {Helper.Cols.DISPLAY_NAME}, null, null, null);
    if (cur.moveToFirst()) {
      n = cur.getString(0);
    }
    cur.close();
    numberToDisplayName.put(number, n);
    Log.d(TAG, String.format("getDisplayName: %s ==> %s", number, n));
    return n;
  }
}
