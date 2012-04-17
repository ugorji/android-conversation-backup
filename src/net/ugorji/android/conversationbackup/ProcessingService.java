package net.ugorji.android.conversationbackup;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
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

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.util.Log;

public class ProcessingService extends IntentService {
  private static final String TAG = ProcessingService.class.getSimpleName();
  private static final DateFormat DF = new SimpleDateFormat("EEE_yyyy_MM_dd__HH_mm_ss");
  
  private Intent updateIntent;
  //private Intent notificationIntent;
  //private PendingIntent notificationContentIntent;
  private NotificationManager notificationManager;
  private File appdir;
  private File resultLogFile;
  private Map<String,String> numberToDisplayName = new HashMap<String,String>();
  
  public ProcessingService() {
    super("ProcessingService");
    updateIntent = new Intent(Helper.UPDATE_PROGRESS_ACTION);
    appdir = Helper.APP_DIR;
    resultLogFile = Helper.RESULT_LOG_FILE;
  }

  @Override
  protected void onHandleIntent(Intent intent) {
    try {
      //runMyServiceBS();
      //Helper.debugAllContentResolvers(this);
      runMyService();
    } catch(RuntimeException e) {
      throw e;
    } catch(Exception e) {
      throw new RuntimeException(e);
    } finally {
      //finish();
    }
  }
  
