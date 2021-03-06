package com.erakk.lnreader.helper;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.erakk.lnreader.Constants;
import com.erakk.lnreader.activity.DisplayImageActivity;
import com.erakk.lnreader.activity.DisplayLightNovelContentActivity;
import com.erakk.lnreader.dao.NovelsDao;
import com.erakk.lnreader.model.PageModel;

@TargetApi(11)
public class BakaTsukiWebViewClient extends WebViewClient {
	private DisplayLightNovelContentActivity caller;
	
	public BakaTsukiWebViewClient(DisplayLightNovelContentActivity caller) {
		super();
		this.caller = caller;
	}
	
	@Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
		caller.setLastReadState();
		Log.d("shouldOverrideUrlLoading", url);
		
		Context context = view.getContext();
		
		// if image file
		if(url.contains("title=File:")) {
			Intent intent = new Intent(context, DisplayImageActivity.class);
			intent.putExtra(Constants.EXTRA_IMAGE_URL, url);
			context.startActivity(intent);
		}
		else {
			// get the title from url
			boolean useDefault = true;
			if(url.contains("/project/index.php?title=")) {
				String titles[] = url.split("title=", 2);
				if(titles.length == 2 && !(titles[1].length() == 0)) {
					Toast.makeText(context, "Loading: " + titles[1], Toast.LENGTH_SHORT).show();
					// check if have inside db
					NovelsDao dao = NovelsDao.getInstance(context);
					try {
						// split anchor text
						String[] titles2 = titles[1].split("#", 2 ); 

						// check if load different page.
						String currentPage = caller.getIntent().getStringExtra(Constants.EXTRA_PAGE);
						if(!currentPage.equalsIgnoreCase(titles2[0])) {
							PageModel tempPage = new PageModel();
							tempPage.setPage(titles2[0]);
							PageModel pageModel =  dao.getPageModel(tempPage, null);
							Log.d("shouldOverrideUrlLoading", "different : " + pageModel.getPage());
							caller.getIntent().putExtra(Constants.EXTRA_PAGE, pageModel.getPage());
							caller.recreate();
						}
						// navigate to the anchor if exist.
						if(titles2.length == 2){
							view.loadUrl("#" + titles2[1]);
						}
						
						useDefault = false;
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
			
			if(useDefault){
				
				Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
				context.startActivity(browserIntent);
			}
			return true;
		}
        return true;
    }	
}
