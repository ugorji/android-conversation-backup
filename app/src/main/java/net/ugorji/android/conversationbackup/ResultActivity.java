package net.ugorji.android.conversationbackup;

import android.content.Intent;
import android.widget.Button;
import android.widget.TextView;

public class ResultActivity extends BaseCBActivity {
  //private static final String TAG = ResultActivity.class.getSimpleName();
  private TextView textview;
  @Override
  protected void onCreateBaseCallback() {
    setContentView(R.layout.result); 
    aboutAppButton = (Button)findViewById(R.id.result_about_app);
    exitAppButton = (Button)findViewById(R.id.result_exit_app);
    homeButton = (Button)findViewById(R.id.result_home);
    textview = (TextView)findViewById(R.id.result_text);
    updateText();
  }
  
  @Override
  protected void updateProgress(Intent intent) {
    updateText();
  }

  @Override
  public void onResume() {
    super.onResume();
    updateText();
  }
  
  private void updateText() {
    //read the file and update the contents.
    textview.setText(Helper.getFileContents(Helper.RESULT_LOG_FILE));
  }
}
