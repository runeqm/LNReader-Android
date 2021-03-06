/**
 * 
 */
package com.erakk.lnreader.dao;

import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;

import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.erakk.lnreader.Constants;
import com.erakk.lnreader.LNReaderApplication;
import com.erakk.lnreader.callback.CallbackEventData;
import com.erakk.lnreader.callback.ICallbackNotifier;
import com.erakk.lnreader.helper.DBHelper;
import com.erakk.lnreader.helper.DownloadFileTask;
import com.erakk.lnreader.model.BookModel;
import com.erakk.lnreader.model.ImageModel;
import com.erakk.lnreader.model.NovelCollectionModel;
import com.erakk.lnreader.model.NovelContentModel;
import com.erakk.lnreader.model.PageModel;
import com.erakk.lnreader.parser.BakaTsukiParser;

/**
 * @author Nandaka
 * 
 */
public class NovelsDao {
	private static final String TAG = NovelsDao.class.toString();
	private static DBHelper dbh;

	private static NovelsDao instance;
	private static Object lock = new Object();
	public static NovelsDao getInstance(Context applicationContext) {
		synchronized (lock){
			if(instance == null) {
				instance = new NovelsDao(applicationContext);
			}
		}
		return instance;
	}
	
	public static NovelsDao getInstance() {
		synchronized (lock){
			if(instance == null) {
				throw new NullPointerException("NovelsDao is not Initialized!");
			}
		}
		return instance;
	}
	
	private NovelsDao(Context context) {
		if (dbh == null) {
			dbh = new DBHelper(context);
		}
	}

	public void deleteDB() {
		synchronized (dbh) {
			SQLiteDatabase db = dbh.getWritableDatabase();
			try{
				dbh.deletePagesDB(db);
			}finally{
				db.close();
			}
		}
	}

	public ArrayList<PageModel> getNovels(ICallbackNotifier notifier) throws Exception {
		ArrayList<PageModel> list = null;
		boolean refresh = false;
		PageModel page = null;
		SQLiteDatabase db = null;
		
		// check if main page exist
		synchronized (dbh) {
			try{
				db = dbh.getReadableDatabase();
				page = dbh.getMainPage(db);
			}finally{
				db.close();
			}
		}
		if (page == null) {
			refresh = true;
			Log.d(TAG, "No Main_Page data!");
		} else {
			Log.d(TAG, "Found Main_Page (" + page.getLastUpdate().toString() + "), last check: " + page.getLastCheck().toString());
			// compare if less than 7 day
			Date today = new Date();
			long diff = today.getTime() - page.getLastCheck().getTime();
			if (diff > (Constants.CHECK_INTERVAL * 24 * 3600 * 1000) && LNReaderApplication.getInstance().isOnline()) {
				refresh = true;
				Log.d(TAG, "Last check is over 7 days, checking online status");
			}
		}

		if (refresh) {
			// get updated main page and novel list from internet
			list = getNovelsFromInternet(notifier);
			Log.d(TAG, "Updated Novel List");
		} else {
			// get from db
			synchronized (dbh) {
				try{
					db = dbh.getReadableDatabase();
					list = dbh.getAllNovels(db);//dbh.selectAllByColumn(db, DBHelper.COLUMN_TYPE, PageModel.TYPE_NOVEL);
				}finally{
					db.close();
				}
			}
			Log.d(TAG, "Found: " + list.size());
		}

		return list;
	}