  private void runMyService() throws Exception {
    Helper.Summary summ = new Helper.Summary();
    summ.timestamp = System.currentTimeMillis();
    
    String s = null;
    SharedPreferences sharedPreferences = getSharedPreferences(Helper.SHARED_PREFERENCES_KEY, MODE_PRIVATE);
    summ.backup_all_numbers = sharedPreferences.getBoolean("backup_all_numbers", false);
    summ.backup_messages = sharedPreferences.getBoolean("backup_messages", false);
    summ.backup_mms_attachments = sharedPreferences.getBoolean("backup_mms_attachments", false);
    summ.backup_call_records = sharedPreferences.getBoolean("backup_call_records", false);
    summ.delete_after_backup = sharedPreferences.getBoolean("delete_after_backup", false);
    summ.random_question = sharedPreferences.getBoolean("random_question", false);
    summ.email_backup = sharedPreferences.getBoolean("email_backup", false);
    summ.specific_numbers_to_backup_edit = sharedPreferences.getString("specific_numbers_to_backup_edit", "");

    //DO SAFETY STUFF
    summ.delete_after_backup = summ.delete_after_backup && Helper.SAFETY_ALLOW_DELETE_AFTER_BACKUP;
    //specific_numbers_to_backup_edit = Helper.write
    //  (Helper.tokens(specific_numbers_to_backup_edit, " ,'\"", true, true), ",", "'", "'");

    // String specific_numbers_to_backup_edit_where_clause = Helper.write
    //   (Helper.tokens(summ.specific_numbers_to_backup_edit, " ,'\"", true, true), 
    //    " OR ", CallLog.Calls.NUMBER + " like '%", "'");
    // String specific_addresses_to_backup_edit_where_clause = Helper.write
    //   (Helper.tokens(summ.specific_numbers_to_backup_edit, " ,'\"", true, true), 
    //    " OR ", Helper.Cols.ADDRESS + " like '%", "'");


    String specific_numbers_to_backup_edit_where_clause = Helper.write
      (Helper.tokens(summ.specific_numbers_to_backup_edit, " ,'\"", true, true), 
       " OR ", " PHONE_NUMBERS_EQUAL(" + CallLog.Calls.NUMBER + ", '", "', 0)");
    String specific_addresses_to_backup_edit_where_clause = Helper.write
      (Helper.tokens(summ.specific_numbers_to_backup_edit, " ,'\"", true, true), 
       " OR ", " PHONE_NUMBERS_EQUAL(" + Helper.Cols.ADDRESS + ", '", "', 0)");

    // boolean  = sharedPreferences.getBoolean("");

    String backupDestName = "android_conversation_backup__" + DF.format(new Date());
    File tmpdir = new File(appdir, backupDestName);
    if(!tmpdir.mkdirs()) throw new RuntimeException("Unable to create directory for backup files: " + tmpdir);
    
    //initialize result file
    Helper.writeToFile(resultLogFile, false, "", null);
    
    int numsteps = 0;
    if(summ.backup_call_records) numsteps++;
    if(summ.backup_messages) numsteps++;
    if(summ.backup_mms_attachments) numsteps++;
    if(summ.delete_after_backup) {
      if(summ.backup_call_records) numsteps++;
      if(summ.backup_messages) numsteps++;
    }
    if(summ.email_backup) numsteps++;
    numsteps++; //for zipping all files
    int percentIncr = (100 / numsteps) - 1; //reduce slightly, so we're never at 100%
    int percentCompl = 0;
    String callLogsWhereClause = null;
    String messagesWhereClause = null;
    if(summ.backup_call_records) {
      updateProgress(getString(R.string.progress_backup_call_records) + getString(R.string.ellipsis), percentCompl);
      //String whereClause = (backup_all_numbers ? null : 
      //                      (CallLog.Calls.NUMBER + " IN (" + specific_numbers_to_backup_edit + ")"));
      String whereClause = (summ.backup_all_numbers ? null : specific_numbers_to_backup_edit_where_clause);
      callLogsWhereClause = whereClause;
      String[] projection = new String[] {Calls._ID, Calls.TYPE, Calls.CACHED_NAME, 
                                          Calls.NUMBER, Calls.DATE, Calls.DURATION};
      Log.d(TAG, String.format("backup_call_records: whereClause: %s", callLogsWhereClause));
      Cursor cur = getContentResolver().query
        (CallLog.Calls.CONTENT_URI, projection, whereClause, null, Calls.DATE);
      JSONArray jsonarr = new JSONArray();
      if(cur.moveToFirst()) {
        do {
          Cl cl = new Cl();
          cl.id = cur.getLong(cur.getColumnIndex(Calls._ID));
          cl.name = cur.getString(cur.getColumnIndex(Calls.CACHED_NAME));
          cl.duration = cur.getInt(cur.getColumnIndex(Calls.DURATION));
          cl.number = cur.getString(cur.getColumnIndex(Calls.NUMBER));
          cl.timestamp = cur.getLong(cur.getColumnIndex(Calls.DATE));
          int cti = cur.getInt(cur.getColumnIndex(Calls.TYPE));
          if(cti == Calls.INCOMING_TYPE) cl.type = "incoming";
          else if(cti == Calls.OUTGOING_TYPE) cl.type = "outgoing";
          else if(cti == Calls.MISSED_TYPE) cl.type = "missed";
          jsonarr.put(cl.toJSON());
          // JSONObject jobj = new JSONObject();
          // //for(String proj0: projection) jobj.put(proj0, cur.getString(cur.getColumnIndex(proj0)));
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
          // jobj.put("datetime", Helper.SDF.format(new Date(cur.getLong(cur.getColumnIndex(Calls.DATE)))));
          // jsonarr.put(jobj);
          
        } while(cur.moveToNext());
      }
      cur.close();
      Helper.writeJSON("call_logs", jsonarr, tmpdir);
      updateProgress(getString(R.string.progress_backup_call_records) + getString(R.string.done), (percentCompl += percentIncr));
    }
    
    Set<Long> msThreadIds = new HashSet<Long>();
    if(summ.backup_messages) {
      updateProgress(getString(R.string.retrieving_messages) + getString(R.string.ellipsis), percentCompl);
      List<Ms> allms = new ArrayList<Ms>();
      //String whereClause = (backup_all_numbers ? null : 
      //                      (Helper.Cols.ADDRESS + " IN (" + specific_numbers_to_backup_edit + ")"));
      String whereClause = (summ.backup_all_numbers ? null : specific_addresses_to_backup_edit_where_clause);
      messagesWhereClause = whereClause;
      String[] projection = new String[] {Helper.Cols.ADDRESS, Helper.Cols.THREAD_ID};
      Log.d(TAG, String.format("backup_messages: whereClause: %s", messagesWhereClause));
      Cursor cur = getContentResolver().query
        (Helper.Cols.COMPLETE_CONVERSATIONS_CONTENT_URI, projection, whereClause, null, null);
      if(cur.moveToFirst()) {
        do {
          msThreadIds.add(cur.getLong(cur.getColumnIndex(Helper.Cols.THREAD_ID)));
        } while(cur.moveToNext());
      }
      cur.close();
      
      whereClause = Helper.Cols.THREAD_ID + " IN (" + Helper.write(msThreadIds, ", ", "'", "'") + ")";
      projection = new String[] {Helper.Cols.ID, Helper.Cols.THREAD_ID, 
                                 Helper.Cols.NAME, 
                                 Helper.Cols.ADDRESS, Helper.Cols.DATE, 
                                 Helper.Cols.SUBJECT, Helper.Cols.BODY,
                                 Helper.Cols.MMS_MESSAGE_BOX, Helper.Cols.SMS_MESSAGE_BOX, 
                                 Helper.Cols.MMS_TRANSACTION_ID, 
      }; // Helper.Cols.TYPE_DISCRIMINATOR_COLUMN, 
      //try null projection, to see if we get MMS - projection cannot be null
      //projection = null;
      cur = getContentResolver().query
        (Helper.Cols.COMPLETE_CONVERSATIONS_CONTENT_URI, projection, whereClause, null, Helper.Cols.DATE);
      Log.d(TAG, "Column Names: " + Arrays.toString(cur.getColumnNames()));
      if(cur.moveToFirst()) {
        do {
          Ms sms = new Ms();
          sms.id = cur.getLong(cur.getColumnIndex(Helper.Cols.ID));
          sms.timestamp = cur.getLong(cur.getColumnIndex(Helper.Cols.DATE));
          sms.number = cur.getString(cur.getColumnIndex(Helper.Cols.ADDRESS));
          sms.text = cur.getString(cur.getColumnIndex(Helper.Cols.BODY));
          sms.subject = cur.getString(cur.getColumnIndex(Helper.Cols.SUBJECT));
          //sms.mms = cur.getString(cur.getColumnIndex(Helper.Cols.TYPE_DISCRIMINATOR_COLUMN)).equals("mms");
          sms.mms = ((s = cur.getString(cur.getColumnIndex(Helper.Cols.MMS_TRANSACTION_ID))) != null && s.trim().length() > 0);
          String msgboxCol = (sms.mms ? Helper.Cols.MMS_MESSAGE_BOX : Helper.Cols.SMS_MESSAGE_BOX);
          sms.sender = (cur.getInt(cur.getColumnIndex(msgboxCol)) != Helper.Cols.MESSAGE_BOX_INBOX);
          //sms.name = cur.getString(cur.getColumnIndex(Helper.Cols.NAME)); //null; //TBD
          
          Log.d(TAG, String.format("MS Info: Number: %s, Name: %s", sms.number, sms.name));
          if(sms.mms) {
            sms.timestamp = sms.timestamp * 1000; // mms timestamp is in seconds
            Log.d(TAG, "Found mms: #: " + sms.number + ", text: " + sms.text + ", id: " + sms.id);
            //Helper.Cols.ADDR_MSG_ID + " = " + sms.id + " AND " + 
            whereClause = (Helper.Cols.ADDR_TYPE + (sms.sender ? " != " : " = ") + 
                           Helper.Cols.PDU_HEADER_FROM);
            Cursor curpart = getContentResolver().query
              (Uri.parse("content://mms/" + sms.id + "/addr"), 
               new String[] {Helper.Cols.ADDRESS, Helper.Cols.ADDR_MSG_ID, Helper.Cols.ADDR_TYPE},
               whereClause, null, null);
            if(curpart.moveToFirst()) {
              sms.number = curpart.getString(curpart.getColumnIndex(Helper.Cols.ADDRESS));
            }
            curpart.close();
          }
          sms.name = getDisplayName(sms.number);
          
          allms.add(sms);
        } while(cur.moveToNext());
      }
      
      cur.close();
      cur = null;

      updateProgress(getString(R.string.retrieving_messages) + getString(R.string.done), (percentCompl += percentIncr));
      
      // Now get MMS attachments if requested
      if(summ.backup_mms_attachments) {
        updateProgress(getString(R.string.backing_up_mms_attachments) + getString(R.string.ellipsis), percentCompl);
        for(Ms mms: allms) {
          if(mms.mms) {
            Log.d(TAG, "Backing up Attachment for: " + mms.id);
            Cursor curpart = getContentResolver().query
              (Helper.Cols.MMS_PART_CONTENT_URI, 
               new String[] {Helper.Cols.ID, 
                             Helper.Cols.PART_DATA, Helper.Cols.PART_MSG_ID, 
                             Helper.Cols.PART_FILENAME, Helper.Cols.PART_NAME, 
                             Helper.Cols.PART_SEQ, Helper.Cols.PART_TEXT,
                             Helper.Cols.PART_CONTENT_TYPE,
                             Helper.Cols.PART_CONTENT_LOCATION },
               Helper.Cols.PART_MSG_ID + "=" + mms.id,
               null,
               Helper.Cols.PART_SEQ);
            if(curpart.moveToFirst()) {
              ByteArrayOutputStream baos = new ByteArrayOutputStream(256);
              byte[] buffer = new byte[256];
              do {
                MmsEntry mmse = new MmsEntry();
                mmse.text = curpart.getString(curpart.getColumnIndex(Helper.Cols.PART_TEXT));
                //if text matches <smil> ... </smil>, skip it
                if(mmse.text != null && Helper.SMIL_PATTERN.matcher(mmse.text).matches()) continue;
                mms.entries.add(mmse);
                mmse.filename = Helper.toAsciiFilename(curpart.getString(curpart.getColumnIndex(Helper.Cols.PART_FILENAME)));
                Log.d(TAG, "Backing up Attachment for: " + mms.id + " ... " + mmse.filename);
                //Uri partURI = Uri.parse(curpart.getString(curpart.getColumnIndex(Helper.Cols.PART_DATA)));
                String partId = curpart.getString(curpart.getColumnIndex(Helper.Cols.ID));
                String partData = curpart.getString(curpart.getColumnIndex(Helper.Cols.PART_DATA));
                String partLoc = curpart.getString(curpart.getColumnIndex(Helper.Cols.PART_CONTENT_LOCATION));
                Uri partURI = Uri.withAppendedPath(Helper.Cols.MMS_PART_CONTENT_URI, partId);
                Log.d(TAG, "Attachment for message: " + mms.id + ", part: " + partId + ", partUri: " +
                      partURI + ", partData: " + partData + ", partLoc: " + partLoc + ", filename: " +
                      mmse.filename + ", text: " + mmse.text);
                //e.g. smil.xml has no data (so don't even try to find stuff)
                if(partData != null) { 
                  mmse.filename = Helper.toAsciiFilename(partLoc);
                  baos.reset();
                  InputStream is = null;
                  try {
                    is = getContentResolver().openInputStream(partURI);
                    if(is != null) {
                      //byte[] buffer = new byte[256];
                      int len = -1;
                      while((len = is.read(buffer)) >= 0) baos.write(buffer, 0, len);
                    }
                  } finally {
                    baos.flush();
                    Helper.close(is, baos);
                    //mmse.content = baos.toByteArray();
                  }
                  //write attachment to file
                  OutputStream ffos = new FileOutputStream
                    (new File(tmpdir, "mms." + mms.id + "." + mmse.filename));
                  //ffos.write(mmse.content);
                  baos.writeTo(ffos);
                  ffos.flush();
                  Helper.close(ffos);
                }
              } while(curpart.moveToNext());
            }
            curpart.close();
            
            //now check if text is blank and there's any text field, and set value
            if(mms.text == null || mms.text.trim().length() == 0) {
              for(MmsEntry mmsee: mms.entries) {
                if(mmsee.text != null && mmsee.text.trim().length() > 0) {
                  mms.text = mmsee.text;
                  break;
                }
              }
            }
          }
        }
        updateProgress(getString(R.string.backing_up_mms_attachments) + getString(R.string.done), (percentCompl += percentIncr));
      }

      //need to sort again, since the mms dates are all messed up and threw off the initial db sort
      Collections.sort(allms);
      
      JSONArray jsonarr = new JSONArray();
      for(Ms ms: allms) jsonarr.put(ms.toJSON());
      Helper.writeJSON("messages", jsonarr, tmpdir);
      updateProgress(getString(R.string.writing_messages_to_json) + getString(R.string.done), percentCompl);
    }

    //now create summary file
    Helper.writeJSON("summary", summ.toJSON(), tmpdir);
    
    //create assets
    Helper.copyAssets(this, tmpdir, "index.html", "acb_script.js", "acb_style.css");
    
    //now package all into a zip file
    File zipfile = new File(appdir, backupDestName + ".zip");
    updateIntent.putExtra("zipfile", zipfile.getAbsolutePath());
    updateProgress(getString(R.string.creating_zip_file) + getString(R.string.ellipsis), percentCompl);
    File[] filess = tmpdir.listFiles();
    if(filess == null || filess.length == 0) {
      zipfile.createNewFile();
      //FileOutputStream foss = new FileOutputStream(zipfile);
      //foss.flush();
      //foss.close();
    } else {
      ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(zipfile));
      byte[] zbuf = new byte[1024];
      for(File f: filess) {
        FileInputStream fin = new FileInputStream(f);
        zout.putNextEntry(new ZipEntry(f.getName()));
        int flen = -1;
        while((flen = fin.read(zbuf)) != -1) {
          zout.write(zbuf, 0, flen);
        }
        zout.flush();
        zout.closeEntry();
        Helper.close(fin);
      }
      zout.finish();
      zout.flush();
      Helper.close(zout);
    }
    //recursively delete the tmp dir
    if(Helper.SAFETY_ALLOW_DELETE_TMP_DIR) Helper.deleteFile(tmpdir); 
    //skip the things for delete after backup, email backup, etc
    updateProgress(getString(R.string.creating_zip_file) + getString(R.string.done), (percentCompl += percentIncr));

