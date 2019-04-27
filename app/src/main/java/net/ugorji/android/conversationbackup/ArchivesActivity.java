package net.ugorji.android.conversationbackup;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ArrayAdapter;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.Formatter;

public class ArchivesActivity extends BaseCBActivity {
  private static final String TAG = ArchivesActivity.class.getSimpleName();
  // private  static final int CONFIRM_DELETE_DIALOG = 201;
  
  private StringBuilder fmtstr = new StringBuilder();
  private Formatter fmt = new Formatter(fmtstr);

  private ListView listView;

  private Helper.MyDialogFrag dfDelete = new Helper.MyDialogFrag();
  
  private class ListEntity {
    private String name;
    private Date date;
    private long size;

    ListEntity(String name, Date date, long size) {
      this.name = name;
      this.date = date;
      this.size= size;
    }
    
    public String toString() {
      fmtstr.delete(0, fmtstr.length());
      fmt.format("%1$s%n%2$tY-%2$tm-%2$td %2$tH:%2$tM:%2$tS%n%3$.2fMB", name, date, size*1.0/1000/1000);
      return fmtstr.toString();
        // return name + "\n" + date + "\n" + SDF.format(date) + "\n" + ;
    }
  }

  private ArrayAdapter<ListEntity> listAdapter;
  
  @Override
  protected void onCreateBaseCallback() {
    dfDelete.tag = "delete";
    dfDelete.msgId = R.string.delete_selected_archives_question;
    dfDelete.actionMsgId = R.string.prompt_yes;
    dfDelete.cancelId = R.string.prompt_no;
    dfDelete.setMyAction((dialog1, id1) -> ArchivesActivity.this.delete());
         
    setContentView(R.layout.archives);
    exitAppButton = (Button) findViewById(R.id.archives_exit_app);
    // updateText();
    Button shareButton = (Button)findViewById(R.id.archives_share);
    shareButton.setOnClickListener(view -> ArchivesActivity.this.share());
    Button deleteButton = (Button)findViewById(R.id.archives_delete);
    deleteButton.setOnClickListener(view -> {if(listView.getCheckedItemCount() > 0) showDialog(dfDelete);});

    listAdapter = new ArrayAdapter<ListEntity>(this, R.layout.archives_list_item);
    loadListAdapter();

    listView = (ListView)findViewById(R.id.archives_list);
    listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
    listView.setAdapter(listAdapter);
  }

  @Override
  public void onResume() {
    super.onResume();
    loadListAdapter();
    listView.clearChoices();
  }

  private ArrayList<ListEntity> entities() {
    ArrayList<ListEntity> r = new ArrayList<ListEntity>(listView.getCheckedItemCount());
    SparseBooleanArray a = listView.getCheckedItemPositions();
    for(int i = 0; i < a.size(); i++) {
      if (a.valueAt(i)) r.add((ListEntity)listView.getItemAtPosition(a.keyAt(i)));
    }
    return r;
  }

  private void logg(String action) {
    SparseBooleanArray a = listView.getCheckedItemPositions();
    Log.d(TAG, "calling " + action +
            " on checked items of count: " + listView.getCheckedItemCount() +
            ", and item positions of size: " + a.size());
    for(int i = 0; i < a.size(); i++) {
      int j = a.keyAt(i);
      if (a.valueAt(i)) Log.d(TAG, "item: " + j + ": " + listView.getItemAtPosition(j));
    }
  }
  
  private void share() {
    // logg("share");
    int num = listView.getCheckedItemCount();
    if(num <= 0) return;
    ArrayList<Uri> uris = new ArrayList<Uri>(num);
    for(ListEntity e: entities()) {
      Uri uri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".provider", new File(getFilesDir(), e.name));
      uris.add(uri);
    }
                                           
    Intent shareIntent = new Intent();
    shareIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
    shareIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.attached_message));
    shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.attached_subject));
    shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
    shareIntent.setType("application/zip");
    startActivity(Intent.createChooser(shareIntent, getString(R.string.share_archive_message)));
  }

  private void delete() {
    // logg("delete");
    int num = listView.getCheckedItemCount();
    if(num <= 0) return;
    for(ListEntity e: entities()) {
      Helper.deleteFile(new File(getFilesDir(), e.name));
    }
    loadListAdapter();
    listView.clearChoices();
  }

  private void loadListAdapter() {
    listAdapter.clear();
    // look at all files in the directory, and add them to the list
    File d = getFilesDir();
    for (File f : getFilesDir().listFiles()) {
      String fname = f.getName();
      if(fname.startsWith("android_conversation_backup") && fname.endsWith(".zip")) {
        listAdapter.add(new ListEntity(fname, new Date(f.lastModified()), f.length()));
      }
    }
    listAdapter.notifyDataSetChanged();
  }
  
}