	public ArrayList<PageModel> getNovelsFromInternet(ICallbackNotifier notifier) throws Exception {
		if(!LNReaderApplication.getInstance().isOnline()) throw new Exception("No Network Connectifity");
		if (notifier != null) {
			notifier.onCallback(new CallbackEventData("Downloading novel list..."));
		}
		// get last updated main page revision from internet
		
		PageModel mainPage = new PageModel();
		mainPage.setPage("Main_Page");
		mainPage = getPageModel(mainPage, notifier);
		mainPage.setType(PageModel.TYPE_OTHER);
		mainPage.setParent("");
		mainPage.setLastCheck(new Date());

		ArrayList<PageModel> list = null;
		synchronized (dbh) {
			SQLiteDatabase db = dbh.getWritableDatabase();
			try{
				//db.beginTransaction();
				mainPage = dbh.insertOrUpdatePageModel(db, mainPage);
				Log.d(TAG, "Updated Main_Page");
	
				// now get the novel list
				list = new ArrayList<PageModel>();
				String url = Constants.BASE_URL + "/project";
				int retry = 0;
				while(retry < Constants.PAGE_DOWNLOAD_RETRY) {
					try{
						Response response = Jsoup.connect(url).timeout(Constants.TIMEOUT).execute();
						Document doc = response.parse();
			
						list = BakaTsukiParser.ParseNovelList(doc);
						Log.d(TAG, "Found from internet: " + list.size() + " Novels");
			
						// saved to db and get saved value
						list = dbh.insertAllNovel(db, list);
						
						// now get the saved value
						list = dbh.getAllNovels(db);
						//db.setTransactionSuccessful();
						
						if (notifier != null) {
							notifier.onCallback(new CallbackEventData("Found: " + list.size() + " novels."));
						}
						break;
					}catch(EOFException eof) {
						++retry;
						if(notifier != null) {
							notifier.onCallback(new CallbackEventData("Retrying: Main_Page (" + retry + " of " + Constants.PAGE_DOWNLOAD_RETRY + ")"));
						}
						if(retry > Constants.PAGE_DOWNLOAD_RETRY) throw eof;
					}
					catch(IOException eof) {
						++retry;
						String message = "Retrying: Main_Page (" + retry + " of " + Constants.PAGE_DOWNLOAD_RETRY + ")";
						if(notifier != null) {
							notifier.onCallback(new CallbackEventData(message));
						}
						Log.d(TAG, message, eof);
						if(retry > Constants.PAGE_DOWNLOAD_RETRY) throw eof;
					}
				}
			}finally{
				//db.endTransaction();
				db.close();
			}
		}
		return list;
	}

	public ArrayList<PageModel> getWatchedNovel() {
		ArrayList<PageModel> watchedNovel = null;
		synchronized (dbh) {
			SQLiteDatabase db = dbh.getReadableDatabase();
			try{
				watchedNovel = dbh.selectAllByColumn(db, DBHelper.COLUMN_IS_WATCHED + " = ? and " + DBHelper.COLUMN_PARENT + " = ?", new String[] {"1", "Main_Page"});
			}finally{
				db.close();
			}			
		}
		return watchedNovel;
	}

	public PageModel getPageModel(PageModel page, ICallbackNotifier notifier) throws Exception {
		PageModel pageModel = null;
		synchronized (dbh) {
			SQLiteDatabase db = dbh.getReadableDatabase();
			try{
				pageModel = dbh.getPageModel(db, page.getPage());
			}finally{
				db.close();
			}
		}
		if (pageModel == null) {
			pageModel = getPageModelFromInternet(page, notifier);
		}
		return pageModel;
	}

	public PageModel getPageModelFromInternet(PageModel page, ICallbackNotifier notifier) throws Exception {
		if(!LNReaderApplication.getInstance().isOnline()) throw new Exception("No Network Connectifity");
		Log.d(TAG, "PageModel = " + page.getPage());
		
		int retry = 0;
		while(retry < Constants.PAGE_DOWNLOAD_RETRY) {
			try{
				Response response = Jsoup.connect("http://www.baka-tsuki.org/project/api.php?action=query&prop=info&format=xml&titles=" + page.getPage()).timeout(Constants.TIMEOUT).execute();
				PageModel pageModel = BakaTsukiParser.parsePageAPI(page, response.parse());
				pageModel.setFinishedRead(page.isFinishedRead());
				pageModel.setWatched(page.isWatched());
				
				synchronized (dbh) {
					// save to db and get saved value
					SQLiteDatabase db = dbh.getWritableDatabase();
					try{
						pageModel = dbh.insertOrUpdatePageModel(db, pageModel);
					}finally{
						db.close();
					}
				}
				return pageModel;
			}catch(EOFException eof) {
				++retry;
				if(notifier != null) {
					notifier.onCallback(new CallbackEventData("Retrying: " + page + " (" + retry + " of " + Constants.PAGE_DOWNLOAD_RETRY + ")"));
				}
				if(retry > Constants.PAGE_DOWNLOAD_RETRY) throw eof;
			}
			catch(IOException eof) {
				++retry;
				String message = "Retrying: " + page + " (" + retry + " of " + Constants.PAGE_DOWNLOAD_RETRY + ")";
				if(notifier != null) {
					notifier.onCallback(new CallbackEventData(message));
				}
				Log.d(TAG, message, eof);
				if(retry > Constants.PAGE_DOWNLOAD_RETRY) throw eof;
			}
		}
		return null;
	}