    // emailing is done from the activity
    // if(email_backup) {
    //   updateProgress("emailing zip file ...", percentCompl);
    //   updateProgress("done emailing zip file", (percentCompl += percentIncr));     
    // }
    
    if(summ.delete_after_backup) {
      //delete call logs
      if(summ.backup_call_records) {
        updateProgress(getString(R.string.deleting_call_logs) + getString(R.string.ellipsis), percentCompl);
        getContentResolver().delete(CallLog.Calls.CONTENT_URI, callLogsWhereClause, null);
        updateProgress(getString(R.string.deleting_call_logs) + getString(R.string.done), (percentCompl += percentIncr));
      }
      //delete messages
      if(summ.backup_messages) {
        updateProgress(getString(R.string.deleting_messages) + getString(R.string.ellipsis), percentCompl);
        //we need a diff way to delete the messages TBD
        //getContentResolver().delete(Helper.Cols.COMPLETE_CONVERSATIONS_CONTENT_URI, messagesWhereClause, null);
        //getContentResolver().delete(Helper.Cols.CONVERSATIONS_CONTENT_URI, messagesWhereClause, null);
        getContentResolver().delete
          (Helper.Cols.CONVERSATIONS_CONTENT_URI, 
           Helper.Cols.THREAD_ID + " IN (" + Helper.write(msThreadIds, ", ", "'", "'") + ")", null);
        updateProgress(getString(R.string.deleting_messages) + getString(R.string.done), (percentCompl += percentIncr));
      }
    }

