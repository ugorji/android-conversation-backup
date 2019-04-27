package net.ugorji.android.conversationbackup;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.os.Environment;
import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * HomeActivity is the main Screen on this app.
 *
 * It controls the whole flow.
 * All actions happen when the user presses the "Run Backup Button".
 * At that time, we check to see if we can write an archive,
 * and then request for permissions and store them.
 * The permissions dictate what actions the Service does.
 *
 * We also now share data between the service and the HomeActivity and ResultActivity
 * using Intents Extras. This ensures that we do not need to depend on 
 * static fields to share information.
 * 
 * <pre>
 * - when u click: process()
 *  - savePrefs (so buttons can be loaded back)
 *  - set Permissions struct to a blank "unset" one.
 *  - check permissions to write to external media.
 *    - if granted and u can make dir, call process2()
 * - process2()
 *  - check for permissions, and decide what to do
 *    - each permission check should update a field in HomeActivity:
 *      - not set (check for permissions)
 *      - set     (permission defined)
 *      - consequently, use a Boolean object.
 *    - each time we check an 'unset' permission, we re-call process again.
 *    - once all perms set, then we continue to finish process
 *  - create bundle and contain the permissions accepted: sms, mms, calls, delete bools set (1=true)
 *  - update the preferences based on that???
 *  - 
 *  - if none of the required backup perms are defined,
 *    - showDialog and return.
 *  - else 
 *    - call processing service intent, with a Bundle set
 *
 *- ProcessingService
 *  - look at bundle to determine what to do (not preferences)
 *  - recreate Permissions
 *
 * After each call to check for permissions, call process2() again.
 *
 * onRequestPermissionsResult will always now just call: process2()
 * - same requestCode: PROCESSING
 *  - set field in Permissions struct
 *  - call process2()
 * - EXTERNAL_STORAGE
 *  - if granted perm, call process2(),
 *  - else showDialog saying (need perm to write backup to external storage) and return
 *
 * Dialogs:
 * - different messages map to different things
 *  - perms_write_external_media: need perm to write to external media
 *  - perms_no_external_storage: cannot proceed due to no external storage
 *  - error_create_write_directory: cannot proceed as unable to create directory to write archive
 *  -
 *
 * Delete after backup:
 * - delete call records
 * - cannot delete SMS messages
 *
* </pre>
 */
public class HomeActivity extends BaseCBActivity {

  private static class Perms {
    Boolean sms;
    // Boolean mms;
    Boolean calls;
    Boolean contacts;
  }

  private String progressMessage = "";
  private static final String TAG = HomeActivity.class.getSimpleName();

  private static final int
    PROGRESS_DIALOG = 100,
    CONFIRM_DIALOG = 101,
    EULA_DIALOG = 102,
    BACKUP_DONE_DIALOG = 103;
    // NEED_PERMS_WRITE_EXTERNAL_MEDIA_DIALOG = 103,
    // NO_EXTERNAL_MEDIA_DIALOG = 104,
    // ERROR_CREATE_WRITE_DIRECTORY_DIALOG = 105;

  private ProgressDialog progressDialog;
  // private AlertDialog confirmDialog;
  // private AlertDialog eulaDialog;
  // private Intent shareIntent;
  private EditText specNumBackupEditView;
  // private OnClickListener checkboxListener;

  // private File appdir;
  // private File resultLogFile;

  private BroadcastReceiver progressReceiver;
  
  private Perms rperms;
  private Perms wperms;
  