	public PageModel updatePageModel(PageModel page) {
		PageModel pageModel = null;
		synchronized (dbh) {
			SQLiteDatabase db = dbh.getWritableDatabase();
			try{
				pageModel = dbh.insertOrUpdatePageModel(db, page);
			}
			finally{
				db.close();
			}
		}
		return pageModel;
	}
	
	/*
	 * NovelCollectionModel
	 */

	public NovelCollectionModel getNovelDetails(PageModel page, ICallbackNotifier notifier) throws Exception {
		boolean refresh = false;
		NovelCollectionModel novel = null;
		synchronized (dbh) {
			SQLiteDatabase db = dbh.getReadableDatabase();
			try{
				novel = dbh.getNovelDetails(db, page.getPage());
			}
			finally{
				db.close();
			}
		}
		if (novel != null) {
			// TODO: add check to refresh
		} else {
			refresh = true;
		}

		if (refresh) {
			novel = getNovelDetailsFromInternet(page, notifier);
		}

		return novel;
	}

	public NovelCollectionModel getNovelDetailsFromInternet(PageModel page, ICallbackNotifier notifier) throws Exception {
		if(!LNReaderApplication.getInstance().isOnline()) throw new Exception("No Network Connectifity");
		Log.d(TAG, "Getting Novel Details from internet: " + page.getPage());
		NovelCollectionModel novel = null;
		
		int retry = 0;
		while(retry < Constants.PAGE_DOWNLOAD_RETRY) {
			try{
				Response response = Jsoup.connect(Constants.BASE_URL + "/project/index.php?title=" + page.getPage()).timeout(Constants.TIMEOUT).execute();
				Document doc = response.parse();
				novel = BakaTsukiParser.ParseNovelDetails(doc, page);
			}catch(EOFException eof) {
				++retry;
				if(notifier != null) {
					notifier.onCallback(new CallbackEventData("Retrying: " + page.getPage() + " (" + retry + " of " + Constants.PAGE_DOWNLOAD_RETRY + ")"));
				}
				if(retry > Constants.PAGE_DOWNLOAD_RETRY) throw eof;
			}
			catch(IOException eof) {
				++retry;
				String message = "Retrying: " + page.getPage() + " (" + retry + " of " + Constants.PAGE_DOWNLOAD_RETRY + ")";
				if(notifier != null) {
					notifier.onCallback(new CallbackEventData(message));
				}
				Log.d(TAG, message, eof);
				if(retry > Constants.PAGE_DOWNLOAD_RETRY) throw eof;
			}
			
			// Novel details' Page Model
			if(novel != null){
				page.setParent("Main_Page"); // insurance
				// get the last update time from internet
				PageModel novelPageTemp = getPageModelFromInternet(page, notifier);
				if(novelPageTemp != null) {
					page.setLastUpdate(novelPageTemp.getLastUpdate());
					page.setLastCheck(new Date());
					novel.setLastUpdate(novelPageTemp.getLastUpdate());
					novel.setLastCheck(new Date());
				}
				else {
					page.setLastUpdate(new Date(0));
					page.setLastCheck(new Date());
					novel.setLastUpdate(new Date(0));
					novel.setLastCheck(new Date());
				}
				// save the changes
				synchronized (dbh) {
					SQLiteDatabase db = dbh.getWritableDatabase();
					try{
						page = dbh.insertOrUpdatePageModel(db, page);
					}
					finally{
						db.close();
					}
				}

				synchronized (dbh) {
					// insert to DB and get saved value
					SQLiteDatabase db = dbh.getWritableDatabase();
					try{
						db.beginTransaction();
						novel = dbh.insertNovelDetails(db, novel);
						db.setTransactionSuccessful();
					}
					finally{
						db.endTransaction();
						db.close();
					}
				}
				// download cover image
				if (novel.getCoverUrl() != null) {
					DownloadFileTask task = new DownloadFileTask(notifier);
					ImageModel image = task.downloadImage(novel.getCoverUrl());
					// TODO: need to save to db?
					Log.d("Image", image.toString());
				}

				Log.d(TAG, "Complete getting Novel Details from internet: " + page.getPage());
				break;
			}
		}
		return novel;
	}


