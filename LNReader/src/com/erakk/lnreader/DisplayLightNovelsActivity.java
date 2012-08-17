package com.erakk.lnreader;

import java.util.ArrayList;

import android.annotation.SuppressLint;
import android.app.ListActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.NavUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.erakk.lnreader.dao.NovelsDao;
import com.erakk.lnreader.helper.AsyncTaskResult;
import com.erakk.lnreader.helper.PageModelAdapter;
import com.erakk.lnreader.model.PageModel;

/*
 * Author: Nandaka
 * Copy from: NovelsActivity.java
 */

public class DisplayLightNovelsActivity extends ListActivity {
	ArrayList<PageModel> listItems=new ArrayList<PageModel>();
	PageModelAdapter adapter;
	NovelsDao dao = new NovelsDao(this);
	
	boolean onlyWatched = false;

    @SuppressLint("NewApi")
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display_light_novels);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        registerForContextMenu(getListView());
        
        Intent intent = getIntent();
        onlyWatched = intent.getExtras().getBoolean(Constants.EXTRA_ONLY_WATCHED);
        
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean invertColors = sharedPrefs.getBoolean("invert_colors", false);
        
        View NovelView = findViewById(R.id.light_novel_list_screen);

        if (invertColors == true) {
        	//NovelList.setBackgroundColor(Color.TRANSPARENT);
        	//ListText.setTextColor(Color.WHITE);
        	//isWatched
        	NovelView.setBackgroundColor(Color.BLACK);
        	
        }
        try {
        	adapter = new PageModelAdapter(this, R.layout.novel_list_item, listItems);
	        new LoadNovelsTask().execute(new Void[] {});
	    	setListAdapter(adapter);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			Toast t = Toast.makeText(this, e.getClass().toString() +": " + e.getMessage(), Toast.LENGTH_SHORT);
			t.show();					
		}
        
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        // Get the item that was clicked
        PageModel o = adapter.getItem(position);
        String novel = o.toString();
        //Create new intent
        Intent intent = new Intent(this, LightNovelChaptersActivity.class);
        intent.putExtra(Constants.EXTRA_MESSAGE, novel);
        intent.putExtra(Constants.EXTRA_PAGE, o.getPage());
        intent.putExtra(Constants.EXTRA_TITLE, o.getTitle());
        startActivity(intent);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_display_light_novels, menu);
        return true;
    }
        
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        	case R.id.menu_settings:
        		Intent launchNewIntent = new Intent(this, DisplaySettingsActivity.class);
        		startActivity(launchNewIntent);
        		return true;
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.novel_context_menu, menu);
    }
    
    @Override
    public boolean onContextItemSelected(MenuItem item) {
    	AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        //String[] names = getResources().getStringArray(R.array.novel_context_menu);
        switch(item.getItemId()) {
        case R.id.add_to_watch:
              Toast.makeText(this, "Added to Watch List",
                          Toast.LENGTH_SHORT).show();
              return true;
        case R.id.download_chapter:
            Toast.makeText(this, "Downloading Chapters",
                        Toast.LENGTH_SHORT).show();
            return true;
        case R.id.view_synopsys:
            Toast.makeText(this, "Viewing Synopsys",
                        Toast.LENGTH_SHORT).show();
            PageModel o = adapter.getItem(info.position);
            String novel = o.toString();
            Intent intent = new Intent(this, DisplaySynopsisActivity.class);
            intent.putExtra(Constants.EXTRA_MESSAGE, novel);
            intent.putExtra(Constants.EXTRA_PAGE, o.getPage());
            intent.putExtra(Constants.EXTRA_TITLE, o.getTitle());
            startActivity(intent);
            return true;
        default:
              return super.onContextItemSelected(item);
    	
//        if(item.getItemId() == android.R.layout.activity_list_item)
//        {
//        	AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
//           long id = getListView().getItemIdAtPosition(info.position);
//           Log.d(null, "Item ID at POSITION:"+id);
//        }
//        else
//        {
//            return false;
//        }
//        return true;
        }
    }
    
     public class LoadNovelsTask extends AsyncTask<Void, ProgressBar, AsyncTaskResult<ArrayList<PageModel>>> {

		@SuppressLint("NewApi")
		@Override
		protected AsyncTaskResult<ArrayList<PageModel>> doInBackground(Void... arg0) {
			ProgressBar pb = (ProgressBar) findViewById(R.id.progressBar2);
	    	pb.setIndeterminate(true);
	    	pb.setActivated(true);
	    	pb.animate();
	    	
			try {
				if (onlyWatched) {
					listItems = dao.getWatchedNovel();
				}
				else {
					listItems = dao.getNovels();
				}
				return new AsyncTaskResult<ArrayList<PageModel>>(listItems);
			} catch (Exception e) {
				e.printStackTrace();
				return new AsyncTaskResult<ArrayList<PageModel>>(e);
			}
		}
		
		@SuppressLint("NewApi")
		protected void onPostExecute(AsyncTaskResult<ArrayList<PageModel>> result) {
	         ProgressBar pb = (ProgressBar) findViewById(R.id.progressBar2);
			 TextView tv = (TextView) findViewById(R.id.loading);
			 tv.setVisibility(TextView.GONE);
	         pb.setActivated(false);
	         pb.setVisibility(ProgressBar.GONE);
	         
	         ArrayList<PageModel> list = result.getResult();
	         if(list != null) adapter.addAll(list);
	         if(result.getError() != null) {
        	 	Exception e = result.getError();
        	 	Toast t = Toast.makeText(getApplicationContext(), e.getClass().toString() + ": " + e.getMessage(), Toast.LENGTH_SHORT);
				t.show();
				Log.e(this.getClass().toString(), e.getClass().toString() + ": " + e.getMessage());
	         }
	    }    	 
    }
}