    updateIntent.putExtra("email_backup", summ.email_backup);
    updateProgress(getString(R.string.completed_processing), 100);
  }
  
  @SuppressWarnings("unused")
  private void runMyServiceBS() {
    //use the values here to do the work
    for(int i = 0; i <= 10; i++) {
      updateProgress("doing some work" + " [" + (i * 10) + "%]", i * 10);
    }

  }
  
  private void updateProgress(String message, int percent) {
    String longMsg = message + " (" + percent + "%)";
    //Add longMsg to log file
    Helper.writeToFile(resultLogFile, true, longMsg, "\n");
    
    Log.d(TAG, "ProgressMessage: " + longMsg);
    
    updateIntent.putExtra("message", message);
    updateIntent.putExtra("percent_completed", percent);
    sendBroadcast(updateIntent);   

    if(notificationManager == null) {
      notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
      //notificationIntent = new Intent();
      //notificationIntent = new Intent(this, ResultActivity.class);
      //notificationIntent = new Intent(Helper.SHOW_RESULT_ACTION);
      //notificationContentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);   
      //notificationContentIntent = PendingIntent.getActivity(this, 0, notificationIntent, Intent.FLAG_ACTIVITY_NEW_TASK);   
    }
    // Notification notification = new NotificationBuilder(getApplicationContext())
    //   .setSmallIcon(R.drawable.icon)
    //   .setTicker("Android Conversation Backup ...")
    //   .setWhen(System.currentTimeMillis())
    //   .setContentTitle("Processing")
    //   .setContentText(message)
    //   .setContentIntent(notificationContentIntent)
    //   .getNotification();
    Intent notificationIntent = new Intent(this, ResultActivity.class);
    PendingIntent notificationContentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);   
    Notification notification = new Notification(R.drawable.icon, getString(R.string.app_name), System.currentTimeMillis());
    notification.setLatestEventInfo(getApplicationContext(), getString(R.string.app_name), longMsg, notificationContentIntent);
    notificationManager.notify(Helper.PROCESSING_NOTIFICATION_ID, notification);
    //Helper.doSleep(2000);
  }

  private String getDisplayName(String number) {
    Log.d(TAG, "getDisplayName: " + number);
    if(Helper.SAFETY_RETURN_NULL_FOR_DISPLAY_NAME) return null;
    if(numberToDisplayName.containsKey(number)) return numberToDisplayName.get(number);
    String n = null;
    Uri uri = Uri.withAppendedPath(Helper.Cols.PHONE_LOOKUP_URI, Uri.encode(number));
    Cursor cur = getContentResolver().query(uri, new String[]{Helper.Cols.DISPLAY_NAME}, null, null, null);
    if(cur.moveToFirst()) {
      n = cur.getString(0);
    } 
    cur.close();
    numberToDisplayName.put(number, n);
    Log.d(TAG, String.format("getDisplayName: %s ==> %s", number, n));
    return n;
  }
  
}

