package net.ugorji.android.conversationbackup;

import java.io.InputStreamReader;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;

public class HomeActivity extends BaseCBActivity {
 
  private static final String TAG = HomeActivity.class.getSimpleName();
  
  private static final int 
    PROGRESS_DIALOG = 100,
    CONFIRM_DIALOG = 101,
    EULA_DIALOG = 102;
  
  private Button processButton;
  private ProgressDialog progressDialog;
  private AlertDialog confirmDialog;
  private AlertDialog eulaDialog;
  private Intent emailIntent;
  
  // private OnClickListener checkboxListener;
  
  protected void onCreateBaseCallback() {
    if(Helper.SAFETY_DEV_MODE) {
      Helper.debugAllContentResolvers(this);
      Helper.getPreferences(this).edit().putString(Helper.PREFERENCE_EULA_ACCEPTED, "-").commit();
    }
    SharedPreferences sharedPreferences = Helper.getPreferences(this);
    if(!sharedPreferences.getString(Helper.PREFERENCE_EULA_ACCEPTED, "-").equals(Helper.VERSION)) {
      showDialog(EULA_DIALOG);
    }
    setContentView(R.layout.main);
    aboutAppButton = (Button)findViewById(R.id.about_app);
    exitAppButton = (Button)findViewById(R.id.exit_app);    
    processButton = (Button)findViewById(R.id.confirm);
    processButton.setOnClickListener(new View.OnClickListener() {
        public void onClick(View view) {
          Log.d(TAG, "process button clicked");
          showDialog(CONFIRM_DIALOG);
        }
      });
    
    // checkboxListener = new OnClickListener() {
    //     public void onClick(View v) {
    //     }
    //   };
    
    resetState(sharedPreferences, R.id.backup_all_numbers, "backup_all_numbers", false);
    resetState(sharedPreferences, R.id.backup_messages, "backup_messages", true);
    resetState(sharedPreferences, R.id.backup_mms_attachments, "backup_mms_attachments", true);
    resetState(sharedPreferences, R.id.backup_call_records, "backup_call_records", true);
    resetState(sharedPreferences, R.id.delete_after_backup, "delete_after_backup", false);
    resetState(sharedPreferences, R.id.email_backup, "email_backup", true);
    resetState(sharedPreferences, R.id.random_question, "random_question", true);
    resetState(sharedPreferences, R.id.specific_numbers_to_backup_edit, "specific_numbers_to_backup_edit", false);
    
    final CheckBox backupAllCB = (CheckBox)findViewById(R.id.backup_all_numbers);
    final EditText specNumBackupEditView = (EditText)findViewById(R.id.specific_numbers_to_backup_edit);
    specNumBackupEditView.setEnabled(!backupAllCB.isChecked());
    backupAllCB.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          specNumBackupEditView.setEnabled(!backupAllCB.isChecked());
        }
      });            
    
    emailIntent = new Intent(android.content.Intent.ACTION_SEND); 
    emailIntent.setType("message/rfc822");
    //emailIntent.setType("application/zip");
    if(Helper.SAFETY_DEV_EMAIL_ADDRESS != null) emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, new String[] {Helper.SAFETY_DEV_EMAIL_ADDRESS}); 
    emailIntent.putExtra(android.content.Intent.EXTRA_TEXT, getString(R.string.attached_message)); 
    emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, getString(R.string.attached_subject)); 
  }
  
  // public void onSaveInstanceState(Bundle savedInstanceState) { 
  //   saveState(savedInstanceState, R.id.backup_all_numbers, "backup_all_numbers");
  //   super.onSaveInstanceState(savedInstanceState);
  // }

  protected Dialog onCreateDialog(int id) {
    Dialog dialog = super.onCreateDialog(id);
    if(dialog != null) return dialog;
    Log.d(TAG, "onCreateDialog");
    AlertDialog.Builder builder = null;
    switch(id) {
    case EULA_DIALOG:
      Log.d(TAG, "Eula");
      String eulaMsg = null; 
      try { 
        eulaMsg = Helper.read(new InputStreamReader(getAssets().open(Helper.ASSET_EULA)), true); 
      } catch(Exception e) {
        Log.e(TAG, "Error loading eula", e);
        throw new RuntimeException(e);
      }
      final SharedPreferences sharedPreferences = Helper.getPreferences(HomeActivity.this);
      builder = new AlertDialog.Builder(this)
        .setTitle(R.string.eula_title)
        .setMessage(eulaMsg)
        .setCancelable(false)
        .setPositiveButton(getString(R.string.eula_accept), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
              sharedPreferences.edit().putString(Helper.PREFERENCE_EULA_ACCEPTED, Helper.VERSION).commit();
            }
          })
        .setNegativeButton(getString(R.string.eula_refuse), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
              dialog.cancel();
              HomeActivity.this.finish();
            }
          });
      Log.d(TAG, "Builder done");
      eulaDialog = builder.create();
      Log.d(TAG, "Builder dialog created done");
      dialog = eulaDialog;
      break;
    case CONFIRM_DIALOG:
      Log.d(TAG, "Builder about to create");
      builder = new AlertDialog.Builder(this)
        .setMessage(getString(R.string.start_processing_prompt_message))
        .setCancelable(false)
        .setPositiveButton(getString(R.string.prompt_yes), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
              HomeActivity.this.process();
              //HomeActivity.this.finish();
            }
          })
        .setNegativeButton(getString(R.string.prompt_no), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
              dialog.cancel();
            }
          });
      Log.d(TAG, "Builder done");
      confirmDialog = builder.create();
      Log.d(TAG, "Builder dialog created done");
      dialog = confirmDialog;
      break;
    case PROGRESS_DIALOG:
      //duh!!! U can't call updateProgress from here
      //updateProgress("Processing Conversation backup", null, 0, false);
      progressDialog = new ProgressDialog(HomeActivity.this);
      progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
      //progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
      //progressDialog.setIndeterminate(true);
      progressDialog.setMessage(getString(R.string.progress_message_default));
      dialog = progressDialog;
      break;
    default:
      dialog =  null;
    }
    Log.d(TAG, "For id: " + id + ", returning dialog: " + dialog);
    return dialog;
  }

  protected void onPrepareDialog(int id, Dialog dialog) {
    switch(id) {
    case PROGRESS_DIALOG:
      progressDialog.setMessage("");
      progressDialog.setProgress(0);
      break;
    }
  }
  
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    Log.d(TAG, "onActivityResult: requestCode: " + requestCode + ", resultCode: " + resultCode);
    Log.d(TAG, "onActivityResult: RESULT_OK: " + RESULT_OK);
    if(requestCode == Helper.SEND_ARCHIVE_REQUEST) {
      String longMsg = (resultCode == RESULT_OK ?
                        getString(R.string.email_sent_success) :
                        getString(R.string.email_sent_fail));
      //for some reason, we always got RESULT_CANCELLED even when the email was successfully sent
      //so don't be specific TBD
      longMsg = getString(R.string.email_sent);
      processingDone(longMsg);
    }
  }
  
  private void processingDone(String longMsg) {
    if(longMsg == null) longMsg = getString(R.string.email_sent);
    updateProgress(longMsg, null, -1, false);
    //TBD: Preferably Use a broadcast, so everyone can get this.
    Helper.writeToFile(Helper.RESULT_LOG_FILE, true, longMsg, "\n");
    //show result activity
    startActivity(new Intent(this, ResultActivity.class));
  }
  
  protected void updateProgress(Intent intent) {
    Bundle extras = intent.getExtras(); 
    updateProgress(extras.getString("message"), 
                   extras.getString("zipfile"), 
                   extras.getInt("percent_completed"),
                   extras.getBoolean("email_backup"));
    
  }
  
  public void onPause() {
    super.onPause();
    if(progressDialog != null && progressDialog.isShowing()) progressDialog.dismiss();
  }
  
  private void updateProgress(String message, String zipfile, int completed, boolean email_backup) {
    if(progressDialog == null) showDialog(PROGRESS_DIALOG);
    progressDialog.setMessage(message);
    if(completed >= 0) progressDialog.setProgress(Math.min(100, completed));
    if(completed >= 100) {
      if(progressDialog.isShowing()) progressDialog.dismiss();
      if(email_backup) {
        //call intent to send email
        emailIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://"+ zipfile));
        startActivityForResult
          (Intent.createChooser(emailIntent, getString(R.string.email_chooser_message)), 
           Helper.SEND_ARCHIVE_REQUEST);
      } else {
        processingDone(null);
      }
    } else if(completed >= 0) {
      if(!progressDialog.isShowing()) progressDialog.show();
    } 
  }
  
  //TBD
  private void process() {
    //save preferences first (since service needs it)
    SharedPreferences sharedPreferences = Helper.getPreferences(this);
    //getSharedPreferences(Helper.SHARED_PREFERENCES_KEY, MODE_PRIVATE);
    SharedPreferences.Editor editor = sharedPreferences.edit();
    savePrefs(editor, R.id.backup_all_numbers, "backup_all_numbers");
    savePrefs(editor, R.id.backup_messages, "backup_messages");
    savePrefs(editor, R.id.backup_mms_attachments, "backup_mms_attachments");
    savePrefs(editor, R.id.backup_call_records, "backup_call_records");
    savePrefs(editor, R.id.delete_after_backup, "delete_after_backup");
    savePrefs(editor, R.id.email_backup, "email_backup");
    savePrefs(editor, R.id.random_question, "random_question");
    savePrefs(editor, R.id.specific_numbers_to_backup_edit, "specific_numbers_to_backup_edit");
    editor.commit();
    
    Intent intent = new Intent(this, ProcessingService.class);
    startService(intent);
    showDialog(PROGRESS_DIALOG);
    //service broadcasts extra info in the Intent
  }
  
  private void resetState(SharedPreferences prefs, int id, String key, boolean defValue) {
    Object o = findViewById(id); 
    if(o != null && prefs != null) {
      if(o instanceof CheckBox) ((CheckBox)o).setChecked(prefs.getBoolean(key, defValue));
      else if(o instanceof EditText) ((EditText)o).setText(prefs.getString(key, ""));
    }
  }
  
  private void savePrefs(SharedPreferences.Editor editor, int id, String key) {
    Object o = findViewById(id);
    if(o != null && editor != null) {
      if(o instanceof CheckBox) editor.putBoolean(key, ((CheckBox)o).isChecked());
      else if(o instanceof EditText) editor.putString(key, ((EditText)o).getText().toString());
    } 
  }

}