	public void deleteBooks(BookModel bookDel) {
		synchronized (dbh) {
			// get from db
			SQLiteDatabase db = dbh.getReadableDatabase();
			try{
				BookModel tempBook = dbh.getBookModel(db, bookDel.getId());
				if(tempBook != null) {
					dbh.deleteBookModel(db, tempBook);
				}
			}
			finally{
				db.close();
			}
		}
	}
	

	public void deletePage(PageModel page) {
		synchronized (dbh) {
			// get from db
			SQLiteDatabase db = dbh.getReadableDatabase();
			try{
				PageModel tempPage = dbh.getPageModel(db, page.getId());
				if(tempPage != null) {
					dbh.deletePageModel(db, tempPage);
				}
			}
			finally{
				db.close();
			}
		}
	}
	
	public ArrayList<PageModel> getChapterCollection(String page, String title, BookModel book) {
		synchronized (dbh) {
			// get from db
			SQLiteDatabase db = dbh.getReadableDatabase();
			try{
				return dbh.getChapterCollection(db, page + Constants.NOVEL_BOOK_DIVIDER + title, book);
			}
			finally{
				db.close();
			}
		}
	}
	
	/*
	 * NovelContentModel
	 */

	public NovelContentModel getNovelContent(PageModel page, ICallbackNotifier notifier) throws Exception {
		NovelContentModel content = null;

		synchronized (dbh) {
			// get from db
			SQLiteDatabase db = dbh.getReadableDatabase();
			try{
				content = dbh.getNovelContent(db, page.getPage());
			}
			finally{
				db.close();
			}
		}
		// get from Internet;
		if (content == null) {
			Log.d("getNovelContent", "Get from Internet: " + page.getPage());
			content = getNovelContentFromInternet(page, notifier);
		}

		return content;
	}

	public NovelContentModel getNovelContentFromInternet(PageModel page, ICallbackNotifier notifier) throws Exception {
		if(!LNReaderApplication.getInstance().isOnline()) throw new Exception("No Network Connectifity");
		
		String oldTitle = page.getTitle();
		
		NovelContentModel content = new NovelContentModel();
		int retry = 0;
		while(retry < Constants.PAGE_DOWNLOAD_RETRY) {
			try{
				Response response = Jsoup.connect(Constants.BASE_URL + "/project/api.php?action=parse&format=xml&prop=text|images&page=" + page.getPage()).timeout(Constants.TIMEOUT).execute();
				Document doc = response.parse();

				content = BakaTsukiParser.ParseNovelContent(doc, page);
				break;
			}catch(EOFException eof) {
				++retry;
				if(notifier != null) {
					notifier.onCallback(new CallbackEventData("Retrying: " + page.getPage() + " (" + retry + " of " + Constants.PAGE_DOWNLOAD_RETRY + ")"));
				}
				if(retry > Constants.PAGE_DOWNLOAD_RETRY) throw eof;
			}
			catch(IOException eof) {
				++retry;
				String message = "Retrying: " + page.getPage() + " (" + retry + " of " + Constants.PAGE_DOWNLOAD_RETRY + ")";
				if(notifier != null) {
					notifier.onCallback(new CallbackEventData(message));
				}
				Log.d(TAG, message, eof);
				if(retry > Constants.PAGE_DOWNLOAD_RETRY) throw eof;
			}
		}
		// download all attached images
		DownloadFileTask task = new DownloadFileTask(notifier);
		for (Iterator<ImageModel> i = content.getImages().iterator(); i.hasNext();) {
			ImageModel image = i.next();
			
			if(notifier != null) {
				notifier.onCallback(new CallbackEventData("Start downloading: " + image.getUrl()));
			}
			image = task.downloadImage(image.getUrl());
			// TODO: need to save image to db? mostly thumbnail only
		}
		
		// get last updated info
		
		PageModel contentPageModelTemp = getPageModelFromInternet(content.getPageModel(), notifier);
		if(contentPageModelTemp != null) {
			// overwrite the old title
			content.getPageModel().setTitle(oldTitle);
			//syncronize the date
			content.getPageModel().setLastUpdate(contentPageModelTemp.getLastUpdate());
			content.getPageModel().setLastCheck(new Date());
			content.setLastUpdate(contentPageModelTemp.getLastUpdate());
			content.setLastCheck(new Date());
		}		
		// page model will be also saved in insertNovelContent()
		
		synchronized (dbh) {
			// save to DB, and get the saved value
			SQLiteDatabase db = dbh.getWritableDatabase();
			try{
				// TODO: somehow using transaction cause problem...
				db.beginTransaction();
				content = dbh.insertNovelContent(db, content);
				db.setTransactionSuccessful();
			}
			finally{
				db.endTransaction();
				db.close();
			}
		}
		return content;
	}

