package net.ugorji.android.conversationbackup;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;

public abstract class BaseCBActivity extends Activity {
  private static final String TAG = BaseCBActivity.class.getSimpleName();
  protected static final int FATAL_DIALOG = 1;

  protected AlertDialog fatalDialog;
  protected String fatalMessage = "";
  protected Button aboutAppButton;
  protected Button exitAppButton;
  protected Button homeButton;
  protected Button archivesButton;
  
  @Override
  protected void onNewIntent(Intent intent) {
    if (intent.getBooleanExtra(Helper.EXIT_ACTION, false)) finish();
  }

  @Override
  protected Dialog onCreateDialog(int id) {
    Dialog dialog = null;
    AlertDialog.Builder builder = null;
    switch (id) {
      case FATAL_DIALOG:
        builder =
            new AlertDialog.Builder(this)
                .setMessage("FATAL: " + fatalMessage)
                .setCancelable(false)
                .setNeutralButton(
                    getString(R.string.prompt_yes),
                    new DialogInterface.OnClickListener() {
                      @Override
                      public void onClick(DialogInterface dialog_, int id_) {
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
      onCreateBaseCallback();
      
      if (homeButton != null) {
        homeButton.setOnClickListener(
            new View.OnClickListener() {
              @Override
              public void onClick(View view) {
                startActivity(new Intent(BaseCBActivity.this, HomeActivity.class));
              }
            });
      }
      if (aboutAppButton != null) {
        aboutAppButton.setOnClickListener(view -> showAboutApp(TAG));
      }
      if (exitAppButton != null) {
        exitAppButton.setOnClickListener(view -> {
              finish();
              NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
              nm.cancel(Helper.PROCESSING_NOTIFICATION_ID);
              // Intent exi = new Intent(ResultActivity.this, HomeActivity.class);
              // exi.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
              // exi.putExtra(Helper.EXIT_ACTION, true);
              // startActivity(exi);
          });
      }
      if (archivesButton != null) {
        archivesButton.setOnClickListener(view -> startActivity(new Intent(BaseCBActivity.this, ArchivesActivity.class)));
      }
    } catch (Exception exc) {
      // show exception in error dialog (which calls finish when done)
      handleFatalMessage(exc.getMessage());
    }
  }

  protected abstract void onCreateBaseCallback();

  protected void handleFatalMessage(String message) {
    fatalMessage = message;
    if (fatalDialog != null) fatalDialog.setMessage("FATAL: " + fatalMessage);
    showDialog(FATAL_DIALOG);
  }

  protected void showAboutApp(String logtag) {
    Intent intent = new Intent(Intent.ACTION_VIEW);
    intent.setData(Uri.parse("market://details?id=" + Helper.class.getPackage().getName()));
    startActivity(intent);
  }
}