  @Override
  protected void onCreateBaseCallback() {
      if (progressDialog == null) {
        progressDialog = new ProgressDialog(HomeActivity.this);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        // progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        // progressDialog.setIndeterminate(true);
        progressMessage = getString(R.string.progress_message_default);
        progressDialog.setMessage(progressMessage);
        progressDialog.setCancelable(false);
        progressDialog.setIndeterminate(false);
        progressDialog.setButton(DialogInterface.BUTTON_NEUTRAL, getString(R.string.prompt_ok), Helper.DismissDialogOnClick);
      }
      progressReceiver =
          new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
              String mAction = intent.getAction();
              if (mAction != null && mAction.equals(Helper.UPDATE_PROGRESS_ACTION)) {
                updateProgress(intent);
              }
            }
          };
    if (Helper.SAFETY_DEV_MODE) {
      Helper.debugAllContentResolvers(this);
      Helper.getPreferences(this)
          .edit()
          .putString(Helper.PREFERENCE_EULA_ACCEPTED, BuildConfig.VERSION_NAME)
          .commit();
    }
    SharedPreferences sharedPreferences = Helper.getPreferences(this);
    if (!sharedPreferences
        .getString(Helper.PREFERENCE_EULA_ACCEPTED, "-")
        .equals(BuildConfig.VERSION_NAME)) {
      showDialog(EULA_DIALOG);
    }
    setContentView(R.layout.main);
    
    specNumBackupEditView = (EditText) findViewById(R.id.specific_numbers_to_backup_edit);
    aboutAppButton = (Button) findViewById(R.id.about_app);
    exitAppButton = (Button) findViewById(R.id.exit_app);
    archivesButton = (Button) findViewById(R.id.archives);
    Button processButton = (Button) findViewById(R.id.confirm);
    processButton.setOnClickListener(view -> showDialog(CONFIRM_DIALOG));
    Button selectContactButton = (Button) findViewById(R.id.select_contact);
    selectContactButton.setOnClickListener(view -> {
        Intent intent = new Intent(Intent.ACTION_PICK, Contacts.CONTENT_URI);
        startActivityForResult(intent, Helper.SELECT_CONTACT_REQUEST);
      });

    resetState(sharedPreferences, R.id.backup_all_numbers, "backup_all_numbers", false);
    resetState(sharedPreferences, R.id.backup_messages, "backup_messages", true);
    resetState(sharedPreferences, R.id.backup_mms_attachments, "backup_mms_attachments", true);
    resetState(sharedPreferences, R.id.backup_call_records, "backup_call_records", true);
    resetState(sharedPreferences, R.id.delete_after_backup, "delete_after_backup", false);
    resetState(sharedPreferences, R.id.share_archive, "share_archive", true);
    resetState(sharedPreferences, R.id.random_question, "random_question", true);
    resetState(
        sharedPreferences,
        R.id.specific_numbers_to_backup_edit,
        "specific_numbers_to_backup_edit",
        false);

    final CheckBox backupAllCB = (CheckBox) findViewById(R.id.backup_all_numbers);
    final EditText specNumBackupEditView2 = specNumBackupEditView;
    final Button selectContactButton2 = selectContactButton;
    specNumBackupEditView.setEnabled(!backupAllCB.isChecked());
    selectContactButton.setEnabled(!backupAllCB.isChecked());
    backupAllCB.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            specNumBackupEditView2.setEnabled(!backupAllCB.isChecked());
            selectContactButton2.setEnabled(!backupAllCB.isChecked());
          }
        });

      registerReceiver(progressReceiver, Helper.PROGRESS_INTENT_FILTER);
  }

  // public void onSaveInstanceState(Bundle savedInstanceState) {
  //   saveState(savedInstanceState, R.id.backup_all_numbers, "backup_all_numbers");
  //   super.onSaveInstanceState(savedInstanceState);
  // }

  private Dialog permsOkDialog(String message) {
    AlertDialog.Builder builder =
      new AlertDialog.Builder(this)
      .setTitle(R.string.perms_title)
      .setMessage(message)
      .setCancelable(false)
      .setNeutralButton(getString(R.string.prompt_yes), Helper.DismissDialogOnClick);
    Dialog dialog = builder.create();
    return dialog;
  }
  
  @Override
  protected Dialog onCreateDialog(int id) {
    Dialog dialog = super.onCreateDialog(id);
    if (dialog != null) return dialog;
    AlertDialog.Builder builder;
    switch (id) {
    case EULA_DIALOG:
        Log.d(TAG, "Eula");
        String eulaMsg = null;
        try {
          eulaMsg = Helper.read(new InputStreamReader(getAssets().open(Helper.ASSET_EULA)), true);
        } catch (Exception e) {
          Log.e(TAG, "Error loading eula", e);
          throw new RuntimeException(e);
        }
        final SharedPreferences sharedPreferences = Helper.getPreferences(HomeActivity.this);
        builder =
            new AlertDialog.Builder(this)
                .setTitle(R.string.eula_title)
                .setMessage(eulaMsg)
                .setCancelable(false)
                .setPositiveButton(getString(R.string.eula_accept),
                                   (dialog1, id1) -> sharedPreferences.edit()
                                   .putString(Helper.PREFERENCE_EULA_ACCEPTED, BuildConfig.VERSION_NAME).commit())
                .setNegativeButton(getString(R.string.eula_refuse),
                                   (dialog1, id1) -> {
                                     dialog1.cancel();
                                     HomeActivity.this.finish();
                                   });
        dialog = builder.create();
        break;
    case CONFIRM_DIALOG:
        builder =
            new AlertDialog.Builder(this)
                .setMessage(getString(R.string.start_processing_prompt_message))
                .setCancelable(false)
                .setPositiveButton(
                    getString(R.string.prompt_yes),
                    new DialogInterface.OnClickListener() {
                      @Override
                      public void onClick(DialogInterface dialog1, int id1) {
                        HomeActivity.this.process();
                        // HomeActivity.this.finish();
                      }
                    })
                .setNegativeButton(
                    getString(R.string.prompt_no),
                    Helper.CancelDialogOnClick);
        dialog = builder.create();
        break;
    case BACKUP_DONE_DIALOG:
        builder =
            new AlertDialog.Builder(this)
          .setMessage(getString(R.string.completed_processing));
        dialog = builder.create();
    case PROGRESS_DIALOG:
        // duh!!! U can't call updateProgress from here
        // updateProgress("Processing Conversation backup", null, 0, false);
      if (!progressDialog.isShowing()) progressDialog.show();
        dialog = progressDialog;
        break;
      default:
        dialog = null;
    }
    Log.d(TAG, "For id: " + id + ", returning dialog: " + dialog);
    return dialog;
  }

  @Override
  protected void onPrepareDialog(int id, Dialog dialog) {
    switch (id) {
      case PROGRESS_DIALOG:
        // progressMessage = getString(R.string.progress_message_default);
        // progressDialog.setMessage(progressMessage);
        progressDialog.setProgress(0);
        break;
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    // Log.d(TAG, "onActivityResult: requestCode: " + requestCode + ", resultCode: " + resultCode);
    // Log.d(TAG, "onActivityResult: RESULT_OK: " + RESULT_OK);
    if (requestCode == Helper.SEND_ARCHIVE_REQUEST) {
      String longMsg =
          (resultCode == RESULT_OK
              ? getString(R.string.archive_shared_success)
              : getString(R.string.archive_shared_fail));
      // For some reason, when sending using email (gmail), we always got RESULT_CANCELLED
      // even when the email was successfully sent.
      // see https://stackoverflow.com/questions/17102578/detect-email-sent-or-not-in-onactivity-result
      // So don't be specific
      longMsg = getString(R.string.archive_shared);
      updateProgress(longMsg, null, 100, false);
      // processingDone(longMsg);
    } else if (requestCode == Helper.SELECT_CONTACT_REQUEST) {
      if (resultCode == RESULT_OK) addSelectedContactNumbers(data);
    }
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    boolean status = true;
    String perm = null;
    for(int i = 0; i < permissions.length; i++) {
      perm = permissions[i];
      if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
        status = false;
      }
    }
    switch (requestCode) {
    case Helper.PERMISSION_PROCESSING:
      switch(perm) {
      case Manifest.permission.READ_SMS:
        rperms.sms = status;
        // rperms.mms = status;
        break;
      case Manifest.permission.READ_CALL_LOG:
        rperms.calls = status;
        break;
      case Manifest.permission.READ_PHONE_STATE:
        break;
      case Manifest.permission.READ_CONTACTS:
        rperms.contacts = status;
        break;
      // case Manifest.permission.WRITE_SMS:
      //   wperms.sms = status;
      //   break;
      case Manifest.permission.WRITE_CONTACTS:
        wperms.contacts = status;
        break;
      case Manifest.permission.WRITE_CALL_LOG:
        wperms.calls = status;
        break;
      }
      process3();
      break;
    }
  }

  private void addSelectedContactNumbers(Intent data) {
    Log.d(TAG, "addSelectedContactNumbers");
    StringBuilder sb = new StringBuilder();
    Set<String> phNums = new LinkedHashSet<String>();
    String s = null;
    Uri contactData = data.getData();
    Cursor c = getContentResolver().query(contactData, null, null, null, null);
    String contactId = null;
    if (c.moveToFirst()) contactId = c.getString(c.getColumnIndex(ContactsContract.Contacts._ID));
    c.close();
    c =
        getContentResolver()
            .query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = " + contactId,
                null,
                null);
    if (c.moveToFirst()) {
      do {
        s = c.getString(c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
        // canonicalize it
        sb.setLength(0);
        for (int i = 0; i < s.length(); i++) {
          char ch = s.charAt(i);
          if (ch >= '0' && ch <= '9') sb.append(ch);
        }
        phNums.add(sb.toString());
      } while (c.moveToNext());
    }
    c.close();
    Log.d(
        TAG,
        String.format("addSelectedContactNumbers: contactId: %s, numbers: %s", contactId, phNums));
    specNumBackupEditView.setText(
        specNumBackupEditView.getText() + " " + Helper.write(phNums, " ", "", ""));
  }

  protected void updateProgress(Intent intent) {
    Bundle extras = intent.getExtras();
    updateProgress(
        extras.getString("message"),
        extras.getString("zipfile"),
        extras.getInt("percent_completed"),
        extras.getBoolean("share_archive"));
  }

  @Override
  public void onPause() {
    super.onPause();
    if (progressDialog != null && progressDialog.isShowing()) progressDialog.dismiss();
    if (progressReceiver != null) unregisterReceiver(progressReceiver);
  }

  @Override
  public void onResume() {
    super.onResume();
    registerReceiver(progressReceiver, Helper.PROGRESS_INTENT_FILTER);
  }

  private void updateProgress(String message, String zipfile, int completed, boolean share_archive) {
    // if (progressDialog == null) showDialog(PROGRESS_DIALOG);
    showDialog(PROGRESS_DIALOG);
    progressMessage = progressMessage + "\n" + message;
    progressDialog.setMessage(progressMessage);
    if (completed >= 0) progressDialog.setProgress(Math.min(100, completed));
    if (completed >= 100) {
      // if (progressDialog.isShowing()) progressDialog.dismiss();
      if (share_archive) {
        if (progressDialog.isShowing()) progressDialog.dismiss();
        // Uri uri = Uri.fromFile(new File(zipfile)); //Uri.parse("file://"+ zipfile));
        Uri uri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".provider", new File(zipfile));
        startActivityForResult(Intent.createChooser(getShareIntent(uri), getString(R.string.share_archive_message)),
                               Helper.SEND_ARCHIVE_REQUEST);
      // } else {
      //   processingDone(null);
      }
    } else if (completed >= 0) {
      if (!progressDialog.isShowing()) progressDialog.show();
    }
  }

  private void process() {
    progressMessage = getString(R.string.progress_message_default);
    progressDialog.setMessage(progressMessage);
        
    // save preferences first (since service needs it)
    SharedPreferences sharedPreferences = Helper.getPreferences(this);
    // getSharedPreferences(Helper.SHARED_PREFERENCES_KEY, MODE_PRIVATE);
    SharedPreferences.Editor editor = sharedPreferences.edit();
    savePrefs(editor, R.id.backup_all_numbers, "backup_all_numbers");
    savePrefs(editor, R.id.backup_messages, "backup_messages");
    savePrefs(editor, R.id.backup_mms_attachments, "backup_mms_attachments");
    savePrefs(editor, R.id.backup_call_records, "backup_call_records");
    savePrefs(editor, R.id.delete_after_backup, "delete_after_backup");
    savePrefs(editor, R.id.share_archive, "share_archive");
    savePrefs(editor, R.id.random_question, "random_question");
    savePrefs(editor, R.id.specific_numbers_to_backup_edit, "specific_numbers_to_backup_edit");
    editor.commit();

    rperms = new Perms();
    wperms = new Perms();
    
    process3();
  }
  
  private Boolean checkPerm(Boolean b, String perm) {
    if(b != null) return b;
    if (ActivityCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) {
      return Boolean.TRUE;
    }
    ActivityCompat.requestPermissions(this, new String[]{perm}, Helper.PERMISSION_PROCESSING);
    return null; // this causes process3 to exit, and callback can call it again.
  }
  
  private void process3() {
    // if any perm == null, then return here (for callback to call again)
    // only check perms iff user requested that action.
    Helper.Summary summ = Helper.getSummary(Helper.getPreferences(this));
    if(summ.backup_messages || summ.backup_mms_attachments) {
      if((rperms.sms = checkPerm(rperms.sms, Manifest.permission.READ_SMS)) == null) return;
    }
    if(summ.backup_call_records) {
      if((rperms.calls = checkPerm(rperms.calls, Manifest.permission.READ_CALL_LOG)) == null) return;
      if((rperms.contacts = checkPerm(rperms.contacts, Manifest.permission.READ_CONTACTS)) == null) return;
    }
    //checkPerm(rperms., Manifest.permission.);
    if(summ.delete_after_backup) {
      // if((wperms.sms = checkPerm(wperms.sms, Manifest.permission.WRITE_SMS)) == null) return;
      if((wperms.calls = checkPerm(wperms.calls, Manifest.permission.WRITE_CALL_LOG)) == null) return;
      // if((wperms.contacts = checkPerm(wperms.contacts, Manifest.permission.WRITE_CONTACTS)) == null) return;
    }
    // TODO: check: call processIt if something to do, or show dialog saying "nothing to do"
    processIt();
  }

  private boolean b(Boolean bb) {
    if(bb == null) return false;
    return bb.booleanValue();
  }
  
  private void processIt() {
    Intent intent = new Intent(this, ProcessingService.class);
    boolean[] args = new boolean[]{
      b(rperms.sms), b(rperms.calls), b(rperms.contacts), false,
      b(wperms.sms), b(wperms.calls), b(wperms.contacts), false,
    };
    intent.putExtra(BuildConfig.APPLICATION_ID + ".perms", args);
    // intent.putExtra(BuildConfig.APPLICATION_ID + ".appdir", appdir.getAbsolutePath());
    // intent.putExtra(BuildConfig.APPLICATION_ID + ".log", resultLogFile.getAbsolutePath());
    
    startService(intent);
    showDialog(PROGRESS_DIALOG);
    // service broadcasts extra info in the Intent
  }

  private void resetState(SharedPreferences prefs, int id, String key, boolean defValue) {
    Object o = findViewById(id);
    if (o != null && prefs != null) {
      if (o instanceof CheckBox) ((CheckBox) o).setChecked(prefs.getBoolean(key, defValue));
      else if (o instanceof EditText) ((EditText) o).setText(prefs.getString(key, ""));
    }
  }

  private void savePrefs(SharedPreferences.Editor editor, int id, String key) {
    Object o = findViewById(id);
    if (o != null && editor != null) {
      if (o instanceof CheckBox) editor.putBoolean(key, ((CheckBox) o).isChecked());
      else if (o instanceof EditText) editor.putString(key, ((EditText) o).getText().toString());
    }
  }

  private Intent getShareIntent(Uri uri) {
    Intent shareIntent = new Intent(Intent.ACTION_SEND);
    // shareIntent.setType("message/rfc822");
    shareIntent.setType("application/zip");
    // shareIntent.putExtra(Intent.EXTRA_EMAIL, new String[] {"blah@blah.com"});
    shareIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.attached_message));
    shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.attached_subject));
    shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
    return shareIntent;
  }
}