	public NovelContentModel updateNovelContent(NovelContentModel content) throws Exception {
		synchronized (dbh) {
			SQLiteDatabase db = dbh.getWritableDatabase();
			try{
				content = dbh.insertNovelContent(db, content);
			}
			finally{
				db.close();
			}
		}
		return content;
	}

	/*
	 * ImageModel
	 */

	public ImageModel getImageModel(String page, ICallbackNotifier notifier) throws Exception {
		
		
		ImageModel image = null;
		synchronized (dbh) {
			SQLiteDatabase db = dbh.getReadableDatabase();
			try{
				image = dbh.getImage(db, page);
	
				if (image == null) {
					Log.d(TAG, "Image not found, might need to check by referer: " + page);
					image = dbh.getImageByReferer(db, page);
				}
			}
			finally{
				db.close();
			}
		}
		if (image == null) {
			Log.d(TAG, "Image not found, getting data from internet: " + page);
			image = getImageModelFromInternet(page, notifier);
		}
		return image;
	}

	public ImageModel getImageModelFromInternet(String page, ICallbackNotifier notifier) throws Exception {
		if(!LNReaderApplication.getInstance().isOnline()) throw new Exception("No Network Connectifity");
		ImageModel image = null;
		String url = page;
		if (!url.startsWith("http"))
			url = Constants.BASE_URL + url;
		
		if(notifier != null) {
			notifier.onCallback(new CallbackEventData("Parsing File Page: " + url));
		}
		
		int retry = 0;
		while(retry < Constants.IMAGE_DOWNLOAD_RETRY) {
			try{
				Response response = Jsoup.connect(url).timeout(Constants.TIMEOUT).execute();
				Document doc = response.parse();
				
				// only return the full  image url
				image = BakaTsukiParser.parseImagePage(doc);
				
				DownloadFileTask downloader = new DownloadFileTask(notifier);
				image = downloader.downloadImage(image.getUrl());
				image.setReferer(page);

				synchronized (dbh) {
					// save to db and get the saved value
					SQLiteDatabase db = dbh.getWritableDatabase();
					try{
						image = dbh.insertImage(db, image);
					}
					finally{
						db.close();
					}
				}
				break;
			}catch(EOFException eof) {
				if(notifier != null) {
					notifier.onCallback(new CallbackEventData("Retrying: " + url + " (" + retry + " of "+ Constants.IMAGE_DOWNLOAD_RETRY + ")"));
				}
				++retry;
				if(retry > Constants.IMAGE_DOWNLOAD_RETRY) throw eof;
			}
			catch(IOException eof) {
				++retry;
				String message = "Retrying: " + url + " (" + retry + " of " + Constants.PAGE_DOWNLOAD_RETRY + ")";
				if(notifier != null) {
					notifier.onCallback(new CallbackEventData(message));
				}
				Log.d(TAG, message, eof);
				if(retry > Constants.PAGE_DOWNLOAD_RETRY) throw eof;
			}	
		}		
		return image;
	}

}
