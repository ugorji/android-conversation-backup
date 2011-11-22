package net.ugorji.android.conversationbackup;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

public abstract class BaseCBActivity extends Activity {
  private static final String TAG = BaseCBActivity.class.getSimpleName();
  protected BroadcastReceiver progressReceiver;
  protected static final int 
    FATAL_DIALOG = 1;
  
  protected AlertDialog fatalDialog;
  protected String fatalMessage = "";
  protected Button aboutAppButton;
  protected Button exitAppButton;
  protected Button homeButton;
  
  @Override
  protected void onNewIntent(Intent intent) {
    Log.d(TAG, "Calling onNewIntent");
    if(intent.getBooleanExtra(Helper.EXIT_ACTION, false)) finish();
  }
  
  @Override
  protected Dialog onCreateDialog(int id) {
    Dialog dialog = null;
    AlertDialog.Builder builder = null;
    switch(id) {
    case FATAL_DIALOG:
      builder = new AlertDialog.Builder(this)
        .setMessage("FATAL: " + fatalMessage)
        .setCancelable(false)
        .setPositiveButton(getString(R.string.prompt_yes), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
              BaseCBActivity.this.finish();
            }
          });
      fatalDialog = builder.create();
      dialog = fatalDialog;
      break;
    }
    return dialog;
  }
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    try {
      Helper.init();
      progressReceiver = new BroadcastReceiver() {
          @Override
          public void onReceive(Context context, Intent intent) {
            String mAction = intent.getAction();
            if(mAction.equals(Helper.UPDATE_PROGRESS_ACTION)) {
              updateProgress(intent);
            }
          }
        };
      onCreateBaseCallback();
      if(homeButton != null) {
        homeButton.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View view) {
            startActivity(new Intent(BaseCBActivity.this, HomeActivity.class));
          }
        });
      }
      aboutAppButton.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View view) {
            showAboutApp(TAG);
          }
        });
      exitAppButton.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View view) {
            finish();
            NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            nm.cancel(Helper.PROCESSING_NOTIFICATION_ID);
            //Intent exi = new Intent(ResultActivity.this, HomeActivity.class);
            //exi.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            //exi.putExtra(Helper.EXIT_ACTION, true);
            //startActivity(exi);
          }
        });
      registerReceiver(progressReceiver, Helper.PROGRESS_INTENT_FILTER);
    } catch(Exception exc) {
      //show exception in error dialog (which calls finish when done)
      handleFatalMessage(exc.getMessage());
    }
  }
  
  @Override
  public void onResume() {
    super.onResume();
    registerReceiver(progressReceiver, Helper.PROGRESS_INTENT_FILTER);
  }
  
  @Override
  public void onPause() {
    super.onPause();
    if(progressReceiver != null) unregisterReceiver(progressReceiver);
  }
  
  protected abstract void onCreateBaseCallback();
  protected abstract void updateProgress(Intent intent);

  protected void handleFatalMessage(String message) {
    fatalMessage = message;
    if(fatalDialog != null) fatalDialog.setMessage("FATAL: " + fatalMessage);
    showDialog(FATAL_DIALOG);
  }
  
  protected void showAboutApp(String logtag) {
    Log.d(logtag, "showing about app in market");
    Intent intent = new Intent(Intent.ACTION_VIEW);
    intent.setData(Uri.parse("market://details?id=" + Helper.class.getPackage().getName()));
    startActivity(intent);
  }
  
}
